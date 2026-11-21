package dev.android.dynamicisland.island.view;

import dev.android.dynamicisland.testutil.RenderCapture;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.Choreographer;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.settings.ModuleSettings;
import java.io.File;
import java.time.Duration;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowChoreographer;
import org.robolectric.shadows.ShadowSystemClock;
import org.robolectric.util.ReflectionHelpers;

/** Real pill painter, identical backgrounds for each mode; capture output is available in CI. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35, qualifiers = "mdpi")
public class PillOutlineContrastRenderTest {
    @Test public void aSmallerRadiusFillsMoreOfBothPillCornersInEveryAnimation() {
        Context context = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = context.getSharedPreferences("pill-roundness-render", 0);
        ShadowChoreographer.setPaused(true);
        ShadowChoreographer.setFrameDelay(Duration.ofMillis(16));
        Bitmap bitmap = Bitmap.createBitmap(320, 120, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        try {
            for (String animation : List.of("Default", "Legacy", "iMotion")) {
                prefs.edit().clear().putBoolean(ModuleSettings.KEY_I_MODE, animation.equals("iMotion"))
                        .putBoolean(ModuleSettings.KEY_LEGACY_ANIMATION, animation.equals("Legacy"))
                        .putBoolean(ModuleSettings.KEY_AMBIENT_GLOW, false)
                        .putInt(ModuleSettings.KEY_PILL_OUTLINE, ModuleSettings.PILL_OUTLINE_NONE).apply();
                ModuleSettings.configurePreview(prefs);
                IslandView view = new IslandView(context, IslandHost.NONE);
                view.setGeometry(320, 160, 60, 22, false);
                view.layout(0, 0, 320, 120);
                IslandItem timer = new IslandItem("timer");
                timer.title = "Timer";
                timer.trailing = IslandItem.Trailing.TEXT;
                timer.trailingText = "10:01";
                view.setItems(List.of(timer));
                for (int frame = 0; frame < 100; frame++) {
                    ShadowSystemClock.advanceBy(Duration.ofMillis(16));
                    view.doFrame(SystemClock.uptimeMillis() * 1_000_000L);
                    Choreographer.getInstance().removeFrameCallback(view);
                }

                RectF bounds = new RectF();
                view.computeMain(bounds);
                view.draw(canvas);
                int[] capsule = cornerPixels(bitmap, bounds);
                prefs.edit().putInt(ModuleSettings.KEY_PILL_ROUNDNESS, 50).apply();
                ModuleSettings.configurePreview(prefs);
                bitmap.eraseColor(Color.TRANSPARENT);
                view.draw(canvas);
                int[] round = cornerPixels(bitmap, bounds);
                prefs.edit().putInt(ModuleSettings.KEY_PILL_ROUNDNESS, 0).apply();
                ModuleSettings.configurePreview(prefs);
                bitmap.eraseColor(Color.TRANSPARENT);
                view.draw(canvas);
                int[] rectangular = cornerPixels(bitmap, bounds);
                bitmap.eraseColor(Color.TRANSPARENT);

                assertTrue(animation + " left corner", capsule[0] < round[0] && round[0] < rectangular[0]);
                assertTrue(animation + " right corner", capsule[1] < round[1] && round[1] < rectangular[1]);
                assertEquals(15f, Shapes.pillRadius(new RectF(0, 0, 30, 30)), .001f);
            }
        } finally {
            prefs.edit().clear().apply();
            ModuleSettings.configurePreview(prefs);
        }
    }

    private static int[] cornerPixels(Bitmap bitmap, RectF bounds) {
        int[] pixels = new int[2];
        for (int row = 0; row < 8; row++) for (int column = 0; column < 8; column++) {
            int y = Math.round(bounds.top) + row;
            if (Color.alpha(bitmap.getPixel(Math.round(bounds.left) + column, y)) > 127) pixels[0]++;
            if (Color.alpha(bitmap.getPixel(Math.round(bounds.right) - 1 - column, y)) > 127) pixels[1]++;
        }
        return pixels;
    }

    @Test public void modesMatchActualBackgroundAndNeverChangePillInterior() throws Exception {
        Context ctx = RuntimeEnvironment.getApplication();
        SharedPreferences prefs = ctx.getSharedPreferences("outline-render", 0);
        int[] backgrounds = {Color.BLACK, 0xFF181818, 0xFF777777, Color.WHITE};
        Bitmap sheet = Bitmap.createBitmap(960, 480, Bitmap.Config.ARGB_8888);
        Canvas capture = new Canvas(sheet);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setTextSize(16f);
        try {
            for (int row = 0; row < backgrounds.length; row++) {
                int[] rimCounts = new int[3];
                for (int mode = 0; mode < 3; mode++) {
                    prefs.edit().putInt(ModuleSettings.KEY_PILL_OUTLINE, mode).apply();
                    ModuleSettings.configurePreview(prefs);
                    IslandView view = new IslandView(ctx, IslandHost.NONE);
                    view.setGeometry(320, 160, 60, 22, false);
                    view.layout(0, 0, 320, 120);
                    Scene scene = ReflectionHelpers.getField(view, "scene");
                    scene.mode = Mode.COMPACT;
                    scene.springs.width.snapTo(200);
                    scene.springs.height.snapTo(40);
                    scene.springs.visibility.snapTo(1);
                    scene.springs.suppress.snapTo(1);
                    scene.springs.shadeGate.snapTo(1);
                    scene.springs.outlineFade.snapTo(1);
                    scene.springs.pillBlob.snapTo(1);
                    view.setPillBackgroundLuma(PillOutlinePolicy.luma(backgrounds[row]));
                    Bitmap pill = Bitmap.createBitmap(320, 120, Bitmap.Config.ARGB_8888);
                    view.draw(new Canvas(pill));
                    RectF rect = new RectF();
                    view.computeMain(rect);
                    assertEquals(Color.BLACK, pill.getPixel(160, 60));
                    for (int y = 0; y < 120; y++) for (int x = 0; x < 320; x++) {
                        int pixel = pill.getPixel(x, y);
                        if (!rect.contains(x + .5f, y + .5f) && Color.alpha(pixel) > 0
                                && Color.red(pixel) > 0) rimCounts[mode]++;
                    }
                    Bitmap tile = Bitmap.createBitmap(320, 120, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(tile);
                    canvas.drawColor(backgrounds[row]);
                    canvas.drawBitmap(pill, 0f, 0f, null);
                    text.setColor(row < 2 ? Color.WHITE : Color.BLACK);
                    canvas.drawText(new String[]{"Auto", "Always", "None"}[mode], 12, 22, text);
                    capture.drawBitmap(tile, mode * 320, row * 120, null);
                }
                assertTrue("Always must paint an external rim", rimCounts[1] > 0);
                assertEquals("None must not paint an external rim", 0, rimCounts[2]);
                if (row < 2) assertEquals(rimCounts[1], rimCounts[0]);
                else assertEquals(0, rimCounts[0]);
            }
            File output = new File("build/render/pill-outline-contrast.png");
            output.getParentFile().mkdirs();
            RenderCapture.write(sheet, output);
        } finally {
            prefs.edit().clear().apply();
            ModuleSettings.configurePreview(prefs);
        }
    }
}
