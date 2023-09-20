package starship_legends.commands;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.ModPlugin;
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

            args = args.toLowerCase();

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;

            boolean requireRelevance = args.contains("relevant");
            boolean predetermined = args.contains("predetermined") || args.contains("deterministic");

            args = args.replace("deterministic", "").replace("relevant", "").replace("predetermined", "");

            String aa[] = args.split("to ");
            List<FleetMemberAPI> ships = Util.getShipsMatchingDescription(aa.length > 1 ? aa[1] : "");

            if(ships.isEmpty()) return CommandResult.BAD_SYNTAX;

            if(predetermined) {
                for (FleetMemberAPI ship : ships) {
                    RepRecord rep = RepRecord.getOrCreate(ship);
                    List<Trait> destinedTraits = RepRecord.getDestinedTraitsForShip(ship, true);

                    if(destinedTraits.size() <= rep.getTraits().size()) {
                        Console.showMessage("The " + ship.getShipName() + " already has the maximum number of traits.");
                        continue;
                    }

                    Trait newTrait = destinedTraits.get(rep.getTraits().size());

                    addTraitToShip(ship, rep, newTrait);
                }
            } else {
                List<Trait> traits = Util.getTraitsMatchingDescription(aa.length > 0 ? aa[0] : "", ", relevant, deterministic");
                Random rand = new Random();

                if (traits.isEmpty()) return CommandResult.BAD_SYNTAX;

                for (FleetMemberAPI ship : ships) {
                    RepRecord rep = RepRecord.getOrCreate(ship);
                    List<Trait> traitsCopy = new LinkedList<>(traits);

                    while (!traitsCopy.isEmpty()) {

                        int i = rand.nextInt(traitsCopy.size());
                        Trait newTrait = traitsCopy.get(i);

                        traitsCopy.remove(i);

                        if (rep.hasTraitType(newTrait.getType()) || (requireRelevance && !newTrait.isRelevantFor(ship))) {
                            if (traitsCopy.isEmpty()) {
                                Console.showMessage("The " + ship.getShipName() + " already has all of the selected traits.");
                                break;
                            } else continue;
                        }

                        addTraitToShip(ship, rep, newTrait);

                        break;
                    }
                }
            }
        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }

        return CommandResult.SUCCESS;
    }

    private boolean addTraitToShip(FleetMemberAPI ship, RepRecord rep, Trait newTrait) {
        if (rep.hasMaximumTraits()) {
            Console.showMessage("The " + ship.getShipName() + " already has the maximum number of traits.");
            return false;
        } else {
            rep.getTraits().add(newTrait);
            RepRecord.updateRepHullMod(ship);

            Console.showMessage("The " + ship.getShipName() + " is now known for "
                    + newTrait.getDescPrefix(true, false).toLowerCase() + " " + newTrait.getName(true, false).toUpperCase());
            return true;
        }
    }
}
