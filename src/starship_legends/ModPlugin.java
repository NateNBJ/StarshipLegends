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
    static final String QUIRK_LIST_PATH = "sun_sl/data/traits.csv";
    static final String SETTINGS_PATH = "STARSHIP_LEGENDS_OPTIONS.ini";
    static final String VARIANT_PREFIX = "sun_sl_";
    static final int TIER_COUNT = 4;

    static boolean settingsAreRead = false;

    public static boolean
            SHOW_REPUTATION_CHANGE_NOTIFICATIONS = true,
            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = true,
            SCALE_LOYALTY_INCREASE_CHANCE_BY_BATTLE_DIFFICULTY = true,
            ENABLE_OFFICER_LOYALTY_SYSTEM = true,
            LOG_REPUTATION_CALCULATION_FACTORS = true;

    public static int
            OFFICER_ADJUSTMENT_TO_TRAIT_EFFECT_MULTIPLIER = 2,
            TRAITS_PER_TIER = 2;
    public static float
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_COMBAT_SHIPS = 0.0f,
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_CIVILIAN_SHIPS = 0.25f,
            MAX_XP_FOR_RESERVED_SHIPS = 80000,
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = 2f,
            CHANCE_OF_MALUS_AT_NO_HULL_LOST = 0.2f,
            CHANCE_OF_MALUS_AT_HALF_HULL_LOST = 0.8f,
            CHANCE_OF_MALUS_WHILE_IN_RESERVE = 0.5f,
            MALUS_CHANCE_REDUCTION_FOR_DIFFICULT_BATTLES = 1.0f,
            MALUS_CHANCE_REDUCTION_FOR_HIGH_DAMAGE_RATIO = 1.0f,
            IMPROVE_LOYALTY_CHANCE_MULTIPLIER = 1.0f,
            CHANCE_OF_LOYALTY_DECREASE_AT_ALL_HULL_LOST = 0.9f,
            MAX_HULL_FRACTION_LOST_FOR_LOYALTY_INCREASE = 0.1f,
            MIN_BATTLE_DIFFICULTY_REQUIRED_FOR_LOYALTY_INCREASE = 0.8f,
            COMBAT_READINESS_DECAY_PERCENT_MODIFIER_PER_LOYALTY_LEVEL = 25;

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

            JSONArray traitJsonArray = Global.getSettings().loadCSV(QUIRK_LIST_PATH);

            for (int i = 0; i < traitJsonArray.length(); i++) {
                new TraitType(traitJsonArray.getJSONObject(i));
            }

            JSONObject cfg = Global.getSettings().loadJSON(SETTINGS_PATH);

            SHOW_REPUTATION_CHANGE_NOTIFICATIONS = cfg.getBoolean("showReputationChangeNotifications");
            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = cfg.getBoolean("useRuthlessSectorToCalculateBattleDifficulty");
            SCALE_LOYALTY_INCREASE_CHANCE_BY_BATTLE_DIFFICULTY = cfg.getBoolean("scaleLoyaltyIncreaseChanceByBattleDifficulty");
            ENABLE_OFFICER_LOYALTY_SYSTEM = cfg.getBoolean("enableOfficerLoyaltySystem");
            LOG_REPUTATION_CALCULATION_FACTORS = cfg.getBoolean("logReputationCalculationFactors");

//            IMPROVE_LOYALTY_CHANCE_MULTIPLIER = (float) cfg.getDouble("improveLoyaltyChanceMult");
//            CHANCE_OF_LOYALTY_DECREASE_AT_ALL_HULL_LOST = (float) cfg.getDouble("chanceOfLoyaltyDecreaseAtAllHullLost");
//            MAX_HULL_FRACTION_LOST_FOR_LOYALTY_INCREASE = (float) cfg.getDouble("maxHullFractionLostForLoyaltyIncrease");
//            MIN_BATTLE_DIFFICULTY_REQUIRED_FOR_LOYALTY_INCREASE = (float) cfg.getDouble("minBattleDifficultyRequiredForLoyaltyIncrease");
//            COMBAT_READINESS_DECAY_PERCENT_MODIFIER_PER_LOYALTY_LEVEL = (float) cfg.getDouble("combatReadinessDecayPercentModifierPerLoyaltyLevel");

            TRAITS_PER_TIER = cfg.getInt("traitsPerTier");
            OFFICER_ADJUSTMENT_TO_TRAIT_EFFECT_MULTIPLIER = cfg.getInt("officerAdjustmentToTraitEffectMultiplier");
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_COMBAT_SHIPS = (float) cfg.getDouble("traitChanceMultiplierForReservedCombatShips");
            TRAIT_CHANCE_MULTIPLIER_FOR_RESERVED_CIVILIAN_SHIPS = (float) cfg.getDouble("traitChanceMultiplierForReservedCivilianShips");
            MAX_XP_FOR_RESERVED_SHIPS = (float) cfg.getDouble("maxXpForReservedShips");
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = (float) cfg.getDouble("traitChanceBonusPerPlayerLevel");

            CHANCE_OF_MALUS_AT_NO_HULL_LOST = (float) cfg.getDouble("chanceOfMalusAtNoHullLost");
            CHANCE_OF_MALUS_AT_HALF_HULL_LOST = (float) cfg.getDouble("chanceOfMalusAtHalfHullLost");
            CHANCE_OF_MALUS_WHILE_IN_RESERVE = (float) cfg.getDouble("chanceOfMalusWhenInReserve");
            MALUS_CHANCE_REDUCTION_FOR_DIFFICULT_BATTLES = (float) cfg.getDouble("malusChanceReductionMultiplierForDifficultBattles");
            MALUS_CHANCE_REDUCTION_FOR_HIGH_DAMAGE_RATIO = (float) cfg.getDouble("malusChanceReductionMultiplierForHighDamageRatio");

            Trait.Teir.Notable.init(cfg);
            Trait.Teir.Wellknown.init(cfg);
            Trait.Teir.Famous.init(cfg);
            Trait.Teir.Legendary.init(cfg);

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
