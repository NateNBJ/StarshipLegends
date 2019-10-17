package starship_legends.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.util.Misc;
import starship_legends.ModPlugin;
import starship_legends.events.SalvageFamousDerelictDialogPlugin;

import java.util.List;
import java.util.Map;

public class SL_ShowFamousDerelict extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {

        try {
            //dialog.getTextPanel().addPara("triggered");

            //Global.getLogger(this.getClass()).info("And again");

            //dialog.getInteractionTarget().getMemoryWithoutUpdate().set("$sun_sl_customType", "famousFoundWreck");
            //dialog.getVisualPanel().showFleetMemberInfo(Global.getSector().getPlayerFleet().getFlagship(), true);


            dialog.setPlugin(new SalvageFamousDerelictDialogPlugin(dialog.getInteractionTarget()));
            dialog.getPlugin().init(dialog);


            //new SalvageFamousDerelictDialogPlugin(derelict).init(dialog);


            return true;
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
            return false;
        }
    }
}
