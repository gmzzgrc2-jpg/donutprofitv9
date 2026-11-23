package com.donutah;

import com.donutah.config.ModConfig;
import com.donutah.ui.DonutConfigScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DonutAhMod implements ClientModInitializer {

    public static final String MOD_ID = "donutah";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static long lastSoundTimestamp = 0L;
    /** Modo diagnostico (no se guarda): muestra en el chat el titulo de cada contenedor que se abre. */
    private static boolean debugTitles = false;

    @Override
    public void onInitializeClient() {
        ModConfig.load();
        PriceTracker.load();
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> PriceTracker.saveNow());
        registerTooltip();
        registerCommands();
        ChatAlerts.register();
    }

    private void registerTooltip() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            ModConfig cfg = ModConfig.get();
            if (!cfg.enabled || !cfg.showTooltipBreakdown || stack.isEmpty()) return;

            // Solo dentro de la subasta (antes tambien salia en cualquier inventario con un "$" en el lore).
            MinecraftClient client = MinecraftClient.getInstance();
            if (!AuctionScreenDetector.isAuctionScreen(client.currentScreen)) return;

            AuctionParser.CachedAuctionInfo info = AuctionParser.analyze(stack);
            if (!info.hasPrice()) return;

            int count = stack.getCount();
            lines.add(Text.empty());
            lines.add(Text.literal("▎ ")
                    .formatted(Formatting.GOLD, Formatting.BOLD)
                    .append(Text.literal("DonutAH ").formatted(Formatting.WHITE, Formatting.BOLD))
                    .append(Text.literal("• Total: ").formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(AuctionParser.formatMoney(info.totalPrice()))
                            .formatted(Formatting.YELLOW, Formatting.BOLD)));

            if (count > 1) {
                lines.add(Text.literal("▎ ")
                        .formatted(Formatting.GREEN, Formatting.BOLD)
                        .append(Text.literal("Unitario (" + count + "x): ").formatted(Formatting.GRAY))
                        .append(Text.literal(AuctionParser.formatMoney(info.unitPrice()) + " c/u")
                                .formatted(Formatting.GREEN, Formatting.BOLD)));
            }

            if (cfg.showMarketComparison) {
                PriceTracker.Market market = PriceTracker.marketFor(stack);
                if (market != null && market.median() > 0.0) {
                    double diffPct = (info.unitPrice() / market.median() - 1.0) * 100.0;
                    Formatting tone = diffPct <= -5.0 ? Formatting.GREEN : (diffPct >= 5.0 ? Formatting.RED : Formatting.GRAY);
                    String sign = diffPct > 0 ? "+" : "";
                    lines.add(Text.literal("▎ ")
                            .formatted(Formatting.AQUA, Formatting.BOLD)
                            .append(Text.literal("Mercado: ").formatted(Formatting.GRAY))
                            .append(Text.literal(AuctionParser.formatMoney(market.median()) + " c/u ")
                                    .formatted(Formatting.WHITE))
                            .append(Text.literal(sign + Math.round(diffPct) + "%").formatted(tone, Formatting.BOLD))
                            .append(Text.literal(" (" + market.samples() + " muestras)").formatted(Formatting.DARK_GRAY)));
                } else if (cfg.trackPrices) {
                    lines.add(Text.literal("▎ Mercado: aprendiendo precio normal...").formatted(Formatting.DARK_GRAY));
                }
            }
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("ahhelper")
                .executes(ctx -> {
                    openConfigScreenNextTick();
                    return 1;
                })
                .then(ClientCommandManager.literal("config")
                    .executes(ctx -> {
                        openConfigScreenNextTick();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("toggle")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.enabled = !cfg.enabled;
                        cfg.saveAsync();
                        sendFeedback("Mod " + (cfg.enabled ? "ACTIVADO" : "DESACTIVADO"),
                                cfg.enabled ? Formatting.GREEN : Formatting.RED);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("maxprice")
                    .then(ClientCommandManager.argument("precio", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "precio").trim();
                            ModConfig cfg = ModConfig.get();
                            if (raw.equalsIgnoreCase("off")) {
                                cfg.enablePriceFilter = false;
                                cfg.saveAsync();
                                sendFeedback("Filtro de precio máximo desactivado.", Formatting.YELLOW);
                                return 1;
                            }
                            double parsed = AuctionParser.parseHumanNumber(raw);
                            if (parsed < 0) {
                                sendFeedback("Formato inválido. Usa ejemplos como: 50000, 250K, 1.5M", Formatting.RED);
                                return 0;
                            }
                            cfg.enablePriceFilter = true;
                            cfg.maxPriceThreshold = parsed;
                            cfg.saveAsync();
                            sendFeedback("Resaltando ofertas <= " + AuctionParser.formatMoney(parsed)
                                    + (cfg.compareUnitPrice ? " (por unidad)" : " (total)"), Formatting.GREEN);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("search")
                    .then(ClientCommandManager.argument("texto", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "texto").trim();
                            ModConfig cfg = ModConfig.get();
                            if (raw.equalsIgnoreCase("clear")) {
                                cfg.searchKeywords = "";
                                cfg.saveAsync();
                                sendFeedback("Filtro de búsqueda limpiado.", Formatting.YELLOW);
                            } else {
                                cfg.searchKeywords = raw;
                                cfg.saveAsync();
                                sendFeedback("Buscando: \"" + raw + "\"", Formatting.AQUA);
                            }
                            return 1;
                        })))
                .then(ClientCommandManager.literal("sound")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.soundAlert = !cfg.soundAlert;
                        cfg.saveAsync();
                        sendFeedback("Alerta sonora: " + (cfg.soundAlert ? "ON" : "OFF"), Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("unit")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.compareUnitPrice = !cfg.compareUnitPrice;
                        cfg.saveAsync();
                        sendFeedback("Comparar por: " + (cfg.compareUnitPrice ? "precio UNITARIO" : "precio TOTAL"),
                                Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("lore")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.searchInLore = !cfg.searchInLore;
                        cfg.saveAsync();
                        sendFeedback("Buscar palabras clave también en el lore: " + (cfg.searchInLore ? "ON" : "OFF"),
                                Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("scanall")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.scanAllContainers = !cfg.scanAllContainers;
                        cfg.saveAsync();
                        sendFeedback("Analizar todos los contenedores: " + (cfg.scanAllContainers ? "ON" : "OFF"),
                                Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("title")
                    .then(ClientCommandManager.argument("texto", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "texto").trim();
                            ModConfig cfg = ModConfig.get();
                            if (raw.equalsIgnoreCase("clear")) {
                                cfg.extraScreenTitles = "";
                                sendFeedback("Títulos extra limpiados.", Formatting.YELLOW);
                            } else {
                                cfg.extraScreenTitles = raw;
                                sendFeedback("Se tratará como subasta cualquier menú cuyo título contenga: " + raw,
                                        Formatting.AQUA);
                            }
                            cfg.saveAsync();
                            return 1;
                        })))
                .then(ClientCommandManager.literal("debug")
                    .executes(ctx -> {
                        debugTitles = !debugTitles;
                        sendFeedback("Debug de títulos: " + (debugTitles ? "ON (abre /ah para ver su título)" : "OFF"),
                                Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("market")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.trackPrices = !cfg.trackPrices;
                        cfg.saveAsync();
                        sendFeedback("Rastreo de precios de mercado: " + (cfg.trackPrices ? "ON" : "OFF"), Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("deals")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.enableDealFilter = !cfg.enableDealFilter;
                        cfg.saveAsync();
                        sendFeedback("Filtro de gangas (" + cfg.dealDiscountPercent + "% bajo la mediana): "
                                + (cfg.enableDealFilter ? "ON" : "OFF"), Formatting.GOLD);
                        return 1;
                    })
                    .then(ClientCommandManager.argument("porcentaje", IntegerArgumentType.integer(5, 90))
                        .executes(ctx -> {
                            ModConfig cfg = ModConfig.get();
                            cfg.dealDiscountPercent = IntegerArgumentType.getInteger(ctx, "porcentaje");
                            cfg.enableDealFilter = true;
                            cfg.saveAsync();
                            sendFeedback("Gangas: ofertas al menos " + cfg.dealDiscountPercent
                                    + "% más baratas que la mediana.", Formatting.GREEN);
                            return 1;
                        })))
                .then(ClientCommandManager.literal("stats")
                    .executes(ctx -> {
                        sendFeedback("Items rastreados: " + PriceTracker.trackedItems()
                                + " | Muestras: " + PriceTracker.totalSamples()
                                + " | Gangas vistas esta sesión: " + PriceTracker.dealsSeen(), Formatting.AQUA);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("clearprices")
                    .executes(ctx -> {
                        PriceTracker.clear();
                        sendFeedback("Historial de precios borrado.", Formatting.YELLOW);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("sell")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.invertPriceFilter = !cfg.invertPriceFilter;
                        cfg.saveAsync();
                        sendFeedback("Modo venta (resaltar precios MAYORES o iguales): "
                                + (cfg.invertPriceFilter ? "ON" : "OFF"), Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("mention")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        cfg.mentionAlert = !cfg.mentionAlert;
                        cfg.saveAsync();
                        sendFeedback("Alerta al mencionar tu nombre: " + (cfg.mentionAlert ? "ON" : "OFF"), Formatting.GOLD);
                        return 1;
                    }))
                .then(ClientCommandManager.literal("alerts")
                    .then(ClientCommandManager.argument("palabras", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String raw = StringArgumentType.getString(ctx, "palabras").trim();
                            ModConfig cfg = ModConfig.get();
                            if (raw.equalsIgnoreCase("clear") || raw.equalsIgnoreCase("off")) {
                                cfg.chatAlertKeywords = "";
                                cfg.chatAlerts = false;
                                sendFeedback("Alertas de chat por palabras desactivadas.", Formatting.YELLOW);
                            } else {
                                cfg.chatAlertKeywords = raw;
                                cfg.chatAlerts = true;
                                sendFeedback("Avisaré cuando el chat contenga: " + raw, Formatting.AQUA);
                            }
                            cfg.saveAsync();
                            return 1;
                        })))
                .then(ClientCommandManager.literal("status")
                    .executes(ctx -> {
                        ModConfig cfg = ModConfig.get();
                        sendFeedback("Mod: " + (cfg.enabled ? "ON" : "OFF")
                                + " | Precio máx: " + (cfg.enablePriceFilter
                                    ? AuctionParser.formatMoney(cfg.maxPriceThreshold) : "off")
                                + " (" + (cfg.compareUnitPrice ? "unitario" : "total") + ")"
                                + " | Palabras: " + (cfg.searchKeywords.isBlank() ? "-" : cfg.searchKeywords)
                                + " | Modo: " + (cfg.requireBothFilters ? "AND" : "OR")
                                + " | Gangas: " + (cfg.enableDealFilter ? cfg.dealDiscountPercent + "%" : "off")
                                + " | Venta: " + (cfg.invertPriceFilter ? "ON" : "OFF"), Formatting.GRAY);
                        return 1;
                    }))
            );
        });
    }

    private static void openConfigScreenNextTick() {
        MinecraftClient client = MinecraftClient.getInstance();
        // send() encola para el siguiente tick: si no, el chat (que se cierra tras el comando) taparia la pantalla.
        client.send(() -> client.setScreen(new DonutConfigScreen(client.currentScreen)));
    }

    public static void triggerAlertSound() {
        ModConfig cfg = ModConfig.get();
        if (!cfg.soundAlert) return;

        long now = System.currentTimeMillis();
        if (now - lastSoundTimestamp >= cfg.soundCooldownMs) {
            lastSoundTimestamp = now;
            playPreviewSound();
        }
    }

    public static void playPreviewSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.35f, 0.85f));
    }

    public static void playChatSound() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f, 0.7f));
    }

    public static void debugScreenTitle(Text title, boolean detected) {
        if (!debugTitles) return;
        sendFeedback("Menú abierto: \"" + title.getString() + "\" -> "
                + (detected ? "detectado como subasta" : "NO detectado (usa /ahhelper title <texto>)"),
                detected ? Formatting.GREEN : Formatting.RED);
    }

    private static void sendFeedback(String msg, Formatting color) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[DonutAH] ").formatted(Formatting.GOLD, Formatting.BOLD)
                    .append(Text.literal(msg).formatted(color)),
                false
            );
        }
    }
}
