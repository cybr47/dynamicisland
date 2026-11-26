package dev.android.dynamicisland.island.view;

import android.graphics.Path;
import android.graphics.RectF;
import dev.android.dynamicisland.island.graphics.Easing;

/** Canvas port of the compact and expanded Headline contours from the ZZKN SystemUI build. */
final class CompactShape {
    // SecHeadlinePillKt.SquircleQuadrant and HeadlineCardShapeDrawable.CIRCLE_QUADRANT.
    private static final float[] SQUIRCLE = {
            1f, 0f, .6926041f, 0f, .4177084f, .037499998f, .23427089f, .2221875f,
            .08812507f, .36947918f, 0f, .61020833f, 0f, 1f
    };
    private static final float[] CIRCLE = {
            1f, 0f, .73478353f, 0f, .48042962f, .10535684f, .29289323f, .29289323f,
            .10535684f, .48042962f, 0f, .73478353f, 0f, 1f
    };
    // SecHeadlinePillKt.SquircleShape's 192-unit icon contour.
    private static final float[] ICON_CUBICS = {
            151.9f, 3.6f, 125.51f, 0f, 96f, 0f,
            66.49f, 0f, 40.1f, 3.6f, 22.49f, 21.33f,
            8.45f, 35.47f, 0f, 58.58f, 0f, 96f,
            0f, 133.42f, 8.46f, 156.53f, 22.49f, 170.67f,
            40.1f, 188.4f, 66.49f, 192f, 96f, 192f,
            125.5f, 192f, 151.9f, 188.4f, 169.51f, 170.67f,
            183.54f, 156.53f, 192f, 133.42f, 192f, 96f,
            192f, 58.58f, 183.55f, 35.47f, 169.51f, 21.33f
    };

    private CompactShape() { }

    static void path(Path out, RectF bounds) {
        path(out, bounds, Math.min(bounds.width(), bounds.height()) / 2f, 1f);
    }

    static void icon(Path out, float left, float top, float size) {
        out.rewind();
        float scale = size / 192f;
        out.moveTo(left + 169.51f * scale, top + 21.33f * scale);
        for (int i = 0; i < ICON_CUBICS.length; i += 6) {
            out.cubicTo(left + ICON_CUBICS[i] * scale, top + ICON_CUBICS[i + 1] * scale,
                    left + ICON_CUBICS[i + 2] * scale, top + ICON_CUBICS[i + 3] * scale,
                    left + ICON_CUBICS[i + 4] * scale, top + ICON_CUBICS[i + 5] * scale);
        }
        out.close();
    }

    static float cardAmount(Card card, RectF bounds) {
        float compactWidth = card.leaving ? card.targetW : card.compactWidth;
        return cardAmount(bounds.width(), compactWidth, card.body.w);
    }

    static float cardAmount(float width, float compactWidth, float cardWidth) {
        if (compactWidth <= 0f || cardWidth <= compactWidth) return 0f;
        return Easing.clamp01((cardWidth - width) / (cardWidth - compactWidth));
    }

    static void path(Path out, RectF bounds, float maxRadius, float amount) {
        out.rewind();
        if (bounds.isEmpty()) return;
        float radius = Math.min(maxRadius, Math.min(bounds.width(), bounds.height()) / 2f);
        if (amount == 0f || radius <= 0f) {
            out.addRoundRect(bounds, radius, radius, Path.Direction.CW);
            return;
        }
        float left = bounds.left, top = bounds.top, right = bounds.right, bottom = bounds.bottom;
        out.moveTo(left + radius, top);
        out.lineTo(right - radius, top);
        corner(out, right, top, -radius, radius, amount, false);
        out.lineTo(right, bottom - radius);
        corner(out, right, bottom, -radius, -radius, amount, true);
        out.lineTo(left + radius, bottom);
        corner(out, left, bottom, radius, -radius, amount, false);
        out.lineTo(left, top + radius);
        corner(out, left, top, radius, radius, amount, true);
        out.close();
    }

    private static void corner(Path out, float x, float y, float sx, float sy, float amount, boolean reverse) {
        int step = reverse ? -2 : 2;
        int point = reverse ? 10 : 2;
        for (int segment = 0; segment < 2; segment++, point += step * 3) {
            int second = point + step;
            int end = second + step;
            out.cubicTo(x + sx * coordinate(point, amount), y + sy * coordinate(point + 1, amount),
                    x + sx * coordinate(second, amount), y + sy * coordinate(second + 1, amount),
                    x + sx * coordinate(end, amount), y + sy * coordinate(end + 1, amount));
        }
    }

    private static float coordinate(int index, float amount) {
        return Easing.lerp(CIRCLE[index], SQUIRCLE[index], amount);
    }
}
