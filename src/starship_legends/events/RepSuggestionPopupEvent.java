package starship_legends.events;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import starship_legends.ModPlugin;

import java.awt.*;
import java.util.List;
import java.util.Set;

public class RepSuggestionPopupEvent  extends BaseIntelPlugin {

    public static String BUTTON_ACCEPT = "button_accept";
    public static String BUTTON_DISMISS = "button_delete";
    public static float DURATION = 120f;

    public static RepSuggestionPopupEvent getActiveSuggestion() {
        List<IntelInfoPlugin> intels = Global.getSector().getIntelManager().getIntel(RepSuggestionPopupEvent.class);

        for(IntelInfoPlugin intel : intels) {
            if(!intel.isEnding() && !intel.isEnded()) return (RepSuggestionPopupEvent)intel;
        }

        return null;
    }

    OwnCrewBarEvent barEvent;
    boolean approved = false;

    public RepSuggestionPopupEvent() {
        barEvent = new OwnCrewBarEvent();
        barEvent.prepareForIntel();

        setImportant(true);
    }

    public boolean isValid() { return barEvent != null && barEvent.subEvent != OwnCrewBarEvent.OptionId.INVALID; }
    public boolean shouldRemoveIntel() {
        float days = getDaysSincePlayerVisible();
        return isEnded() || days >= DURATION;
    }
    public String getName() {
        ShipHullSpecAPI hull = barEvent.ship.getHullSpec().getBaseHull() == null
                ? barEvent.ship.getHullSpec()
                : barEvent.ship.getHullSpec().getBaseHull();

        return hull.getHullName() + " Trait Suggestion";
    }
    public String getSmallDescriptionTitle() {
        return getName();
    }
    public PersonAPI getPerson() { return barEvent.getPerson(); }

    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        try {
            Color c = getTitleColor(mode);
            info.addPara(getName(), c, 0f);
            addBulletPoints(info, mode);
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = getBulletColorForMode(mode);

        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = opad;

        bullet(info);
        info.addPara("Current: %s", initPad, tc, h, barEvent.trait.getName(barEvent.requiresCrew));
        info.addPara("Alternate: %s", initPad, tc, h, barEvent.replacementTrait.getName(barEvent.requiresCrew));
        unindent(info);
    }

    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        try {
            Color h = Misc.getHighlightColor();
            Color g = Misc.getGrayColor();
            Color tc = Misc.getTextColor();
            float pad = 3f;
            float opad = 10f;

            barEvent.createSmallDescriptionForIntel(this, info, width, height);

            if(!isEnding()) {
                float days = DURATION - getDaysSincePlayerVisible();
                info.addPara("This opportunity will be available for %s more " + getDaysString(days) + ".",
                        opad, tc, h, getDays(days));

                ButtonAPI button = addGenericButton(info, width, "Approve the suggestion", BUTTON_ACCEPT);
                button.setShortcut(Keyboard.KEY_T, true);

                info.addSpacer(-10f);
                addDeleteButton(info, width, "Disregard");
            }
        } catch (Exception e) { ModPlugin.reportCrash(e); }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        // Prevents confirmation prompt for intel removal
        return false;
    }

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if(buttonId == BUTTON_ACCEPT) {
            setImportant(false);
            barEvent.doApproveActions();
            approved = true;
            endAfterDelay();
            ui.recreateIntelUI();
        } else if (buttonId == BUTTON_DISMISS) {
            setImportant(false);
			//endImmediately();
            endAfterDelay();
			ui.recreateIntelUI();
		}
	}

    @Override
    public String getIcon() {
        return barEvent != null ? barEvent.rep.getTier().getIntelIcon() : super.getIcon();
    }

    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add(Tags.INTEL_FLEET_LOG);
        return tags;
    }

    @Override
    public String getCommMessageSound() {
        return super.getCommMessageSound();
    }
}