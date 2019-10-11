package starship_legends;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.campaign.Faction;

import java.util.HashMap;
import java.util.Map;

public class Integration {
    static Map<String, Boolean> derelictAvailabilityOverride = new HashMap();
    static Map<String, Boolean> flagshipAvailabilityOverride = new HashMap();

    public static boolean isFamousDerelictEventAvailableAtMarket(MarketAPI market) {
        FactionConfig cfg = FactionConfig.get(market.getFactionId());

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
}
