package starship_legends.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.*;

import java.util.List;

public class AdjustTraits implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;

            int shuffleSign = Util.getGoodnessSignFromArgs(args);

            args = Util.removeGoodnessKeywordsFromArgs(args);

            List<FleetMemberAPI> matches = Util.getShipsMatchingDescription(args);

            if(matches.isEmpty()) {
                return CommandResult.BAD_SYNTAX;
            }

            for(FleetMemberAPI ship : matches) {
                if(RepRecord.existsFor(ship)) {
                    RepRecord rep = RepRecord.get(ship);
                    int sign = shuffleSign == 0 ? rep.getTraitAdjustSign() : shuffleSign;
                    float prevRatingDiscrepancy = rep.getRatingDiscrepancy();
                    Trait[] traits = rep.chooseTraitsToShuffle(sign);

                    if(traits == null) {
                        Console.showMessage("Could not adjust traits of the " + ship.getShipName());
                        continue;
                    }

                    RepChange rc = new RepChange(ship);

                    rc.setTraitChange(traits, sign);

                    rc.apply(false);

                    //Console.showMessage("Sign: " + sign);

                    if(!rep.hasTrait(traits[0])) {
                        String message = BaseIntelPlugin.BULLET + "The " + ship.getShipName() + " is no longer known for "
                                + traits[0].getDescPrefix(ship.getMinCrew() > 0 || ship.isMothballed()) + " %s.";

                        Console.showMessage(String.format(message, traits[0].getName(true).toUpperCase()));
                    } else {
                        String discrepancyMaybe = Global.getSettings().isDevMode()
                                ? "  (" + (int)(prevRatingDiscrepancy * 100) + "%% -> " + (int)(rep.getRatingDiscrepancy() * 100) + "%%)"
                                : "";

                        Trait tUp = traits[0], tDown = traits[1];

                        String message = "The " + ship.getShipName() + " is now known better for "
                                + tUp.getDescPrefix(true) + " %s than " + tDown.getDescPrefix(true) + " %s"
                                + discrepancyMaybe;

                        Console.showMessage(String.format(message, tUp.getName(true).toUpperCase(),
                                tDown.getName(true).toUpperCase()));
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
