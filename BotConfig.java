package com.auctionbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map;

public class BotConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("auctionbot.json").toFile();

    public static BotConfig INSTANCE = new BotConfig();

    public int buyPercent = 90;
    public SpeedMode speedMode = SpeedMode.FAST;
    public Map<String, ItemData> items = new LinkedHashMap<>();

    public enum SpeedMode {
        ULTRA("Ультра быстрый", 150, 100, 350),
        FAST("Быстрый", 300, 200, 500),
        MEDIUM("Средний", 600, 400, 800);

        public final String displayName;
        public final long chatDelayMs;
        public final long guiReactDelayMs;
        public final long refreshDelayMs;

        SpeedMode(String displayName, long chatDelayMs, long guiReactDelayMs, long refreshDelayMs) {
            this.displayName = displayName;
            this.chatDelayMs = chatDelayMs;
            this.guiReactDelayMs = guiReactDelayMs;
            this.refreshDelayMs = refreshDelayMs;
        }

        public SpeedMode next() {
            SpeedMode[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    public static class ItemData {
        public String displayName;
        public double unitPrice;
        public boolean enabled;

        public ItemData(String displayName, double unitPrice, boolean enabled) {
            this.displayName = displayName;
            this.unitPrice = unitPrice;
            this.enabled = enabled;
        }
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                BotConfig loaded = GSON.fromJson(reader, BotConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                    if (INSTANCE.items == null) INSTANCE.items = new LinkedHashMap<>();
                    if (INSTANCE.speedMode == null) INSTANCE.speedMode = SpeedMode.FAST;
                    if (INSTANCE.buyPercent < 1 || INSTANCE.buyPercent > 100) INSTANCE.buyPercent = 90;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateItemPrice(String rawName, double newUnitPrice) {
        String key = rawName.trim().toLowerCase();
        ItemData existing = items.get(key);
        if (existing == null) {
            items.put(key, new ItemData(rawName.trim(), newUnitPrice, true));
        } else {
            existing.unitPrice = newUnitPrice;
            existing.displayName = rawName.trim();
        }
    }
}
