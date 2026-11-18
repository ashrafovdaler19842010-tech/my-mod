package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.RequestCommandCompletionsC2SPacket;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Queue;
import java.util.Set;

public class MarketScanner {
    public static final MarketScanner INSTANCE = new MarketScanner();

    private static final String ALPHABET = "абвгдеёжзийклмнопрстуфхцчшщъыьэюяabcdefghijklmnopqrstuvwxyz";
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
    private long chestWaitStartTime = 0;

    private int alphabetIndex = 0;
    private boolean waitingTabResponse = false;

    private final Set<String> discoveredItems = new LinkedHashSet<>();
    private final Queue<String> itemsQueue = new ArrayDeque<>();
    private String currentSearchQuery = "";

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public String getStatusText() {
        return switch (state) {
            case IDLE -> "Ожидание";
            case COLLECTING_TABS -> "Сбор TAB (" + alphabetIndex + "/" + ALPHABET.length() + ")";
            case SEARCHING_ITEM, WAITING_CHEST -> "Скан цен (осталось: " + itemsQueue.size() + ")";
        };
    }

    public void startFullScan(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        discoveredItems.clear();
        itemsQueue.clear();
        alphabetIndex = 0;
        waitingTabResponse = false;
        state = State.COLLECTING_TABS;
        nextActionTime = System.currentTimeMillis();
        client.player.sendMessage(Text.literal("§b[AuctionBot] §fНачинаю сбор предметов через TAB..."), false);
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
                    // Таймаут ожидания TAB (1.5 сек), если на эту букву нет предметов
                    if (now - chestWaitStartTime > 1500) {
                        waitingTabResponse = false;
                        alphabetIndex++;
                    }
                    return;
                }

                if (alphabetIndex < ALPHABET.length()) {
                    char letter = ALPHABET.charAt(alphabetIndex);
                    waitingTabResponse = true;
                    chestWaitStartTime = now;
                    client.getNetworkHandler().sendPacket(
                            new RequestCommandCompletionsC2SPacket(COMPLETION_ID, "/ah search " + letter)
                    );
                } else {
                    itemsQueue.addAll(discoveredItems);
                    client.player.sendMessage(Text.literal("§b[AuctionBot] §fНайдено предметов: " + itemsQueue.size() + ". Начинаю оценку цен..."), false);
                    state = State.SEARCHING_ITEM;
                    nextActionTime = now + speed.chatDelayMs;
                }
            }

            case SEARCHING_ITEM -> {
                if (itemsQueue.isEmpty()) {
                    BotConfig.save();
                    lastFullScanTime = now;
                    state = State.IDLE;
                    client.player.sendMessage(Text.literal("§a[AuctionBot] §fСканирование цен завершено! Перехожу к скупке."), false);
                    return;
                }

                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + speed.guiReactDelayMs;
                    return;
                }

                currentSearchQuery = itemsQueue.poll();
                client.getNetworkHandler().sendChatCommand("ah search " + currentSearchQuery);
                chestWaitStartTime = now;
                state = State.WAITING_CHEST;
                nextActionTime = now + speed.guiReactDelayMs;
            }

            case WAITING_CHEST -> {
                if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
                    evaluateChestAndSave(containerScreen, client);
                    client.player.closeHandledScreen();
                    state = State.SEARCHING_ITEM;
                    nextActionTime = now + speed.chatDelayMs;
                } else if (now - chestWaitStartTime > 2500) {
                    // Если сундук не открылся за 2.5 сек — пропускаем этот запрос
                    state = State.SEARCHING_ITEM;
                    nextActionTime = now + speed.chatDelayMs;
                }
            }
        }
    }

    public boolean handleSuggestionsPacket(CommandSuggestionsS2CPacket packet) {
        if (state != State.COLLECTING_TABS || packet.id() != COMPLETION_ID) {
            return false;
        }

        for (CommandSuggestionsS2CPacket.Suggestion suggestion : packet.suggestions()) {
            String text = suggestion.text().trim();
            if (!text.isEmpty()) {
                discoveredItems.add(text);
            }
        }

        waitingTabResponse = false;
        alphabetIndex++;
        nextActionTime = System.currentTimeMillis() + 50;
        return true;
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
        }
    }
}
