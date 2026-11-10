package dev.android.dynamicisland.island;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import com.android.systemui.statusbar.phone.ongoingactivity.OngoingActivityDataHelper;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.settings.ModuleSettings;
import dev.android.dynamicisland.settings.RuntimeRegressionTest;
import dev.android.dynamicisland.settings.SettingsProvider;
import java.time.Duration;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35,qualifiers="w360dp-h800dp-mdpi",shadows=RuntimeRegressionTest.EmptyXPrefs.class)
public class IncomingCallControllerTest {
    private final Context app=RuntimeEnvironment.getApplication();
    private boolean previousHeadsUp, previousAutoOpen, previousIMotion, previousIncomingCard, previousThirdParty, previousPreviewProcess;

    @Before public void rememberSettings() {
        previousHeadsUp=ReflectionHelpers.getStaticField(ModuleSettings.class,"headsUpIsland");
        previousAutoOpen=ReflectionHelpers.getStaticField(ModuleSettings.class,"autoOpenCards");
        previousIMotion=ReflectionHelpers.getStaticField(ModuleSettings.class,"iMode");
        previousIncomingCard=ReflectionHelpers.getStaticField(ModuleSettings.class,"showIncomingCallCard");
        previousThirdParty=ReflectionHelpers.getStaticField(ModuleSettings.class,"thirdPartyCalls");
        previousPreviewProcess=ReflectionHelpers.getStaticField(ModuleSettings.class,"previewProcess");
        ReflectionHelpers.setStaticField(ModuleSettings.class,"showIncomingCallCard",true);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"thirdPartyCalls",true);
    }

    @After public void restoreSettings() {
        ModuleSettings.setOnSwitchesChanged(null);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"headsUpIsland",previousHeadsUp);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"autoOpenCards",previousAutoOpen);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"iMode",previousIMotion);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"showIncomingCallCard",previousIncomingCard);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"thirdPartyCalls",previousThirdParty);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"previewProcess",previousPreviewProcess);
        OngoingActivityDataHelper.baseActivityComponentName=null;
    }

    private IslandController controller(boolean imotion) {
        var prefs=ModuleSettings.openStore(app);prefs.edit().clear().putBoolean(ModuleSettings.KEY_I_MODE,imotion).commit();ModuleSettings.configurePreview(prefs);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"autoOpenCards",false);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"headsUpIsland",false);
        var controller=ReflectionHelpers.callConstructor(IslandController.class,ReflectionHelpers.ClassParameter.from(Context.class,app),
                ReflectionHelpers.ClassParameter.from(ClassLoader.class,getClass().getClassLoader()));
        IslandPlacement placement=ReflectionHelpers.getField(controller,"placement");placement.ensureWindow(true);
        shadowOf(Looper.getMainLooper()).idle();
        return controller;
    }

    private StatusBarNotification call(boolean ringing) {
        return call("com.samsung.android.incallui", ringing);
    }

    private StatusBarNotification call(String pkg, boolean ringing) {
        var person=new Person.Builder().setName("Alice").build();
        var answer=PendingIntent.getBroadcast(app,0,new Intent("audit.answer.call"),PendingIntent.FLAG_IMMUTABLE);
        var decline=PendingIntent.getBroadcast(app,0,new Intent("audit.decline.call"),PendingIntent.FLAG_IMMUTABLE);
        Notification.Style style=ringing ? Notification.CallStyle.forIncomingCall(person,decline,answer) : Notification.CallStyle.forOngoingCall(person,decline);
        var n=new Notification.Builder(app,"calls").setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setStyle(style).build();
        return new StatusBarNotification(pkg,pkg,1,null,1001,1,0,n,android.os.Process.myUserHandle(),System.currentTimeMillis());
    }

    private IslandView view(IslandController controller) { IslandPlacement placement=ReflectionHelpers.getField(controller,"placement");return placement.view(); }

    private List<IslandItem> shownItems(IslandController controller) {
        Object scene=ReflectionHelpers.getField(view(controller),"scene");
        return ReflectionHelpers.getField(scene,"items");
    }

    private void setConfirmedForeground(IslandController controller, String pkg) {
        Object sources=ReflectionHelpers.getField(controller,"sources");
        ReflectionHelpers.setField(sources,"foregroundApp",
                new ForegroundApp(()->List.of(new ForegroundApp.Task(pkg,true,true,false)),()->pkg));
    }

    private long hideBroadcastCount() {
        List<Intent> sent = shadowOf((Application) app).getBroadcastIntentsForUser(UserHandle.getUserHandleForUid(0));
        return sent == null ? 0 : sent.stream().filter(i -> SamsungCallPopupTakeover.HIDE_ACTION.equals(i.getAction())).count();
    }

    public static class CallInfo {
        public String key = "0|com.samsung.android.incallui|1|null|1001";
        public String packageName = "com.samsung.android.incallui";
        public boolean isOngoing = true;
        public Long callStartTime = System.currentTimeMillis() - 4_000;
        public String appName = "Phone";
    }

    public static class OngoingCallController {
        public final CallInfo callNotificationInfo = new CallInfo();
        public boolean chipShown;
        public boolean hasOngoingCall() { return chipShown; }
    }

    @Test public void aSnapshotAloneAutoShowsTheCallerWithAutoOpenDisabledThenClosesOnAnswer() {
        for(boolean imotion:new boolean[]{false,true}) {
            var controller=controller(imotion);var call=call(true);
            controller.onNotificationsSnapshot(List.of(call));shadowOf(Looper.getMainLooper()).idle();
            assertTrue("a native incoming call refreshes the island without an ongoing chip; iMotion="+imotion+"; "+dev.android.dynamicisland.xposed.Diagnostics.report(),view(controller).isExpanded());
            assertTrue(controller.ownsHeadsUp(call.getKey()));
            Object owner=controller.headsUpOwner(call.getKey());assertNotNull(owner);
            assertEquals("Alice",((dev.android.dynamicisland.island.model.IslandItem)owner).title);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(8));assertTrue(view(controller).isExpanded());
            controller.onNotificationsSnapshot(List.of(call(false)));shadowOf(Looper.getMainLooper()).idle();assertFalse(view(controller).isExpanded());
        }
    }

    @Test public void aCallPinDecisionCanShowTheBannerBeforeTheFullSnapshotButHiddenOverlayDoesNotClaimIt() {
        var controller=controller(false);var call=call(true);
        assertTrue(controller.peekHeadsUp(call));assertTrue(controller.ownsHeadsUp(call.getKey()));
        view(controller).setSuppressed(true);assertFalse(controller.peekHeadsUp(call));assertFalse(controller.ownsHeadsUp(call.getKey()));
    }

    @Test public void disabledIncomingCallCardLeavesRingingToSamsungWithoutHijackingHeadsUpOrShadeRowThenDisplaysOngoingCallAfterAnswering() {
        var controller=controller(false);
        // Even with headsUpIsland=true and autoOpenCards=true (default user settings), disabled incoming call card must not hijack heads-up or shade row
        ReflectionHelpers.setStaticField(ModuleSettings.class,"headsUpIsland",true);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"autoOpenCards",true);
        ReflectionHelpers.setStaticField(ModuleSettings.class,"showIncomingCallCard",false);
        shadowOf((Application) app).clearBroadcastIntents();

        // Simulate One UI 8 OngoingCallController having callStartTime > 0 (from notification.when) during ringing
        var prematureCallController = new OngoingCallController();
        prematureCallController.chipShown = true;
        prematureCallController.callNotificationInfo.callStartTime = System.currentTimeMillis() - 1_000;
        controller.setCallController(prematureCallController);
        setConfirmedForeground(controller, "com.sec.android.app.launcher");

        var ringing=call(true);
        assertFalse("heads-up pin decision must not claim native ringing call when disabled even with headsUpIsland=true",controller.peekHeadsUp(ringing));
        controller.onNotificationsSnapshot(List.of(ringing));
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(6));
        assertFalse("Dynamic Island must not auto-expand for native incoming call when disabled",view(controller).isExpanded());
        assertTrue("no regular call card or OA card is shown on Dynamic Island while ringing",shownItems(controller).isEmpty());
        assertFalse(controller.ownsHeadsUp(ringing.getKey()));
        assertNull(controller.headsUpOwner(ringing.getKey()));
        assertNull("ShadeLiveCards itemForKey must return null while ringing so notification panel keeps native call presentation",
                controller.itemForKey(ringing.getKey()));
        assertEquals("Samsung mini-window hide broadcast must not be sent while ringing when disabled",0L,hideBroadcastCount());

        // Third-party VoIP incoming call still works while native incoming call card is disabled
        var voip=call("example.voip",true);
        controller.onNotificationsSnapshot(List.of(ringing,voip));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("VoIP incoming call still expands Dynamic Island",view(controller).isExpanded());
        assertTrue(controller.ownsHeadsUp(voip.getKey()));
        controller.onNotificationsSnapshot(List.of(ringing));
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(view(controller).isExpanded());

        // User answers the native call: Samsung opens full-screen InCallUI, then user presses Back to return to another app
        var ongoingCallController = new OngoingCallController();
        ongoingCallController.chipShown = false; // full-screen InCallUI is visible
        controller.setCallController(ongoingCallController);
        setConfirmedForeground(controller, "com.samsung.android.incallui");
        var connected = call(false);
        controller.onNotificationsSnapshot(List.of(connected));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("ongoing call takeover broadcast is sent once connected", hideBroadcastCount() >= 1L);
        assertTrue("ongoing call card is suppressed while full-screen InCallUI is in foreground", shownItems(controller).isEmpty());

        // User returns to another app: ongoing call card appears on Dynamic Island after fly-in deferral (260ms)
        setConfirmedForeground(controller, "com.sec.android.app.launcher");
        controller.requestRefresh();
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350));
        List<IslandItem> shownInLauncher = shownItems(controller);
        assertEquals("ongoing call card appears once user leaves full-screen InCallUI", 1, shownInLauncher.size());
        IslandItem ongoingCard = shownInLauncher.get(0);
        assertEquals(IslandItem.Kind.CALL, ongoingCard.kind);
        assertFalse(ongoingCard.incomingCall);
        assertEquals(IslandItem.Trailing.CHRONOMETER, ongoingCard.trailing);
        assertEquals(2, ongoingCard.actions.size());
    }

    @Test public void livePreferenceToggleWhileRingingUpdatesCardAndSamsungTakeoverWithoutRestart() {
        var controller = controller(false);
        Robolectric.buildContentProvider(SettingsProvider.class).create(SettingsProvider.AUTHORITY);
        var store = ModuleSettings.openStore(app);
        store.edit().clear()
                .putBoolean(ModuleSettings.KEY_I_MODE, false)
                .putBoolean(ModuleSettings.KEY_AUTO_OPEN_CARDS, false)
                .putBoolean(ModuleSettings.KEY_HEADS_UP_ISLAND, false)
                .putBoolean(ModuleSettings.KEY_SHOW_INCOMING_CALL_CARD, true)
                .commit();
        ReflectionHelpers.setStaticField(ModuleSettings.class, "previewProcess", false);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "started", true);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "prefs", null);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "loaded", false);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "viaProvider", false);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "lastRemote", null);
        ReflectionHelpers.setStaticField(ModuleSettings.class, "lastSwitches", null);
        ReflectionHelpers.callStaticMethod(ModuleSettings.class, "refresh");

        controller.start();
        shadowOf(Looper.getMainLooper()).idle();
        shadowOf((Application) app).clearBroadcastIntents();

        var ringing = call(true);
        controller.onNotificationsSnapshot(List.of(ringing));
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("incoming call card is shown initially", view(controller).isExpanded());
        assertTrue(controller.ownsHeadsUp(ringing.getKey()));
        long initialBroadcasts = hideBroadcastCount();
        assertEquals("first hide attempt sent on ringing", 1L, initialBroadcasts);

        // Switch preference OFF live while ringing
        store.edit().putBoolean(ModuleSettings.KEY_SHOW_INCOMING_CALL_CARD, false).commit();
        ReflectionHelpers.callStaticMethod(ModuleSettings.class, "refresh");
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse("incoming call card closes cleanly when switched off live", view(controller).isExpanded());
        assertFalse(controller.ownsHeadsUp(ringing.getKey()));

        // Advance 5 seconds: pending Samsung hide retries must have been cancelled
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5));
        assertEquals("pending Samsung mini-window hide retries must be cancelled when switched off",
                initialBroadcasts, hideBroadcastCount());

        // Switch preference back ON live while still ringing
        store.edit().putBoolean(ModuleSettings.KEY_SHOW_INCOMING_CALL_CARD, true).commit();
        ReflectionHelpers.callStaticMethod(ModuleSettings.class, "refresh");
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("incoming call card re-opens when switched back on while ringing", view(controller).isExpanded());
        assertTrue(controller.ownsHeadsUp(ringing.getKey()));
        assertTrue("Samsung mini-window hide takeover resumes when switched back on",
                hideBroadcastCount() > initialBroadcasts);
    }
}
