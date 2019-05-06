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
import com.fs.starfarer.api.util.WeightedRandomPicker;

import java.util.*;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static void log(String message) { if(ModPlugin.LOG_REPUTATION_CALCULATION_FACTORS) Global.getLogger(CampaignScript.class).info(message); }

    static final float AVG_TIME_BETWEEN_REP_CHANGES = 10f;

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

            long xpEarned = Global.getSector().getPlayerStats().getXP() - xpBeforeBattle;
            Random rand = new Random();
            FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();

            if(xpEarned <= 0) return;

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
                float damageDealtRatio = fpWorthOfDamageDealt.get(ship) / Math.max(1, ship.getDeploymentCostSupplies());
                PersonAPI captain = ship.getCaptain() == null || ship.getCaptain().isDefault()
                        ? originalCaptains.get(ship.getId())
                        : ship.getCaptain();
                RepChange repChange = new RepChange(ship, captain);
                float xp = deployed ? xpEarned : Math.min(xpEarned, ModPlugin.MAX_XP_FOR_RESERVED_SHIPS);
                float traitChance = (xp / xpToGuarantee) * playerLevelBonus;

                if(!deployed) traitChance *= ship.isCivilian()
                        ? ModPlugin.TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_CIVILIAN_SHIPS
                        : ModPlugin.TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_COMBAT_SHIPS;

                //float combatScore =

                String msg = "Rolling reputation for " + ship.getShipName() + " (" + ship.getHullSpec().getHullName() + ")"
                        + "\n    Chance of gaining new trait: " + (int)(traitChance * 100) + "% - ";

                if (rand.nextFloat() <= traitChance) {
                    RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);

                    msg += "SUCCEEDED";
                    if(deployed) msg += "\n      Battle Difficulty: " + difficulty
                            + "\n           Damage Taken: " + fractionDamageTaken + " of total hull lost"
                            + "\n           Damage Dealt: " + damageDealtRatio + " of own supply cost worth of damage";

                    float malusChance = !deployed
                            ? ModPlugin.CHANCE_OF_MALUS_WHILE_IN_RESERVE
                            : ModPlugin.CHANCE_OF_MALUS_AT_NO_HULL_LOST + fractionDamageTaken
                            * (ModPlugin.CHANCE_OF_MALUS_AT_HALF_HULL_LOST - ModPlugin.CHANCE_OF_MALUS_AT_NO_HULL_LOST);

                    //if(wasDeployed && difficulty > 1) malusChance *= (1 / (1 - difficulty)) * ModPlugin.MALUS_CHANCE_REDUCTION_FOR_DIFFICULT_BATTLES;


                    boolean traitIsBad = rand.nextFloat() < Math.min(malusChance, ModPlugin.CHANCE_OF_MALUS_AT_HALF_HULL_LOST);

                    msg += "             Bonus chance: " + (int)(100 - malusChance * 100) + "% - " + (traitIsBad ? "FAILED" : "SUCCEEDED");

                    repChange.newTrait = Trait.chooseTrait(ship, rand, fractionDamageTaken, damageDealtRatio,
                            difficulty, deployed, disabled, msg);
                } else msg += "FAILED";

                log("    " + ship.getHullId() + " " + fractionDamageTaken + " " + deployed + " " + captain.getNameString());

                if (ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM
                        && deployed
                        && RepRecord.existsFor(ship)
                        && captain != null
                        && !captain.isDefault()) {

                    RepRecord rep = RepRecord.get(ship);
                    int opinionOfOfficer = rep.getOpinionOfOfficer(captain);
                    float improveLoyaltyChance = ModPlugin.IMPROVE_LOYALTY_CHANCE_MULTIPLIER;

                    if(ModPlugin.MAX_HULL_FRACTION_LOST_FOR_LOYALTY_INCREASE > 0) {
                        improveLoyaltyChance *= 1 - fractionDamageTaken / ModPlugin.MAX_HULL_FRACTION_LOST_FOR_LOYALTY_INCREASE;
                    }

                    if(ModPlugin.SCALE_LOYALTY_INCREASE_CHANCE_BY_BATTLE_DIFFICULTY) improveLoyaltyChance *= difficulty;

                    switch (opinionOfOfficer) {
                        case -2: improveLoyaltyChance *= 0.25f; break;
                        case -1: improveLoyaltyChance *= 0.25f; break;
                        case 0: improveLoyaltyChance *= 0.25f; break;
                        case 1: improveLoyaltyChance *= 0.05f; break;
                        case 2: default: improveLoyaltyChance = 0; break;
                    }

                    msg += "    Chance of Loyalty increase: " + (int)(improveLoyaltyChance * 100) + "%";

                    if (difficulty >= ModPlugin.MIN_BATTLE_DIFFICULTY_REQUIRED_FOR_LOYALTY_INCREASE
                            && fractionDamageTaken <= ModPlugin.MAX_HULL_FRACTION_LOST_FOR_LOYALTY_INCREASE
                            && rand.nextFloat() <= improveLoyaltyChance) {

                        repChange.captainOpinionChange = 1;
                        //rep.adjustOpinionOfOfficer(captain, 1);
                    } else if (rand.nextFloat() <= fractionDamageTaken * ModPlugin.CHANCE_OF_LOYALTY_DECREASE_AT_ALL_HULL_LOST
                            && opinionOfOfficer > -2) {

                        repChange.captainOpinionChange = -1;
                        //rep.adjustOpinionOfOfficer(captain, -1);
                    }
                }

                if(repChange.hasAnyChanges()) pendingRepChanges.val.add(repChange);
            }

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

//    @Override
//    public void reportPlayerActivatedAbility(AbilityPlugin ability, Object param) {
//        Random rand = new Random();
//        FleetDataAPI fleet = Global.getSector().getPlayerFleet().getFleetData();
//
//        for(FleetMemberAPI ship : fleet.getMembersListCopy()) {
//            RepChange d = new RepChange(ship, ship.getCaptain());
//            d.newTrait = Trait.chooseTrait(ship, rand, ability.getId().startsWith("sun") ? 1 : 0, 0, true, false);
//            d.apply();
//        }
//    }

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
