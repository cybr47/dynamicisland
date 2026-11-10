package dev.android.dynamicisland.island;

import dev.android.dynamicisland.island.detect.CallDetector;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Opens an incoming call without depending on ordinary notification auto-open, and keeps it until answered or dismissed. */
final class IncomingCallBanner {
    private final Set<String> announced = new HashSet<>();
    private IslandItem current;

    private static boolean enabled(IslandItem item) {
        if (item == null || !item.incomingCall) return false;
        return item.packageName == null || CallDetector.NATIVE_PACKAGES.contains(item.packageName)
                ? ModuleSettings.showIncomingCallCard() : ModuleSettings.thirdPartyCalls();
    }

    IslandItem owner(String notificationKey, IslandView view) {
        return current != null && enabled(current) && notificationKey != null && notificationKey.equals(current.sourceKey) && view != null
                && !view.isSuppressedNow() && view.isShowingCard(current) ? current : null;
    }

    void beforeUpdate(IslandView view, Set<String> ringing, List<IslandItem> shown) {
        announced.retainAll(ringing);
        if (current == null) return;
        boolean visible = view.isShowingCard(current);
        if (!enabled(current) || !ringing.contains(current.key)) {
            if (visible) {
                if (!enabled(current)) announced.remove(current.key);
                view.collapse();
            }
            current = null;
        } else if (visible && !shown.isEmpty() && shown.get(0).priority() > current.priority()) {
            announced.remove(current.key); // Charging may interrupt; resume the ringing card after it leaves.
            current = null;
        } else if (!visible) current = null;
    }

    boolean show(IslandView view, List<IslandItem> shown) {
        for (IslandItem item : shown) {
            if (!enabled(item) || announced.contains(item.key)) continue;
            if (!shown.isEmpty() && shown.get(0).priority() > item.priority()) return false;
            if (view.peek(item, 1_000)) {
                announced.add(item.key); current = item;
                return true;
            }
        }
        return false;
    }
}
