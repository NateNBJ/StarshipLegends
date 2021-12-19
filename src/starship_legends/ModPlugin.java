package starship_legends;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.thoughtworks.xstream.XStream;
import org.json.JSONArray;
import org.json.JSONObject;
import starship_legends.events.FamousDerelictIntel;
import starship_legends.events.FamousShipBarEventCreator;
import starship_legends.hullmods.Reputation;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ModPlugin extends BaseModPlugin {
    public static final String ID = "sun_starship_legends";
    public static final String TRAIT_LIST_PATH = "sun_sl/data/traits.csv";
    public static final String LOYALTY_LEVEL_LIST_PATH = "sun_sl/data/loyalty_levels.csv";
    public static final String HULL_REGEN_SHIPS_PATH = "data/config/starship_legends/hull_regen_ships.csv";
    public static final String SETTINGS_PATH = "STARSHIP_LEGENDS_OPTIONS.ini";
    public static final int TIER_COUNT = 4;
    public static final int LOYALTY_LIMIT = 3;
    public static final int DEFAULT_TRAIT_LIMIT = 8;

    static Saved<String> version = new Saved<>("version", "");

    static boolean settingsAreRead = false;

    public static final Map<String, Float> HULL_REGEN_SHIPS = new HashMap<>();


    public static boolean
            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = true,
            USE_RUTHLESS_SECTOR_TO_CALCULATE_SHIP_STRENGTH = true,
            ENABLE_OFFICER_LOYALTY_SYSTEM = true,
            LOG_REPUTATION_CALCULATION_FACTORS = true,
            COMPENSATE_FOR_EXPERIENCE_MULT = true,
            USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE = false,
            SHOW_COMBAT_RATINGS = true,
            IGNORE_ALL_MALUSES = false,
            REMOVE_ALL_DATA_AND_FEATURES = false,
            SHOW_NEW_TRAIT_NOTIFICATIONS = true,
            ALLOW_CUSTOM_COMMANDER_PRESETS = true,
            FAMOUS_DERELICT_MAY_BE_GUARDED_BY_REMNANT_FLEET = false,
            MULTIPLY_RATING_LOSSES_BY_PERCENTAGE_OF_LOST_HULL = true,
            CONSIDER_NORMAL_HULLMODS_FOR_TRAIT_COMPATIBILITY = false,
            USE_ADVANCED_SHIP_STRENGTH_ESTIMATION = false;

    public static int
            MINIMUM_EFFECT_REDUCTION_PERCENT = -95,
            TRAITS_PER_TIER = 2,
            DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION = 30,
            TRAITS_FOR_FLEETS_WITH_NO_COMMANDER = 0,
            TRAITS_FOR_FLEETS_WITH_MIN_LEVEL_COMMANDER = 1,
            TRAITS_FOR_FLEETS_WITH_MAX_LEVEL_COMMANDER = 5;

    public static float
            GLOBAL_EFFECT_MULT = 1,
            FLEET_TRAIT_EFFECT_MULT = 2,
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = 0.02f,
            BONUS_CHANCE_FOR_CIVILIAN_SHIPS = 0.5f,

            BASE_RATING = 0.5f,
            BONUS_CHANCE_RANDOMNESS = 0.001f,
            BATTLE_DIFFICULTY_MULT = 0.0f,
            SUPPORT_MULT = 0.125f,
            DAMAGE_TAKEN_MULT = 0.5f,
            DAMAGE_DEALT_MULT = 0.125f,
            DAMAGE_DEALT_MIN_THRESHOLD = 0.0f,
            BONUS_CHANCE_FOR_RESERVED_SHIPS_MULT = 1.0f,
            TRAIT_POSITION_CHANGE_CHANCE_MULT = 5.0f,
            CHANCE_TO_IGNORE_LOGISTICS_TRAITS_ON_COMBAT_SHIPS = 0.75f,
            CHANCE_TO_IGNORE_COMBAT_TRAITS_ON_CIVILIAN_SHIPS = 0.75f,

            DMOD_FACTOR_FOR_ENEMY_SHIPS = 0.1f,
            SMOD_FACTOR_FOR_ENEMY_SHIPS = 0.1f,
            SKILL_FACTOR_FOR_ENEMY_SHIPS = 0.1f,
            DMOD_FACTOR_FOR_PLAYER_SHIPS = 0.0f,
            SMOD_FACTOR_FOR_PLAYER_SHIPS = 0.0f,
            SKILL_FACTOR_FOR_PLAYER_SHIPS = 0.0f,
            STRENGTH_INCREASE_PER_PLAYER_LEVEL = 0.07f,

            TRAIT_CHANCE_MULT_FLAT = 0.1f,
            TRAIT_CHANCE_MULT_PER_PLAYER_CAPTAIN_LEVEL = 0.005f,
            TRAIT_CHANCE_MULT_PER_NON_PLAYER_CAPTAIN_LEVEL = 0.01f,
            TRAIT_CHANCE_MULT_PER_FLEET_POINT = 0.0075f,
            TRAIT_CHANCE_MULT_PER_DAMAGE_TAKEN_PERCENT = 0.01f,
            TRAIT_CHANCE_MULT_PER_DAMAGE_DEALT_PERCENT = 0.005f,

            TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS = 0.0f,
            TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS = 0.0f,

            TRAIT_CHANCE_MULT_FOR_COMBAT_SHIPS = 0,
            TRAIT_CHANCE_MULT_FOR_CIVILIAN_SHIPS = 0,

            FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE = 2f,
            FAMOUS_DERELICT_BAR_EVENT_CHANCE = 0.5f,
            ANY_FAMOUS_SHIP_BAR_EVENT_CHANCE_MULT = 2.5f,

            IMPROVE_LOYALTY_CHANCE_MULT = 1,
            WORSEN_LOYALTY_CHANCE_MULT = 1,
            AVERAGE_FRACTION_OF_GOOD_TRAITS = 1;

    CampaignScript script;

    static void log(String message) { if(true) Global.getLogger(ModPlugin.class).info(message); }

    static class Version {
        public final int MAJOR, MINOR, PATCH, RC;

        public Version(String versionStr) {
            String[] temp = versionStr.replace("Starsector ", "").replace("a", "").split("-RC");

            RC = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;

            temp = temp[0].split("\\.");

            MAJOR = temp.length > 0 ? Integer.parseInt(temp[0]) : 0;
            MINOR = temp.length > 1 ? Integer.parseInt(temp[1]) : 0;
            PATCH = temp.length > 2 ? Integer.parseInt(temp[2]) : 0;
        }

        public boolean isOlderThan(Version other, boolean ignoreRC) {
            if(MAJOR < other.MAJOR) return true;
            if(MINOR < other.MINOR) return true;
            if(PATCH < other.PATCH) return true;
            if(!ignoreRC && !other.isOlderThan(this, true) && RC < other.RC) return true;

            return false;
        }

        @Override
        public String toString() {
            return String.format("%d.%d.%d%s-RC%d", MAJOR, MINOR, PATCH, (MAJOR >= 1 ? "" : "a"), RC);
        }
    }

    @Override
    public void onApplicationLoad() throws Exception {
        String message = "";

        try {
            ModSpecAPI spec = Global.getSettings().getModManager().getModSpec("sun_starship_legends");
            Version minimumVersion = new Version(spec.getGameVersion());
            Version currentVersion = new Version(Global.getSettings().getVersionString());

            if(currentVersion.isOlderThan(minimumVersion, false)) {
                message = String.format("\rThis version of Starsector is too old for %s!" +
                                "\rPlease make sure Starsector is up to date. (http://fractalsoftworks.com/preorder/)" +
                                "\rMinimum Version: %s" +
                                "\rCurrent Version: %s",
                        spec.getName(), minimumVersion, currentVersion);
            }
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Version comparison failed.", e);
        }

        if(!message.isEmpty()) throw new Exception(message);
    }

    @Override
    public void beforeGameSave() {
        Saved.updatePersistentData();
        Global.getSector().removeTransientScript(script);
        Global.getSector().removeListener(script);
        Util.removeRepHullmodFromAutoFitGoalVariants();
    }

    @Override
    public void afterGameSave() {
        Global.getSector().addTransientScript(script = new CampaignScript());

        Saved.loadPersistentData(); // Because script attributes will be reset
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            Reputation.moduleMap.clear();

            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();
            CampaignScript.reset();
            FactionConfig.clearEnemyFleetRep();

            readSettingsIfNecessary();

            if (REMOVE_ALL_DATA_AND_FEATURES) {
                Util.clearAllStarshipLegendsData();
            } else {
                BarEventManager bar = BarEventManager.getInstance();

                if (!bar.hasEventCreator(FamousShipBarEventCreator.class)) {
                    bar.addEventCreator(new FamousShipBarEventCreator());
                }

                for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    RepRecord.updateRepHullMod(ship);
                }

                if(settingsHaveBeenRead()) {
                    for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                        Reputation.applyEffects(ship);
                    }

                    Global.getSector().getPlayerFleet().getFleetData().updateCargoCapacities();
                }
            }

            boolean allRepRecordsHaveNoRating = true;

            if(isUpdateDiagnosticCheckNeeded()) {
                String oldVersion = version.val;
                version.val = Global.getSettings().getModManager().getModSpec("sun_starship_legends").getVersion();

                log("Starship Legends version updated from " + oldVersion + " to " + version.val);
                log("Performing update diagnostics...");

                // Remove any enemy rep hullmods from player ships
                try {
                    for (FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                        ship.getVariant().removePermaMod(Reputation.ENEMY_HULLMOD_ID);
                    }
                } catch (Exception e) {
                    log("An error occurred while removing enemy rep hullmods from player ships!");
                }

                // Remove irrelevant traits from existing notable ships
                try {
                    for (FleetMemberAPI ship : Reputation.getShipsOfNote()) {
                        if(!RepRecord.existsFor(ship)) continue;

                        RepRecord rep = RepRecord.get(ship);
                        List<Trait> traitsToRemove = new LinkedList<>();

                        for(Trait t : rep.getTraits()) {
                            if(!t.isRelevantFor(ship)) {
                                traitsToRemove.add(t);
                            }
                        }

                        for(Trait t : traitsToRemove) {
                            log("Removing " + t.getName(true) + " from the " + ship.getShipName() + " (" + ship.getHullId() + ")");
                            rep.getTraits().remove(t);
                        }
                    }
                } catch (Exception e) {
                    log("An error occurred while removing irrelevant traits from existing notable ships!");
                }

                // Remove bugged famous derelicts from the sector
                try {
                    List<SectorEntityToken> buggedDerelictsToRemove = new ArrayList<>();

                    for(StarSystemAPI system : Global.getSector().getStarSystems()) {
                        for(SectorEntityToken entity : system.getAllEntities()) {
                            MemoryAPI memory = entity.getMemoryWithoutUpdate();

                            if(memory.contains("$sun_sl_customType") && !memory.contains(FamousDerelictIntel.MEMORY_KEY)) {
                                buggedDerelictsToRemove.add(entity);
                            }
                        }
                    }

                    for(SectorEntityToken token : buggedDerelictsToRemove) {
                        token.getContainingLocation().removeEntity(token);
                    }

                    if(!buggedDerelictsToRemove.isEmpty()) {
                        log("Removed " + buggedDerelictsToRemove.size() + " bugged derelicts");
                    }
                } catch (Exception e) {
                    log("An error occurred while removing bugged famous derelicts from the sector!");
                }

                // Remove existing duplicate traits
                try {
                    for (RepRecord rep : RepRecord.INSTANCE_REGISTRY.val.values()) {
                        Set<TraitType> found = new HashSet<>();

                        if (rep.getRating() != 0) allRepRecordsHaveNoRating = false;

                        for (Trait t : new ArrayList<>(rep.getTraits())) {
                            if (found.contains(t.getType()) || t.getName(true).isEmpty()) {
                                rep.getTraits().remove(t);
                            } else found.add(t.getType());
                        }
                    }
                } catch (Exception e) {
                    log("An error occurred while removing duplicate traits!");
                }

                // If no ships have ratings, estimate them
                try {
                    if (allRepRecordsHaveNoRating) {
                        for (FleetMemberAPI ship : Reputation.getShipsOfNote()) {
                            if (!ship.getHullSpec().isCivilianNonCarrier() && RepRecord.existsFor(ship)) {
                                RepRecord rep = RepRecord.get(ship);
                                float progress = rep.getTraits().size() / (float) Trait.getTraitLimit();

                                rep.setRating(RepRecord.INITIAL_RATING * (1f - progress)
                                        + rep.getFractionOfBonusEffectFromTraits() * progress);
                            }
                        }
                    }
                } catch (Exception e) {
                    log("An error occurred while estimating ratings!");
                }

                // Remove RepRecords with no traits
                try {
                    for (FleetMemberAPI ship : new LinkedList<>(Reputation.getShipsOfNote())) {
                        if (!RepRecord.existsFor(ship) || RepRecord.get(ship).getTraits().isEmpty()) {
                            RepRecord.deleteFor(ship);
                        }
                    }
                } catch (Exception e) {
                    log("An error occurred while removing RepRecords without traits!");
                }
            }
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void configureXStream(XStream x) {
        x.alias("sun_sl_rr", RepRecord.class);
        x.aliasAttribute(RepRecord.class, "traits", "t");
        x.aliasAttribute(RepRecord.class, "opinionsOfOfficers", "o");
        x.aliasAttribute(RepRecord.class, "rating", "r");

        x.alias("sun_sl_t", Trait.class);
        x.aliasAttribute(Trait.class, "typeID", "t");
        x.aliasAttribute(Trait.class, "effectSign", "e");

        x.alias("sun_sl_rc", RepChange.class);
        x.aliasAttribute(RepChange.class, "ship", "s");
        x.aliasAttribute(RepChange.class, "captain", "c");
        x.aliasAttribute(RepChange.class, "trait", "t");
        x.aliasAttribute(RepChange.class, "captainOpinionChange", "o");
        x.aliasAttribute(RepChange.class, "shuffleSign", "d");
    }
    
    public static boolean isUpdateDiagnosticCheckNeeded() {
        if(version.val == null || version.val.equals("")) return true;

        Version lastSavedVersion = new Version(version.val);
        Version currentVersion = new Version(Global.getSettings().getModManager().getModSpec("sun_starship_legends").getVersion());

        return lastSavedVersion.isOlderThan(currentVersion, true);
    }

    public static boolean settingsHaveBeenRead() { return settingsAreRead; }

    public static boolean readSettingsIfNecessary() {
        try {
            if(settingsAreRead) return true;

            JSONArray jsonArray = Global.getSettings().loadCSV(TRAIT_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) new TraitType(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().loadCSV(LOYALTY_LEVEL_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) LoyaltyLevel.values()[i].init(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("hull_id", HULL_REGEN_SHIPS_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject data = jsonArray.getJSONObject(i);
                HULL_REGEN_SHIPS.put(data.getString("hull_id"), (float)data.getDouble("damage_counted_per_damage_sustained"));
            }

            JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            FactionConfig.readDerelictChanceMultipliers(cfg.getJSONArray("famousDerelictChanceMultipliersByShipStrength"));

            if(FactionConfig.hasNotBeenRead()) {
                for (FactionAPI faction : Global.getSector().getAllFactions()) {
                    new FactionConfig(faction);
                }
            }

            Trait.Tier.Notable.init(cfg);
            Trait.Tier.Wellknown.init(cfg);
            Trait.Tier.Famous.init(cfg);
            Trait.Tier.Legendary.init(cfg);

            REMOVE_ALL_DATA_AND_FEATURES = cfg.getBoolean("removeAllDataAndFeatures");

            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = cfg.getBoolean("useRuthlessSectorToCalculateBattleDifficulty");
            USE_RUTHLESS_SECTOR_TO_CALCULATE_SHIP_STRENGTH = cfg.getBoolean("useRuthlessSectorToCalculateShipStrength");
            ENABLE_OFFICER_LOYALTY_SYSTEM = cfg.getBoolean("enableOfficerLoyaltySystem");
            LOG_REPUTATION_CALCULATION_FACTORS = cfg.getBoolean("logReputationCalculationFactors");
            COMPENSATE_FOR_EXPERIENCE_MULT = cfg.getBoolean("compensateForExperienceMult");
            IGNORE_ALL_MALUSES = cfg.getBoolean("ignoreAllMaluses");
            SHOW_COMBAT_RATINGS = cfg.getBoolean("showCombatRatings");
            SHOW_NEW_TRAIT_NOTIFICATIONS = cfg.getBoolean("showNewTraitNotifications");
            MULTIPLY_RATING_LOSSES_BY_PERCENTAGE_OF_LOST_HULL = cfg.getBoolean("multiplyRatingLossesByPercentageOfLostHull");

            GLOBAL_EFFECT_MULT = (float) cfg.getDouble("globalEffectMult");
            FLEET_TRAIT_EFFECT_MULT = (float) cfg.getDouble("fleetTraitEffectMult");
            TRAITS_PER_TIER = cfg.getInt("traitsPerTier");
            DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION = cfg.getInt("daysMothballedPerTraitToResetReputation");

            TRAIT_CHANCE_MULT_FLAT = (float) cfg.getDouble("traitChanceMultFlat");
            TRAIT_CHANCE_MULT_PER_PLAYER_CAPTAIN_LEVEL = (float) cfg.getDouble("traitChanceMultPerPlayerCaptainLevel");
            TRAIT_CHANCE_MULT_PER_NON_PLAYER_CAPTAIN_LEVEL = (float) cfg.getDouble("traitChanceMultPerNonPlayerCaptainLevel");
            TRAIT_CHANCE_MULT_PER_FLEET_POINT = (float) cfg.getDouble("traitChanceMultPerFleetPoint");
            TRAIT_CHANCE_MULT_PER_DAMAGE_TAKEN_PERCENT = (float) cfg.getDouble("traitChanceMultPerDamageTakenPercent");
            TRAIT_CHANCE_MULT_PER_DAMAGE_DEALT_PERCENT = (float) cfg.getDouble("traitChanceMultPerDamageDealtPercent");

            USE_ADVANCED_SHIP_STRENGTH_ESTIMATION = cfg.getBoolean("useAdvancedShipStrengthEstimation");

            try {
                DMOD_FACTOR_FOR_PLAYER_SHIPS = (float) cfg.getDouble("dModFactorForPlayerShips");
                SMOD_FACTOR_FOR_PLAYER_SHIPS = (float) cfg.getDouble("sModFactorForPlayerShips");
                SKILL_FACTOR_FOR_PLAYER_SHIPS = (float) cfg.getDouble("skillFactorForPlayerShips");
                DMOD_FACTOR_FOR_ENEMY_SHIPS = (float) cfg.getDouble("dModFactorForEnemyShips");
                SMOD_FACTOR_FOR_ENEMY_SHIPS = (float) cfg.getDouble("sModFactorForEnemyShips");
                SKILL_FACTOR_FOR_ENEMY_SHIPS = (float) cfg.getDouble("skillFactorForEnemyShips");
                STRENGTH_INCREASE_PER_PLAYER_LEVEL = (float) cfg.getDouble("strengthIncreasePerPlayerLevel");
            } catch (Exception e) {
                if(USE_ADVANCED_SHIP_STRENGTH_ESTIMATION) throw e;
            }

            TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS = (float) cfg.getDouble("traitChanceMultForReservedCombatShips");
            TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS = (float) cfg.getDouble("traitChanceMultForReservedCivilianShips");
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = (float) cfg.getDouble("traitChanceBonusPerPlayerLevel");

            BASE_RATING = (float) cfg.getDouble("baseRating");
            BATTLE_DIFFICULTY_MULT = (float) cfg.getDouble("battleDifficultyMult");
            SUPPORT_MULT = (float) cfg.getDouble("supportMult");
            DAMAGE_TAKEN_MULT = (float) cfg.getDouble("damageTakenMult");
            DAMAGE_DEALT_MULT = (float) cfg.getDouble("damageDealtMult");
            DAMAGE_DEALT_MIN_THRESHOLD = (float) cfg.getDouble("damageDealtMinThreshold");


            BONUS_CHANCE_FOR_CIVILIAN_SHIPS = (float) cfg.getDouble("bonusChanceForCivilianShips");
            BONUS_CHANCE_RANDOMNESS = (float) cfg.getDouble("bonusChanceRandomness");
            BONUS_CHANCE_FOR_RESERVED_SHIPS_MULT = (float) cfg.getDouble("bonusChanceForReservedShipsMult");
            TRAIT_POSITION_CHANGE_CHANCE_MULT = (float) cfg.getDouble("traitPositionChangeChanceMult");
            USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE = cfg.getBoolean("useRatingFromLastBattleAsBasisForBonusChance");

            IMPROVE_LOYALTY_CHANCE_MULT = (float) cfg.getDouble("improveLoyaltyChanceMult");
            WORSEN_LOYALTY_CHANCE_MULT = (float) cfg.getDouble("worsenLoyaltyChanceMult");

            CHANCE_TO_IGNORE_LOGISTICS_TRAITS_ON_COMBAT_SHIPS  = (float) cfg.getDouble("chanceToIgnoreLogisticsTraitsOnCombatShips");
            CHANCE_TO_IGNORE_COMBAT_TRAITS_ON_CIVILIAN_SHIPS = (float) cfg.getDouble("chanceToIgnoreCombatTraitsOnCivilianShips");

            AVERAGE_FRACTION_OF_GOOD_TRAITS = (float) cfg.getDouble("averageFractionOfGoodTraits");
            TRAITS_FOR_FLEETS_WITH_NO_COMMANDER = cfg.getInt("traitsForFleetsWithNoCommander");
            TRAITS_FOR_FLEETS_WITH_MIN_LEVEL_COMMANDER = cfg.getInt("traitsForFleetsWithMinLevelCommander");
            TRAITS_FOR_FLEETS_WITH_MAX_LEVEL_COMMANDER = cfg.getInt("traitsForFleetsWithMaxLevelCommander");
            ALLOW_CUSTOM_COMMANDER_PRESETS = cfg.getBoolean("allowCustomCommanderPresets");

            TRAIT_CHANCE_MULT_FOR_COMBAT_SHIPS = (float) cfg.getDouble("traitChanceMultForCombatShips");
            TRAIT_CHANCE_MULT_FOR_CIVILIAN_SHIPS = (float) cfg.getDouble("traitChanceMultForCivilianShips");

            FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE = (float) cfg.getDouble("famousFlagshipBarEventChance");
            FAMOUS_DERELICT_BAR_EVENT_CHANCE = (float) cfg.getDouble("famousDerelictBarEventChance");

            FAMOUS_DERELICT_MAY_BE_GUARDED_BY_REMNANT_FLEET = cfg.getBoolean("famousDerelictMayBeGuardedByRemnantFleet");
            CONSIDER_NORMAL_HULLMODS_FOR_TRAIT_COMPATIBILITY = cfg.getBoolean("considerNormalHullmodsForTraitCompatibility");

            ANY_FAMOUS_SHIP_BAR_EVENT_CHANCE_MULT = FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE + FAMOUS_DERELICT_BAR_EVENT_CHANCE;

            return settingsAreRead = true;
        } catch (Exception e) {
            return settingsAreRead = reportCrash(e);
        }
    }

    public static CampaignFleetAPI createFleetSafely(FleetParamsV3 params) {
        try {
            return FleetFactoryV3.createFleet(params);
        } catch (Exception e) {
            Global.getLogger(ModPlugin.class).warn("Failed to generate fleet: " + params.toString());
            reportCrash(e, false);
        }

        return null;
    }

    public static boolean reportCrash(Exception exception) {
        return reportCrash(exception, true);
    }
    public static boolean reportCrash(Exception exception, boolean displayToUser) {
        try {
            String stackTrace = "", message = "Starship Legends encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                StackTraceElement ste = exception.getStackTrace()[i];
                stackTrace += "    " + ste.toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if(!displayToUser) {
                return true;
            } else if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);
            } else if (Global.getSector() != null && Global.getCurrentState() == GameState.CAMPAIGN) {
                CampaignUIAPI ui = Global.getSector().getCampaignUI();

                ui.addMessage(message, Color.RED);
                ui.addMessage(exception.getMessage(), Color.ORANGE);
                ui.showConfirmDialog(message + "\n\n" + exception.getMessage(), "Ok", null, null, null);

                if(ui.getCurrentInteractionDialog() != null) ui.getCurrentInteractionDialog().dismiss();
            } else return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
