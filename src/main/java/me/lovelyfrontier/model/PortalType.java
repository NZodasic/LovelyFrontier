package me.lovelyfrontier.model;

import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;

public class PortalType {
    private String id;
    private Material triggerItem;
    // 3x3 pattern mapping character keys to Materials (e.g. 'S' -> Material.STONE)
    private String[] pattern = new String[3]; // e.g. ["SIS", "IBI", "SIS"]
    private final Map<Character, Material> patternKeys = new HashMap<>();
    private String action; // OPEN_MENU, RANDOM_DUNGEON, SPECIFIC:<dungeon_id>
    private String triggerMethod = "DROP"; // DROP, RIGHT_CLICK, LEFT_CLICK

    public PortalType() {}

    public PortalType(String id, Material triggerItem, String[] pattern, Map<Character, Material> patternKeys, String action, String triggerMethod) {
        this.id = id;
        this.triggerItem = triggerItem;
        this.pattern = pattern;
        this.patternKeys.putAll(patternKeys);
        this.action = action;
        this.triggerMethod = triggerMethod != null ? triggerMethod : "DROP";
    }

    public String getTriggerMethod() { return triggerMethod; }
    public void setTriggerMethod(String triggerMethod) { this.triggerMethod = triggerMethod; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Material getTriggerItem() { return triggerItem; }
    public void setTriggerItem(Material triggerItem) { this.triggerItem = triggerItem; }

    public String[] getPattern() { return pattern; }
    public void setPattern(String[] pattern) { this.pattern = pattern; }

    public Map<Character, Material> getPatternKeys() { return patternKeys; }
    public void addPatternKey(char key, Material material) { this.patternKeys.put(key, material); }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    /**
     * Helper to verify if a 3x3 grid matches this pattern.
     * grid contains Materials in order from top-left to bottom-right:
     * [0] [1] [2]
     * [3] [4] [5]
     * [6] [7] [8]
     */
    public boolean matches(Material[] grid) {
        if (grid.length != 9) return false;
        for (int r = 0; r < 3; r++) {
            String row = pattern[r];
            if (row == null || row.length() != 3) return false;
            for (int c = 0; c < 3; c++) {
                char ch = row.charAt(c);
                Material required = patternKeys.get(ch);
                // Space ' ' means ignore this block
                if (ch == ' ' || required == null) {
                    continue;
                }
                int index = r * 3 + c;
                if (grid[index] != required) {
                    return false;
                }
            }
        }
        return true;
    }
}
