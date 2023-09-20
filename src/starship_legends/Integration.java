package starship_legends;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Integration {
    static Map<String, Boolean> derelictAvailabilityOverride = new HashMap();
    static Map<String, Boolean> flagshipAvailabilityOverride = new HashMap();
    static Set<String> biologicalShips = new HashSet<>();

    public static boolean isFamousDerelictEventAvailableAtMarket(MarketAPI market) {
        FactionConfig cfg = FactionConfig.get(market.getFactionId());

        if(ModPlugin.FAMOUS_DERELICT_BAR_EVENT_CHANCE <= 0) return false;

        if(derelictAvailabilityOverride.containsKey(market.getId())) {
            return derelictAvailabilityOverride.get(market.getId());
        } else if(cfg != null) {
            return cfg.isFamousDerelictBarEventAllowed();
        } else {
            return market.getFaction().isShowInIntelTab();
        }
    }
    public static boolean isFamousFlagshipEventAvailableAtMarket(MarketAPI market) {
        FactionConfig cfg = FactionConfig.get(market.getFactionId());

        if(flagshipAvailabilityOverride.containsKey(market.getId())) {
            return flagshipAvailabilityOverride.get(market.getId());
        } else if(cfg != null) {
            return cfg.isFamousFlagshipBarEventAllowed();
        } else {
            return market.getFaction().isShowInIntelTab();
        }
    }
    public static void setAvailabilityOfFamousDerelictEventAtMarket(MarketAPI market, boolean isAvailable) {
        derelictAvailabilityOverride.put(market.getId(), isAvailable);
    }
    public static void setAvailabilityOfFamousFlagshipEventAtMarket(MarketAPI market, boolean isAvailable) {
        flagshipAvailabilityOverride.put(market.getId(), isAvailable);

    }
    public static void transferReputation(FleetMemberAPI from, FleetMemberAPI to) {
        RepRecord.transfer(from, to);
    }
    public static void registerTraitEffect(final String traitID) {
        registerTraitEffect(traitID, false);
    }
    public static void registerTraitEffect(final String traitID, final boolean appliesToFighters) {
        if(traitID != null && !traitID.isEmpty()) {
            registerTraitEffect(traitID, new TraitType.Effect() {
                @Override
                public boolean isAppliedToFighters() {
                    return appliesToFighters;
                }

                @Override
                public void apply(MutableShipStatsAPI stats, FleetMemberAPI ship, String id, float effectPercent) {
                    stats.getDynamic().getStat(traitID).modifyPercent(id, effectPercent);
                }
            });
        }
    }
    public static void registerTraitEffect(String traitID, TraitType.Effect effect) {
        if(traitID != null && !traitID.isEmpty() && effect != null) {
            TraitType.EFFECT_REGISTRY.put(traitID, effect);
        }
    }
    public static void registerTraitEffects(Map<String, TraitType.Effect> traitEffects) {
        if(traitEffects != null) {
            for (Map.Entry<String, TraitType.Effect> pair : traitEffects.entrySet()) {
                registerTraitEffect(pair.getKey(), pair.getValue());
            }
        }
    }
    public static void registerBiologicalShip(String hullID) {
        if(hullID != null) biologicalShips.add(hullID);
    }
}
