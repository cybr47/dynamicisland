package dev.android.dynamicisland.island;

import static org.junit.Assert.*;
import android.content.Context;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.IslandView;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35)
public class IncomingCallBannerTest {
    @After public void restoreSettings() {
        Context app=RuntimeEnvironment.getApplication();
        var prefs=ModuleSettings.openStore(app);
        prefs.edit().clear().commit();
        ModuleSettings.configurePreview(prefs);
    }

    private IslandItem call() {
        var item=new IslandItem("incoming:call");item.sourceKey="call";item.packageName="com.samsung.android.incallui";
        item.title="Alice";item.kind=IslandItem.Kind.CALL;
        item.incomingCall=true;item.transientPopup=true;return item;
    }
    private IslandView view(boolean iMotion) {
        Context app=RuntimeEnvironment.getApplication();var prefs=ModuleSettings.openStore(app);
        prefs.edit().clear().putBoolean(ModuleSettings.KEY_I_MODE,iMotion).commit();ModuleSettings.configurePreview(prefs);
        var view=new IslandView(app,null);view.setGeometry(1080,540,60,90,false);view.layout(0,0,1080,900);return view;
    }
    @Test public void ringsAsABannerWithoutAutoOpenAndDoesNotCollapseAfterThreeSecondsInEitherMode() {
        for(boolean iMotion:new boolean[]{false,true}) {
            var view=view(iMotion);var banner=new IncomingCallBanner();var call=call();view.setItems(List.of(call));
            assertTrue(banner.show(view,List.of(call)));assertTrue(view.isExpanded());assertTrue(view.isShowingCard(call));
            ShadowSystemClock.advanceBy(Duration.ofSeconds(30));
            Runnable timeout=ReflectionHelpers.callInstanceMethod(view,"autoCollapseTask");timeout.run();
            assertTrue("caller stays visible while ringing; iMotion="+iMotion,view.isExpanded());
            banner.beforeUpdate(view,Set.of(),List.of());view.setItems(List.of());assertFalse(view.isExpanded());
        }
    }
    @Test public void explicitDismissalIsRespectedButANewCallAndChargingInterruptionCanAnnounce() {
        for(boolean iMotion:new boolean[]{false,true}) {
            var view=view(iMotion);var banner=new IncomingCallBanner();var call=call();view.setItems(List.of(call));assertTrue(banner.show(view,List.of(call)));
            view.collapse();banner.beforeUpdate(view,Set.of(call.key),List.of(call));view.setItems(List.of(call));assertFalse(banner.show(view,List.of(call)));
            banner.beforeUpdate(view,Set.of(),List.of());view.setItems(List.of());
            banner.beforeUpdate(view,Set.of(call.key),List.of(call));view.setItems(List.of(call));assertTrue(banner.show(view,List.of(call)));
            var charging=new IslandItem("event:charging");charging.kind=IslandItem.Kind.EVENT;charging.transientPopup=true;
            banner.beforeUpdate(view,Set.of(call.key),List.of(charging,call));view.setItems(List.of(charging,call));assertFalse(banner.show(view,List.of(charging,call)));
            banner.beforeUpdate(view,Set.of(call.key),List.of(call));view.setItems(List.of(call));assertTrue(banner.show(view,List.of(call)));
        }
    }
    @Test public void shadeAndReplyDoNotConsumeThePendingCallAnnouncement() {
        var view=view(false);var banner=new IncomingCallBanner();var call=call();view.setItems(List.of(call));
        view.setHeld(true);assertFalse(banner.show(view,List.of(call)));view.setHeld(false);
        view.setShadeFraction(1);assertFalse(banner.show(view,List.of(call)));view.setShadeFraction(0);
        assertTrue(banner.show(view,List.of(call)));
    }
    @Test public void duplicateNativeAndOaCardsAreRemovedOnlyForTheExactRingingNotification() {
        var incoming=call();var duplicate=new IslandItem("call:call");duplicate.kind=IslandItem.Kind.CALL;
        var oa=new IslandItem("oa:call");oa.sourceKey="call";oa.kind=IslandItem.Kind.CALL;
        var noEntry=new IslandItem("oa:call");noEntry.kind=IslandItem.Kind.CALL;
        var fallback=new IslandItem("callx:call");fallback.kind=IslandItem.Kind.CALL;
        var other=new IslandItem("call:other");other.kind=IslandItem.Kind.CALL;
        var items=new java.util.ArrayList<>(List.of(duplicate,oa,noEntry,fallback,incoming,other));ItemDedupe.apply(items,key->Set.of());
        assertEquals(List.of(incoming,other),items);

        // Even when showIncomingCallCard is disabled (so `incoming` is not in `items`), extra ringing keys still suppress duplicate call/oa cards
        var withoutIncoming=new java.util.ArrayList<>(List.of(duplicate,oa,noEntry,fallback,other));
        ItemDedupe.apply(withoutIncoming,key->Set.of(),Set.of("call"));
        assertEquals(List.of(other),withoutIncoming);
    }

    @Test public void aDedicatedIncomingCardWinsOverARingingLegacyCardForTheSameNotification() {
        var incoming=call();var legacy=new IslandItem("call:call");legacy.sourceKey="call";legacy.incomingCall=true;
        var items=new java.util.ArrayList<>(List.of(legacy,incoming));ItemDedupe.apply(items,key->Set.of());
        assertEquals(List.of(incoming),items);
    }

    @Test public void disabledPreferencePreventsAutoOpenClosesLiveRingingBannerAndPreservesVoipBanner() {
        var view=view(false);var banner=new IncomingCallBanner();var call=call();
        ReflectionHelpers.setStaticField(ModuleSettings.class,"showIncomingCallCard",false);
        view.setItems(List.of(call));
        assertFalse("disabled preference must not auto-open native incoming card",banner.show(view,List.of(call)));
        assertFalse(view.isExpanded());
        assertNull(banner.owner(call.sourceKey,view));

        // Third-party VoIP incoming call still opens when native incoming call card is disabled
        var voip=new IslandItem("incoming:voip");voip.sourceKey="voip";voip.packageName="example.voip";
        voip.title="Bob";voip.kind=IslandItem.Kind.CALL;voip.incomingCall=true;voip.transientPopup=true;
        view.setItems(List.of(voip));
        assertTrue(banner.show(view,List.of(voip)));
        assertTrue(view.isExpanded());
        banner.beforeUpdate(view,Set.of(),List.of());view.setItems(List.of());assertFalse(view.isExpanded());

        // Switching preference ON while ringing opens the native card, and switching OFF closes it cleanly
        ReflectionHelpers.setStaticField(ModuleSettings.class,"showIncomingCallCard",true);
        view.setItems(List.of(call));
        assertTrue(banner.show(view,List.of(call)));
        assertTrue(view.isExpanded());
        assertSame(call,banner.owner(call.sourceKey,view));

        ReflectionHelpers.setStaticField(ModuleSettings.class,"showIncomingCallCard",false);
        assertNull("owner immediately yields when preference is disabled",banner.owner(call.sourceKey,view));
        banner.beforeUpdate(view,Set.of(call.key),List.of(call));
        assertFalse("currently displayed incoming card closes cleanly when switched off",view.isExpanded());

        // Re-enabling while still ringing can announce again since it was closed by the setting, not user dismissal
        ReflectionHelpers.setStaticField(ModuleSettings.class,"showIncomingCallCard",true);
        view.setItems(List.of(call));
        assertTrue(banner.show(view,List.of(call)));
        assertTrue(view.isExpanded());

        // Manual dismissal while enabled is still respected across updates
        view.collapse();
        banner.beforeUpdate(view,Set.of(call.key),List.of(call));
        assertFalse(banner.show(view,List.of(call)));
    }
}
