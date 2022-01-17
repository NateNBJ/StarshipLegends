package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import org.lwjgl.input.Keyboard;
import starship_legends.RepRecord;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SalvageFamousDerelictDialogPlugin implements InteractionDialogPlugin {
    public static final String RECOVER = "recover";
    public static final String LEAVE = "not_now";
    public static final String RECOVERY_FINISHED = "finished";

    InteractionDialogAPI dialog;
    FamousDerelictIntel intel;

    final SectorEntityToken derelict;
    final Random random;
    final CampaignFleetAPI playerFleet;
    final List shipList = new LinkedList();

    public SalvageFamousDerelictDialogPlugin(SectorEntityToken derelict) {
        this.derelict = derelict;
        this.random = new Random(derelict.toString().hashCode());
        this.playerFleet = Global.getSector().getPlayerFleet();
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        this.dialog = dialog;

        intel = (FamousDerelictIntel)derelict.getMemory().get(FamousDerelictIntel.MEMORY_KEY);

        intel.showRecoveryDescription(dialog.getTextPanel());

        shipList.add(intel.ship);

        dialog.getVisualPanel().showFleetMemberInfo(intel.ship, true);

        dialog.getOptionPanel().clearOptions();
        dialog.getOptionPanel().addOption("Consider ship recovery", RECOVER);
        dialog.getOptionPanel().addOption("Not now", LEAVE);
        dialog.getOptionPanel().setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);
    }

    @Override
    public void optionSelected(String optionText, Object optionData) {
        switch ((String)optionData) {
            case RECOVER:
                final CampaignFleetAPI playerFleet = Global.getSector().getPlayerFleet();
                dialog.showFleetMemberRecoveryDialog("Select ships to recover", shipList, new FleetMemberPickerListener() {
                    public void pickedFleetMembers(List<FleetMemberAPI> selected) {
                        if (selected.isEmpty()) return;

                        for (FleetMemberAPI member : selected) {
                            int index = shipList.indexOf(member);
                            if (index >= 0) {
                                //ShipRecoverySpecial.PerShipData shipData = data.ships.get(index);
                                //data.ships.remove(index);
                                shipList.remove(index);

                                float minHull = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_HULL_MIN, 0f);
                                float maxHull = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_HULL_MAX, 0f);
                                float minCR = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_CR_MIN, 0f);
                                float maxCR = playerFleet.getStats().getDynamic().getValue(Stats.RECOVERED_CR_MAX, 0f);

                                float hull = (float) Math.random() * (maxHull - minHull) + minHull;
                                hull = Math.max(hull, member.getStatus().getHullFraction());
                                member.getStatus().setHullFraction(hull);

                                float cr = (float) Math.random() * (maxCR - minCR) + minCR;
                                member.getRepairTracker().setCR(cr);

                                playerFleet.getFleetData().addFleetMember(member);
                            }

                            dialog.getPlugin().optionSelected(null, RECOVERY_FINISHED);
                        }
                    }
                    public void cancelledFleetMemberPicking() { }
                });
                break;
            case RECOVERY_FINISHED:
                intel.notifyThatPlayerRecoveredDerelict();

                derelict.getContainingLocation().removeEntity(derelict);

                dialog.getTextPanel().addPara("The " + intel.ship.getShipName() + " is now part of your fleet.");

                dialog.getOptionPanel().clearOptions();
                dialog.getOptionPanel().addOption("Leave", LEAVE);
                dialog.getOptionPanel().setShortcut(LEAVE, Keyboard.KEY_ESCAPE, false, false, false, true);

                RepRecord.Origin.Type origin = intel.timeScale == FamousDerelictIntel.TimeScale.Centuries
                        ? RepRecord.Origin.Type.AncientDerelict
                        : RepRecord.Origin.Type.FamousDerelict;

                RepRecord.setShipOrigin(intel.ship,  origin);

                break;
            case LEAVE:
                dialog.dismiss();
                break;
        }
    }

    @Override
    public void optionMousedOver(String optionText, Object optionData) {

    }

    @Override
    public void advance(float amount) {

    }

    @Override
    public void backFromEngagement(EngagementResultAPI battleResult) {

    }

    @Override
    public Object getContext() {
        return null;
    }

    @Override
    public Map<String, MemoryAPI> getMemoryMap() {
        return null;
    }
}