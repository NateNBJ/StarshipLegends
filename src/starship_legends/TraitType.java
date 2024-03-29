package starship_legends;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class TraitType {
    public static abstract class Effect {
        public boolean isAppliedToFighters() { return false; }
        public abstract void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent);
    }
    public static class Tags {
        public static final String
                SUPERSTITION = "superstition",
                LOGISTICAL = "logistical",
                CARRIER = "carrier",
                CREW = "crew",
                ATTACK = "attack",
                SHIELD = "shield",
                CLOAK = "cloak",
                NO_AI = "no_ai",
                NO_BIO = "no_bio",
                FLAT_EFFECT = "flat_effect",
                FLAT_PERCENT = "flat_percent",
                COMBAT = "combat",
                LOYALTY = "loyalty",
                FLUX = "flux",
                TURRET = "turret",
                BALLISTIC = "ballistic",
                ENERGY = "energy",
                MISSILE = "missile",
                SYNERGY = "synergy",
                COMPOSITE = "composite",
                HYBRID = "hybrid",
                DMOD = "dmod",
                SYSTEM = "system",
                DEFENSE = "defense";
    }
    private static final WeightedRandomPicker<TraitType>
            PICKER = new WeightedRandomPicker<>(),
            DISABLED_PICKER = new WeightedRandomPicker<>();
    static final Map<String, TraitType> INSTANCE_REGISTRY = new HashMap<>();
    static final Map<String, Effect> EFFECT_REGISTRY = new HashMap<>();

    public static TraitType get(String id) {
        if(!INSTANCE_REGISTRY.containsKey(id)) throw new IllegalArgumentException("No TraitType exists with the id " + id);
        else return INSTANCE_REGISTRY.get(id);
    }
    public static WeightedRandomPicker<TraitType> getPickerCopy(boolean forDisabledShips) {
        return (forDisabledShips ? DISABLED_PICKER : PICKER).clone();
    }
    public static List<TraitType> getAll() { return new ArrayList<>(INSTANCE_REGISTRY.values()); }


    private String id, desc, bonusName, malusName, bonusDesc, malusDesc,
            bonusNameAI, malusNameAI, bonusDescAI, malusDescAI,
            bonusNameBio, malusNameBio, bonusDescBio, malusDescBio;
    private Set<String> tags = new HashSet<>(), requiredBuiltIns = new HashSet<>(), incompatibleBuiltIns = new HashSet<>();
    private float baseBonus, baseChance;
    private Trait bonus, malus;
    private boolean applicableToFleets;

    public String getId() { return id; }
    public String getEffectDescription() { return desc; }
    public Set<String> getTags() { return tags; }
    public Set<String> getRequiredBuiltInHullmods() { return requiredBuiltIns; }
    public Set<String> getIncompatibleBuiltInHullmods() { return incompatibleBuiltIns; }
    public float getBaseBonus() { return baseBonus; }
    public float getBaseChance() { return baseChance; }
    public Trait getTrait(boolean isMalus) { return isMalus ? malus : bonus; }
    public boolean canBePositive() {
        return bonus != null;
    }
    public boolean canBeNegative() {
        return malus != null;
    }
    public String getName(boolean isMalus, boolean requiresCrew, boolean biological) {
        if(biological && (!requiresCrew || !tags.contains(Tags.CREW))) {
            String name = isMalus ? malusNameBio : bonusNameBio;

            if(name != null && !name.isEmpty()) return name;
        }

        return isMalus
                ? (!requiresCrew && !malusNameAI.isEmpty()) ? malusNameAI : malusName
                : (!requiresCrew && !bonusNameAI.isEmpty()) ? bonusNameAI : bonusName;
    }
    public String getDescriptionPrefix(boolean isMalus, boolean requiresCrew, boolean biological) {
        if(biological && (!requiresCrew || !tags.contains(Tags.CREW))) {
            String desc = isMalus ? malusDescBio : bonusDescBio;

            if(desc != null && !desc.isEmpty()) return desc;
        }
            
        return isMalus
                ? (!requiresCrew && !malusDescAI.isEmpty()) ? malusDescAI : malusDesc
                : (!requiresCrew && !bonusDescAI.isEmpty()) ? bonusDescAI : bonusDesc;
    }
    public boolean isApplicableToFleets() { return applicableToFleets; }

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
        bonusNameBio = data.optString("bonus_name_bio", "");
        malusNameBio = data.optString("malus_name_bio", "");
        bonusDescBio = data.optString("bonus_desc_bio", "");
        malusDescBio = data.optString("malus_desc_bio", "");
        desc = data.getString("desc");
        baseBonus = (float)data.getDouble("base_bonus");
        applicableToFleets = data.getBoolean("applicable_to_fleets");
        baseChance = (float)data.getDouble("chance");

        String[] ja = data.getString("tags").replace(" ", "").split(",");
        for(int i = 0; i < ja.length; ++i) tags.add(ja[i].toLowerCase());

        ja = data.getString("required_hullmods").replace(" ", "").split(",");
        for(int i = 0; i < ja.length; ++i) if(!ja[i].isEmpty()) requiredBuiltIns.add(ja[i]);

        ja = data.getString("incompatible_hullmods").replace(" ", "").split(",");
        for(int i = 0; i < ja.length; ++i) if(!ja[i].isEmpty()) incompatibleBuiltIns.add(ja[i]);

        if(!bonusName.isEmpty()) bonus = new Trait(this, false);
        if(!malusName.isEmpty()) malus = new Trait(this, true);

        INSTANCE_REGISTRY.put(id, this);
    }
}
