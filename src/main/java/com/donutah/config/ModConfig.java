package com.donutah.config;

import com.donutah.AuctionParser;
import com.donutah.DonutAhMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("donutah.json");
    private static final AtomicBoolean SAVE_QUEUED = new AtomicBoolean(false);
    private static final Object IO_LOCK = new Object();

    private static volatile ModConfig instance;

    public boolean enabled = true;
    public boolean enablePriceFilter = false;
    public double maxPriceThreshold = 100_000.0;
    public boolean compareUnitPrice = false;
    public String searchKeywords = "";
    public boolean requireBothFilters = false;
    /** Ademas del nombre, busca las palabras clave en el lore del item. */
    public boolean searchInLore = false;

    /** Titulos extra (separados por coma) que tambien se consideran "subasta". */
    public String extraScreenTitles = "";
    /** Fallback: analizar TODOS los contenedores (utiles si el titulo del /ah no es texto normal). */
    public boolean scanAllContainers = false;

    /** Modo venta: resalta precios MAYORES o iguales al umbral (util en /orders o al vender). */
    public boolean invertPriceFilter = false;

    // --- Mercado (aprende el precio normal de cada item) ---
    public boolean trackPrices = true;
    public boolean showMarketComparison = true;
    public boolean enableDealFilter = false;
    /** Una oferta es "ganga" si su precio unitario es al menos este % mas barato que la mediana. */
    public int dealDiscountPercent = 30;

    // --- Alertas de chat ---
    public boolean mentionAlert = true;
    public boolean chatAlerts = false;
    public String chatAlertKeywords = "";

    public HighlightStyle highlightStyle = HighlightStyle.CYBER_SHIMMER;
    public int accentColor = 0x00F57A;
    public float animationSpeed = 1.0f;
    public boolean showTooltipBreakdown = true;
    public boolean soundAlert = true;
    public int soundCooldownMs = 2000;

    private transient String[] parsedKeywords = new String[0];
    private transient String lastKeywordRaw = null;
    private transient String[] parsedChatKeywords = new String[0];
    private transient String lastChatKeywordRaw = null;

    public enum HighlightStyle {
        CYBER_SHIMMER("Cyber Shimmer"),
        PULSE_AURA("Pulse Aura"),
        MINIMAL_FRAME("Minimal Frame");

        private final String displayName;

        HighlightStyle(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public HighlightStyle next() {
            HighlightStyle[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public static ModConfig get() {
        ModConfig local = instance;
        if (local == null) {
            synchronized (IO_LOCK) {
                local = instance;
                if (local == null) {
                    load();
                    local = instance;
                }
            }
        }
        return local;
    }

    public static void load() {
        ModConfig result = null;
        if (Files.exists(CONFIG_PATH)) {
            try {
                String json = Files.readString(CONFIG_PATH, StandardCharsets.UTF_8);
                result = GSON.fromJson(json, ModConfig.class);
            } catch (Exception e) {
                DonutAhMod.LOGGER.error("No se pudo leer donutah.json, restaurando valores por defecto.", e);
            }
        }

        boolean fresh = result == null;
        if (fresh) {
            result = new ModConfig();
        }
        result.sanitize();
        instance = result;
        if (fresh) {
            result.saveAsync();
        }
    }

    public void sanitize() {
        if (highlightStyle == null) highlightStyle = HighlightStyle.CYBER_SHIMMER;
        if (searchKeywords == null) searchKeywords = "";
        if (extraScreenTitles == null) extraScreenTitles = "";
        if (chatAlertKeywords == null) chatAlertKeywords = "";
        dealDiscountPercent = Math.max(5, Math.min(90, dealDiscountPercent));
        if (Double.isNaN(maxPriceThreshold) || maxPriceThreshold < 0) maxPriceThreshold = 0;
        if (Double.isInfinite(maxPriceThreshold)) maxPriceThreshold = 1e15;
        if (Float.isNaN(animationSpeed) || animationSpeed < 0.25f || animationSpeed > 3.0f) animationSpeed = 1.0f;
        if (soundCooldownMs < 250) soundCooldownMs = 250;
        accentColor = accentColor & 0x00FFFFFF;
        rebuildKeywordCache();
        AuctionParser.invalidateCache();
    }

    public void rebuildKeywordCache() {
        if (searchKeywords.equals(lastKeywordRaw)) return;
        lastKeywordRaw = searchKeywords;
        parsedKeywords = splitKeywords(searchKeywords);
    }

    public String[] getParsedKeywords() {
        if (!searchKeywords.equals(lastKeywordRaw)) {
            rebuildKeywordCache();
        }
        return parsedKeywords;
    }

    public String[] getParsedChatKeywords() {
        if (!chatAlertKeywords.equals(lastChatKeywordRaw)) {
            lastChatKeywordRaw = chatAlertKeywords;
            parsedChatKeywords = splitKeywords(chatAlertKeywords);
        }
        return parsedChatKeywords;
    }

    private static String[] splitKeywords(String raw) {
        if (raw == null || raw.isBlank()) return new String[0];
        String[] parts = raw.split(",");
        List<String> cleanList = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                cleanList.add(trimmed);
            }
        }
        return cleanList.toArray(new String[0]);
    }

    /** Guarda con debounce de 150 ms; varios cambios seguidos producen una sola escritura. */
    public void saveAsync() {
        sanitize();
        if (!SAVE_QUEUED.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                SAVE_QUEUED.set(false);
            }
            writeToDisk();
        });
    }

    private void writeToDisk() {
        synchronized (IO_LOCK) {
            try {
                Files.createDirectories(CONFIG_PATH.getParent());
                Path tempFile = CONFIG_PATH.resolveSibling("donutah.json.tmp");
                Files.writeString(tempFile, GSON.toJson(this), StandardCharsets.UTF_8);
                try {
                    Files.move(tempFile, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tempFile, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                DonutAhMod.LOGGER.error("Error guardando configuración de DonutAH", e);
            }
        }
    }
}
