package com.donutah.ui.widgets;

import com.donutah.config.ModConfig;
import com.donutah.ui.AnimationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

public class AnimatedToggleButton extends ButtonWidget {

    private final BooleanSupplier stateSupplier;
    private final String label;
    private final String subtitle;
    private float thumbProgress;
    private float hoverProgress;
    private long lastFrameNanos = System.nanoTime();

    public AnimatedToggleButton(int x, int y, int width, int height,
                                String label, String subtitle,
                                BooleanSupplier stateSupplier, PressAction onPress) {
        super(x, y, width, height, Text.literal(label), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.label = label;
        this.subtitle = subtitle;
        this.stateSupplier = stateSupplier;
        this.thumbProgress = stateSupplier.getAsBoolean() ? 1.0f : 0.0f;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;

        boolean active = stateSupplier.getAsBoolean();
        thumbProgress = AnimationUtil.expDecay(thumbProgress, active ? 1.0f : 0.0f, 16.0f, dt);
        hoverProgress = AnimationUtil.expDecay(hoverProgress, this.isHovered() ? 1.0f : 0.0f, 14.0f, dt);

        int accent = ModConfig.get().accentColor;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int cardBg = AnimationUtil.lerpColor(0x161922, 0x1E2330, hoverProgress);
        context.fill(x, y, x + w, y + h, AnimationUtil.withAlpha(cardBg, 0.92f));

        int borderColor = active
                ? AnimationUtil.withAlpha(accent, 0.35f + 0.25f * hoverProgress)
                : AnimationUtil.withAlpha(0x2E3446, 0.80f);
        context.fill(x, y, x + w, y + 1, borderColor);
        context.fill(x, y + h - 1, x + w, y + h, borderColor);
        context.fill(x, y, x + 1, y + h, borderColor);
        context.fill(x + w - 1, y, x + w, y + h, borderColor);

        var tr = MinecraftClient.getInstance().textRenderer;
        boolean showSubtitle = !subtitle.isEmpty() && h >= 28;
        int textY = showSubtitle ? y + 6 : y + (h - 8) / 2;
        context.drawTextWithShadow(tr, Text.literal(label), x + 10, textY, 0xFFF3F6FC);
        if (showSubtitle) {
            context.drawText(tr, Text.literal(subtitle), x + 10, y + 18, 0xFF8A94A6, false);
        }

        int trackW = 32;
        int trackH = 16;
        int trackX = x + w - trackW - 10;
        int trackY = y + (h - trackH) / 2;

        int trackRgb = AnimationUtil.lerpColor(0x2B3142, accent, thumbProgress);
        context.fill(trackX, trackY, trackX + trackW, trackY + trackH, AnimationUtil.withAlpha(trackRgb, 1.0f));

        int thumbSize = 12;
        int minThumbX = trackX + 2;
        int maxThumbX = trackX + trackW - thumbSize - 2;
        int thumbX = Math.round(minThumbX + (maxThumbX - minThumbX) * thumbProgress);
        int thumbY = trackY + 2;

        context.fill(thumbX, thumbY, thumbX + thumbSize, thumbY + thumbSize, 0xFFFFFFFF);
    }
}
