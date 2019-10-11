package starship_legends.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.util.Misc;
import starship_legends.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SL_ShowMarketDefenses extends MarketCMD {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if(!super.execute(ruleId, dialog, params, memoryMap)) return false;

        if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return true;

        try {
            FactionConfig.clearEnemyFleetRep();
            //CampaignFleetAPI primary = getInteractionTargetForFIDPI();

            PersonAPI commander = guessCommander();

            if (commander == null || commander.isDefault()) return false;

            FactionConfig.get(commander.getFaction()).showFleetReputation(dialog, commander);

            return true;
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
            return false;
        }
    }

    protected PersonAPI guessCommander() {
        CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
        CampaignFleetAPI actualOther = getInteractionTargetForFIDPI();
        List<CampaignFleetAPI> pulledIn = new ArrayList();
        BattleAPI b = Global.getFactory().createBattle(pf, actualOther);

        for (CampaignFleetAPI fleet : pf.getContainingLocation().getFleets()) {
            if (b == fleet.getBattle()) continue;
            if (fleet.getBattle() != null) continue;

            if (fleet.isStationMode()) continue;

            float dist = Misc.getDistance(actualOther.getLocation(), fleet.getLocation());
            dist -= actualOther.getRadius();
            dist -= fleet.getRadius();

            if (fleet.getFleetData().getNumMembers() <= 0) continue;

            float baseSensorRange = pf.getBaseSensorRangeToDetect(fleet.getSensorProfile());
            boolean visible = fleet.isVisibleToPlayerFleet();
            SectorEntityToken.VisibilityLevel level = fleet.getVisibilityLevelToPlayerFleet();

            float joinRange = Misc.getBattleJoinRange();
            if (fleet.getFaction().isPlayerFaction() && !fleet.isStationMode()) {
                joinRange += Global.getSettings().getFloat("battleJoinRangePlayerFactionBonus");
            }
            if (dist < joinRange &&
                    (dist < baseSensorRange || (visible && level != SectorEntityToken.VisibilityLevel.SENSOR_CONTACT)) &&
                    ((fleet.getAI() != null && fleet.getAI().wantsToJoin(b, true)) || fleet.isStationMode())) {

                pulledIn.add(fleet);
            }
        }

        b.leave(actualOther, false);
        b.finish(BattleAPI.BattleSide.NO_JOIN, false);

        //pulledIn.add(actualOther);

        return Util.getHighestLevelEnemyCommanderInBattle(pulledIn);
    }
}
