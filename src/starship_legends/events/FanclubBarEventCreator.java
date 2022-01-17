package starship_legends.events;

import com.fs.starfarer.api.impl.campaign.intel.bar.PortsideBarEvent;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BaseBarEventCreator;
import starship_legends.ModPlugin;

public class FanclubBarEventCreator extends BaseBarEventCreator {
	
	public PortsideBarEvent createBarEvent() {
		return new FanclubBarEvent();
	}

	@Override
	public float getBarEventActiveDuration() {
		return 1f;
	}

	@Override
	public float getBarEventFrequencyWeight() {
		return ModPlugin.REMOVE_ALL_DATA_AND_FEATURES ? 0
				: super.getBarEventFrequencyWeight() * FanclubBarEvent.getChanceOfAnyFanclubEvent();
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
