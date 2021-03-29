package starship_legends.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.BaseCommandPlugin;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.SalvageDefenderInteraction;
import com.fs.starfarer.api.util.Misc;
//import ruthless_sector.campaign.rulecmd.RS_SalvageDefenderInteraction;
import starship_legends.FactionConfig;
import starship_legends.ModPlugin;

import java.util.List;
import java.util.Map;

public class SL_SalvageDefenderInteraction extends BaseCommandPlugin {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        try {
            BaseCommandPlugin si = new SalvageDefenderInteraction();

            // TODO figure out what's going wrong with the RS_SalvageDefenderInteraction import...
//            if(Global.getSettings().getModManager().isModEnabled("sun_ruthless_sector")) {
//                try {
//                    si = new RS_SalvageDefenderInteraction();
//                } catch (Exception e) {
//                    ModPlugin.reportCrash(e, false);
//                }
//            }

            if(si.execute(ruleId, dialog, params, memoryMap)) {
                if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return true;

                final MemoryAPI memory = getEntityMemory(memoryMap);
                final CampaignFleetAPI defenders = memory.getFleet("$defenderFleet");

                FactionConfig.get(defenders.getFaction()).showFleetReputation(dialog, null);

                return true;
            } else return false;
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
            return false;
        }
    }
}
