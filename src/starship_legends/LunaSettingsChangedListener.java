package starship_legends;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

import static starship_legends.ModPlugin.LUNALIB_ID;

public class LunaSettingsChangedListener implements LunaSettingsListener {
    @Override
    public void settingsChanged(String idOfModWithChangedSettings) {
        if(idOfModWithChangedSettings.equals(ModPlugin.ID)) {
            ModPlugin.readSettings();
        }
    }
    public static void addToManagerIfNeeded() {
        if(Global.getSettings().getModManager().isModEnabled(LUNALIB_ID)
                && !LunaSettings.INSTANCE.hasListenerOfClass(LunaSettingsChangedListener.class)) {

            LunaSettings.INSTANCE.addListener(new LunaSettingsChangedListener());
        }
    }
}
