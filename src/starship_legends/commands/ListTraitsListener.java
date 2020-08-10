package starship_legends.commands;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandListener;
import org.lazywizard.console.Console;
import starship_legends.ModPlugin;
import starship_legends.Trait;
import starship_legends.Util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ListTraitsListener implements CommandListener {
    @Override
    public boolean onPreExecute(String command, String args, BaseCommand.CommandContext context, boolean alreadyIntercepted) {
        return !ModPlugin.REMOVE_ALL_DATA_AND_FEATURES
                && context.isInCampaign()
                && command.toLowerCase().equals("list")
                && !args.isEmpty()
                && args.split(" ")[0].toLowerCase().equals("traits");
    }

    @Override
    public BaseCommand.CommandResult execute(String command, String args, BaseCommand.CommandContext context) {
        try {
            String arg1 = args.toLowerCase().split(" ")[0];

            args = args.replace(arg1, "").trim();

            List<String> traits = new LinkedList<>();

            for(Trait trait : Util.getTraitsMatchingDescription(args)) traits.add(trait.getName(true));

            Collections.sort(traits);

            String traitList = "";

            for(int i = 0; i < traits.size(); ++i) traitList += traits.get(i) + (i == traits.size() - 1 ? "" : ", ");

            Console.showMessage(traitList);
        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return BaseCommand.CommandResult.ERROR;
        }

        return BaseCommand.CommandResult.SUCCESS;
    }

    @Override
    public void onPostExecute(String command, String args, BaseCommand.CommandResult result, BaseCommand.CommandContext context, CommandListener interceptedBy) {

    }
}
