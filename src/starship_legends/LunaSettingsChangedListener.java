package starship_legends;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettingsListener;

import static starship_legends.ModPlugin.LUNALIB_ID;

public class LunaSettingsChangedListener implements LunaSettingsListener {
    @Override
    public void settingsChanged() {
        ModPlugin.readSettings();
    }

    public static void addToManagerIfNeeded() {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)
                && !Global.getSector().getListenerManager().hasListenerOfClass(LunaSettingsChangedListener.class)) {

            Global.getSector().getListenerManager().addListener(new LunaSettingsChangedListener(), true);
        }
    }
}
