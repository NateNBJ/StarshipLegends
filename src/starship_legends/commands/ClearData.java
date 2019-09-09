package starship_legends.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.campaign.comm.IntelManagerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.*;
import starship_legends.hullmods.Reputation;

import java.util.ArrayList;
import java.util.List;

public class ClearData implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return CommandResult.WRONG_CONTEXT;
            }

            IntelManagerAPI intel = Global.getSector().getIntelManager();

            List<IntelInfoPlugin> battleReports = new ArrayList<>(intel.getIntel(BattleReport.class));
            for(IntelInfoPlugin br : battleReports) {
                intel.removeIntel(br);
            }

            Util.removeRepHullmodFromAutoFitGoalVariants();

            for(FleetMemberAPI ship : Reputation.getShipsOfNote()) {
                ShipVariantAPI v = ship.getVariant();
                v.removePermaMod("sun_sl_notable");
                v.removePermaMod("sun_sl_wellknown");
                v.removePermaMod("sun_sl_famous");
                v.removePermaMod("sun_sl_legendary");

                List<String> slots = v.getModuleSlots();

                for(int i = 0; i < slots.size(); ++i) {
                    ShipVariantAPI module = v.getModuleVariant(slots.get(i));
                    module.removePermaMod("sun_sl_notable");
                    module.removePermaMod("sun_sl_wellknown");
                    module.removePermaMod("sun_sl_famous");
                    module.removePermaMod("sun_sl_legendary");
                }
            }

            Saved.deletePersistantData();

            Console.showMessage("All saved data from Starship Legends has been deleted. Save your game and close Starsector to continue.");

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
