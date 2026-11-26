package dev.android.dynamicisland.island.view;

import static dev.android.dynamicisland.island.view.IslandView.clamp01;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import dev.android.dynamicisland.island.feedback.AudioLevels;
import dev.android.dynamicisland.island.graphics.Colors;
import dev.android.dynamicisland.island.graphics.LiquidBridge;
import dev.android.dynamicisland.island.graphics.Metaball;
import dev.android.dynamicisland.island.model.IslandItem;
import dev.android.dynamicisland.settings.ModuleSettings;

/** Draws the compact pill, its side handles and the bridge to the first expanded card. */
final class PillPainter {
    private static final float LAYER_SLIDE_DP = 18.0f;
    /** How far the notch body reaches past the top edge of the screen, so no top edge or corner of it is seen. */
    private static final float NOTCH_OVERREACH_DP = 12.0f;
    private static final float SHADE_CONTENT_GONE_BY = 0.45f;
    private static final float SHADE_CONTENT_RANGE = 0.4f;

    private final Metrics m;
    private final Paints paints;
    private final Scene scene;
    private final Springs sp;
    private final Shapes shapes;
    private final CardBridge bridge;
    private final StackPainter stack;
    private final RectF bubble = new RectF();
    private final Path neck = new Path();
    private final Path bodyCutout = new Path();
    private final HeadlineHandles handles = new HeadlineHandles();
    private final Path notchContentClip = new Path();
    private final Path glowCutout = new Path();
    private final AmbientGlow glow, bubbleGlow;
    private final GlowMotion glowMotion = new GlowMotion();
    private final Tween glowPresence = new Tween(0, 1200);
    private IslandItem lastGlowSession;
    private float glowAlpha;
    private final RectF outlineRect = new RectF();
    private final RectF shrunk = new RectF();
    private final Path pillPath = new Path();
    private final Path rimPath = new Path();

    PillPainter(Metrics m, Paints paints, Scene scene, Shapes shapes, StackPainter stack, CardBridge bridge) {
        this.m = m;
        this.paints = paints;
        this.scene = scene;
        this.sp = scene.springs;
        this.shapes = shapes;
        this.bridge = bridge;
        this.stack = stack;
        glow = new AmbientGlow(m.dp(1));
        bubbleGlow = new AmbientGlow(m.dp(1));
    }

    /** Draws the compact row in the pill's rectangle for this frame. */
    void draw(Canvas canvas, RectF full, long t) {
        float gate = IslandView.clamp01(sp.shadeGate.value());
        shapes.shadePill(full, shrunk);
        RectF pill = shrunk;
        float contentScale = pill.width() / Math.max(1.0f, full.width());
        float contentAlpha = clamp01((gate - SHADE_CONTENT_GONE_BY) / SHADE_CONTENT_RANGE);
        float leftHandle = m.oneDesign ? shapes.handlePresence(-1) : 0f;
        float rightHandle = m.oneDesign ? shapes.handlePresence(1) : 0f;
        float outline = clamp01(sp.outlineFade.value()) * scene.pillOutlineOpacity();
        Director.ClassicPage page = scene.classicPage;
        if (page != null) {
            drawCompactGlow(canvas, pill, contentAlpha);
            page.draw(canvas, handles, pill, page.from, page.to, Scene.pillBodyColor(page.from.item),
                    Scene.pillBodyColor(page.to.item), contentAlpha, outline, contentAlpha);
            return;
        }
        float r = pill.height() / 2f;
        float split = m.oneDesign ? 0f : sp.split.value() * clamp01((gate - .55f) / .45f);
        boolean hasBubble = split > .003f && (scene.mode != Mode.EXPANDED || !ModuleSettings.legacyAnimation());
        float neckAlpha = 0f;
        bubble.setEmpty();
        if (hasBubble) {
            shapes.bubble(pill, split, bubble);
            neckAlpha = bubbleNeck(pill, r);
        }
        LiquidBridge cardBridge = bridge.update(t, pill, r);
        boolean pillVisible = pill.width() > IslandView.MIN_VISIBLE_SIZE && pill.height() > IslandView.MIN_VISIBLE_SIZE;
        // The notch shape holds when only the pill is on screen.
        boolean notch = pillVisible && !hasBubble && Math.max(leftHandle, rightHandle) <= .003f && cardBridge == null && gate >= 0.999f
                && (scene.mode == Mode.COMPACT || scene.restingShown()) && ModuleSettings.notchStyle();
        if (notch) {
            NotchShape.body(pillPath, pill, r, m.dp(NOTCH_OVERREACH_DP));
        } else {
            m.compactPath(pillPath, pill);
        }
        bodyCutout.rewind();
        if (pillVisible) bodyCutout.addPath(pillPath);
        if (hasBubble) bodyCutout.addOval(bubble, Path.Direction.CW);
        if (neckAlpha > 0f) bodyCutout.addPath(neck);

        // A returning compact body stays opaque; pillBlob only gates its content until the fold finishes.
        float bodyAlpha = scene.mode == Mode.COMPACT ? 1.0f : clamp01(sp.pillBlob.value());
        if (ModuleSettings.ambientGlow() && bodyAlpha > IslandView.MIN_VISIBLE_ALPHA) {
            int save = canvas.save();
            canvas.clipOutPath(bodyCutout);
            // Also prime state if Android draws before admitting the first animation frame.
            stepGlow(0, null);
            float alpha = bodyAlpha * clamp01(contentScale) * glowAlpha;
            if (pillVisible) glow.draw(canvas, pill, glowMotion, alpha, ModuleSettings.glowStrength());
            if (hasBubble) bubbleGlow.draw(canvas, bubble, glowMotion, alpha, ModuleSettings.glowStrength());
            canvas.restoreToCount(save);
        }
        boolean outlinePill = pillVisible && (scene.mode == Mode.COMPACT || scene.restingShown()) && bodyAlpha > IslandView.MIN_VISIBLE_ALPHA;
        if ((outlinePill || hasBubble) && outline > IslandView.MIN_VISIBLE_ALPHA) {
            drawOutline(canvas, pill, r, notch, outlinePill ? outline * bodyAlpha : 0f, outline, neckAlpha);
        }

        int mainColor = bodyColor(scene.mainIn);
        paints.shape.setColor(mainColor);
        paints.shape.setAlpha(Math.round(bodyAlpha * 255.0f));
        if (pillVisible) {
            canvas.drawPath(pillPath, paints.shape);
        }
        paints.shape.setColor(Colors.BLACK);
        if (cardBridge != null) {
            bridge.draw(canvas, cardBridge);
        }
        paints.shape.setAlpha(255);
        if (m.oneDesign) {
            handles.draw(canvas, pill, -1, leftHandle, outline, m);
            handles.draw(canvas, pill, 1, rightHandle, outline, m);
        } else if (hasBubble) {
            if (neckAlpha > 0f) {
                paints.shape.setColor(Colors.withAlpha(mainColor, neckAlpha));
                canvas.drawPath(neck, paints.shape);
            }
            paints.shape.setColor(bodyColor(scene.bubbleIn));
            canvas.drawOval(bubble, paints.shape);
            paints.shape.setColor(Colors.BLACK);
        }

        if (pillVisible && contentAlpha > IslandView.MIN_VISIBLE_ALPHA) {
            int contentSave = canvas.save();
            if (notch) {
                // Keep the notch content inside its ordinary layout below the top edge.
                float radius = full.height() / 2f;
                notchContentClip.rewind();
                notchContentClip.addRoundRect(full, radius, radius, Path.Direction.CW);
                canvas.clipPath(notchContentClip);
            } else {
                canvas.clipPath(pillPath);
            }
            if (Math.abs(contentScale - 1.0f) > 0.001f) {
                canvas.scale(contentScale, contentScale, full.centerX(), full.centerY());
            }
            drawPillContent(canvas, full, t, contentAlpha);
            canvas.restoreToCount(contentSave);
        }
        if (hasBubble && split > .55f) {
            float alpha = clamp01((split - .55f) / .4f) * contentAlpha;
            drawBubbleLayer(canvas, scene.bubbleOut, t, alpha);
            drawBubbleLayer(canvas, scene.bubbleIn, t, alpha);
        }
    }

    private float bubbleNeck(RectF pill, float pillRadius) {
        float bubbleRadius = bubble.width() / 2f;
        float pillX = pill.right - pillRadius;
        float distance = bubble.centerX() - pillX;
        float touching = pillRadius + bubbleRadius;
        float rest = pillRadius + m.bubbleGap + m.bubbleR;
        float spread = .55f * (1f - clamp01((distance - touching) / Math.max(1f, rest - touching - m.dp(.5f))));
        neck.rewind();
        if (spread <= .01f) return 0f;
        return stack.neckAlpha(Metaball.neck(pillX, pill.centerY(), pillRadius,
                bubble.centerX(), bubble.centerY(), bubbleRadius, spread, neck));
    }

    void drawCompactGlow(Canvas canvas,RectF bounds,float alpha) {
        if(!ModuleSettings.ambientGlow() || glowAlpha<.002f) return;
        int save=canvas.save();
        m.compactPath(glowCutout, bounds);
        canvas.clipOutPath(glowCutout);
        glow.draw(canvas,bounds,glowMotion,alpha*glowAlpha,ModuleSettings.glowStrength());
        canvas.restoreToCount(save);
    }

    boolean stepGlow(float dt, AudioLevels audio) {
        long now = IslandView.now();
        if (!ModuleSettings.ambientGlow()) {
            glowPresence.set(0, now - 1200); glowAlpha = 0; lastGlowSession = null;
            return false;
        }
        if (scene.mode != Mode.COMPACT && sp.pillBlob.value() <= IslandView.MIN_VISIBLE_ALPHA) return false;
        // the empty pill that stays on screen has nothing to glow about
        if (scene.restingShown() && Math.abs(sp.width.value() - m.idleWidth()) < m.dp(1.0f)) return false;
        IslandItem music = scene.musicForGlow();
        boolean show = music != null || !ModuleSettings.glowMusicOnly();
        glowPresence.set(show ? 1 : 0, now);
        glowAlpha = glowPresence.value(now);
        if (music != null) lastGlowSession = music;
        else if (!ModuleSettings.glowMusicOnly()) lastGlowSession = null;
        if (!show && glowAlpha <= 0) { lastGlowSession = null; return false; }
        // All compact bodies share this state, including pause and the last album colour on session end.
        boolean moving = glowMotion.step(dt, lastGlowSession, ModuleSettings.glowAlbum(),
                ModuleSettings.glowColour(), music == null ? null : audio);
        return glowPresence.running(now) || (glowAlpha > 0 && moving);
    }

    /** Paint the contrast rim outside the dark body. */
    private void drawOutline(Canvas canvas, RectF pill, float r, boolean notch, float alpha,
                             float bubbleOutline, float neckAlpha) {
        int save = canvas.save();
        canvas.clipOutPath(bodyCutout);
        Paint paint = paints.outline;
        paint.setColor(Colors.withAlpha(m.outlineColor(), alpha));
        if (notch && alpha > 0f) {
            outlineRect.set(pill);
            outlineRect.inset(-m.outlineW, -m.outlineW);
            NotchShape.body(rimPath, outlineRect, r + m.outlineW, m.dp(NOTCH_OVERREACH_DP));
            canvas.drawPath(rimPath, paint);
        } else if (alpha > 0f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * m.outlineW);
            canvas.drawPath(pillPath, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (!bubble.isEmpty()) {
            paint.setColor(Colors.withAlpha(m.outlineColor(), bubbleOutline));
            canvas.drawCircle(bubble.centerX(), bubble.centerY(), bubble.width() / 2f + m.outlineW, paint);
            if (neckAlpha > 0f) {
                float fade = ModuleSettings.legacyAnimation() ? neckAlpha : neckAlpha * neckAlpha;
                paint.setColor(Colors.withAlpha(m.outlineColor(), bubbleOutline * fade));
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                paint.setStrokeWidth(2f * m.outlineW);
                canvas.drawPath(neck, paint);
                paint.setStyle(Paint.Style.FILL);
            }
        }
        canvas.restoreToCount(save);
    }

    private void drawBubbleLayer(Canvas canvas, Layer layer, long t, float extraAlpha) {
        if (layer == null) {
            return;
        }
        float alpha = layer.alpha(t) * extraAlpha;
        if (alpha > IslandView.MIN_VISIBLE_ALPHA) {
            float merging = 1 - clamp01(sp.split.value());
            float blur = Math.max(layer.blurAmount(t), merging);
            Canvas content = layer.contentBlur.begin(canvas, bubble, blur, m.dp(ContentBlur.RADIUS_DP));
            try {
                layer.content.draw(content, bubble, alpha, layer.elapsedIn(t));
            } finally {
                layer.contentBlur.end(canvas, content);
            }
        }
    }

    private void drawPillContent(Canvas canvas, RectF pill, long t, float extraAlpha) {
        if (scene.mainOut != null) {
            drawLayer(canvas, scene.mainOut, pill, t, extraAlpha);
        }
        if (scene.mainIn != null && scene.mode != Mode.EXPANDED) {
            drawLayer(canvas, scene.mainIn, pill, t, extraAlpha);
        }
    }

    /** The pill takes its item's team colour while compact; otherwise (and while a card is out) it is black. */
    private int bodyColor(Layer layer) {
        return scene.pillBodyColor(layer);
    }

    private void drawLayer(Canvas canvas, Layer layer, RectF shape, long t, float extraAlpha) {
        // the content goes with the pill's body: a pill that is out of sight (the cards are folding back into it) shows nothing
        float alpha = layer.alpha(t) * (layer.handoff != null ? 1 : clamp01(sp.pillBlob.value())) * extraAlpha;
        if (alpha <= IslandView.MIN_VISIBLE_ALPHA) {
            return;
        }
        float scale = layer.scale(t);
        int save = canvas.save();
        canvas.translate(layer.offset(t) * m.dp(LAYER_SLIDE_DP), 0.0f);
        canvas.scale(scale, scale, shape.centerX(), shape.centerY());
        Canvas content = layer.contentBlur.begin(canvas, shape, layer.blurAmount(t), m.dp(ContentBlur.RADIUS_DP));
        try {
            layer.content.draw(content, shape, alpha, layer.elapsedIn(t));
        } finally {
            layer.contentBlur.end(canvas, content);
            canvas.restoreToCount(save);
        }
    }

}
