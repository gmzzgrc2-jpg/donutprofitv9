package com.donutah.ui.widgets;

import com.donutah.config.ModConfig;
import com.donutah.ui.AnimationUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

public class ModernActionButton extends ButtonWidget {

    private final BooleanSupplier selectedSupplier;
    private final boolean primaryAccent;
    private float hoverAnim;
    private float selectAnim;
    private long lastFrameNanos = System.nanoTime();

    public ModernActionButton(int x, int y, int width, int height, Text message,
                              boolean primaryAccent, BooleanSupplier selectedSupplier, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.primaryAccent = primaryAccent;
        this.selectedSupplier = selectedSupplier;
        this.selectAnim = (selectedSupplier != null && selectedSupplier.getAsBoolean()) ? 1.0f : 0.0f;
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;

        boolean selected = selectedSupplier != null && selectedSupplier.getAsBoolean();
        hoverAnim = AnimationUtil.expDecay(hoverAnim, this.isHovered() ? 1.0f : 0.0f, 16.0f, dt);
        selectAnim = AnimationUtil.expDecay(selectAnim, selected ? 1.0f : 0.0f, 16.0f, dt);

        int accent = ModConfig.get().accentColor;
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int baseRgb = primaryAccent ? accent : AnimationUtil.lerpColor(0x1B1F2B, accent, selectAnim * 0.25f);
        int bgRgb = AnimationUtil.lerpColor(baseRgb, AnimationUtil.brighten(baseRgb, 1.22f), hoverAnim);
        context.fill(x, y, x + w, y + h, AnimationUtil.withAlpha(bgRgb, primaryAccent ? 0.95f : 0.92f));

        int borderRgb = (primaryAccent || selected)
                ? AnimationUtil.brighten(accent, 1.15f)
                : AnimationUtil.lerpColor(0x31384B, accent, hoverAnim * 0.65f);
        int border = AnimationUtil.withAlpha(borderRgb, 0.90f);
        context.fill(x, y, x + w, y + 1, border);
        context.fill(x, y + h - 1, x + w, y + h, border);
        context.fill(x, y, x + 1, y + h, border);
        context.fill(x + w - 1, y, x + w, y + h, border);

        if (selectAnim > 0.05f && !primaryAccent) {
            int barW = Math.round((w - 12) * selectAnim);
            int barX = x + (w - barW) / 2;
            context.fill(barX, y + h - 2, barX + barW, y + h, AnimationUtil.withAlpha(accent, 1.0f));
        }

        var tr = MinecraftClient.getInstance().textRenderer;
        int textW = tr.getWidth(getMessage());
        int textX = x + (w - textW) / 2;
        int textY = y + (h - 8) / 2;
        int textColor = primaryAccent ? 0xFF0D1117 : (selected ? 0xFFFFFFFF : 0xFFD4DCEB);
        context.drawTextWithShadow(tr, getMessage(), textX, textY, textColor);
    }
}
