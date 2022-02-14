package starship_legends;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import com.fs.starfarer.api.impl.campaign.FleetInteractionDialogPluginImpl;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.plugins.LevelupPlugin;
import com.fs.starfarer.api.util.Misc;
import starship_legends.events.FamousDerelictIntel;
import starship_legends.events.FamousFlagshipIntel;
import starship_legends.events.FamousShipBarEvent;
import starship_legends.events.RepSuggestionPopupEvent;
import starship_legends.hullmods.Reputation;

import java.util.*;

public class CampaignScript extends BaseCampaignEventListener implements EveryFrameScript {
    static class BattleRecord {
        PersonAPI originalCaptain = null;
        float fractionDamageTaken = 0, damageDealt = 0;
    }

    public CampaignScript() { super(true); }

    Saved<Long> timestampOfNextTraitSuggestion = new Saved<>("tsOfNextJealousy", Long.MAX_VALUE);

    static boolean isResetNeeded = false, damageOnlyDealtViaNukeCommand = true, checkForRecoveredDerelicts = false;
    static long previousXP = Long.MAX_VALUE;
    static Set<FleetMemberAPI>
            deployedShips = new HashSet<>(),
            disabledShips = new HashSet<>(),
            shipsDeployedInProperBattle = new HashSet<>(),
            destroyedEnemies = new HashSet<>(),
            routedEnemies = new HashSet<>();
    static FleetEncounterContext context = null;
    static TextPanelAPI textPanel = null;

    static Map<String, BattleRecord> battleRecords = new HashMap<>();
    static Set<FleetMemberAPI> originalShipList;
    static FleetMemberAPI famousRecoverableShip = null;

    public static BattleRecord getBattleRecord(String shipID) {
        if(battleRecords.containsKey(shipID)) {
            return battleRecords.get(shipID);
        } else {
            BattleRecord newBR = new BattleRecord();
            battleRecords.put(shipID, newBR);
            return newBR;
        }
    }

    public static void recordDamageDealt(String shipID, float fpWorthOfDamage) {
        if(fpWorthOfDamage > 0) {
            damageOnlyDealtViaNukeCommand = false;
            getBattleRecord(shipID).damageDealt += fpWorthOfDamage;
        }
    }
    public static void recordDamageSustained(String shipID, float damageSustained) {
        if(damageSustained > 0) getBattleRecord(shipID).fractionDamageTaken += damageSustained;

    }
    public static void collectRealSnapshotInfoIfNeeded() {
        if(Global.getSector() == null || Global.getSector().getPlayerFleet() == null) return;

        if(originalShipList == null) {
            originalShipList = new HashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

            for (FleetMemberAPI ship : originalShipList) {
                getBattleRecord(ship.getId()).originalCaptain = ship.getCaptain();
            }
        }
    }
    public static long getReAdjustedXp() {
        long xpEarned = Global.getSector().getPlayerStats().getXP() - previousXP;

        // At max level, xp resets at each "level up" to the amount needed at max level, so...
        if(xpEarned < 0) {
            LevelupPlugin lup = Global.getSettings().getLevelupPlugin();
            long xpPerRollover = lup.getXPForLevel(lup.getMaxLevel() + 1) - lup.getXPForLevel(lup.getMaxLevel());
            xpEarned += xpPerRollover;
        }

        if (ModPlugin.COMPENSATE_FOR_EXPERIENCE_MULT) xpEarned /= Global.getSettings().getFloat("xpGainMult");

        return xpEarned;
    }
    public static void growRepForPeacefulXp(long xp) {
        for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            RepChange rc = new RepChange(ship);
            RepRecord rep = RepRecord.getOrCreate(ship);

            float shipXp = xp * (ship.getHullSpec().isCivilianNonCarrier()
                    ? ModPlugin.PEACEFUL_XP_MULT_FOR_CIVILIAN_SHIPS
                    : ModPlugin.PEACEFUL_XP_MULT_FOR_COMBAT_SHIPS);

            rep.progress(shipXp, rc);

            if(rc.hasAnyChanges()) rc.apply(true);
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

            textPanel = null;
            context = null;
            famousRecoverableShip = null;
            damageOnlyDealtViaNukeCommand = true;
            checkForRecoveredDerelicts = false;
            battleRecords.clear();
            originalShipList = null;
            deployedShips.clear();
            shipsDeployedInProperBattle.clear();
            disabledShips.clear();
            disabledShips.clear();
            destroyedEnemies.clear();
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

    @Override
    public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
        try {
            if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES || !battle.isPlayerInvolved() || originalShipList == null) return;

            long xpEarned = getReAdjustedXp();
            boolean isWin = battle == null || primaryWinner == null || battle.getPlayerSide() == null ? null
                    : battle.getPlayerSide().contains(primaryWinner);
            BattleReport report = new BattleReport(battle, destroyedEnemies, routedEnemies, isWin);
            Set<String> currentShipSet = new HashSet<>();

            for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                currentShipSet.add(ship.getId());

                if(!originalShipList.contains(ship)) {
                    RepRecord.setShipOrigin(ship, RepRecord.Origin.Type.BattleSalvage);
                }
            }

            for (FleetMemberAPI ship : originalShipList) {
                if (!battleRecords.containsKey(ship.getId())
                        || ship.isMothballed()
                        || !currentShipSet.contains(ship.getId())) {

                    continue;
                }

                BattleRecord br = getBattleRecord(ship.getId());
                br.damageDealt = Math.max(0, br.damageDealt) / Math.max(1, Util.getShipStrength(ship, true));
                RepRecord rep = RepRecord.getOrCreate(ship);
                RepChange rc = new RepChange(ship, br, deployedShips.contains(ship), disabledShips.contains(ship),
                        shipsDeployedInProperBattle.contains(ship));
                float xpForShip = xpEarned;


                if(rc.foughtInBattle) {
                    float xpMultPerCaptainLevel = br.originalCaptain.isPlayer()
                            ? ModPlugin.XP_MULT_PER_PLAYER_CAPTAIN_LEVEL
                            : ModPlugin.XP_MULT_PER_NON_PLAYER_CAPTAIN_LEVEL;

                    xpForShip *= ModPlugin.XP_MULT_FLAT
                            + ModPlugin.XP_MULT_PER_FLEET_POINT * ship.getHullSpec().getFleetPoints()
                            + ModPlugin.XP_MULT_PER_DAMAGE_DEALT_PERCENT * br.damageDealt * 100f
                            + xpMultPerCaptainLevel * br.originalCaptain.getStats().getLevel();
                } else {
                    xpForShip *= ship.getHullSpec().isCivilianNonCarrier()
                            ? ModPlugin.XP_MULT_FOR_RESERVED_CIVILIAN_SHIPS
                            : ModPlugin.XP_MULT_FOR_RESERVED_COMBAT_SHIPS;
                }

                rep.progress(xpForShip, rc);
                rep.getStory().update(rc, report.getEnemyFaction());
                report.addChange(rc);
                rc.apply(false);
            }

            if(report.changes.size() > 0) {
                report.sortChanges();
                Global.getSector().getIntelManager().addIntel(report);
            }

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
            if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES || dialog == null || dialog.getPlugin() == null)
                return;

            textPanel = dialog.getTextPanel();
            isResetNeeded = true;

            if(dialog.getPlugin() instanceof FleetInteractionDialogPluginImpl) {

                FleetInteractionDialogPluginImpl plugin = (FleetInteractionDialogPluginImpl) dialog.getPlugin();

                if (!(plugin.getContext() instanceof FleetEncounterContext)) return;
                context = (FleetEncounterContext) plugin.getContext();
                CampaignFleetAPI combined = context.getBattle() == null ? null : context.getBattle().getNonPlayerCombined();

                if (combined == null) return;
                PersonAPI commander = Util.getHighestLevelEnemyCommanderInBattle(context.getBattle().getNonPlayerSide());

                FactionConfig fc = FactionConfig.get(combined.getFaction());

                if (fc != null) {
                    fc.showFleetReputation(dialog, commander);

                    for (FleetMemberAPI ship : combined.getFleetData().getMembersListCopy()) {
                        ship.getVariant().addPermaMod(Reputation.ENEMY_HULLMOD_ID);
                    }
                }

                for (IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(FamousFlagshipIntel.class)) {
                    ((FamousFlagshipIntel) i).applyHullmodsToShip();
                }
            } else if(dialog.getInteractionTarget() != null && dialog.getInteractionTarget().getMemoryWithoutUpdate().getString("$customType") == "wreck") {
                checkForRecoveredDerelicts = true;
                originalShipList = new HashSet<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());
                isResetNeeded = true;
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

            disabledShips.addAll(pf.getDisabled());
            disabledShips.addAll(pf.getDestroyed());

            deployedShips.addAll(pf.getDeployed());
            deployedShips.addAll(pf.getRetreated());
            deployedShips.addAll(disabledShips);

            if(ef.getGoal() != FleetGoal.ESCAPE) {
                shipsDeployedInProperBattle.addAll(deployedShips);
            }

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
    public void reportEconomyMonthEnd() {
        FamousShipBarEvent.clearPreviousMonthsClaimedFleets();
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        try {
            super.reportPlayerMarketTransaction(transaction);

            RepRecord.Origin.Type marketType = RepRecord.Origin.Type.OtherMarket;

            switch (transaction.getSubmarket().getSpecId()) {
                case "open_market": marketType = RepRecord.Origin.Type.OpenMarket; break;
                case "black_market": marketType = RepRecord.Origin.Type.BlackMarket; break;
                case "generic_military": marketType = RepRecord.Origin.Type.MilitaryMarket; break;
                case "storage": case "local_resources": marketType = RepRecord.Origin.Type.Manufactured; break;
            }

            if(transaction.getSubmarket().getSpecId().equals("storage")) {
                for (PlayerMarketTransaction.ShipSaleInfo info : transaction.getShipsSold()) {
                    RepRecord.setShipOrigin(info.getMember(), RepRecord.Origin.Type.Unknown, "");
                }
            }

            for(PlayerMarketTransaction.ShipSaleInfo info : transaction.getShipsBought()) {
                RepRecord.setShipOrigin(info.getMember(), marketType, transaction.getMarket().getName());
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public boolean isDone() { return false; }

    @Override
    public boolean runWhilePaused() {
        return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES
                || !ModPlugin.settingsAreRead
                || famousRecoverableShip != null
                || !Reputation.moduleMap.isEmpty();
    }

    @Override
    public void advance(float amount) {
        try {
            if(Global.getSector().getCampaignUI().getCurrentCoreTab() != CoreUITabId.REFIT) Reputation.moduleMap.clear();

            if (!ModPlugin.readSettingsIfNecessary(false)) return;

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

            if(isResetNeeded && !Global.getSector().getCampaignUI().isShowingDialog()) {
                if(checkForRecoveredDerelicts && originalShipList != null && !ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) {
                    for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                        if (!originalShipList.contains(ship)) {
                            RepRecord.setShipOrigin(ship, RepRecord.Origin.Type.Derelict);
                        }
                    }
                }

                reset();
            }

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

            for(IntelInfoPlugin i : Global.getSector().getIntelManager().getIntel(FamousDerelictIntel.class)) {
                ((FamousDerelictIntel)i).updateFleetActions();
            }

            if(!Global.getSector().isPaused()) {
                if(ModPlugin.AVERAGE_DAYS_BETWEEN_TRAIT_SIDEGRADE_SUGGESTIONS > 0) {
                    long ts = Global.getSector().getClock().getTimestamp();
                    boolean incrementTS = false;

                    if(ts >= timestampOfNextTraitSuggestion.val) {
                        float traitsInFleet = 0;
                        float minTraitsToGuaranteeEvent = 50;
                        boolean suggestionAlreadyExists = RepSuggestionPopupEvent.getActiveSuggestion() != null;
                        Random rand = new Random(ts);

                        for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                            traitsInFleet += RepRecord.isShipNotable(ship) ? RepRecord.get(ship).getTraits().size() : 0;
                        }

                        if(!suggestionAlreadyExists && traitsInFleet / minTraitsToGuaranteeEvent > rand.nextFloat()) {
                            RepSuggestionPopupEvent intel = new RepSuggestionPopupEvent();

                            if (intel.isValid()) Global.getSector().getIntelManager().addIntel(intel);
                        }

                        incrementTS = true;
                    } else if(timestampOfNextTraitSuggestion.val.equals(Long.MAX_VALUE)) {
                        incrementTS = true;
                    }

                    if(incrementTS) {
                        Random rand = new Random(ts);
                        timestampOfNextTraitSuggestion.val = ts + (long)(ModPlugin.TIMESTAMP_TICKS_PER_DAY
                                * (1 + ModPlugin.AVERAGE_DAYS_BETWEEN_TRAIT_SIDEGRADE_SUGGESTIONS)
                                * (rand.nextDouble() + 0.5));
                    }
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
}
