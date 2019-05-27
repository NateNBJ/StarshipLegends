package starship_legends;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import starship_legends.hullmods.Reputation;

import java.util.*;

public class RepRecord {
    static final Saved<Map<String, RepRecord>> INSTANCE_REGISTRY = new Saved<Map<String, RepRecord>>("reputationRecords", new HashMap<String, RepRecord>());
    static final float INITIAL_RATING = 0.5f;

    public static boolean existsFor(String shipID) { return INSTANCE_REGISTRY.val.containsKey(shipID); }
    public static boolean existsFor(ShipAPI ship) { return existsFor(ship.getFleetMemberId()); }
    public static boolean existsFor(FleetMemberAPI ship) { return existsFor(ship.getId()); }
    public static RepRecord get(String shipID) { return INSTANCE_REGISTRY.val.get(shipID); }
    public static RepRecord get(ShipAPI ship) { return get(ship.getFleetMemberId()); }
    public static RepRecord get(FleetMemberAPI ship) { return get(ship.getId()); }
    public static void deleteFor(FleetMemberAPI ship) {
        INSTANCE_REGISTRY.val.remove(ship.getId());

        Reputation.removeShipOfNote(ship.getVariant().getHullVariantId());

        ShipVariantAPI v = ship.getVariant();
        v.removePermaMod("sun_sl_notable");
        v.removePermaMod("sun_sl_wellknown");
        v.removePermaMod("sun_sl_famous");
        v.removePermaMod("sun_sl_legendary");

    }
    public static Trait.Teir getTierFromTraitCount(int count) {
        if(count > 3 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Legendary;
        if(count > 2 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Famous;
        if(count > 1 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Wellknown;
        if(count > 0 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Notable;
        else return Trait.Teir.UNKNOWN;
    }

    private List<Trait> traits = new ArrayList<>();
    private Map<String, Integer> opinionsOfOfficers = new HashMap<>();
    private float rating = INITIAL_RATING;

    public RepRecord(FleetMemberAPI ship) {
        INSTANCE_REGISTRY.val.put(ship.getId(), this);
    }

    public boolean hasMaximumTraits() {
        return getTraits().size() >= Trait.getTraitLimit();
    }

    public boolean hasTrait(Trait trait) {
        for(Trait t : traits) if(t.equals(trait)) return true;

        return false;
    }

    public LoyaltyLevel getLoyaltyLevel(PersonAPI officer) {
        int index = opinionsOfOfficers.containsKey(officer.getId())
                ? opinionsOfOfficers.get(officer.getId()) + ModPlugin.LOYALTY_LIMIT
                : ModPlugin.LOYALTY_LIMIT;
        return LoyaltyLevel.values()[index];
    }

    public void adjustOpinionOfOfficer(PersonAPI officer, int adjustment) {
        int currentVal = opinionsOfOfficers.containsKey(officer.getId()) ? opinionsOfOfficers.get(officer.getId()) : 0;
        int newVal = Math.max(Math.min(currentVal + adjustment, ModPlugin.LOYALTY_LIMIT), -ModPlugin.LOYALTY_LIMIT);

        opinionsOfOfficers.put(officer.getId(), newVal);
    }

    public List<Trait> getTraits() { return traits; }

    public Trait.Teir getTeir() { return getTierFromTraitCount(traits.size()); }

    public float getRating() {
        return rating;
    }

    public float getFractionOfBonusEffectFromTraits() {
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit());
        float goodness = 0, total = 0;

        for(Trait trait : getTraits()) {
            if (traitsLeft <= 0) break;

            float effect = getTierFromTraitCount(traitsLeft--).getEffectMultiplier();

            total += effect;

            if(trait.effectSign > 0) goodness += effect;
        }

        return total <= 0 ? INITIAL_RATING : goodness / total;
    }

    public void adjustRatingToward(float adjustmentRating, float adjustmentWeight) {
        adjustmentRating = Math.max(-1, Math.min(2, adjustmentRating));
        rating = Math.max(-0.001f, Math.min(1.001f, rating * (1f-adjustmentWeight) + adjustmentRating * adjustmentWeight));
    }

    public float getLoyaltyBonus(PersonAPI captain) {
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit());
        int loyaltyEffectAdjustment = (ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault())
                ? getLoyaltyLevel(captain).getTraitAdjustment()
                : 0;

        for(Trait trait : getTraits()) {
            if (traitsLeft <= 0) break;

            traitsLeft--;

            if(trait.getType().equals("loyalty")) {
                return trait.getEffect(RepRecord.getTierFromTraitCount(traitsLeft--), loyaltyEffectAdjustment, null) * 0.01f;
            }
        }

        return 0;
    }

    public static float getXpToGuaranteeNewTrait(FleetMemberAPI ship) {
        if(!RepRecord.existsFor(ship)) return Trait.Teir.Notable.getXpToGuaranteeNewTrait();

        RepRecord rr = RepRecord.get(ship);
        int increase = 1;

        for (RepChange c : CampaignScript.pendingRepChanges.val) if(c.ship == ship && c.trait != null) increase++;

        return getTierFromTraitCount(rr.traits.size() + increase).getXpToGuaranteeNewTrait();
    }
}
