package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import org.lazywizard.console.Console;
import starship_legends.events.FamousDerelictIntel;
import starship_legends.events.FamousFlagshipIntel;
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

    public static float getFractionOfFittingSlots(FleetMemberAPI ship, WeaponAPI.WeaponType primary,
                                                  WeaponAPI.WeaponType hybrid1, WeaponAPI.WeaponType hybrid2) {

        float total = ship.getHullSpec().getFighterBays() * 10, fit = 0;

        for(WeaponSlotAPI slot : ship.getHullSpec().getAllWeaponSlotsCopy()) {
            float op = 0;

            switch (slot.getSlotSize()) {
                case SMALL: op = 5; break;
                case MEDIUM: op = 10; break;
                case LARGE: op = 20; break;
            }

            if(slot.getWeaponType() == primary) fit += op * 1.0f;
            else if(slot.getWeaponType() == hybrid1) fit += op * 0.8f;
            else if(slot.getWeaponType() == hybrid2) fit += op * 0.8f;
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
            Console.showMessage("good, bad, positive, negative, pos, neg, logistical, combat, disabled, disabled_only, carrier, crew, attack, shield, cloak, no_ai, flat_effect, defense, flux" + additionallySupportedTags);
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
        if(ship.getHullSpec().isCivilianNonCarrier() || ship.isCivilian() || ship.isMothballed()) {
            return ship.getDeploymentCostSupplies();
        }

        if(ModPlugin.USE_RUTHLESS_SECTOR_TO_CALCULATE_SHIP_STRENGTH
                && Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector")) {

            try { return ruthless_sector.ModPlugin.getShipStrength(ship); } catch (Exception e) { }
        }

        float strength = ship.getFleetPointCost();

        if(ship.isFighterWing() || !ship.canBeDeployedForCombat()) {
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
        } else {
            return ship.getDeploymentCostSupplies();
        }

        return strength;
    }
//    public static float getShipStrength(FleetMemberAPI ship) {
//        float fp = ship.getFleetPointCost();
//        float strength;
//
//        if(ship.isFighterWing() || !ship.canBeDeployedForCombat()) {
//            return 0;
//        } if(ship.isStation()) {
//            ShipVariantAPI variant = ship.getVariant();
//            List<String> slots = variant.getModuleSlots();
//            float totalOP = 0, detachedOP = 0;
//
//            for(int i = 0; i < slots.size(); ++i) {
//                ShipVariantAPI module = variant.getModuleVariant(slots.get(i));
//                float op = module.getHullSpec().getOrdnancePoints(null);
//
//                totalOP += op;
//
//                if(ship.getStatus().isPermaDetached(i+1)) {
//                    detachedOP += op;
//                }
//            }
//
//            strength = fp * (totalOP - detachedOP) / Math.max(1, totalOP);
//        } else {
//            boolean isPlayerShip = ship.getOwner() == 0 && !ship.isAlly();
//            float dModFactor = isPlayerShip ? ModPlugin.DMOD_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.DMOD_FACTOR_FOR_ENEMY_SHIPS;
//            float sModFactor = isPlayerShip ? ModPlugin.SMOD_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.SMOD_FACTOR_FOR_ENEMY_SHIPS;
//            float skillFactor = isPlayerShip ? ModPlugin.SKILL_FACTOR_FOR_PLAYER_SHIPS : ModPlugin.SKILL_FACTOR_FOR_ENEMY_SHIPS;
//
//            float dMods = DModManager.getNumDMods(ship.getVariant());
//            float sMods = ship.getVariant().getSMods().size();
//            float skills = 0;
//            PersonAPI captain = ship.getCaptain();
//
//            if(captain != null && !captain.isDefault()) {
//                for(MutableCharacterStatsAPI.SkillLevelAPI skill : captain.getStats().getSkillsCopy()) {
//                    if (skill.getSkill().isCombatOfficerSkill()) {
//                        if(skill.getLevel() > 0) skills += skill.getSkill().isElite() ? 1.25f : 1;
//                    }
//                }
//            }
//
//            float dModMult = (float) Math.pow(1 - dModFactor, dMods);
//            float sModMult = (float) Math.pow(1 + sModFactor, sMods);
//            float skillMult = (float) Math.pow(1 + skillFactor, skills);
//            float playerStrengthMult = 1;
//
//            if(isPlayerShip) {
//                playerStrengthMult += ModPlugin.STRENGTH_INCREASE_PER_PLAYER_LEVEL
//                        * Global.getSector().getPlayerPerson().getStats().getLevel();
//            }
//
//            strength = fp * (1 + (fp - 5f) / 25f) * dModMult * sModMult * skillMult * playerStrengthMult;
//
//            Global.getLogger(Util.class).info(String.format("%20s strength: %3.1f = %3.1f * %.2f * %.2f * %.2f * %.2f",
//                    ship.getHullId(), strength, fp * (1 + (fp - 5f) / 25f), dModMult, sModMult, skillMult, playerStrengthMult));
//        }
//
//        return strength;
//    }
    public static void removeRepHullmodFromVariant(ShipVariantAPI v) {
        if(v == null) return;

        v.removePermaMod("sun_sl_notable");
        v.removePermaMod("sun_sl_wellknown");
        v.removePermaMod("sun_sl_famous");
        v.removePermaMod("sun_sl_legendary");

        // Line below may NPE
        List<String> slots = v.getModuleSlots();

        for(int i = 0; i < slots.size(); ++i) {
            ShipVariantAPI module = v.getModuleVariant(slots.get(i));
            Util.removeRepHullmodFromVariant(module);
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
    public static void showTraits(TooltipMakerAPI tooltip, RepRecord rep, PersonAPI captain, boolean requiresCrew, ShipAPI.HullSize size) {
        //int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
        int traitsLeft = rep.getTraits().size();
        int loyaltyEffectAdjustment = 0;

        if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault()) {
            loyaltyEffectAdjustment = rep.getLoyalty(captain).getTraitAdjustment();
        }

        for(Trait trait : rep.getTraits()) {
            if (traitsLeft <= 0) break;

            Trait.Tier tier = RepRecord.getTierFromTraitCount(traitsLeft--);

            trait.addParagraphTo(tooltip, tier, loyaltyEffectAdjustment, requiresCrew, size, false,
                    rep.equals(FactionConfig.getEnemyFleetRep()));
        }
    }
    public static void showTraits(TextPanelAPI textPanel, RepRecord rep, PersonAPI captain, boolean requiresCrew, ShipAPI.HullSize size) {
        if(textPanel == null) return;

        TooltipMakerAPI tooltip = textPanel.beginTooltip();

        showTraits(tooltip, rep, captain, requiresCrew, size);

        textPanel.addTooltip();
    }
    public static void clearAllStarshipLegendsData() {
        IntelManagerAPI intelManager = Global.getSector().getIntelManager();

        for(IntelInfoPlugin i : intelManager.getIntel(BattleReport.class)) intelManager.removeIntel(i);
        for(IntelInfoPlugin i : intelManager.getIntel(FamousFlagshipIntel.class)) intelManager.removeIntel(i);
        for(IntelInfoPlugin i : intelManager.getIntel(FamousDerelictIntel.class)) intelManager.removeIntel(i);

        intelManager.removeAllThatShouldBeRemoved();

        Util.removeRepHullmodFromAutoFitGoalVariants();

        for(FleetMemberAPI ship : Reputation.getShipsOfNote()) {
            ShipVariantAPI v = ship.getVariant();
            Util.removeRepHullmodFromVariant(v);
        }

        Saved.deletePersistantData();

        ModPlugin.REMOVE_ALL_DATA_AND_FEATURES = true;
    }
    public static void teleportEntity(SectorEntityToken entityToMove, SectorEntityToken destination) {
        entityToMove.getContainingLocation().removeEntity(entityToMove);
        destination.getContainingLocation().addEntity(entityToMove);
        Global.getSector().setCurrentLocation(destination.getContainingLocation());
        entityToMove.setLocation(destination.getLocation().x,
                destination.getLocation().y-150);
    }
}
