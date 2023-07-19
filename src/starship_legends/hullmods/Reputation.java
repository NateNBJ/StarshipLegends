package starship_legends.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.console.Console;
import starship_legends.*;

import java.awt.*;
import java.util.List;
import java.util.*;


public class Reputation extends BaseHullMod {
    private final static Map<ShipAPI.HullSize, Integer> FLAT_EFFECT_MULT = new HashMap();
    static {
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.DEFAULT, 0);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.FIGHTER, 0);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.FRIGATE, 1);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.DESTROYER, 2);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.CRUISER, 4);
        FLAT_EFFECT_MULT.put(ShipAPI.HullSize.CAPITAL_SHIP, 6);
    }

    static transient Saved<HashSet<FleetMemberAPI>> shipsOfNote = new Saved<>("shipsOfNote", new HashSet<FleetMemberAPI>());
    public static transient HashMap<String, FleetMemberAPI> moduleMap = new HashMap<>(); // TODO - Remove this nonsense if Alex fixes getFleetMember

    public static final String ENEMY_HULLMOD_ID = "sun_sl_enemy_reputation";
    public static final Color BORDER_COLOR = new Color(147, 102, 50, 0);
    public static final Color NAME_COLOR = new Color(224, 184, 139, 255);

    public static FleetMemberAPI getFleetMember(MutableShipStatsAPI stats) {
        String id = stats.getVariant().getHullVariantId();

        return moduleMap.containsKey(id) ? moduleMap.get(id) : stats.getFleetMember();
    }
    public static FleetMemberAPI getFleetMember(ShipAPI ship) {
        String id = ship.getVariant().getHullVariantId();

        return moduleMap.containsKey(id) ? moduleMap.get(id) : ship.getFleetMember();
    }
    public static void printRegistry() {
        int shipsWithRepFound = 0;
        Map<String, RepRecord> reps = RepRecord.getInstanceRegistryCopy();
        List<FleetMemberAPI> registeredShipsWithNoRep = new ArrayList<>();
        String msg = "HULL             ID       OWNER  NAME                 THEME        XP       LOYALTIES    TRAITS";
        String format = "%-16s %-8s %-6s %-20s %-12s %-8s %-12s %s";

        for(FleetMemberAPI ship : shipsOfNote.val) {
            String id = ship.getId();

            if(reps.containsKey(id)) {
                RepRecord rep = reps.get(id);

                msg += System.lineSeparator() + String.format(format, Util.getSubString(ship.getHullId(), 16),
                        id, ship.getOwner(), Util.getSubString(ship.getShipName(), 20),
                        Util.getSubString(rep.getThemeKey(), 12), Util.getImpreciseNumberString(rep.getXp()),
                        rep.getCaptainLoyaltyLevels().size(), rep.getTraits().size());

                shipsWithRepFound++;
                reps.remove(id);
            } else {
                registeredShipsWithNoRep.add(ship);
            }
        }

        msg = shipsWithRepFound + " ships with reputation records:" + System.lineSeparator()
                + System.lineSeparator() + msg;

        if(!reps.isEmpty()) {
            msg += System.lineSeparator() + System.lineSeparator()
                    + reps.size() + " reputation records with no matching ship:";

            for(RepRecord rep : reps.values()) msg += System.lineSeparator() + rep;
        }

        if(!registeredShipsWithNoRep.isEmpty()) {
            msg += System.lineSeparator() + System.lineSeparator()
                    + registeredShipsWithNoRep.size() + " ships with no reputation record:";

            for(FleetMemberAPI ship : registeredShipsWithNoRep) msg += System.lineSeparator() + ship;
        }

        if(Global.getSettings().getModManager().isModEnabled("lw_console")) Console.showMessage(msg);

        Global.getLogger(RepRecord.class).info(msg);
    }
    public static float getFlatEffectMult(ShipAPI.HullSize size) {
        return size == null ? 1 : FLAT_EFFECT_MULT.get(size);
    }
    public static void addShipOfNote(FleetMemberAPI ship) {
        shipsOfNote.val.add(ship);
    }
    public static void removeShipOfNote(FleetMemberAPI ship) {
        shipsOfNote.val.remove(ship);
    }
    public static Collection<FleetMemberAPI> getShipsOfNote() {
        return shipsOfNote.val;
    }

    public static void applyEffects(FleetMemberAPI ship) {
        if(!RepRecord.isShipNotable(ship)) return;

        RepRecord rep = RepRecord.get(ship);

        applyEffects(rep, ship, ship.getHullSpec().getHullSize(), Util.getCaptain(ship), ship.getStats(),
                ship.isFighterWing(), rep.getTier().getHullModID());
    }
    public static void applyEffects(RepRecord rep, FleetMemberAPI ship, ShipAPI.HullSize size, PersonAPI captain, MutableShipStatsAPI stats, boolean isFighter, String id) {
        try {
            int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
            int loyaltyEffectAdjustment = 0;
            float effectMult = id.equals(ENEMY_HULLMOD_ID) ? ModPlugin.FLEET_TRAIT_EFFECT_MULT : 1.0f;

            if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM && captain != null && !captain.isDefault()) {
                LoyaltyLevel ll = rep.getLoyalty(captain);
                String loyaltyId = id + "_loyalty";

                loyaltyEffectAdjustment = Util.isNonIntegratedAiCore(captain) ? 0 : ll.getTraitAdjustment();
                stats.getCRLossPerSecondPercent().modifyPercent(loyaltyId, ll.getCrDecayMult());

                if(ll.getMaxCrReduction() > 0) {
                    stats.getPeakCRDuration().modifyPercent(loyaltyId, -ll.getMaxCrReduction());

                    if(!captain.isAICore()) {
                        stats.getMaxCombatReadiness().modifyFlat(loyaltyId, -0.01f * ll.getMaxCrReduction(), ll.getName() + " crew");
                    }
                }
            }

            for(Trait trait : rep.getTraits()) {
                if(traitsLeft <= 0) break;

                float e = Math.max(ModPlugin.MINIMUM_EFFECT_REDUCTION_PERCENT, effectMult
                        * trait.getEffect(RepRecord.getTierFromTraitCount(traitsLeft--), loyaltyEffectAdjustment, size));

                trait.applyEffect(stats, ship, isFighter, id, e);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }
    public static void applyEffects(FleetMemberAPI ship, ShipAPI.HullSize size, PersonAPI captain, MutableShipStatsAPI stats, boolean isFighter, String id) {
        try {
            if(!ModPlugin.settingsHaveBeenRead()) return;
            if(ship == null || !RepRecord.isShipNotable(ship)) return;

            RepRecord rep = RepRecord.get(ship);
            applyEffects(rep, ship, size, captain, stats, isFighter, id);
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        try {
            if(!ModPlugin.settingsHaveBeenRead() || ModPlugin.REMOVE_ALL_DATA_AND_FEATURES)
                return;

            FleetMemberAPI ship = getFleetMember(stats);

            if(ship == null) return;

            try {
                if (!stats.getVariant().getStationModules().isEmpty()) {
                    moduleMap.clear();

                    for (Map.Entry<String, String> e : stats.getVariant().getStationModules().entrySet()) {
                        ShipVariantAPI module = stats.getVariant().getModuleVariant(e.getKey());

                        moduleMap.put(module.getHullVariantId(), ship);
                    }
                }
            } catch (Exception e) {
                ModPlugin.reportCrash(e, false);
            }

            if(id.equals(ENEMY_HULLMOD_ID)) {
                if(FactionConfig.getEnemyFleetRep() != null) {
                    applyEffects(FactionConfig.getEnemyFleetRep(), ship, hullSize, ship.getFleetCommanderForStats(),
                            stats, false, id);
                }
            } else {
                applyEffects(ship, hullSize, Util.getCaptain(ship), stats, false, id);

                if(ship.getOwner() != 0) {
                    // Try to prevent famous enemy ships from being randomly selected as recoverable, since they are
                    //  manually forced to be recoverable anyway
                    stats.getDynamic().getMod(Stats.INDIVIDUAL_SHIP_RECOVERY_MOD).modifyFlat(id, Float.MIN_VALUE * 0.8f);
                }
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public void applyEffectsToFighterSpawnedByShip(ShipAPI fighter, ShipAPI ship, String id) {
        if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

        PersonAPI liege = ship.getOriginalOwner() == 0 ? ship.getCaptain() : ship.getFleetMember().getFleetCommanderForStats();

        applyEffects(ship.getFleetMember(), ship.getHullSize(), liege, fighter.getMutableStats(), true, id);
    }

    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize, ShipAPI ship) {
        if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return "";

        FleetMemberAPI fm = getFleetMember(ship);

        return fm == null ? "SHIP NOT FOUND" : fm.getShipName();
    }

    @Override
    public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
        try {
            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return;

            FleetMemberAPI fm = getFleetMember(ship);

            if(fm == null || !RepRecord.existsFor(fm)) {
                String msg = fm == null
                        ? "ERROR: Could not find ship: "
                            + ship.toString()
                        : "ERROR: No reputation record was found for this ship." +
                            "\n Hull ID: " + fm.getHullSpec().getHullId() +
                            "\n Is registered: " + shipsOfNote.val.contains(fm) +
                            "\n Ship ID: " + fm.getId();

                msg += "\n Please notify the mod author with this information.";

                if(Global.getSettings().getModManager().isModEnabled("lw_console")) {
                    msg += "\n\n Note that changes made using console commands from the refit screen will not be reflected immediately.";
                }

                tooltip.addPara(msg, 10, Misc.getNegativeHighlightColor());

                return;
            }

            RepRecord rep = RepRecord.get(fm);
            Trait.Tier previousTier = Trait.Tier.UNKNOWN;
            int traitsLeft = Math.min(rep.getTraits().size(), Trait.getTraitLimit());
            int loyaltyEffectAdjustment = 0;
            boolean requiresCrew = Util.isShipCrewed(fm);
            boolean showXp = Global.getSettings().isDevMode()
                    ? ModPlugin.SHOW_SHIP_XP_IN_DEV_MODE
                    : ModPlugin.SHOW_SHIP_XP;

            Trait.Tier tier = RepRecord.getTierFromTraitCount(traitsLeft);

            if(tier == Trait.Tier.UNKNOWN) {
                String details = "\n Limit: "  + Trait.getTraitLimit()
                        + "\n Traits: " + rep.getTraits()
                        + "\n Please notify the mod author with this information.";

                tooltip.addPara(tier.getFlavorText(requiresCrew) + details, 10, Misc.getNegativeHighlightColor());
            } else {
                tooltip.addPara(tier.getFlavorText(requiresCrew), 10, Misc.getGrayColor(), Misc.getGrayColor(),
                        fm.getShipName());

                if(Global.getSettings().isDevMode()) {
                    tooltip.addPara("Reputation theme: " + rep.getThemeKey(), 10, Misc.getGrayColor(),
                            Misc.getGrayColor());
                }

                if(showXp && rep.getTraits().size() < Trait.getTraitLimit()) {
                    String xp = Util.getImpreciseNumberString(rep.getXp());
                    String req = Util.getImpreciseNumberString(RepRecord.getXpRequiredToLevelUp(fm));
                    tooltip.addPara("Progress to next pair of traits: " + xp + " / " + req + " XP", 10, Misc.getGrayColor(),
                            Misc.getGrayColor());
                }
            }

            if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
                PersonAPI cap = Util.getCaptain(fm);

                if(cap != null && !cap.isDefault()) {
                    LoyaltyLevel ll = rep.getLoyalty(cap);
                    Color clr = ll.ordinal() < LoyaltyLevel.INDIFFERENT.ordinal()
                            ? Misc.getNegativeHighlightColor()
                            : Misc.getHighlightColor();

                    if(Util.isNonIntegratedAiCore(cap)) {
                        tooltip.beginImageWithText(cap.getPortraitSprite(), 64).addPara(
                                "The AI core does not affect the performance of the " + fm.getShipName()
                                + " because it is not fully integrated.", 3);
                        tooltip.addImageWithText(8);
                    } else {
                        loyaltyEffectAdjustment = ll.getTraitAdjustment();

                        String message = requiresCrew
                                ? "The crew of the " + fm.getShipName()
                                + " is %s " + ll.getPreposition()
                                + " its captain, " + cap.getNameString().trim()
                                : "The ship's integration status is %s";
                        String upOrDown = ll.getCrDecayMult() < 0 ? "reducing" : "increasing";
                        String finalValue = "";
                        String decay = "";

                        if(ll.getCrDecayMult() != 0) decay = (int) Math.abs(ll.getCrDecayMult()) + "%";

                        // Extra format keys are added in order to skip them for the sake of values that might come later
                        if (ll.getCrDecayMult() == 0 && loyaltyEffectAdjustment == 0) message += "%s%s.";
                        else if (loyaltyEffectAdjustment == 0) message += ", " + upOrDown + " CR decay rate by %s%s.";
                        else message += ", " + upOrDown + " CR decay rate by %s and %s the ship's traits.";


                        if (ll.getMaxCrReduction() > 0) {
                            message += requiresCrew
                                    ? " The maximum CR and peak performance time of the ship will be reduced by %s " +
                                        "while " + cap.getHeOrShe() + " remains the captain."
                                    : " The peak performance time of the ship will be reduced by %s.";
                            finalValue = (int) ll.getMaxCrReduction() + "%";
                        } else if(ll.getFameGainBonus() > 0 && !rep.hasMaximumTraits()) {
                            message += " The ship also gains fame %s faster.";
                            finalValue = (int) ll.getFameGainBonus() + "%";
                        }

                        if (ll == LoyaltyLevel.INSPIRED) {
                            int daysLeft = RepRecord.getDaysOfInspirationRemaining(fm, cap);
                            String firstPart = requiresCrew
                                    ? " The crew will remain inspired "
                                    : " Integration will remain amplified ";

                            message += daysLeft <= 0
                                    ? firstPart + "until the end of the next battle."
                                    : firstPart + "for at least another " + daysLeft + " days.";
                        }

                        tooltip.beginImageWithText(cap.getPortraitSprite(), 64).addPara(message, 3, clr,
                                requiresCrew ? ll.getName() : ll.getAiIntegrationStatusName(), decay,
                                ll.getTraitAdjustDesc(), finalValue);

                        if (showXp) {
                            String xp = Util.getImpreciseNumberString(rep.getLoyaltyXp(cap));

                            if (ll == LoyaltyLevel.INSPIRED || ll == LoyaltyLevel.FIERCELY_LOYAL) {
                                tooltip.addPara("Progress to next " + (requiresCrew ? "inspiration" : "amplification")
                                                + ": " + xp + " XP", 10, Misc.getGrayColor(),
                                        Misc.getGrayColor());
                            } else {
                                String req = Util.getImpreciseNumberString(ll.getXpToImprove());
                                tooltip.addPara("Progress to improved " + (requiresCrew ? "loyalty" : "integration")
                                                + ": " + xp + " / " + req + " XP", 10, Misc.getGrayColor(),
                                        Misc.getGrayColor());
                            }
                        }

                        tooltip.addImageWithText(8);
                    }
                } else if(!rep.getCaptainLoyaltyLevels().isEmpty()) {
                    int bestOpinion = 0;
                    Set<String> trustedOfficers = new HashSet<>();
//                    Set<String> unfoundOfficers = new HashSet<>();

                    for(Map.Entry<String, Integer> e : rep.getCaptainLoyaltyLevels().entrySet()) {
                        boolean officerNotFound = !e.getKey().equals(Global.getSector().getPlayerPerson().getId());

                        if(officerNotFound) {
                            for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                                if (e.getKey().equals(officer.getPerson().getId())) {
                                    officerNotFound = false;
                                    break;
                                }
                            }
                        }

                        if(officerNotFound) {
//                            unfoundOfficers.add(e.getKey());
                            continue;
                        }

                        if(e.getValue() > bestOpinion) {
                            trustedOfficers.clear();
                            bestOpinion = e.getValue();
                        }

                        if(e.getValue() == bestOpinion) trustedOfficers.add(e.getKey());
                    }

//                    for(String id : unfoundOfficers) {
//                        if(!id.contains("_core")) {
//                            rep.getCaptainLoyaltyLevels().remove(id);
//                        }
//                    }

                    if(bestOpinion > 0 && !trustedOfficers.isEmpty()) {
                        LoyaltyLevel ll = LoyaltyLevel.values()[bestOpinion + ModPlugin.LOYALTY_LIMIT];
                        String message = requiresCrew
                                ? "The crew is %s " + ll.getPreposition() + " the following officers:"
                                : "This ship has "+ Misc.getAOrAnFor(ll.getAiIntegrationStatusName())
                                    + " %s integration status with:";
                        tooltip.addPara(message, 10, Misc.getHighlightColor(), requiresCrew ? ll.getName() : ll.getAiIntegrationStatusName());
                        List<PersonAPI> captains = new LinkedList<>();
                        captains.add(Global.getSector().getPlayerPerson());

                        for (OfficerDataAPI od : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy()) {
                            captains.add(od.getPerson());
                        }

                        for (int i = 0; i < captains.size(); i++) {
                            PersonAPI captain = captains.get(i);

                            if(trustedOfficers.contains(Util.getCaptainId(captain))) {
                                tooltip.beginImageWithText(captain.getPortraitSprite(), 24)
                                    .addPara(captain.getNameString().trim(), 3);
                                tooltip.addImageWithText(i + 1 == captains.size() ? 10 : 3);
                            }
                        }
                    }
                }
            }

            for(Trait trait : rep.getTraits()) {
                if(traitsLeft <= 0) break;

                Trait.Tier currentTier = RepRecord.getTierFromTraitCount(traitsLeft--);

                if(currentTier != previousTier) {
                    tooltip.addPara("%s traits:", 10, Misc.getTextColor(), currentTier.getDisplayName());
                    previousTier = currentTier;
                }

                trait.addParagraphTo(tooltip, currentTier, loyaltyEffectAdjustment, requiresCrew, hullSize, false, false);
            }

            int rumoredTraitsToShow = Global.getSettings().isDevMode() ?
                    ModPlugin.RUMORED_TRAITS_SHOWN_IN_DEV_MODE : ModPlugin.RUMORED_TRAITS_SHOWN;

            if(rumoredTraitsToShow > 0 && rep.getTraits().size() < Trait.getTraitLimit()) {
                List<Trait> destinedTraits = RepRecord.getDestinedTraitsForShip(fm, true);
                int traitIndex = rep.getTraits().size();
                int traitIndexOfLastRumoredTraitToShow = Math.min(Trait.getTraitLimit(), traitIndex + rumoredTraitsToShow - 1);

                tooltip.addPara("Rumored traits:", 10, Misc.getGrayColor(), Misc.getGrayColor());

                while (traitIndex <= traitIndexOfLastRumoredTraitToShow && destinedTraits.size() > traitIndex) {
                    Trait trait = destinedTraits.get(traitIndex);
                    trait.addParagraphTo(tooltip, Trait.Tier.Rumored, loyaltyEffectAdjustment,
                            requiresCrew, hullSize, false, false);
                    traitIndex += 1;
                }
            }

            if(RepRecord.shipHasChronicler(fm)) {
                int daysLeft = RepRecord.getDaysOfChroniclerRemaining(fm);
                String timeLeft, effectStr = ship.getHullSpec().isCivilianNonCarrier()
                        ? (int)(100 * ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_CIVILIAN_SHIPS) + "%"
                        : (int)(100 * ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_COMBAT_SHIPS) + "%";
                String nextOrOther = daysLeft <= 0 ? "for a %s." : "for at least the next %s.";

                if(daysLeft <= 0) timeLeft = "short time";
                else if(daysLeft == 1) timeLeft = "day or so";
                else if(daysLeft > 60) timeLeft = (daysLeft / 30) + " months";
                else timeLeft = daysLeft + " days";

                tooltip.addPara("A chronicler is recording and publishing stories involving this ship, increasing " +
                        "reputation growth by %s " + nextOrOther, 10, Misc.getGrayColor(), Misc.getHighlightColor(),
                        effectStr, timeLeft);
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public Color getBorderColor() {
        return BORDER_COLOR;
    }

    @Override
    public Color getNameColor() {
        return NAME_COLOR;
    }

    @Override
    public int getDisplaySortOrder() {
        return -6;
    }

    @Override
    public int getDisplayCategoryIndex() {
        return 0;
    }
}
