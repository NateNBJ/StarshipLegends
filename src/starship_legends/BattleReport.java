package starship_legends;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.util.Set;

public class BattleReport extends BaseIntelPlugin {
    SectorEntityToken debrisCloud; // TODO
    FactionAPI enemyFaction;
    int battleDifficulty = 0;

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        String title = enemyFaction == null
                ? "Battle report"
                : "Report for battle against %s";

        info.addPara(title, 0f, Misc.getTextColor(), getFactionForUIColors().getColor(),
                enemyFaction == null ? "" : enemyFaction.getDisplayNameLongWithArticle());
    }

    @Override
    public boolean hasSmallDescription() {
        return true;
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        info.addPara("Battle Report small description", Misc.getHighlightColor(), 0f);

    }

    @Override
    public boolean hasLargeDescription() {
        return true;
    }

    @Override
    public void createLargeDescription(CustomPanelAPI panel, float width, float height) {
        TooltipMakerAPI desc = panel.createUIElement(width, height, true);
        desc.addPara("Battle Report large description", Misc.getHighlightColor(), 0f);
        panel.addUIElement(desc).inTL(0, 0);
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public String getSortString() {
        return "Battle Report";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return enemyFaction == null ? Global.getSector().getPlayerFaction() : enemyFaction;
    }

    // TODO
    @Override
    public String getIcon() {
        return Global.getSector().getPlayerPerson().getPortraitSprite();
    }

    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_FLEET_LOG);
        if(enemyFaction != null) tags.add(enemyFaction.getId());
        return tags;
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return debrisCloud;
    }
}
