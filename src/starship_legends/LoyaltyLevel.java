package starship_legends;

import org.json.JSONException;
import org.json.JSONObject;

public enum LoyaltyLevel {
    MUTINOUS, OPENLY_INSUBORDINATE, INSUBORDINATE, DOUBTFUL, INDIFFERENT, CONFIDENT, LOYAL, FIERCELY_LOYAL, INSPIRED, UNKNOWN;

    public static LoyaltyLevel fromInt(int loyaltyInt) {
        int index = Math.max(0, Math.min(ModPlugin.LOYALTY_LIMIT * 2, loyaltyInt + ModPlugin.LOYALTY_LIMIT));

        return LoyaltyLevel.values()[index];
    }

    String name, preposition, traitAdjustDesc, nameAI;
    float crDecay, maxCrReduction, fameGainBonus;
    int traitAdjustment, xpToImprove;

    public String getName() {
        return name;
    }
    public String getAiIntegrationStatusName() {
        return nameAI;
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
    public float getMaxCrReduction() { return maxCrReduction; }
    public float getFameGainBonus() { return fameGainBonus; }
    public int getXpToImprove() {
        return xpToImprove;
    }
    public int getTraitAdjustment() { return traitAdjustment; }
    public boolean isAtBest() { return getIndex() == ModPlugin.LOYALTY_LIMIT; }
    public boolean isAtWorst() { return getIndex() == -ModPlugin.LOYALTY_LIMIT; }
    public int getIndex() { return ordinal() - ModPlugin.LOYALTY_LIMIT; }
    public LoyaltyLevel getOneBetter() {
        return isAtBest() || this == UNKNOWN ? UNKNOWN : LoyaltyLevel.fromInt(ordinal() - ModPlugin.LOYALTY_LIMIT + 1);
    }
    public LoyaltyLevel getOneWorse() {
        return isAtWorst() || this == UNKNOWN ? UNKNOWN : LoyaltyLevel.fromInt(ordinal() - ModPlugin.LOYALTY_LIMIT - 1);
    }

    public void init(JSONObject o) throws JSONException {
        name = o.getString("name");
        nameAI = o.getString("name_ai");
        preposition = o.getString("preposition");
        traitAdjustDesc = o.getString("trait_adjust_desc");

        crDecay = (float) o.getDouble("cr_decay");
        xpToImprove = o.getInt("xp_to_improve");
        maxCrReduction = (float) o.getDouble("max_cr_reduction");
        fameGainBonus = (float) o.getDouble("fame_gain_bonus");

        traitAdjustment = o.getInt("trait_adjustment");
    }
}
