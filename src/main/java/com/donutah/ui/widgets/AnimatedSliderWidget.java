package com.donutah.ui.widgets;

import com.donutah.config.ModConfig;
import com.donutah.ui.AnimationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public class AnimatedSliderWidget extends SliderWidget {

    private final double min;
    private final double max;
    private final String title;
    private final DoubleFunction<String> formatter;
    private final DoubleConsumer onChange;

    private float visualValue;
    private float hoverAnim;
    private long lastFrameNanos = System.nanoTime();

    public AnimatedSliderWidget(int x, int y, int width, int height,
                                String title, double min, double max, double initialValue,
                                DoubleFunction<String> formatter, DoubleConsumer onChange) {
        super(x, y, width, height, Text.empty(), normalize(initialValue, min, max));
        this.title = title;
        this.min = min;
        this.max = max;
        this.formatter = formatter;
        this.onChange = onChange;
        this.visualValue = (float) this.value;
        updateMessage();
    }

    private static double normalize(double val, double min, double max) {
        if (max <= min) return 0.0;
        return Math.max(0.0, Math.min(1.0, (val - min) / (max - min)));
    }

    public void setActualValue(double realValue) {
        this.value = normalize(realValue, min, max);
        updateMessage();
    }

    public double getActualValue() {
        return min + (max - min) * this.value;
    }

    @Override
    protected void updateMessage() {
        setMessage(Text.literal(title + ": " + formatter.apply(getActualValue())));
    }

    @Override
    protected void applyValue() {
        onChange.accept(getActualValue());
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;

        visualValue = AnimationUtil.expDecay(visualValue, (float) this.value, 22.0f, dt);
        hoverAnim = AnimationUtil.expDecay(hoverAnim, this.isHovered() ? 1.0f : 0.0f, 14.0f, dt);

        int accent = ModConfig.get().accentColor;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int bgRgb = AnimationUtil.lerpColor(0x161922, 0x1E2330, hoverAnim);
        context.fill(x, y, x + w, y + h, AnimationUtil.withAlpha(bgRgb, 0.92f));

        int border = AnimationUtil.withAlpha(AnimationUtil.lerpColor(0x2E3446, accent, hoverAnim * 0.6f), 0.85f);
        context.fill(x, y, x + w, y + 1, border);
        context.fill(x, y + h - 1, x + w, y + h, border);
        context.fill(x, y, x + 1, y + h, border);
        context.fill(x + w - 1, y, x + w, y + h, border);

        var tr = MinecraftClient.getInstance().textRenderer;
        String valueStr = formatter.apply(getActualValue());
        context.drawTextWithShadow(tr, Text.literal(title), x + 10, y + 6, 0xFFD8E0EE);
        int valW = tr.getWidth(valueStr);
        context.drawTextWithShadow(tr, Text.literal(valueStr), x + w - valW - 10, y + 6,
                AnimationUtil.withAlpha(AnimationUtil.brighten(accent, 1.2f), 1.0f));

        int barX = x + 10;
        int barY = y + h - 9;
        int barW = w - 20;
        int barH = 4;

        context.fill(barX, barY, barX + barW, barY + barH, 0xFF282E3E);
        int filledW = Math.round(barW * visualValue);
        if (filledW > 0) {
            context.fill(barX, barY, barX + filledW, barY + barH, AnimationUtil.withAlpha(accent, 1.0f));
        }

        int thumbX = barX + filledW - 3;
        context.fill(thumbX, barY - 2, thumbX + 6, barY + barH + 2, 0xFFFFFFFF);
    }
}
