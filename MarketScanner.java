package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class MarketScanner {
    public static final MarketScanner INSTANCE = new MarketScanner();

    private static final String RU_CHARS = "абвгдежзийклмнопрстуфхцчшщъыьэюя";
    private static final String EN_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final long SCAN_INTERVAL_MS = 30 * 60 * 1000L; // 30 минут
    private static final int COMPLETION_ID = 987654;

    public enum State {
        IDLE,
        COLLECTING_TABS,
        SEARCHING_ITEM,
        WAITING_CHEST
    }

    private State state = State.IDLE;
    private long lastFullScanTime = 0;
    private long nextActionTime = 0;
    private long stepStartTime = 0;

    private final List<String> prefixes = new ArrayList<>();
    private int prefixIndex = 0;
    private boolean waitingTabResponse = false;

    private final Set<String> discoveredItems = new LinkedHashSet<>();
    private final Queue<String> itemsQueue = new ArrayDeque<>();
    private String currentSearchQuery = "";

    public MarketScanner() {
        // Генерируем все 2-буквенные комбинации (аа..яя и aa..zz)
        for (int i = 0; i < RU_CHARS.length(); i++) {
            for (int j = 0; j < RU_CHARS.length(); j++) {
                prefixes.add("" + RU_CHARS.charAt(i) + RU_CHARS.charAt(j));
            }
        }
        for (int i = 0; i < EN_CHARS.length(); i++) {
            for (int j = 0; j < EN_CHARS.length(); j++) {
                prefixes.add("" + EN_CHARS.charAt(i) + EN_CHARS.charAt(j));
            }
        }
    }

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public String getStatusText() {
        return switch (state) {
            case IDLE -> "Ожидание";
            case COLLECTING_TABS -> "Сбор TAB (" + prefixIndex + "/" + prefixes.size() + ") найдено: " + discoveredItems.size();
            case SEARCHING_ITEM, WAITING_CHEST -> "Скан цен (осталось: " + itemsQueue.size() + ")";
        };
    }

    public void startFullScan(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        discoveredItems.clear();
        itemsQueue.clear();
        prefixIndex = 0;
        waitingTabResponse = false;
        state = State.COLLECTING_TABS;
        nextActionTime = System.currentTimeMillis();
        client.player.sendMessage(Text.literal("§b[AuctionBot] §fЗапуск сбора предметов по 2 буквам (аа..zz)..."), false);
    }

    public void stopScan() {
        state = State.IDLE;
        waitingTabResponse = false;
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
            case COLLECTING_TABS -> {
                if (waitingTabResponse) {
                    // Если сервер не ответил за 220 мс (на эти 2 буквы нет предметов) — идем к следующей паре
                    if (now - stepStartTime > 220) {
                        waitingTabResponse = false;
                        prefixIndex++;
                    }
                    return;
                }

                if (prefixIndex < prefixes.size()) {
                    String prefix = prefixes.get(prefixIndex);
                    String cmd = "/ah search " + prefix;

                    // Открываем чат с командой, чтобы сервер 100% прислал TAB-подсказки
                    if (!(client.currentScreen instanceof ChatScreen)) {
                        client.setScreen(new ChatScreen(cmd));
                    }

                    waitingTabResponse = true;
                    stepStartTime = now;
                    client.getNetworkHandler().sendPacket(
                            new RequestCommandCompletionsC2SPacket(COMPLETION_ID, cmd)
                    );
                } else {
                    if (client.currentScreen instanceof ChatScreen) {
                        client.setScreen(null);
                    }
                    itemsQueue.addAll(discoveredItems);
                    client.player.sendMessage(Text.literal("§b[AuctionBot] §fСбор окончен! Найдено предметов: " + itemsQueue.size()), false);
                    state = State.SEARCHING_ITEM;
                    nextActionTime = now + speed.chatDelayMs;
                }
            }

            case SEARCHING_ITEM -> {
                if (itemsQueue.isEmpty()) {
                    BotConfig.save();
                    lastFullScanTime = now;
                    state = State.IDLE;
                    client.player.sendMessage(Text.literal("§a[AuctionBot] §fВсе цены сохранены! Откройте R-Ctrl для настройки."), false);
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
        if (state != State.COLLECTING_TABS) {
            return false;
        }

        for (CommandSuggestionsS2CPacket.Suggestion suggestion : packet.suggestions()) {
            String text = suggestion.text().trim();
            if (!text.isEmpty() && !text.startsWith("/")) {
                discoveredItems.add(text);
            }
        }

        if (packet.id() == COMPLETION_ID) {
            waitingTabResponse = false;
            prefixIndex++;
            nextActionTime = System.currentTimeMillis() + 30;
            return true;
        }
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
