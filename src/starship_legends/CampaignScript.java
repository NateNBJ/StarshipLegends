package starship_legends;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerUtil;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import starship_legends.events.FamousDerelictIntel;
import starship_legends.events.FamousFlagshipIntel;
import starship_legends.events.FamousShipBarEvent;
import starship_legends.hullmods.Reputation;

import java.util.*;
import java.util.List;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static class BattleRecord {
        PersonAPI originalCaptain = null;
        float fractionDamageTaken = 0, damageDealt = 0, supportContribution = 0, rating = 0;
    }

    static void log(String message) { if(true) Global.getLogger(CampaignScript.class).info(message); }

    static final long INITIAL_TIMESTAMP = -55661070348000L;
    static final float AVG_TIME_BETWEEN_REP_CHANGES = 10f;
    static final String NEW_LINE = "\n    ";

    public CampaignScript() { super(true); }

    static boolean isResetNeeded = false, damageOnlyDealtViaNukeCommand = true;
    static float playerFP = 0, enemyFP = 0, fpWorthOfDamageDealtDuringEngagement = 0;
    static long previousXP = Long.MAX_VALUE;
    static Random rand = new Random();
    static Set<FleetMemberAPI> deployedShips = new HashSet<>(),
            disabledShips = new HashSet<>(),
            destroyedEnemies = new HashSet<>(),
            routedEnemies = new HashSet<>();
    static FleetEncounterContext context = null;

    static Map<FleetMemberAPI, Float> playerDeployedFP = new HashMap();
    static Map<FleetMemberAPI, Float> enemyDeployedFP = new HashMap();
    static Map<String, BattleRecord> battleRecords = new HashMap<>();
    static List<FleetMemberAPI> originalShipList;
    static FleetMemberAPI famousRecoverableShip = null;

    static Saved<LinkedList<RepChange>> pendingRepChanges = new Saved<>("pendingRepChanges", new LinkedList<RepChange>());
    static Saved<Float> timeUntilNextChange = new Saved<>("timeUntilNextChange", 0f);
    static Saved<HashMap<String, Integer>> dayMothballed = new Saved<>("dayMothballed", new HashMap<String, Integer>());

    public static BattleRecord getBattleRecord(String shipID) {
        if(battleRecords.containsKey(shipID)) {
            return battleRecords.get(shipID);
        } else {
            BattleRecord newBR = new BattleRecord();
            battleRecords.put(shipID, newBR);
            return newBR;
        }
    }
    public static void recordSupportContribution(String shipID, float contribution) {
        if(contribution > 0) {
            getBattleRecord(shipID).supportContribution += contribution;
        }
    }
    public static void recordDamageDealt(String shipID, float fpWorthOfDamage) {
        if(fpWorthOfDamage > 0) {
            getBattleRecord(shipID).damageDealt += fpWorthOfDamage;
            fpWorthOfDamageDealtDuringEngagement += fpWorthOfDamage;
        }
    }
    public static void recordDamageSustained(String shipID, float damageSustained) {
        if(damageSustained > 0) getBattleRecord(shipID).fractionDamageTaken += damageSustained;

    }
    public static void collectRealSnapshotInfoIfNeeded() {
        if(Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        if(originalShipList == null) {
            //reset();

            originalShipList = Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy();

            for (FleetMemberAPI ship : originalShipList) {
                getBattleRecord(ship.getId()).originalCaptain = ship.getCaptain();
            }
        }
    }
    public static long getReAdjustedXp() {
        long xpEarned = Global.getSector().getPlayerStats().getXP() - previousXP;

        if(ModPlugin.COMPENSATE_FOR_EXPERIENCE_MULT) xpEarned /= Global.getSettings().getFloat("xpGainMult");

        return xpEarned;
    }
    public static void growRepForPeacefulXp(long xp) {
        float playerLevelBonus = 1 + ModPlugin.TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL
                * Global.getSector().getPlayerStats().getLevel();

        for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            RepChange rc = new RepChange(ship);
            float chancePerXp = RepRecord.getTraitChancePerXp(ship);
            float traitChance = xp * chancePerXp * playerLevelBonus;

            traitChance *= ship.getHullSpec().isCivilianNonCarrier()
                    ? ModPlugin.TRAIT_CHANCE_MULT_FOR_CIVILIAN_SHIPS
                    : ModPlugin.TRAIT_CHANCE_MULT_FOR_COMBAT_SHIPS;

            if (rand.nextFloat() <= traitChance) {
                boolean traitIsBonus = ModPlugin.IGNORE_ALL_MALUSES || rand.nextFloat() <= ModPlugin.BONUS_CHANCE_FOR_CIVILIAN_SHIPS;

                rc.setTraitChange(RepRecord.chooseNewTrait(ship, rand, !traitIsBonus, 1, 0, false));

                pendingRepChanges.val.add(rc);
            }
        }
    }

    static void reset() {
        try {
            boolean forceRecoveryOfFamousShip = false;

            if(context != null && famousRecoverableShip != null && context.didPlayerWinEncounterOutright()) {
                for(FleetMemberAPI m : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    if(m.equals(famousRecoverableShip)) break;
                }

                forceRecoveryOfFamousShip = true;
            }

            if(forceRecoveryOfFamousShip) {
                // Then no recovery option will be displayed at all...

                CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();

                famousRecoverableShip.getRepairTracker().setMothballed(true);
                Global.getSector().getCampaignUI().addMessage("The " + famousRecoverableShip.getShipName()
                        + " has been recovered and added to your fleet.");

                // Below ripped from FleetInteractionDialogPluginImpl RECOVERY_SELECT
                if (famousRecoverableShip.getStatus().getNumStatuses() <= 1) {
                    famousRecoverableShip.getStatus().repairDisabledABit();
                }

                float minHull = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_HULL_MIN, 0f);
                float maxHull = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_HULL_MAX, 0f);
                float minCR = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_CR_MIN, 0f);
                float maxCR = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_CR_MAX, 0f);

                float hull = (float) Math.random() * (maxHull - minHull) + minHull;
                if (hull < 0.01f) hull = 0.01f;
                famousRecoverableShip.getStatus().setHullFraction(hull);

                float cr = (float) Math.random() * (maxCR - minCR) + minCR;
                famousRecoverableShip.getRepairTracker().setCR(cr);

                playerFleet.getFleetData().addFleetMember(famousRecoverableShip);
                if (context != null) {
                    context.getBattle().getCombinedFor(playerFleet).getFleetData().addFleetMember(famousRecoverableShip);
                    context.getBattle().getMemberSourceMap().put(famousRecoverableShip, playerFleet);
                }

                famousRecoverableShip.setFleetCommanderForStats(null, null);
                famousRecoverableShip.setOwner(0);

                if (!Misc.isUnremovable(famousRecoverableShip.getCaptain())) {
                    famousRecoverableShip.setCaptain(Global.getFactory().createPerson());
                    famousRecoverableShip.getCaptain().setFaction(Factions.PLAYER);
                }
                Global.getLogger(CampaignScript.class).info("Famous ship force recovered");
            }

            context = null;
            famousRecoverableShip = null;
            playerFP = 0;
            enemyFP = 0;
            fpWorthOfDamageDealtDuringEngagement = 0;
            damageOnlyDealtViaNukeCommand = true;
            playerDeployedFP.clear();
            enemyDeployedFP.clear();
            battleRecords.clear();
            originalShipList = null;
            deployedShips.clear();
            disabledShips.clear();
            destroyedEnemies.clear();
            routedEnemies.clear();
            FactionConfig.clearEnemyFleetRep();
            CombatPlugin.CURSED.clear();
            CombatPlugin.PHASEMAD.clear();
            previousXP = Global.getSector().getPlayerStats().getXP();

            if(originalShipList != null) {
                Set<FleetMemberAPI> survivingShips = new HashSet(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

                for (FleetMemberAPI ship : originalShipList) {
                    if (!survivingShips.contains(ship)) RepRecord.deleteFor(ship);
                }
            }

            isResetNeeded = false;
        } catch (Exception e) { ModPlugin.reportCrash(e); }

    }
    static void calculateBattleRatings(float difficulty) {
        if(deployedShips.isEmpty()) return;

        for (FleetMemberAPI ship : deployedShips) {
            BattleRecord br = getBattleRecord(ship.getId());

            br.damageDealt = Math.max(0, br.damageDealt) / Math.max(1, ship.getDeploymentCostSupplies());
            br.fractionDamageTaken = !disabledShips.contains(ship) ? Math.min(Math.max(0, br.fractionDamageTaken), 1) : 1;

            br.rating = ModPlugin.BASE_RATING
                    + ModPlugin.BATTLE_DIFFICULTY_MULT * difficulty
                    - ModPlugin.DAMAGE_TAKEN_MULT * br.fractionDamageTaken
                    + ModPlugin.SUPPORT_MULT * br.supportContribution
                    + ModPlugin.DAMAGE_DEALT_MULT * Math.max(0, br.damageDealt - ModPlugin.DAMAGE_DEALT_MIN_THRESHOLD);
        }
    }

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES || !battle.isPlayerInvolved() || originalShipList == null) return;

            long xpEarned = getReAdjustedXp();

            Random rand = new Random();
            float difficulty = 1;
            boolean isWin = battle == null || primaryWinner == null || battle.getPlayerSide() == null ? null
                    : battle.getPlayerSide().contains(primaryWinner);

            if(ModPlugin.USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY
                    && Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector")) {

                try {
                    playerFP = (float)ruthless_sector.ModPlugin.getPlayerFleetStrengthInLastBattle();
                    enemyFP = (float)ruthless_sector.ModPlugin.getEnemyFleetStrengthInLastBattle();
                    difficulty = (float)ruthless_sector.ModPlugin.getDifficultyMultiplierForLastBattle();
                } catch (Exception e) {
                    ModPlugin.reportCrash(e, false);
                }
            } else {
                for(Float f : playerDeployedFP.values()) playerFP += f;
                for(Float f : enemyDeployedFP.values()) enemyFP += f;

                difficulty = (playerFP > 1 ? enemyFP / playerFP : 1);
            }

            BattleReport report = new BattleReport(difficulty, battle, destroyedEnemies, routedEnemies, isWin, playerFP, enemyFP);
            Set<String> currentShipSet = new HashSet<>();

            for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                currentShipSet.add(ship.getId());
            }

            calculateBattleRatings(difficulty);

            for (FleetMemberAPI ship : originalShipList) {
                if (!battleRecords.containsKey(ship.getId()) || ship.isMothballed()) continue;

                BattleRecord br = getBattleRecord(ship.getId());
                RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);
                RepChange rc = new RepChange(ship, br, deployedShips.contains(ship), disabledShips.contains(ship));

                float chancePerXp = RepRecord.getTraitChancePerXp(ship);
                float playerLevelBonus = 1 + ModPlugin.TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL
                        * Global.getSector().getPlayerStats().getLevel();
                //xp = rc.deployed ? xp : Math.min(xp, ModPlugin.MAX_XP_FOR_RESERVED_SHIPS);
                float traitChance = xpEarned * chancePerXp * playerLevelBonus;
                int adjustmentSign = 0;
                float bonusChance;
                boolean participatedInBattle = rc.deployed && (br.supportContribution > 0 || damageOnlyDealtViaNukeCommand);

                if(participatedInBattle) {
                    float traitChanceMultPerCaptainLevel = br.originalCaptain.isPlayer()
                            ? ModPlugin.TRAIT_CHANCE_MULT_PER_PLAYER_CAPTAIN_LEVEL
                            : ModPlugin.TRAIT_CHANCE_MULT_PER_NON_PLAYER_CAPTAIN_LEVEL;

                    traitChance *= ModPlugin.TRAIT_CHANCE_MULT_FLAT
                            + ModPlugin.TRAIT_CHANCE_MULT_PER_FLEET_POINT * ship.getHullSpec().getFleetPoints()
                            + ModPlugin.TRAIT_CHANCE_MULT_PER_DAMAGE_TAKEN_PERCENT * br.fractionDamageTaken * 100f
                            + ModPlugin.TRAIT_CHANCE_MULT_PER_DAMAGE_DEALT_PERCENT * br.damageDealt * 100f
                            + traitChanceMultPerCaptainLevel * br.originalCaptain.getStats().getLevel();
                } else {
                    traitChance *= ship.getHullSpec().isCivilianNonCarrier()
                            ? ModPlugin.TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS
                            : ModPlugin.TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS;
                }

                if(ship.getHullSpec().isCivilianNonCarrier()) {
                    rc.newRating = bonusChance = br.rating = ModPlugin.BONUS_CHANCE_FOR_CIVILIAN_SHIPS;
                } else if(participatedInBattle) {
                    if(ModPlugin.USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE) {
                        rc.newRating = br.rating;
                        bonusChance = br.rating;
                    } else {
                        float adjustmentAmount = 0.1f;

                        if(ModPlugin.MULTIPLY_RATING_LOSSES_BY_PERCENTAGE_OF_LOST_HULL && br.rating < rep.getRating()) {
                            adjustmentAmount *= br.fractionDamageTaken;
                        }

                        rc.newRating = rep.getAdjustedRating(br.rating, adjustmentAmount);
                        rc.ratingAdjustment = rc.newRating - rep.getRating();
                        adjustmentSign = Math.abs(rc.ratingAdjustment) > 0.01f ? (int)Math.signum(rc.ratingAdjustment) : 0;
                        bonusChance = 0.5f
                                + (rc.newRating - rep.getFractionOfBonusEffectFromTraits(1))
                                + (rc.newRating - rep.getFractionOfBonusEffectFromTraits(-1));
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
                    boolean traitAdjustmentSignMismatch = (traitIsBonus && adjustmentSign < 0)
                            || (!traitIsBonus && adjustmentSign >= 0);
                    double chanceToFlipTraitSignDueToMismatch =
                            Math.pow(1.5 - rep.getTraits().size() / (double)Trait.getTraitLimit() * 1.5, 3);

                    if(traitAdjustmentSignMismatch && rand.nextDouble() < chanceToFlipTraitSignDueToMismatch) {
                        traitIsBonus = !traitIsBonus;
                        traitAdjustmentSignMismatch = false;
                    }

                    msg += "SUCCEEDED"
                            + NEW_LINE + "Bonus Chance: " + (int)(bonusChance * 100) + "% - " + (traitIsBonus ? "SUCCEEDED" : "FAILED");

                    if((traitIsBonus || !ModPlugin.IGNORE_ALL_MALUSES) && !traitAdjustmentSignMismatch) {
                        rc.setTraitChange(RepRecord.chooseNewTrait(ship, rand, !traitIsBonus, rc.damageTakenFraction,
                                rc.damageDealtPercent, rc.disabled));
                    }
                } else if(participatedInBattle) {
                    msg += "FAILED";

                    float shuffleChance = Math.abs(bonusChance - 0.5f) * ModPlugin.TRAIT_POSITION_CHANGE_CHANCE_MULT;
                    boolean traitsWereShuffled = false;

                    if(shuffleChance > 0.05f && !ModPlugin.IGNORE_ALL_MALUSES) {
                        int sign = (int)Math.signum(bonusChance - 0.5f); // The goodness (not direction) of the shuffle

                        if(adjustmentSign == 0 || sign == adjustmentSign) {
                            msg += NEW_LINE + "Chance to Shuffle a Trait " + (sign < 0 ? "Worse" : "Better") + ": "
                                    + (int) (shuffleChance * 100) + "% - ";

                            Trait[] shuffleTraits = rep.chooseTraitsToShuffle(ship, sign, rc.newRating);

                            // If the absolute change in rating is less than 1, prevent removal of traits
                            if(shuffleTraits != null && shuffleTraits.length == 1 && adjustmentSign == 0) {
                                shuffleTraits = null;
                            }

                            if (rep.getTraits().size() <= 2) {
                                msg += "TOO FEW TRAITS";
                            } else if (shuffleTraits != null && rand.nextFloat() <= shuffleChance) {
                                rc.setTraitChange(shuffleTraits, sign);
                                traitsWereShuffled = true;
                                msg += "SUCCEEDED";
                            } else {
                                msg += "FAILED";
                            }
                        }
                    }

                    if(!traitsWereShuffled) {
                        for(int i = rep.getTraits().size() - 1; i >= 0; --i) {
                            Trait trait = rep.getTraits().get(i);

                            if(trait.getType().getTags().contains(TraitType.Tags.DMOD)
                                    && !trait.isRelevantFor(ship)) {

                                boolean isNewestTrait = i == rep.getTraits().size() - 1;
                                Trait[] shuffleTraits = isNewestTrait
                                        ? new Trait[] { trait }
                                        : new Trait[] { rep.getTraits().get(i + 1), trait };

                                rc.setTraitChange(shuffleTraits, isNewestTrait ? -trait.getEffectSign() : rep.getTraits().get(i + 1).getEffectSign());

                                break;
                            }
                        }
                    }
                }

                int loyaltyChange = 0;

                if (ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && rc.deployed && rc.captain != null
                        && !rc.captain.isDefault() && !rep.getTraits().isEmpty()) {

                    LoyaltyLevel ll = rep.getLoyalty(rc.captain);
                    float loyaltyAdjustChance = (br.rating - 0.5f) + rep.getLoyaltyBonus(rc.captain);
                    boolean success = rand.nextFloat() <= Math.abs(loyaltyAdjustChance);

                    if(ModPlugin.USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE
                            || Math.signum(loyaltyAdjustChance) == Math.signum(rc.ratingAdjustment)) {

                        if (loyaltyAdjustChance > 0.05f && !ll.isAtBest() && adjustmentSign >= 0
                                && rc.newRating >= ll.getRatingRequiredToImprove()) {

                            loyaltyAdjustChance *= ll.getBaseImproveChance() * ModPlugin.IMPROVE_LOYALTY_CHANCE_MULT;
                            msg += NEW_LINE + "Loyalty Increase Chance: " + (int) (loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                            if (success) loyaltyChange = 1;
                        } else if (loyaltyAdjustChance < -0.05f && !ll.isAtWorst() && adjustmentSign <= 0
                                && rc.damageTakenFraction >= ll.getDamageRequiredToWorsen()) {

                            loyaltyAdjustChance = Math.abs(loyaltyAdjustChance);
                            loyaltyAdjustChance *= ll.getBaseWorsenChance() * ModPlugin.WORSEN_LOYALTY_CHANCE_MULT;
                            msg += NEW_LINE + "Loyalty Reduction Chance: " + (int) (loyaltyAdjustChance * 100) + "% - " + (success ? "SUCCEEDED" : "FAILED");

                            if (success) loyaltyChange = -1;
                        }
                    }
                }

                rc.setLoyaltyChange(loyaltyChange);

                if(rc.deployed && ModPlugin.LOG_REPUTATION_CALCULATION_FACTORS) log(msg);

                if(!currentShipSet.contains(ship.getId())) {
                    rc.damageTakenFraction = Float.MAX_VALUE;
                    rc.captainOpinionChange = 0;
                    rc.trait = null;
                }

                report.addChange(rc);
                //rc.apply(false);
            }

            float excessRating = 0;

            for(RepChange rc : report.changes) {
                excessRating += Math.max(0, rc.newRating - 1.004f);
                rc.newRating = Math.min(1.004f, rc.newRating);
            }
            // Don't merge these loops
            for(RepChange rc : report.changes) {
                if(rc.deployed) {
                    rc.newRating += excessRating / (float) deployedShips.size();
                }

                rc.newRating *= 100;
                rc.ratingAdjustment *= 100;
                rc.apply(false);
            }

            if(report.changes.size() > 0) Global.getSector().getIntelManager().addIntel(report);

            for(IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(FamousDerelictIntel.class)) {
                ((FamousDerelictIntel)i).checkIfPlayerRecoveredDerelict();
            }

            if(battle != null && battle.getSnapshotBothSides() != null) {
                for(CampaignFleetAPI fleet : battle.getSnapshotBothSides()) {
                    for (FleetMemberAPI enemy : fleet.getFleetData().getMembersListCopy()) {
                        enemy.getVariant().removePermaMod(Reputation.ENEMY_HULLMOD_ID);
                    }
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        reset();
    }

    @Override
    public void reportShownInteractionDialog(InteractionDialogAPI dialog) {
        try {
            if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES || dialog == null || dialog.getPlugin() == null
                    || !(dialog.getPlugin() instanceof FleetInteractionDialogPluginImpl))
                return;

            isResetNeeded = true;

            FleetInteractionDialogPluginImpl plugin = (FleetInteractionDialogPluginImpl) dialog.getPlugin();

            if (!(plugin.getContext() instanceof FleetEncounterContext)) return;
            context = (FleetEncounterContext) plugin.getContext();
            CampaignFleetAPI combined = context.getBattle() == null ? null : context.getBattle().getNonPlayerCombined();

            if (combined == null) return;
            PersonAPI commander = Util.getHighestLevelEnemyCommanderInBattle(context.getBattle().getNonPlayerSide());

            FactionConfig fc  = FactionConfig.get(combined.getFaction());

            if(fc != null) {
                fc.showFleetReputation(dialog, commander);

                for(FleetMemberAPI ship : combined.getFleetData().getMembersListCopy()) {
                    ship.getVariant().addPermaMod(Reputation.ENEMY_HULLMOD_ID);
                }
            }

            for(IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(FamousFlagshipIntel.class)) {
                ((FamousFlagshipIntel)i).applyHullmodsToShip();
            }
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }

    @Override
    public void reportPlayerEngagement(EngagementResultAPI result) {
        try {
            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

            EngagementResultForFleetAPI pf = result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();
            EngagementResultForFleetAPI ef = !result.didPlayerWin()
                    ? result.getWinnerResult()
                    : result.getLoserResult();

            if(pf.getGoal() == FleetGoal.ESCAPE || fpWorthOfDamageDealtDuringEngagement > 0) {
                damageOnlyDealtViaNukeCommand = false;
            }

            disabledShips.addAll(pf.getDisabled());
            disabledShips.addAll(pf.getDestroyed());

            deployedShips.addAll(pf.getDeployed());
            deployedShips.addAll(pf.getRetreated());
            deployedShips.addAll(disabledShips);

            for(FleetMemberAPI ship : ef.getDestroyed()) {
                destroyedEnemies.add(Global.getFactory().createFleetMember(FleetMemberType.SHIP, ship.getVariant()));

                if(RepRecord.existsFor(ship)) famousRecoverableShip = ship;
            }

            for(FleetMemberAPI ship : ef.getDisabled()) {
                destroyedEnemies.add(Global.getFactory().createFleetMember(FleetMemberType.SHIP, ship.getVariant()));

                if(RepRecord.existsFor(ship)) famousRecoverableShip = ship;
            }

            if(famousRecoverableShip != null) {
                Misc.makeUnimportant(ef.getFleet(), "sun_sl_famous_derelict");
            }

            routedEnemies.clear();
            for(FleetMemberAPI ship : ef.getRetreated()) {
                routedEnemies.add(Global.getFactory().createFleetMember(FleetMemberType.SHIP, ship.getVariant()));
            }

            previousXP = Global.getSector().getPlayerStats().getXP();

            List<FleetMemberAPI> shipsDeployed = new ArrayList<>();
            shipsDeployed.addAll(pf.getDeployed());
            shipsDeployed.addAll(pf.getDisabled());
            shipsDeployed.addAll(pf.getRetreated());
            shipsDeployed.addAll(pf.getDestroyed());

            float deployedFP = 0;

            for(FleetMemberAPI fm : shipsDeployed) {
                float fp = Util.getShipStrength(fm);
                playerDeployedFP.put(fm, fp);
                deployedFP += fp;
            }

            if(deployedFP > 0) {
                float contribution = fpWorthOfDamageDealtDuringEngagement / deployedFP;

                for (FleetMemberAPI fm : shipsDeployed) {
                    recordSupportContribution(fm.getId(), contribution);
                }
            }

            fpWorthOfDamageDealtDuringEngagement = 0;

            for(FleetMemberAPI fm : ef.getDeployed()) enemyDeployedFP.put(fm, Util.getShipStrength(fm));
            for(FleetMemberAPI fm : ef.getRetreated()) enemyDeployedFP.put(fm, Util.getShipStrength(fm));
            for(FleetMemberAPI fm : ef.getDisabled()) enemyDeployedFP.put(fm, Util.getShipStrength(fm));
            for(FleetMemberAPI fm : ef.getDestroyed()) enemyDeployedFP.put(fm, Util.getShipStrength(fm));
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void reportPlayerClosedMarket(MarketAPI market) {
        if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

        for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            RepRecord.updateRepHullMod(ship);
        }
    }

    @Override
    public void reportEconomyTick(int iterIndex) {
        try {
            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

            int day = (int)Global.getSector().getClock().getElapsedDaysSince(INITIAL_TIMESTAMP);

            for (FleetMemberAPI ship : new LinkedList<>(Reputation.getShipsOfNote())) {
                if (ship.isMothballed() && RepRecord.existsFor(ship)) {
                    if (!dayMothballed.val.containsKey(ship.getId())) {
                        log("Mothballed: " + ship.getHullId());
                        dayMothballed.val.put(ship.getId(), day);
                    } else if (day > (dayMothballed.val.get(ship.getId()) + ModPlugin.DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION * RepRecord.get(ship).getTraits().size())) {
                        log("Reputation removed: " + ship.getHullId());
                        dayMothballed.val.remove(ship.getId());
                        RepRecord.deleteFor(ship);
                    }

                    if(dayMothballed.val.containsKey(ship.getId())) {
                        log(ship.getHullId() + " " + ship.getId() + " has " + ((dayMothballed.val.get(ship.getId()) + ModPlugin.DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION * RepRecord.get(ship).getTraits().size()) - day) + " days left before its reputation is cleared.");
                    }
                } else if (dayMothballed.val.containsKey(ship.getId())) {
                    log("No longer mothballed: " + ship.getHullId());
                    dayMothballed.val.remove(ship.getId());
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void reportEconomyMonthEnd() {
        FamousShipBarEvent.clearPreviousMonthsClaimedFleets();
    }

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() {
        return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES
                || !ModPlugin.settingsAreRead
                || famousRecoverableShip != null;
    }

    @Override
    public void advance(float amount) {
        try {
            if (!ModPlugin.readSettingsIfNecessary()) return;

            if(context != null && famousRecoverableShip != null && context.didPlayerWinEncounterOutright()) {
                Set<FleetMemberAPI> recoverable = new HashSet<>();
                // Below seems to give unreliable results, changing the outcome of previous decisions, resulting in jank
                BattleAPI b = context.getBattle();
                recoverable.addAll(context.getRecoverableShips(b, b.getPlayerCombined(), b.getNonPlayerCombined()));
                recoverable.addAll(context.getStoryRecoverableShips());

                if(!recoverable.contains(famousRecoverableShip)) {
                    context.getStoryRecoverableShips().add(famousRecoverableShip);
                }

                return; // To prevent early reset, which would prematurely force famousRecoverableShip to be added to player fleet
            }

            if(isResetNeeded) reset();

            if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) {
                Util.clearAllStarshipLegendsData();
                return;
            }



            long xp = Global.getSector().getPlayerStats().getXP();

            if(previousXP == Long.MAX_VALUE) {
                previousXP = xp;
            } else if(previousXP < xp) {
                // Don't change the order of these two lines
                long dXp = Math.min(10000, Global.getSector().getPlayerStats().getXP() - previousXP);
                growRepForPeacefulXp(dXp);
                previousXP = xp;
            }

            if(!Global.getSector().isPaused() && pendingRepChanges.val.size() > 0 && (timeUntilNextChange.val -= amount) <= 0) {
                int i = rand.nextInt(pendingRepChanges.val.size());

                if(pendingRepChanges.val.get(i).apply(ModPlugin.SHOW_NEW_TRAIT_NOTIFICATIONS)) {
                    timeUntilNextChange.val = Math.max(1, AVG_TIME_BETWEEN_REP_CHANGES * (0.5f + rand.nextFloat())
                            * (3f / Math.max(pendingRepChanges.val.size(), 3f)));
                }

                pendingRepChanges.val.remove(i);
            } else if(pendingRepChanges.val.isEmpty()) timeUntilNextChange.val = 0f;

            for(IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(FamousDerelictIntel.class)) {
                ((FamousDerelictIntel)i).updateFleetActions();
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
