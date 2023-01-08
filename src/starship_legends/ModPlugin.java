package starship_legends;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV3;
import com.fs.starfarer.api.impl.campaign.fleets.FleetParamsV3;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.thoughtworks.xstream.XStream;
import lunalib.lunaSettings.LunaSettings;
import org.json.JSONArray;
import org.json.JSONObject;
import starship_legends.events.FamousDerelictIntel;
import starship_legends.events.FamousShipBarEventCreator;
import starship_legends.events.FanclubBarEventCreator;
import starship_legends.events.OwnCrewBarEventCreator;
import starship_legends.hullmods.Reputation;

import java.awt.*;
import java.util.List;
import java.util.*;

public class ModPlugin extends BaseModPlugin {
    public static final String ID = "sun_starship_legends";
    public static final String
            TRAIT_LIST_PATH = "data/config/starship_legends/traits.csv",
            LOYALTY_LEVEL_LIST_PATH = "sun_sl/data/loyalty_levels.csv",
            REP_TIER_LIST_PATH = "sun_sl/data/rep_tiers.csv",
            REP_THEMES_PATH = "data/config/starship_legends/rep_themes/",
            REP_THEME_LIST_PATH = REP_THEMES_PATH + "rep_themes.csv",
            HULL_REGEN_SHIPS_PATH = "data/config/starship_legends/hull_regen_ships.csv",
            SETTINGS_PATH = "STARSHIP_LEGENDS_OPTIONS.ini";
    public static final int TIER_COUNT = 4;
    public static final int LOYALTY_LIMIT = 4;
    public static final double TIMESTAMP_TICKS_PER_DAY = 8.64E7D;
    public static final Map<String, Float> HULL_REGEN_SHIPS = new HashMap<>();

    static Saved<String> version = new Saved<>("version", "");
    static boolean settingsAreRead = false, isNewGame = false;

    static final String LUNALIB_ID = "lunalib";
    static JSONObject settingsCfg = null;
    static <T> T get(String id, Class<T> type) throws Exception {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)) {
            if(type == Integer.class) return type.cast(LunaSettings.getInt(ModPlugin.ID, id));
            if(type == Float.class) return type.cast(LunaSettings.getFloat(ModPlugin.ID, id));
            if(type == Boolean.class) return type.cast(LunaSettings.getBoolean(ModPlugin.ID, id));
            if(type == Double.class) return type.cast(LunaSettings.getDouble(ModPlugin.ID, id));
            if(type == String.class) return type.cast(LunaSettings.getString(ModPlugin.ID, id));
        } else {
            if(settingsCfg == null) settingsCfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            if(type == Integer.class) return type.cast(settingsCfg.getInt(id));
            if(type == Float.class) return type.cast((float) settingsCfg.getDouble(id));
            if(type == Boolean.class) return type.cast(settingsCfg.getBoolean(id));
            if(type == Double.class) return type.cast(settingsCfg.getDouble(id));
            if(type == String.class) return type.cast(settingsCfg.getString(id));
        }

        throw new MissingResourceException("No setting found with id: " + id, type.getName(), id);
    }
    static int getInt(String id) throws Exception { return get(id, Integer.class); }
    static double getDouble(String id) throws Exception { return get(id, Double.class); }
    static float getFloat(String id) throws Exception { return get(id, Float.class); }
    static boolean getBoolean(String id) throws Exception { return get(id, Boolean.class); }
    static String getString(String id) throws Exception { return get(id, String.class); }
    static boolean readSettings() {
        try {
            REMOVE_ALL_DATA_AND_FEATURES = getBoolean("removeAllDataAndFeatures");

            ENABLE_OFFICER_LOYALTY_SYSTEM = getBoolean("enableOfficerLoyaltySystem");
            COMPENSATE_FOR_EXPERIENCE_MULT = getBoolean("compensateForExperienceMult");
            SHOW_NEW_TRAIT_NOTIFICATIONS = getBoolean("showNewTraitNotifications");
            SHOW_SHIP_XP = getBoolean("showShipXp");
            SHOW_SHIP_XP_IN_DEV_MODE = getBoolean("showShipXpInDevMode");
            RUMORED_TRAITS_SHOWN = getInt("rumoredTraitsShown");
            RUMORED_TRAITS_SHOWN_IN_DEV_MODE = getInt("rumoredTraitsShownInDevMode");

            GLOBAL_EFFECT_MULT = getFloat("globalEffectMult");
            FLEET_TRAIT_EFFECT_MULT = getFloat("fleetTraitEffectMult");

            MAX_INITIAL_NEGATIVE_TRAITS = getInt("maxInitialNegativeTraits");
            MIN_INITIAL_NEGATIVE_TRAITS = getInt("minInitialNegativeTraits");
            MIN_NEGATIVE_TRAITS = getFloat("minNegativeTraits");
            LOYALTY_IMPROVEMENT_RATE_MULT = getFloat("loyaltyImprovementRateMult");
            BASE_LOYALTY_LEVELS_LOST_WHEN_DISABLED = getInt("baseLoyaltyLevelsLostWhenDisabled");
            MAX_LOYALTY_LEVELS_LOST_WHEN_DISABLED = getInt("maxLoyaltyLevelsLostWhenDisabled");
            MIN_LOYALTY_LEVELS_LOST_WHEN_DISABLED = getInt("minLoyaltyLevelsLostWhenDisabled");

            LOYALTY_LOSS_MULT_FROM_CREW_SAFETY = getFloat("loyaltyLossMultFromCrewSafety");
            LOYALTY_LOSS_MULT_FROM_RELATIVE_STRENGTH = getFloat("loyaltyLossMultFromRelativeStrength");

            XP_MULT_FLAT = getFloat("xpMultFlat");
            XP_MULT_PER_PLAYER_CAPTAIN_LEVEL = getFloat("xpMultPerPlayerCaptainLevel");
            XP_MULT_PER_NON_PLAYER_CAPTAIN_LEVEL = getFloat("xpMultPerNonPlayerCaptainLevel");
            XP_MULT_PER_FLEET_POINT = getFloat("xpMultPerFleetPoint");
            XP_MULT_PER_DAMAGE_DEALT_PERCENT = getFloat("xpMultPerDamageDealtPercent");

            XP_MULT_FOR_RESERVED_COMBAT_SHIPS = getFloat("xpMultForReservedCombatShips");
            XP_MULT_FOR_RESERVED_CIVILIAN_SHIPS = getFloat("xpMultForReservedCivilianShips");
            FAME_BONUS_PER_PLAYER_LEVEL = getFloat("fameBonusPerPlayerLevel");

            CHANCE_TO_IGNORE_LOGISTICS_TRAITS_ON_COMBAT_SHIPS  = getFloat("chanceToIgnoreLogisticsTraitsOnCombatShips");
            CHANCE_TO_IGNORE_COMBAT_TRAITS_ON_CIVILIAN_SHIPS = getFloat("chanceToIgnoreCombatTraitsOnCivilianShips");

            AVERAGE_FRACTION_OF_GOOD_TRAITS = getFloat("averageFractionOfGoodTraits");
            TRAITS_FOR_FLEETS_WITH_NO_COMMANDER = getInt("traitsForFleetsWithNoCommander");
            TRAITS_FOR_FLEETS_WITH_MIN_LEVEL_COMMANDER = getInt("traitsForFleetsWithMinLevelCommander");
            TRAITS_FOR_FLEETS_WITH_MAX_LEVEL_COMMANDER = getInt("traitsForFleetsWithMaxLevelCommander");
            ALLOW_CUSTOM_COMMANDER_PRESETS = getBoolean("allowCustomCommanderPresets");

            PEACEFUL_XP_MULT_FOR_COMBAT_SHIPS = getFloat("peacefulXpMultForCombatShips");
            PEACEFUL_XP_MULT_FOR_CIVILIAN_SHIPS = getFloat("peacefulXpMultForCivilianShips");

            AVERAGE_DAYS_BETWEEN_TRAIT_SIDEGRADE_SUGGESTIONS = getFloat("averageDaysBetweenTraitSidegradeSuggestions");
            ONLY_SUGGEST_SIDEGRADES_FOR_SHIPS_WITH_OFFICER = getBoolean("onlySuggestSidegradesForShipsWithOfficer");
            FAMOUS_DERELICT_MAY_BE_GUARDED_BY_REMNANT_FLEET = getBoolean("famousDerelictMayBeGuardedByRemnantFleet");
            AVERAGE_ADDITIONAL_BAR_EVENTS = getFloat("averageAdditionalBarEvents");

            FAME_BONUS_FROM_CHRONICLERS_FOR_COMBAT_SHIPS = getFloat("fameBonusFromChroniclersForCombatShips");
            FAME_BONUS_FROM_CHRONICLERS_FOR_CIVILIAN_SHIPS = getFloat("fameBonusFromChroniclersForCivilianShips");

            settingsCfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);
            settingsCfg = settingsCfg.getJSONObject("barEventChanceMultipliers");

            TRAIT_UPGRADE_BAR_EVENT_CHANCE = getFloat("traitUpgrade");
            TRAIT_SIDEGRADE_BAR_EVENT_CHANCE = getFloat("traitSidegrade");
            REPAIR_DMOD_BAR_EVENT_CHANCE = getFloat("repairDmod");
            CHRONICLER_JOINS_BAR_EVENT_CHANCE = getFloat("chroniclerJoins");
            LOYAL_CREW_JOINS_BAR_EVENT_CHANCE = getFloat("loyalCrewJoins");
            BUY_SHIP_OFFER_BAR_EVENT_CHANCE = getFloat("captainOffersToBuyFamousShip");
            JOIN_WITH_SHIP_BAR_EVENT_CHANCE = getFloat("captainOffersToJoinWithShip");
            FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE = getFloat("famousFlagshipIntel");
            FAMOUS_DERELICT_BAR_EVENT_CHANCE = getFloat("famousDerelictIntel");
            HEAR_LEGEND_OF_OWN_SHIP_BAR_EVENT_CHANCE = getFloat("hearLegendOfOwnShip");

            if(AVERAGE_ADDITIONAL_BAR_EVENTS > 0 && !REMOVE_ALL_DATA_AND_FEATURES) {
                SettingsAPI settings = Global.getSettings();
                settings.setFloat("maxBarEvents", (float) (ORIGINAL_MAX_BAR_EVENTS + Math.ceil(AVERAGE_ADDITIONAL_BAR_EVENTS)));

                if (ORIGINAL_BAR_EVENT_PROB_ONE_MORE > 0 && ORIGINAL_BAR_EVENT_PROB_ONE_MORE < 1) {
                    float newProb = ORIGINAL_BAR_EVENT_PROB_ONE_MORE + (1 - ORIGINAL_BAR_EVENT_PROB_ONE_MORE)
                            * (AVERAGE_ADDITIONAL_BAR_EVENTS / (AVERAGE_ADDITIONAL_BAR_EVENTS + 1));

                    settings.setFloat("barEventProbOneMore", newProb);
                }
            }

        } catch (Exception e) {
            settingsCfg = null;

            return reportCrash(e);
        }

        settingsCfg = null;

        return true;
    }

    public static boolean
            ENABLE_OFFICER_LOYALTY_SYSTEM = true,
            COMPENSATE_FOR_EXPERIENCE_MULT = true,
            REMOVE_ALL_DATA_AND_FEATURES = false,
            SHOW_NEW_TRAIT_NOTIFICATIONS = true,
            ALLOW_CUSTOM_COMMANDER_PRESETS = true,
            SHOW_SHIP_XP = false,
            SHOW_SHIP_XP_IN_DEV_MODE = true,
            ONLY_SUGGEST_SIDEGRADES_FOR_SHIPS_WITH_OFFICER = false,
            FAMOUS_DERELICT_MAY_BE_GUARDED_BY_REMNANT_FLEET = false;

    public static int
            MINIMUM_EFFECT_REDUCTION_PERCENT = -95,
            TRAITS_PER_TIER = 2,
            TRAITS_FOR_FLEETS_WITH_NO_COMMANDER = 0,
            TRAITS_FOR_FLEETS_WITH_MIN_LEVEL_COMMANDER = 1,
            MAX_INITIAL_NEGATIVE_TRAITS = 4,
            MIN_INITIAL_NEGATIVE_TRAITS = 1,
            BASE_LOYALTY_LEVELS_LOST_WHEN_DISABLED = 2,
            MAX_LOYALTY_LEVELS_LOST_WHEN_DISABLED = 2,
            MIN_LOYALTY_LEVELS_LOST_WHEN_DISABLED = 0,
            RUMORED_TRAITS_SHOWN = 2,
            RUMORED_TRAITS_SHOWN_IN_DEV_MODE = 8,
            TRAITS_FOR_FLEETS_WITH_MAX_LEVEL_COMMANDER = 5;

    public static float
            ORIGINAL_MIN_BAR_EVENTS = 1,
            ORIGINAL_MAX_BAR_EVENTS = 3,
            ORIGINAL_BAR_EVENT_PROB_ONE_MORE = 0.5f,

            LOYALTY_LOSS_MULT_FROM_CREW_SAFETY = 1.0f,
            LOYALTY_LOSS_MULT_FROM_RELATIVE_STRENGTH = 1.0f,


            GLOBAL_EFFECT_MULT = 1,
            FLEET_TRAIT_EFFECT_MULT = 2,
            FAME_BONUS_PER_PLAYER_LEVEL = 0.02f,

            CHANCE_TO_IGNORE_LOGISTICS_TRAITS_ON_COMBAT_SHIPS = 0.75f,
            CHANCE_TO_IGNORE_COMBAT_TRAITS_ON_CIVILIAN_SHIPS = 0.75f,

            AVERAGE_DAYS_BETWEEN_TRAIT_SIDEGRADE_SUGGESTIONS = 30f,

            MIN_NEGATIVE_TRAITS = 1,
            LOYALTY_IMPROVEMENT_RATE_MULT = 1,

            XP_MULT_FLAT = 0.1f,
            XP_MULT_PER_PLAYER_CAPTAIN_LEVEL = 0.005f,
            XP_MULT_PER_NON_PLAYER_CAPTAIN_LEVEL = 0.01f,
            XP_MULT_PER_FLEET_POINT = 0.0075f,
            XP_MULT_PER_DAMAGE_DEALT_PERCENT = 0.005f,

            XP_MULT_FOR_RESERVED_COMBAT_SHIPS = 0.0f,
            XP_MULT_FOR_RESERVED_CIVILIAN_SHIPS = 0.0f,

            PEACEFUL_XP_MULT_FOR_COMBAT_SHIPS = 0,
            PEACEFUL_XP_MULT_FOR_CIVILIAN_SHIPS = 0,

            FAME_BONUS_FROM_CHRONICLERS_FOR_COMBAT_SHIPS = 1,
            FAME_BONUS_FROM_CHRONICLERS_FOR_CIVILIAN_SHIPS = 5,

            AVERAGE_ADDITIONAL_BAR_EVENTS = 0,

            TRAIT_UPGRADE_BAR_EVENT_CHANCE = 1f,
            TRAIT_SIDEGRADE_BAR_EVENT_CHANCE = 2f,
            REPAIR_DMOD_BAR_EVENT_CHANCE = 0.5f,
            CHRONICLER_JOINS_BAR_EVENT_CHANCE = 1.0f,
            LOYAL_CREW_JOINS_BAR_EVENT_CHANCE = 1.0f,
            BUY_SHIP_OFFER_BAR_EVENT_CHANCE = 1f,
            JOIN_WITH_SHIP_BAR_EVENT_CHANCE = 0.5f,
            HEAR_LEGEND_OF_OWN_SHIP_BAR_EVENT_CHANCE = 1f,
            FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE = 2f,
            FAMOUS_DERELICT_BAR_EVENT_CHANCE = 0.5f,

            AVERAGE_FRACTION_OF_GOOD_TRAITS = 0.75f;

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

    void removeScripts() {
        Global.getSector().removeTransientScript(script);
        Global.getSector().removeListener(script);
        Util.removeRepHullmodFromAutoFitGoalVariants();
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

            SettingsAPI settings = Global.getSettings();
            ORIGINAL_MIN_BAR_EVENTS = settings.getInt("minBarEvents");
            ORIGINAL_MAX_BAR_EVENTS = settings.getInt("maxBarEvents");
            ORIGINAL_BAR_EVENT_PROB_ONE_MORE = settings.getFloat("barEventProbOneMore");
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Version comparison failed.", e);
        }

        if(!message.isEmpty()) throw new Exception(message);

        createTraitEffects();
    }

    @Override
    public void beforeGameSave() {
        Saved.updatePersistentData();
        removeScripts();
    }

    @Override
    public void afterGameSave() {
        Global.getSector().addTransientScript(script = new CampaignScript());

        Saved.loadPersistentData(); // Because script attributes will be reset
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            removeScripts();

            Reputation.moduleMap.clear();

            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();
            CampaignScript.reset();
            FactionConfig.clearEnemyFleetRep();

            readSettingsIfNecessary(true);

            if(isNewGame) {
                for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
                    RepRecord.setShipOrigin(ship, RepRecord.Origin.Type.StartedWith, "");
                }

                isNewGame = false;
            }


            if (REMOVE_ALL_DATA_AND_FEATURES) {
                Util.clearAllStarshipLegendsData();
            } else {
                BarEventManager bar = BarEventManager.getInstance();

                if (!bar.hasEventCreator(FamousShipBarEventCreator.class)) {
                    bar.addEventCreator(new FamousShipBarEventCreator());
                    bar.addEventCreator(new OwnCrewBarEventCreator());
                    bar.addEventCreator(new FanclubBarEventCreator());
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

                // Replace irrelevant traits from existing notable ships
                try {
                    for (FleetMemberAPI ship : Reputation.getShipsOfNote()) {
                        if(!RepRecord.existsFor(ship)) continue;

                        RepRecord rep = RepRecord.get(ship);
                        List<Trait> destinedTraits = RepRecord.getDestinedTraitsForShip(ship, true);

                        for(int i = 0; i < rep.getTraits().size(); ++i) {
                            Trait trait = rep.getTraits().get(i);

                            if(!trait.isRelevantFor(ship)) {
                                log("Removing " + trait.getName(true) + " from the " + ship.getShipName()
                                        + " (" + ship.getHullId() + ") for not being relevant to the ship");
                                rep.getTraits().get(i).typeID = destinedTraits.get(i).typeID;
                            }
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

                        for (Trait t : new ArrayList<>(rep.getTraits())) {
                            if (found.contains(t.getType()) || t.getName(true).isEmpty()) {
                                rep.getTraits().remove(t);
                            } else found.add(t.getType());
                        }
                    }
                } catch (Exception e) {
                    log("An error occurred while removing duplicate traits!");
                }
            }

            LunaSettingsChangedListener.addToManagerIfNeeded();
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void configureXStream(XStream x) {
        RepRecord.configureXStream(x);
        RepRecord.Story.configureXStream(x);
        RepRecord.Origin.configureXStream(x);
        Trait.configureXStream(x);
        RepChange.configureXStream(x);
        BattleReport.configureXStream(x);
    }

    @Override
    public void onNewGameAfterTimePass() {
        super.onNewGameAfterTimePass();

        isNewGame = true;
    }

    public static boolean isUpdateDiagnosticCheckNeeded() {
        if(version.val == null || version.val.equals("")) return true;

        Version lastSavedVersion = new Version(version.val);
        Version currentVersion = new Version(Global.getSettings().getModManager().getModSpec("sun_starship_legends").getVersion());

        return lastSavedVersion.isOlderThan(currentVersion, true);
    }
    public static boolean settingsHaveBeenRead() { return settingsAreRead; }
    public static boolean readSettingsIfNecessary(boolean forceRefresh) {
        try {
            if(forceRefresh) settingsAreRead = false;

            if(settingsAreRead) return true;

            JSONArray jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("id", TRAIT_LIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) new TraitType(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().loadCSV(LOYALTY_LEVEL_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) LoyaltyLevel.values()[i].init(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().loadCSV(REP_TIER_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) {
                Trait.Tier.values()[i + 2].init(jsonArray.getJSONObject(i));
            }

            jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("hull_id", HULL_REGEN_SHIPS_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject data = jsonArray.getJSONObject(i);
                HULL_REGEN_SHIPS.put(data.getString("hull_id"), (float)data.getDouble("damage_counted_per_damage_sustained"));
            }

            jsonArray = Global.getSettings().getMergedSpreadsheetDataForMod("theme_id", REP_THEME_LIST_PATH, ID);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject data = jsonArray.getJSONObject(i);
                String key = data.getString("theme_id");

                JSONObject json = Global.getSettings().getMergedJSONForMod(REP_THEMES_PATH + key + ".json", ID);

                new RepTheme(key, (float)data.getDouble("likelihood"), json);
            }

            JSONObject cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            TRAITS_PER_TIER = 2 * Math.max(1, cfg.getInt("traitPairsPerTier"));

            FactionConfig.readDerelictChanceMultipliers(cfg.getJSONArray("famousDerelictChanceMultipliersByShipStrength"));

            if(FactionConfig.hasNotBeenRead()) {
                for (FactionAPI faction : Global.getSector().getAllFactions()) {
                    new FactionConfig(faction);
                }
            }

            return settingsAreRead = readSettings();
        } catch (Exception e) {
            return settingsAreRead = reportCrash(e);
        }
    }
    public static void createTraitEffects() {
        Integration.registerTraitEffect("fighter_damage", new TraitType.Effect() {
            @Override
            public boolean isAppliedToFighters() { return true; }

            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getBallisticWeaponDamageMult().modifyPercent(id, effectPercent);
                stats.getEnergyWeaponDamageMult().modifyPercent(id, effectPercent);
                stats.getMissileWeaponDamageMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("fighter_speed", new TraitType.Effect() {
            @Override
            public boolean isAppliedToFighters() { return true; }

            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getAcceleration().modifyPercent(id, effectPercent);
                stats.getMaxSpeed().modifyPercent(id, effectPercent);
                stats.getTurnAcceleration().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("fighter_durability", new TraitType.Effect() {
            @Override
            public boolean isAppliedToFighters() { return true; }

            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getHullDamageTakenMult().modifyPercent(id, -effectPercent);
                stats.getShieldDamageTakenMult().modifyPercent(id, -effectPercent);
                stats.getArmorDamageTakenMult().modifyPercent(id, -effectPercent);
            }
        });
        Integration.registerTraitEffect("ammo_regen", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getBallisticAmmoRegenMult().modifyPercent(id, effectPercent);
                stats.getEnergyAmmoRegenMult().modifyPercent(id, effectPercent);
                stats.getMissileAmmoRegenMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("ammo_capacity", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getBallisticAmmoBonus().modifyPercent(id, effectPercent);
                stats.getEnergyAmmoBonus().modifyPercent(id, effectPercent);
                stats.getMissileAmmoBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("system_cooldown", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getSystemCooldownBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("system_regen_rate", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getSystemRegenBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("ballistics_rof", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getBallisticRoFMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("energy_cost", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getEnergyWeaponFluxCostMod().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("pd_range", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getNonBeamPDWeaponRangeBonus().modifyPercent(id, effectPercent);
                stats.getBeamPDWeaponRangeBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("pd_damage", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDamageToMissiles().modifyPercent(id, effectPercent);
                stats.getDamageToFighters().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("dmod_integrity", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                int dmods = 0;
                for(String modId : ship.getVariant().getPermaMods()) {
                if(Global.getSettings().getHullModSpec(modId).hasTag("dmod")) dmods++;
                }

                stats.getHullBonus().modifyPercent(id, effectPercent * dmods);
            }
        });
        Integration.registerTraitEffect("missile_guidance", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getMissileGuidance().modifyPercent(id, effectPercent);
                stats.getMissileAccelerationBonus().modifyPercent(id, effectPercent);
                stats.getMissileMaxSpeedBonus().modifyPercent(id, effectPercent);
                stats.getMissileTurnAccelerationBonus().modifyPercent(id, effectPercent);
                stats.getMissileMaxTurnRateBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("missile_reload", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getMissileRoFMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("cursed", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                CombatPlugin.CURSED.put(ship.getId(), effectPercent);
            }
        });
        Integration.registerTraitEffect("phase_mad", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                CombatPlugin.PHASEMAD.put(ship.getId(), effectPercent);
            }
        });
        Integration.registerTraitEffect("dmod_effect", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDynamic().getStat(Stats.DMOD_EFFECT_MULT).modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("survey", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDynamic().getMod(Stats.getSurveyCostReductionId(Commodities.SUPPLIES)).modifyFlat(id, -effectPercent);
            }
        });
        Integration.registerTraitEffect("blockade_runner", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getZeroFluxSpeedBoost().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("drive_stabilizer", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getSensorProfile().modifyFlat(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("command_support", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDynamic().getMod(Stats.COMMAND_POINT_RATE_FLAT).modifyFlat(id, effectPercent * 0.01f);
            }
        });
        Integration.registerTraitEffect("nav_support", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDynamic().getMod(Stats.COORDINATED_MANEUVERS_FLAT).modifyFlat(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("ecm_support", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDynamic().getMod(Stats.ELECTRONIC_WARFARE_FLAT).modifyFlat(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("cr_cap", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                Trait trait = TraitType.get("cr_cap").getTrait(effectPercent < 0);
                String traitName = trait.getName(Util.isShipCrewed(ship));
                stats.getMaxCombatReadiness().modifyFlat(id, effectPercent * 0.01f, traitName);
            }
        });
        Integration.registerTraitEffect("cr_recovery", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getBaseCRRecoveryRatePercentPerDay().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("damage", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getBallisticWeaponDamageMult().modifyPercent(id, effectPercent);
                stats.getEnergyWeaponDamageMult().modifyPercent(id, effectPercent);
                stats.getMissileWeaponDamageMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("malfunction", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getCriticalMalfunctionChance().modifyPercent(id, effectPercent);
                stats.getEngineMalfunctionChance().modifyPercent(id, effectPercent);
                stats.getShieldMalfunctionChance().modifyPercent(id, effectPercent);
                stats.getWeaponMalfunctionChance().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("mount_durability", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getWeaponHealthBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("engine_durability", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getEngineHealthBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("crew_casualties", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getCrewLossMult().modifyPercent(id, effectPercent);
                stats.getDynamic().getStat(Stats.FIGHTER_CREW_LOSS_MULT).modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("recovery_chance", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyPercent(id, effectPercent);
                stats.getBreakProb().modifyPercent(id, -effectPercent);
            }
        });
        Integration.registerTraitEffect("maneuverability", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getAcceleration().modifyPercent(id, effectPercent);
                stats.getDeceleration().modifyPercent(id, effectPercent);
                stats.getTurnAcceleration().modifyPercent(id, effectPercent);
                stats.getMaxTurnRate().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("hull_integrity", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getHullBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("shield_strength", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getShieldDamageTakenMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("armor_strength", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getArmorBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("engine_power", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getMaxSpeed().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("emp_resistance", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getEmpDamageTakenMult().modifyPercent(id, -effectPercent);
            }
        });
        Integration.registerTraitEffect("shield_stability", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getShieldUpkeepMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("peak_cr_time", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getPeakCRDuration().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("overload_time", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getOverloadTimeMod().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("flux_capacity", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getFluxCapacity().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("flux_dissipation", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getFluxDissipation().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("sensor_strength", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getSensorStrength().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("sensor_profile", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getSensorProfile().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("refit_time", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getFighterRefitTimeMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("salvage", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getDynamic().getMod(Stats.SALVAGE_VALUE_MULT_MOD).modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("cargo_capacity", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getCargoMod().modifyFlat(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("fuel_efficiency", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getFuelUseMod().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("fuel_capacity", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getFuelMod().modifyFlat(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("supply_upkeep", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getSuppliesPerMonth().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("phase_cost", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getPhaseCloakActivationCostBonus().modifyPercent(id, effectPercent);
                stats.getPhaseCloakUpkeepCostBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("phase_cooldown", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getPhaseCloakCooldownBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("range", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getBallisticWeaponRangeBonus().modifyPercent(id, effectPercent);
                stats.getEnergyWeaponRangeBonus().modifyPercent(id, effectPercent);
                stats.getMissileWeaponRangeBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("repair", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getCombatEngineRepairTimeMult().modifyPercent(id, -effectPercent);
                stats.getCombatWeaponRepairTimeMult().modifyPercent(id, -effectPercent);
                stats.getRepairRatePercentPerDay().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("weapon_stability", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getRecoilPerShotMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("turret_rotation", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getWeaponTurnRateBonus().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("vent_rate", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getVentRateMult().modifyPercent(id, effectPercent);
            }
        });
        Integration.registerTraitEffect("shield_raise_rate", new TraitType.Effect() {
            @Override
            public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                stats.getShieldUnfoldRateMult().modifyPercent(id, effectPercent);
            }
        });
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
