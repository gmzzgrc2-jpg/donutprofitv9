package com.donutah.ui;

import com.donutah.config.ModConfig;
import net.minecraft.client.gui.DrawContext;

public final class SlotHighlightRenderer {

    private static final long START_NANOS = System.nanoTime();

    private SlotHighlightRenderer() {}

    public static void renderAnimatedHighlight(DrawContext context, int x, int y, ModConfig cfg) {
        double elapsedSec = ((System.nanoTime() - START_NANOS) / 1_000_000_000.0) * cfg.animationSpeed;
        int rgb = cfg.accentColor;

        switch (cfg.highlightStyle) {
            case CYBER_SHIMMER -> renderCyberShimmer(context, x, y, rgb, elapsedSec);
            case PULSE_AURA -> renderPulseAura(context, x, y, rgb, elapsedSec);
            case MINIMAL_FRAME -> renderMinimalFrame(context, x, y, rgb, elapsedSec);
        }
    }

    private static void renderCyberShimmer(DrawContext context, int x, int y, int rgb, double t) {
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4.2);

        int topFill = AnimationUtil.withAlpha(rgb, 0.14f + 0.08f * pulse);
        int bottomFill = AnimationUtil.withAlpha(rgb, 0.28f + 0.10f * pulse);
        context.fillGradient(x, y, x + 16, y + 16, topFill, bottomFill);

        int borderCol = AnimationUtil.withAlpha(rgb, 0.75f);
        drawOutline(context, x, y, 16, 16, borderCol);

        float perimeterPos = (float) ((t * 42.0) % 60.0);
        int brightRgb = AnimationUtil.brighten(rgb, 1.45f);
        int sparkCore = AnimationUtil.withAlpha(0xFFFFFF, 0.95f);
        int sparkGlow = AnimationUtil.withAlpha(brightRgb, 0.70f);

        drawPerimeterSpark(context, x, y, perimeterPos, sparkGlow, 3);
        drawPerimeterSpark(context, x, y, perimeterPos, sparkCore, 1);

        drawCornerBrackets(context, x, y, 16, 16, 3, AnimationUtil.withAlpha(brightRgb, 0.95f));
    }

    private static void renderPulseAura(DrawContext context, int x, int y, int rgb, double t) {
        float wave = 0.5f + 0.5f * (float) Math.sin(t * 3.6);
        int fill = AnimationUtil.withAlpha(rgb, 0.18f + 0.24f * wave);
        context.fill(x, y, x + 16, y + 16, fill);

        int border = AnimationUtil.withAlpha(AnimationUtil.brighten(rgb, 1.25f), 0.55f + 0.45f * wave);
        drawOutline(context, x, y, 16, 16, border);
    }

    private static void renderMinimalFrame(DrawContext context, int x, int y, int rgb, double t) {
        float wave = 0.5f + 0.5f * (float) Math.sin(t * 3.0);
        int cornerCol = AnimationUtil.withAlpha(AnimationUtil.brighten(rgb, 1.2f), 0.75f + 0.25f * wave);
        drawCornerBrackets(context, x, y, 16, 16, 4, cornerCol);
    }

    private static void drawOutline(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y + 1, x + 1, y + h - 1, color);
        ctx.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void drawCornerBrackets(DrawContext ctx, int x, int y, int w, int h, int len, int color) {
        ctx.fill(x, y, x + len, y + 1, color);
        ctx.fill(x, y, x + 1, y + len, color);
        ctx.fill(x + w - len, y, x + w, y + 1, color);
        ctx.fill(x + w - 1, y, x + w, y + len, color);
        ctx.fill(x, y + h - 1, x + len, y + h, color);
        ctx.fill(x, y + h - len, x + 1, y + h, color);
        ctx.fill(x + w - len, y + h - 1, x + w, y + h, color);
        ctx.fill(x + w - 1, y + h - len, x + w, y + h, color);
    }

    private static void drawPerimeterSpark(DrawContext ctx, int x, int y, float pos, int color, int size) {
        for (int i = 0; i < size; i++) {
            float p = (pos + i) % 60.0f;
            int px;
            int py;
            if (p < 15.0f) {
                px = x + (int) p;
                py = y;
            } else if (p < 30.0f) {
                px = x + 15;
                py = y + (int) (p - 15.0f);
            } else if (p < 45.0f) {
                px = x + 15 - (int) (p - 30.0f);
                py = y + 15;
            } else {
                px = x;
                py = y + 15 - (int) (p - 45.0f);
            }
            ctx.fill(px, py, px + 1, py + 1, color);
        }
    }
}
