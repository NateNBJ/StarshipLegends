package starship_legends.events;

import java.awt.*;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.PersonBountyIntel;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.ui.*;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.input.Keyboard;
import starship_legends.*;

public class FamousFlagshipIntel extends FleetLogIntel {
	public static final float MAX_DURATION = PersonBountyIntel.MAX_DURATION / 2f;

	FleetMemberAPI ship;
	RepRecord rep;
	FactionAPI faction;
	CampaignFleetAPI fleet;
	PersonAPI commander;
	String location;
	String activity;

	public FamousFlagshipIntel(FamousShipBarEvent event) {
		super(MAX_DURATION * 2f);

		ship = event.ship;
		rep = event.rep;
		faction = event.faction;
		fleet = event.fleet;
		commander = event.commander;
		location = fleet.getContainingLocation().getName();
		activity = event.activity;

		Misc.makeImportant(fleet, "sun_sl_famous_flagship");
		setRemoveTrigger(fleet);
	}

	@Override
	public String getSmallDescriptionTitle() {
		return "Rumors about " + commander.getNameString().trim() + "'s fleet";
	}

	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		info.addPara(getSmallDescriptionTitle(), getTitleColor(mode), 0f);

		float pad = 3f;
		float opad = 10f;
		Color tc = getBulletColorForMode(mode);
		float initPad = (mode == ListInfoMode.IN_DESC) ? opad : pad;


		bullet(info);
		//boolean isUpdate = getListInfoParam() != null; // true if notification?

		info.addPara("Ship Class: %s", initPad, tc, Misc.getHighlightColor(), ship.getHullSpec().getHullName());
		info.addPara("Faction: %s", pad, tc, faction.getColor(), Misc.ucFirst(faction.getDisplayName()));
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		try {
			info.addImage(commander.getPortraitSprite(), width, 128, 10);

			String hisOrHer =
					commander.getGender() == FullName.Gender.MALE ? "his" : "her",
					timeAgo = Misc.ucFirst(Misc.getAgoStringForTimestamp(timestamp).toLowerCase());

			String bestFactionPrefix = faction.getEntityNamePrefix();
			if (bestFactionPrefix.isEmpty()) bestFactionPrefix = faction.getPersonNamePrefix();
			if (bestFactionPrefix.isEmpty()) bestFactionPrefix = faction.getDisplayName();
			if (activity == null || activity.equals("")) activity = "somewhere ";

			info.addPara(timeAgo + " you heard that a %s fleet commanded by " + commander.getNameString().trim() +
					" was " + activity + "in the " + location + ". It's not clear how much longer this will be the case.",
					10, Misc.getTextColor(), faction.getColor(), bestFactionPrefix);

			info.addPara(Misc.ucFirst(hisOrHer) + " flagship is %s, notable for the following traits:", 10,
					Misc.getTextColor(), Misc.getHighlightColor(), Util.getShipDescription(ship));

			info.addPara("", 0);

			Util.showTraits(info, rep, null, !FactionConfig.get(faction).isCrewlessTraitNamesUsed(), ship.getHullSpec().getHullSize());

			LoyaltyLevel ll = rep.getLoyalty(commander);
			String desc = "The crew of the " + ship.getShipName() + " is %s " + ll.getPreposition() + " "
					+ commander.getNameString().trim() + ".";

			info.addPara(desc, 10, Misc.getTextColor(), Misc.getHighlightColor(), ll.getName());

//			ButtonAPI button = info.addButton("Abandon", "abandon", width, 20, 6);
//			button.setShortcut(Keyboard.KEY_U, true);

			if(Global.getSettings().isDevMode()) {
				info.addButton("Go to fleet", "gotoFleet", width, 20, 6);
			}
		} catch (Exception e) {
			ModPlugin.reportCrash(e);
		}
	}

	@Override
	public boolean shouldRemoveIntel() {
		return fleet == null || fleet.getFlagship() != ship || super.shouldRemoveIntel()
				|| ModPlugin.REMOVE_ALL_DATA_AND_FEATURES;
	}

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		super.buttonPressConfirmed(buttonId, ui);

		switch ((String)buttonId) {
			case "abandon":
				setImportant(false);
				endAfterDelay();
				break;
			case "gotoFleet": Util.teleportEntity(Global.getSector().getPlayerFleet(), fleet); break;
		}
	}

	public void applyHullmodsToShip() {
		ship.getVariant().addMod("reinforcedhull");
		RepRecord.updateRepHullMod(ship);
	}

	@Override
	public void reportRemovedIntel() {
		try {
			super.reportRemovedIntel();

			setImportant(false);
			setNew(false);

			if (fleet != null) Misc.makeUnimportant(fleet, "sun_sl_famous_flagship");

			if (!Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy().contains(ship)) {
				RepRecord.deleteFor(ship);
			}
		} catch (Exception e) { ModPlugin.reportCrash(e); }
	}

	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(faction.getId());
		return tags;
	}

	public String getSortString() {
		return "Famous Flagship" + timestamp;
	}

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		return fleet == null ? super.getMapLocation(map) : fleet;
	}
}