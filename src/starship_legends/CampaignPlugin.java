package starship_legends;

import com.fs.starfarer.api.PluginPick;
import com.fs.starfarer.api.campaign.BaseCampaignPlugin;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.InteractionDialogPlugin;
import com.fs.starfarer.api.campaign.SectorEntityToken;

public class CampaignPlugin extends BaseCampaignPlugin {

    @Override
    public String getId()
    {
        return "StarshipLegendsCampaignPlugin";
    }

    @Override
    public boolean isTransient() { return true; }

    @Override
    public PluginPick<InteractionDialogPlugin> pickInteractionDialogPlugin(SectorEntityToken interactionTarget) {
        try {
            if (interactionTarget instanceof CampaignFleetAPI) CampaignScript.collectRealSnapshotInfo();
        } catch (Exception e) { ModPlugin.reportCrash(e); }

        return null;
    }
}
