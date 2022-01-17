package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipSystemSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.thoughtworks.xstream.XStream;
import org.json.JSONException;
import org.json.JSONObject;
import starship_legends.hullmods.Reputation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.fs.starfarer.api.combat.WeaponAPI.WeaponType.*;

public class Trait implements Comparable<Trait> {
    public enum Tier {
        UNKNOWN, Rumored, Notable, Wellknown, Famous, Legendary;

        int xpRequired = 1;
        float effectMultiplier = 0;

        public int getXpRequired() { return xpRequired; }
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
        public String getIcon() {
            return Global.getSettings().getSpriteName("starship_legends", name().toLowerCase());
        }

        public void init(JSONObject cfg) throws JSONException {
            JSONObject o = cfg.getJSONObject(name().toLowerCase());
            xpRequired = (int) o.getDouble("xpRequiredOnAverage");
            effectMultiplier = (float) o.getDouble("effectMult");

            if(xpRequired <= 0) xpRequired = Integer.MAX_VALUE;
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

    public static void configureXStream(XStream x) {
        x.alias("sun_sl_t", Trait.class);
        x.aliasAttribute(Trait.class, "typeID", "t");
        x.aliasAttribute(Trait.class, "effectSign", "e");
    }
    String typeID;
    int effectSign;

    public Trait(TraitType type, boolean isMalus) {
        typeID = type.getId();
        this.effectSign = isMalus ? -1 : 1;
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

    public String getDescPrefix(boolean requiresCrew, String previousPrefix) {
        String retVal = getType().getDescriptionPrefix(effectSign < 0, requiresCrew).toLowerCase();
        String firstWordOfPrev = previousPrefix.split(" ")[0].toLowerCase();

        if(retVal.startsWith(firstWordOfPrev)) retVal = retVal.replace(firstWordOfPrev, "").trim();

        if(!retVal.equals("")) retVal = " " + retVal;

        return retVal;
    }

    public String getDescription() {
        if(typeID.equals("phase_mad")) return "causes spontaneous malfunctions while phased";
        else if(typeID.equals("cursed")) return "causes spontaneous malfunctions";
        else return (effectSign * getType().getBaseBonus() > 0 ? "increases " : "decreases ") + getType().getEffectDescription();
    }

    public String getParentheticalDescription() {
        return (effectSign * getType().getBaseBonus() > 0 ? "(+" : "(-") + getType().getEffectDescription() + ")";
    }

    public String getEffectValueString(float percent) {
        return (percent >= 0 ? "+" : "-") + Misc.getRoundedValueMaxOneAfterDecimal(Math.abs(percent))
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
        String bullet = useBullet ? BaseIntelPlugin.BULLET : "  ";

        if(tier == Tier.Rumored) {
            tooltip.addPara(bullet + getName(requiresCrew) + ": %s " + getType().getEffectDescription(),
                    1, Misc.getGrayColor(), getHighlightColor(),
                    getEffectValueString(getEffectSign() * getType().getBaseBonus() * 0.0000001f));
        } else {
            float effect = Math.max(ModPlugin.MINIMUM_EFFECT_REDUCTION_PERCENT, getEffect(tier, loyaltyEffectAdjustment, hullSize)
                    * (isFleetTrait ? ModPlugin.FLEET_TRAIT_EFFECT_MULT : 1));
            tooltip.addPara(bullet + getName(requiresCrew) + ": %s " + getType().getEffectDescription(),
                    1, getHighlightColor(), getEffectValueString(effect));
        }
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

    public boolean isRelevantFor(FleetMemberAPI ship) { return isRelevantFor(ship, true); }

    public boolean isRelevantFor(FleetMemberAPI ship, boolean allowCrewTrait) {
        if(ship == null || ship.getHullSpec() == null) return false;

        ShipHullSpecAPI hull = ship.getHullSpec();
        ShieldAPI.ShieldType shieldType = hull.getShieldType();
        TraitType type = getType();
        ShipSystemSpecAPI system = Global.getSettings().getShipSystemSpec(hull.getShipSystemId());

        for(String tag : type.getTags()) {
            switch (tag) {
                case TraitType.Tags.SYSTEM:
                    if(system.getRegen(null) > 0) {
                        if(type.getId().equals("system_cooldown")) return false;
                    } else if(system.getCooldown(null) > 1) {
                        if(type.getId().equals("system_regen_rate")) return false;
                    } else return false;
                    break;
                case TraitType.Tags.DMOD:
                    if(!hull.isDefaultDHull()) return false;
                    break;
                case TraitType.Tags.LOYALTY:
                    if(!ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) return false;
                    break;
                case TraitType.Tags.CREW:
                    if(!allowCrewTrait) return false;
                    break;
                case TraitType.Tags.CARRIER:
                    if(!hull.getHints().contains(ShipHullSpecAPI.ShipTypeHints.CARRIER)) return false;
                    break;
                case TraitType.Tags.SHIELD:
                    if(shieldType != ShieldAPI.ShieldType.FRONT && shieldType != ShieldAPI.ShieldType.OMNI)
                        return false;
                    break;
                case TraitType.Tags.CLOAK:
                    if(shieldType != ShieldAPI.ShieldType.PHASE) return false;
                    break;
                case TraitType.Tags.NO_AI:
                    if(hull.getMinCrew() <= 0) return false;
                    break;
                case TraitType.Tags.ATTACK:
                    boolean hasWeaponSlot = false;

                    for(WeaponSlotAPI slot : hull.getAllWeaponSlotsCopy()) {
                        switch (slot.getWeaponType()) {
                            case SYSTEM: case DECORATIVE: case STATION_MODULE: case LAUNCH_BAY: continue;
                            default: hasWeaponSlot = true; break;
                        }

                        if(hasWeaponSlot) break;
                    }

                    if(!hasWeaponSlot) return false;
                    break;
                case TraitType.Tags.TURRET:
                    boolean hasTurretSlot = false;

                    for(WeaponSlotAPI slot : hull.getAllWeaponSlotsCopy()) {
                        switch (slot.getWeaponType()) {
                            case SYSTEM: case DECORATIVE: case STATION_MODULE: case LAUNCH_BAY: continue;
                            default: hasTurretSlot = slot.isTurret(); break;
                        }

                        if(hasTurretSlot) break;
                    }

                    if(!hasTurretSlot) return false;
                    break;
                case TraitType.Tags.MISSILE:
                    if(Util.getFractionOfFittingSlots(hull, MISSILE, COMPOSITE, SYNERGY) < 0.2f) return false;
                case TraitType.Tags.BALLISTIC:
                    if(Util.getFractionOfFittingSlots(hull, BALLISTIC, COMPOSITE, HYBRID) < 0.4f) return false;
                case TraitType.Tags.ENERGY:
                    if(Util.getFractionOfFittingSlots(hull, ENERGY, SYNERGY, HYBRID) < 0.4f) return false;
                case TraitType.Tags.SYNERGY:
                    if(Util.getFractionOfFittingSlots(hull, SYNERGY, ENERGY, MISSILE) < 0.3f) return false;
                case TraitType.Tags.COMPOSITE:
                    if(Util.getFractionOfFittingSlots(hull, COMPOSITE, BALLISTIC, MISSILE) < 0.3f) return false;
                case TraitType.Tags.HYBRID:
                    if(Util.getFractionOfFittingSlots(hull, HYBRID, ENERGY, BALLISTIC) < 0.4f) return false;
                case TraitType.Tags.FLUX:
                    switch (hull.getHullId()) {
                        case "swp_excelsior":
                        case "swp_boss_excelsior":
                        case "swp_excelsior_elyon":
                        case "swp_excelsior_reward":
                            return false;
                    }
                    break;
            }
        }

        if(!type.getIncompatibleBuiltInHullmods().isEmpty()) {
            for (String mod : hull.getBuiltInMods()) {
                if (type.getIncompatibleBuiltInHullmods().contains(mod)) return false;
            }
        }

        if(!type.getRequiredBuiltInHullmods().isEmpty()) {
            if (!hull.getBuiltInMods().containsAll(type.getRequiredBuiltInHullmods())) {
                return false;
            }
        }

        return true;
    }

    public boolean isBonus() { return effectSign > 0; }

    public boolean isMalus() { return effectSign < 0; }

    @Override
    public String toString() {
        return (isBonus() ? "+" : "-") + typeID;
    }

    @Override
    public int compareTo(Trait trait) {
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
