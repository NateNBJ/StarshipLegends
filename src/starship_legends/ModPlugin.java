package starship_legends;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.thoughtworks.xstream.XStream;
import org.json.JSONArray;
import org.json.JSONObject;
import starship_legends.hullmods.Reputation;

import java.awt.*;
import java.util.*;

public class ModPlugin extends BaseModPlugin {
    public static final String ID = "sun_starship_legends";
    public static final String TRAIT_LIST_PATH = "sun_sl/data/traits.csv";
    public static final String LOYALTY_LEVEL_LIST_PATH = "sun_sl/data/loyalty_levels.csv";
    public static final String HULL_REGEN_SHIPS_PATH = "sun_sl/data/hull_regen_ships.csv";
    public static final String SETTINGS_PATH = "STARSHIP_LEGENDS_OPTIONS.ini";
    public static final String RUTHLESS_SETTINGS_PATH = "RUTHLESS_STARSHIP_LEGENDS_OPTIONS.ini";
    public static final String VARIANT_PREFIX = "sun_sl_";
    public static final int TIER_COUNT = 4;
    public static final int LOYALTY_LIMIT = 3;

    static boolean settingsAreRead = false;

    public static final Map<String, Float> HULL_REGEN_SHIPS = new HashMap<>();

    public static boolean
            SHOW_REPUTATION_CHANGE_NOTIFICATIONS = true,
            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = true,
            USE_RUTHLESS_SECTOR_TO_CALCULATE_SHIP_STRENGTH = true,
            ENABLE_OFFICER_LOYALTY_SYSTEM = true,
            LOG_REPUTATION_CALCULATION_FACTORS = true,
            COMPENSATE_FOR_EXPERIENCE_MULT = true,
            USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE = false,
            SHOW_COMBAT_RATINGS = true,
            IGNORE_ALL_MALUSES = false;

    public static int
            TRAITS_PER_TIER = 2,
            DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION = 30;
    public static float
            GLOBAL_EFFECT_MULT = 1,
            MAX_XP_FOR_RESERVED_SHIPS = 80000,
            TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS = 0.0f,
            TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS = 0.25f,
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

            IMPROVE_LOYALTY_CHANCE_MULT = 1.0f,
            WORSEN_LOYALTY_CHANCE_MULT = 1.0f;

    CampaignScript script;

    class Version {
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
            if(!ignoreRC && RC < other.RC) return true;

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
        Util.removeRepHullmodFromAutoFitGoalVariants();
        Saved.updatePersistentData();
        Global.getSector().removeTransientScript(script);
        Global.getSector().removeListener(script);

//        for(ShipVariantAPI v : Global.getSector().getAutofitVariants().getTargetVariants("sunder")) {
//            v.removeMod();
//            v.getPermaMods().clear();
//        }
    }

    @Override
    public void afterGameSave() {
        Global.getSector().addTransientScript(script = new CampaignScript());

        Saved.loadPersistentData(); // Because script attributes will be reset
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();
            CampaignScript.reset();

            readSettingsIfNecessary();

            boolean allRepRecordsHaveNoRating = true;

            // Remove RepRecords for ships that no longer exist
//            try {
//                Set<String> foundIDs = new HashSet<>();
//
//                for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
//                    foundIDs.add(ship.getId());
//                }
//
//                for(MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
//                    for(FleetMemberAPI ship : market.getSubmarket(Submarkets.SUBMARKET_STORAGE).getCargo().getMothballedShips().getMembersListCopy()) {
//                        foundIDs.add(ship.getId());
//                    }
//                }
//
//                List<String> recordIDs = new ArrayList<>(RepRecord.INSTANCE_REGISTRY.val.keySet());
//
//                for(String id : recordIDs) {
//                    if(!foundIDs.contains(id)) {
//                        RepRecord.INSTANCE_REGISTRY.val.remove(id);
//                    }
//                }
//            } catch (Exception e) {
//                Global.getLogger(this.getClass()).info("Failed to remove duplicate traits!");
//            }

            // Remove existing duplicate traits
            try {
                for(RepRecord rep : RepRecord.INSTANCE_REGISTRY.val.values()) {
                    Set<TraitType> found = new HashSet<>();

                    if(rep.getRating() != 0) allRepRecordsHaveNoRating = false;

                    for (Trait t : new ArrayList<>(rep.getTraits())) {
                        if (found.contains(t.getType()) || t.getName(true).isEmpty()) {
                            rep.getTraits().remove(t);
                        } else found.add(t.getType());
                    }
                }
            } catch (Exception e) {
                Global.getLogger(this.getClass()).info("Failed to remove duplicate traits!");
            }

            // If no ships have ratings, estimate them
            try {
                if(allRepRecordsHaveNoRating) {
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
                Global.getLogger(this.getClass()).info("Failed to remove duplicate traits!");
            }

            // Remove RepRecords with no traits
            try {
                for(FleetMemberAPI ship : new LinkedList<>(Reputation.getShipsOfNote())) {
                    if(!RepRecord.existsFor(ship) || RepRecord.get(ship).getTraits().isEmpty()) {
                        RepRecord.deleteFor(ship);
                    }
                }
            } catch (Exception e) {
                Global.getLogger(this.getClass()).info("Failed to remove RepRecords without traits!");
            }

            // Compile existing RepChanges into a battle report
            try {
                if(!CampaignScript.pendingRepChanges.val.isEmpty()) {
                    BattleReport report = new BattleReport(1, null, null, null, null, 0, 0);
                    for (RepChange rc : CampaignScript.pendingRepChanges.val) {
                        report.addChange(rc);
                    }

                    CampaignScript.pendingRepChanges.val.clear();
                    Global.getSector().getIntelManager().addIntel(report);
                }
            } catch (Exception e) {
                Global.getLogger(this.getClass()).info("Failed to compile pending reputation changes!");
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

    public static boolean settingsHaveBeenRead() { return settingsAreRead; }

    public static boolean readSettingsIfNecessary() {
        try {
            if(settingsAreRead) return true;

            JSONArray jsonArray = Global.getSettings().loadCSV(TRAIT_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) new TraitType(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().loadCSV(LOYALTY_LEVEL_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) LoyaltyLevel.values()[i].init(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().loadCSV(HULL_REGEN_SHIPS_PATH);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject data = jsonArray.getJSONObject(i);
                HULL_REGEN_SHIPS.put(data.getString("hull_id"), (float)data.getDouble("damage_counted_per_damage_sustained"));
            }

            JSONObject cfg;

            if(Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector")) {
                try {
                    cfg = Global.getSettings().getMergedJSONForMod(RUTHLESS_SETTINGS_PATH, ID);
                } catch (Exception e) { cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID); }
            } else cfg = Global.getSettings().getMergedJSONForMod(SETTINGS_PATH, ID);

            Trait.Teir.Notable.init(cfg);
            Trait.Teir.Wellknown.init(cfg);
            Trait.Teir.Famous.init(cfg);
            Trait.Teir.Legendary.init(cfg);

            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = cfg.getBoolean("useRuthlessSectorToCalculateBattleDifficulty");
            USE_RUTHLESS_SECTOR_TO_CALCULATE_SHIP_STRENGTH = cfg.getBoolean("useRuthlessSectorToCalculateShipStrength");
            ENABLE_OFFICER_LOYALTY_SYSTEM = cfg.getBoolean("enableOfficerLoyaltySystem");
            LOG_REPUTATION_CALCULATION_FACTORS = cfg.getBoolean("logReputationCalculationFactors");
            COMPENSATE_FOR_EXPERIENCE_MULT = cfg.getBoolean("compensateForExperienceMult");
            IGNORE_ALL_MALUSES = cfg.getBoolean("ignoreAllMaluses");
            SHOW_COMBAT_RATINGS = cfg.getBoolean("showCombatRatings");

            GLOBAL_EFFECT_MULT = (float) cfg.getDouble("globalEffectMult");
            TRAITS_PER_TIER = cfg.getInt("traitsPerTier");
            DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION = cfg.getInt("daysMothballedPerTraitToResetReputation");

            TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS = (float) cfg.getDouble("traitChanceMultForReservedCombatShips");
            TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS = (float) cfg.getDouble("traitChanceMultForReservedCivilianShips");
            MAX_XP_FOR_RESERVED_SHIPS = (float) cfg.getDouble("maxXpForReservedShips");
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


            return settingsAreRead = true;
        } catch (Exception e) {
            return settingsAreRead = reportCrash(e);
        }
    }

    public static boolean reportCrash(Exception exception) {
        try {
            String stackTrace = "", message = "Starship Legends encountered an error!\nPlease let the mod author know.";

            for(int i = 0; i < exception.getStackTrace().length; i++) {
                stackTrace += "    " + exception.getStackTrace()[i].toString() + System.lineSeparator();
            }

            Global.getLogger(ModPlugin.class).error(exception.getMessage() + System.lineSeparator() + stackTrace);

            if (Global.getCombatEngine() != null && Global.getCurrentState() == GameState.COMBAT) {
                Global.getCombatEngine().getCombatUI().addMessage(1, Color.ORANGE, exception.getMessage());
                Global.getCombatEngine().getCombatUI().addMessage(2, Color.RED, message);
            } else if (Global.getSector() != null) {
                Global.getSector().getCampaignUI().addMessage(message, Color.RED);
                Global.getSector().getCampaignUI().addMessage(exception.getMessage(), Color.ORANGE);
                Global.getSector().getCampaignUI().showConfirmDialog(message + "\n\n"
                        + exception.getMessage(), "Ok", null, null, null);
            } else return false;

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
