
package starship_legends.commands;

import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.LoyaltyLevel;
import starship_legends.ModPlugin;
import starship_legends.RepRecord;
import starship_legends.Util;

public class SetTraitEffectMult implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if(args.isEmpty() || ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return CommandResult.BAD_SYNTAX;

            try {
                float prevMult = ModPlugin.GLOBAL_EFFECT_MULT;
                ModPlugin.GLOBAL_EFFECT_MULT = Float.parseFloat(args);

                Console.showMessage("Global trait effect multiplier changed from " + prevMult + " to " + ModPlugin.GLOBAL_EFFECT_MULT);
            } catch (Exception e) {
                return CommandResult.BAD_SYNTAX;
            }
        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
