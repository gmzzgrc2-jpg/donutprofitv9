package com.donutah;

import com.donutah.config.ModConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public final class AuctionParser {

    private static final int MAX_CACHE_ENTRIES = 512;
    private static final Map<ItemStack, CachedAuctionInfo> STACK_CACHE = new IdentityHashMap<>(256);

    private AuctionParser() {}

    /**
     * @param dealRatio     precio unitario / mediana del mercado (0.7 = 30% mas barato), o -1 si no hay mediana aun
     * @param marketVersion version de PriceTracker con la que se calculo (para invalidar al llegar muestras nuevas)
     */
    public record CachedAuctionInfo(
            double totalPrice,
            double unitPrice,
            int cachedCount,
            boolean matchesFilter,
            double dealRatio,
            int marketVersion
    ) {
        public static final CachedAuctionInfo EMPTY = new CachedAuctionInfo(-1.0, -1.0, 0, false, -1.0, 0);

        public boolean hasPrice() {
            return totalPrice > 0.0;
        }
    }

    public static void invalidateCache() {
        STACK_CACHE.clear();
    }

    public static CachedAuctionInfo analyze(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return CachedAuctionInfo.EMPTY;
        }

        int currentCount = stack.getCount();
        CachedAuctionInfo cached = STACK_CACHE.get(stack);
        if (cached != null && (cached == CachedAuctionInfo.EMPTY
                || (cached.cachedCount() == currentCount && cached.marketVersion() == PriceTracker.version()))) {
            return cached;
        }

        if (STACK_CACHE.size() >= MAX_CACHE_ENTRIES) {
            STACK_CACHE.clear();
        }

        double totalPrice = extractRawPrice(stack);
        if (totalPrice <= 0.0) {
            STACK_CACHE.put(stack, CachedAuctionInfo.EMPTY);
            return CachedAuctionInfo.EMPTY;
        }

        double unitPrice = totalPrice / Math.max(1, currentCount);

        int marketVersion = PriceTracker.version();
        double median = PriceTracker.medianFor(PriceTracker.keyOf(stack));
        double dealRatio = median > 0.0 ? unitPrice / median : -1.0;

        boolean matches = evaluateMatch(stack, totalPrice, unitPrice, dealRatio);

        CachedAuctionInfo computed = new CachedAuctionInfo(
                totalPrice, unitPrice, currentCount, matches, dealRatio, marketVersion);
        STACK_CACHE.put(stack, computed);
        return computed;
    }

    private static boolean evaluateMatch(ItemStack stack, double totalPrice, double unitPrice, double dealRatio) {
        ModConfig cfg = ModConfig.get();
        String[] keywords = cfg.getParsedKeywords();

        boolean priceActive = cfg.enablePriceFilter;
        boolean keywordActive = keywords.length > 0;
        boolean dealActive = cfg.enableDealFilter;

        int activeCount = (priceActive ? 1 : 0) + (keywordActive ? 1 : 0) + (dealActive ? 1 : 0);
        if (activeCount == 0) {
            return false;
        }

        double evaluatedPrice = cfg.compareUnitPrice ? unitPrice : totalPrice;
        boolean priceMatch = priceActive
                && (cfg.invertPriceFilter
                    ? evaluatedPrice >= cfg.maxPriceThreshold
                    : evaluatedPrice <= cfg.maxPriceThreshold);
        boolean keywordMatch = keywordActive && matchesKeywords(stack, keywords, cfg.searchInLore);
        boolean dealMatch = dealActive && dealRatio > 0.0
                && dealRatio <= 1.0 - cfg.dealDiscountPercent / 100.0;

        int matched = (priceMatch ? 1 : 0) + (keywordMatch ? 1 : 0) + (dealMatch ? 1 : 0);
        return cfg.requireBothFilters ? matched == activeCount : matched > 0;
    }

    private static boolean matchesKeywords(ItemStack stack, String[] keywords, boolean searchLore) {
        String name = Formatting.strip(stack.getName().getString());
        String itemName = Formatting.strip(stack.getItem().getName().getString());

        for (String kw : keywords) {
            if (containsIgnoreCase(name, kw) || containsIgnoreCase(itemName, kw)) {
                return true;
            }
        }

        if (searchLore) {
            LoreComponent lore = stack.get(DataComponentTypes.LORE);
            if (lore != null) {
                for (Text line : lore.lines()) {
                    String clean = Formatting.strip(line.getString());
                    for (String kw : keywords) {
                        if (containsIgnoreCase(clean, kw)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String haystack, String lowerNeedle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    private static double extractRawPrice(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return -1.0;
        }

        double fallback = -1.0;
        for (Text line : lore.lines()) {
            String raw = line.getString();
            if (raw == null || raw.indexOf('$') < 0) continue;

            String clean = Formatting.strip(raw);
            if (clean == null) continue;

            double parsed = PriceParser.findPrice(clean);
            if (parsed <= 0.0) continue;

            // Una linea "Price: $..." gana siempre sobre otras lineas con "$".
            if (PriceParser.hasPriceLabel(clean)) {
                return parsed;
            }
            if (fallback < 0.0) {
                fallback = parsed;
            }
        }
        return fallback;
    }

    public static double parseHumanNumber(String input) {
        return PriceParser.parseHuman(input);
    }

    public static String formatMoney(double amount) {
        return PriceParser.formatMoney(amount);
    }
}
