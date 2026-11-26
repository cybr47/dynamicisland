package dev.android.dynamicisland.island.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.island.view.card.CardBody;
import dev.android.dynamicisland.settings.ModuleSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "w360dp-h640dp-notnight-mdpi")
public class CompactShapeTest {
    @Test
    public void wideBarHasFullerCornersWithinTheSameBounds() {
        RectF bar = new RectF(20, 10, 160, 46);
        Path path = new Path();
        CompactShape.path(path, bar);
        assertTrue(path.isConvex());
        RectF bounds = new RectF();
        path.computeBounds(bounds, true);
        assertEquals(bar, bounds);

        Region body = region(path);
        assertTrue(body.contains(25, 14));
        assertTrue(body.contains(154, 14));
        assertTrue(body.contains(90, 28));
        assertFalse(body.contains(20, 10));
        assertFalse(roundRect(bar, 18).contains(25, 14));
    }

    @Test
    public void squareHeadlineKeepsItsSquircleAndCardMorphFollowsWidth() {
        Path path = new Path();
        RectF dot = new RectF(30, 30, 54, 54);
        CompactShape.path(path, dot);
        assertNotEquals(roundRect(dot, 12), region(path));

        RectF card = new RectF(10, 20, 220, 160);
        assertEquals(1f, CompactShape.cardAmount(100, 100, 210), 0f);
        assertEquals(.5f, CompactShape.cardAmount(155, 100, 210), 0f);
        float amount = CompactShape.cardAmount(card.width(), 100, 210);
        assertEquals(0f, amount, 0f);
        CompactShape.path(path, card, 28, amount);
        assertEquals(roundRect(card, 28), region(path));
    }

    @Test
    public void headlineIconUsesTheSeparateNativeSquareContour() {
        Path icon = new Path();
        CompactShape.icon(icon, 20, 10, 56);
        RectF bounds = new RectF();
        icon.computeBounds(bounds, true);
        assertEquals(new RectF(20, 10, 76, 66), bounds);
        assertTrue(region(icon).contains(22, 31));
        assertFalse(region(icon).contains(20, 10));
    }

    @Test
    public void compactCoverSettingAndAppIconReserveTheCorrectWidthAroundTheCamera() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences("headline-test", Context.MODE_PRIVATE);
        prefs.edit().clear().putInt(ModuleSettings.KEY_ISLAND_STYLE, ModuleSettings.STYLE_ONE_DESIGN).commit();
        ModuleSettings.configurePreview(prefs);
        IslandView view = new IslandView(context, IslandHost.NONE);
        view.setGeometry(360, 180, 26, 22, false);
        IslandItem music = new IslandItem("music");
        music.kind = IslandItem.Kind.MEDIA;
        music.isMedia = true;
        music.icon = new ColorDrawable(0xFFE5558C);
        music.iconIsImage = true;
        music.cardImage = music.icon;
        float squarePill = new CompactContent(view, music).desiredWidth();

        prefs.edit().putBoolean(ModuleSettings.KEY_WIDER_ALBUM_COVER, true).commit();
        ModuleSettings.configurePreview(prefs);
        float widePill = new CompactContent(view, music).desiredWidth();
        assertTrue(widePill > squarePill);
        assertTrue(widePill <= view.maxPillW);

        music.cardImage = null;
        assertEquals(squarePill, new CompactContent(view, music).desiredWidth(), .01f);
        IslandItem app = new IslandItem("app");
        app.packageName = "example.app";
        app.kind = IslandItem.Kind.LIVE;
        app.appIcon = new ColorDrawable(0xFF166DF0);
        prefs.edit().putBoolean(ModuleSettings.KEY_WIDER_ALBUM_COVER, false).commit();
        ModuleSettings.configurePreview(prefs);
        assertEquals(widePill, new CompactContent(view, app).desiredWidth(), .01f);
        app.kind = IslandItem.Kind.TIMER;
        app.icon = app.appIcon;
        assertEquals(squarePill, new CompactContent(view, app).desiredWidth(), .01f);
    }

    @Test
    public void adaptiveIconZoomScalesTheLogoInsideTheFixedTile() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences("headline-test", Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        IslandView view = new IslandView(context, IslandHost.NONE);
        int blue = 0xFF166DF0;
        int white = 0xFFFFFFFF;
        Bitmap logo = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setColor(white);
        new Canvas(logo).drawRect(18, 18, 42, 42, paint);
        IslandItem app = new IslandItem("app");
        app.appIcon = new AdaptiveIconDrawable(new ColorDrawable(blue), new BitmapDrawable(context.getResources(), logo));

        ModuleSettings.configurePreview(prefs);
        Bitmap original = drawIconTile(view, app);
        prefs.edit().putInt(ModuleSettings.KEY_APP_ICON_ZOOM, 50).commit();
        ModuleSettings.configurePreview(prefs);
        Bitmap smaller = drawIconTile(view, app);
        prefs.edit().putInt(ModuleSettings.KEY_APP_ICON_ZOOM, 150).commit();
        ModuleSettings.configurePreview(prefs);
        Bitmap larger = drawIconTile(view, app);

        assertEquals(white, original.getPixel(64, 30));
        assertEquals(blue, smaller.getPixel(64, 30));
        assertEquals(blue, original.getPixel(72, 30));
        assertEquals(white, larger.getPixel(72, 30));
        assertEquals(0, larger.getPixel(19, 30));
    }

    private static Bitmap drawIconTile(IslandView view, IslandItem app) {
        Bitmap tile = Bitmap.createBitmap(100, 60, Bitmap.Config.ARGB_8888);
        view.drawHeadlineImage(new Canvas(tile), app, app.appIcon, 20, 30, 60, 28, 1f);
        return tile;
    }

    @Test
    public void samsungClockTextRowKeepsItsActionsAfterPause() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences("headline-test", 0);
        prefs.edit().clear().putInt(ModuleSettings.KEY_ISLAND_STYLE, ModuleSettings.STYLE_ONE_DESIGN)
                .putInt(ModuleSettings.KEY_MATERIAL_STYLE, ModuleSettings.MATERIAL_CLASSIC).commit();
        ModuleSettings.configurePreview(prefs);
        IslandView view = new IslandView(context, IslandHost.NONE);
        BodyFactory factory = new BodyFactory(view, null);
        IslandItem timer = new IslandItem("timer");
        timer.packageName = "com.sec.android.app.clockpackage";
        timer.kind = IslandItem.Kind.TIMER;
        timer.trailing = IslandItem.Trailing.CHRONOMETER;
        IslandItem.Action cancel = new IslandItem.Action("Cancel", null, null, () -> { }, true);
        IslandItem.Action pause = new IslandItem.Action("Pause", null, null, () -> { }, false);
        timer.actions.add(cancel);
        timer.actions.add(pause);
        CardBody running = factory.create(timer, 336, false);
        assertEquals(0xFAFCFCFF, running.headlineSurfaceColor());
        assertSame(cancel, running.hit(84, running.h - 22).action);
        assertSame(pause, running.hit(252, running.h - 22).action);

        IslandItem.Action resume = new IslandItem.Action("Resume", null, null, () -> { }, false);
        timer.actions.set(1, resume);
        timer.trailing = IslandItem.Trailing.TEXT;
        timer.trailingText = "12:51";
        CardBody paused = factory.create(timer, 336, false);
        assertEquals(running.h, paused.h, .01f);
        assertSame(resume, paused.hit(252, paused.h - 22).action);
    }

    private static Region roundRect(RectF bounds, float radius) {
        Path path = new Path();
        path.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        return region(path);
    }

    private static Region region(Path path) {
        Region result = new Region();
        result.setPath(path, new Region(0, 0, 240, 180));
        return result;
    }
}
