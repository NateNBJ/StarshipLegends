
package starship_legends.commands;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.LoyaltyLevel;
import starship_legends.ModPlugin;
import starship_legends.RepRecord;
import starship_legends.Util;

public class SetLoyalty implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;

            if(args.isEmpty()) return CommandResult.BAD_SYNTAX;

            String arg1 = args.toLowerCase().split(" ")[0];
            args = args.replace(arg1, "").trim();

            int newLoyalty;

            try {
                newLoyalty = Integer.parseInt(arg1);
            } catch (Exception e) {
                return CommandResult.BAD_SYNTAX;
            }

            for(FleetMemberAPI ship : Util.getShipsMatchingDescription(args)) {
                PersonAPI cap = Util.getCaptain(ship);

                if(cap == null || cap.isDefault()) {
                    Console.showMessage("The " + ship.getShipName() + " has no captain assigned");
                } else {
                    RepRecord rep = RepRecord.getOrCreate(ship);

                    rep.setLoyalty(cap, newLoyalty);

                    LoyaltyLevel ll = rep.getLoyalty(cap);

                    Console.showMessage("The crew of the " + ship.getShipName() + " is now " + ll.getName().toUpperCase() + " "
                        + ll.getPreposition() + " " + cap.getNameString().trim());
                }
            }

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
