package starship_legends;

import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.json.JSONException;
import org.json.JSONObject;
import starship_legends.hullmods.Reputation;

import java.util.Random;
import java.awt.Color;
import java.util.Set;

public class Trait {
    public enum Teir {
        Notable, Wellknown, Famous, Legendary, UNKNOWN;

        long xpToGuaranteeNewTrait = 1;
        float effectMultiplier = 0;

        public float getXpToGuaranteeNewTrait() { return xpToGuaranteeNewTrait; }
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
                        return "Officers, crewmen, and everyday spacers have often heard of the" +
                                " %s, and share stories of its exploits and misadventures. Sometimes the reputation it has" +
                                " earned is warranted based on the physical properties of the ship, but often, the" +
                                " superstitions and culture among its crew are enough to influence its performance.";
                    case Legendary:
                        return "Even planet-bound children tell" +
                                " stories of the exploits and misadventures of the %s and its crew. Sometimes the reputation" +
                                " it has earned is warranted based on the physical properties of the ship, but often, the" +
                                " superstitions and culture among its crew are enough to influence its performance.";
                }
            } else {
                switch (this) {
                    case Notable:
                        return "Your officers have informed you that the %s has displayed uncommon traits for a ship" +
                                " of its class.";
                    case Wellknown:
                        return "Your fleet members often tell stories about the %s.";
                    case Famous:
                        return "Officers, crewmen, and everyday spacers have often heard of the" +
                                " %s, and share stories of its exploits and misadventures.";
                    case Legendary:
                        return "Even planet-bound children tell stories of the exploits and misadventures of the %s.";
                }
            }

            return "ERROR: UNKNOWN TRAIT TIER FLAVOR TEXT";
        }

        public void init(JSONObject cfg) throws JSONException {
            JSONObject o = cfg.getJSONObject(name().toLowerCase());
            xpToGuaranteeNewTrait = Math.max(1, o.getLong("xpToGuaranteeNewTrait"));
            effectMultiplier = (float) o.getDouble("effectMultiplier");
        }
    }

    public static int getTraitLimit() { return ModPlugin.TRAITS_PER_TIER * ModPlugin.TIER_COUNT; }

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

    public void addParagraphTo(TooltipMakerAPI tooltip, Teir teir, int loyaltyEffectAdjustment, boolean requiresCrew, ShipAPI.HullSize hullSize) {
        float effect = getEffect(teir, loyaltyEffectAdjustment, hullSize);
        tooltip.addPara("  " + getName(requiresCrew) + ": %s " + getType().getEffectDescription(), 1, getHighlightColor(), getEffectValueString(effect));
    }

    public String getTypeId() {
        return getType().getId();
    }

    public Set<String> getTags() {
        return getType().getTags();
    }

    public float getEffect(Teir teir, int loyaltyEffectAdjustment, ShipAPI.HullSize hullSize) {
        float baseBonus = (getType().getBaseBonus()
                * (getTags().contains(TraitType.Tags.FLAT_EFFECT) ? Reputation.getFlatEffectMult(hullSize) : 1));
        return baseBonus * effectSign * teir.getEffectMultiplier()
                + baseBonus * loyaltyEffectAdjustment;
    }
}
