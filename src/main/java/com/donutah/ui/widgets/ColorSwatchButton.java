package com.donutah.ui.widgets;

import com.donutah.config.ModConfig;
import com.donutah.ui.AnimationUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class ColorSwatchButton extends ButtonWidget {

    private final int swatchRgb;
    private float selectAnim;
    private float hoverAnim;
    private long lastFrameNanos = System.nanoTime();

    public ColorSwatchButton(int x, int y, int size, int swatchRgb, String tooltipName, PressAction onPress) {
        super(x, y, size, size, Text.literal(tooltipName), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.swatchRgb = swatchRgb & 0x00FFFFFF;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;

        boolean selected = (ModConfig.get().accentColor & 0x00FFFFFF) == swatchRgb;
        selectAnim = AnimationUtil.expDecay(selectAnim, selected ? 1.0f : 0.0f, 18.0f, dt);
        hoverAnim = AnimationUtil.expDecay(hoverAnim, this.isHovered() ? 1.0f : 0.0f, 16.0f, dt);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        if (selectAnim > 0.02f || hoverAnim > 0.02f) {
            float ringAlpha = Math.max(selectAnim, hoverAnim * 0.5f);
            int ringColor = AnimationUtil.withAlpha(0xFFFFFF, ringAlpha);
            context.fill(x - 2, y - 2, x + w + 2, y - 1, ringColor);
            context.fill(x - 2, y + h + 1, x + w + 2, y + h + 2, ringColor);
            context.fill(x - 2, y - 1, x - 1, y + h + 1, ringColor);
            context.fill(x + w + 1, y - 1, x + w + 2, y + h + 1, ringColor);
        }

        context.fill(x, y, x + w, y + h, AnimationUtil.withAlpha(swatchRgb, 1.0f));

        if (selectAnim > 0.2f) {
            int dotSize = Math.round(6 * selectAnim);
            int dx = x + (w - dotSize) / 2;
            int dy = y + (h - dotSize) / 2;
            context.fill(dx, dy, dx + dotSize, dy + dotSize, 0xFF0E1118);
        }
    }
}
