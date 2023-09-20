package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventWithPerson;
import com.fs.starfarer.api.util.Misc;
import de.unkrig.commons.nullanalysis.NotNull;
import starship_legends.LoyaltyLevel;
import starship_legends.RepRecord;
import starship_legends.Util;

import java.util.Map;

public class BaseShipBarEvent extends BaseBarEventWithPerson {
    CampaignFleetAPI playerFleet = null;
    boolean requiresCrew = false, shipShown = false, biological = false;

    FleetMemberAPI ship = null;
    PersonAPI captain = null;
    RepRecord rep = null;

    protected CampaignFleetAPI getPlayerFleet() {
        if(playerFleet == null) playerFleet = Global.getSector().getPlayerFleet();

        return playerFleet;
    }

    void setShip(@NotNull FleetMemberAPI ship) {
        this.ship = ship;
        this.rep = RepRecord.get(ship);
        this.captain = ship.getCaptain();
        this.requiresCrew = Util.isShipCrewed(ship);
        this.biological = Util.isShipBiological(ship);
    }
    void showShip() {
        //Global.getSector().getCampaignUI().showCoreUITab(CoreUITabId.REFIT, ship);

        dialog.getVisualPanel().showFleetMemberInfo(ship);

        text.addPara("The " + ship.getShipName() + " is known for the following traits:");
        Util.showTraits(text, rep, captain, requiresCrew, biological, ship.getHullSpec().getHullSize());

        if(captain != null && !captain.isDefault() && rep != null) {
            LoyaltyLevel ll = rep.getLoyalty(captain);
            String desc = "The crew of the " + ship.getShipName() + " is %s " + ll.getPreposition() + " "
                    + captain.getNameString().trim() + ".";

            text.addPara(desc, Misc.getTextColor(), Misc.getHighlightColor(), ll.getName());
        }


        shipShown = true;
    }
    void addShowShipOptionIfNotAlreadyShown(Object optionId) {
        if(ship != null && !shipShown) {
            options.addOption("View the specifications of the " + ship.getShipName(), optionId);
        }
    }
    void reset() {
        ship = null;
        captain = null;
        rep = null;
        requiresCrew = shipShown = false;
    }

    @Override
    public void init(InteractionDialogAPI dialog, Map<String, MemoryAPI> memoryMap) {
        super.init(dialog, memoryMap);

        shipShown = false;
    }
}
