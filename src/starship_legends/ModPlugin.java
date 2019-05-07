package starship_legends;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.thoughtworks.xstream.XStream;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ModPlugin extends BaseModPlugin {
    static final String TRAIT_LIST_PATH = "sun_sl/data/traits.csv";
    static final String LOYALTY_LEVEL_LIST_PATH = "sun_sl/data/loyalty_levels.csv";
    static final String SETTINGS_PATH = "STARSHIP_LEGENDS_OPTIONS.ini";
    static final String VARIANT_PREFIX = "sun_sl_";
    static final int TIER_COUNT = 4;
    static final int LOYALTY_LIMIT = 3;

    static boolean settingsAreRead = false;

    public static boolean
            SHOW_REPUTATION_CHANGE_NOTIFICATIONS = true,
            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = true,
            ENABLE_OFFICER_LOYALTY_SYSTEM = true,
            LOG_REPUTATION_CALCULATION_FACTORS = true;

    public static int
            TRAITS_PER_TIER = 2;
    public static float
            MAX_XP_FOR_RESERVED_SHIPS = 80000,
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_COMBAT_SHIPS = 0.0f,
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_CIVILIAN_SHIPS = 0.25f,
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = 0.02f,

            BASE_CHANCE_TO_BE_BONUS = 0.5f,
            BONUS_CHANCE_BATTLE_DIFFICULTY_MULTIPLIER = 0.25f,
            BONUS_CHANCE_DAMAGE_DEALT_MULTIPLIER = 0.5f,
            BONUS_CHANCE_DAMAGE_TAKEN_MULTIPLIER = 1.0f,
            BONUS_CHANCE_FOR_RESERVED_SHIPS_MULTIPLIER = 1.0f,

            IMPROVE_LOYALTY_CHANCE_MULTIPLIER = 1.0f,
            WORSEN_LOYALTY_CHANCE_MULTIPLIER = 1.0f;

    CampaignScript script;

    @Override
    public void beforeGameSave() {
        Saved.updatePersistentData();
        Global.getSector().removeTransientScript(script);
        Global.getSector().removeListener(script);
    }

    @Override
    public void afterGameSave() {
        Global.getSector().addTransientScript(script = new CampaignScript());

        Saved.loadPersistentData(); // Because script attributes will be reset
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            Global.getSector().registerPlugin(new CampaignPlugin());
            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();

            readSettingsIfNecessary();

            try {
                for(RepRecord rep : RepRecord.INSTANCE_REGISTRY.val.values()) {
                    Set<TraitType> found = new HashSet<>();

                    for (Trait t : new ArrayList<>(rep.getTraits())) {
                        if (found.contains(t.getType()) || t.getName(true).isEmpty()) {
                            rep.getTraits().remove(t);
                        } else found.add(t.getType());
                    }
                }
            } catch (Exception e) {
                Global.getLogger(this.getClass()).info("Failed to remove duplicate traits!");
            }
        } catch (Exception e) { reportCrash(e); }
    }

    @Override
    public void configureXStream(XStream x) {
        x.alias("sun_sl_rr", RepRecord.class);
        x.aliasAttribute(RepRecord.class, "traits", "t");
        x.aliasAttribute(RepRecord.class, "opinionsOfOfficers", "o");

        x.alias("sun_sl_t", Trait.class);
        x.aliasAttribute(Trait.class, "typeID", "t");
        x.aliasAttribute(Trait.class, "effectSign", "e");

        x.alias("sun_sl_rc", RepChange.class);
        x.aliasAttribute(RepChange.class, "ship", "s");
        x.aliasAttribute(RepChange.class, "captain", "c");
        x.aliasAttribute(RepChange.class, "newTrait", "t");
        x.aliasAttribute(RepChange.class, "captainOpinionChange", "o");
    }

    public static boolean settingsHaveBeenRead() { return settingsAreRead; }

    public static boolean readSettingsIfNecessary() {
        try {
            if(settingsAreRead) return true;

            JSONArray jsonArray = Global.getSettings().loadCSV(TRAIT_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) new TraitType(jsonArray.getJSONObject(i));

            jsonArray = Global.getSettings().loadCSV(LOYALTY_LEVEL_LIST_PATH);
            for (int i = 0; i < jsonArray.length(); i++) LoyaltyLevel.values()[i].init(jsonArray.getJSONObject(i));

            JSONObject cfg = Global.getSettings().loadJSON(SETTINGS_PATH);

            Trait.Teir.Notable.init(cfg);
            Trait.Teir.Wellknown.init(cfg);
            Trait.Teir.Famous.init(cfg);
            Trait.Teir.Legendary.init(cfg);

            SHOW_REPUTATION_CHANGE_NOTIFICATIONS = cfg.getBoolean("showReputationChangeNotifications");
            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = cfg.getBoolean("useRuthlessSectorToCalculateBattleDifficulty");
            ENABLE_OFFICER_LOYALTY_SYSTEM = cfg.getBoolean("enableOfficerLoyaltySystem");
            LOG_REPUTATION_CALCULATION_FACTORS = cfg.getBoolean("logReputationCalculationFactors");

            TRAITS_PER_TIER = cfg.getInt("traitsPerTier");
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_COMBAT_SHIPS = (float) cfg.getDouble("traitChanceMultiplierForReservedCombatShips");
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_CIVILIAN_SHIPS = (float) cfg.getDouble("traitChanceMultiplierForReservedCivilianShips");
            MAX_XP_FOR_RESERVED_SHIPS = (float) cfg.getDouble("maxXpForReservedShips");
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = (float) cfg.getDouble("traitChanceBonusPerPlayerLevel");


            BASE_CHANCE_TO_BE_BONUS = (float) cfg.getDouble("baseChanceToBeBonus");
            BONUS_CHANCE_BATTLE_DIFFICULTY_MULTIPLIER = (float) cfg.getDouble("bonusChanceBattleDifficultyMultiplier");
            BONUS_CHANCE_DAMAGE_DEALT_MULTIPLIER = (float) cfg.getDouble("bonusChanceDamageDealtMultiplier");
            BONUS_CHANCE_DAMAGE_TAKEN_MULTIPLIER = (float) cfg.getDouble("bonusChanceDamageTakenMultiplier");
            BONUS_CHANCE_FOR_RESERVED_SHIPS_MULTIPLIER = (float) cfg.getDouble("bonusChanceForReservedShipsMultiplier");

            IMPROVE_LOYALTY_CHANCE_MULTIPLIER = (float) cfg.getDouble("improveLoyaltyChanceMultiplier");
            WORSEN_LOYALTY_CHANCE_MULTIPLIER = (float) cfg.getDouble("worsenLoyaltyChanceMultiplier");

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
