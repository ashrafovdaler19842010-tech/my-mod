package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MarketScanner {
    public static final MarketScanner INSTANCE = new MarketScanner();

    private static final String RU_CHARS = "абвгдежзийклмнопрстуфхцчшщъыьэюя";
    private static final long SCAN_INTERVAL_MS = 30 * 60 * 1000L; // 30 минут

    public enum State {
        IDLE,
        OPEN_CHAT,
        TYPE_PREFIX,
        PRESS_TAB,
        WAIT_TAB_RESULT,
        SEARCHING_ITEM,
        WAITING_CHEST
    }

    private State state = State.IDLE;
    private long lastFullScanTime = 0;
    private long nextActionTime = 0;
    private long stepStartTime = 0;

    private final List<String> prefixes = new ArrayList<>();
    private int prefixIndex = 0;
    private boolean gotSuggestionsForCurrentPrefix = false;

    private final Set<String> discoveredItems = new LinkedHashSet<>();
    private final Queue<String> itemsQueue = new ArrayDeque<>();
    private String currentSearchQuery = "";

    public MarketScanner() {
        // Только русский алфавит: от "аа" до "яя"
        for (int i = 0; i < RU_CHARS.length(); i++) {
            for (int j = 0; j < RU_CHARS.length(); j++) {
                prefixes.add("" + RU_CHARS.charAt(i) + RU_CHARS.charAt(j));
            }
        }
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public String getStatusText() {
        return switch (state) {
            case IDLE -> "Ожидание";
            case OPEN_CHAT, TYPE_PREFIX, PRESS_TAB, WAIT_TAB_RESULT -> {
                String cur = prefixIndex < prefixes.size() ? prefixes.get(prefixIndex) : "яя";
                yield "TAB [" + cur + "] (" + prefixIndex + "/" + prefixes.size() + ") Найдено: " + discoveredItems.size();
            }
            case SEARCHING_ITEM, WAITING_CHEST -> "Скан цен (осталось: " + itemsQueue.size() + ")";
        };
    }

    public void startFullScan(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        discoveredItems.clear();
        itemsQueue.clear();
        prefixIndex = 0;
        gotSuggestionsForCurrentPrefix = false;
        state = State.OPEN_CHAT;
        nextActionTime = System.currentTimeMillis();
        client.player.sendMessage(Text.literal("§b[AuctionBot] §fСбор предметов по русским буквам (аа..яя)..."), false);
    }

    public void stopScan() {
        state = State.IDLE;
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        long now = System.currentTimeMillis();

        // Автозапуск раз в 30 минут, если бот активен
        if (state == State.IDLE && AuctionSniper.INSTANCE.isBotEnabled()) {
            if (lastFullScanTime == 0 || (now - lastFullScanTime >= SCAN_INTERVAL_MS)) {
                startFullScan(client);
                return;
            }
        }

        if (state == State.IDLE || now < nextActionTime) return;

        BotConfig.SpeedMode speed = BotConfig.INSTANCE.speedMode;

        switch (state) {
            case OPEN_CHAT -> {
                client.setScreen(new ChatScreen(""));
                state = State.TYPE_PREFIX;
                nextActionTime = now + 100;
            }

            case TYPE_PREFIX -> {
                if (prefixIndex >= prefixes.size()) {
                    // Дошли до "яя" — закрываем чат и переходим к оценке цен
                    if (client.currentScreen instanceof ChatScreen) {
                        client.setScreen(null);
                    }
                    itemsQueue.addAll(discoveredItems);
                    client.player.sendMessage(Text.literal("§b[AuctionBot] §fДошли до ЯЯ! Найдено предметов: " + itemsQueue.size() + ". Оцениваю цены..."), false);
                    state = State.SEARCHING_ITEM;
                    nextActionTime = now + speed.chatDelayMs;
                    return;
                }

                String prefix = prefixes.get(prefixIndex);
                String cmd = "/ah search " + prefix;

                // Меняем текст прямо в открытом чате без пересоздания окна
                client.setScreen(new ChatScreen(cmd));
                gotSuggestionsForCurrentPrefix = false;
                state = State.PRESS_TAB;
                nextActionTime = now + 80;
            }

            case PRESS_TAB -> {
                if (client.currentScreen instanceof ChatScreen chatScreen) {
                    // Имитируем реальное нажатие клавиши TAB в открытом чате
                    chatScreen.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0);
                }
                stepStartTime = now;
                state = State.WAIT_TAB_RESULT;
                nextActionTime = now + 120;
            }

            case WAIT_TAB_RESULT -> {
                // Если сервер прислал список или прошло 350 мс (на эту пару ничего нет) — идем к следующей паре
                if (gotSuggestionsForCurrentPrefix || (now - stepStartTime > 350)) {
                    prefixIndex++;
                    state = State.TYPE_PREFIX;
                    nextActionTime = now + 40;
                }
            }

            case SEARCHING_ITEM -> {
                if (itemsQueue.isEmpty()) {
                    BotConfig.save();
                    lastFullScanTime = now;
                    state = State.IDLE;
                    client.player.sendMessage(Text.literal("§a[AuctionBot] §fВсе цены запомнены! Начинаю покупку."), false);
                    return;
                }

                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    client.setScreen(null);
                    nextActionTime = now + speed.guiReactDelayMs;
                    return;
                }

                currentSearchQuery = itemsQueue.poll();
                client.getNetworkHandler().sendChatCommand("ah search " + currentSearchQuery);
                stepStartTime = now;
                state = State.WAITING_CHEST;
                nextActionTime = now + speed.guiReactDelayMs;
            }

            case WAITING_CHEST -> {
                if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
                    evaluateChestAndSave(containerScreen, client);
                    client.player.closeHandledScreen();
                    state = State.SEARCHING_ITEM;
                    nextActionTime = now + speed.chatDelayMs;
                } else if (now - stepStartTime > 2500) {
                    state = State.SEARCHING_ITEM;
                    nextActionTime = now + speed.chatDelayMs;
                }
            }
        }
    }

    public boolean handleSuggestionsPacket(CommandSuggestionsS2CPacket packet) {
        if (state != State.PRESS_TAB && state != State.WAIT_TAB_RESULT) {
            return false;
        }

        boolean addedAny = false;
        for (CommandSuggestionsS2CPacket.Suggestion suggestion : packet.suggestions()) {
            String text = suggestion.text().trim();
            if (!text.isEmpty() && !text.startsWith("/") && !text.contains("<")) {
                discoveredItems.add(text);
                addedAny = true;
            }
        }

        if (addedAny) {
            gotSuggestionsForCurrentPrefix = true;
        }
        // Не отменяем пакет (возвращаем false), чтобы список подсказок отрисовывался в чате
        return false;
    }

    private void evaluateChestAndSave(GenericContainerScreen screen, MinecraftClient client) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int maxSlots = Math.min(45, handler.getRows() * 9);

        double bestUnitPrice = Double.MAX_VALUE;
        String foundItemName = currentSearchQuery;

        for (int i = 0; i < maxSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            double unitPrice = PriceParser.parseUnitPrice(stack, client);
            if (unitPrice > 0 && unitPrice < bestUnitPrice) {
                bestUnitPrice = unitPrice;
                String cleanName = PriceParser.getCleanItemName(stack);
                if (!cleanName.isEmpty()) {
                    foundItemName = cleanName;
                }
            }
        }

        if (bestUnitPrice < Double.MAX_VALUE) {
            BotConfig.INSTANCE.updateItemPrice(foundItemName, bestUnitPrice);
            BotConfig.save();
        }
    }
}
