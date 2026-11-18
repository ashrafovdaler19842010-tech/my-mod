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

    // Точные индексы нижнего ряда аукциона FunTime (слоты 46..54 -> индексы 45..53)
    private static final int REFRESH_SLOT_INDEX = 45; // 46-й слот: Обновить
    private static final int STORAGE_SLOT_INDEX = 46; // 47-й слот: Хранилище

    public enum SniperState {
        OFF,
        OPENING_AH,
        WAIT_HUMAN_REACTION,
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
    private int cyclesBeforePause = 18;
    private long lastStorageCheckTime = 0;

    // Плавный легитный Анти-АФК
    private long nextAntiAfkTime = 0;
    private int smoothTurnTicks = 0;
    private float turnStepYaw = 0;

    private String pendingSellItemName = "";
    private double pendingSellUnitPrice100 = 0;
    private int pendingSellCount = 1;

    public boolean isBotEnabled() {
        return state != SniperState.OFF;
    }

    private long jitter(long baseMs, int minExtra, int maxExtra) {
        return baseMs + minExtra + random.nextInt(Math.max(1, maxExtra - minExtra + 1));
    }

    public void forceStopAll(MinecraftClient client) {
        state = SniperState.OFF;
        smoothTurnTicks = 0;
        MarketScanner.INSTANCE.stopScan();
        if (client.player != null) {
            client.player.setSneaking(false);
            if (client.currentScreen != null) {
                client.player.closeHandledScreen();
            }
            client.player.sendMessage(Text.literal("§c[AuctionBot] §fЭКСТРЕННЫЙ СТОП [C]! Всё остановлено."), false);
        }
    }

    public void toggleBot(MinecraftClient client) {
        if (state == SniperState.OFF) {
            state = SniperState.OPENING_AH;
            refreshCycles = 0;
            cyclesBeforePause = 14 + random.nextInt(12);
            lastStorageCheckTime = System.currentTimeMillis();
            nextAntiAfkTime = System.currentTimeMillis() + 20_000L + random.nextInt(15_000);
            nextActionTime = System.currentTimeMillis() + jitter(200, 100, 300);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a[AuctionBot] §fБот ЗАПУЩЕН. (Стоп на клавишу C)"), false);
            }
        } else {
            forceStopAll(client);
        }
    }

    public String getHudStatus() {
        if (MarketScanner.INSTANCE.isRunning()) {
            return "§b" + MarketScanner.INSTANCE.getStatusText();
        }
        if (state == SniperState.OFF) return "§cВЫКЛ";
        return "§aСкупка §7(" + refreshCycles + "/50 | База: " + BotConfig.INSTANCE.items.size() + ")";
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null) return;
        if (state == SniperState.OFF) return;

        // Пока идет скан 4x50 страниц — снайпер ждет его завершения
        if (MarketScanner.INSTANCE.isRunning()) {
            return;
        }

        long now = System.currentTimeMillis();

        // Плавный естественный Анти-АФК (раз в 25-45 секунд)
        if (smoothTurnTicks > 0) {
            client.player.setYaw(client.player.getYaw() + turnStepYaw);
            smoothTurnTicks--;
            if (smoothTurnTicks == 0) {
                client.player.setSneaking(false);
            }
        } else if (now >= nextAntiAfkTime) {
            nextAntiAfkTime = now + 25_000L + random.nextInt(20_000);
            smoothTurnTicks = 6 + random.nextInt(6);
            float totalAngle = (random.nextBoolean() ? 1f : -1f) * (2.5f + random.nextFloat() * 5.0f);
            turnStepYaw = totalAngle / smoothTurnTicks;
            if (client.currentScreen == null && random.nextBoolean()) {
                client.player.setSneaking(true);
            }
        }

        if (now < nextActionTime) return;

        BotConfig.SpeedMode speed = BotConfig.INSTANCE.speedMode;

        // Проверка хранилища раз в ~60-75 секунд
        if ((state == SniperState.OPENING_AH || state == SniperState.SCANNING_AH)
                && (now - lastStorageCheckTime >= 60_000L + random.nextInt(15_000))) {
            lastStorageCheckTime = now;
            state = SniperState.STORAGE_OPEN_AH;
            nextActionTime = now + jitter(250, 100, 300);
            return;
        }

        switch (state) {
            case OPENING_AH -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(speed.guiReactDelayMs, 150, 300);
                    return;
                }
                client.getNetworkHandler().sendChatCommand("ah");
                stateStartTime = now;
                state = SniperState.WAIT_HUMAN_REACTION;
                nextActionTime = now + jitter(speed.guiReactDelayMs, 180, 350);
            }

            case WAIT_HUMAN_REACTION -> {
                if (client.currentScreen instanceof GenericContainerScreen) {
                    state = SniperState.SCANNING_AH;
                    nextActionTime = now + jitter(150, 100, 260);
                } else if (now - stateStartTime > 3500) {
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + jitter(450, 150, 400);
                }
            }
            case SCANNING_AH -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + jitter(350, 120, 300);
                    return;
                }

                GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                int mainSlots = Math.min(45, handler.getRows() * 9);

                // 1. Проверяем первые 45 слотов (игнорируем серые панели купленных товаров)
                for (int i = 0; i < mainSlots; i++) {
                    ItemStack stack = handler.slots.get(i).getStack();
                    if (PriceParser.isSoldPlaceholder(stack)) continue;

                    String cleanName = PriceParser.getCleanItemName(stack);
                    BotConfig.ItemData data = BotConfig.INSTANCE.items.get(cleanName.toLowerCase());
                    if (data == null || !data.enabled || data.unitPrice <= 0) continue;

                    double currentUnitPrice = PriceParser.parseUnitPrice(stack, client);
                    if (currentUnitPrice <= 0) continue;

                    double maxAllowedUnitPrice = data.unitPrice * (BotConfig.INSTANCE.buyPercent / 100.0);
                    // Защита от подставы анти-чита (мусор за 2к при рыночной цене 100 млн — дешевле 15%)
                    double minAntiBaitPrice = data.unitPrice * 0.15;

                    if (currentUnitPrice <= maxAllowedUnitPrice && currentUnitPrice >= minAntiBaitPrice) {
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

                        state = SniperState.SELLING_BOUGHT;
                        nextActionTime = now + jitter(240, 110, 250);
                        return;
                    }
                }

                // 2. После 50 циклов обновления меняем анархию /hub -> /an103..108
                if (refreshCycles >= 50) {
                    refreshCycles = 0;
                    client.player.closeHandledScreen();
                    state = SniperState.SWITCHING_HUB;
                    nextActionTime = now + jitter(500, 250, 600);
                    return;
                }

                // 3. Кликаем строго по 46-му слоту (индекс 45) для обновления аукциона
                if (handler.slots.size() > REFRESH_SLOT_INDEX && !handler.slots.get(REFRESH_SLOT_INDEX).getStack().isEmpty()) {
                    client.interactionManager.clickSlot(
                            handler.syncId, REFRESH_SLOT_INDEX, 0, SlotActionType.PICKUP, client.player
                    );
                    refreshCycles++;
                    cyclesBeforePause--;

                    if (cyclesBeforePause <= 0) {
                        cyclesBeforePause = 14 + random.nextInt(12);
                        nextActionTime = now + jitter(1500, 500, 1400);
                    } else {
                        nextActionTime = now + jitter(speed.refreshDelayMs, 120, 320);
                    }
                } else {
                    client.player.closeHandledScreen();
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + jitter(600, 200, 450);
                }
            }

            case SELLING_BOUGHT -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(300, 120, 280);
                    return;
                }

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
                nextActionTime = now + jitter(650, 250, 550);
            }

            case SWITCHING_HUB -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                }
                client.getNetworkHandler().sendChatCommand("hub");
                state = SniperState.SWITCHING_ANARCHY;
                nextActionTime = now + jitter(2000, 500, 1200);
            }

            case SWITCHING_ANARCHY -> {
                int anarchyNum = 103 + random.nextInt(6);
                client.getNetworkHandler().sendChatCommand("an" + anarchyNum);
                client.player.sendMessage(Text.literal("§d[AuctionBot] §fПереход на /an" + anarchyNum), false);
                state = SniperState.OPENING_AH;
                nextActionTime = now + jitter(2800, 700, 1500);
            }

            case STORAGE_OPEN_AH -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(300, 120, 250);
                    return;
                }
                client.getNetworkHandler().sendChatCommand("ah");
                stateStartTime = now;
                state = SniperState.STORAGE_WAIT_MAIN;
                nextActionTime = now + jitter(450, 180, 350);
            }

            case STORAGE_WAIT_MAIN -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    if (now - stateStartTime > 3500) state = SniperState.OPENING_AH;
                    return;
                }

                GenericContainerScreenHandler handler = containerScreen.getScreenHandler();
                // Кликаем строго по 47-му слоту (индекс 46 — Хранилище)
                if (handler.slots.size() > STORAGE_SLOT_INDEX && !handler.slots.get(STORAGE_SLOT_INDEX).getStack().isEmpty()) {
                    client.interactionManager.clickSlot(
                            handler.syncId, STORAGE_SLOT_INDEX, 0, SlotActionType.PICKUP, client.player
                    );
                    stateStartTime = now;
                    state = SniperState.STORAGE_WAIT_INNER;
                    nextActionTime = now + jitter(500, 180, 350);
                } else {
                    client.player.closeHandledScreen();
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + jitter(500, 180, 350);
                }
            }

            case STORAGE_WAIT_INNER -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    if (now - stateStartTime > 3500) state = SniperState.OPENING_AH;
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
                nextActionTime = now + jitter(550, 200, 450);
            }
        }
    }
}
