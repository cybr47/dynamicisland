package dev.android.dynamicisland.island.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Region;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 35)
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
    public void aContractedBarAndFullyOpenedCardKeepTheirRoundOutlines() {
        Path path = new Path();
        RectF dot = new RectF(30, 30, 54, 54);
        CompactShape.path(path, dot);
        assertEquals(roundRect(dot, 12), region(path));

        RectF compact = new RectF(20, 10, 160, 40);
        RectF card = new RectF(10, 20, 220, 160);
        assertEquals(1f, CompactShape.cardAmount(compact, 30, 140), 0f);
        float amount = CompactShape.cardAmount(card, 30, 140);
        assertEquals(0f, amount, 0f);
        CompactShape.path(path, card, 28, amount);
        assertEquals(roundRect(card, 28), region(path));
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
