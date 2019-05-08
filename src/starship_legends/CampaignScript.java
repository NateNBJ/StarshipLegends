package starship_legends;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.DeployedFleetMemberAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;

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

    public static void recordDamage(FleetMemberAPI ship, float fpWorthOfDamage) {
        if(fpWorthOfDamage > 0) {
            if (!fpWorthOfDamageDealt.containsKey(ship)) fpWorthOfDamageDealt.put(ship, fpWorthOfDamage);
            else fpWorthOfDamageDealt.put(ship, fpWorthOfDamageDealt.get(ship) + fpWorthOfDamage);
        }
    }

    public static void collectRealSnapshotInfo() {
        originalHullFractions.clear();

        for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            originalHullFractions.put(ship.getId(), ship.getStatus().getHullFraction());
            originalCaptains.put(ship.getId(), ship.getCaptain());
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (!battle.isPlayerInvolved()) {
                playerFP = 0;
                enemyFP = 0;
                return;
            }
            //Global.getSector().getIntelManager().getIntel(this.getClass()).get(0)
            long xpEarned = Global.getSector().getPlayerStats().getXP() - xpBeforeBattle;
            Random rand = new Random();
            FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();

            if(xpEarned <= 0) return;

            BaseIntelPlugin report = new BattleReport();




            boolean rsIsAvailable = Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector");
            float difficulty = rsIsAvailable
                    ? (float)ruthless_sector.ModPlugin.getDifficultyMultiplierForLastBattle()
                    : enemyFP / playerFP;

            for (FleetMemberAPI ship : fleet.getMembersListCopy()) {
                if (!originalHullFractions.containsKey(ship.getId()) || ship.isMothballed()) continue;


                boolean disabled = disabledShips.contains(ship),
                        deployed = deployedShips.contains(ship) || disabled;
                float xpToGuarantee = RepRecord.getXpToGuaranteeNewTrait(ship);
                float playerLevelBonus = 1 + ModPlugin.TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL
                        * Global.getSector().getPlayerStats().getLevel();
                float fractionDamageTaken = originalHullFractions.get(ship.getId()) - ship.getStatus().getHullFraction();
                float damageDealtRatio = fpWorthOfDamageDealt.containsKey(ship)
                        ? fpWorthOfDamageDealt.get(ship) / Math.max(1, ship.getDeploymentCostSupplies())
                        : 0;
                PersonAPI captain = ship.getCaptain() == null || ship.getCaptain().isDefault()
                        ? originalCaptains.get(ship.getId())
                        : ship.getCaptain();
                RepChange repChange = new RepChange(ship, captain);
                float xp = deployed ? xpEarned : Math.min(xpEarned, ModPlugin.MAX_XP_FOR_RESERVED_SHIPS);
                float traitChance = (xp / xpToGuarantee) * playerLevelBonus;

                if(!deployed) traitChance *= ship.isCivilian()
                        ? ModPlugin.TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_CIVILIAN_SHIPS
                        : ModPlugin.TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_COMBAT_SHIPS;

                float bonusChance = ModPlugin.BASE_CHANCE_TO_BE_BONUS;

                if(deployed) {
                    bonusChance = bonusChance
                            + difficulty * ModPlugin.BONUS_CHANCE_BATTLE_DIFFICULTY_MULTIPLIER
                            - fractionDamageTaken * ModPlugin.BONUS_CHANCE_DAMAGE_TAKEN_MULTIPLIER
                            + Math.max(0, damageDealtRatio - 1) * ModPlugin.BONUS_CHANCE_DAMAGE_DEALT_MULTIPLIER;
                } else bonusChance *= ModPlugin.BONUS_CHANCE_FOR_RESERVED_SHIPS_MULTIPLIER;

                String msg = "Rolling reputation for " + ship.getShipName() + " (" + ship.getHullSpec().getHullName() + ")"
                        + NEW_LINE + "Battle Difficulty: " + difficulty
                        + NEW_LINE + "Damage Taken: " + fractionDamageTaken + " of total hull lost"
                        + NEW_LINE + "Damage Dealt: " + damageDealtRatio + " of own supply cost worth of damage"
                        + NEW_LINE + "Chance of Gaining New Trait: " + (int)(traitChance * 100) + "% - ";


                if (rand.nextFloat() <= traitChance) {
                    boolean traitIsBonus = rand.nextFloat() < bonusChance;

                    msg += "SUCCEEDED"
                            + NEW_LINE + "Bonus Chance: " + (int)(bonusChance * 100) + "% - " + (traitIsBonus ? "SUCCEEDED" : "FAILED");

                    repChange.trait = Trait.chooseTrait(ship, rand, !traitIsBonus, fractionDamageTaken,
                            damageDealtRatio, deployed, disabled);
                } else {
                    msg += "FAILED";

                    float shuffleChance = Math.abs(bonusChance - 0.5f);

                    if(shuffleChance > 0.1f && RepRecord.existsFor(ship)) {
                        int sign = (int)Math.signum(bonusChance - 0.5f);
                        RepRecord rep = RepRecord.get(ship);

                        msg += NEW_LINE + "Chance to Shuffle a Trait " + (sign < 0 ? "Worse" : "Better") + ": "
                                + (int)(shuffleChance * 100) + " - ";

                        if(rep.getTraits().size() <= 2) {
                            msg += "TOO FEW TRAITS";
                        } if(rand.nextFloat() <= shuffleChance) {
                            msg += "SUCCEEDED";

                            int i = (int)(Math.floor(Math.pow(rand.nextFloat(), 2) * rep.getTraits().size()));
                            i = Math.min(i, rep.getTraits().size() - 1);

                            repChange.trait = rep.getTraits().get(i);
                            repChange.shuffleSign = sign;
                        } else {
                            msg += "FAILED";
                        }
                    }
                }

                if (ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && deployed && RepRecord.existsFor(ship) && captain != null
                        && !captain.isDefault()) {

                    RepRecord rep = RepRecord.get(ship);
                    LoyaltyLevel ll = rep.getLoyaltyLevel(captain);
                    float loyaltyAdjustChance = (bonusChance - 0.5f) + rep.getLoyaltyMultiplier(captain);

                    if(loyaltyAdjustChance > 0.1f && !ll.isAtBest()) {
                        loyaltyAdjustChance *= ll.getBaseImproveChance() * ModPlugin.IMPROVE_LOYALTY_CHANCE_MULTIPLIER;
                        boolean success = rand.nextFloat() <= loyaltyAdjustChance;
                        msg += NEW_LINE + "Loyalty Increase Chance: " + (int)(loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                        if(success) repChange.captainOpinionChange = 1;
                    } else if(loyaltyAdjustChance < 0.1f && !ll.isAtWorst()) {
                        loyaltyAdjustChance = Math.abs(loyaltyAdjustChance);
                        loyaltyAdjustChance *= ll.getBaseWorsenChance() * ModPlugin.WORSEN_LOYALTY_CHANCE_MULTIPLIER;
                        boolean success = rand.nextFloat() <= loyaltyAdjustChance;
                        msg += NEW_LINE + "Loyalty Reduction Chance: " + (int)(loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                        if(success) repChange.captainOpinionChange = 1;
                    }
                }

                if(ModPlugin.LOG_REPUTATION_CALCULATION_FACTORS) log(msg);

                if(repChange.hasAnyChanges()) pendingRepChanges.val.add(repChange);
            }

            Global.getSector().getIntelManager().addIntel(report);

            playerFP = 0;
            enemyFP = 0;
            originalHullFractions.clear();
            originalCaptains.clear();
            fpWorthOfDamageDealt.clear();
            deployedShips.clear();
            disabledShips.clear();
            xpBeforeBattle = Long.MAX_VALUE;
        } catch (Exception e) { ModPlugin.reportCrash(e); }
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
                case "distress_call": d.trait = Trait.chooseTrait(ship, rand, true, 0.5f, 0, true, false); break; // Good combat trait
                case "interdiction_pulse": d.trait = Trait.chooseTrait(ship, rand, false, 0.5f, 0, true, false); break; // Bad combat trait
                case "sustained_burn": d.trait = Trait.chooseTrait(ship, rand, rand.nextBoolean(), 0.5f, 0, false, false); break; // Reserved trait
                case "emergency_burn": d.trait = Trait.chooseTrait(ship, rand, rand.nextBoolean(), 0.5f, 0, true, true); break; // Disabled trait
                case "sensor_burst": d.captainOpinionChange = 1; break;
                case "go_dark": d.captainOpinionChange = -1; break;
            }

            d.apply();
        }
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

                if(pendingRepChanges.val.get(i).apply()) {
                    timeUntilNextChange.val = Math.max(1, AVG_TIME_BETWEEN_REP_CHANGES * (0.5f + rand.nextFloat())
                            * (3f / Math.max(pendingRepChanges.val.size(), 3f)));

                }

                pendingRepChanges.val.remove(i);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
