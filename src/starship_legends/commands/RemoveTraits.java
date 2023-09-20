package starship_legends.commands;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.ModPlugin;
import starship_legends.RepRecord;
import starship_legends.Trait;
import starship_legends.Util;

import java.util.LinkedList;
import java.util.List;

public class RemoveTraits implements BaseCommand {
    private void removeTrait(FleetMemberAPI ship, RepRecord rep, Trait trait) {
        String message = BaseIntelPlugin.BULLET + "The " + ship.getShipName();

        if(rep.getTraits().contains(trait)) {
            rep.getTraits().remove(trait);
            message += " is no longer known for ";
        } else {
            message += " is not known for ";
        }

        message += trait.getDescPrefix(Util.isShipCrewed(ship), Util.isShipBiological(ship)).toLowerCase() + " %s.";

        Console.showMessage(String.format(message, trait.getName(Util.isShipCrewed(ship), Util.isShipBiological(ship)).toUpperCase()));
    }

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return CommandResult.WRONG_CONTEXT;
            }

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;

            boolean removeIrrelevant = args.toLowerCase().contains("irrelevant");

            if(removeIrrelevant) args = args.replace("irrelevant", "");

            String aa[] = args.split("from ");
            List<Trait> traits = Util.getTraitsMatchingDescription(aa.length > 0 ? aa[0] : "", ", irrelevant");
            List<FleetMemberAPI> ships = Util.getShipsMatchingDescription(aa.length > 1 ? aa[1] : "");

            if(removeIrrelevant && aa[0].toLowerCase().trim().isEmpty()) traits.clear();

            if((!removeIrrelevant && (traits == null || traits.isEmpty())) || ships == null ||  ships.isEmpty()) return CommandResult.BAD_SYNTAX;

            for(FleetMemberAPI ship : ships) {
                if(RepRecord.isShipNotable(ship)) {
                    RepRecord rep = RepRecord.get(ship);

                    for (Trait t : traits) {
                        if (rep.hasTrait(t)) {
                            removeTrait(ship, rep, t);
                        }
                    }

                    if(removeIrrelevant) {
                        for (Trait t : new LinkedList<>(rep.getTraits())) {
                            if (!t.isRelevantFor(ship)) {
                                removeTrait(ship, rep, t);
                            }
                        }
                    }

                    RepRecord.updateRepHullMod(ship);
                }
            }

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }

        return CommandResult.SUCCESS;
    }
}
