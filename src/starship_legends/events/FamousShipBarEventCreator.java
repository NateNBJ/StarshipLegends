package starship_legends.events;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import starship_legends.ModPlugin;

public class FamousShipBarEventCreator extends BaseBarEventCreator {
	
	public PortsideBarEvent createBarEvent() {
		return new FamousShipBarEvent();
	}

	@Override
	public float getBarEventActiveDuration() {
		return 5f + (float) Math.random() * 5f;
	}

	@Override
	public float getBarEventFrequencyWeight() {
		if(ModPlugin.REMOVE_ALL_DATA_AND_FEATURES) return 0;

		return super.getBarEventFrequencyWeight()
				* (ModPlugin.FAMOUS_FLAGSHIP_BAR_EVENT_CHANCE
				+ ModPlugin.FAMOUS_DERELICT_BAR_EVENT_CHANCE);
	}

	@Override
	public float getBarEventTimeoutDuration() {
		return 0;
	}

	@Override
	public float getBarEventAcceptedTimeoutDuration() {
		return 0;
	}


}
