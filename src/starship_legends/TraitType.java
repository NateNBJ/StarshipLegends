package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TraitType {
    public static class Tags {
        public static final String
                LOGISTICAL = "LOGISTICAL",
                DISABLED = "DISABLED",
                DISABLED_ONLY = "DISABLED_ONLY",
                CARRIER = "CARRIER",
                CREW = "CREW",
                ATTACK = "ATTACK",
                SHIELD = "SHIELD",
                CLOAK = "CLOAK",
                NO_AI = "NO_AI",
                FLAT_EFFECT = "FLAT_EFFECT",
                DEFENSE = "DEFENSE";
    }
    private static final WeightedRandomPicker<TraitType>
            PICKER = new WeightedRandomPicker<>(),
            DISABLED_PICKER = new WeightedRandomPicker<>();
    private static final Map<String, TraitType> INSTANCE_REGISTRY = new HashMap<>();

    public static TraitType get(String id) {
        if(!INSTANCE_REGISTRY.containsKey(id)) throw new IllegalArgumentException("No TraitType exists with the id " + id);
        else return INSTANCE_REGISTRY.get(id);
    }
    public static WeightedRandomPicker<TraitType> getPickerCopy(boolean forDisabledShips) {
        return (forDisabledShips ? DISABLED_PICKER : PICKER).clone();
    }

    private String id, desc, bonusName, malusName, bonusDesc, malusDesc,
            bonusNameAI, malusNameAI, bonusDescAI, malusDescAI;
    private Set<String> tags = new HashSet<>(), requiredBuiltIns = new HashSet<>(), incompatibleBuiltIns = new HashSet<>();
    private float baseBonus;
    private Trait bonus, malus;

    public String getId() { return id; }
    public String getEffectDescription() { return desc; }
    public Set<String> getTags() { return tags; }
    public Set<String> getRequiredBuiltInHullmods() { return requiredBuiltIns; }
    public Set<String> getIncompatibleBuiltInHullmods() { return incompatibleBuiltIns; }
    public float getBaseBonus() { return baseBonus; }
    public Trait getTrait(boolean isMalus) { return isMalus ? malus : bonus; }
    public String getName(boolean isMalus, boolean requiresCrew) {
        return isMalus
                ? (!requiresCrew && !malusNameAI.isEmpty()) ? malusNameAI : malusName
                : (!requiresCrew && !bonusNameAI.isEmpty()) ? bonusNameAI : bonusName;
    }
    public String getDescriptionPrefix(boolean isMalus, boolean requiresCrew) {
        return isMalus
                ? (!requiresCrew && !malusDescAI.isEmpty()) ? malusDescAI : malusDesc
                : (!requiresCrew && !bonusDescAI.isEmpty()) ? bonusDescAI : bonusDesc;
    }

    public TraitType(JSONObject data) throws JSONException {
        id = data.getString("id");
        bonusName = data.getString("bonus_name");
        malusName = data.getString("malus_name");
        bonusDesc = data.getString("bonus_desc");
        malusDesc = data.getString("malus_desc");
        bonusNameAI = data.getString("bonus_name_ai");
        malusNameAI = data.getString("malus_name_ai");
        bonusDescAI = data.getString("bonus_desc_ai");
        malusDescAI = data.getString("malus_desc_ai");
        desc = data.getString("desc");
        baseBonus = (float)data.getDouble("base_bonus");

        String[] ja = data.getString("tags").replace(" ", "").split(",");
        for(int i = 0; i < ja.length; ++i) tags.add(ja[i]);

        ja = data.getString("required_built_in_mods").replace(" ", "").split(",");
        for(int i = 0; i < ja.length; ++i) if(!ja[i].isEmpty()) requiredBuiltIns.add(ja[i]);

        ja = data.getString("incompatible_built_in_mods").replace(" ", "").split(",");
        for(int i = 0; i < ja.length; ++i) if(!ja[i].isEmpty()) incompatibleBuiltIns.add(ja[i]);


        if(!bonusName.isEmpty()) bonus = new Trait(this, 1);
        if(!malusName.isEmpty()) malus = new Trait(this, -1);

        float chance = (float)data.getDouble("chance");

        if(tags.contains(Tags.DISABLED) || tags.contains(Tags.DISABLED_ONLY)) DISABLED_PICKER.add(this, chance);
        if(!tags.contains(Tags.DISABLED_ONLY)) PICKER.add(this, chance);

        INSTANCE_REGISTRY.put(id, this);
    }
}
