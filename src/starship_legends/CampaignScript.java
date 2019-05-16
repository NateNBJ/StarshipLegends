package starship_legends;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import starship_legends.hullmods.Reputation;

import java.util.*;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static void log(String message) { if(true) Global.getLogger(CampaignScript.class).info(message); }

    static final float AVG_TIME_BETWEEN_REP_CHANGES = 10f;
    static final String NEW_LINE = "\n    ";

    public CampaignScript() { super(true); }

    float playerFP = 0, enemyFP = 0;
    long xpBeforeBattle = Long.MAX_VALUE;
    Random rand = new Random();
    Set<FleetMemberAPI> deployedShips = new HashSet<>();
    Set<FleetMemberAPI> disabledShips = new HashSet<>();
    static Map<String, Float> originalHullFractions = new HashMap<>();
    static Map<String, PersonAPI> originalCaptains = new HashMap<>();
    static Map<FleetMemberAPI, Float> fpWorthOfDamageDealt = new HashMap<>();

    static Saved<LinkedList<RepChange>> pendingRepChanges = new Saved<>("pendingRepChanges", new LinkedList<RepChange>());
    static Saved<Float> timeUntilNextChange = new Saved<>("timeUntilNextChange", AVG_TIME_BETWEEN_REP_CHANGES);
    static Saved<HashMap<String, Integer>> dayMothballed = new Saved<>("dayMothballed", new HashMap<String, Integer>());

    public static void recordDamage(FleetMemberAPI ship, float fpWorthOfDamage) {
        if(fpWorthOfDamage > 0) {
            if (!fpWorthOfDamageDealt.containsKey(ship)) fpWorthOfDamageDealt.put(ship, fpWorthOfDamage);
            else fpWorthOfDamageDealt.put(ship, fpWorthOfDamageDealt.get(ship) + fpWorthOfDamage);
        }
    }

    public static void collectRealSnapshotInfoIfNeeded() {
        if(Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        if(originalHullFractions.isEmpty()) {
            for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                originalHullFractions.put(ship.getId(), ship.getStatus().getHullFraction());
                originalCaptains.put(ship.getId(), ship.getCaptain());
            }
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (!battle.isPlayerInvolved()) {
                reset();
                return;
            }

            long xpEarned = Global.getSector().getPlayerStats().getXP() - xpBeforeBattle;

            if(ModPlugin.COMPENSATE_FOR_EXPERIENCE_MULTIPLIER) xpEarned /= Global.getSettings().getFloat("xpGainMult");

            Random rand = new Random();
            FleetDataAPI pf = Global.getSector().getPlayerFleet().getFleetData();
            float difficulty = Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector")
                    ? (float)ruthless_sector.ModPlugin.getDifficultyMultiplierForLastBattle()
                    : enemyFP / playerFP;
            BattleReport report = new BattleReport(difficulty, battle);

            for (FleetMemberAPI ship : pf.getMembersListCopy()) {
                if (!originalHullFractions.containsKey(ship.getId()) || ship.isMothballed()) continue;

                PersonAPI captain = ship.getCaptain() == null || ship.getCaptain().isDefault()
                        ? originalCaptains.get(ship.getId())
                        : ship.getCaptain();
                RepChange rc = new RepChange(ship, captain);
                rc.disabled = disabledShips.contains(ship);
                rc.deployed = deployedShips.contains(ship) || rc.disabled;
                float xpToGuarantee = RepRecord.getXpToGuaranteeNewTrait(ship);
                float playerLevelBonus = 1 + ModPlugin.TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL
                        * Global.getSector().getPlayerStats().getLevel();
                rc.damageTakenFraction = originalHullFractions.get(ship.getId()) - ship.getStatus().getHullFraction();
                rc.damageDealtPercent = fpWorthOfDamageDealt.containsKey(ship)
                        ? fpWorthOfDamageDealt.get(ship) / Math.max(1, ship.getDeploymentCostSupplies())
                        : 0;
                float xp = rc.deployed ? xpEarned : Math.min(xpEarned, ModPlugin.MAX_XP_FOR_RESERVED_SHIPS);
                float traitChance = (xp / xpToGuarantee) * playerLevelBonus;

                if(!rc.deployed) traitChance *= ship.isCivilian()
                        ? ModPlugin.TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_CIVILIAN_SHIPS
                        : ModPlugin.TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_COMBAT_SHIPS;

                float bonusChance = ModPlugin.BASE_CHANCE_TO_BE_BONUS;

                if(rc.deployed) {
                    bonusChance = bonusChance
                            + difficulty * ModPlugin.BONUS_CHANCE_BATTLE_DIFFICULTY_MULTIPLIER
                            - rc.damageTakenFraction * ModPlugin.BONUS_CHANCE_DAMAGE_TAKEN_MULTIPLIER
                            + Math.max(0, rc.damageDealtPercent - ModPlugin.BONUS_CHANCE_DAMAGE_DEALT_MIN_THRESHOLD) * ModPlugin.BONUS_CHANCE_DAMAGE_DEALT_MULTIPLIER;
                } else bonusChance *= ModPlugin.BONUS_CHANCE_FOR_RESERVED_SHIPS_MULTIPLIER;

                String msg = "Rolling reputation for " + ship.getShipName() + " (" + ship.getHullSpec().getHullName() + ")";
                if(rc.deployed) msg += NEW_LINE + "Battle Difficulty: " + difficulty
                        + NEW_LINE + "Damage Taken: " + rc.damageTakenFraction + " of total hull lost"
                        + NEW_LINE + "Damage Dealt: " + rc.damageDealtPercent + " of own supply cost worth of damage"
                        + NEW_LINE + "Chance of Gaining New Trait: " + (int)(traitChance * 100) + "% - ";


                if (rand.nextFloat() <= traitChance) {
                    boolean traitIsBonus = (ModPlugin.BONUS_CHANCE_RANDOMNESS * (rand.nextFloat() - 0.5f) + 0.5f) < bonusChance;

                    msg += "SUCCEEDED"
                            + NEW_LINE + "Bonus Chance: " + (int)(bonusChance * 100) + "% - " + (traitIsBonus ? "SUCCEEDED" : "FAILED");

                    if(traitIsBonus || !ModPlugin.IGNORE_ALL_MALUSES) {
                        rc.setTraitChange(chooseTrait(ship, rand, !traitIsBonus, rc.damageTakenFraction,
                                rc.damageDealtPercent, rc.deployed, rc.disabled));
                    }
                } else {
                    msg += "FAILED";

                    float shuffleChance = Math.abs(bonusChance - 0.5f);

                    if(shuffleChance > 0.05f && RepRecord.existsFor(ship) && !ModPlugin.IGNORE_ALL_MALUSES) {
                        int sign = (int)Math.signum(bonusChance - 0.5f);
                        RepRecord rep = RepRecord.get(ship);

                        msg += NEW_LINE + "Chance to Shuffle a Trait " + (sign < 0 ? "Worse" : "Better") + ": "
                                + (int)(shuffleChance * 100) + "% - ";

                        if(rep.getTraits().size() <= 2) {
                            msg += "TOO FEW TRAITS";
                        } else if(rand.nextFloat() <= shuffleChance) {
                            msg += "SUCCEEDED";

                            int i = rep.getTraits().size() - (int)(Math.ceil(Math.pow(rand.nextFloat(), 2) * rep.getTraits().size()));

                            rc.setTraitChange(rep.getTraits().get(Math.max(i, 1)), sign);
                        } else {
                            msg += "FAILED";
                        }
                    }
                }

                if (ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && rc.deployed && RepRecord.existsFor(ship) && captain != null
                        && !captain.isDefault()) {

                    RepRecord rep = RepRecord.get(ship);
                    LoyaltyLevel ll = rep.getLoyaltyLevel(captain);
                    float loyaltyAdjustChance = (bonusChance - 0.5f) + rep.getLoyaltyBonus(captain);
                    boolean success = rand.nextFloat() <= Math.abs(loyaltyAdjustChance);

                    if(loyaltyAdjustChance > 0.05f && !ll.isAtBest()) {
                        loyaltyAdjustChance *= ll.getBaseImproveChance() * ModPlugin.IMPROVE_LOYALTY_CHANCE_MULTIPLIER;
                        msg += NEW_LINE + "Loyalty Increase Chance: " + (int)(loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                        if(success) rc.setLoyaltyChange(1);
                    } else if(loyaltyAdjustChance < 0.05f && !ll.isAtWorst()) {
                        loyaltyAdjustChance = Math.abs(loyaltyAdjustChance);
                        loyaltyAdjustChance *= ll.getBaseWorsenChance() * ModPlugin.WORSEN_LOYALTY_CHANCE_MULTIPLIER;
                        msg += NEW_LINE + "Loyalty Reduction Chance: " + (int)(loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                        if(success) rc.setLoyaltyChange(-1);
                    }
                }

                if(ModPlugin.LOG_REPUTATION_CALCULATION_FACTORS) log(msg);

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
                    case TraitType.Tags.SALVAGE_GANTRY:
                        if(!ship.getVariant().hasHullMod("repair_gantry")) traitIsRelevant = false;
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

            boolean skipCombatLogisticsMismatch = (type.getTags().contains(TraitType.Tags.LOGISTICAL) == wasDeployed)
                    && !traitIsBad && rand.nextFloat() <= 0.75f;

            if(trait != null && traitIsRelevant && !skipCombatLogisticsMismatch)
                return trait;
        }

        return null;
    }

    void reset() {
        playerFP = 0;
        enemyFP = 0;
        originalHullFractions.clear();
        originalCaptains.clear();
        fpWorthOfDamageDealt.clear();
        deployedShips.clear();
        disabledShips.clear();
        xpBeforeBattle = Long.MAX_VALUE;
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            xpBeforeBattle = Global.getSector().getPlayerStats().getXP();

            EngagementResultForFleetAPI pf = result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();
            EngagementResultForFleetAPI ef = !result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();

            deployedShips.addAll(pf.getDeployed());
            deployedShips.addAll(pf.getRetreated());

            disabledShips.addAll(pf.getDisabled());
            disabledShips.addAll(pf.getDestroyed());

            if(pf.getAllEverDeployedCopy() != null) {
                for (DeployedFleetMemberAPI fm : pf.getAllEverDeployedCopy()) {
                    playerFP += fm.getMember().getDeploymentCostSupplies();
                }
            }

            if(ef.getAllEverDeployedCopy() != null) {
                for (DeployedFleetMemberAPI fm : ef.getAllEverDeployedCopy()) {
                    enemyFP += fm.getMember().getDeploymentCostSupplies();
                }
            }
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

            for (FleetMemberAPI ship : Reputation.getShipsOfNote()) {
                if (ship.isMothballed() && RepRecord.existsFor(ship)) {
                    if (!dayMothballed.val.containsKey(ship.getId())) {
                        log("Mothballed");
                        dayMothballed.val.put(ship.getId(), day);
                    } else if (day > dayMothballed.val.get(ship.getId()) + ModPlugin.DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION * RepRecord.get(ship).getTraits().size()) {
                        log("Reputation removed");
                        dayMothballed.val.remove(ship.getId());
                        RepRecord.deleteFor(ship);
                    }
                } else if (dayMothballed.val.containsKey(ship.getId())) {
                    log("No longer mothballed");
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
