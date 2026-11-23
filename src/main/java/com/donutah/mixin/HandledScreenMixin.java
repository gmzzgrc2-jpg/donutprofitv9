package com.donutah.mixin;

import com.donutah.AuctionParser;
import com.donutah.AuctionScreenDetector;
import com.donutah.DonutAhMod;
import com.donutah.PriceTracker;
import com.donutah.config.ModConfig;
import com.donutah.ui.SlotHighlightRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    @Unique
    private Boolean donutah$isAuctionScreen = null;

    /** Stacks que ya dispararon la alerta sonora en esta pantalla (por identidad). */
    @Unique
    private final Set<ItemStack> donutah$alerted = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Stacks ya registrados en el historial de precios en esta pantalla. */
    @Unique
    private final Set<ItemStack> donutah$observed = Collections.newSetFromMap(new IdentityHashMap<>());

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    // En 1.21.11 la firma es drawSlot(DrawContext, Slot, int mouseX, int mouseY).
    @Inject(
            method = "drawSlot(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/screen/slot/Slot;II)V",
            at = @At("TAIL")
    )
    private void donutah$onDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled || !slot.hasStack()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || slot.inventory == client.player.getInventory()) {
            return;
        }

        if (donutah$isAuctionScreen == null) {
            donutah$isAuctionScreen = AuctionScreenDetector.isAuctionTitle(this.getTitle());
            DonutAhMod.debugScreenTitle(this.getTitle(), donutah$isAuctionScreen);
        }
        if (!donutah$isAuctionScreen) {
            return;
        }

        ItemStack stack = slot.getStack();
        AuctionParser.CachedAuctionInfo info = AuctionParser.analyze(stack);

        // Alimenta el historial de precios (una vez por oferta) para calcular el "precio normal".
        if (cfg.trackPrices && info.hasPrice()) {
            if (donutah$observed.size() > 512) {
                donutah$observed.clear();
            }
            if (donutah$observed.add(stack)) {
                PriceTracker.observe(stack, info.totalPrice(), info.unitPrice(), cfg.dealDiscountPercent);
            }
        }

        if (!info.matchesFilter()) {
            return;
        }

        SlotHighlightRenderer.renderAnimatedHighlight(context, slot.x, slot.y, cfg);

        // Sonido solo la primera vez que aparece cada oferta (antes sonaba cada 2 s mientras estuviera visible).
        if (donutah$alerted.size() > 256) {
            donutah$alerted.clear();
        }
        if (donutah$alerted.add(stack)) {
            DonutAhMod.triggerAlertSound();
        }
    }
}
