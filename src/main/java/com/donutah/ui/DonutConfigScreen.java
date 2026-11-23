package com.donutah.ui;

import com.donutah.AuctionParser;
import com.donutah.DonutAhMod;
import com.donutah.PriceTracker;
import com.donutah.config.ModConfig;
import com.donutah.ui.widgets.AnimatedSliderWidget;
import com.donutah.ui.widgets.AnimatedToggleButton;
import com.donutah.ui.widgets.ColorSwatchButton;
import com.donutah.ui.widgets.ModernActionButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public class DonutConfigScreen extends Screen {

    private enum Tab {
        FILTERS("Filtros"),
        MARKET("Mercado"),
        VISUALS("Diseño"),
        EXTRAS("Extras"),
        PREVIEW("Previa");

        final String title;
        Tab(String title) { this.title = title; }
    }

    private static final int[] COLOR_PRESETS = {
            0x00F57A, // Donut Emerald
            0xFFB800, // Gold Rush
            0x00E5FF, // Electric Cyan
            0xB84DFF, // Hyper Violet
            0xFF3B5C, // Crimson Alert
            0xF0F4F8  // Arctic White
    };

    private static final String[] COLOR_NAMES = {
            "Emerald", "Gold", "Cyan", "Violet", "Crimson", "White"
    };

    private static final double PREVIEW_TOTAL_PRICE = 1_200_000.0;
    private static final int PREVIEW_COUNT = 16;

    private final Screen parent;
    private Tab activeTab = Tab.FILTERS;

    private float openProgress = 0.0f;
    private long lastFrameNanos = System.nanoTime();

    private TextFieldWidget maxPriceInput;
    private TextFieldWidget keywordInput;
    private AnimatedSliderWidget priceSlider;

    // Posiciones calculadas al construir cada pestaña y usadas al dibujar.
    private int keywordLabelY;
    private int colorLabelY;
    private int marketInfoY;
    private int chatLabelY;
    private int titlesLabelY;
    private int previewPanelH;
    private int previewLineH;

    private final ItemStack previewStack = new ItemStack(Items.NETHERITE_INGOT, PREVIEW_COUNT);

    public DonutConfigScreen(Screen parent) {
        super(Text.literal("DonutSMP AH Helper"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        rebuildTabWidgets();
    }

    private void switchTab(Tab next) {
        if (this.activeTab == next) return;
        this.activeTab = next;
        this.clearChildren();
        rebuildTabWidgets();
    }

    // ---------------------------------------------------------------- layout

    private int getModalWidth() {
        return Math.min(460, this.width - 20);
    }

    private int getModalHeight() {
        return Math.min(300, this.height - 8);
    }

    private int getModalX() {
        return (this.width - getModalWidth()) / 2;
    }

    private int getModalY() {
        return (this.height - getModalHeight()) / 2;
    }

    /** En ventanas bajas (GUI scale alto) se reducen alturas y separaciones. */
    private boolean isCompact() {
        return getModalHeight() < 262;
    }

    private int contentTop() {
        return getModalY() + 60;
    }

    private void rebuildTabWidgets() {
        maxPriceInput = null;
        keywordInput = null;
        priceSlider = null;

        ModConfig cfg = ModConfig.get();
        int mx = getModalX();
        int my = getModalY();
        int mw = getModalWidth();
        int mh = getModalHeight();

        int tabCount = Tab.values().length;
        int tabGap = 6;
        int totalTabW = mw - 28;
        int tabW = (totalTabW - tabGap * (tabCount - 1)) / tabCount;
        int tabY = my + 28;

        for (int i = 0; i < tabCount; i++) {
            final Tab tab = Tab.values()[i];
            int tx = mx + 14 + i * (tabW + tabGap);
            addDrawableChild(new ModernActionButton(
                    tx, tabY, tabW, 20,
                    Text.literal(tab.title),
                    false,
                    () -> activeTab == tab,
                    btn -> switchTab(tab)
            ));
        }

        int contentX = mx + 14;
        int contentY = contentTop();
        int contentW = mw - 28;

        switch (activeTab) {
            case FILTERS -> buildFiltersTab(cfg, contentX, contentY, contentW);
            case MARKET -> buildMarketTab(cfg, contentX, contentY, contentW);
            case VISUALS -> buildVisualsTab(cfg, contentX, contentY, contentW);
            case EXTRAS -> buildExtrasTab(cfg, contentX, contentY, contentW);
            case PREVIEW -> buildPreviewTab(cfg, contentX, contentY, contentW);
        }

        int footerY = my + mh - 26;
        addDrawableChild(new ModernActionButton(
                mx + mw - 104, footerY, 90, 20,
                Text.literal("Listo"),
                true,
                null,
                btn -> close()
        ));

        addDrawableChild(new ModernActionButton(
                mx + 14, footerY, 110, 20,
                Text.literal("Probar sonido"),
                false,
                null,
                btn -> DonutAhMod.playPreviewSound()
        ));
    }

    private void buildFiltersTab(ModConfig cfg, int x, int y, int w) {
        boolean compact = isCompact();
        int th = compact ? 24 : 30;
        int sh = compact ? 26 : 28;
        int g = compact ? 4 : 6;
        int halfW = (w - 8) / 2;
        int cy = y;

        // Fila 1: toggles principales
        addDrawableChild(new AnimatedToggleButton(
                x, cy, halfW, th,
                "Mod activo", "Activa/desactiva todo",
                () -> cfg.enabled,
                btn -> {
                    cfg.enabled = !cfg.enabled;
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new AnimatedToggleButton(
                x + halfW + 8, cy, halfW, th,
                "Filtro de precio", "Resalta <= precio máx",
                () -> cfg.enablePriceFilter,
                btn -> {
                    cfg.enablePriceFilter = !cfg.enablePriceFilter;
                    cfg.saveAsync();
                }
        ));
        cy += th + g;

        // Fila 2: precio máximo + presets
        maxPriceInput = new TextFieldWidget(this.textRenderer, x, cy, 130, 20, Text.literal("Precio máximo"));
        maxPriceInput.setMaxLength(20);
        maxPriceInput.setText(formatEditableNumber(cfg.maxPriceThreshold));
        maxPriceInput.setChangedListener(text -> {
            double parsed = AuctionParser.parseHumanNumber(text);
            if (parsed >= 0) {
                cfg.maxPriceThreshold = parsed;
                if (priceSlider != null && parsed <= 10_000_000.0) {
                    priceSlider.setActualValue(parsed);
                }
                cfg.saveAsync();
            }
        });
        addDrawableChild(maxPriceInput);

        double[] presets = {50_000, 250_000, 1_000_000, 10_000_000};
        String[] presetLabels = {"$50K", "$250K", "$1M", "$10M"};
        int chipStartX = x + 136;
        int chipGap = 4;
        int chipW = (w - 136 - chipGap * 3) / 4;
        for (int i = 0; i < presets.length; i++) {
            final double pVal = presets[i];
            addDrawableChild(new ModernActionButton(
                    chipStartX + i * (chipW + chipGap), cy, chipW, 20,
                    Text.literal(presetLabels[i]),
                    false,
                    () -> Math.abs(cfg.maxPriceThreshold - pVal) < 1.0,
                    btn -> {
                        cfg.enablePriceFilter = true;
                        cfg.maxPriceThreshold = pVal;
                        if (maxPriceInput != null) maxPriceInput.setText(formatEditableNumber(pVal));
                        if (priceSlider != null) priceSlider.setActualValue(pVal);
                        cfg.saveAsync();
                    }
            ));
        }
        cy += 20 + g;

        // Fila 3: slider
        priceSlider = new AnimatedSliderWidget(
                x, cy, w, sh,
                "Ajuste rápido de precio", 1_000.0, 10_000_000.0,
                Math.min(10_000_000.0, Math.max(1_000.0, cfg.maxPriceThreshold)),
                AuctionParser::formatMoney,
                val -> {
                    double rounded = Math.round(val / 1000.0) * 1000.0;
                    cfg.maxPriceThreshold = rounded;
                    if (maxPriceInput != null && !maxPriceInput.isFocused()) {
                        maxPriceInput.setText(formatEditableNumber(rounded));
                    }
                    cfg.saveAsync();
                }
        );
        addDrawableChild(priceSlider);
        cy += sh + g;

        // Fila 4: modos de comparación
        addDrawableChild(new AnimatedToggleButton(
                x, cy, halfW, th,
                "Comparar unitario", "Usa precio c/u en stacks",
                () -> cfg.compareUnitPrice,
                btn -> {
                    cfg.compareUnitPrice = !cfg.compareUnitPrice;
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new AnimatedToggleButton(
                x + halfW + 8, cy, halfW, th,
                "Exigir ambos (AND)", "Precio Y palabra clave",
                () -> cfg.requireBothFilters,
                btn -> {
                    cfg.requireBothFilters = !cfg.requireBothFilters;
                    cfg.saveAsync();
                }
        ));
        cy += th + g;

        // Fila 5: palabras clave
        keywordLabelY = cy;
        cy += 11;
        keywordInput = new TextFieldWidget(this.textRenderer, x, cy, w - 58, 20, Text.literal("Palabras clave"));
        keywordInput.setMaxLength(120);
        keywordInput.setSuggestion(cfg.searchKeywords.isEmpty() ? "elytra, spawner, beacon" : "");
        keywordInput.setText(cfg.searchKeywords);
        keywordInput.setChangedListener(text -> {
            cfg.searchKeywords = text;
            keywordInput.setSuggestion(text.isEmpty() ? "elytra, spawner, beacon" : "");
            cfg.saveAsync();
        });
        addDrawableChild(keywordInput);

        addDrawableChild(new ModernActionButton(
                x + w - 52, cy, 52, 20,
                Text.literal("Limpiar"),
                false,
                null,
                btn -> {
                    cfg.searchKeywords = "";
                    if (keywordInput != null) keywordInput.setText("");
                    cfg.saveAsync();
                }
        ));
    }

    private void buildMarketTab(ModConfig cfg, int x, int y, int w) {
        boolean compact = isCompact();
        int th = compact ? 24 : 30;
        int sh = compact ? 26 : 28;
        int g = compact ? 4 : 6;
        int halfW = (w - 8) / 2;
        int cy = y;

        addDrawableChild(new AnimatedToggleButton(
                x, cy, halfW, th,
                "Rastrear precios", "Aprende el precio normal",
                () -> cfg.trackPrices,
                btn -> {
                    cfg.trackPrices = !cfg.trackPrices;
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new AnimatedToggleButton(
                x + halfW + 8, cy, halfW, th,
                "Filtro de gangas", "Resalta bajo la mediana",
                () -> cfg.enableDealFilter,
                btn -> {
                    cfg.enableDealFilter = !cfg.enableDealFilter;
                    cfg.saveAsync();
                }
        ));
        cy += th + g;

        addDrawableChild(new AnimatedSliderWidget(
                x, cy, w, sh,
                "Descuento mínimo para ganga", 5.0, 80.0,
                Math.max(5.0, Math.min(80.0, cfg.dealDiscountPercent)),
                val -> Math.round(val) + "%",
                val -> {
                    cfg.dealDiscountPercent = (int) Math.round(val);
                    cfg.saveAsync();
                }
        ));
        cy += sh + g;

        addDrawableChild(new AnimatedToggleButton(
                x, cy, halfW, th,
                "Comparar con mercado", "Línea extra en tooltip",
                () -> cfg.showMarketComparison,
                btn -> {
                    cfg.showMarketComparison = !cfg.showMarketComparison;
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new ModernActionButton(
                x + halfW + 8, cy, halfW, th,
                Text.literal("Borrar historial"),
                false,
                null,
                btn -> PriceTracker.clear()
        ));
        cy += th + g;

        marketInfoY = cy;
    }

    private void buildExtrasTab(ModConfig cfg, int x, int y, int w) {
        boolean compact = isCompact();
        int th = compact ? 24 : 30;
        int g = compact ? 4 : 6;
        int halfW = (w - 8) / 2;
        int cy = y;

        addDrawableChild(new AnimatedToggleButton(
                x, cy, halfW, th,
                "Alerta de mención", "Suena si dicen tu nombre",
                () -> cfg.mentionAlert,
                btn -> {
                    cfg.mentionAlert = !cfg.mentionAlert;
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new AnimatedToggleButton(
                x + halfW + 8, cy, halfW, th,
                "Alertas por palabras", "Suena con tus palabras",
                () -> cfg.chatAlerts,
                btn -> {
                    cfg.chatAlerts = !cfg.chatAlerts;
                    cfg.saveAsync();
                }
        ));
        cy += th + g;

        chatLabelY = cy;
        cy += 11;
        final TextFieldWidget chatInput = new TextFieldWidget(this.textRenderer, x, cy, w, 20, Text.literal("Palabras de chat"));
        chatInput.setMaxLength(120);
        chatInput.setSuggestion(cfg.chatAlertKeywords.isEmpty() ? "sold, vendido, tpa" : "");
        chatInput.setText(cfg.chatAlertKeywords);
        chatInput.setChangedListener(text -> {
            cfg.chatAlertKeywords = text;
            chatInput.setSuggestion(text.isEmpty() ? "sold, vendido, tpa" : "");
            cfg.saveAsync();
        });
        addDrawableChild(chatInput);
        cy += 20 + g;

        addDrawableChild(new AnimatedToggleButton(
                x, cy, halfW, th,
                "Modo venta (>=)", "Resalta precios MAYORES",
                () -> cfg.invertPriceFilter,
                btn -> {
                    cfg.invertPriceFilter = !cfg.invertPriceFilter;
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new AnimatedToggleButton(
                x + halfW + 8, cy, halfW, th,
                "Analizar todo", "Cualquier contenedor",
                () -> cfg.scanAllContainers,
                btn -> {
                    cfg.scanAllContainers = !cfg.scanAllContainers;
                    cfg.saveAsync();
                }
        ));
        cy += th + g;

        titlesLabelY = cy;
        cy += 11;
        final TextFieldWidget titlesInput = new TextFieldWidget(this.textRenderer, x, cy, w, 20, Text.literal("Menus extra"));
        titlesInput.setMaxLength(120);
        titlesInput.setSuggestion(cfg.extraScreenTitles.isEmpty() ? "orders, shop" : "");
        titlesInput.setText(cfg.extraScreenTitles);
        titlesInput.setChangedListener(text -> {
            cfg.extraScreenTitles = text;
            titlesInput.setSuggestion(text.isEmpty() ? "orders, shop" : "");
            cfg.saveAsync();
        });
        addDrawableChild(titlesInput);
    }

    private void buildVisualsTab(ModConfig cfg, int x, int y, int w) {
        boolean compact = isCompact();
        int th = compact ? 24 : 30;
        int sh = compact ? 26 : 28;
        int g = compact ? 4 : 6;
        int halfW = (w - 8) / 2;
        int cy = y;

        addDrawableChild(new ModernActionButton(
                x, cy, halfW, sh,
                Text.literal("Estilo: " + cfg.highlightStyle.getDisplayName()),
                false,
                () -> true,
                btn -> {
                    cfg.highlightStyle = cfg.highlightStyle.next();
                    btn.setMessage(Text.literal("Estilo: " + cfg.highlightStyle.getDisplayName()));
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new AnimatedSliderWidget(
                x + halfW + 8, cy, halfW, sh,
                "Velocidad anim.", 0.5, 2.5, Math.max(0.5, Math.min(2.5, cfg.animationSpeed)),
                val -> String.format(Locale.US, "%.2fx", val),
                val -> {
                    cfg.animationSpeed = (float) val;
                    cfg.saveAsync();
                }
        ));
        cy += sh + g;

        colorLabelY = cy;
        cy += 11;
        int swatchSize = 22;
        int swatchGap = 10;
        for (int i = 0; i < COLOR_PRESETS.length; i++) {
            final int rgb = COLOR_PRESETS[i];
            addDrawableChild(new ColorSwatchButton(
                    x + 2 + i * (swatchSize + swatchGap), cy, swatchSize,
                    rgb, COLOR_NAMES[i],
                    btn -> {
                        cfg.accentColor = rgb;
                        cfg.saveAsync();
                    }
            ));
        }
        cy += swatchSize + g + 2;

        addDrawableChild(new AnimatedToggleButton(
                x, cy, halfW, th,
                "Precio unitario tooltip", "Muestra c/u al pasar ratón",
                () -> cfg.showTooltipBreakdown,
                btn -> {
                    cfg.showTooltipBreakdown = !cfg.showTooltipBreakdown;
                    cfg.saveAsync();
                }
        ));

        addDrawableChild(new AnimatedToggleButton(
                x + halfW + 8, cy, halfW, th,
                "Alerta sonora", "Sonido al detectar oferta",
                () -> cfg.soundAlert,
                btn -> {
                    cfg.soundAlert = !cfg.soundAlert;
                    cfg.saveAsync();
                }
        ));
        cy += th + g;

        addDrawableChild(new AnimatedSliderWidget(
                x, cy, w, sh,
                "Cooldown del sonido", 0.25, 10.0,
                Math.max(0.25, Math.min(10.0, cfg.soundCooldownMs / 1000.0)),
                val -> String.format(Locale.US, "%.2fs", val),
                val -> {
                    cfg.soundCooldownMs = (int) Math.round(val * 1000.0);
                    cfg.saveAsync();
                }
        ));
    }

    private void buildPreviewTab(ModConfig cfg, int x, int y, int w) {
        previewLineH = isCompact() ? 11 : 13;
        int tipH = 16 + 6 * previewLineH;
        previewPanelH = 24 + tipH + 8;

        addDrawableChild(new ModernActionButton(
                x, y + previewPanelH + 6, w, 20,
                Text.literal("Cambiar estilo de animación (" + cfg.highlightStyle.getDisplayName() + ")"),
                false,
                null,
                btn -> {
                    cfg.highlightStyle = cfg.highlightStyle.next();
                    btn.setMessage(Text.literal("Cambiar estilo de animación (" + cfg.highlightStyle.getDisplayName() + ")"));
                    cfg.saveAsync();
                }
        ));
    }

    // ---------------------------------------------------------------- render

    /**
     * Todo lo que va DETRAS de los widgets (fondo, panel, textos, vista previa) se dibuja aqui.
     * Asi el orden es correcto aunque la version de Screen llame a renderBackground antes o
     * despues de render(); antes el degradado del fondo se pintaba encima del panel.
     */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        openProgress = AnimationUtil.expDecay(openProgress, 1.0f, 16.0f, dt);

        int bgAlpha = Math.round(175 * openProgress);
        int topColor = (bgAlpha << 24) | 0x07090E;
        int bottomColor = (Math.min(220, bgAlpha + 30) << 24) | 0x0B0F19;
        context.fillGradient(0, 0, this.width, this.height, topColor, bottomColor);

        ModConfig cfg = ModConfig.get();
        int accent = cfg.accentColor;
        int mx = getModalX();
        int my = getModalY();
        int mw = getModalWidth();
        int mh = getModalHeight();

        context.fill(mx, my, mx + mw, my + mh, 0xF010131B);
        context.fill(mx, my, mx + mw, my + 2, AnimationUtil.withAlpha(accent, 1.0f));

        int outerBorder = AnimationUtil.withAlpha(0x283044, 0.95f);
        context.fill(mx, my + mh - 1, mx + mw, my + mh, outerBorder);
        context.fill(mx, my + 2, mx + 1, my + mh, outerBorder);
        context.fill(mx + mw - 1, my + 2, mx + mw, my + mh, outerBorder);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("DONUT").formatted(Formatting.WHITE, Formatting.BOLD)
                        .append(Text.literal("AH").withColor(accent))
                        .append(Text.literal("  •  Configuración").formatted(Formatting.GRAY)),
                mx + 14, my + 11, 0xFFFFFFFF);

        String statusBadge = cfg.enabled ? "● ACTIVO" : "○ PAUSADO";
        int badgeColor = cfg.enabled ? AnimationUtil.withAlpha(accent, 1.0f) : 0xFFEF4444;
        int badgeW = this.textRenderer.getWidth(statusBadge);
        context.drawTextWithShadow(this.textRenderer, Text.literal(statusBadge),
                mx + mw - badgeW - 14, my + 11, badgeColor);

        context.fill(mx + 14, my + 54, mx + mw - 14, my + 55, 0xFF1F2536);

        int contentX = mx + 14;
        int contentY = contentTop();
        int contentW = mw - 28;

        switch (activeTab) {
            case FILTERS -> context.drawText(this.textRenderer,
                    Text.literal("Palabras clave (separadas por coma):"),
                    contentX, keywordLabelY, 0xFF9AA5B8, false);
            case MARKET -> {
                context.drawText(this.textRenderer,
                        Text.literal("Items rastreados: " + PriceTracker.trackedItems()
                                + "  •  Muestras: " + PriceTracker.totalSamples()
                                + "  •  Gangas vistas: " + PriceTracker.dealsSeen()),
                        contentX, marketInfoY, 0xFFE2E8F0, false);
                context.drawText(this.textRenderer,
                        Text.literal("Navega el /ah: necesita 5+ ofertas de un item para fijar su mediana."),
                        contentX, marketInfoY + 12, 0xFF8A94A6, false);
            }
            case VISUALS -> context.drawText(this.textRenderer,
                    Text.literal("Color de acento y resaltado:"),
                    contentX, colorLabelY, 0xFF9AA5B8, false);
            case EXTRAS -> {
                context.drawText(this.textRenderer,
                        Text.literal("Palabras de alerta en el chat (separadas por coma):"),
                        contentX, chatLabelY, 0xFF9AA5B8, false);
                context.drawText(this.textRenderer,
                        Text.literal("Menús extra a analizar (parte del título, ej: orders):"),
                        contentX, titlesLabelY, 0xFF9AA5B8, false);
            }
            case PREVIEW -> renderLivePreviewPanel(context, contentX, contentY, contentW, cfg);
        }
    }

    private void renderLivePreviewPanel(DrawContext context, int x, int y, int w, ModConfig cfg) {
        context.fill(x, y, x + w, y + previewPanelH, 0xCC151924);
        context.fill(x, y, x + w, y + 1, 0xFF2A3246);
        context.fill(x, y + previewPanelH - 1, x + w, y + previewPanelH, 0xFF2A3246);

        context.drawTextWithShadow(this.textRenderer,
                Text.literal("Simulación en tiempo real (/ah)"),
                x + 12, y + 8, 0xFFE2E8F0);

        int slotX = x + 22;
        int slotY = y + 36;
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF373737);
        context.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);

        context.drawItem(previewStack, slotX, slotY);
        context.drawStackOverlay(this.textRenderer, previewStack, slotX, slotY);
        SlotHighlightRenderer.renderAnimatedHighlight(context, slotX, slotY, cfg);

        int tipX = slotX + 34;
        int tipY = y + 24;
        int tipW = w - 70;
        int tipH = 16 + 6 * previewLineH;
        context.fill(tipX, tipY, tipX + tipW, tipY + tipH, 0xF0100010);
        int tipBorder = AnimationUtil.withAlpha(0x5000FF, 0.75f);
        context.fill(tipX, tipY, tipX + tipW, tipY + 1, tipBorder);
        context.fill(tipX, tipY + tipH - 1, tipX + tipW, tipY + tipH, tipBorder);

        double unit = PREVIEW_TOTAL_PRICE / PREVIEW_COUNT;
        List<Text> mockLines = List.of(
                Text.literal("Netherite Ingot x" + PREVIEW_COUNT).formatted(Formatting.AQUA),
                Text.literal("Seller: DonutPlayer").formatted(Formatting.GRAY),
                Text.literal("Price: $1,200,000").formatted(Formatting.GREEN),
                Text.empty(),
                Text.literal("▎ ").formatted(Formatting.GOLD, Formatting.BOLD)
                        .append(Text.literal("DonutAH ").formatted(Formatting.WHITE, Formatting.BOLD))
                        .append(Text.literal("• Total: ").formatted(Formatting.DARK_GRAY))
                        .append(Text.literal(AuctionParser.formatMoney(PREVIEW_TOTAL_PRICE))
                                .formatted(Formatting.YELLOW, Formatting.BOLD)),
                Text.literal("▎ ").formatted(Formatting.GREEN, Formatting.BOLD)
                        .append(Text.literal("Unitario (" + PREVIEW_COUNT + "x): ").formatted(Formatting.GRAY))
                        .append(Text.literal(AuctionParser.formatMoney(unit) + " c/u")
                                .formatted(Formatting.GREEN, Formatting.BOLD))
        );

        for (int i = 0; i < mockLines.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, mockLines.get(i),
                    tipX + 8, tipY + 8 + i * previewLineH, 0xFFFFFFFF);
        }
    }

    private String formatEditableNumber(double val) {
        if (val == Math.floor(val)) {
            return String.format(Locale.US, "%.0f", val);
        }
        return String.format(Locale.US, "%.2f", val);
    }

    @Override
    public void close() {
        ModConfig.get().saveAsync();
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
