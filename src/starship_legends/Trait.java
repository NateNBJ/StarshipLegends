package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
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
import java.util.List;
import java.util.*;

import static com.fs.starfarer.api.combat.WeaponAPI.WeaponType.*;

public class Trait implements Comparable<Trait> {
    public enum Tier {
        UNKNOWN, Rumored, Notable, Wellknown, Famous, Legendary;

        int xpRequired = 1;
        float effectMultiplier = 0;
        String name = "ERROR", crewedFlavorText = "ERROR", aiFlavorText = "ERROR", bioFlavorText = "ERROR";

        public int getXpRequired() { return xpRequired; }
        public float getEffectMultiplier() {
            return effectMultiplier;
        }
        public String getHullModID() {
            return "sun_sl_" + this.name().toLowerCase();
        }
        public String getIconPath() { return "sun_sl/graphics/hullmods/" + name().toLowerCase() + ".png";}
        public String getDisplayName() {
            return name;
        }
        public String getFlavorText(boolean shipRequiresCrew, boolean shipIsBiological) {
            if(shipIsBiological) return bioFlavorText;

            return shipRequiresCrew ? crewedFlavorText : aiFlavorText;
        }
        public String getIcon() {
            return Global.getSettings().getSpriteName("starship_legends", name().toLowerCase());
        }
        public String getIntelIcon() {
            return Global.getSettings().getSpriteName("starship_legends", name().toLowerCase() + "Intel");
        }

        public void init(JSONObject cfg) throws JSONException {
            xpRequired = (int) cfg.getDouble("xpRequiredOnAverage");
            effectMultiplier = (float) cfg.getDouble("effectMult");
            name = cfg.getString("name");
            crewedFlavorText = cfg.getString("crewedFlavorText");
            aiFlavorText = cfg.getString("aiFlavorText");
            bioFlavorText = cfg.getString("bioFlavorText");

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
    public String getName(boolean requiresCrew, boolean biological) {
        return getType().getName(effectSign < 0, requiresCrew, biological);
    }
    public String getLowerCaseName(boolean requiresCrew, boolean biological) {
        return getType().getName(effectSign < 0, requiresCrew, biological).toLowerCase().replace("ai persona", "AI persona");
    }
    public String getDescPrefix(boolean requiresCrew, boolean biological) {
        return getType().getDescriptionPrefix(effectSign < 0, requiresCrew, biological);
    }
    public String getDescPrefix(boolean requiresCrew, boolean biological, String previousPrefix) {
        String retVal = getType().getDescriptionPrefix(effectSign < 0, requiresCrew, biological).toLowerCase();
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
    public String getDescription(Tier tier, int loyaltyEffectAdjustment, ShipAPI.HullSize size) {

        if(typeID.equals("phase_mad") || typeID.equals("cursed")) {
            return getDescription();
        } else {
            float effect = Math.max(ModPlugin.MINIMUM_EFFECT_REDUCTION_PERCENT,
                    getEffect(tier, loyaltyEffectAdjustment, size));
            String effectStr = Misc.getRoundedValueMaxOneAfterDecimal(Math.abs(effect))
                    + (getTags().contains(TraitType.Tags.FLAT_EFFECT) ? "" : "%%");

            return (effectSign * getType().getBaseBonus() > 0 ? "increases " : "decreases ")
                    + getType().getEffectDescription() + " by " + effectStr;
        }
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
    public void addParagraphTo(TooltipMakerAPI tooltip, Tier tier, int loyaltyEffectAdjustment, boolean requiresCrew, boolean biological, ShipAPI.HullSize hullSize, boolean useBullet, boolean isFleetTrait) {
        String bullet = useBullet ? BaseIntelPlugin.BULLET : "  ";

        if(tier == Tier.Rumored) {
            tooltip.addPara(bullet + getName(requiresCrew, biological) + ": %s " + getType().getEffectDescription(),
                    1, Misc.getGrayColor(), getHighlightColor(),
                    getEffectValueString(getEffectSign() * getType().getBaseBonus() * 0.0000001f));
        } else {
            float effect = Math.max(ModPlugin.MINIMUM_EFFECT_REDUCTION_PERCENT, getEffect(tier, loyaltyEffectAdjustment, hullSize)
                    * (isFleetTrait ? ModPlugin.FLEET_TRAIT_EFFECT_MULT : 1));
            tooltip.addPara(bullet + getName(requiresCrew, biological) + ": %s " + getType().getEffectDescription(),
                    1, getHighlightColor(), getEffectValueString(effect));
        }
    }
    public void addParagraphTo(TextPanelAPI textPanel, Tier tier, int loyaltyEffectAdjustment, boolean requiresCrew, boolean biological, ShipAPI.HullSize hullSize, boolean useBullet, boolean isFleetTrait) {
        addParagraphTo(textPanel, tier, loyaltyEffectAdjustment, requiresCrew, biological, hullSize, useBullet, isFleetTrait, "");
    }
    public void addParagraphTo(TextPanelAPI textPanel, Tier tier, int loyaltyEffectAdjustment, boolean requiresCrew, boolean biological, ShipAPI.HullSize hullSize, boolean useBullet, boolean isFleetTrait, String prefix) {
        String bullet = useBullet ? BaseIntelPlugin.BULLET : "  ";

        if(tier == Tier.Rumored) {
            textPanel.addPara(bullet + prefix + getName(requiresCrew, biological) + ": %s " + getType().getEffectDescription(),
                    Misc.getGrayColor(), getHighlightColor(),
                    getEffectValueString(getEffectSign() * getType().getBaseBonus() * 0.0000001f));
        } else {
            float effect = Math.max(ModPlugin.MINIMUM_EFFECT_REDUCTION_PERCENT, getEffect(tier, loyaltyEffectAdjustment, hullSize)
                    * (isFleetTrait ? ModPlugin.FLEET_TRAIT_EFFECT_MULT : 1));
            textPanel.addPara(bullet + prefix + getName(requiresCrew, biological) + ": %s " + getType().getEffectDescription(),
                    getHighlightColor(), getEffectValueString(effect));
        }
    }
    public void addComparisonParagraphsTo(TextPanelAPI textPanel, FleetMemberAPI ship, Trait traitToCompare) {
        RepRecord rep = RepRecord.get(ship);
        PersonAPI captain = ship.getCaptain();
        boolean requiresCrew = Util.isShipCrewed(ship);
        boolean biological = Util.isShipBiological(ship);
        ShipAPI.HullSize size = ship.getHullSpec().getHullSize();
        int traitsLeft = rep.getTraits().size();
        int loyaltyEffectAdjustment = 0;

        if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault()) {
            loyaltyEffectAdjustment = Util.isNonIntegratedAiCore(captain) ? 0 : rep.getLoyalty(captain).getTraitAdjustment();
        }

        for(Trait trait : rep.getTraits()) {
            if (traitsLeft <= 0) break;

            Trait.Tier tier = RepRecord.getTierFromTraitCount(traitsLeft--);

            if(this.equals(trait)) {
                textPanel.addPara("%s currently " + trait.getDescription(tier, loyaltyEffectAdjustment, size),
                        Misc.getGrayColor(), Misc.getHighlightColor(), trait.getName(requiresCrew, biological));

                textPanel.addPara("%s would " + traitToCompare.getDescription(tier, loyaltyEffectAdjustment, size).replace("ses ", "se "),
                        Misc.getGrayColor(), Misc.getHighlightColor(), traitToCompare.getName(requiresCrew, biological));
            }
        }
    }
    public void addComparisonParagraphsTo(TooltipMakerAPI info, FleetMemberAPI ship, Trait traitToCompare) {
        RepRecord rep = RepRecord.get(ship);
        PersonAPI captain = ship.getCaptain();
        boolean requiresCrew = Util.isShipCrewed(ship);
        boolean biological = Util.isShipBiological(ship);
        ShipAPI.HullSize size = ship.getHullSpec().getHullSize();
        int traitsLeft = rep.getTraits().size();
        int loyaltyEffectAdjustment = 0;

        if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault()) {
            loyaltyEffectAdjustment = Util.isNonIntegratedAiCore(captain) ? 0 : rep.getLoyalty(captain).getTraitAdjustment();
        }

        for(Trait trait : rep.getTraits()) {
            if (traitsLeft <= 0) break;

            Trait.Tier tier = RepRecord.getTierFromTraitCount(traitsLeft--);

            if(this.equals(trait)) {
//                info.setBulletedListMode(BaseIntelPlugin.BULLET);
                info.addPara("%s currently " + trait.getDescription(tier, loyaltyEffectAdjustment, size),
                        10, Misc.getTextColor(), Misc.getHighlightColor(), trait.getName(requiresCrew, biological));
                info.addPara("%s would " + traitToCompare.getDescription(tier, loyaltyEffectAdjustment, size).replace("ses ", "se "),
                        10, Misc.getTextColor(), Misc.getHighlightColor(), traitToCompare.getName(requiresCrew, biological));
//                info.setBulletedListMode(null);
            }
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
        float baseBonus = getType().getBaseBonus();

        if(getTags().contains(TraitType.Tags.FLAT_EFFECT) || getTags().contains(TraitType.Tags.FLAT_PERCENT)) {
            baseBonus *= Reputation.getFlatEffectMult(hullSize);
        }

        float effect = baseBonus * effectSign * tier.getEffectMultiplier() + baseBonus * loyaltyEffectAdjustment;

        return Math.max(ModPlugin.MINIMUM_EFFECT_REDUCTION_PERCENT, effect * ModPlugin.GLOBAL_EFFECT_MULT);
    }
    public boolean isRelevantFor(FleetMemberAPI ship) { return isRelevantFor(ship, true); }
    public boolean isRelevantFor(FleetMemberAPI ship, boolean allowCrewTrait) {
        if(ship == null || ship.getHullSpec() == null) return false;

        ShipHullSpecAPI hull = ship.getHullSpec();
        ShieldAPI.ShieldType shieldType = hull.getShieldType();
        TraitType type = getType();
        Set<String> permanentMods = new HashSet<>(hull.getBuiltInMods());
        permanentMods.addAll(ship.getVariant().getSMods());
        permanentMods.addAll(ship.getVariant().getPermaMods());

        for(String tag : type.getTags()) {
            switch (tag) {
                case TraitType.Tags.SYSTEM:
                    ShipSystemSpecAPI system = Global.getSettings().getShipSystemSpec(hull.getShipSystemId());

                    if(system == null) {
                        return false;
                    } else if(system.getRegen(null) > 0) {
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
                    if(!Util.isPhaseShip(ship)) return false;
                    break;
                case TraitType.Tags.NO_AI:
                    if(!Util.isShipCrewed(ship)) return false;
                    break;
                case TraitType.Tags.NO_BIO:
                    if(!Util.isShipBiological(ship)) return false;
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
            for (String mod : permanentMods) {
                if (type.getIncompatibleBuiltInHullmods().contains(mod)) return false;
            }
        }

        if(!type.getRequiredBuiltInHullmods().isEmpty()) {
            if (!permanentMods.containsAll(type.getRequiredBuiltInHullmods())) {
                return false;
            }
        }

        return true;
    }
    public boolean isBonus() { return effectSign > 0; }
    public boolean isMalus() { return effectSign < 0; }
    public void applyEffect(MutableShipStatsAPI stats, FleetMemberAPI ship, boolean isFighter, String id, float effectPercent) {
        if(TraitType.EFFECT_REGISTRY.containsKey(getTypeId())) {
            try {
                TraitType.Effect effect = TraitType.EFFECT_REGISTRY.get(getTypeId());

                if((isFighter == effect.isAppliedToFighters())) {
                    effect.apply(stats, ship, id, effectPercent);
                }
            } catch (Exception e) {
                ModPlugin.reportCrash(e, false);
            }
        }
    }

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
