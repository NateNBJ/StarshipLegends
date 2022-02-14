package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
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

public class OwnCrewBarEvent extends BaseShipBarEvent {
    public enum OptionId {
        FLIP_TRAIT,
        REMOVE_DMOD,
        SIDEGRADE_TRAIT,
        SHOW_SHIP,
        INVALID,
        ACCEPT,
        LEAVE;
    }

    public static float getChanceOfAnyCrewEvent() {
        return ModPlugin.TRAIT_UPGRADE_BAR_EVENT_CHANCE
                + ModPlugin.TRAIT_SIDEGRADE_BAR_EVENT_CHANCE
                + ModPlugin.REPAIR_DMOD_BAR_EVENT_CHANCE;
    }

    OptionId subEvent = OptionId.INVALID;
    String officerTypeStr, pref1, pref2, personDesc;

    int supplyCost = 0, loyaltyCost = 0, crewCost = 0;
    Trait trait = null, replacementTrait = null;
    HullModSpecAPI dmod = null;
    boolean crewTraitMismatch = false;

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
                FleetMemberAPI shipWithPreexistingSuggestion = RepSuggestionPopupEvent.getActiveSuggestion() == null
                        ? null : RepSuggestionPopupEvent.getActiveSuggestion().barEvent.ship;

                for (FleetMemberAPI ship : playerFleet.getFleetData().getMembersListCopy()) {
                    if(isShipViableForEvent(ship, null) && ship != shipWithPreexistingSuggestion) {
                        picker.add(ship, RepRecord.get(ship).getTraits().size()
                                * (ship.getHullSpec().isCivilianNonCarrier() ? 0.5f : 1));
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
    void reset() {
        super.reset();
        crewCost = supplyCost = loyaltyCost = 0;
        trait = null;
        replacementTrait = null;
        dmod = null;
        crewTraitMismatch = false;
    }
    void chooseStrings() {
        if (ship.getMinCrew() <= 0) officerTypeStr = "AI maintenance and monitoring team";
        else if (ship.getMinCrew() <= 30) officerTypeStr = "crew";
        else if (ship.getMinCrew() >= 300) officerTypeStr = "senior leadership";
        else officerTypeStr = "leadership";

        if (trait != null && replacementTrait != null) {
            pref1 = replacementTrait.getDescPrefix(requiresCrew).toLowerCase();
            pref2 = trait.getDescPrefix(requiresCrew, pref1).toLowerCase();
        }

        if (ship.getMinCrew() <= 0) personDesc = "The lead AI specialist";
        else if (captain.isPlayer()) personDesc = "Your second-in-command";
        else personDesc = "The ship's captain";
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
            if (ship.getMinCrew() <= 0) personDesc = "The lead AI specialist for the ";
            else if (captain.isPlayer()) personDesc = "Your second-in-command aboard the ";
            else personDesc = "The captain of the ";

            return personDesc + ship.getShipName() + " submitted an approval request on behalf of their " + officerType
                    + ", who " + planPhrase + " that would result in the ship" + knownForMaybe + pref1
                    + " %s %s rather than" + pref2 + " %s %s.";
        } else {
            return "After a while, " + personDesc.toLowerCase() + " leans forward, seeming to become more sober. "
                    + "\"My " + officerType + " " + planPhrase + " that would result in the "
                    + ship.getShipName() + knownForMaybe + pref1 + " %s rather than" + pref2 + " %s. It seems "
                    + "reasonable to me, but I thought I'd run it by you before implementing it.\" "
                    + Misc.ucFirst(getHeOrShe()) + " slides over a tripad displaying charts and diagrams. \"The details,\" "
                    + getHeOrShe() + " explains.";
        }
    }
    void doApproveActions() {
        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

        if(trait != null && replacementTrait != null) {
            rep.getTraits().add(rep.getTraitPosition(trait), replacementTrait);
            rep.getTraits().remove(trait);
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
    }
    void showApproveText(TextPanelAPI text) {
        if(trait != null && replacementTrait != null) {
            text.addPara("The " + ship.getShipName() + " is now known for " + pref1 + " %s instead of" + pref2 + " %s",
                    Misc.getTextColor(), Misc.getHighlightColor(),
                    replacementTrait.getLowerCaseName(requiresCrew),
                    trait.getLowerCaseName(requiresCrew)
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
            String str = ll.getName();

            text.setFontSmallInsignia();
            text.addParagraph("The crew of the " + ship.getShipName() + " is now merely " + str + " "
                            + ll.getPreposition() + " " + captain.getNameString(),
                    Misc.getNegativeHighlightColor());
            text.highlightInLastPara(Misc.getHighlightColor(), str);
            text.setFontInsignia();
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
            info.addImage(getPerson().getPortraitSprite(), width, 128, opad);
        }

        info.addPara(getSidegradeProse(true), opad, new Color[]{ h, g, h, g },
                replacementTrait.getLowerCaseName(requiresCrew),
                replacementTrait.getParentheticalDescription(),
                trait.getLowerCaseName(requiresCrew),
                trait.getParentheticalDescription());
        info.addShipList(1, 1, 64, Color.WHITE, shipList, opad);
        info.addPara("The " + ship.getShipName() + " is known for these traits:", opad);
        Util.showTraits(info, rep, captain, requiresCrew,
                ship.getHullSpec().getHullSize());

        if(event.approved) {
            info.addPara("You approved the suggestion, and the " + ship.getShipName() + " is now known for "
                            + pref1 + " %s instead of" + pref2 + " %s.", opad, tc, h,
                    replacementTrait.getLowerCaseName(requiresCrew), trait.getLowerCaseName(requiresCrew));
        }
    }
    boolean prepareForIntel() {
        subEvent = OptionId.SIDEGRADE_TRAIT;
        regen(null);

        if(subEvent == OptionId.INVALID) return false;

        if(captain != null && !captain.isDefault() && !captain.isPlayer()) {
            person = captain;
        }

        chooseStrings();

        return true;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES ? false : super.shouldShowAtMarket(market)
                && (market.getFaction().getRelToPlayer().isAtWorst(RepLevel.SUSPICIOUS) || market.getFaction().isPlayerFaction())
                && Integration.isFamousFlagshipEventAvailableAtMarket(market);
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

        if(subEvent == OptionId.INVALID) {
            WeightedRandomPicker<OptionId> picker = new WeightedRandomPicker(getRandom());
            picker.add(OptionId.FLIP_TRAIT, ModPlugin.TRAIT_UPGRADE_BAR_EVENT_CHANCE);
            picker.add(OptionId.SIDEGRADE_TRAIT, ModPlugin.TRAIT_SIDEGRADE_BAR_EVENT_CHANCE);
            picker.add(OptionId.REMOVE_DMOD, ModPlugin.REPAIR_DMOD_BAR_EVENT_CHANCE);

            while (!picker.isEmpty()) {
                OptionId pick = picker.pickAndRemove();

                if (tryCreateEvent(pick)) {
                    subEvent = pick;
                    break;
                }
            }
        } else if(!tryCreateEvent(subEvent)) {
            subEvent = OptionId.INVALID;
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

            if(subEvent != OptionId.INVALID) {
                if(captain == null || captain.isDefault() || captain.isPlayer()) {
                    dialog.getVisualPanel().showFleetMemberInfo(ship);
                } else {
                    person = captain;
                    dialog.getVisualPanel().showPersonInfo(person, true);
                }

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

            switch ((OptionId) optionData) {
                case FLIP_TRAIT: {
                    int maxCrewCost = (int)Math.max(2, ship.getHullSpec().getMinCrew() * 0.05f);
                    Set<String> tags = replacementTrait.getTags();
                    String evenTheCrewMaybe = requiresCrew && !trait.getTags().contains(TraitType.Tags.CREW)
                            ? ", even the crew" : "";
                    String str = personDesc + " eventually leans forward, seeming to become more sober. "
                            + "\"The " + ship.getShipName() + " is a fine ship, but I think people underestimate it"
                            + evenTheCrewMaybe + ". You're aware that it has a reputation for "
                            + trait.getDescPrefix(requiresCrew).toLowerCase() + " %s? ";

                    if(trait.getType().getId().equals("phase_mad")) {
                        str += "Well, I've found a solution. Nothing can be done about the unusually high amount of "
                                + "brainwave interference caused by the ship's phase field, but I've discovered a "
                                + "neural augmentation analeptic developed by the Tri-Tachyon corporation "
                                + "that counteracts the effects. It would even heighten the crew's senses. I'm sure many "
                                + "of the crew won't appreciate the benefits, given that it can cause discomfort. I'm "
                                + "sure it won't take them too long to acclimate, however. Thoughts?\"";
                    } else if(trait.getType().getId().equals("recovery_chance")) {
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

                        str += ". Replacing the necessary parts would require a " + supplyAmountDesc + " amount of "
                                + "supplies, but the real catch is that the work involved is so dangerous. The loyalty "
                                + "of the ship's crew will surely suffer because of it, and we could lose as many as "
                                + "%s personnel. It's not an easy call, but it's not mine to make. What do you think?\"";
                    }

                    text.addPara(str, Misc.getTextColor(), h, trait.getName(requiresCrew).toLowerCase(), maxCrewCost + "");

                    text.setFontSmallInsignia();
                    text.addPara("%s " + trait.getDescription(), g, h, trait.getName(requiresCrew));

                    if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
                        if(supplyCost == 0) text.addPara("Agreeing to this will %s the loyalty of the crew", g, h, "reduce");
                        else text.addPara("Agreeing to this will %s the loyalty of the crew and consume %s supplies", g, h, "reduce", "" + supplyCost);
                    } else {
                        if(supplyCost != 0) text.addPara("Agreeing to this will consume %s supplies", g, h, "" + supplyCost);
                    }

                    if(maxCrewCost != 0) text.addPara("Up to %s crew may be lost", g, h, "" + maxCrewCost);

                    text.setFontInsignia();

                    addStandardOptions();
                    break;
                }
                case REMOVE_DMOD: {
                    int maxCrewCost = (int)Math.max(2, ship.getHullSpec().getMinCrew() * 0.05f);
                    String meOrYou = captain.isPlayer() ? "you" : "me";

                    if(crewCost == 0) crewCost = random.nextInt(maxCrewCost + 1);

                    text.addPara(personDesc + " mentions a ship mechanic " + getHeOrShe() + " met recently who described "
                            + "a way to fix the " + ship.getShipName() + "'s %s. \"I was skeptical at first, but my "
                            + (ship.getMinCrew() >= 100 ? "chief" : "") + " engineers assured me it would work. The "
                            + "only catch is that the labor it would require is dangerous and toilsome. We might lose "
                            + "as many as %s crew, and they're sure to resent " + meOrYou + " for the order. I thought it might "
                            + "be worth considering anyway.\"",
                            Misc.getTextColor(), Misc.getHighlightColor(), dmod.getDisplayName().toLowerCase(),
                            maxCrewCost + "");

                    if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM) {
                        text.setFontSmallInsignia();
                        text.addPara("Agreeing to this will reduce the loyalty of the crew", Misc.getGrayColor());
                        text.setFontInsignia();
                    }

                    addStandardOptions();
                    break;
                }
                case SIDEGRADE_TRAIT: {
                    text.addPara(getSidegradeProse(false), Misc.getTextColor(), Misc.getHighlightColor(),
                            replacementTrait.getLowerCaseName(requiresCrew), trait.getLowerCaseName(requiresCrew));

                    text.setFontSmallInsignia();
                    text.addPara("%s " + replacementTrait.getDescription(), Misc.getGrayColor(), Misc.getHighlightColor(),
                            replacementTrait.getName(requiresCrew));
                    text.addPara("%s " + trait.getDescription(), Misc.getGrayColor(), Misc.getHighlightColor(),
                            trait.getName(requiresCrew));
                    text.setFontInsignia();

                    addStandardOptions();
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
