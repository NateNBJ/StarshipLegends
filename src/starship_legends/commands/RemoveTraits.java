package starship_legends.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.*;

import java.util.List;

public class RemoveTraits implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return CommandResult.WRONG_CONTEXT;
            }

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;



            boolean requireRelevance = args.toLowerCase().contains("relevant");

            if(requireRelevance) args = args.replace("relevant", "");

            String aa[] = args.split("from ");
            List<Trait> traits = Util.getTraitsMatchingDescription(aa.length > 0 ? aa[0] : "");
            List<FleetMemberAPI> ships = Util.getShipsMatchingDescription(aa.length > 1 ? aa[1] : "");

            if(traits == null || traits.isEmpty() || ships == null ||  ships.isEmpty()) return CommandResult.BAD_SYNTAX;

            for(FleetMemberAPI ship : ships) {
                if(RepRecord.existsFor(ship)) {
                    RepRecord rep = RepRecord.get(ship);

                    for (Trait t : traits) {
                        if (rep.hasTrait(t)) {
                            rep.getTraits().remove(t);
                            String message = BaseIntelPlugin.BULLET + "The " + ship.getShipName() + " is no longer known for "
                                    + t.getDescPrefix(ship.getMinCrew() > 0 || ship.isMothballed()) + " %s.";

                            Console.showMessage(String.format(message, t.getName(true).toUpperCase()));
                        }
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
