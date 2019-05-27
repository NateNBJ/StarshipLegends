package starship_legends;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.thoughtworks.xstream.XStream;
import org.json.JSONArray;
import org.json.JSONObject;
import starship_legends.hullmods.Reputation;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
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
            LOG_REPUTATION_CALCULATION_FACTORS = true,
            COMPENSATE_FOR_EXPERIENCE_MULT = true,
            USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE = false,
            IGNORE_ALL_MALUSES = false;

    public static int
            TRAITS_PER_TIER = 2,
            DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION = 30;
    public static float
            MAX_XP_FOR_RESERVED_SHIPS = 80000,
            TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS = 0.0f,
            TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS = 0.25f,
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = 0.02f,

            BASE_RATING = 0.5f,
            BONUS_CHANCE_RANDOMNESS = 1.0f,
            BATTLE_DIFFICULTY_MULT = 0.25f,
            DAMAGE_TAKEN_MULT = 1.0f,
            DAMAGE_DEALT_MULT = 0.25f,
            DAMAGE_DEALT_MIN_THRESHOLD = 1.0f,
            BONUS_CHANCE_FOR_RESERVED_SHIPS_MULT = 1.0f,
            TRAIT_POSITION_CHANGE_CHANCE_MULT = 1.0f,

            IMPROVE_LOYALTY_CHANCE_MULT = 1.0f,
            WORSEN_LOYALTY_CHANCE_MULT = 1.0f;

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
            Global.getSector().addTransientScript(script = new CampaignScript());

            Saved.loadPersistentData();

            readSettingsIfNecessary();

            boolean allRepRecordsHaveNoRating = true;

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

                            rep.adjustRatingToward(RepRecord.INITIAL_RATING * (1f - progress)
                                    + rep.getFractionOfBonusEffectFromTraits() * progress, 1);
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
                    BattleReport report = new BattleReport(1, null);
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

            JSONObject cfg = Global.getSettings().loadJSON(SETTINGS_PATH);

            Trait.Teir.Notable.init(cfg);
            Trait.Teir.Wellknown.init(cfg);
            Trait.Teir.Famous.init(cfg);
            Trait.Teir.Legendary.init(cfg);

            USE_RUTHLESS_SECTOR_TO_CALCULATE_BATTLE_DIFFICULTY = cfg.getBoolean("useRuthlessSectorToCalculateBattleDifficulty");
            ENABLE_OFFICER_LOYALTY_SYSTEM = cfg.getBoolean("enableOfficerLoyaltySystem");
            LOG_REPUTATION_CALCULATION_FACTORS = cfg.getBoolean("logReputationCalculationFactors");
            COMPENSATE_FOR_EXPERIENCE_MULT = cfg.getBoolean("compensateForExperienceMult");
            IGNORE_ALL_MALUSES = cfg.getBoolean("ignoreAllMaluses");

            TRAITS_PER_TIER = cfg.getInt("traitsPerTier");
            DAYS_MOTHBALLED_PER_TRAIT_TO_RESET_REPUTATION = cfg.getInt("daysMothballedPerTraitToResetReputation");

            TRAIT_CHANCE_MULT_FOR_RESERVED_COMBAT_SHIPS = (float) cfg.getDouble("traitChanceMultForReservedCombatShips");
            TRAIT_CHANCE_MULT_FOR_RESERVED_CIVILIAN_SHIPS = (float) cfg.getDouble("traitChanceMultForReservedCivilianShips");
            MAX_XP_FOR_RESERVED_SHIPS = (float) cfg.getDouble("maxXpForReservedShips");
            TRAIT_CHANCE_BONUS_PER_PLAYER_LEVEL = (float) cfg.getDouble("traitChanceBonusPerPlayerLevel");

            BASE_RATING = (float) cfg.getDouble("baseRating");
            BATTLE_DIFFICULTY_MULT = (float) cfg.getDouble("battleDifficultyMult");
            DAMAGE_TAKEN_MULT = (float) cfg.getDouble("damageTakenMult");
            DAMAGE_DEALT_MULT = (float) cfg.getDouble("damageDealtMult");
            DAMAGE_DEALT_MIN_THRESHOLD = (float) cfg.getDouble("damageDealtMinThreshold");

            BONUS_CHANCE_RANDOMNESS = (float) cfg.getDouble("bonusChanceRandomness");
            BONUS_CHANCE_FOR_RESERVED_SHIPS_MULT = (float) cfg.getDouble("bonusChanceForReservedShipsMult");
            TRAIT_POSITION_CHANGE_CHANCE_MULT = (float) cfg.getDouble("traitPositionChangeChanceMult");
            USE_RATING_FROM_LAST_BATTLE_AS_BASIS_FOR_BONUS_CHANCE = cfg.getBoolean("useRatingFromLastBattleAsBasisForBonusChance");

            IMPROVE_LOYALTY_CHANCE_MULT = (float) cfg.getDouble("improveLoyaltyChanceMult");
            WORSEN_LOYALTY_CHANCE_MULT = (float) cfg.getDouble("worsenLoyaltyChanceMult");


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
