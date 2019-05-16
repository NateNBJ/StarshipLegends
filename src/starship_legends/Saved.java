package starship_legends;

import com.fs.starfarer.api.Global;

import java.util.*;

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
            } else if(saved.val.getClass().isPrimitive()) {
                saved.val = saved.defaultVal;
            } else if(saved.val instanceof Collection) {
                ((Collection)saved.val).clear();
            }
        }
    }

    public T val, defaultVal;
    private String key;

    public Saved(String key, T defaultValue) {
        this.key = PREFIX + key;
        this.val = defaultValue;
        this.defaultVal = defaultValue;
        instanceRegistry.put(this.key, this);
    }
}
