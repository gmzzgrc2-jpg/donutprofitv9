package com.donutah;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Aprende el "precio normal" (mediana del precio unitario) de cada item mientras navegas la subasta.
 * Se guarda en config/donutah_prices.json. Todo es local: no consulta nada al servidor.
 */
public final class PriceTracker {

    private static final int MAX_SAMPLES = 120;
    private static final int MIN_SAMPLES = 5;
    private static final int MAX_KEYS = 600;
    private static final int MAX_SIGNATURES = 4000;
    private static final int SAVE_EVERY_SAMPLES = 40;

    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("donutah_prices.json");
    private static final Gson GSON = new Gson();
    private static final Object LOCK = new Object();
    private static final AtomicBoolean SAVE_QUEUED = new AtomicBoolean(false);

    private static final Map<String, Series> SERIES = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Series> eldest) {
            return size() > MAX_KEYS;
        }
    };

    /** Ofertas ya contadas (para no duplicar muestras al reabrir la misma pagina). */
    private static final Map<String, Boolean> SIGNATURES = new LinkedHashMap<>(512, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > MAX_SIGNATURES;
        }
    };

    private static volatile int version = 0;
    private static int unsavedSamples = 0;
    private static int dealsSeenThisSession = 0;

    private PriceTracker() {}

    public record Market(double median, int samples) {}

    private static final class Series {
        final double[] values = new double[MAX_SAMPLES];
        int size = 0;
        int next = 0;
        double median = -1.0;
        boolean dirty = true;

        void add(double v) {
            values[next] = v;
            next = (next + 1) % MAX_SAMPLES;
            if (size < MAX_SAMPLES) size++;
            dirty = true;
        }

        double median() {
            if (size < MIN_SAMPLES) return -1.0;
            if (dirty) {
                double[] copy = Arrays.copyOf(values, size);
                Arrays.sort(copy);
                median = (size % 2 == 1) ? copy[size / 2] : (copy[size / 2 - 1] + copy[size / 2]) / 2.0;
                dirty = false;
            }
            return median;
        }

        /** Muestras de la mas antigua a la mas reciente. */
        double[] ordered() {
            double[] out = new double[size];
            int start = size < MAX_SAMPLES ? 0 : next;
            for (int i = 0; i < size; i++) {
                out[i] = values[(start + i) % MAX_SAMPLES];
            }
            return out;
        }
    }

    /** Cambia cada vez que se anade una muestra; AuctionParser lo usa para invalidar su cache. */
    public static int version() {
        return version;
    }

    /**
     * Clave de agrupacion: item + nombre personalizado + encantamientos.
     * Asi un libro de Mending no se mezcla con uno de Protection.
     * (Pociones y spawners de distinto tipo sin nombre propio si comparten clave.)
     */
    public static String keyOf(ItemStack stack) {
        StringBuilder sb = new StringBuilder(Registries.ITEM.getId(stack.getItem()).toString());

        Text custom = stack.get(DataComponentTypes.CUSTOM_NAME);
        if (custom != null) {
            String name = Formatting.strip(custom.getString());
            if (name != null && !name.isBlank()) {
                sb.append('|').append(name.trim().toLowerCase(Locale.ROOT));
            }
        }

        appendEnchants(sb, stack.get(DataComponentTypes.ENCHANTMENTS));
        appendEnchants(sb, stack.get(DataComponentTypes.STORED_ENCHANTMENTS));
        return sb.toString();
    }

    private static void appendEnchants(StringBuilder sb, ItemEnchantmentsComponent comp) {
        if (comp == null || comp.isEmpty()) return;
        TreeSet<String> sorted = new TreeSet<>();
        for (var entry : comp.getEnchantments()) {
            sorted.add(entry.getIdAsString() + comp.getLevel(entry));
        }
        sb.append('|').append(String.join(",", sorted));
    }

    /** Registra el precio unitario de una oferta (una sola vez por oferta distinta). */
    public static void observe(ItemStack stack, double totalPrice, double unitPrice, int dealPercent) {
        if (unitPrice <= 0.0) return;
        String key = keyOf(stack);
        String signature = key + '#' + stack.getCount() + '#' + Math.round(totalPrice);

        boolean shouldSave;
        synchronized (LOCK) {
            if (SIGNATURES.put(signature, Boolean.TRUE) != null) return;

            Series series = SERIES.computeIfAbsent(key, k -> new Series());
            double med = series.median();
            if (med > 0.0 && unitPrice <= med * (1.0 - dealPercent / 100.0)) {
                dealsSeenThisSession++;
            }
            series.add(unitPrice);
            version++;
            unsavedSamples++;
            shouldSave = unsavedSamples >= SAVE_EVERY_SAMPLES;
        }
        if (shouldSave) saveAsync();
    }

    /** Mediana y numero de muestras del item, o null si aun no hay suficientes. */
    public static Market marketFor(ItemStack stack) {
        String key = keyOf(stack);
        synchronized (LOCK) {
            Series series = SERIES.get(key);
            if (series == null) return null;
            double med = series.median();
            return med > 0.0 ? new Market(med, series.size) : null;
        }
    }

    /** Mediana por clave, o -1 si aun no hay suficientes muestras. */
    public static double medianFor(String key) {
        synchronized (LOCK) {
            Series series = SERIES.get(key);
            return series == null ? -1.0 : series.median();
        }
    }

    public static int trackedItems() {
        synchronized (LOCK) {
            return SERIES.size();
        }
    }

    public static int totalSamples() {
        synchronized (LOCK) {
            int n = 0;
            for (Series s : SERIES.values()) n += s.size;
            return n;
        }
    }

    public static int dealsSeen() {
        return dealsSeenThisSession;
    }

    public static void clear() {
        synchronized (LOCK) {
            SERIES.clear();
            SIGNATURES.clear();
            dealsSeenThisSession = 0;
            version++;
            unsavedSamples = 1;
        }
        saveAsync();
    }

    // ------------------------------------------------------------ persistencia

    public static void load() {
        if (!Files.exists(PATH)) return;
        try {
            String json = Files.readString(PATH, StandardCharsets.UTF_8);
            Type type = new TypeToken<Map<String, double[]>>() {}.getType();
            Map<String, double[]> data = GSON.fromJson(json, type);
            if (data == null) return;
            synchronized (LOCK) {
                for (Map.Entry<String, double[]> e : data.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null) continue;
                    Series series = new Series();
                    for (double v : e.getValue()) {
                        if (v > 0.0 && !Double.isNaN(v) && !Double.isInfinite(v)) series.add(v);
                    }
                    if (series.size > 0) SERIES.put(e.getKey(), series);
                }
                version++;
                unsavedSamples = 0;
            }
        } catch (Exception e) {
            DonutAhMod.LOGGER.error("No se pudo leer donutah_prices.json, se empieza sin historial.", e);
        }
    }

    public static void saveAsync() {
        if (!SAVE_QUEUED.compareAndSet(false, true)) return;
        CompletableFuture.runAsync(() -> {
            SAVE_QUEUED.set(false);
            saveNow();
        });
    }

    public static void saveNow() {
        Map<String, double[]> snapshot = new LinkedHashMap<>();
        synchronized (LOCK) {
            if (unsavedSamples == 0) return;
            for (Map.Entry<String, Series> e : SERIES.entrySet()) {
                snapshot.put(e.getKey(), e.getValue().ordered());
            }
            unsavedSamples = 0;
        }
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, GSON.toJson(snapshot), StandardCharsets.UTF_8);
        } catch (IOException e) {
            DonutAhMod.LOGGER.error("Error guardando donutah_prices.json", e);
        }
    }
}
