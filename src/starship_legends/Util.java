package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BattleAPI;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.Console;
import starship_legends.events.FamousDerelictIntel;
import starship_legends.events.FamousFlagshipIntel;
import starship_legends.events.RepSuggestionPopupEvent;
import starship_legends.hullmods.Reputation;

import java.util.*;

public class Util {
    public static Comparator<FleetMemberAPI> SHIP_SIZE_COMPARATOR = new Comparator<FleetMemberAPI>() {
        @Override
        public int compare(FleetMemberAPI s1, FleetMemberAPI s2) {
            return (s2.getHullSpec().getHullSize().ordinal() * 10000 + (int)s2.getHullSpec().getHitpoints())
                    - (s1.getHullSpec().getHullSize().ordinal() * 10000 + (int)s1.getHullSpec().getHitpoints());
        }
    };

    static Set<String> vowels = new HashSet<>();
    static {
        vowels.add("a");
        vowels.add("e");
        vowels.add("i");
        vowels.add("o");
        vowels.add("u");
    }

    public static PersonAPI getCaptain(FleetMemberAPI ship) {
        PersonAPI cap = ship.getCaptain();
        Collection<String> mods = ship.getVariant().getHullMods();

        if((cap == null || cap.isDefault())
                && (mods.contains("neural_interface") || mods.contains("neural_integrator"))
                && Global.getSector().getPlayerFleet().getFlagship().getVariant().getHullMods().contains("neural_interface")) {

            return Global.getSector().getPlayerPerson();
        } else return cap;
    }
    public static String getCaptainId(PersonAPI captain) {
        return captain.getAICoreId() != null ? captain.getAICoreId() : captain.getId();
    }
    public static FleetMemberAPI findPlayerShip(String id) {
        for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if(ship.getId().equals(id)) return ship;
        }

        for(MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            SubmarketAPI storage = market.getSubmarket("storage");

            if(storage == null || storage.getCargo() == null || storage.getCargo().getMothballedShips() == null) continue;

            for(FleetMemberAPI ship : storage.getCargo().getMothballedShips().getMembersListCopy()) {
                if(ship.getId().equals(id)) return ship;
            }
        }

        return null;
    }
    public static Set<String> getIdsOfShipsOwnedByPlayer() {
        Set<String> retVal = new HashSet<>();
        Set<MarketAPI> markets = new HashSet<>();

        for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            retVal.add(ship.getId());
        }

        markets.addAll(Global.getSector().getEconomy().getMarketsCopy());

        for(SectorEntityToken entity : Global.getSector().getCustomEntitiesWithTag(Tags.STATION)) {
            if(entity.getMarket() != null) markets.add(entity.getMarket());
        }

        for(MarketAPI market : markets) {
            SubmarketAPI storage = market.getSubmarket("storage");

            if(storage == null || storage.getCargo() == null || storage.getCargo().getMothballedShips() == null) continue;

            for(FleetMemberAPI ship : storage.getCargo().getMothballedShips().getMembersListCopy()) {
                retVal.add(ship.getId());
            }
        }

        // runcode Console.showMessage(starship_legends.Util.getIdsOfShipsOwnedByPlayer().size());

        return retVal;
    }
    public static MarketAPI getStorageLocationOfShip(String id) {
        for(MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            SubmarketAPI storage = market.getSubmarket("storage");

            if(storage == null || storage.getCargo() == null || storage.getCargo().getMothballedShips() == null) continue;

            for(FleetMemberAPI ship : storage.getCargo().getMothballedShips().getMembersListCopy()) {
                if(ship.getId().equals(id)) return market;
            }
        }

        return null;
    }
    public static List<FleetMemberAPI> findPlayerOwnedShip(Trait.Tier minRepTier) {
        List<FleetMemberAPI> retVal = new ArrayList<>();

        for(FleetMemberAPI ship : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if(RepRecord.shipTierIsAtLeast(ship, minRepTier)) retVal.add(ship);
        }

        for(MarketAPI market : Global.getSector().getEconomy().getMarketsCopy()) {
            SubmarketAPI storage = market.getSubmarket("storage");

            if(storage == null || storage.getCargo() == null || storage.getCargo().getMothballedShips() == null) continue;

            for(FleetMemberAPI ship : storage.getCargo().getMothballedShips().getMembersListCopy()) {
                if(RepRecord.shipTierIsAtLeast(ship, minRepTier)) retVal.add(ship);
            }
        }

        return retVal;
    }
    public static String getSubString(String str, int length) {
        return str == null ? "-" : str.substring(0, Math.min(length, str.length()));
    }
    public static String getImpreciseNumberString(double number) {
        String retVal = "";

        if(number >= 10000000d) {
            retVal = (int)(number / 1000000d) + "M";
        } else if(number >= 10000d) {
            retVal = (int)(number / 1000d) + "K";
        } else  {
            retVal = (int)number + "";
        }

        return retVal;
    }
    public static float getFractionOfFittingSlots(ShipHullSpecAPI hull, WeaponAPI.WeaponType primary,
                                                  WeaponAPI.WeaponType secondary1, WeaponAPI.WeaponType secondary2) {

        float total = hull.getFighterBays() * 10, fit = 0;

        for(WeaponSlotAPI slot : hull.getAllWeaponSlotsCopy()) {
            float op = 0;

            switch (slot.getSlotSize()) {
                case SMALL: op = 5; break;
                case MEDIUM: op = 10; break;
                case LARGE: op = 20; break;
            }

            if(slot.getWeaponType() == primary) fit += op * 1.0f;
            else if(slot.getWeaponType() == secondary1) fit += op * 0.8f;
            else if(slot.getWeaponType() == secondary2) fit += op * 0.8f;
            else if(slot.getWeaponType() == WeaponAPI.WeaponType.UNIVERSAL) fit += op * 0.6f;

            total += op;
        }

        return fit / total;
    }
    public static String getWithAnOrA(String string) {
        if(string == null || string.isEmpty()) return "";

        return (vowels.contains("" + string.toLowerCase().charAt(0)) ? "an " : "a ") + string;
    }
    public static String getShipDescription(FleetMemberAPI ship) {
        return getShipDescription(ship, true);
    }
    public static String getShipDescription(FleetMemberAPI ship, boolean addAnOrA) {
        String desc = ship.getHullSpec().getHullName() + " class " + ship.getHullSpec().getDesignation();

        return addAnOrA ? getWithAnOrA(desc) : desc;
    }
    public static void removeRepHullmodFromAutoFitGoalVariants() {
        try {
            for (ShipHullSpecAPI spec : Global.getSettings().getAllShipHullSpecs()) {
                for (ShipVariantAPI v : Global.getSector().getAutofitVariants().getTargetVariants(spec.getHullId())) {
                    if(v != null) removeRepHullmodFromVariant(v);
                }
            }
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }
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
        return getTraitsMatchingDescription(desc, "");
    }
    public static List<Trait> getTraitsMatchingDescription(String desc, String additionallySupportedTags) {
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

                if(desc.isEmpty() || trait.getLowerCaseName(true, false).equals(desc)) {
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
            Console.showMessage("good, bad, positive, negative, pos, neg, logistical, combat, disabled, carrier, crew, attack, shield, cloak, no_ai, flat_effect, defense, flux" + additionallySupportedTags);
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
    public static void removeRepHullmodFromVariant(ShipVariantAPI v) {
        if(v == null) return;

        v.removePermaMod("sun_sl_notable");
        v.removePermaMod("sun_sl_wellknown");
        v.removePermaMod("sun_sl_famous");
        v.removePermaMod("sun_sl_legendary");

        try {
            List<String> slots = v.getModuleSlots();

            for (int i = 0; i < slots.size(); ++i) {
                ShipVariantAPI module = v.getModuleVariant(slots.get(i));
                Util.removeRepHullmodFromVariant(module);
            }
        } catch (Exception e) {
            Global.getLogger(Util.class).warn("Could not retrieve module slots for variant " + v.getHullVariantId());
            ModPlugin.reportCrash(e, false);
        }
    }
    public static PersonAPI getHighestLevelEnemyCommanderInBattle(List<CampaignFleetAPI> fleets) {
        PersonAPI commander = null;

        for(CampaignFleetAPI fleet : fleets) {
            int lvl = fleet.getCommanderStats().getLevel();

            if(commander == null || commander.getStats().getLevel() < lvl) {
                commander = fleet.getCommander();
            }
        }

        return commander;
    }
    public static PersonAPI getHighestLevelEnemyCommanderInBattle(BattleAPI battle) {
        PersonAPI commander = battle.getNonPlayerCombined().getCommander();

        for(CampaignFleetAPI fleet : battle.getNonPlayerSide()) {
            int lvl = fleet.getCommanderStats().getLevel();

            if(commander == null || commander.getStats().getLevel() < lvl) {
                commander = fleet.getCommander();
            }
        }

        return commander;
    }
    public static void showTraits(TooltipMakerAPI tooltip, RepRecord rep, PersonAPI captain, boolean requiresCrew, boolean biological, ShipAPI.HullSize size) {
        //int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
        int traitsLeft = rep.getTraits().size();
        int loyaltyEffectAdjustment = 0;

        if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault()) {
            loyaltyEffectAdjustment = isNonIntegratedAiCore(captain) ? 0 : rep.getLoyalty(captain).getTraitAdjustment();
        }

        for(Trait trait : rep.getTraits()) {
            if (traitsLeft <= 0) break;

            Trait.Tier tier = RepRecord.getTierFromTraitCount(traitsLeft--);

            trait.addParagraphTo(tooltip, tier, loyaltyEffectAdjustment, requiresCrew, biological, size, false,
                    rep.equals(FactionConfig.getEnemyFleetRep()));
        }
    }
    public static void showTraits(TextPanelAPI textPanel, RepRecord rep, PersonAPI captain, boolean requiresCrew, boolean biological, ShipAPI.HullSize size) {
        if(textPanel == null) return;

        TooltipMakerAPI tooltip = textPanel.beginTooltip();

        showTraits(tooltip, rep, captain, requiresCrew, biological, size);

        textPanel.addTooltip();
    }
    public static void teleportEntity(SectorEntityToken entityToMove, SectorEntityToken destination) {
        entityToMove.getContainingLocation().removeEntity(entityToMove);
        destination.getContainingLocation().addEntity(entityToMove);
        Global.getSector().setCurrentLocation(destination.getContainingLocation());
        entityToMove.setLocation(destination.getLocation().x,
                destination.getLocation().y-150);
    }
    public static void clearAllStarshipLegendsData() {
        IntelManagerAPI intelManager = Global.getSector().getIntelManager();

        for(IntelInfoPlugin i : intelManager.getIntel(BattleReport.class)) intelManager.removeIntel(i);
        for(IntelInfoPlugin i : intelManager.getIntel(FamousFlagshipIntel.class)) intelManager.removeIntel(i);
        for(IntelInfoPlugin i : intelManager.getIntel(FamousDerelictIntel.class)) intelManager.removeIntel(i);
        for(IntelInfoPlugin i : intelManager.getIntel(RepSuggestionPopupEvent.class)) intelManager.removeIntel(i);

        intelManager.removeAllThatShouldBeRemoved();

        Util.removeRepHullmodFromAutoFitGoalVariants();

        for(FleetMemberAPI ship : Reputation.getShipsOfNote()) {
            ShipVariantAPI v = ship.getVariant();
            Util.removeRepHullmodFromVariant(v);
        }

        Saved.deletePersistantData();

        ModPlugin.REMOVE_ALL_DATA_AND_FEATURES = true;
    }
    public static float getShipStrength(FleetMemberAPI ship, boolean isPlayerShip) {
        float fp = ship.getFleetPointCost();
        float strength;

        if(!isPlayerShip && (ship.isFighterWing() || !ship.canBeDeployedForCombat() || ship.getHullSpec().isCivilianNonCarrier() || ship.isMothballed())) {
            strength = 0;
        } else if(ship.isStation()) {
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

            strength = fp * (totalOP - detachedOP) / Math.max(1, totalOP);
        } else if(isPlayerShip) {
            float dMods = DModManager.getNumDMods(ship.getVariant());
            float dModMult = (float) Math.pow(0.9, dMods);

            strength = fp * (1 + (fp - 5f) / 25f) * dModMult;

//            Global.getLogger(ModPlugin.class).info(String.format("%20s strength: %3.1f = %3.1f",
//                    ship.getHullId(), strength, fp * (1 + (fp - 5f) / 25f)));
        } else {
            float dMods = DModManager.getNumDMods(ship.getVariant());
            float sMods = ship.getVariant().getSMods().size();
            float skills = 0;
            PersonAPI captain = ship.getCaptain();

            if(captain != null && !captain.isDefault()) {
                for(MutableCharacterStatsAPI.SkillLevelAPI skill : captain.getStats().getSkillsCopy()) {
                    if (skill.getSkill().isCombatOfficerSkill()) {
                        if(skill.getLevel() > 0) skills += skill.getSkill().isElite() ? 1.25f : 1;
                    }
                }
            }

            float dModMult = (float) Math.pow(0.9, dMods);
            float sModMult = (float) Math.pow(1.1, sMods);
            float skillMult = (float) Math.pow(1.1, skills);

            strength = fp * (1 + (fp - 5f) / 25f) * dModMult * sModMult * skillMult;

//            Global.getLogger(ModPlugin.class).info(String.format("%20s strength: %3.1f = %3.1f * %.2f * %.2f * %.2f",
//                    ship.getHullId(), strength, fp * (1 + (fp - 5f) / 25f), dModMult, sModMult, skillMult));
        }

        return strength;
    }
    public static Random getUniqueShipRng(FleetMemberAPI ship) {
        String seed = "";

        for(int i = ship.getId().length() - 1; i >= 0; --i) {
            seed += ship.getId().charAt(i);
        }

        return new Random(seed.hashCode());
    }
    public static boolean isPhaseShip(FleetMemberAPI ship) {
        ShipHullSpecAPI hull = ship.getHullSpec();
        String defId = hull.getShipDefenseId();

        if(defId == null || defId.isEmpty()) return false;

        defId = defId.toLowerCase();

        if(hull.getShieldType() != ShieldAPI.ShieldType.PHASE
                || !(hull.isPhase() || defId.contains("phase") || defId.contains("cloak"))) {
            return false;
        }

        return true;
    }
    public static boolean isShipCrewed(FleetMemberAPI ship) {
        return ship.isMothballed()
                ? (ship.getHullSpec().getMinCrew() > 0 && !ship.getVariant().hasHullMod(HullMods.AUTOMATED))
                : (ship.getMinCrew() > 0);
    }
    public static boolean isNonIntegratedAiCore(PersonAPI captain) {
        return captain.isAICore()
                && !captain.isPlayer()
                && !captain.getAICoreId().equals("sotf_sierracore_officer")
                && !Misc.isUnremovable(captain);
    }
    public static boolean isShipBiological(FleetMemberAPI fleetMember) {
        return Integration.biologicalShips.contains(fleetMember.getHullSpec().getBaseHullId());
    }
    public static FleetMemberAPI copyFleetMember(FleetMemberAPI ship) {
        try {
            return Global.getFactory().createFleetMember(FleetMemberType.SHIP, ship.getVariant());
        } catch (Exception e) {
            ModPlugin.reportCrash(e, false);
        }

        return null;
    }
    public static boolean isAnyShipInPlayerFleetNotable() {
        for(FleetMemberAPI fm : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if(RepRecord.existsFor(fm)) return true;
        }

        return false;
    }
}
