package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

public class RepTheme {
    private static final Map<String, RepTheme> INSTANCE_REGISTRY = new HashMap<>();
    private static final WeightedRandomPicker<RepTheme> THEME_PICKER = new WeightedRandomPicker<>();

    private final String key;

    Map<Trait, Float> goodTraitFrequency = new TreeMap();
    Map<Trait, Float> badTraitFrequency = new TreeMap();

    void readTraitFrequencies(JSONObject data, boolean isGood) throws JSONException {
        Map<Trait, Float> picker = isGood ? goodTraitFrequency : badTraitFrequency;
        String frequencyKey = isGood ? "goodTraitFrequency" : "badTraitFrequency";

        if(data.has(frequencyKey)) {
            JSONObject entries = data.getJSONObject(frequencyKey);
            Iterator<String> keys = entries.keys();

            while (keys.hasNext()) {
                String key = keys.next();
                TraitType type = TraitType.get(key);

                if(type != null && type.getTrait(!isGood) != null) {
                    float weightMult = (float)entries.getDouble(key);
                    Trait trait = type.getTrait(!isGood);

                    if(picker.containsKey(trait)) {
                        if (weightMult > 0) picker.put(trait, picker.get(trait) * weightMult);
                        else picker.remove(trait);
                    }
                }
            }
        }
    }

    public static RepTheme pickRandomTheme(Random rand) {
        return THEME_PICKER.pick(rand);
    }
    public static RepTheme get(String key) {
        return INSTANCE_REGISTRY.containsKey(key) ? INSTANCE_REGISTRY.get(key) : null;
    }

    RepTheme(String key, float likelihood, JSONObject data) throws JSONException, IOException {
        this.key = key;

        try {
            for(TraitType type : TraitType.getAll()) {
                if(type.canBePositive()) goodTraitFrequency.put(type.getTrait(false), type.getBaseChance());
                if(type.canBeNegative()) badTraitFrequency.put(type.getTrait(true), type.getBaseChance());
            }

            readTraitFrequencies(data, true);
            readTraitFrequencies(data, false);

            INSTANCE_REGISTRY.put(key, this);
            THEME_PICKER.add(this, likelihood);
        } catch (Exception e) {
            Global.getLogger(this.getClass()).error("Error reading theme data: " + key, e);
        }
    }

    public String getKey() { return key; }
    public WeightedRandomPicker<TraitType> createGoodTraitPicker(Random rand) {
        WeightedRandomPicker<TraitType> retVal = new WeightedRandomPicker(rand);

        for(Map.Entry<Trait, Float> e : goodTraitFrequency.entrySet()) retVal.add(e.getKey().getType(), e.getValue());

        return retVal;
    }
    public WeightedRandomPicker<TraitType> createBadTraitPicker(Random rand) {
        WeightedRandomPicker<TraitType> retVal = new WeightedRandomPicker(rand);

        for(Map.Entry<Trait, Float> e : badTraitFrequency.entrySet()) retVal.add(e.getKey().getType(), e.getValue());

        return retVal;
    }
}
