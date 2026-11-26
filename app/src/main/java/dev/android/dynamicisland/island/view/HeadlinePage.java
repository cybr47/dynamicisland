package dev.android.dynamicisland.island.view;

import android.graphics.Canvas;
import android.graphics.RectF;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.graphics.Easing;
import dev.android.dynamicisland.island.graphics.Spring;

/** Exchanges the two compact surfaces under a horizontal drag and settles at either end. */
class HeadlinePage {
    private final Metrics metrics;
    final int direction;
    final RectF fromMain = new RectF();
    final RectF toMain = new RectF();
    final RectF main = new RectF();
    final Spring progress = new Spring(0, .30f, .90f).setValueThreshold(.001f);
    final Spring pull = new Spring(0, .36f, .78f).setValueThreshold(.015f);
    final Spring load = new Spring(0, .34f, .82f).setValueThreshold(.001f);
    private final RectF surface = new RectF();
    private final RectF contentBounds = new RectF();
    private final RectF exchangeBounds = new RectF();
    private boolean previousOutside, nextOutside;
    boolean released;
    boolean commit;

    HeadlinePage(Metrics metrics, int direction) {
        this.metrics = metrics;
        this.direction = direction;
    }

    void drag(float distance) {
        progress.snapTo(IslandView.clamp01(Math.abs(distance) / travel()));
        float tension = (float) Math.tanh(Math.abs(distance) / metrics.dp(65));
        pull.snapTo(-direction * metrics.dp(9) * tension);
        load.snapTo(tension);
        bounds();
    }

    void release(float velocity) {
        commit = Math.abs(velocity) > metrics.dp(600) ? velocity * direction < 0 : progress.value() > .24f;
        released = true;
        float speed = Math.max(-5, Math.min(5, -velocity * direction / travel()));
        progress.impulse(speed - progress.velocity());
        progress.animateTo(commit ? 1 : 0);
        pull.animateTo(0);
        load.animateTo(0);
    }

    void cancel() {
        released = true;
        commit = false;
        progress.animateTo(0);
        pull.animateTo(0);
        load.animateTo(0);
    }

    private float travel() {
        return Math.max(metrics.dp(70), fromMain.width());
    }

    boolean moving() {
        return !progress.isAtRest() || !pull.isAtRest() || !load.isAtRest();
    }

    boolean step(float dt) {
        boolean active = progress.step(dt) | pull.step(dt) | load.step(dt);
        bounds();
        return active;
    }

    void bounds() {
        interpolate(fromMain, toMain, fraction(), main);
        float tension = load.value();
        main.inset(-main.width() * .012f * tension, main.height() * .025f * tension);
        main.offset(pull.value(), -metrics.dp(1.2f) * tension);
    }

    float fraction() { return IslandView.clamp01(progress.value()); }

    /** Whether the queue extends beyond the exchanging pair on either side. */
    void outerHandles(boolean before, boolean after) {
        previousOutside = before;
        nextOutside = after;
    }

    float outerHandle(int side) {
        float p = fraction();
        if (side == direction) return nextOutside ? ease((p - .68f) / .32f) : 0;
        return previousOutside ? ease((.32f - p) / .32f) : 0;
    }

    private static float ease(float fraction) {
        float t = IslandView.clamp01(fraction);
        return t * t * (3f - 2f * t);
    }

    /** The part of the moving pair or its extra handle that extends past the main bounds. */
    void sideBounds(RectF pill, int side, RectF out) {
        float exchange = side == direction ? 1 - fraction() : fraction();
        float reach = HeadlineHandles.reach(pill.height()) * (exchange + outerHandle(side));
        if (reach < .005f) {
            out.setEmpty();
            return;
        }
        float edge = side < 0 ? pill.left : pill.right;
        out.set(side < 0 ? edge - reach : edge, pill.top,
                side < 0 ? edge : edge + reach, pill.bottom);
    }

    static void interpolate(RectF a, RectF b, float fraction, RectF out) {
        out.set(Easing.lerp(a.left, b.left, fraction), Easing.lerp(a.top, b.top, fraction),
                Easing.lerp(a.right, b.right, fraction), Easing.lerp(a.bottom, b.bottom, fraction));
    }

    void draw(Canvas canvas, HeadlineHandles handles, RectF pill, Content from, Content to,
              int fromColor, int toColor, float contentAlpha, float outline, float handleAlpha) {
        if (pill.isEmpty()) return;
        float p = fraction();
        // Start with the arriving bracket just outside the pill. As it grows, move the departing
        // surface into the opposite bracket so the camera stays inside the combined footprint.
        float handle = HeadlineHandles.width(pill.height());
        float gap = HeadlineHandles.gap(pill.height());
        float before = direction > 0 ? p : 1 - p;
        float after = 1 - before;
        float left = pill.left - (handle + gap) * before;
        float right = pill.right + (handle + gap) * after;
        float leftWidth = Easing.lerp(pill.width(), handle, before);
        float rightWidth = right - (left + leftWidth + gap);
        exchangeBounds.set(left, pill.top, right, pill.bottom);
        handles.draw(canvas, exchangeBounds, -direction, outerHandle(-direction) * handleAlpha, outline, metrics);
        handles.draw(canvas, exchangeBounds, direction, outerHandle(direction) * handleAlpha, outline, metrics);
        float fromAlpha = contentAlpha * fade(1 - p);
        float toAlpha = contentAlpha * fade(p);
        if (direction > 0) {
            drawSurface(canvas, handles, pill, left, leftWidth, -1, from, fromColor, 1 - p, fromAlpha, outline);
            drawSurface(canvas, handles, pill, right - rightWidth, rightWidth, 1, to, toColor, p, toAlpha, outline);
        } else {
            drawSurface(canvas, handles, pill, left, leftWidth, -1, to, toColor, p, toAlpha, outline);
            drawSurface(canvas, handles, pill, right - rightWidth, rightWidth, 1, from, fromColor, 1 - p, fromAlpha, outline);
        }
    }

    private static float fade(float presence) {
        return ease((presence - .58f) / .42f);
    }

    private void drawSurface(Canvas canvas, HeadlineHandles handles, RectF pill, float left, float width,
                             int side, Content content, int color, float presence, float alpha, float outline) {
        float pillHeight = pill.height();
        float handleWidth = HeadlineHandles.width(pillHeight);
        float amount = IslandView.clamp01((width - handleWidth) / (pillHeight - handleWidth));
        float height = Easing.lerp(HeadlineHandles.height(pillHeight), pillHeight, amount);
        surface.set(left, pill.centerY() - height / 2, left + width, pill.centerY() + height / 2);
        handles.drawSurface(canvas, surface, side, Colors.blend(Colors.BLACK, color, presence), outline, metrics);
        if (alpha < .003f) return;
        float naturalWidth = Math.max(width, content.desiredWidth());
        contentBounds.set(surface.centerX() - naturalWidth / 2, pill.centerY() - metrics.pillH / 2,
                surface.centerX() + naturalWidth / 2, pill.centerY() + metrics.pillH / 2);
        int save = canvas.save();
        handles.clip(canvas);
        float scale = Math.min(1f, Math.min(height / metrics.pillH, width / naturalWidth));
        canvas.scale(scale, scale, surface.centerX(), surface.centerY());
        content.draw(canvas, contentBounds, alpha, 1000);
        canvas.restoreToCount(save);
    }
}
