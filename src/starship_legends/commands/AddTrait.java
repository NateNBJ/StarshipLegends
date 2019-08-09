package starship_legends.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.CampaignScript;
import starship_legends.RepRecord;
import starship_legends.Trait;
import starship_legends.Util;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class AddTrait implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }

            String aa[] = args.toLowerCase().split("to ");
            List<Trait> traits = Util.getTraitsMatchingDescription(aa.length > 0 ? aa[0] : "");
            List<FleetMemberAPI> ships = Util.getShipsMatchingDescription(aa.length > 1 ? aa[1] : "");
            Random rand = new Random();

            if(traits.isEmpty() || ships.isEmpty()) return CommandResult.BAD_SYNTAX;

            for(FleetMemberAPI ship : ships) {
                RepRecord rep = RepRecord.existsFor(ship) ? RepRecord.get(ship) : new RepRecord(ship);
                List<Trait> traitsCopy = new LinkedList<>(traits);
                float prevRatingDiscrepancy = rep.getRatingDiscrepancy();

                if(rep.hasMaximumTraits()) {
                    Console.showMessage("The " + ship.getShipName() + " already has the maximum number of traits.");
                    continue;
                }

                while(!traitsCopy.isEmpty()) {
                    int i = rand.nextInt(traitsCopy.size());
                    Trait newTrait = traitsCopy.get(i);

                    traitsCopy.remove(i);

                    if(!rep.hasTrait(newTrait)) {
                        rep.getTraits().add(newTrait);
                        RepRecord.updateRepHullMod(ship);
                        String discrepancyMaybe = Global.getSettings().isDevMode()
                                ? "  (" + (int)(prevRatingDiscrepancy * 100) + "% -> " + (int)(rep.getRatingDiscrepancy() * 100) + "%)"
                                : "";

                        Console.showMessage("The " + ship.getShipName() + " is now known for "
                                + newTrait.getDescPrefix(true) + " " + newTrait.getName(true).toUpperCase()
                                + discrepancyMaybe);
                        break;
                    } else if(traitsCopy.isEmpty()) {
                        Console.showMessage("The " + ship.getShipName() + " already has all of the selected traits.");
                    }
                }
            }

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
