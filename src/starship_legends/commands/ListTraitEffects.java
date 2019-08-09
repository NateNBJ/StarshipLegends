package starship_legends.commands;

import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.Trait;
import starship_legends.Util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ListTraitEffects implements BaseCommand {
    @Override
    public BaseCommand.CommandResult runCommand(String args, BaseCommand.CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return BaseCommand.CommandResult.WRONG_CONTEXT;
            }

            List<String> traits = new LinkedList<>();

            for(Trait trait : Util.getTraitsMatchingDescription(args)) {
                traits.add(trait.getName(true)
                        + ": " + trait.getEffectValueString(trait.getType().getBaseBonus() * trait.getEffectSign())
                        + " " + trait.getType().getEffectDescription() + " per level");
            }

            Collections.sort(traits);

            for(String str : traits) {
                Console.showMessage(str);
            }

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return BaseCommand.CommandResult.ERROR;
        }


        return BaseCommand.CommandResult.SUCCESS;
    }
}
