package com.donutah;

import com.donutah.config.ModConfig;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;
import java.util.regex.Pattern;

/** Decide si una pantalla es la subasta (/ah). Compartido por el mixin y el tooltip. */
public final class AuctionScreenDetector {

    // "ah" solo como palabra suelta (antes coincidia con cualquier titulo que contuviera "ah").
    private static final Pattern AH_WORD = Pattern.compile("(?<![a-z0-9])ah(?![a-z0-9])");

    private AuctionScreenDetector() {}

    public static boolean isAuctionScreen(Screen screen) {
        return screen instanceof HandledScreen<?> && isAuctionTitle(screen.getTitle());
    }

    public static boolean isAuctionTitle(Text title) {
        ModConfig cfg = ModConfig.get();
        if (cfg.scanAllContainers) return true;
        if (title == null) return false;

        String raw = Formatting.strip(title.getString());
        if (raw == null) return false;
        String lower = raw.toLowerCase(Locale.ROOT);

        if (lower.contains("auction") || lower.contains("subasta") || AH_WORD.matcher(lower).find()) {
            return true;
        }

        if (!cfg.extraScreenTitles.isBlank()) {
            for (String part : cfg.extraScreenTitles.split(",")) {
                String kw = part.trim().toLowerCase(Locale.ROOT);
                if (!kw.isEmpty() && lower.contains(kw)) return true;
            }
        }
        return false;
    }
}
