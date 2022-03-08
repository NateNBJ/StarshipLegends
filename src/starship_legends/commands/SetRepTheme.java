
package starship_legends.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;
import starship_legends.ModPlugin;
import starship_legends.RepRecord;
import starship_legends.RepTheme;
import starship_legends.Util;

public class SetRepTheme implements BaseCommand {
    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        try {
            if (!context.isInCampaign()) {
                Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
                return CommandResult.WRONG_CONTEXT;
            }

            if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return  CommandResult.WRONG_CONTEXT;

            if(args.isEmpty()) return CommandResult.BAD_SYNTAX;

            String arg1 = args.toLowerCase().split(" ")[0];
            args = args.replace(arg1, "").trim();

            RepTheme newTheme = RepTheme.get(arg1);
            FactionAPI themeFaction = null;

            if(newTheme == null) {
                try { themeFaction = Global.getSector().getFaction(arg1); }
                catch (Exception e) { return CommandResult.BAD_SYNTAX; }
            }

            for(FleetMemberAPI ship : Util.getShipsMatchingDescription(args)) {
                RepRecord rep = RepRecord.getOrCreate(ship);

                if(newTheme != null) {
                    rep.setTheme(newTheme);
                    Console.showMessage("The " + ship.getShipName() + " now has the \"" +newTheme.getKey() + "\" theme.");
                } else if(themeFaction != null) {
                    rep.setFactionTheme(themeFaction);
                    Console.showMessage("The " + ship.getShipName() + " now has the theme of "
                            + themeFaction.getDisplayNameWithArticle());
                } else return CommandResult.BAD_SYNTAX;
            }

        } catch (Exception e) {
            Console.showException("Error: unhandled exception!", e);
            return CommandResult.ERROR;
        }


        return CommandResult.SUCCESS;
    }
}
