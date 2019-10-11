package starship_legends.commands;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.ModPlugin;
import starship_legends.RepRecord;
import starship_legends.Util;

import java.util.List;

public class ClearRep implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }
            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;

            List<FleetMemberAPI> matches = Util.getShipsMatchingDescription(args);

            if(matches.isEmpty()) {
                return CommandResult.BAD_SYNTAX;
            }

            for(FleetMemberAPI ship : matches) {
                if(RepRecord.existsFor(ship)) {
                    RepRecord.deleteFor(ship);
                    Console.showMessage("Reputation cleared from the " + ship.getShipName());
                }
            }

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
