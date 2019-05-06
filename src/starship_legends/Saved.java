package starship_legends;

import com.fs.starfarer.api.Global;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Saved<T> {
    static final String PREFIX = "sun_sl_";
    static Map<String, Saved> instanceRegistry = new HashMap();

    static void updatePersistentData() {
        for(Saved saved : instanceRegistry.values()) {
            Global.getSector().getPersistentData().put(saved.key, saved.val);
        }
    }
    static void loadPersistentData() {
        for(Saved saved : instanceRegistry.values()) {
            if(Global.getSector().getPersistentData().containsKey(saved.key)) {
                saved.val = Global.getSector().getPersistentData().get(saved.key);
            }
        }
    }

    public T val;
    private String key;

    public Saved(String key, T defaultValue) {
        this.key = PREFIX + key;
        this.val = defaultValue;
        instanceRegistry.put(this.key, this);
    }
}
