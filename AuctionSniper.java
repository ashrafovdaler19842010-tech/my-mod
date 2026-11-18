package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.Random;

public class AuctionSniper {
    public static final AuctionSniper INSTANCE = new AuctionSniper();

    public enum SniperState {
        OFF,
        OPENING_AH,
        SCANNING_AH,
        SELLING_BOUGHT,
        SWITCHING_HUB,
        SWITCHING_ANARCHY,
        STORAGE_OPEN_AH,
        STORAGE_WAIT_MAIN,
        STORAGE_WAIT_INNER
    }

    private SniperState state = SniperState.OFF;
    private final Random random = new Random();

    private long nextActionTime = 0;
    private long stateStartTime = 0;
    private int refreshCycles = 0;
    private long lastStorageCheckTime = 0;

    private String pendingSellItemName = "";
    private double pendingSellUnitPrice100 = 0;
    private int pendingSellCount = 1;

    public boolean isBotEnabled() {
        return state != SniperState.OFF;
    }

    public void toggleBot(MinecraftClient client) {
        if (state == SniperState.OFF) {
            state = SniperState.OPENING_AH;
            refreshCycles = 0;
            lastStorageCheckTime = System.currentTimeMillis();
            nextActionTime = System.currentTimeMillis();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a[AuctionBot] §fБот ЗАПУЩЕН!"), false);
            }
        } else {
            state = SniperState.OFF;
            MarketScanner.INSTANCE.stopScan();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§c[AuctionBot] §fБот ОСТАНОВЛЕН."), false);
            }
        }
    }

    public String getHudStatus() {
        if (state == SniperState.OFF) return "§cВЫКЛ";
        if (MarketScanner.INSTANCE.isRunning()) return "§b" + MarketScanner.INSTANCE.getStatusText();
        return "§aСкупка §7(Цикл: " + refreshCycles + "/50)";
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null) return;
        if (state == SniperState.OFF) return;

        if (MarketScanner.INSTANCE.isRunning()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now < nextActionTime) return;

        BotConfig.SpeedMode speed = BotConfig.INSTANCE.speedMode;

        // Проверка хранилища раз в минуту (60 000 мс)
        if ((state == SniperState.OPENING_AH || state == SniperState.SCANNING_AH)
                && (now - lastStorageCheckTime >= 60_000L)) {
            lastStorageCheckTime = now;
            state = SniperState.STORAGE_OPEN_AH;
            nextActionTime = now;
            return;
        }

        switch (state) {
            case OPENING_AH -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + speed.guiReactDelayMs;
                    return;
                }
                client.getNetworkHandler().sendChatCommand("ah");
                stateStartTime = now;
                state = SniperState.SCANNING_AH;
                nextActionTime = now + speed.guiReactDelayMs;
            }

            case SCANNING_AH -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    if (now - stateStartTime > 3000) {
                        state = SniperState.OPENING_AH;
                    }
                    return;
                }

                GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                int rows = handler.getRows();
                int mainSlots = Math.min(45, rows * 9);

                // 1. Проверяем первые 45 слотов на выгодные предметы
                for (int i = 0; i < mainSlots; i++) {
                    ItemStack stack = handler.slots.get(i).getStack();
                    if (stack.isEmpty()) continue;

                    String cleanName = PriceParser.getCleanItemName(stack);
                    BotConfig.ItemData data = BotConfig.INSTANCE.items.get(cleanName.toLowerCase());
                    if (data == null || !data.enabled || data.unitPrice <= 0) continue;

                    double currentUnitPrice = PriceParser.parseUnitPrice(stack, client);
                    if (currentUnitPrice <= 0) continue;

                    double maxAllowedUnitPrice = data.unitPrice * (BotConfig.INSTANCE.buyPercent / 100.0);

                    if (currentUnitPrice <= maxAllowedUnitPrice) {
                        client.interactionManager.clickSlot(
                                handler.syncId, i, 0, SlotActionType.QUICK_MOVE, client.player
                        );

                        pendingSellItemName = cleanName;
                        pendingSellUnitPrice100 = data.unitPrice;
                        pendingSellCount = stack.getCount();

                        client.player.sendMessage(Text.literal(
                                "§a[Куплено] §f" + cleanName + " x" + pendingSellCount +
                                        " по " + (long) currentUnitPrice + "/шт."
                        ), false);

                        client.player.closeHandledScreen();
                        state = SniperState.SELLING_BOUGHT;
                        nextActionTime = now + speed.chatDelayMs;
                        return;
                    }
                }

                // 2. После 50 циклов обновления уходим в /hub -> /an103..108
                if (refreshCycles >= 50) {
                    refreshCycles = 0;
                    client.player.closeHandledScreen();
                    state = SniperState.SWITCHING_HUB;
                    nextActionTime = now + speed.chatDelayMs;
                    return;
                }

                // 3. Ищем кнопку "Обновить" в последнем ряду (слоты 45..53)
                int refreshSlot = findButtonSlot(handler, "обновить", "refresh", "обновление");
                if (refreshSlot != -1) {
                    client.interactionManager.clickSlot(
                            handler.syncId, refreshSlot, 0, SlotActionType.PICKUP, client.player
                    );
                    refreshCycles++;
                    nextActionTime = now + speed.refreshDelayMs;
                } else {
                    client.player.closeHandledScreen();
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + speed.chatDelayMs;
                }
            }
            case SELLING_BOUGHT -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + speed.guiReactDelayMs;
                    return;
                }

                // Ищем купленный предмет в инвентаре игрока (слоты 0..35)
                int foundInvSlot = -1;
                int actualCount = pendingSellCount;
                for (int i = 0; i < 36; i++) {
                    ItemStack invStack = client.player.getInventory().getStack(i);
                    if (!invStack.isEmpty()) {
                        String name = PriceParser.getCleanItemName(invStack);
                        if (name.equalsIgnoreCase(pendingSellItemName)) {
                            foundInvSlot = i;
                            actualCount = invStack.getCount();
                            break;
                        }
                    }
                }

                if (foundInvSlot != -1) {
                    int hotbarSlot = client.player.getInventory().getSelectedSlot();
                    if (foundInvSlot >= 9) {
                        client.interactionManager.clickSlot(
                                client.player.playerScreenHandler.syncId,
                                foundInvSlot,
                                hotbarSlot,
                                SlotActionType.SWAP,
                                client.player
                        );
                    } else {
                        client.player.getInventory().setSelectedSlot(foundInvSlot);
                    }

                    long sellTotalPrice = Math.max(1L, Math.round(pendingSellUnitPrice100 * actualCount));
                    client.getNetworkHandler().sendChatCommand("ah sell " + sellTotalPrice);
                    client.player.sendMessage(Text.literal(
                            "§e[Продажа] §fВыставлен " + pendingSellItemName + " x" + actualCount + " за $" + sellTotalPrice
                    ), false);
                }

                state = SniperState.OPENING_AH;
                nextActionTime = now + speed.chatDelayMs;
            }

            case SWITCHING_HUB -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                }
                client.getNetworkHandler().sendChatCommand("hub");
                state = SniperState.SWITCHING_ANARCHY;
                nextActionTime = now + 1500L;
            }

            case SWITCHING_ANARCHY -> {
                int anarchyNum = 103 + random.nextInt(6); // От 103 до 108 включительно
                client.getNetworkHandler().sendChatCommand("an" + anarchyNum);
                client.player.sendMessage(Text.literal("§d[AuctionBot] §fПереход на /an" + anarchyNum), false);
                state = SniperState.OPENING_AH;
                nextActionTime = now + 2000L;
            }

            case STORAGE_OPEN_AH -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + speed.guiReactDelayMs;
                    return;
                }
                client.getNetworkHandler().sendChatCommand("ah");
                stateStartTime = now;
                state = SniperState.STORAGE_WAIT_MAIN;
                nextActionTime = now + speed.guiReactDelayMs;
            }

            case STORAGE_WAIT_MAIN -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    if (now - stateStartTime > 3000) state = SniperState.OPENING_AH;
                    return;
                }

                GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                int storageSlot = findButtonSlot(handler, "хранилище", "склад", "storage", "мои предметы");
                if (storageSlot != -1) {
                    client.interactionManager.clickSlot(
                            handler.syncId, storageSlot, 0, SlotActionType.PICKUP, client.player
                    );
                    stateStartTime = now;
                    state = SniperState.STORAGE_WAIT_INNER;
                    nextActionTime = now + speed.guiReactDelayMs;
                } else {
                    client.player.closeHandledScreen();
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + speed.chatDelayMs;
                }
            }

            case STORAGE_WAIT_INNER -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    if (now - stateStartTime > 3000) state = SniperState.OPENING_AH;
                    return;
                }

                GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                int relistSlot = -1;
                for (int i = 0; i < handler.getRows() * 9; i++) {
                    ItemStack stack = handler.slots.get(i).getStack();
                    if (!stack.isEmpty()) {
                        String name = PriceParser.getCleanItemName(stack).toLowerCase();
                        if (name.contains("перевыставить") || name.contains("relist") || name.contains("вернуть все")) {
                            relistSlot = i;
                            break;
                        }
                    }
                }

                if (relistSlot != -1) {
                    client.interactionManager.clickSlot(
                            handler.syncId, relistSlot, 0, SlotActionType.PICKUP, client.player
                    );
                    client.player.sendMessage(Text.literal("§b[Хранилище] §fПредметы перевыставлены!"), false);
                }

                client.player.closeHandledScreen();
                state = SniperState.OPENING_AH;
                nextActionTime = now + speed.chatDelayMs;
            }
        }
    }

    private int findButtonSlot(GenericContainerScreenHandler handler, String... keywords) {
        int totalSlots = handler.getRows() * 9;
        int startSlot = Math.max(0, totalSlots - 9); // Последний ряд (слоты 45..53)

        for (int i = startSlot; i < totalSlots; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            String name = PriceParser.getCleanItemName(stack).toLowerCase();
            for (String kw : keywords) {
                if (name.contains(kw)) {
                    return i;
                }
            }
        }
        return -1;
    }
}
