package starship_legends.commands;

import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import org.lazywizard.console.commands.List_;
import starship_legends.ModPlugin;
import starship_legends.Trait;
import starship_legends.Util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ListTraits extends List_ {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return CommandResult.WRONG_CONTEXT;
            }

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;

            if (args.isEmpty()) return CommandResult.BAD_SYNTAX;

            String arg1 = args.toLowerCase().split(" ")[0];

            if(!arg1.equals("traits")) return super.runCommand(args, context);

            args = args.replace(arg1, "").trim();

            List<String> traits = new LinkedList<>();

            for(Trait trait : Util.getTraitsMatchingDescription(args)) traits.add(trait.getName(true));

            Collections.sort(traits);

            String traitList = "";

            for(int i = 0; i < traits.size(); ++i) traitList += traits.get(i) + (i == traits.size() - 1 ? "" : ", ");

            Console.showMessage(traitList);
        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
