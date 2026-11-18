package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;

public class MarketScanner {
    public static final MarketScanner INSTANCE = new MarketScanner();

    private static final long SCAN_INTERVAL_MS = 10 * 60 * 1000L; // Раз в 10 минут
    private static final int PAGES_PER_ITEM = 2;
    private static final int NEXT_PAGE_SLOT_INDEX = 51; // 52-й слот (стрелка вправо)

    public enum State {
        IDLE,
        SEARCH_ITEM,
        WAIT_OPEN,
        SCAN_PAGE,
        SWITCHING_HUB,
        SWITCHING_ANARCHY
    }

    private State state = State.IDLE;
    private final Random random = new Random();

    private long lastFullScanTime = 0;
    private long nextActionTime = 0;
    private long stateStartTime = 0;

    private final Queue<String> keysQueue = new ArrayDeque<>();
    private String currentUniqueKey = "";
    private BotConfig.ItemData currentTarget = null;
    private final List<Double> currentItemPrices = new ArrayList<>();

    private int pagesCheckedForCurrent = 0;
    private int itemsCheckedSinceSwitch = 0;

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public String getStatusText() {
        if (state == State.IDLE) return "Ожидание";
        String name = currentTarget != null ? currentTarget.searchName : "...";
        return "Оценка: " + name + " (ост: " + keysQueue.size() + ")";
    }

    public void startFullScan(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        keysQueue.clear();
        for (Map.Entry<String, BotConfig.ItemData> entry : BotConfig.INSTANCE.items.entrySet()) {
            if (entry.getValue().enabled) {
                keysQueue.add(entry.getKey());
            }
        }

        if (keysQueue.isEmpty()) {
            client.player.sendMessage(Text.literal("§c[AuctionBot] §fСписок пуст! Наведите мышку на лот в /ah и нажмите [X / Ч]."), false);
            state = State.IDLE;
            return;
        }

        itemsCheckedSinceSwitch = 0;
        state = State.SEARCH_ITEM;
        nextActionTime = System.currentTimeMillis() + 250;
        client.player.sendMessage(Text.literal("§b[AuctionBot] §fНачинаю оценку " + keysQueue.size() + " выбранных предметов (смена анки каждые 2 шт.)..."), false);
    }

    public void stopScan() {
        state = State.IDLE;
        keysQueue.clear();
    }

    private long jitter(long baseMs, int minExtra, int maxExtra) {
        return baseMs + minExtra + random.nextInt(Math.max(1, maxExtra - minExtra + 1));
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null) return;
        long now = System.currentTimeMillis();

        // Автозапуск раз в 10 минут, если бот включен и в списке есть выбранные предметы
        if (state == State.IDLE && AuctionSniper.INSTANCE.isBotEnabled() && !BotConfig.INSTANCE.items.isEmpty()) {
            if (lastFullScanTime == 0 || (now - lastFullScanTime >= SCAN_INTERVAL_MS)) {
                startFullScan(client);
                return;
            }
        }

        if (state == State.IDLE || now < nextActionTime) return;

        BotConfig.SpeedMode speed = BotConfig.INSTANCE.speedMode;

        switch (state) {
            case SEARCH_ITEM -> {
                if (keysQueue.isEmpty()) {
                    BotConfig.save();
                    lastFullScanTime = now;
                    state = State.IDLE;
                    client.player.sendMessage(Text.literal("§a[AuctionBot] §fЦены всех выбранных предметов обновлены! Начинаю скупку."), false);
                    return;
                }

                // Каждые 2 проверенных предмета меняем анархию, чтобы анти-спам не забанил/не кикнул
                if (itemsCheckedSinceSwitch >= 2) {
                    itemsCheckedSinceSwitch = 0;
                    if (client.currentScreen != null) {
                        client.player.closeHandledScreen();
                    }
                    state = State.SWITCHING_HUB;
                    nextActionTime = now + jitter(500, 200, 450);
                    return;
                }

                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(250, 100, 250);
                    return;
                }

                currentUniqueKey = keysQueue.poll();
                currentTarget = BotConfig.INSTANCE.items.get(currentUniqueKey);
                if (currentTarget == null || !currentTarget.enabled) {
                    return;
                }

                currentItemPrices.clear();
                pagesCheckedForCurrent = 0;

                client.getNetworkHandler().sendChatCommand("ah search " + currentTarget.searchName);
                stateStartTime = now;
                state = State.WAIT_OPEN;
                nextActionTime = now + jitter(Math.max(400L, speed.guiReactDelayMs), 150, 350);
            }

            case WAIT_OPEN -> {
                if (client.currentScreen instanceof GenericContainerScreen) {
                    state = State.SCAN_PAGE;
                    nextActionTime = now + jitter(180, 100, 250);
                } else if (now - stateStartTime > 3000) {
                    // Если по этому запросу ничего не найдено или не открылось — идем к следующему
                    itemsCheckedSinceSwitch++;
                    state = State.SEARCH_ITEM;
                    nextActionTime = now + jitter(600, 250, 500);
                }
            }
            case SCAN_PAGE -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    finishCurrentItem(client);
                    return;
                }

                collectMatchingFromPage(containerScreen, client);
                pagesCheckedForCurrent++;

                GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                boolean canFlipNext = pagesCheckedForCurrent < PAGES_PER_ITEM
                        && handler.slots.size() > NEXT_PAGE_SLOT_INDEX
                        && !handler.slots.get(NEXT_PAGE_SLOT_INDEX).getStack().isEmpty();

                if (canFlipNext) {
                    client.interactionManager.clickSlot(
                            handler.syncId, NEXT_PAGE_SLOT_INDEX, 0, SlotActionType.PICKUP, client.player
                    );
                    nextActionTime = now + jitter(320, 150, 300);
                } else {
                    client.player.closeHandledScreen();
                    finishCurrentItem(client);
                }
            }

            case SWITCHING_HUB -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                }
                client.getNetworkHandler().sendChatCommand("hub");
                state = State.SWITCHING_ANARCHY;
                nextActionTime = now + jitter(2100, 600, 1300);
            }

            case SWITCHING_ANARCHY -> {
                int anarchyNum = 103 + random.nextInt(6);
                client.getNetworkHandler().sendChatCommand("an" + anarchyNum);
                client.player.sendMessage(Text.literal("§d[AuctionBot] §fСмена анки для скана -> /an" + anarchyNum), false);
                state = State.SEARCH_ITEM;
                nextActionTime = now + jitter(2800, 800, 1500);
            }
        }
    }

    private void collectMatchingFromPage(GenericContainerScreen screen, MinecraftClient client) {
        if (currentTarget == null) return;
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int maxSlots = Math.min(45, handler.getRows() * 9);

        for (int i = 0; i < maxSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (PriceParser.isSoldPlaceholder(stack)) continue;

            // Проверяем, что предмет совпадает по точному названию + зачарованиям
            String slotKey = PriceParser.buildUniqueKey(stack);
            if (!slotKey.equals(currentUniqueKey)) continue;

            double unitPrice = PriceParser.parseUnitPrice(stack, client);
            if (unitPrice > 0) {
                currentItemPrices.add(unitPrice);
            }
        }
    }

    private void finishCurrentItem(MinecraftClient client) {
        if (currentTarget != null && !currentItemPrices.isEmpty()) {
            Collections.sort(currentItemPrices);

            double chosenPrice;
            if (currentItemPrices.size() >= 3) {
                double median = currentItemPrices.get(currentItemPrices.size() / 2);
                double minLegitThreshold = median * 0.25; // Отбрасываем подставы анти-чита

                chosenPrice = currentItemPrices.get(currentItemPrices.size() - 1);
                for (double p : currentItemPrices) {
                    if (p >= minLegitThreshold) {
                        chosenPrice = p;
                        break;
                    }
                }
            } else if (currentItemPrices.size() == 2 && currentItemPrices.get(0) < currentItemPrices.get(1) * 0.2) {
                chosenPrice = currentItemPrices.get(1);
            } else {
                chosenPrice = currentItemPrices.get(0);
            }

            currentTarget.unitPrice = chosenPrice;
            BotConfig.save();
        }

        itemsCheckedSinceSwitch++;
        state = State.SEARCH_ITEM;
        nextActionTime = System.currentTimeMillis() + jitter(650, 250, 500);
    }

    public boolean handleSuggestionsPacket(CommandSuggestionsS2CPacket packet) {
        return false;
    }
}
