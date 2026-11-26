package dev.android.dynamicisland.island.view;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RectF;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.graphics.Easing;

/** Headline's side indicators and the contour used as they widen into a pill. */
final class HeadlineHandles {
    private static final float WIDTH = 5f / 32f;
    private static final float HEIGHT = 22f / 32f;
    private static final float GAP = 2f / 32f;
    private static final float BRACKET_ASPECT = 5f / 22f;
    private static final float BRACKET_EXTENT = 5.76611f / 6f;
    private static final int CONTOUR_SAMPLES = 64;
    // SecHeadlinePillKt.LeftSwipeIndicatorPath, with the right side reflected.
    private static final float[] CUBICS = {
            2.44332f, .137235f, 3.63872f, -.280736f, 4.63379f, .198679f,
            5.62882f, .678137f, 6.0468f, 1.87353f, 5.56738f, 2.8686f,
            4.55917f, 4.96127f, 3.99998f, 7.91946f, 4f, 11.0004f,
            4.00005f, 14.0815f, 4.55916f, 17.0399f, 5.56738f, 19.1323f,
            6.04687f, 20.1273f, 5.62886f, 21.3227f, 4.63379f, 21.8022f,
            3.63876f, 22.2815f, 2.44332f, 21.8636f, 1.96387f, 20.8686f,
            .617823f, 18.0751f, .0000538826f, 14.4762f, 0f, 11.0004f,
            -.0000238167f, 7.5247f, .617883f, 3.92599f, 1.96387f, 1.13227f
    };

    private final Path path = new Path();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    static float width(float pillHeight) { return WIDTH * pillHeight; }
    static float height(float pillHeight) { return HEIGHT * pillHeight; }
    static float gap(float pillHeight) { return GAP * pillHeight; }
    static float reach(float pillHeight) { return (GAP + WIDTH) * pillHeight; }

    static void bounds(RectF pill, int side, float presence, RectF out) {
        float width = width(pill.height()) * presence;
        float gap = gap(pill.height()) * presence;
        float height = height(pill.height()) * (.65f + .35f * presence);
        float left = side < 0 ? pill.left - gap - width : pill.right + gap;
        out.set(left, pill.centerY() - height / 2, left + width, pill.centerY() + height / 2);
    }

    void draw(Canvas canvas, RectF pill, int side, float presence, float outline, Metrics m) {
        draw(canvas, pill, side, presence, presence, outline, m);
    }

    void draw(Canvas canvas, RectF pill, int side, float presence, float alpha, float outline, Metrics m) {
        if (presence < .003f || alpha < .003f) return;
        bounds(pill, side, presence, bounds);
        // A custom full-width pill can leave room on only one side of the cutout.
        if (bounds.left < m.outlineW || bounds.right > m.screenW - m.outlineW) return;
        bracket(path, bounds, side);
        canvas.drawPath(path, fill(Colors.withAlpha(Colors.BLACK, alpha)));
        rim(canvas, path, m.outlineW * .55f, alpha * outline);
    }

    void drawSurface(Canvas canvas, RectF surface, int side, int color, float outline, Metrics m) {
        exchangePath(path, surface, side);
        canvas.drawPath(path, fill(color));
        float aspect = surface.width() / surface.height();
        float thickness = Easing.lerp(.55f, 1f, IslandView.clamp01((aspect - BRACKET_ASPECT) / (1f - BRACKET_ASPECT)));
        rim(canvas, path, m.outlineW * thickness, outline);
    }

    void clip(Canvas canvas) {
        canvas.clipPath(path);
    }

    private Paint fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        return paint;
    }

    private void rim(Canvas canvas, Path shape, float thickness, float opacity) {
        if (opacity < .003f) return;
        int save = canvas.save();
        canvas.clipOutPath(shape);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f * thickness);
        paint.setColor(Colors.withAlpha(Colors.PILL_OUTLINE, opacity));
        canvas.drawPath(shape, paint);
        canvas.restoreToCount(save);
    }

    static void exchangePath(Path out, RectF bounds, int side) {
        float aspect = bounds.width() / bounds.height();
        if (aspect >= 1f) {
            CompactShape.path(out, bounds);
        } else if (aspect <= BRACKET_ASPECT) {
            bracket(out, bounds, side);
        } else {
            float fraction = (aspect - BRACKET_ASPECT) / (1f - BRACKET_ASPECT);
            float progress = fraction * fraction * (3f - 2f * fraction);
            out.rewind();
            for (int i = 0; i <= CONTOUR_SAMPLES; i++) vertex(out, bounds, side, progress, i, false);
            for (int i = CONTOUR_SAMPLES - 1; i > 0; i--) vertex(out, bounds, side, progress, i, true);
            out.close();
        }
    }

    private static void bracket(Path out, RectF bounds, int side) {
        out.rewind();
        float start = side < 0 ? bounds.left : bounds.left + bounds.width() * BRACKET_EXTENT;
        float sx = bounds.width() / (side < 0 ? 6f : -6f);
        float sy = bounds.height() / 22f;
        out.moveTo(start + 1.96387f * sx, bounds.top + 1.13227f * sy);
        for (int i = 0; i < CUBICS.length; i += 6) {
            out.cubicTo(start + CUBICS[i] * sx, bounds.top + CUBICS[i + 1] * sy,
                    start + CUBICS[i + 2] * sx, bounds.top + CUBICS[i + 3] * sy,
                    start + CUBICS[i + 4] * sx, bounds.top + CUBICS[i + 5] * sy);
        }
        out.close();
    }

    /** Sample the normalized upper curves once, as in SecHeadlineExchangeShapeKt. */
    private static final class Contours {
        static final float[] CAPSULE = sample(false);
        static final float[] BRACKET = sample(true);
    }

    private static void vertex(Path out, RectF bounds, int side, float amount, int i, boolean lower) {
        int index = i * 2;
        float x = Easing.lerp(Contours.BRACKET[index], Contours.CAPSULE[index], amount);
        float y = Easing.lerp(Contours.BRACKET[index + 1], Contours.CAPSULE[index + 1], amount);
        if (side > 0) x = Easing.lerp(BRACKET_EXTENT, 1f, amount) - x;
        float px = bounds.left + x * bounds.width();
        float py = bounds.top + (lower ? 1f - y : y) * bounds.height();
        if (i == 0 && !lower) out.moveTo(px, py);
        else out.lineTo(px, py);
    }

    private static float[] sample(boolean bracket) {
        Path path = new Path();
        RectF unit = new RectF(0, 0, 1, 1);
        if (bracket) bracket(path, unit, -1);
        else CompactShape.path(path, unit);
        PathMeasure measure = new PathMeasure(path, true);
        float length = measure.getLength();
        float[] xy = new float[2];
        float start = midpoint(measure, length, xy, 0f);
        float end = midpoint(measure, length, xy, bracket ? 2f / 3f : 1f);
        float sweep = (end - start + length) % length;
        float[] points = new float[(CONTOUR_SAMPLES + 1) * 2];
        for (int i = 0; i <= CONTOUR_SAMPLES; i++) {
            measure.getPosTan((start + sweep * i / CONTOUR_SAMPLES) % length, xy, null);
            points[2 * i] = xy[0];
            points[2 * i + 1] = Math.max(0f, Math.min(.5f, xy[1]));
        }
        points[1] = .5f;
        points[points.length - 1] = .5f;
        return points;
    }

    private static float midpoint(PathMeasure measure, float length, float[] xy, float x) {
        float best = Float.POSITIVE_INFINITY;
        float at = 0f;
        for (int i = 0; i < 512; i++) {
            float distance = length * i / 512;
            measure.getPosTan(distance, xy, null);
            float dx = xy[0] - x;
            float dy = xy[1] - .5f;
            float score = dx * dx + dy * dy;
            if (score < best) {
                best = score;
                at = distance;
            }
        }
        return at;
    }
}
