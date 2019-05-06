package starship_legends;

import com.fs.starfarer.api.Global;
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
        Unknown, Notable, Wellknown, Famous, Legendary;

        long xpToGuaranteeNewTrait = 1;
        float effectMultiplier = 0;

        public float getXpToGuaranteeNewTrait() { return xpToGuaranteeNewTrait; }
        public float getEffectMultiplier() {
            return effectMultiplier;
        }
        public String getHullModID() {
            return "sun_sl_" + this.name().toLowerCase();
        }
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

    public static Trait chooseTrait(FleetMemberAPI ship, Random rand, float fractionDamageTaken, float damageDealtRatio,
                                    float difficulty, boolean wasDeployed, boolean wasDisabled, String msg) {

        RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);

        if(rep.hasMaximumTraits()) return null;

        WeightedRandomPicker<TraitType> picker = TraitType.getPickerCopy(wasDisabled);
        float malusChance = !wasDeployed
                ? ModPlugin.CHANCE_OF_MALUS_WHILE_IN_RESERVE
                : ModPlugin.CHANCE_OF_MALUS_AT_NO_HULL_LOST + fractionDamageTaken
                    * (ModPlugin.CHANCE_OF_MALUS_AT_HALF_HULL_LOST - ModPlugin.CHANCE_OF_MALUS_AT_NO_HULL_LOST);

        //if(wasDeployed && difficulty > 1) malusChance *= (1 / (1 - difficulty)) * ModPlugin.MALUS_CHANCE_REDUCTION_FOR_DIFFICULT_BATTLES;


        boolean traitIsBad = rand.nextFloat() < Math.min(malusChance, ModPlugin.CHANCE_OF_MALUS_AT_HALF_HULL_LOST);

        msg += "             Bonus chance: " + (int)(100 - malusChance * 100) + "% - " + (traitIsBad ? "FAILED" : "SUCCEEDED");

        for(Trait trait : rep.getTraits()) picker.remove(trait.getType());

        for(RepChange change : CampaignScript.pendingRepChanges.val) {
            if(change.ship == ship && change.newTrait != null) picker.remove(change.newTrait.getType());
        }

        while(!picker.isEmpty()) {
            TraitType type = picker.pickAndRemove();
            Trait trait = type.getTrait(traitIsBad);
            boolean traitIsRelevant = true;
            ShieldAPI.ShieldType shieldType = ship.getHullSpec().getShieldType();

            for(String tag : type.getTags()) {
                switch (tag) {
                    case TraitType.Tags.CARRIER:
                        if(!ship.isCarrier()) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.SHIELD:
                        if(shieldType != ShieldAPI.ShieldType.FRONT && shieldType != ShieldAPI.ShieldType.OMNI)
                            traitIsRelevant = false;
                        break;
                    case TraitType.Tags.CLOAK:
                        if(shieldType != ShieldAPI.ShieldType.PHASE) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.SALVAGE_GANTRY:
                        if(!ship.getVariant().hasHullMod("repair_gantry")) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.NO_AI:
                        if(ship.getMinCrew() <= 0) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.DEFENSE:
                        if(traitIsBad && fractionDamageTaken < 0.05f) traitIsRelevant = false;
                        break;
                    case TraitType.Tags.ATTACK:
                        if(traitIsBad && damageDealtRatio > 1) traitIsRelevant = false;
                        break;
                }
            }

            boolean skipCombatLogisticsMismatch = (type.getTags().contains(TraitType.Tags.LOGISTICAL) == wasDeployed)
                    && !traitIsBad && rand.nextFloat() <= 0.75f;

            if(trait != null && traitIsRelevant && !skipCombatLogisticsMismatch)
                return trait;
        }

        return null;
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
                + baseBonus * loyaltyEffectAdjustment * ModPlugin.OFFICER_ADJUSTMENT_TO_TRAIT_EFFECT_MULTIPLIER;
    }
}
