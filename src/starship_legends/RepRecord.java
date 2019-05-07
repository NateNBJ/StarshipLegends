package starship_legends;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.*;

public class RepRecord {
    static final Saved<Map<String, RepRecord>> INSTANCE_REGISTRY = new Saved<Map<String, RepRecord>>("reputationRecords", new HashMap<String, RepRecord>());

    public static boolean existsFor(String shipID) { return INSTANCE_REGISTRY.val.containsKey(shipID); }
    public static boolean existsFor(ShipAPI ship) { return existsFor(ship.getFleetMemberId()); }
    public static boolean existsFor(FleetMemberAPI ship) { return existsFor(ship.getId()); }
    public static RepRecord get(String shipID) { return INSTANCE_REGISTRY.val.get(shipID); }
    public static RepRecord get(ShipAPI ship) { return get(ship.getFleetMemberId()); }
    public static RepRecord get(FleetMemberAPI ship) { return get(ship.getId()); }
    public static Trait.Teir getTeirFromTraitCount(int count) {
        if(count > 3 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Legendary;
        if(count > 2 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Famous;
        if(count > 1 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Wellknown;
        if(count > 0 * ModPlugin.TRAITS_PER_TIER) return Trait.Teir.Notable;
        else return Trait.Teir.UNKNOWN;
    }

    private List<Trait> traits = new ArrayList<>();
    private Map<String, Integer> opinionsOfOfficers = new HashMap<>();

    public RepRecord(FleetMemberAPI ship) {
        INSTANCE_REGISTRY.val.put(ship.getId(), this);
    }

    public boolean hasMaximumTraits() {
        return getTraits().size() >= Trait.getTraitLimit();
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

    public Trait.Teir getTeir() { return getTeirFromTraitCount(traits.size()); }

    public float getLoyaltyMultiplier(PersonAPI captain) {
        int traitsLeft = Math.min(getTraits().size(), Trait.getTraitLimit());
        int loyaltyEffectAdjustment = (ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault())
                ? getLoyaltyLevel(captain).getTraitAdjustment()
                : 0;

        for(Trait trait : getTraits()) {
            if (traitsLeft <= 0) break;

            traitsLeft--;

            if(trait.getType().equals("loyalty") || trait.getType().equals("loyalty_ai")) {
                return 1 + trait.getEffect(RepRecord.getTeirFromTraitCount(traitsLeft--), loyaltyEffectAdjustment, null) * 0.01f;
            }
        }

        return 1;
    }

    public static float getXpToGuaranteeNewTrait(FleetMemberAPI ship) {
        if(!RepRecord.existsFor(ship)) return Trait.Teir.Notable.getXpToGuaranteeNewTrait();

        RepRecord rr = RepRecord.get(ship);
        int increase = 1;

        for (RepChange c : CampaignScript.pendingRepChanges.val) if(c.ship == ship && c.newTrait != null) increase++;

        return getTeirFromTraitCount(rr.traits.size() + increase).getXpToGuaranteeNewTrait();
    }
}
