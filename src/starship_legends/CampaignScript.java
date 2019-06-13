package starship_legends;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import starship_legends.hullmods.Reputation;

import java.util.*;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static void log(String message) { if(true) Global.getLogger(CampaignScript.class).info(message); }

    static final float AVG_TIME_BETWEEN_REP_CHANGES = 10f;
    static final String NEW_LINE = "\n    ";

    public CampaignScript() { super(true); }

    static float playerFP = 0, enemyFP = 0;
    static long xpBeforeBattle = Long.MAX_VALUE;
    static Random rand = new Random();
    static Set<FleetMemberAPI> deployedShips = new HashSet<>();
    static Set<FleetMemberAPI> disabledShips = new HashSet<>();

    static Map<FleetMemberAPI, Float> playerDeployedFP = new HashMap();
    static Map<FleetMemberAPI, Float> enemyDeployedFP = new HashMap();
    static Map<String, Float> fractionDamageTaken = new HashMap<>();
    static Map<String, PersonAPI> originalCaptains = new HashMap<>();
    static Map<String, Float> fpWorthOfDamageDealt = new HashMap<>();
    static List<FleetMemberAPI> originalShipList;

    static Saved<LinkedList<RepChange>> pendingRepChanges = new Saved<>("pendingRepChanges", new LinkedList<RepChange>());
    static Saved<Float> timeUntilNextChange = new Saved<>("timeUntilNextChange", AVG_TIME_BETWEEN_REP_CHANGES);
    static Saved<HashMap<String, Integer>> dayMothballed = new Saved<>("dayMothballed", new HashMap<String, Integer>());

    public static void recordDamageDealt(String shipID, float fpWorthOfDamage) {
        if(fpWorthOfDamage > 0) {
            if (!fpWorthOfDamageDealt.containsKey(shipID)) fpWorthOfDamageDealt.put(shipID, fpWorthOfDamage);
            else fpWorthOfDamageDealt.put(shipID, fpWorthOfDamageDealt.get(shipID) + fpWorthOfDamage);
        }
    }

    public static void recordDamageSustained(String shipID, float damageSustained) {
        if(damageSustained > 0) {
            if (!fractionDamageTaken.containsKey(shipID)) fractionDamageTaken.put(shipID, Math.min(1, damageSustained));
            else fractionDamageTaken.put(shipID, Math.min(1, fractionDamageTaken.get(shipID) + damageSustained));
        }
    }

    public static void collectRealSnapshotInfoIfNeeded() {
        if(Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        if(originalShipList == null) {
            reset();

            originalShipList = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();

            for (FleetMemberAPI ship : originalShipList) {
                //fractionDamageTaken.put(ship.getId(), ship.getStatus().getHullFraction());
                originalCaptains.put(ship.getId(), ship.getCaptain());
            }
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (!battle.isPlayerInvolved() || originalShipList == null) return;

            long xpEarned = Global.getSector().getPlayerStats().getXP() - xpBeforeBattle;

            if(ModPlugin.COMPENSATE_FOR_EXPERIENCE_MULT) xpEarned /= Global.getSettings().getFloat("xpGainMult");

            Random rand = new Random();
            float difficulty = 1;

            if(Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector")
                        && ruthless_sector.ModPlugin.getDifficultyMultiplierForLastBattle() > 0) {

                difficulty = (float)ruthless_sector.ModPlugin.getDifficultyMultiplierForLastBattle();
            } else {
                for(Float f : playerDeployedFP.values()) playerFP += f;
                for(Float f : enemyDeployedFP.values()) enemyFP += f;

//                for(Map.Entry<FleetMemberAPI, Float> e :playerDeployedFP.entrySet()) {
//                    log(e.getKey().getHullId() + " : " + e.getValue());
//                }
//                for(Map.Entry<FleetMemberAPI, Float> e : enemyDeployedFP.entrySet()) {
//                    log(e.getKey().getHullId() + " : " + e.getValue());
//                }

                difficulty = (playerFP > 1 ? enemyFP / playerFP : 1);
            }

            BattleReport report = new BattleReport(difficulty, battle);
            Set<String> currentShipSet = new HashSet<>();

            for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                currentShipSet.add(ship.getId());
            }

            for (FleetMemberAPI ship : originalShipList) {
                if (!originalCaptains.containsKey(ship.getId()) || ship.isMothballed()) continue;

                RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);

                PersonAPI captain = originalCaptains.containsKey(ship.getId())
                        ? originalCaptains.get(ship.getId())
                        : ship.getCaptain();
//                PersonAPI captain = ship.getCaptain() == null || ship.getCaptain().isDefault()
//                        ? originalCaptains.get(ship.getId())
//                        : ship.getCaptain();
                RepChange rc = new RepChange(ship, captain);
                rc.disabled = disabledShips.contains(ship);
                rc.deployed = deployedShips.contains(ship) || rc.disabled;
                float xpToGuarantee = RepRecord.getXpToGuaranteeNewTrait(ship);
                float playerLevelBonus = 1 + ModPlugin.TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL
                        * Global.getSector().getPlayerStats().getLevel();
                float xp = rc.deployed ? xpEarned : Math.min(xpEarned, ModPlugin.MAX_XP_FOR_RESERVED_SHIPS);
                float traitChance = (xp / xpToGuarantee) * playerLevelBonus;

                if(rc.disabled) rc.damageTakenFraction = 1;
                else if(!rc.deployed || !fractionDamageTaken.containsKey(ship.getId())) rc.damageTakenFraction = 0;
                else rc.damageTakenFraction = fractionDamageTaken.get(ship.getId());

                rc.damageDealtPercent = fpWorthOfDamageDealt.containsKey(ship.getId()) && rc.deployed
                        ? Math.max(0, fpWorthOfDamageDealt.get(ship.getId()) / Math.max(1, ship.getDeploymentCostSupplies()))
                        : 0;

                if(!rc.deployed) traitChance *= ship.getHullSpec().isCivilianNonCarrier()
                        ? ModPlugin.TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS
                        : ModPlugin.TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS;

                float bonusChance, battleRating = ModPlugin.BASE_RATING;

                if(rc.deployed) {
                    battleRating += difficulty * ModPlugin.BATTLE_DIFFICULTY_MULT
                            - rc.damageTakenFraction * ModPlugin.DAMAGE_TAKEN_MULT
                            + Math.max(0, rc.damageDealtPercent - ModPlugin.DAMAGE_DEALT_MIN_THRESHOLD) * ModPlugin.DAMAGE_DEALT_MULT;

                    if(ship.getHullSpec().isCivilianNonCarrier()) {
                        bonusChance = battleRating = ModPlugin.BASE_RATING;
                    } else {
                        if(ModPlugin.USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE) {
                            rc.newRating = battleRating;
                            bonusChance = battleRating;
                        } else {
                            rc.newRating = rep.getAdjustedRating(battleRating, 0.1f);
                            rc.ratingAdjustment = rc.newRating - rep.getRating();
                            bonusChance = 0.5f + rc.newRating - rep.getFractionOfBonusEffectFromTraits();
                        }
                    }
                } else {
                    rc.newRating = rep.getRating();
                    bonusChance = ModPlugin.BASE_RATING * ModPlugin.BONUS_CHANCE_FOR_RESERVED_SHIPS_MULT;
                }


                String msg = "Rolling reputation for " + ship.getShipName() + " (" + ship.getHullSpec().getHullName() + ")";
                if(rc.deployed) msg += NEW_LINE + "Battle Difficulty: " + difficulty
                        + NEW_LINE + "Damage Taken: " + rc.damageTakenFraction + " of total hull lost"
                        + NEW_LINE + "Damage Dealt: " + rc.damageDealtPercent + " of own supply cost worth of damage"
                        + NEW_LINE + "Chance of Gaining New Trait: " + (int)(traitChance * 100) + "% - ";

                if (rand.nextFloat() <= traitChance) {
                    boolean traitIsBonus = (ModPlugin.BONUS_CHANCE_RANDOMNESS * (rand.nextFloat() - 0.5f) + 0.5f) <= bonusChance;

                    msg += "SUCCEEDED"
                            + NEW_LINE + "Bonus Chance: " + (int)(bonusChance * 100) + "% - " + (traitIsBonus ? "SUCCEEDED" : "FAILED");

                    if(traitIsBonus || !ModPlugin.IGNORE_ALL_MALUSES) {
                        rc.setTraitChange(chooseTrait(ship, rand, !traitIsBonus, rc.damageTakenFraction,
                                rc.damageDealtPercent, rc.deployed, rc.disabled));
                    }
                } else {
                    msg += "FAILED";

                    float shuffleChance = Math.abs(bonusChance - 0.5f) * ModPlugin.TRAIT_POSITION_CHANGE_CHANCE_MULT;

                    if(rc.deployed && shuffleChance > 0.05f && !ModPlugin.IGNORE_ALL_MALUSES) {
                        int sign = (int)Math.signum(bonusChance - 0.5f);

                        msg += NEW_LINE + "Chance to Shuffle a Trait " + (sign < 0 ? "Worse" : "Better") + ": "
                                + (int)(shuffleChance * 100) + "% - ";

                        if(rep.getTraits().size() <= 2) {
                            msg += "TOO FEW TRAITS";
                        } else if(rand.nextFloat() <= shuffleChance) {

//                            for(int i = rep.getTraits().size() - 1; i >= 0; --i) {
//                                Trait t = rep.getTraits().get(i);
//
//
//                                int j = t.effectSign > 0 ? i - sign : i + sign;
//                            }


                            msg += "SUCCEEDED";

                            int i = rep.getTraits().size() - (int)(Math.ceil(Math.pow(rand.nextFloat(), 2) * rep.getTraits().size()));

                            rc.setTraitChange(rep.getTraits().get(Math.max(i, 1)), sign);
                        } else {
                            msg += "FAILED";
                        }
                    }
                }

                int loyaltyChange = 0;

                if (ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && rc.deployed && captain != null
                        && !captain.isDefault()) {

                    LoyaltyLevel ll = rep.getLoyaltyLevel(captain);
                    float loyaltyAdjustChance = (battleRating - 0.5f) + rep.getLoyaltyBonus(captain);
                    boolean success = rand.nextFloat() <= Math.abs(loyaltyAdjustChance);

                    if(loyaltyAdjustChance > 0.05f && !ll.isAtBest() && rc.newRating >= ll.getRatingRequiredToImprove()) {
                        loyaltyAdjustChance *= ll.getBaseImproveChance() * ModPlugin.IMPROVE_LOYALTY_CHANCE_MULT;
                        msg += NEW_LINE + "Loyalty Increase Chance: " + (int)(loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                        if(success) loyaltyChange = 1;
                    } else if(loyaltyAdjustChance < 0.05f && !ll.isAtWorst()) {
                        loyaltyAdjustChance = Math.abs(loyaltyAdjustChance);
                        loyaltyAdjustChance *= ll.getBaseWorsenChance() * ModPlugin.WORSEN_LOYALTY_CHANCE_MULT;
                        msg += NEW_LINE + "Loyalty Reduction Chance: " + (int)(loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                        if(success) loyaltyChange = -1;
                    }
                }

                rc.setLoyaltyChange(loyaltyChange);

                if(rc.deployed && ModPlugin.LOG_REPUTATION_CALCULATION_FACTORS) log(msg);

                if(!currentShipSet.contains(ship.getId())) {
                    rc.damageTakenFraction = Float.MAX_VALUE;
                    rc.captainOpinionChange = 0;
                    rc.trait = null;
                }

                rc.newRating *= 100;
                rc.ratingAdjustment *= 100;

                report.addChange(rc);
                rc.apply(false);
            }

            if(report.changes.size() > 0) Global.getSector().getIntelManager().addIntel(report);
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        reset();
    }

    public static Trait chooseTrait(FleetMemberAPI ship, Random rand, boolean traitIsBad, float fractionDamageTaken,
                                    float damageDealtRatio, boolean wasDeployed, boolean wasDisabled) {

        RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);

        if(rep.hasMaximumTraits()) return null;

        WeightedRandomPicker<TraitType> picker = TraitType.getPickerCopy(wasDisabled);

        for(Trait trait : rep.getTraits()) picker.remove(trait.getType());

        for(RepChange change : CampaignScript.pendingRepChanges.val) {
            if(change.ship == ship && change.trait != null) picker.remove(change.trait.getType());
        }

        while(!picker.isEmpty()) {
            TraitType type = picker.pickAndRemove();
            Trait trait = type.getTrait(traitIsBad);
            boolean traitIsRelevant = true;
            ShieldAPI.ShieldType shieldType = ship.getHullSpec().getShieldType();

            for(String tag : type.getTags()) {
                switch (tag) {
                    case TraitType.Tags.CARRIER:
                        if(!ship.isCarrier()) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.SHIELD:
                        if(shieldType != ShieldAPI.ShieldType.FRONT && shieldType != ShieldAPI.ShieldType.OMNI)
                            traitIsRelevant = false;
                        break;
                    case TraitType.Tags.CLOAK:
                        if(shieldType != ShieldAPI.ShieldType.PHASE) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.NO_AI:
                        if(ship.getMinCrew() <= 0) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.DEFENSE:
                        if(traitIsBad && fractionDamageTaken < 0.05f) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.ATTACK:
                        if(traitIsBad && damageDealtRatio > 1) traitIsRelevant = false;
                        break;
                }
            }

            if(!type.getIncompatibleBuiltInHullmods().isEmpty()) {
                for (String mod : ship.getHullSpec().getBuiltInMods()) {
                    if (type.getIncompatibleBuiltInHullmods().contains(mod)) traitIsRelevant = false;
                }
            }

            if(!type.getRequiredBuiltInHullmods().isEmpty()) {
                if (!ship.getHullSpec().getBuiltInMods().containsAll(type.getRequiredBuiltInHullmods())) {
                    traitIsRelevant = false;
                }
            }

            boolean skipCombatLogisticsMismatch = false;

            if(type.getTags().contains(TraitType.Tags.LOGISTICAL)
                    && !ship.getHullSpec().isCivilianNonCarrier()
                    && rand.nextFloat() <= ModPlugin.CHANCE_TO_IGNORE_LOGISTICS_TRAITS_ON_COMBAT_SHIPS) {

                skipCombatLogisticsMismatch = true;
            } else if(!type.getTags().contains(TraitType.Tags.LOGISTICAL)
                    && ship.getHullSpec().isCivilianNonCarrier()
                    && rand.nextFloat() <= ModPlugin.CHANCE_TO_IGNORE_COMBAT_TRAITS_ON_CIVILIAN_SHIPS) {

                skipCombatLogisticsMismatch = true;
            }

//            boolean skipCombatLogisticsMismatch = (type.getTags().contains(TraitType.Tags.LOGISTICAL) == wasDeployed)
//                    && !traitIsBad && rand.nextFloat() <= 0.75f;

            if(trait != null && traitIsRelevant && !skipCombatLogisticsMismatch)
                return trait;
        }

        return null;
    }

    static void reset() {
        playerFP = 0;
        enemyFP = 0;
        playerDeployedFP.clear();
        enemyDeployedFP.clear();
        fractionDamageTaken.clear();
        originalCaptains.clear();
        originalShipList = null;
        fpWorthOfDamageDealt.clear();
        deployedShips.clear();
        disabledShips.clear();
        xpBeforeBattle = Long.MAX_VALUE;
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            EngagementResultForFleetAPI pf = result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();
            EngagementResultForFleetAPI ef = !result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();

            disabledShips.addAll(pf.getDisabled());
            disabledShips.addAll(pf.getDestroyed());

            if(xpBeforeBattle != Long.MAX_VALUE && ef.getGoal() == FleetGoal.ESCAPE) return;

            deployedShips.addAll(pf.getDeployed());
            deployedShips.addAll(pf.getRetreated());

            xpBeforeBattle = Global.getSector().getPlayerStats().getXP();

            for(FleetMemberAPI fm : pf.getDeployed()) playerDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : pf.getRetreated()) playerDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : pf.getDisabled()) playerDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : pf.getDestroyed()) playerDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));

            for(FleetMemberAPI fm : ef.getDeployed()) enemyDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : ef.getRetreated()) enemyDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : ef.getDisabled()) enemyDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));
            for(FleetMemberAPI fm : ef.getDestroyed()) enemyDeployedFP.put(fm, CombatPlugin.getShipStrength(fm));
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            RepChange.updateRepHullMod(ship);
        }
    }

    @Override
    public void reportPlayerActivatedAbility(AbilityPlugin ability, Object param) {
        if(true) return;

        Random rand = new Random();
        FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();

        for(FleetMemberAPI ship : fleet.getMembersListCopy()) {
            RepChange d = new RepChange(ship, ship.getCaptain());

            switch (ability.getId()) {
                case "distress_call": d.trait = chooseTrait(ship, rand, false, 0.5f, 0, true, false); break; // Good combat trait
                case "interdiction_pulse": d.trait = chooseTrait(ship, rand, true, 0.5f, 0, true, false); break; // Bad combat trait
                case "sustained_burn": d.trait = chooseTrait(ship, rand, rand.nextBoolean(), 0.5f, 0, false, false); break; // Reserved trait
                case "emergency_burn": d.trait = chooseTrait(ship, rand, rand.nextBoolean(), 0.5f, 0, true, true); break; // Disabled trait
                case "sensor_burst": d.setLoyaltyChange(1); break;
                case "go_dark": d.setLoyaltyChange(-1); break;
            }

            d.apply(true);
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        try {
            int day = Global.getSector().getClock().getDay();

            for (FleetMemberAPI ship : new LinkedList<>(Reputation.getShipsOfNote())) {
                if (ship.isMothballed() && RepRecord.existsFor(ship)) {
                    if (!dayMothballed.val.containsKey(ship.getId())) {
                        //log("Mothballed");
                        dayMothballed.val.put(ship.getId(), day);
                    } else if (day > dayMothballed.val.get(ship.getId()) + ModPlugin.DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION * RepRecord.get(ship).getTraits().size()) {
                        //log("Reputation removed");
                        dayMothballed.val.remove(ship.getId());
                        RepRecord.deleteFor(ship);
                    }
                } else if (dayMothballed.val.containsKey(ship.getId())) {
                    //log("No longer mothballed");
                    dayMothballed.val.remove(ship.getId());
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() { return !ModPlugin.settingsAreRead; }

    @Override
    public void advance(float amount) {
        try {
            if (!ModPlugin.readSettingsIfNecessary()) return;

            if(pendingRepChanges.val.size() > 0 && (timeUntilNextChange.val -= amount) <= 0) {
                int i = rand.nextInt(pendingRepChanges.val.size());

                if(pendingRepChanges.val.get(i).apply(true)) {
                    timeUntilNextChange.val = Math.max(1, AVG_TIME_BETWEEN_REP_CHANGES * (0.5f + rand.nextFloat())
                            * (3f / Math.max(pendingRepChanges.val.size(), 3f)));

                }

                pendingRepChanges.val.remove(i);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
