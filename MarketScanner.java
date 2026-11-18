package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MarketScanner {
    public static final MarketScanner INSTANCE = new MarketScanner();

    private static final long SCAN_INTERVAL_MS = 10 * 60 * 1000L; // Раз в 10 минут
    private static final int PAGES_PER_ANARCHY = 50;
    private static final int TOTAL_PHASES = 4; // 4 круга по 50 стр. = 200 стр.
    private static final int NEXT_PAGE_SLOT_INDEX = 51; // 52-й слот (стрелка вправо)

    public enum State {
        IDLE,
        OPENING_AH,
        WAIT_OPEN,
        FLIPPING_PAGES,
        SWITCHING_HUB,
        SWITCHING_ANARCHY
    }

    private State state = State.IDLE;
    private final Random random = new Random();

    private long lastFullScanTime = 0;
    private long nextActionTime = 0;
    private long stateStartTime = 0;
    private int currentPhase = 1;
    private int currentPageCount = 0;

    private final Map<String, String> originalNames = new HashMap<>();
    private final Map<String, String> itemIds = new HashMap<>();
    private final Map<String, List<Double>> collectedPrices = new HashMap<>();

    public boolean isRunning() {
        return state != State.IDLE;
    }

    public String getStatusText() {
        if (state == State.IDLE) return "Ожидание";
        return "Скан [" + currentPhase + "/" + TOTAL_PHASES + "] стр. " + currentPageCount + "/" + PAGES_PER_ANARCHY;
    }

    public void startFullScan(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;
        originalNames.clear();
        itemIds.clear();
        collectedPrices.clear();
        currentPhase = 1;
        currentPageCount = 0;
        state = State.OPENING_AH;
        nextActionTime = System.currentTimeMillis() + 200;
        client.player.sendMessage(Text.literal("§b[AuctionBot] §fЗапуск скана: 4 круга по 50 стр. (всего 200 стр.)"), false);
    }

    public void stopScan() {
        state = State.IDLE;
    }

    private long jitter(long baseMs, int minExtra, int maxExtra) {
        return baseMs + minExtra + random.nextInt(Math.max(1, maxExtra - minExtra + 1));
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null) return;
        long now = System.currentTimeMillis();

        // Автозапуск раз в 10 минут, если бот включен
        if (state == State.IDLE && AuctionSniper.INSTANCE.isBotEnabled()) {
            if (lastFullScanTime == 0 || (now - lastFullScanTime >= SCAN_INTERVAL_MS)) {
                startFullScan(client);
                return;
            }
        }

        if (state == State.IDLE || now < nextActionTime) return;

        switch (state) {
            case OPENING_AH -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(250, 100, 250);
                    return;
                }
                client.getNetworkHandler().sendChatCommand("ah");
                stateStartTime = now;
                state = State.WAIT_OPEN;
                nextActionTime = now + jitter(350, 150, 300);
            }

            case WAIT_OPEN -> {
                if (client.currentScreen instanceof GenericContainerScreen) {
                    currentPageCount = 0;
                    state = State.FLIPPING_PAGES;
                    nextActionTime = now + jitter(180, 100, 220);
                } else if (now - stateStartTime > 3500) {
                    state = State.OPENING_AH;
                }
            }
            case FLIPPING_PAGES -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    state = State.OPENING_AH;
                    nextActionTime = now + 400;
                    return;
                }

                collectFromPage(containerScreen, client);
                currentPageCount++;

                GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                boolean canFlipNext = currentPageCount < PAGES_PER_ANARCHY
                        && handler.slots.size() > NEXT_PAGE_SLOT_INDEX
                        && !handler.slots.get(NEXT_PAGE_SLOT_INDEX).getStack().isEmpty();

                if (canFlipNext) {
                    client.interactionManager.clickSlot(
                            handler.syncId, NEXT_PAGE_SLOT_INDEX, 0, SlotActionType.PICKUP, client.player
                    );
                    nextActionTime = now + jitter(260, 120, 280);
                } else {
                    client.player.closeHandledScreen();
                    if (currentPhase < TOTAL_PHASES) {
                        currentPhase++;
                        state = State.SWITCHING_HUB;
                        nextActionTime = now + jitter(500, 200, 500);
                    } else {
                        finalizeAndSavePrices(client);
                        lastFullScanTime = now;
                        state = State.IDLE;
                    }
                }
            }

            case SWITCHING_HUB -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                }
                client.getNetworkHandler().sendChatCommand("hub");
                state = State.SWITCHING_ANARCHY;
                nextActionTime = now + jitter(2000, 600, 1400);
            }

            case SWITCHING_ANARCHY -> {
                int anarchyNum = 103 + random.nextInt(6);
                client.getNetworkHandler().sendChatCommand("an" + anarchyNum);
                client.player.sendMessage(Text.literal("§b[AuctionBot] §fКруг #" + currentPhase + " на /an" + anarchyNum + "..."), false);
                state = State.OPENING_AH;
                nextActionTime = now + jitter(2800, 800, 1500);
            }
        }
    }

    private void collectFromPage(GenericContainerScreen screen, MinecraftClient client) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int maxSlots = Math.min(45, handler.getRows() * 9);

        for (int i = 0; i < maxSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (PriceParser.isSoldPlaceholder(stack)) continue;

            double unitPrice = PriceParser.parseUnitPrice(stack, client);
            if (unitPrice <= 0) continue;

            String cleanName = PriceParser.getCleanItemName(stack);
            if (cleanName.isEmpty()) continue;

            String key = cleanName.toLowerCase();
            originalNames.putIfAbsent(key, cleanName);
            itemIds.putIfAbsent(key, PriceParser.getItemId(stack));
            collectedPrices.computeIfAbsent(key, k -> new ArrayList<>()).add(unitPrice);
        }
    }

    private void finalizeAndSavePrices(MinecraftClient client) {
        int savedCount = 0;

        for (Map.Entry<String, List<Double>> entry : collectedPrices.entrySet()) {
            String key = entry.getKey();
            List<Double> prices = entry.getValue();
            if (prices.isEmpty()) continue;

            Collections.sort(prices);

            double chosenBestPrice;
            if (prices.size() >= 3) {
                double median = prices.get(prices.size() / 2);
                double minLegitThreshold = median * 0.25;

                chosenBestPrice = prices.get(prices.size() - 1);
                for (double p : prices) {
                    if (p >= minLegitThreshold) {
                        chosenBestPrice = p;
                        break;
                    }
                }
            } else {
                if (prices.size() == 2 && prices.get(0) < prices.get(1) * 0.2) {
                    chosenBestPrice = prices.get(1);
                } else {
                    chosenBestPrice = prices.get(0);
                }
            }

            String displayName = originalNames.getOrDefault(key, key);
            String id = itemIds.getOrDefault(key, "minecraft:barrier");
            BotConfig.INSTANCE.updateItemPrice(displayName, id, chosenBestPrice);
            savedCount++;
        }

        BotConfig.save();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(
                    "§a[AuctionBot] §fСкан 4x50 стр. завершен! Сохранено: " + savedCount + " предметов."
            ), false);
        }
    }

    public boolean handleSuggestionsPacket(CommandSuggestionsS2CPacket packet) {
        return false;
    }
}
