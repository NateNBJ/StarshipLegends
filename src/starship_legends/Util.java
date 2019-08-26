package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.Console;

import java.util.*;

public class Util {
    public static Comparator<FleetMemberAPI> SHIP_SIZE_COMPARATOR = new Comparator<FleetMemberAPI>() {
        @Override
        public int compare(FleetMemberAPI s1, FleetMemberAPI s2) {
            return (s2.getHullSpec().getHullSize().ordinal() * 10000 + (int)s2.getHullSpec().getHitpoints())
                    - (s1.getHullSpec().getHullSize().ordinal() * 10000 + (int)s1.getHullSpec().getHitpoints());
        }
    };
    public static List<FleetMemberAPI> getShipsMatchingDescription(String desc) {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();

        desc = desc.toLowerCase().trim();

        if(desc.equals("all")) return pf.getFleetData().getMembersListCopy();

        List<FleetMemberAPI> matches = new ArrayList<>();

        if(desc.isEmpty() || desc.equals("flagship")) {
            if(pf.getFlagship() != null) matches.add(pf.getFlagship());
        } else {
            for(FleetMemberAPI fm : pf.getFleetData().getMembersListCopy()) {
                if(fm.getShipName().toLowerCase().contains(desc)) {
                    matches.add(fm);
                }
            }
        }

        if(matches.isEmpty()) {
            for(FleetMemberAPI fm : pf.getFleetData().getMembersListCopy()) {
                ShipHullSpecAPI spec = fm.getHullSpec();

                if(spec.getDesignation().toLowerCase().contains(desc)
                        || spec.getHullName().toLowerCase().contains(desc)
                        || spec.getHullSize().name().toLowerCase().contains(desc)) {

                    matches.add(fm);
                }
            }
        }

        if(matches.isEmpty()) {
            Console.showMessage("Error: no ships matching the description provided: " + desc);
            Console.showMessage("Ship descriptions may be ship names, classes, designations, or hull sizes.");
        }

        return matches;
    }
    public static List<Trait> getTraitsMatchingDescription(String desc) {
        List<Trait> matches;

        desc = desc.toLowerCase().trim();

        if(desc.isEmpty() || desc.equals("all")) {
            matches = Trait.getAll();
        } else {
            matches = new ArrayList<>();
            int sign = getGoodnessSignFromArgs(desc);

            desc = Util.removeGoodnessKeywordsFromArgs(desc);

            Set<String> argTags = new HashSet<>(Arrays.asList(desc.split(" ")));

            for(Trait trait : Trait.getAll()) {
                if(sign != 0 && sign != trait.getEffectSign()) continue;

                if(desc.isEmpty() || trait.getLowerCaseName(true).equals(desc)) {
                    matches.add(trait);
                } else {
                    boolean hasAllTags = true;

                    for(String tag : argTags) {
                        if(!trait.getTags().contains(tag)) {
                            hasAllTags = false;
                            break;
                        }
                    }

                    if(hasAllTags) matches.add(trait);
                }
            }
        }

        if(matches.isEmpty()) {
            Console.showMessage("Error: no traits matching the description provided: " + desc);
            Console.showMessage("Trait descriptions may be trait names or be composed of the following keywords:");
            Console.showMessage("good, bad, positive, negative, pos, neg, logistical, combat, disabled, disabled_only, carrier, crew, attack, shield, cloak, no_ai, flat_effect, defense, flux");
        }

        return matches;
    }
    public static int getGoodnessSignFromArgs(String args) {
        int sign = 0;

        if(args.contains("good") || args.contains("positive") || args.contains("pos")) {
            sign = 1;
        } else if(args.contains("bad") || args.contains("negative") || args.contains("neg")) {
            sign = -1;
        }

        return sign;
    }
    public static String removeGoodnessKeywordsFromArgs(String args) {
        return args.replace("good", "").replace("positive", "").replace("pos", "").replace("bad", "").replace("negative", "").replace("neg", "").trim();
    }
    public static float getShipStrength(FleetMemberAPI ship) {
        if(ModPlugin.USE_RUTHLESS_SECTOR_TO_CALCULATE_SHIP_STRENGTH
                && Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector")) {

            try { return ruthless_sector.ModPlugin.getShipStrength(ship); } catch (Exception e) { }
        }

        float strength = ship.getFleetPointCost();

        if(ship.getHullSpec().isCivilianNonCarrier() || ship.isMothballed() || ship.isFighterWing() || ship.isCivilian() || !ship.canBeDeployedForCombat()) {
            return 0;
        } if(ship.isStation()) {
            ShipVariantAPI variant = ship.getVariant();
            List<String> slots = variant.getModuleSlots();
            float totalOP = 0, detachedOP = 0;

            for(int i = 0; i < slots.size(); ++i) {
                ShipVariantAPI module = variant.getModuleVariant(slots.get(i));
                float op = module.getHullSpec().getOrdnancePoints(null);

                totalOP += op;

                if(ship.getStatus().isPermaDetached(i+1)) {
                    detachedOP += op;
                }
            }

            strength *= (totalOP - detachedOP) / Math.max(1, totalOP);
        } else if(ship.getHullSpec().hasTag("UNBOARDABLE")) {
            float dModMult = ship.getBaseDeploymentCostSupplies() > 0
                    ? (ship.getDeploymentCostSupplies() / ship.getBaseDeploymentCostSupplies())
                    : 1;

            return strength * Math.max(1, Math.min(2, 1 + (strength - 5f) / 25f)) * dModMult;
        } else{
            return ship.getDeploymentCostSupplies();
        }

        return strength;
    }
}
