package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class FactionConfig {
    public class ForcedRepPreset {
        List<Trait> forcedGoodTraits = new LinkedList();
        List<Trait> forcedBadTraits = new LinkedList();
        int defaultNumberOfForcedTraits = Integer.MIN_VALUE;

        public ForcedRepPreset(JSONObject data) throws JSONException {

            if(data.has("defaultNumberOfTraits")) defaultNumberOfForcedTraits = data.getInt("defaultNumberOfTraits");

            readForcedTraits(data, true);
            readForcedTraits(data, false);
        }
        void readForcedTraits(JSONObject preset, boolean isGood) throws JSONException {
            List<Trait> list = isGood ? forcedGoodTraits : forcedBadTraits;
            String key = isGood ? "goodTraits" : "badTraits";

            if(preset.has(key)) {
                JSONArray traits = preset.getJSONArray(key);

                for(int i = 0; i < traits.length(); ++i) {
                    TraitType type = TraitType.get(traits.getString(i));

                    if(type != null && type.getTrait(!isGood) != null) {
                        list.add(type.getTrait(!isGood));
                    }
                }
            }
        }
    }

    private static final Map<String, FactionConfig> INSTANCE_REGISTRY = new HashMap<>();

    static final Map<String, Float> DERELICT_PROBABILITY = new HashMap<>();

    final String factionID;

    boolean useCrewlessTraitNames = false;
    boolean allowFamousFlagshipsInFleets = true;
    boolean allowFamousFlagshipBarEvent = false;
    boolean allowFamousDerelictBarEvent = false;
    String descriptionOverride = null;
    ForcedRepPreset forcedPreset = null;
    Map<Trait, Float> goodTraitFrequency = new TreeMap();
    Map<Trait, Float> badTraitFrequency = new TreeMap();
    Map<String, Float> exclusiveDerelictProbability = new HashMap<>();
    Map<String, ForcedRepPreset> forcedCommanderPresets = new HashMap();

    void readData(JSONObject data) throws JSONException {
        if(data.has("descriptionOverride")) descriptionOverride = data.getString("descriptionOverride");
        if(data.has("useCrewlessTraitNames")) useCrewlessTraitNames = data.getBoolean("useCrewlessTraitNames");
        if(data.has("allowFamousFlagshipsInFleets")) allowFamousFlagshipsInFleets = data.getBoolean("allowFamousFlagshipsInFleets");
        if(data.has("allowFamousFlagshipBarEvent")) allowFamousFlagshipBarEvent = data.getBoolean("allowFamousFlagshipBarEvent");
        if(data.has("allowFamousDerelictBarEvent")) allowFamousDerelictBarEvent = data.getBoolean("allowFamousDerelictBarEvent");

        if(data.has("derelictFrequency")) {
            JSONObject df = data.getJSONObject("derelictFrequency");
            Iterator<String> keys = df.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                addGlobalDerelict(key, (float) df.getDouble(key));
            }
        }

        if(data.has("exclusiveDerelictFrequency")) {
            JSONObject df = data.getJSONObject("exclusiveDerelictFrequency");
            Iterator<String> keys = df.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                addExclusiveDerelict(key, (float)df.getDouble(key));
            }
        }

        readTraitFrequencies(data, true);
        readTraitFrequencies(data, false);

        if(data.has("forcedPreset")) forcedPreset = new ForcedRepPreset(data.getJSONObject("forcedPreset"));

        if(data.has("forcedCommanderPresets")) {
            JSONObject presets = data.getJSONObject("forcedCommanderPresets");

            Iterator<String> keys = presets.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                forcedCommanderPresets.put(key, new ForcedRepPreset(presets.getJSONObject(key)));
            }
        }
    }
    void readTraitFrequencies(JSONObject data, boolean isGood) throws JSONException {
        Map<Trait, Float> picker = isGood ? goodTraitFrequency : badTraitFrequency;
        String frequencyKey = isGood ? "goodTraitFrequency" : "badTraitFrequency";

        if(data.has(frequencyKey)) {
            JSONObject entries = data.getJSONObject(frequencyKey);
            Iterator<String> keys = entries.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                TraitType type = TraitType.get(key);

                if(type != null && type.isApplicableToFleets() && type.getTrait(!isGood) != null) {
                    float weight = (float)entries.getDouble(key);
                    Trait trait = type.getTrait(!isGood);

                    if(weight > 0) picker.put(trait, weight);
                    else if(picker.containsKey(trait)) picker.remove(trait);
                }
            }
        }
    }
    ForcedRepPreset getPreset(PersonAPI commander) {
        if(forcedPreset != null) {
            return forcedPreset;
        } else if(commander != null && forcedCommanderPresets.containsKey(commander.getNameString().trim())) {
            return forcedCommanderPresets.get(commander.getNameString().trim());
        } else return null;
    }
    int getNumberOfTraits(PersonAPI commander) {
        ForcedRepPreset preset = getPreset(commander);


        if(preset != null && preset.defaultNumberOfForcedTraits >= 0) {
            return (int)(preset.defaultNumberOfForcedTraits
                    * (ModPlugin.TRAITS_FOR_FLEETS_WITH_MAX_LEVEL_COMMANDER / (float)ModPlugin.DEFAULT_TRAIT_LIMIT));
        } else if(commander != null && !commander.isDefault()) {
            return (int)(ModPlugin.TRAITS_FOR_FLEETS_WITH_MIN_LEVEL_COMMANDER
                    + (commander.getStats().getLevel() / Math.max(1, Global.getSettings().getFloat("officerMaxLevel")-1))
                    * (ModPlugin.TRAITS_FOR_FLEETS_WITH_MAX_LEVEL_COMMANDER - ModPlugin.TRAITS_FOR_FLEETS_WITH_MIN_LEVEL_COMMANDER));
        } else return ModPlugin.TRAITS_FOR_FLEETS_WITH_NO_COMMANDER;
    }
    void chooseSettingsBasedOnFactionProperties() {
        //useCrewlessTraitNames = true;

        if(!getFaction().isShowInIntelTab()) {
            descriptionOverride = "The ships in this fleet have the following traits:";
            allowFamousFlagshipsInFleets = false;
            allowFamousFlagshipBarEvent = false;
            allowFamousDerelictBarEvent = false;

            return;
        }

        for(String specID : getFaction().getKnownShips()) {
            ShipHullSpecAPI spec = null;

            try {
                spec = Global.getSettings().getHullSpec(specID);
            } catch (Exception e) {
                Global.getLogger(FactionConfig.class).warn("Hull ID " + specID + " not found.");
            }

            if(spec == null || spec.isCivilianNonCarrier() || spec.isDHull()
                    || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.UNBOARDABLE)
                    || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.CIVILIAN)
                    || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.HIDE_IN_CODEX)
                    || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.STATION)
                    || spec.getHints().contains(ShipHullSpecAPI.ShipTypeHints.SHIP_WITH_MODULES)
            ) continue;

            addGlobalDerelict(specID, spec.isPhase() ? 0.5f : 1);
        }
    }

    private static float[] derelictChanceMults = new float[32];
    private static JSONObject defaultData = null, factionConfigs = null;
    private static RepRecord rep = null;

    public static void readDerelictChanceMultipliers(JSONArray list) throws JSONException {
        int start = list.length() - 9;
        // We're reading the last 9 entries, not the first, in-case someone has "overwritten" the list

        for(int i = start; i < list.length() - 1; i++) {
            float chance = (float) list.getDouble(i);
            int at = (i - start) * 4;

            derelictChanceMults[at + 0] = chance;
            derelictChanceMults[at + 1] = chance;
            derelictChanceMults[at + 2] = chance;
            derelictChanceMults[at + 3] = chance;
        }

        derelictChanceMults[31] = (float) list.getDouble(list.length() - 1);
    }
    public static RepRecord getEnemyFleetRep() { return rep; }
    public static void clearEnemyFleetRep() { rep = null; }

    public static float getAdjustedDerelictProbability(float baseWeight, ShipHullSpecAPI spec) {
        return baseWeight * derelictChanceMults[Math.min(31, spec.getFleetPoints())];
    }
    public static boolean hasNotBeenRead() { return INSTANCE_REGISTRY.isEmpty(); }
    public static FactionConfig get(FactionAPI faction) { return get(faction.getId()); }
    public static FactionConfig get(String factionID) {
        if(INSTANCE_REGISTRY.containsKey(factionID)) return INSTANCE_REGISTRY.get(factionID);
        else return INSTANCE_REGISTRY.get(Factions.PLAYER);
    }
    public static void addGlobalDerelict(String hullID, float probabilityWeight) {
        ShipHullSpecAPI spec = null;

        try {
            spec = Global.getSettings().getHullSpec(hullID);
        } catch (Exception e) {
            Global.getLogger(FactionConfig.class).warn("Hull ID " + hullID + " not found.");
        }

        if(spec == null) return;

        probabilityWeight = getAdjustedDerelictProbability(probabilityWeight, spec);

        if(DERELICT_PROBABILITY.containsKey(hullID)) {
            DERELICT_PROBABILITY.put(hullID, Math.min(DERELICT_PROBABILITY.get(hullID), probabilityWeight));
        } else DERELICT_PROBABILITY.put(hullID, probabilityWeight);
    }

    FactionConfig(FactionAPI faction) throws JSONException, IOException {
        this.factionID = faction.getId();

        if(defaultData == null) {
            factionConfigs = Global.getSettings().getMergedJSONForMod("data/config/starship_legends/factionConfigurations.json", ModPlugin.ID);
            defaultData = Global.getSettings().loadJSON("sun_sl/data/defaultFactionReputation.json", ModPlugin.ID);
        }

        readData(defaultData);

        try {
            boolean dataWasFound = false;

            if (faction.getCustom().has("starshipLegendsConfig")) {
                Global.getLogger(this.getClass()).info("Reading custom section for faction: " + faction.getId());
                readData(faction.getCustom().getJSONObject("starshipLegendsConfig"));
                dataWasFound = true;
            }

            if (factionConfigs.has(faction.getId())) {
                Global.getLogger(this.getClass()).info("Reading config file for faction: " + faction.getId());
                readData(factionConfigs.getJSONObject(faction.getId()));
                dataWasFound = true;
            }

            if(!dataWasFound){
                Global.getLogger(this.getClass()).info("No integration data found for faction: " + faction.getId());
                chooseSettingsBasedOnFactionProperties();
            }

            INSTANCE_REGISTRY.put(faction.getId(), this);
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Error reading config for faction: " + faction.getId(), e);
        }
    }

    public FactionAPI getFaction() {
        return Global.getSector().getFaction(factionID);
    }

    public String chooseDerelictHullType(Random rand) {
        WeightedRandomPicker<String> picker = new WeightedRandomPicker<>(rand);

        for(Map.Entry<String, Float> entry : DERELICT_PROBABILITY.entrySet()) {
            if(Global.getSettings().getHullSpec(entry.getKey()) != null) picker.add(entry.getKey(), entry.getValue());
        }

        for(Map.Entry<String, Float> entry : exclusiveDerelictProbability.entrySet()) {
            if(Global.getSettings().getHullSpec(entry.getKey()) != null) picker.add(entry.getKey(), entry.getValue());
        }

        return picker.pick(rand);
    }

    public boolean isCrewlessTraitNamesUsed() {
        return useCrewlessTraitNames;
    }

    public void setUseCrewlessTraitNames(boolean useCrewlessTraitNames) {
        this.useCrewlessTraitNames = useCrewlessTraitNames;
    }

    public boolean isFamousFlagshipAllowedInFleets() {
        return allowFamousFlagshipsInFleets;
    }

    public void setAllowFamousFlagshipsInFleets(boolean allowFamousFlagshipsInFleets) {
        this.allowFamousFlagshipsInFleets = allowFamousFlagshipsInFleets;
    }

    public boolean isFamousFlagshipBarEventAllowed() {
        return allowFamousFlagshipBarEvent;
    }

    public void setAllowFamousFlagshipBarEvent(boolean allowFamousFlagshipBarEvent) {
        this.allowFamousFlagshipBarEvent = allowFamousFlagshipBarEvent;
    }

    public boolean isFamousDerelictBarEventAllowed() {
        return allowFamousDerelictBarEvent;
    }

    public void setAllowFamousDerelictBarEvent(boolean allowFamousDerelictBarEvent) {
        this.allowFamousDerelictBarEvent = allowFamousDerelictBarEvent;
    }

    public String getDescriptionOverride() {
        return descriptionOverride;
    }

    public void setDescriptionOverride(String descriptionOverride) {
        this.descriptionOverride = descriptionOverride;
    }

    public void addExclusiveDerelict(String hullID, float probabilityWeight) {
        ShipHullSpecAPI spec = null;

        try {
            spec = Global.getSettings().getHullSpec(hullID);
        } catch (Exception e) {
            Global.getLogger(FactionConfig.class).warn("Hull ID " + hullID + " not found.");
        }

        if(spec == null) return;

        probabilityWeight = getAdjustedDerelictProbability(probabilityWeight, spec);

        if(exclusiveDerelictProbability.containsKey(hullID)) {
            exclusiveDerelictProbability.put(hullID, (float)Math.min(exclusiveDerelictProbability.get(hullID), probabilityWeight));
        } else exclusiveDerelictProbability.put(hullID, probabilityWeight);
    }
    public RepRecord buildReputation(PersonAPI commander, int traitCount) {
//        int loyalty = (rand.nextFloat() < (0.4f + commander.getStats().getLevel() * 0.025f) ? 1 : -1)
//                * (int)Math.floor(Math.pow(rand.nextFloat(), 0.75f) * (ModPlugin.LOYALTY_LIMIT + 1));

        int loyalty = 0;

        return buildReputation(commander, traitCount, ModPlugin.AVERAGE_FRACTION_OF_GOOD_TRAITS, null, true, loyalty);
    }
    public RepRecord buildReputation(PersonAPI commander, int traitCount, float ratingGoal, FleetMemberAPI ship,
                                     boolean allowCrewTraits, int loyalty) {

        if(traitCount <= 0) return null;

        Random rand = new Random(commander != null && !commander.isDefault()
                ? commander.getNameString().hashCode()
                : getFaction().getDisplayNameLong().hashCode());
        RepRecord retVal = ship == null ? new RepRecord() : new RepRecord(ship);
        WeightedRandomPicker<TraitType> randomGoodTraits = new WeightedRandomPicker(rand);
        WeightedRandomPicker<TraitType> randomBadTraits = new WeightedRandomPicker(rand);
        ForcedRepPreset preset = getPreset(commander);

        for(Map.Entry<Trait, Float> e : goodTraitFrequency.entrySet()) randomGoodTraits.add(e.getKey().getType(), e.getValue());
        for(Map.Entry<Trait, Float> e : badTraitFrequency.entrySet()) randomBadTraits.add(e.getKey().getType(), e.getValue());

        List<Trait> forcedGoodTraits = new LinkedList();
        List<Trait> forcedBadTraits = new LinkedList();

        if(preset != null) {
            forcedGoodTraits.addAll(preset.forcedGoodTraits);
            forcedBadTraits.addAll(preset.forcedBadTraits);
        }

        while (retVal.getTraits().size() < traitCount) {
            float traits = retVal.getTraits().size();
            boolean isBonus = traits <= 2
                    ? rand.nextFloat() < (ModPlugin.AVERAGE_FRACTION_OF_GOOD_TRAITS * (1 - traits/3f) + ratingGoal * (traits/3f))
                    : retVal.getFractionOfBonusEffectFromTraits() <= ratingGoal;

            WeightedRandomPicker<TraitType> randomTraits = isBonus ? randomGoodTraits : randomBadTraits;
            List<Trait> forcedTraits = isBonus ? forcedGoodTraits : forcedBadTraits;
            Trait newTrait = null;

            if(ship != null) {
                newTrait = RepRecord.chooseNewTrait(ship, rand, !isBonus, true, true, allowCrewTraits, randomTraits);
            } else if(preset != null && !forcedTraits.isEmpty()) {
                newTrait = forcedTraits.get(0);
                forcedTraits.remove(0);
            } else if(!randomTraits.isEmpty()) {
                TraitType newType = randomTraits.pickAndRemove();
                newTrait = newType.getTrait(!isBonus);
            }

            if(newTrait == null) break;
            else if(retVal.hasTraitType(newTrait.getType())) continue;
            else retVal.getTraits().add(newTrait);
        }

        if(commander != null) retVal.setLoyalty(commander, loyalty);

        return retVal;
    }
    public void showFleetReputation(InteractionDialogAPI dialog, PersonAPI commander) {
        if(dialog == null || dialog.getTextPanel() == null) return;

        rep = buildReputation(commander, getNumberOfTraits(commander));

        TextPanelAPI panel = dialog.getTextPanel();

        if(rep == null || rep.getTraits().isEmpty() || panel == null) return;

        String desc = "The ships in the opposing fleet have the following traits:";

        if(descriptionOverride != null && !descriptionOverride.isEmpty()) {
            desc = String.format(descriptionOverride, commander != null ? commander.getNameString().trim() : "");
        } else if(commander != null) {
            desc = "The ships in " + commander.getNameString().trim() + "'s fleet are known for having the following traits:";
        }

        if(commander != null && !commander.isDefault() && commander.getPortraitSprite() != null) {
            TooltipMakerAPI tt = panel.beginTooltip();
            tt.beginImageWithText(commander.getPortraitSprite(), 48).addPara(desc, 3);
            tt.addImageWithText(3);
            panel.addTooltip();
        } else {
            panel.addPara(desc);
        }

        Util.showTraits(panel, rep, commander, !useCrewlessTraitNames, ShipAPI.HullSize.DEFAULT);
    }
}
