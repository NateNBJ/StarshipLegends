
package starship_legends.commands;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.rpg.Person;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.LoyaltyLevel;
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
                PersonAPI cap = ship.getCaptain();

                if(cap == null || cap.isDefault()) {
                    Console.showMessage("The " + ship.getShipName() + " has no captain assigned");
                } else {
                    RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);

                    rep.setOpinionOfOfficer(cap, newLoyalty);

                    LoyaltyLevel ll = rep.getLoyaltyLevel(cap);

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
