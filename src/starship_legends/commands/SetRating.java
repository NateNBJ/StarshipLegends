
package starship_legends.commands;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.rpg.Person;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.LoyaltyLevel;
import starship_legends.ModPlugin;
import starship_legends.RepRecord;
import starship_legends.Util;

public class SetRating implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return CommandResult.WRONG_CONTEXT;

            if(args.isEmpty()) return CommandResult.BAD_SYNTAX;

            String arg1 = args.toLowerCase().split(" ")[0];
            args = args.replace(arg1, "").trim();

            float newRating;

            try {
                newRating = Float.parseFloat(arg1);
                if(newRating > 1.1f) newRating /= 100f;
            } catch (Exception e) {
                return CommandResult.BAD_SYNTAX;
            }

            for(FleetMemberAPI ship : Util.getShipsMatchingDescription(args)) {
                    RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);

                    rep.setRating(newRating);

                    Console.showMessage("The " + ship.getShipName() + " now has a rating of "
                            + (int)(rep.getRating() * 100) + "%");
            }

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
