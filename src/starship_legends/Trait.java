package starship_legends;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import starship_legends.hullmods.Reputation;

import java.util.ArrayList;
import java.util.List;
import java.awt.Color;
import java.util.Objects;
import java.util.Set;

public class Trait implements Comparable<Trait> {
    public enum Tier {
        Notable, Wellknown, Famous, Legendary, UNKNOWN;

        float traitChancePerXp = 1;
        float effectMultiplier = 0;

        public float getTraitChancePerXp() { return traitChancePerXp; }
        public float getEffectMultiplier() {
            return effectMultiplier;
        }
        public String getHullModID() {
            return "sun_sl_" + this.name().toLowerCase();
        }
        public String getIconPath() { return "sun_sl/graphics/hullmods/" + name().toLowerCase() + ".png";}
        public String getDisplayName() {
            return this == Wellknown ? "Well-known" : name();
        }
        public String getFlavorText(boolean shipRequiresCrew) {
            if (shipRequiresCrew) {
                switch (this) {
                    case Notable:
                        return "The %s and its crew have a history together which has led the crew to attribute" +
                                " certain characteristics to the ship. Sometimes this reputation is warranted based on the" +
                                " physical properties of the ship, but often, the superstitions and culture among its crew" +
                                " are enough to influence its performance.";
                    case Wellknown:
                        return "Your fleet members tell stories" +
                                " about the exploits and misadventures of the %s and its crew, giving it a certain reputation." +
                                " Sometimes this reputation is warranted based on the physical properties of the ship, but" +
                                " often, the superstitions and culture among its crew are enough to influence its performance.";
                    case Famous:
                        return "Officers, crewmen, and everyday spacers have often heard of the %s and its crew," +
                                " sharing stories of exploits and misadventure. Sometimes the reputation the ship has" +
                                " earned is warranted based on its physical properties, but often, the" +
                                " superstitions and culture among its crew are enough to influence its performance.";
                    case Legendary:
                        return "Even planet-bound children tell" +
                                " stories of the exploits and misadventures of the %s and its crew. Sometimes the reputation" +
                                " the ship has earned is warranted based on its physical properties, but often, the" +
                                " superstitions and culture among its crew are enough to influence its performance.";
                }
            } else {
                switch (this) {
                    case Notable:
                        return "Your officers have informed you that the %s has displayed uncommon traits for a ship" +
                                " of its class. The AI persona controlling it has started showing a few quirks of its own," +
                                " for better or worse. Your AI specialist assures you that it's nothing to worry about.";
                    case Wellknown:
                        return "Members of your fleet often tell stories which include the %s and its AI persona," +
                                " giving it a certain reputation. Sometimes this reputation is warranted based on the" +
                                " physical properties of the ship, but it seems the AI persona has also integrated" +
                                " thoroughly enough with the ship to affect its performance.";
                    case Famous:
                        return "Officers, crewmen, and everyday spacers have often heard of the %s and its AI Persona," +
                                " sharing stories of exploits and misadventure. Sometimes the reputation the ship has" +
                                " earned is warranted based on its physical properties, but it seems the AI persona has" +
                                " also integrated thoroughly enough with the ship to affect its performance.";
                    case Legendary:
                        return "Even planet-bound children tell stories of the exploits and misadventures of the %s," +
                                " and the AI persona in control of it is often a prominent character in these stories." +
                                " Sometimes the reputation the ship has earned is warranted based on its physical" +
                                " properties, but it seems the AI persona has also integrated thoroughly enough with" +
                                " the ship to affect its performance.";
                }
            }

            return "ERROR: UNKNOWN TRAIT TIER FLAVOR TEXT";
        }

        public void init(JSONObject cfg) throws JSONException {
            JSONObject o = cfg.getJSONObject(name().toLowerCase());
            traitChancePerXp = (float) o.getDouble("traitChancePerXp");
            effectMultiplier = (float) o.getDouble("effectMult");
        }
    }

    public static int getTraitLimit() { return ModPlugin.TRAITS_PER_TIER * ModPlugin.TIER_COUNT; }
    public static List<Trait> getAll() {
        List<Trait> traits = new ArrayList<>();

        for(TraitType type : TraitType.getAll()) {
            if(type.getTrait(false) != null) traits.add(type.getTrait(false));
            if(type.getTrait(true) != null) traits.add(type.getTrait(true));
        }

        return traits;
    }

    String typeID;
    int effectSign;

    public Trait(TraitType type, int effectSign) {
        typeID = type.getId();
        this.effectSign = effectSign;
    }

    public TraitType getType() { return TraitType.get(typeID); }

    public String getName(boolean requiresCrew) {
        return getType().getName(effectSign < 0, requiresCrew);
    }

    public String getLowerCaseName(boolean requiresCrew) {
        return getType().getName(effectSign < 0, requiresCrew).toLowerCase().replace("ai persona", "AI persona");
    }

    public String getDescPrefix(boolean requiresCrew) {
        return getType().getDescriptionPrefix(effectSign < 0, requiresCrew);
    }

    public String getEffectValueString(float percent) {
        return (percent >= 0 ? "+" : "") + Misc.getRoundedValueMaxOneAfterDecimal(percent)
                + (getTags().contains(TraitType.Tags.FLAT_EFFECT) ? "" : "%");
    }

    public Color getHighlightColor() {
        switch (effectSign) {
            case 1: return Misc.getHighlightColor();
            case -1: return Misc.getNegativeHighlightColor();
            default: return null;
        }
    }

    public void addParagraphTo(TooltipMakerAPI tooltip, Tier tier, int loyaltyEffectAdjustment, boolean requiresCrew, ShipAPI.HullSize hullSize, boolean useBullet, boolean isFleetTrait) {
        float effect = getEffect(tier, loyaltyEffectAdjustment, hullSize)
                * (isFleetTrait ? ModPlugin.FLEET_TRAIT_EFFECT_MULT : 1);
        String bullet = useBullet ? BaseIntelPlugin.BULLET : "  ";
        tooltip.addPara(bullet + getName(requiresCrew) + ": %s " + getType().getEffectDescription(), 1, getHighlightColor(), getEffectValueString(effect));
    }

    public String getTypeId() {
        return getType().getId();
    }

    public Set<String> getTags() {
        return getType().getTags();
    }

    public int getEffectSign() { return effectSign; }

    public float getEffect(Tier tier, int loyaltyEffectAdjustment, ShipAPI.HullSize hullSize) {
        float baseBonus = (getType().getBaseBonus()
                * ((getTags().contains(TraitType.Tags.FLAT_EFFECT) || (getTags().contains(TraitType.Tags.FLAT_PERCENT)))
                        ? Reputation.getFlatEffectMult(hullSize) : 1));
        return (baseBonus * effectSign * tier.getEffectMultiplier() + baseBonus * loyaltyEffectAdjustment)
                * ModPlugin.GLOBAL_EFFECT_MULT;
    }

    @Override
    public String toString() {
        return (effectSign > 0 ? "+" : "-") + typeID;
    }

    @Override
    public int compareTo(@NotNull Trait trait) {
        return this.toString().compareTo(trait.toString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trait trait = (Trait) o;
        return effectSign == trait.effectSign &&
                typeID.equals(trait.typeID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeID, effectSign);
    }
}
