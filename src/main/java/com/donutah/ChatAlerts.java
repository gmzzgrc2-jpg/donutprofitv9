package com.donutah;

import com.donutah.config.ModConfig;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

/** Avisos de chat: mencion de tu nombre y palabras clave propias (ventas, tpa, eventos...). */
public final class ChatAlerts {

    private static long lastAlertMillis = 0L;

    private ChatAlerts() {}

    public static void register() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) handle(message);
        });
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                handle(message));
    }

    private static void handle(Text message) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.enabled || (!cfg.mentionAlert && !cfg.chatAlerts)) return;

        String clean = Formatting.strip(message.getString());
        if (clean == null || clean.isBlank()) return;
        String lower = clean.toLowerCase(Locale.ROOT);

        String reason = null;
        if (cfg.chatAlerts) {
            for (String kw : cfg.getParsedChatKeywords()) {
                if (lower.contains(kw)) {
                    reason = kw;
                    break;
                }
            }
        }
        if (reason == null && cfg.mentionAlert && isMention(lower)) {
            reason = "mención";
        }
        if (reason == null) return;

        final String shownReason = reason;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            long now = System.currentTimeMillis();
            if (now - lastAlertMillis < cfg.soundCooldownMs) return;
            lastAlertMillis = now;

            if (cfg.soundAlert) DonutAhMod.playChatSound();
            if (client.inGameHud != null) {
                client.inGameHud.setOverlayMessage(
                        Text.literal("🔔 DonutAH: " + shownReason).formatted(Formatting.GOLD, Formatting.BOLD),
                        false);
            }
        });
    }

    /** True si tu nombre aparece en el mensaje y no solo como autor de la linea. */
    private static boolean isMention(String lowerMessage) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getSession() == null) return false;
        String name = client.getSession().getUsername();
        if (name == null || name.length() < 3) return false;
        String lowerName = name.toLowerCase(Locale.ROOT);

        int first = lowerMessage.indexOf(lowerName);
        if (first < 0) return false;

        // Si el primer nombre es el prefijo del autor ("nombre » hola", "<nombre> hola"), exige otra aparicion.
        String after = lowerMessage.substring(first + lowerName.length()).stripLeading();
        boolean looksLikeSender = first <= 40 && !after.isEmpty()
                && ":»>|-".indexOf(after.charAt(0)) >= 0;
        if (!looksLikeSender) return true;

        return lowerMessage.indexOf(lowerName, first + lowerName.length()) >= 0;
    }
}
