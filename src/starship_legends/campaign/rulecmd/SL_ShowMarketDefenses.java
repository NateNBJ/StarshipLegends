package starship_legends.campaign.rulecmd;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;
import com.fs.starfarer.api.util.Misc;
import starship_legends.*;
import starship_legends.hullmods.Reputation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SL_ShowMarketDefenses extends MarketCMD {
    @Override
    public boolean execute(String ruleId, InteractionDialogAPI dialog, List<Misc.Token> params, Map<String, MemoryAPI> memoryMap) {
        if(Global.getSettings().getModManager().isModEnabled("nexerelin")) {
            if(!(new Nex_MarketCMD(dialog.getInteractionTarget()).execute(ruleId, dialog, params, memoryMap))) return false;
        } else {
            if(!super.execute(ruleId, dialog, params, memoryMap)) return false;
        }

        if (ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return true;

        try {
            FactionConfig.clearEnemyFleetRep();
            List<CampaignFleetAPI> pulledIn = new ArrayList();

            PersonAPI commander = guessCommander(pulledIn);

            if (commander == null || commander.isDefault()) return false;

            FactionConfig.get(commander.getFaction()).showFleetReputation(dialog, commander);

            ArrayList<FleetMemberAPI> allEnemyShips = new ArrayList<>();

            for(CampaignFleetAPI fleet : pulledIn) allEnemyShips.addAll(fleet.getFleetData().getMembersListCopy());

            for(FleetMemberAPI ship : allEnemyShips) ship.getVariant().addPermaMod(Reputation.ENEMY_HULLMOD_ID);

            return true;
        } catch (Exception e) {
            ModPlugin.reportCrash(e);
            return false;
        }
    }

    protected PersonAPI guessCommander(List<CampaignFleetAPI> pulledIn) {
        try {
            CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
            CampaignFleetAPI actualOther = getInteractionTargetForFIDPI();
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
                if (dist < joinRange
                        && !fleet.isPlayerFleet()
                        && (dist < baseSensorRange || (visible && level != SectorEntityToken.VisibilityLevel.SENSOR_CONTACT))
                        && ((fleet.getAI() != null && fleet.getAI().wantsToJoin(b, true)) || fleet.isStationMode())) {

                    pulledIn.add(fleet);
                }
            }

            b.leave(actualOther, false);
            b.finish(BattleAPI.BattleSide.NO_JOIN, false);

            pulledIn.add(actualOther);
        } catch (Exception e) {
            return null;
        }

        return Util.getHighestLevelEnemyCommanderInBattle(pulledIn);
    }
}
