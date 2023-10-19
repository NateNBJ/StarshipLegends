package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.input.Keyboard;
import starship_legends.*;

import java.awt.*;
import java.util.List;
import java.util.*;

import static starship_legends.events.OwnCrewBarEvent.OptionId.INVALID;
import static starship_legends.events.OwnCrewBarEvent.OptionId.LEAVE;

public class OwnCrewBarEvent extends BaseShipBarEvent {
    public enum OptionId {
        FLIP_TRAIT,
        REMOVE_DMOD,
        SIDEGRADE_TRAIT,
        CHRONICLE_OFFER,
        PICK_ALTERNATE,
        SHOW_SHIP,
        INVALID,
        ACCEPT,
        LEAVE;
    }

    public static float getChanceOfAnyCrewEvent() {
        return ModPlugin.TRAIT_UPGRADE_BAR_EVENT_CHANCE
                + ModPlugin.CHRONICLER_JOINS_BAR_EVENT_CHANCE
                + ModPlugin.TRAIT_SIDEGRADE_BAR_EVENT_CHANCE
                + ModPlugin.REPAIR_DMOD_BAR_EVENT_CHANCE;
    }

    OptionId subEvent = INVALID;
    String officerTypeStr, pref1, pref2, personDesc;

    int supplyCost = 0, loyaltyCost = 0, crewCost = 0;
    Trait trait = null, replacementTrait = null;
    HullModSpecAPI dmod = null;
    boolean crewTraitMismatch = false;

    int chronicleMonths = 0;
    List<FleetMemberAPI> chronicleAlternates = new ArrayList<>();
    String alternateShipTypeDesc, chroniclerDesc, shortChroniclerDesc = "the chronicler";

    boolean isShipViableForEvent(FleetMemberAPI ship, LoyaltyLevel loyaltyRequired) {
        return ship != null
                && RepRecord.isShipNotable(ship)
                && (loyaltyRequired == null || (ship.getCaptain() != null
                    && !ship.getCaptain().isDefault()
                    && RepRecord.get(ship).getLoyalty(ship.getCaptain()).getIndex() >= loyaltyRequired.getIndex()));
    }
    boolean tryCreateEvent(OptionId type) {
        reset();
        WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>(getRandom());

        switch (type) {
            case FLIP_TRAIT: {
                for (FleetMemberAPI ship : playerFleet.getFleetData().getMembersListCopy()) {
                    if(isShipViableForEvent(ship, LoyaltyLevel.FIERCELY_LOYAL) && RepRecord.get(ship).getFlipTrait() != null) {
                        picker.add(ship, RepRecord.get(ship).getTraits().size() * (ship.isFlagship() ? 3 : 1));
                    }
                }

                while (!picker.isEmpty()) {
                    setShip(picker.pickAndRemove());
                    trait = rep.getFlipTrait();
                    loyaltyCost = 1;

                    String id = trait.getType().getId();

                    if(id.equals("phase_mad")) replacementTrait = TraitType.get("sensor_strength").getTrait(false);
                    else if(id.equals("cursed")) replacementTrait = TraitType.get("flux_dissipation").getTrait(false);
                    else replacementTrait = trait.getType().getTrait(!trait.isMalus());

                    if(replacementTrait == null || rep.getTraits().contains(replacementTrait)) {
                        for(int i = 0; i < 12; ++i) {
                            replacementTrait = RepRecord.chooseRandomNewTrait(ship, getRandom(), !trait.isMalus());

                            if(replacementTrait != null && !replacementTrait.getType().getTags().contains(TraitType.Tags.CREW)) {
                                break;
                            }
                        }
                    }

                    if(replacementTrait != null) return true;
                }

                break;
            }
            case REMOVE_DMOD: {
                for (FleetMemberAPI ship : playerFleet.getFleetData().getMembersListCopy()) {
                    if(ship.getVariant().hasDMods() && isShipViableForEvent(ship, LoyaltyLevel.LOYAL)) {
                        picker.add(ship);
                    }
                }

                while (!picker.isEmpty()) {
                    setShip(picker.pickAndRemove());
                    loyaltyCost = 1;

                    List<HullModSpecAPI> dmods = new ArrayList<>();

                    for (String id : ship.getVariant().getHullMods()) {
                        HullModSpecAPI mod = DModManager.getMod(id);
                        if (mod.hasTag(Tags.HULLMOD_DMOD)) {
                            if (ship.getVariant().getHullSpec().getBuiltInMods().contains(id)) continue;

                            dmods.add(mod);
                        }
                    }

                    dmod = dmods.isEmpty() ? null : dmods.get(getRandom().nextInt(dmods.size()));

                    if(dmod != null) return true;
                }

                break;
            }
            case SIDEGRADE_TRAIT: {
                Set<FleetMemberAPI> shipsWithPreexistingSuggestions = RepSuggestionPopupEvent.getShipsWithActiveSuggestions();

                for (FleetMemberAPI ship : playerFleet.getFleetData().getMembersListCopy()) {
                    boolean hasCaptain = ship.getCaptain() != null && !ship.getCaptain().isDefault();

                    if((hasCaptain || !ModPlugin.ONLY_SUGGEST_SIDEGRADES_FOR_SHIPS_WITH_OFFICER)
                            && isShipViableForEvent(ship, null)
                            && !shipsWithPreexistingSuggestions.contains(ship)) {

                        float weight = RepRecord.get(ship).getTraits().size() * (1 + 0.2f * ship.getHullSpec().getHullSize().ordinal());

                        if(hasCaptain) weight *= 2;
                        else if(ship.getHullSpec().isCivilianNonCarrier()) weight *= 0.5f;

                        picker.add(ship, weight);
                    }
                }

                while (!picker.isEmpty()) {
                    setShip(picker.pickAndRemove());
                    trait = rep.getTraits().get(random.nextInt(rep.getTraits().size()));
                    replacementTrait = RepRecord.chooseRandomNewTrait(ship, getRandom(), trait.isMalus());

                    if(replacementTrait == null) continue;

                    crewTraitMismatch = trait.getType().getTags().contains(TraitType.Tags.CREW)
                            != replacementTrait.getType().getTags().contains(TraitType.Tags.CREW);

                    if(crewTraitMismatch && random.nextFloat() < 0.75f) continue;

                    return true;
                }

                break;
            }
            case CHRONICLE_OFFER: {
                for (FleetMemberAPI ship : playerFleet.getFleetData().getMembersListCopy()) {
                    RepRecord rr = RepRecord.get(ship);
                    boolean shipIsLegalHere = Util.isShipCrewed(ship)
                            || ( market.isPlayerOwned() || !market.getFaction().isIllegal(Commodities.AI_CORES));

                    if(isShipViableForEvent(ship, null) && !rr.hasMaximumTraits() && shipIsLegalHere) {
                        picker.add(ship, (float)Math.pow(rr.getTier().ordinal(), 2));
                    }
                }

                chronicleMonths = 4 + 4 * random.nextInt(4);
                chronicleAlternates.clear();

                while (!picker.isEmpty()) {
                    setShip(picker.pickAndRemove());

                    return true;
                }
            }
        }

        return false;
    }
    void addStandardOptions() {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        addShowShipOptionIfNotAlreadyShown(OptionId.SHOW_SHIP);
        options.addOption("Agree to the suggestion", OptionId.ACCEPT);
        options.addOption("Reject the suggestion for now", OptionId.LEAVE);
        options.setShortcut(FamousShipBarEvent.OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

        if(cargo.getCrew() < crewCost) {
            options.setEnabled(OptionId.ACCEPT, false);
            options.setTooltip(OptionId.ACCEPT, "You don't have enough crew (" + crewCost + " needed)");
        }
        if(cargo.getSupplies() < supplyCost) {
            options.setEnabled(OptionId.ACCEPT, false);
            options.setTooltip(OptionId.ACCEPT, "You don't have enough supplies (" + supplyCost + " needed)");
        }

//        String tt;
//
//        if(dmod != null) {
//            tt = "- The ship's %s will be repaired\n"
//                    + "- Loyalty will be reduced\n"
//                    + "- You will lose %s";
//
//            options.setTooltip(OptionId.ACCEPT, tt);
//            options.setTooltipHighlightColors(OptionId.ACCEPT, Misc.getHighlightColor(), Misc.getHighlightColor());
//            options.setTooltipHighlights(OptionId.ACCEPT, dmod.getDisplayName(), Misc.getDGSCredits(supplyCost));
//        } else {
//            tt = "- Gain %s %s\n"
//                    + "- Lose %s %s\n";
//
//            if(loyaltyCost > 0) {
//                tt += "- Loyalty will be reduced\n";
//
//                if(crewCost > 0) tt += "- You will lose %s crew";
//                if(supplyCost > 0) tt += "- You will lose %s supplies";
//            }
//
//            options.setTooltip(OptionId.ACCEPT, tt);
//            options.setTooltipHighlightColors(OptionId.ACCEPT, Misc.getHighlightColor(), Misc.getGrayColor(),
//                    Misc.getHighlightColor(), Misc.getGrayColor(), Misc.getNegativeHighlightColor(), Misc.getNegativeHighlightColor());
//            options.setTooltipHighlights(OptionId.ACCEPT,
//                    trait.getName(requiresCrew), trait.getParentheticalDescription(),
//                    replacementTrait.getName(requiresCrew), replacementTrait.getParentheticalDescription(),
//                    (crewCost > 0 ? crewCost : supplyCost) + "", supplyCost + "");
//        }
    }
    void addChroniclerOptions() {
        options.addOption("Allow them to chronicle the " + ship.getShipName(), OptionId.ACCEPT);

        if(!chronicleAlternates.isEmpty()) {
            options.addOption("Convince them to chronicle a different " + alternateShipTypeDesc + " for half the duration", OptionId.PICK_ALTERNATE);
        }

        options.addOption("Decline the offer for now", OptionId.LEAVE);
        options.setShortcut(FamousShipBarEvent.OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }
    void reset() {
        super.reset();
        crewCost = supplyCost = loyaltyCost = 0;
        trait = null;
        replacementTrait = null;
        dmod = null;
        crewTraitMismatch = false;
        chronicleMonths = 0;
        chronicleAlternates.clear();
    }
    void chooseStrings() {
        if(random == null) random = new Random(seed);

        if (!Util.isShipCrewed(ship)) personDesc = "The lead AI specialist";
        else if (captain.isPlayer()) personDesc = "Your second-in-command";
        else personDesc = "The ship's captain";

        if (!Util.isShipCrewed(ship)) officerTypeStr = "AI maintenance and monitoring team";
        else if (ship.getMinCrew() <= 30) officerTypeStr = "crew";
        else if (ship.getMinCrew() >= 300) officerTypeStr = "senior leadership";
        else officerTypeStr = "leadership";

        if (trait != null && replacementTrait != null) {
            pref1 = replacementTrait.getDescPrefix(requiresCrew, biological).toLowerCase();
            pref2 = trait.getDescPrefix(requiresCrew, biological, pref1).toLowerCase();
        }

        if(chronicleMonths > 0) {
            chronicleAlternates.clear();

            if(biological) {
                alternateShipTypeDesc = "organic ship";
                chroniclerDesc = "a biologist who is interested in publishing observations " +
                        "of space-faring megafauna in practical settings. The researcher";
                shortChroniclerDesc = "the biologist";

                for(FleetMemberAPI fm : playerFleet.getFleetData().getMembersListCopy()) {
                    ShipHullSpecAPI spec = fm.getHullSpec();

                    if(Util.isShipBiological(fm) && (spec.isCivilianNonCarrier() == ship.getHullSpec().isCivilianNonCarrier())) {
                        chronicleAlternates.add(fm);
                    }
                }
            } else if(!Util.isShipCrewed(ship)) {
                alternateShipTypeDesc = "autonomous ship";
                chroniclerDesc = "an artificial intelligence researcher who is interested in publishing observations " +
                        "of autonomous ships in practical settings. The researcher";
                shortChroniclerDesc = "the researcher";

                for(FleetMemberAPI fm : playerFleet.getFleetData().getMembersListCopy()) {
                    ShipHullSpecAPI spec = fm.getHullSpec();

                    if(!Util.isShipCrewed(fm) && (spec.isCivilianNonCarrier() == ship.getHullSpec().isCivilianNonCarrier())) {
                        chronicleAlternates.add(fm);
                    }
                }
            } else if(Util.isPhaseShip(ship)) {
                alternateShipTypeDesc = "phase ship";
                chroniclerDesc = "a hyperspace physicist who would like to study the effects of phase rift tearing on " +
                        "neural oscillations and publish their findings. The physicist";
                shortChroniclerDesc = "the physicist";

                for(FleetMemberAPI fm : playerFleet.getFleetData().getMembersListCopy()) {
                    if(Util.isPhaseShip(fm)) chronicleAlternates.add(fm);
                }
            } else if(ship.getHullSpec().isCivilianNonCarrier()) {
                alternateShipTypeDesc = "auxiliary ship";

                for(FleetMemberAPI fm : playerFleet.getFleetData().getMembersListCopy()) {
                    if(fm.getHullSpec().isCivilianNonCarrier()) chronicleAlternates.add(fm);
                }

                List<String> mods = ship.getHullSpec().getBuiltInMods();
                String designation = ship.getHullSpec().getDesignation().toLowerCase();

                if(mods.contains(HullMods.SURVEYING_EQUIPMENT)) {
                    chroniclerDesc = "a self-styled \"planetary survivalist\" who wants to accompany the " +
                            ship.getShipName() + "'s survey team during away missions and document their adventures. " +
                            "The survivalist";
                    shortChroniclerDesc = "the survivalist";
                } else if(mods.contains("ground_support") || mods.contains("advanced_ground_support")) {
                    chroniclerDesc = "an author of combat-action novels who wants to accompany your marines to " +
                            "document a story based on actual events. The novelist";
                    shortChroniclerDesc = "the novelist";
                } else if(mods.contains("repair_gantry")) {
                    chroniclerDesc = "an independent creator of holo-vid documentaries who would like to record the " +
                            "hazards and struggles suffered by working-class spacers during salvage operations. " +
                            "The documentary creator";
                    shortChroniclerDesc = "the documentary creator";
                } else if(designation.contains("tanker")) {
                    chroniclerDesc = "someone who describes themselves as a hyperlight drifter who hitch-hikes across " +
                            "the galaxy in search of exotic celestial phenomena to share with their network followers. " +
                            "The drifter";
                    shortChroniclerDesc = "the drifter";
                } else {
                    chroniclerDesc = "an independent creator of holo-vid documentaries who would like to record the " +
                            "day to day lifestyle aboard one of your vessels that, preferably, would never see combat. " +
                            "The documentary creator";
                    shortChroniclerDesc = "the documentary creator";
                }
            } else if(random.nextBoolean()) {
                ShipAPI.HullSize size = ship.getHullSpec().getHullSize();
                alternateShipTypeDesc = size.toString().toLowerCase().replace("_", "");

                for(FleetMemberAPI fm : playerFleet.getFleetData().getMembersListCopy()) {
                    if(fm.getHullSpec().getHullSize().equals(size) && !fm.getHullSpec().isCivilianNonCarrier()) {
                        chronicleAlternates.add(fm);
                    }
                }

                if(random.nextBoolean()) {
                    switch (size) {
                        case FRIGATE:
                            chroniclerDesc = "a tabloid journalist known for writing dramatizations of fast-paced " +
                                    "skirmishes between nimble frigates who is interested in flying with the crew of the " +
                                    ship.getShipName() + " for a while. The journalist";
                            shortChroniclerDesc = "the journalist";
                            break;
                        case DESTROYER:
                            chroniclerDesc = "a tabloid journalist known for writing dramatized versions of brutal " +
                                    "skirmishes between destroyers who is interested in flying with the crew of the " +
                                    ship.getShipName() + " for a while. The journalist";
                            shortChroniclerDesc = "the journalist";
                            break;
                        case CRUISER:
                            chroniclerDesc = "a tactical analyst known for pioneering theories detailing unconventional " +
                                    "applications for cruisers in battle, and has expressed interest in spending a while " +
                                    "aboard the " + ship.getShipName() + " to gain some real-world experience. The analyst";
                            shortChroniclerDesc = "the analyst";
                            break;
                        case CAPITAL_SHIP:
                            chroniclerDesc = "a tactical analyst known for pioneering theories detailing unconventional " +
                                    "applications for capital warships who has expressed interest in spending a while " +
                                    "aboard the " + ship.getShipName() + " to gain some real-world experience. The analyst";
                            shortChroniclerDesc = "the analyst";
                            break;
                    }
                } else {
                    switch (size) {
                        case FRIGATE:
                            chroniclerDesc = "a former fighter pilot who's made a name for themselves publishing " +
                                    "commentary on the application of frigates in combat, and thinks they " +
                                    "might be able to learn something new aboard the " + ship.getShipName() +
                                    ". The former pilot";
                            shortChroniclerDesc = "the former pilot";
                            break;
                        case DESTROYER:
                            chroniclerDesc = "a former executive officer who's made a name for themselves publishing " +
                                    "commentary on the application of destroyers in combat, and thinks they " +
                                    "might be able to learn something new aboard the " + ship.getShipName() +
                                    ". The former XO";
                            shortChroniclerDesc = "the former XO";
                            break;
                        case CRUISER:
                            chroniclerDesc = "a former warship captain who's made a name for themselves publishing " +
                                    "commentary on the application of cruisers in combat, and thinks they " +
                                    "might be able to learn something new aboard the " + ship.getShipName() +
                                    ". The former captain";
                            shortChroniclerDesc = "the former captain";
                            break;
                        case CAPITAL_SHIP:
                            chroniclerDesc = "a retired fleet commander who's made a name for themselves publishing " +
                                    "commentary on the application of capital ships in combat, and thinks they " +
                                    "might be able to learn something new aboard the " + ship.getShipName() +
                                    ". The former commander";
                            shortChroniclerDesc = "the former commander";
                            break;
                    }
                }
            } else {
                alternateShipTypeDesc = "warship";

                for(FleetMemberAPI fm : playerFleet.getFleetData().getMembersListCopy()) {
                    if(!fm.getHullSpec().isCivilianNonCarrier()) chronicleAlternates.add(fm);
                }

                if(market.isPlayerOwned()) {
                    if(random.nextBoolean()) {
                        chroniclerDesc = "a local of the colony who wants to witness your fleet's battles firsthand, " +
                                "and boasts an impressive network following. The colonist";
                        shortChroniclerDesc = "the colonist";
                    } else {
                        chroniclerDesc = "an independent journalist who is eager to peddle propaganda " +
                                "for the sake of " + market.getFaction().getDisplayNameLongWithArticle() +
                                ". The propagandist";
                        shortChroniclerDesc = "the propagandist";
                    }

                } else if(market.getFaction().equals(Misc.getCommissionFaction())) {
                    chroniclerDesc = "a war correspondent who is interested in reporting from the perspective of an " +
                            "independent war fleet fighting on behalf of " +
                            market.getFaction().getDisplayNameLongWithArticle() + ", such as your own. The correspondent";
                    shortChroniclerDesc = "the correspondent";
                } else {
                    chroniclerDesc = "a spacer who's made a name for themselves by collecting and publishing " +
                            "first-hand accounts of warship combat, and would like to travel with the crew of the " +
                            ship.getShipName() + " for a while. The spacer";
                    shortChroniclerDesc = "the spacer";
                }
            }

            chronicleAlternates.remove(ship);
        }
    }
    String getSidegradeProse(boolean requireFormality) {
        String officerType, planPhrase;
        boolean involvesSuperstition = trait.getType().getTags().contains(TraitType.Tags.SUPERSTITION)
                || replacementTrait.getType().getTags().contains(TraitType.Tags.SUPERSTITION);
        String knownForMaybe = involvesSuperstition ? " being known for " : " ";
        Random rand = new Random(seed);

        if(!requiresCrew) {
            officerType = requireFormality ? "lead programmer" : "best coder";
            planPhrase = (requireFormality ? "developed" : "came up with") + " an algorithm";
        } else if(involvesSuperstition) {
            officerType = "psychology officer";
            planPhrase = "proposed a policy change";
        } else if(crewTraitMismatch) {
            officerType = ship.getMinCrew() > 100 ? "chief engineer" : "engineer";
            planPhrase = "developed an automation process";
        } else if(trait.getType().getTags().contains(TraitType.Tags.CREW)) {
            officerType = "quartermaster";
            planPhrase = rand.nextBoolean()
                    ? (requireFormality ? "planned a crew reassignment" : "had a crew reassignment idea")
                    : "created a retraining regimen";
        } else {
            officerType = ship.getMinCrew() > 100 ? "chief engineer" : "engineer";
            planPhrase = rand.nextBoolean()
                    ? "suggested a subsystem adjustment"
                    : "designed an adapter replacement";
        }

        if(ship.getMinCrew() < 10) officerType = "XO";

        if(requireFormality) {
            if (!Util.isShipCrewed(ship)) personDesc = "The lead AI specialist for the ";
            else if (captain.isPlayer()) personDesc = "Your second-in-command aboard the ";
            else personDesc = "The captain of the ";

            return personDesc + ship.getShipName() + " submitted an approval request on behalf of their " + officerType
                    + ", who " + planPhrase + " that would result in the ship" + knownForMaybe + pref1
                    + " %s rather than" + pref2 + " %s.";
        } else {
            return "After a while, " + personDesc.toLowerCase() + " leans forward, seeming to become more sober. "
                    + "\"My " + officerType + " " + planPhrase + " that would result in the "
                    + ship.getShipName() + knownForMaybe + pref1 + " %s rather than" + pref2 + " %s. It seems "
                    + "reasonable to me, but I thought I'd run it by you before implementing it.\" "
                    + Misc.ucFirst(getHeOrShe()) + " slides over a tripad displaying charts and diagrams. \"The details,\" "
                    + getHeOrShe() + " explains.";
        }
    }
    void showLoyaltyCostWarning() {
        Color g = Misc.getGrayColor();
        Color h = Misc.getHighlightColor();

        LoyaltyLevel currLoyalty = rep.getLoyalty(captain);
        LoyaltyLevel newLoyalty = LoyaltyLevel.fromInt(currLoyalty.getIndex() - loyaltyCost);
        String currLoyaltyStr = (requiresCrew ? currLoyalty.getName() : currLoyalty.getAiIntegrationStatusName());
        String newLoyaltyStr = (requiresCrew ? newLoyalty.getName() : newLoyalty.getAiIntegrationStatusName());
        String consumeSuppliesString = "", supplyCostStr = "";
        String loyaltyCostString = requiresCrew
                ? "Agreeing to this will %s the standing of the crew from %s to %s"
                : "Agreeing to this will %s the integration status of the AI core from %s to %s";

        if(supplyCost > 0) {
            consumeSuppliesString = " and consume %s supplies";
            supplyCostStr = "" + supplyCost;
        }

        text.addPara(loyaltyCostString+ consumeSuppliesString, g, h, "reduce",
                currLoyaltyStr.toLowerCase(), newLoyaltyStr.toLowerCase(), supplyCostStr);
    }
    void doApproveActions() {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        if(trait != null && replacementTrait != null) {
            int traitIndex = rep.getTraitPosition(trait);

            if(traitIndex > -1) {
                rep.getTraits().add(traitIndex, replacementTrait);
                rep.getTraits().remove(trait);
            } else if(!rep.hasMaximumTraits() && !rep.hasTrait(replacementTrait)) {
                // If the original trait was already removed somehow, then still add the replacement trait
                rep.getTraits().add(replacementTrait);
            }
        }

        if(dmod != null) {
            DModManager.removeDMod(ship.getVariant(), dmod.getId());
            ship.setVariant(ship.getVariant(), true, true);
        }

        if(supplyCost > 0) {
            cargo.removeSupplies(supplyCost);
        }

        if(crewCost > 0) {
            cargo.removeCrew(crewCost);
        }

        if(loyaltyCost > 0 && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            rep.adjustLoyalty(captain, -loyaltyCost);
        }

        if(chronicleMonths > 0) {
            RepRecord.addChroniclerDays(ship, chronicleMonths * 30);
        }
    }
    void showApproveText(TextPanelAPI text) {
        if(trait != null && replacementTrait != null) {
            text.addPara("The " + ship.getShipName() + " is now known for " + pref1 + " %s instead of" + pref2 + " %s",
                    Misc.getTextColor(), Misc.getHighlightColor(),
                    replacementTrait.getLowerCaseName(requiresCrew, biological),
                    trait.getLowerCaseName(requiresCrew, biological)
            );
        }

        if(dmod != null) {
            text.addPara("The " + ship.getShipName() + " no longer has %s",
                    Misc.getTextColor(), Misc.getHighlightColor(),
                    dmod.getDisplayName().toLowerCase());
        }

        if(supplyCost > 0) {
            AddRemoveCommodity.addCommodityLossText(Commodities.SUPPLIES, supplyCost, text);
        }

        if(crewCost > 0) {
            AddRemoveCommodity.addCommodityLossText(Commodities.CREW, crewCost, text);
        }

        if(loyaltyCost > 0 && ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
            LoyaltyLevel ll = rep.getLoyalty(captain);
            String str = (requiresCrew ? ll.getName() : ll.getAiIntegrationStatusName());

            text.setFontSmallInsignia();

            if(requiresCrew) {
                text.addParagraph("The crew of the " + ship.getShipName() + " is now merely " + str + " "
                                + ll.getPreposition() + " " + captain.getNameString(),
                        Misc.getNegativeHighlightColor());
            } else {
                text.addParagraph("The AI integration status of the " + ship.getShipName() + " is now merely " + str,
                        Misc.getNegativeHighlightColor());
            }

            text.highlightInLastPara(Misc.getHighlightColor(), str);
            text.setFontInsignia();
        }

        if(chronicleMonths > 0) {
            String aboardOrStudying = Util.isShipCrewed(ship) ? "aboard" : "studying",
                    timeframe = chronicleMonths + " months",
                    effectStr = ship.getHullSpec().isCivilianNonCarrier()
                        ? (int)(100 * ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_CIVILIAN_SHIPS) + "%"
                        : (int)(100 * ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_COMBAT_SHIPS) + "%";;

            text.addPara("You agree to allow " + shortChroniclerDesc + " to spend at least %s "
                    + aboardOrStudying + " the " + ship.getShipName() + ", chronicling various aspects of its travels. " +
                    "This will increase the rate at which the ship's reputation grows by %s for the duration.",
                    Misc.getTextColor(), Misc.getHighlightColor(), timeframe, effectStr);

            text.addPara("You excuse yourself while one of your officers describes the various non-disclosure " +
                    "and network traffic limitations " + shortChroniclerDesc + " will be subjected to.");
        }
    }
    void createSmallDescriptionForIntel(RepSuggestionPopupEvent event, TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = Misc.getTextColor();
        float pad = 3f;
        float opad = 10f;
        List<FleetMemberAPI> shipList = new ArrayList<>();
        shipList.add(ship);

        if(captain != null && !captain.isDefault() && !captain.isPlayer()) {
            info.addImage(captain.getPortraitSprite(), width, 128, opad);
        }

        info.addPara(getSidegradeProse(true), opad, new Color[]{ h, h },
                replacementTrait.getLowerCaseName(requiresCrew, biological),
                trait.getLowerCaseName(requiresCrew, biological));
        info.addShipList(1, 1, 64, Color.WHITE, shipList, opad);

        if(event.approved) {
            info.addPara("You approved the suggestion, and the " + ship.getShipName() + " is now known for "
                            + pref1 + " %s instead of" + pref2 + " %s.", opad, tc, h,
                    replacementTrait.getLowerCaseName(requiresCrew, biological), trait.getLowerCaseName(requiresCrew, biological));
        } else if(event.isEnding()) {
            info.addPara("You rejected the suggestion. The " + ship.getShipName() + " will remain known for "
                            + trait.getDescPrefix(requiresCrew, biological).toLowerCase() + " %s.", opad, tc, h,
                    trait.getLowerCaseName(requiresCrew, biological));
        } else {
            trait.addComparisonParagraphsTo(info, ship, replacementTrait);
        }
    }
    boolean prepareForIntel() {
        subEvent = OptionId.SIDEGRADE_TRAIT;
        regen(null);

        if(subEvent == INVALID) return false;

        if(captain != null && !captain.isDefault() && !captain.isPlayer() && Util.isShipCrewed(ship)) {
            person = captain;
        }

        chooseStrings();

        return true;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES ? false : super.shouldShowAtMarket(market)
                && (market.getFaction().getRelToPlayer().isAtWorst(RepLevel.SUSPICIOUS) || market.getFaction().isPlayerFaction())
                && Integration.isFamousFlagshipEventAvailableAtMarket(market)
                && getChanceOfAnyCrewEvent() > 0;
    }

    @Override
    protected void regen(MarketAPI market) {
        playerFleet = Global.getSector().getPlayerFleet();

        if(market == null) {
            seed = Global.getSector().getClock().getTimestamp();
            random = new Random(seed);
            person = createPerson();
        } else {
            if (this.market == market || playerFleet == null) return;

            super.regen(market);
        }

        reset();

        if (Global.getSettings().isDevMode()) random = new Random();

        if(subEvent == INVALID) {
            WeightedRandomPicker<OptionId> picker = new WeightedRandomPicker(getRandom());
            picker.add(OptionId.FLIP_TRAIT, ModPlugin.TRAIT_UPGRADE_BAR_EVENT_CHANCE);
            picker.add(OptionId.SIDEGRADE_TRAIT, ModPlugin.TRAIT_SIDEGRADE_BAR_EVENT_CHANCE);
            picker.add(OptionId.REMOVE_DMOD, ModPlugin.REPAIR_DMOD_BAR_EVENT_CHANCE);
            picker.add(OptionId.CHRONICLE_OFFER, ModPlugin.CHRONICLER_JOINS_BAR_EVENT_CHANCE);

            while (!picker.isEmpty()) {
                OptionId pick = picker.pickAndRemove();

                if (tryCreateEvent(pick)) {
                    subEvent = pick;
                    break;
                }
            }
        } else if(!tryCreateEvent(subEvent)) {
            subEvent = INVALID;
        }
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        try {
            super.addPromptAndOption(dialog, memoryMap);

            regen(dialog.getInteractionTarget().getMarket());

            String shipOrShips = Global.getSector().getPlayerFleet().getNumShips() == 1 ? "your ship" : "one of your ships";
            TextPanelAPI text = dialog.getTextPanel();
            text.addPara("A small group of officers from " + shipOrShips + " are here on shore leave. One of them waves.");

            dialog.getOptionPanel().addOption("Join your officers for a drink", this);
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        try {
            super.init(dialog, memoryMap);

            done = false;

            if(subEvent != INVALID) {
//                if(captain == null || captain.isDefault() || captain.isPlayer() || !Util.isShipCrewed(ship)) {
                    dialog.getVisualPanel().showFleetMemberInfo(ship);
//                } else {
//                    person = captain;
//                    dialog.getVisualPanel().showPersonInfo(person, true);
//                }

                chooseStrings();

                dialog.getTextPanel().addPara("You join the group of officers from the " + ship.getShipName()
                        + " " + officerTypeStr + ". A few of the lower-ranking officers seem ill at ease with their boss intruding "
                        + "on what is supposed to be their time off, but they relax after you buy a round of drinks and "
                        + "make it clear that the normal rules don't apply here.");

                //dialog.makeOptionOpenCore(OptionId.SHOW_SHIP.toString(), CoreUITabId.REFIT, CampaignUIAPI.CoreUITradeMode.NONE);
            }

            optionSelected(null, subEvent);
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        try {
            if (!(optionData instanceof OwnCrewBarEvent.OptionId)) return;

            OptionPanelAPI options = dialog.getOptionPanel();
            TextPanelAPI text = dialog.getTextPanel();
            options.clearOptions();
            Color g = Misc.getGrayColor();
            Color h = Misc.getHighlightColor();

            if(random == null) random = new Random(seed);
            if(!rep.hasTrait(trait) && optionData != LEAVE) optionData = INVALID;

            switch ((OptionId) optionData) {
                case FLIP_TRAIT: {
                    int maxCrewCost = requiresCrew ? (int)Math.max(2, ship.getHullSpec().getMinCrew() * 0.05f) : 0;
                    Set<String> tags = replacementTrait.getTags();
                    String evenTheCrewMaybe = requiresCrew && !trait.getTags().contains(TraitType.Tags.CREW)
                            ? ", even the crew" : "";
                    String shipDesc = biological ? "impressive creature" : "fine ship";
                    String str = personDesc + " eventually leans forward, seeming to become more sober. "
                            + "\"The " + ship.getShipName() + " is a " + shipDesc + ", but I think people underestimate it"
                            + evenTheCrewMaybe + ". You're aware that it has a reputation for "
                            + trait.getDescPrefix(requiresCrew, biological).toLowerCase() + " %s? ";

                    if(biological) {
                        str += "Well, I figured out what the problem is and I've already cultivated an enzyme that " +
                                "would fix it. I won't go into details about the biological processes involved so " +
                                "we can all keep our drinks down. The important thing is that it will work. " +
                                "The only catch is that ";

                        if(requiresCrew) {
                            str += "it will cause substantial discomfort for the crew.";
                        } else {
                            str += "it will disrupt the integration status of the control module somewhat.";
                        }

                        str += " Do you think I should go through with it?\"";
                    } else if(trait.getType().getId().equals("phase_mad")) {
                        str += "Well, I've found a solution. Nothing can be done about the unusually high amount of "
                                + "brainwave interference caused by the ship's phase field, but I've discovered a "
                                + "neural augmentation analeptic developed by the Tri-Tachyon corporation "
                                + "that counteracts the effects. It would even heighten the crew's senses. I'm sure many "
                                + "of the crew won't appreciate the benefits, given that it can cause discomfort. I'm "
                                + "sure it won't take them too long to acclimate, however. Thoughts?\"";
                    } else if(requiresCrew && trait.getType().getId().equals("recovery_chance")) {
                        str += "It's superstitious nonsense, of course. Unfortunately, the effects are quite real. "
                                + "The crew is quick to despair and give up during recovery operations due to their "
                                + "misguided beliefs. I think we can turn that around with a properly executed "
                                + "re-education campaign. My quartermaster and I have been working on "
                                + "something that should do the trick, although I'm sure it will alienate some of our "
                                + "most superstitious crew members. All we need now is your permission.\"";
                    } else if(trait.getType().getId().equals("cursed")) {
                        str += "It's superstitious nonsense, of course. Unfortunately, the effects are quite real. "
                                + "The crew tends to panic and make careless mistakes when they hear certain noises "
                                + "from the ships flux valves, which they foolishly interpret as some sort of omen. "
                                + "The irony is that those sounds are caused by a quirk of the ship's flux regulation "
                                + "network that would be beneficial if the crew knew how to take advantage of it. "
                                + "I think we can turn this sorry state of affairs around with a properly executed "
                                + "re-education campaign. My quartermaster and I have been working on "
                                + "something that should do the trick, although I'm sure it will alienate some of our "
                                + "most superstitious crew members. All we need now is your permission.\"";
                        str += "";
                    } else if(tags.contains(TraitType.Tags.CREW)) {
                        if(requiresCrew) {
                            maxCrewCost = crewCost = (int)Math.max(2, ship.getHullSpec().getMinCrew() * 0.1f);
                            str += "Well, I believe I've identified the crew members responsible for that. "
                                    + "If you like, I can terminate their contracts and ensure that their replacements "
                                    + "are properly capable. All told, we'd have to replace %s people. I'm sure you're "
                                    + "well aware of what that would do to morale, but i think it would be for the best in the long run.\"";
                        } else {
                            str += "Well, it turns out the AI persona has a corrupt subroutine that's the source of the "
                                    + "issue. Unfortunately, repairing it would also result in a moderate degradation "
                                    + "of the persona's social integration network, but that will repair over time. Say "
                                    + "the word and it's done.\"";
                        }
                    } else {
                        supplyCost = (int)ship.getHullSpec().getSuppliesPerMonth();
                        str += "Well, I think I've tracked down the reason for that. It turns out there's a fault in the ";

                        if(tags.contains(TraitType.Tags.SHIELD)) {
                            str += "shield emission nodes";
                        } else if (tags.contains(TraitType.Tags.CLOAK)) {
                            str += "hyperspace resonance diodes";
                        } else if (tags.contains(TraitType.Tags.CARRIER)) {
                            str += "flight coordination relay devices";
                        } else if (tags.contains(TraitType.Tags.SYSTEM)) {
                            str += "ship system catalyst";
                        } else if (tags.contains(TraitType.Tags.FLUX)) {
                            str += "secondary flux valves";
                        } else if (tags.contains(TraitType.Tags.DEFENSE)) {
                            str += "joists of the inner hull";
                        } else if (tags.contains(TraitType.Tags.DMOD)) {
                            str += "substructure support network";
                        } else if (tags.contains(TraitType.Tags.ATTACK)) {
                            str += "ordnance couplings";
                        } else {
                            str += "subsystem processors";
                        }

                        if(crewCost == 0) crewCost = random.nextInt(maxCrewCost + 1);

                        String supplyAmountDesc = "moderate";

                        if(supplyCost < 10) supplyAmountDesc = "paltry";
                        else if(supplyCost >= 30) supplyAmountDesc = "significant";

                        if(requiresCrew) {
                            str += ". Replacing the necessary parts would require a " + supplyAmountDesc + " amount of "
                                    + "supplies, but the real catch is that the work involved is so dangerous. The loyalty "
                                    + "of the ship's crew will surely suffer because of it, and we could lose as many as "
                                    + "%s personnel. It's not an easy call, but it's not mine to make. What do you think?\"";
                        } else {
                            str += ". Replacing the necessary parts would require a " + supplyAmountDesc + " amount of "
                                    + "supplies, but the real catch is that the work involved is would disrupt the "
                                    + "integration status of the AI. It's not an easy call, but it's not mine to make. "
                                    + "What do you think?\"";
                        }
                    }

                    text.addPara(str, Misc.getTextColor(), h, trait.getName(requiresCrew, biological).toLowerCase(), maxCrewCost + "");

                    text.setFontSmallInsignia();
                    text.addPara("%s " + trait.getDescription(), g, h, trait.getName(requiresCrew, biological));

                    if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
                        showLoyaltyCostWarning();
                    } else {
                        if(supplyCost != 0) text.addPara("Agreeing to this will consume %s supplies", g, h, "" + supplyCost);
                    }

                    if(maxCrewCost != 0) text.addPara("Up to %s crew may be lost", g, h, "" + maxCrewCost);

                    text.setFontInsignia();

                    addStandardOptions();
                    break;
                }
                case REMOVE_DMOD: {
                    int maxCrewCost = requiresCrew ? (int)Math.max(2, ship.getHullSpec().getMinCrew() * 0.05f) : 0;
                    String meOrYou = captain.isPlayer() ? "you" : "me";
                    String catchStr = requiresCrew
                            ? "the labor it would require is dangerous and toilsome. We might lose "
                                + "as many as %s crew, and they're sure to resent " + meOrYou + " for the order"
                            : "the process will interfere with the integration of the AI core";

                    if(requiresCrew && crewCost == 0) crewCost = random.nextInt(maxCrewCost + 1);

                    text.addPara(personDesc + " mentions a ship mechanic " + getHeOrShe() + " met recently who described "
                            + "a way to fix the " + ship.getShipName() + "'s %s. \"I was skeptical at first, but my"
                            + (ship.getMinCrew() >= 100 ? " chief" : "") + " engineer assured me it would work. The "
                            + "only catch is that " + catchStr + ". I thought it might be worth considering anyway.\"",
                            Misc.getTextColor(), Misc.getHighlightColor(), dmod.getDisplayName().toLowerCase(),
                            maxCrewCost + "");

                    if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
                        text.setFontSmallInsignia();
                        showLoyaltyCostWarning();
                        text.setFontInsignia();
                    }

                    addStandardOptions();
                    break;
                }
                case SIDEGRADE_TRAIT: {
                    text.addPara(getSidegradeProse(false), Misc.getTextColor(), Misc.getHighlightColor(),
                            replacementTrait.getLowerCaseName(requiresCrew, biological), trait.getLowerCaseName(requiresCrew, biological));

                    text.setFontSmallInsignia();
                    trait.addComparisonParagraphsTo(text, ship, replacementTrait);
                    text.setFontInsignia();

                    addStandardOptions();
                    break;
                }
                case CHRONICLE_OFFER: {
                    text.addPara(personDesc + " introduces you to " + chroniclerDesc + " explains that they would " +
                                    "like to remain with your fleet for about %s. They reassure you that they " +
                                    "would comply with any information security protocols you require.",
                            Misc.getTextColor(), h, chronicleMonths + " months");

                    String fameBonus = ship.getHullSpec().isCivilianNonCarrier()
                            ? (int)(100 * ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_CIVILIAN_SHIPS) + "%"
                            : (int)(100 * ModPlugin.FAME_BONUS_FROM_CHRONICLERS_FOR_COMBAT_SHIPS) + "%";
                    String message = Util.isShipCrewed(ship)
                            ? "With this person's connections, their presence aboard one of your " +
                            alternateShipTypeDesc + "s would increase its reputation growth rate by %s"
                            : "With this person's connections, any autonomous ship they study would grow in " +
                            "reputation %s more quickly";

                    text.setFontSmallInsignia();
                    text.addPara(message, g, h, fameBonus);
                    text.setFontInsignia();

                    addChroniclerOptions();

                    break;
                }
                case PICK_ALTERNATE: {
                    dialog.showFleetMemberPickerDialog("Select craft to chronicle", "Ok", "Cancel",
                            3, 7, 58f, true, false, chronicleAlternates,
                            new FleetMemberPickerListener() {
                                public void pickedFleetMembers(List<FleetMemberAPI> members) {
                                    if(!members.isEmpty()) {
                                        ship = members.get(0);
                                        chronicleMonths /= 2;
                                        optionSelected(null, OptionId.ACCEPT);
                                    }
                                }
                                public void cancelledFleetMemberPicking() { }
                            });

                    addChroniclerOptions();

                    break;
                }
                case SHOW_SHIP: {
                    showShip();
                    addStandardOptions();
                    break;
                }
                case INVALID: {
                    text.addPara("You talk with your officers for a while, but nothing significant comes of it.");
                    options.addOption("Continue", OptionId.LEAVE);
                    options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
                    BarEventManager.getInstance().notifyWasInteractedWith(this);
                    break;
                }
                case ACCEPT: {
                    doApproveActions();
                    showApproveText(text);
                    BarEventManager.getInstance().notifyWasInteractedWith(this);
                    options.addOption("Continue", OptionId.LEAVE);
                    break;
                }
                case LEAVE: {
                    done = noContinue = true;
                    break;
                }
            }
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }

    @Override
    protected String getPersonFaction() {
        return Global.getSector().getPlayerFaction().getId();
    }

    @Override
    protected String getPersonRank() {
        return Ranks.SPACE_CAPTAIN;
    }

    @Override
    protected String getPersonPost() {
        return Ranks.SPACE_CAPTAIN;
    }
}
