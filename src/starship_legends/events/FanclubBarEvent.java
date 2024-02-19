package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.DModManager;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflaterParams;
import com.fs.starfarer.api.impl.campaign.ids.Commodities;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.impl.campaign.rulecmd.AddRemoveCommodity;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.MutableValue;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.lwjgl.input.Keyboard;
import starship_legends.*;

import java.util.*;

public class FanclubBarEvent extends BaseShipBarEvent {
    public enum OptionId {
        BUY_SHIP_OFFER,
        SHIP_JOIN_OFFER,
        CREW_JOIN_OFFER,
        SHOW_CAPTAIN,
        SHOW_SHIP,
        INVALID,
        ACCEPT,
        LEAVE,
    }

    public static float getChanceOfAnyFanclubEvent() {
        if(!Util.isAnyShipInPlayerFleetNotable()) return 0;

        return ModPlugin.LOYAL_CREW_JOINS_BAR_EVENT_CHANCE
                + ModPlugin.BUY_SHIP_OFFER_BAR_EVENT_CHANCE
                + ModPlugin.JOIN_WITH_SHIP_BAR_EVENT_CHANCE;
    }

    OptionId subEvent = OptionId.INVALID;
    int creditChange = 0;
    int crewChange = 0;
    boolean captainShown = false;
    MarketAPI marketWhereShipIsStored = null;

    boolean tryCreateEvent(OptionId type) {
        reset();

        WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>(getRandom());
        float baseCrewCost = Global.getSector().getEconomy().getCommoditySpec(Commodities.CREW).getBasePrice();

        switch (type) {
            case BUY_SHIP_OFFER: {
                for (FleetMemberAPI ship : Util.findPlayerOwnedShip(Trait.Tier.Famous)) {
                    if(Util.isShipCrewed(ship)) {
                        picker.add(ship, RepRecord.get(ship).getTier() == Trait.Tier.Legendary ? 4 : 1);
                    }
                }

                while (!picker.isEmpty()) {
                    setShip(picker.pickAndRemove());

                    float repMult = 1 + (rep.getFractionOfBonusEffectFromTraits() - 0.5f) * rep.getTraits().size() * 0.5f;
                    float qualMult = 1 - DModManager.getNumDMods(ship.getVariant()) * 0.2f;

                    if(repMult * qualMult >= 1 && !(ship.isFlagship() && playerFleet.getFleetData().getNumMembers() < 2)) {
                        boolean isRemote = !playerFleet.getFleetData().getMembersListCopy().contains(ship);

                        crewChange = isRemote ? 0 : (int) -ship.getMinCrew();
                        creditChange = (int)(crewChange * baseCrewCost
                                + ship.getBaseBuyValue() * repMult * qualMult
                                + ship.getVariant().getSMods().size() * 100000 * (ship.getHullSpec().getHullSize().ordinal() - 1));

                        if(!playerFleet.getFleetData().getMembersListCopy().contains(ship)) {
                            marketWhereShipIsStored = Util.getStorageLocationOfShip(ship.getId());

                            if(marketWhereShipIsStored == null) return false;
                        }

                        return true;
                    }
                }
                break;
            }
            case SHIP_JOIN_OFFER: {
                do {
                    String variantID = FamousShipBarEvent.chooseDerelictVariant(market, random);

                    if (variantID == null) return false;

                    ship = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variantID);
                } while (ship.getHullSpec().isCivilianNonCarrier());

                FamousShipBarEvent.pickShipName(ship, random);

                FactionAPI faction = random.nextBoolean()
                        ? market.getFaction() : Global.getSector().getFaction(Factions.INDEPENDENT);
                captain = OfficerManagerEvent.createOfficer(faction, 1 + random.nextInt(3),
                        OfficerManagerEvent.SkillPickPreference.ANY, random);
                captain.setPersonality(faction.pickPersonality());
                ship.setCaptain(captain);

                int traitCount = (1 + random.nextInt(2) + random.nextInt(2)) * 2;
                rep = RepRecord.getOrCreate(ship);
                rep.adjustLoyalty(captain, 1 + random.nextInt(2));
                RepRecord.addDestinedTraitsToShip(ship, requiresCrew, traitCount);
                rep.applyToShip(ship);

                ship.setOwner(1);
                CampaignFleetAPI temp = Global.getFactory().createEmptyFleet(Factions.INDEPENDENT, FleetTypes.PATROL_SMALL, true);
                temp.getFleetData().addFleetMember(ship);
                DefaultFleetInflaterParams params = new DefaultFleetInflaterParams();
                params.allWeapons = random.nextBoolean();
                params.factionId = faction.getId();
                params.quality = 1;
                DefaultFleetInflater inflater = new DefaultFleetInflater(params);
                inflater.inflate(temp);
                int sMods = Math.max(0, random.nextInt(3) - ship.getVariant().getSMods().size());
                int dMods = random.nextInt(3) + (ship.getVariant().isDHull() ? 1 : 0);
                //List<String> sModsToReAdd = new LinkedList<>();

                if (sMods > 0) {
                    List<HullModSpecAPI> mods = new ArrayList<>();

                    for (String id : ship.getVariant().getNonBuiltInHullmods()) {
                        mods.add(Global.getSettings().getHullModSpec(id));
                    }

                    Collections.sort(mods, new Comparator<HullModSpecAPI>() {
                        @Override
                        public int compare(HullModSpecAPI mod1, HullModSpecAPI mod2) {
                            ShipAPI.HullSize size = ship.getHullSpec().getHullSize();
                            return mod1.getCostFor(size) - mod2.getCostFor(size);
                        }
                    });

                    for (int i = 0; i < sMods && i < mods.size(); i++) {
                        ship.getVariant().removeMod(mods.get(i).getId());
                        ship.getVariant().addPermaMod(mods.get(i).getId(), true);
                        //sModsToReAdd.add(mods.get(i).getId());
                    }
                }

                if (dMods > 0) DModManager.addDMods(ship, true, dMods, random);

                inflater.inflate(temp);

                //for (int i = 0; i < sModsToReAdd.size(); i++) ship.getVariant().addPermaMod(sModsToReAdd.get(i), true);

                crewChange = (int)(ship.getMinCrew() * 1.05f);
                creditChange = (int)(-crewChange * baseCrewCost
                        - ship.getBaseBuyValue() * (0.8f + random.nextFloat() * 0.6f)
                        - 1500 * captain.getStats().getLevel());

                setShip(ship);

                return true;
            }
            case CREW_JOIN_OFFER: {
                for (FleetMemberAPI ship : playerFleet.getFleetData().getMembersListCopy()) {
                    if(ship != null
                            && Util.isShipCrewed(ship)
                            && RepRecord.isShipNotable(ship)
                            && ship.getCaptain() != null
                            && !ship.getCaptain().isDefault()) {

                        LoyaltyLevel ll = RepRecord.get(ship).getLoyalty(ship.getCaptain());
                        float significance = LoyaltyLevel.values().length - ll.getIndex();

                        if(significance > 0 && !ll.isAtBest()) picker.add(ship, significance);
                    }
                }

                while (!picker.isEmpty()) {
                    setShip(picker.pickAndRemove());

                    break;
                }

                crewChange = 5 + random.nextInt(3) * 5;
                creditChange = (int)(-crewChange * (0.5f + 0.5f * random.nextFloat()) * baseCrewCost);

                return ship != null;
            }
        }

        return false;
    }
    void addStandardOptions() {
        MutableValue purse = Global.getSector().getPlayerFleet().getCargo().getCredits();
        String acceptText = "Agree to the offer";

        switch (subEvent) {
            case BUY_SHIP_OFFER: {
                acceptText = ("Agree to sell the " + ship.getShipName());
                break;
            }
            case SHIP_JOIN_OFFER: {
                acceptText = ("Hire " + person.getNameString() + ", along with " + getHisOrHer() + " ship and crew");
                break;
            }
            case CREW_JOIN_OFFER: {
                acceptText = "Hire them";
                break;
            }
        }

        if(subEvent == OptionId.SHIP_JOIN_OFFER && !captainShown) {
            options.addOption("Ask " + captain.getNameString() + " about " + getHimOrHerself(), OptionId.SHOW_CAPTAIN);
        }
        if(subEvent != OptionId.CREW_JOIN_OFFER) addShowShipOptionIfNotAlreadyShown(OptionId.SHOW_SHIP);
        options.addOption(acceptText, OptionId.ACCEPT);
        options.addOption("Thank " + getHimOrHer() + " for " + getHisOrHer() + " enthusiasm, but decline the offer", OptionId.LEAVE);
        options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

        if(purse.get() < -creditChange) {
            options.setEnabled(OptionId.ACCEPT, false);
            options.setTooltip(OptionId.ACCEPT, "You don't have enough credits");
        } else if(subEvent == OptionId.SHIP_JOIN_OFFER
                && playerFleet.getFleetData().getOfficersCopy().size() >= Global.getSector().getPlayerStats().getOfficerNumber().getModifiedInt()) {
            options.setEnabled(OptionId.ACCEPT, false);
            options.setTooltip(OptionId.ACCEPT, "Your roster of officers is already full");
        } else if(creditChange != 0) {
            options.setTooltip(OptionId.ACCEPT, "Agreeing to the offer will cost " + Misc.getDGSCredits(-creditChange));
        }
    }
    void reset() {
        super.reset();

        captainShown = false;
        creditChange = 0;
        marketWhereShipIsStored = null;
    }

    @Override
    public boolean shouldShowAtMarket(MarketAPI market) {
        try {
            Random rand = new Random(seed + market.getId().hashCode());

            return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES ? false : super.shouldShowAtMarket(market)
                    && (market.getFaction().getRelToPlayer().isAtWorst(RepLevel.WELCOMING) || market.getFaction().isPlayerFaction())
                    && Integration.isFamousFlagshipEventAvailableAtMarket(market)
                    && Global.getSector().getPlayerStats().getLevel() > (rand.nextInt(8) + 7)
                    && getChanceOfAnyFanclubEvent() > 0;
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
            return false;
        }
    }

    @Override
    protected void regen(MarketAPI market) {
        playerFleet = Global.getSector().getPlayerFleet();

        if (this.market == market || playerFleet == null) return;

        super.regen(market);
        reset();

//        if (Global.getSettings().isDevMode()) random = new Random();

        WeightedRandomPicker<OptionId> picker = new WeightedRandomPicker(getRandom());
        picker.add(OptionId.BUY_SHIP_OFFER, ModPlugin.BUY_SHIP_OFFER_BAR_EVENT_CHANCE);
        picker.add(OptionId.CREW_JOIN_OFFER, ModPlugin.LOYAL_CREW_JOINS_BAR_EVENT_CHANCE);
        picker.add(OptionId.SHIP_JOIN_OFFER, ModPlugin.JOIN_WITH_SHIP_BAR_EVENT_CHANCE);

        while (!picker.isEmpty()) {
            OptionId pick = picker.pickAndRemove();

            if(tryCreateEvent(pick)) {
                subEvent = pick;
                break;
            }
        }
    }

    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        try {
            super.addPromptAndOption(dialog, memoryMap);

            regen(dialog.getInteractionTarget().getMarket());

            TextPanelAPI text = dialog.getTextPanel();
            text.addPara("A lively group of spacers seems to recognize you, if the pointing and staring is anything"
                    + " to go by.");

            dialog.getOptionPanel().addOption("See what the gawking group of spacers has to say", this);
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        try {
            super.init(dialog, memoryMap);

            boolean shipToSellRemovedFromFleet = !getPlayerFleet().getFleetData().getMembersListCopy().contains(ship)
                    && subEvent == OptionId.BUY_SHIP_OFFER
                    && marketWhereShipIsStored == null;

            done = false;
            captainShown = false;

            if(ship == null || rep == null || shipToSellRemovedFromFleet) subEvent = OptionId.INVALID;

            if(subEvent != OptionId.INVALID) {
                if(captain == null || captain.isDefault() || captain.isPlayer()) {
                    if(ship != null) dialog.getVisualPanel().showFleetMemberInfo(ship);
                } else if(subEvent == OptionId.SHIP_JOIN_OFFER) {
                    person = captain;
                    dialog.getVisualPanel().showPersonInfo(person, true);
                }
            }

            optionSelected(null, subEvent);
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
        }
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        try {
            if (!(optionData instanceof FanclubBarEvent.OptionId)) return;

            ShipHullSpecAPI hull = ship == null ? null : ship.getHullSpec();
            OptionPanelAPI options = dialog.getOptionPanel();
            TextPanelAPI text = dialog.getTextPanel();
            options.clearOptions();

            switch ((OptionId) optionData) {
                case BUY_SHIP_OFFER: {
                    String fleetType = ship.getHullSpec().isCivilianNonCarrier() ? "trade fleet" : "mercenary outfit";
                    String hullName = ship.getHullSpec().getDParentHull() == null
                            ? ship.getHullSpec().getHullName()
                            : ship.getHullSpec().getDParentHull().getHullName();
                    String shipNotInFleetMaybe = marketWhereShipIsStored == null || marketWhereShipIsStored == market ? ""
                            : "When you inform " + getHimOrHer() + " that the " + ship.getShipName() + " is mothballed "
                                + "at " + marketWhereShipIsStored.getName() + ", " + getHeOrShe() + " shrugs. "
                                +"\"No matter. I'd gladly cross half the sector for a ship like that.\" ";

                    if(market.getFaction().getRelationshipLevel(Factions.INDEPENDENT).isAtBest(RepLevel.SUSPICIOUS)) {
                        fleetType  = ship.getHullSpec().isCivilianNonCarrier() ? "trade fleet" : "privateering outfit";
                    }

                    String str = "One of the spacers is somewhat overdressed. When " + getHeOrShe() + " sees you approach, "
                            + getHeOrShe() + " smiles lazily and gestures to an open seat at the table. "
                            + "\"Please forgive the prying eyes of my crew. They tell me you're the owner of a rather "
                            + "noteworthy " + hullName + " class " + ship.getHullSpec().getDesignation().toLowerCase()
                            + " named the " + ship.getShipName() + ". If what my crew told me is true, "
                            + "I might be persuaded to purchase it from you at a premium"
                            + (crewChange < 0 ? ", provided that a sufficient number of its crew remain aboard. " : ". ")
                            + "I run a modest " + fleetType + ", you see, and I've been struggling to expand it. Most "
                            + "ships on the market are so shoddy or broken down that they're more liability than asset.\" "
                            + shipNotInFleetMaybe
                            + "After a short bout of haggling, it becomes clear that " + getHeOrShe() + " would be "
                            + "willing to purchase the " + ship.getShipName()
                            + (crewChange < 0 ? ", along with the contracts for %s members of its crew," : "")
                            + " for %s. You think " + getHeOrShe() + " might be a little optimistic about what the "
                            + "ship is capable of.";
                    String credits = Misc.getDGSCredits(creditChange);

                    text.addPara(str, Misc.getTextColor(), Misc.getHighlightColor(),
                            crewChange < 0 ? -crewChange + "" : credits, credits);

                    addStandardOptions();
                    break;
                }
                case SHIP_JOIN_OFFER: {
                    person = captain;

                    String str = "A " + getManOrWoman() + " with a dignified bearing stands to greet you as you approach. \""
                            + Global.getSector().getPlayerPerson().getNameString() + "? Thought so. I've heard you're "
                            + "not a bad person to work for. I command " + Misc.getAOrAnFor(hull.getHullName()) + " "
                            + hull.getHullName()
                            + " class " + hull.getDesignation() + ". It's a fine ship, I'm a capable "
                            + "captain, and my crew is loyal. We've been looking for work ever since our old fleet "
                            + "disbanded. We're asking %s. I'll send the details if you're interested.";

                    text.addPara(str, Misc.getTextColor(), Misc.getHighlightColor(), Misc.getDGSCredits(-creditChange));



                    addStandardOptions();
                    break;
                }
                case CREW_JOIN_OFFER: {
                    String str = "The ragtag group of spacers murmurs excitedly when you approach. One of them stands "
                            + "to greet you. \"I've heard of you. I was just telling my mates how you've been stirring "
                            + "things up in the sector. Some of us would like to be part of that. Are you looking "
                            + "for crew? We'll waive our sign-on fees.\" Several of the other spacers shout objections, "
                            + " and their de facto representative raises " + getHisOrHer() + " hands in apology. "
                            + "\"Some of us will waive our sign-on fees.\"";

                    text.addPara(str, Misc.getTextColor(), Misc.getHighlightColor());
                    text.addPara("All told, %s spacers are willing to join your fleet for a total of %s.",
                            Misc.getGrayColor(), Misc.getHighlightColor(), crewChange + "", Misc.getDGSCredits(-creditChange));

                    addStandardOptions();
                    break;
                }
                case SHOW_CAPTAIN: {
                    text.addPara("The captain answers your questions in a manner that is both thorough and succinct. "
                            + "It doesn't take long to grasp the extent of " + getHisOrHer() + " capabilities.");
                    text.addSkillPanel(captain, false);

                    String personality = Misc.lcFirst(captain.getPersonalityAPI().getDisplayName());
                    text.addParagraph("Personality: " + personality + ", level: " + captain.getStats().getLevel());
                    text.highlightInLastPara(Misc.getHighlightColor(), personality, "" + captain.getStats().getLevel());
                    text.addParagraph(captain.getPersonalityAPI().getDescription());
                    captainShown = true;

                    addStandardOptions();
                    break;
                }
                case SHOW_SHIP: {
                    showShip();
                    addStandardOptions();
                    break;
                }
                case INVALID: {
                    text.addPara("You talk with the group of spacers for a while, but nothing significant comes of it.");
                    options.addOption("Continue", OptionId.LEAVE);
                    options.setShortcut(OptionId.LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
                    BarEventManager.getInstance().notifyWasInteractedWith(this);
                    break;
                }
                case ACCEPT: {
                    if(subEvent == OptionId.CREW_JOIN_OFFER && ship != null) {
                        text.addPara("The spacers celebrate with a bawdy clash of toasts and shouts when you tell "
                                        + "them you could use a few more deckhands, and their spokesperson reassures "
                                        + "you that you won't regret hiring them. When it becomes clear that they plan "
                                        + "to celebrate for several more hours, you excuse yourself, explaining that "
                                        + "you need to make arrangements for their onboarding with your quartermaster",
                                Misc.getTextColor());

                        if(ModPlugin.ENABLE_OFFICER_LOYALTY_SYSTEM
                                && getPlayerFleet().getFleetData().getMembersListCopy().contains(ship)
                                && ship != null && ship.getCaptain() != null && !ship.getCaptain().isDefault()) {

                            rep.adjustLoyaltyXp(100000, captain);
                            text.addPara("The spacers have been assigned to the " + ship.getShipName()
                                            + ", bringing the crew closer to becoming "
                                            + rep.getLoyalty(captain).getOneBetter().getName().toLowerCase() + ".",
                                    Misc.getGrayColor());
                        }
                    } else if(subEvent == OptionId.BUY_SHIP_OFFER) {
                        text.addPara("The overdressed fleet commander smiles and raises " + getHisOrHer()
                                        + " glass with a flourish. \"To our shared prosperity!\" " + getHeOrShe()
                                        + " exclaims. " + Misc.ucFirst(getHeOrShe()) + " eagerly relates a story "
                                        + getHeOrShe() + " heard about the " + ship.getShipName() + ", seeming to want "
                                        + "to hear your version of events, but you call over your second in command to "
                                        + "iron out the details of the transfer and explain that you are, "
                                        + "unfortunately, quite busy.",
                                Misc.getTextColor());
                        
                        if(marketWhereShipIsStored == null) {
                            playerFleet.getFleetData().removeFleetMember(ship);
                            text.addPara("The " + ship.getShipName() + " is no longer part of your fleet.",
                                    Misc.getGrayColor());
                        } else {
                            marketWhereShipIsStored.getSubmarket("storage").getCargo().getMothballedShips().removeFleetMember(ship);
                            text.addPara("The " + ship.getShipName() + " has been removed from your storage at "
                                    + marketWhereShipIsStored.getName(), Misc.getGrayColor());
                        }
                    } else if(subEvent == OptionId.SHIP_JOIN_OFFER) {
                        playerFleet.getFleetData().addFleetMember(ship);
                        playerFleet.getFleetData().addOfficer(captain);
                        text.addPara("After the initial arrangements have been made, your new captain nods sharply and"
                                        + " shakes your hand. \"I'm confident that the " + ship.getShipName()
                                        + " will be an asset to your fleet. Now, if you'll excuse me, I have much"
                                        + " preparation to do.\"",
                                Misc.getTextColor());
                        text.addPara(captain.getNameString() + " and " + getHisOrHer()
                                + " ship have joined your fleet.", Misc.getGrayColor());
                        ship.getRepairTracker().setCR(0.7f);
                        RepRecord.updateRepHullMod(ship);
                        RepRecord.setShipOrigin(ship, RepRecord.Origin.Type.Recruitment, market.getName());
                    }

                    if (creditChange != 0) {
                        MutableValue purse = Global.getSector().getPlayerFleet().getCargo().getCredits();

                        if(creditChange > 0) {
                            purse.add(creditChange);
                            AddRemoveCommodity.addCreditsGainText((int)creditChange, text);
                        } else {
                            purse.subtract(-creditChange);
                            AddRemoveCommodity.addCreditsLossText((int)-creditChange, text);
                        }
                    }

                    if(crewChange != 0) {
                        CargoAPI cargo = Global.getSector().getPlayerFleet().getCargo();

                        if(crewChange > 0) {
                            cargo.addCrew(crewChange);
                            AddRemoveCommodity.addCommodityGainText(Commodities.CREW, crewChange, text);
                        } else {
                            cargo.removeCrew(-crewChange);
                            AddRemoveCommodity.addCommodityLossText(Commodities.CREW, -crewChange, text);
                        }
                    }

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
}
