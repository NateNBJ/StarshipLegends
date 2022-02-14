package starship_legends.commands;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.hullmods.Reputation;

public class PrintData implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }

//            Global.getLogger(ModPlugin.class).info(String.format("%20s strength: %3.1f = %3.1f * %.2f * %.2f * %.2f * %.2f",
//                    ship.getHullId(), strength, fp * (1 + (fp - 5f) / 25f), dModMult, sModMult, skillMult, playerStrengthMult));

            Reputation.printRegistry();
            //RepRecord.printRegistry();
        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
