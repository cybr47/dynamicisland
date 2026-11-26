package dev.android.dynamicisland.island.view;

import dev.android.dynamicisland.island.model.IslandItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preserves the order in which ongoing activities became available to the island. */
final class ActivityQueue {
    private final List<IslandItem> items = new ArrayList<>();
    private String selected;

    void update(List<IslandItem> active, List<IslandItem> minimized) {
        Map<String, IslandItem> available = new LinkedHashMap<>();
        addActivities(active, available);
        addActivities(minimized, available);
        int previousIndex = indexOf(selected);
        if (previousIndex >= 0 && !available.containsKey(selected)) {
            selected = null;
            for (int i = previousIndex + 1; i < items.size() && selected == null; i++) {
                String key = items.get(i).key;
                if (available.containsKey(key)) selected = key;
            }
            for (int i = previousIndex - 1; i >= 0 && selected == null; i--) {
                String key = items.get(i).key;
                if (available.containsKey(key)) selected = key;
            }
        }
        List<IslandItem> remaining = new ArrayList<>(available.size());
        for (IslandItem item : items) {
            IslandItem latest = available.remove(item.key);
            if (latest != null) remaining.add(latest);
        }
        remaining.addAll(available.values());
        items.clear();
        items.addAll(remaining);
        if (selected == null && !items.isEmpty()) selected = items.get(0).key;
    }

    private static void addActivities(List<IslandItem> source, Map<String, IslandItem> available) {
        for (IslandItem item : source) {
            if (!item.transientPopup) available.putIfAbsent(item.key, item);
        }
    }

    IslandItem current() {
        int index = indexOf(selected);
        return index < 0 ? null : items.get(index);
    }

    boolean select(String key) {
        if (indexOf(key) < 0) return false;
        selected = key;
        return true;
    }

    IslandItem neighbor(String key, int direction) {
        int index = indexOf(key);
        if (index < 0) return null;
        int neighbor = index + direction;
        return neighbor < 0 || neighbor >= items.size() ? null : items.get(neighbor);
    }

    private int indexOf(String key) {
        if (key != null) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).key.equals(key)) return i;
            }
        }
        return -1;
    }
}
