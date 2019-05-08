package starship_legends;

import org.json.JSONException;
import org.json.JSONObject;

public enum LoyaltyLevel {
    OPENLY_INSUBORDINATE, INSUBORDINATE, DOUBTFUL, INDIFFERENT, CONFIDENT, LOYAL, FIERCELY_LOYAL, UNKNOWN;

    String name, preposition, traitAdjustDesc;
    float crDecay, baseImproveChance, baseWorsenChance;
    int traitAdjustment;

    public String getName() {
        return name;
    }
    public String getPreposition() {
        return preposition;
    }
    public String getTraitAdjustDesc() {
        return traitAdjustDesc;
    }
    public float getCrDecayMult() {
        return crDecay;
    }
    public float getBaseImproveChance() {
        return baseImproveChance;
    }
    public float getBaseWorsenChance() {
        return baseWorsenChance;
    }
    public int getTraitAdjustment() { return traitAdjustment; }
    public boolean isAtBest() { return getIndex() == ModPlugin.LOYALTY_LIMIT; }
    public boolean isAtWorst() { return getIndex() == -ModPlugin.LOYALTY_LIMIT; }
    public int getIndex() { return ordinal() - ModPlugin.LOYALTY_LIMIT; }

    public void init(JSONObject o) throws JSONException {
        name = o.getString("name");
        preposition = o.getString("preposition");
        traitAdjustDesc = o.getString("trait_adjust_desc");

        crDecay = (float) o.getDouble("cr_decay");
        baseImproveChance = (float) o.getDouble("base_improve_chance");
        baseWorsenChance = (float) o.getDouble("base_worsen_chance");

        traitAdjustment = o.getInt("trait_adjustment");
    }
}
