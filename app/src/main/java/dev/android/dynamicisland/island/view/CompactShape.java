package dev.android.dynamicisland.island.view;

import android.graphics.Path;
import android.graphics.RectF;
import dev.android.dynamicisland.island.graphics.Easing;

/** The camera bar's outline. Near-square bounds converge on a circle as the bar contracts. */
final class CompactShape {
    private static final float BAR_ASPECT = 1.9f;
    private static final float CORNER_SCALE = 0.82f;
    private static final float CIRCLE_HANDLE = 0.55228475f;
    private static final float BAR_HANDLE = 0.84f;

    private CompactShape() { }

    static void path(Path out, RectF bounds) {
        path(out, bounds, Math.min(bounds.width(), bounds.height()) / 2f, 1f);
    }

    /** Fade the compact contour as the card grows into its normal expanded shape. */
    static float cardAmount(RectF bounds, float pillHeight, float expandedHeight) {
        return 1f - smoothStep((bounds.height() - pillHeight) / Math.max(1f, expandedHeight - pillHeight));
    }

    static void path(Path out, RectF bounds, float maxRadius, float amount) {
        out.rewind();
        if (bounds.isEmpty()) return;

        float aspect = bounds.width() / bounds.height();
        float blend = amount * smoothStep((aspect - 1f) / (BAR_ASPECT - 1f));
        float radius = Math.min(maxRadius, Math.min(bounds.width(), bounds.height()) / 2f);
        if (blend == 0f || radius == 0f) {
            out.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            return;
        }
        radius *= Easing.lerp(1f, CORNER_SCALE, blend);
        float handle = radius * Easing.lerp(CIRCLE_HANDLE, BAR_HANDLE, blend);
        float left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom;
        float start = left + radius, end = right - radius;
        float upper = top + radius, lower = bottom - radius;

        out.moveTo(start, top);
        out.lineTo(end, top);
        out.cubicTo(end + handle, top, right, upper - handle, right, upper);
        out.lineTo(right, lower);
        out.cubicTo(right, lower + handle, end + handle, bottom, end, bottom);
        out.lineTo(start, bottom);
        out.cubicTo(start - handle, bottom, left, lower + handle, left, lower);
        out.lineTo(left, upper);
        out.cubicTo(left, upper - handle, start - handle, top, start, top);
        out.close();
    }

    private static float smoothStep(float value) {
        float t = Easing.clamp01(value);
        return t * t * (3f - 2f * t);
    }
}
