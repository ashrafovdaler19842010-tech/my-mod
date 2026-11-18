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

    public void toggleBot(MinecraftClient client) {
        if (state == SniperState.OFF) {
            state = SniperState.OPENING_AH;
            refreshCycles = 0;
            cyclesBeforePause = 14 + random.nextInt(12);
            lastStorageCheckTime = System.currentTimeMillis();
            nextAntiAfkTime = System.currentTimeMillis() + 20_000L + random.nextInt(15_000);
            nextActionTime = System.currentTimeMillis() + jitter(200, 100, 300);
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§a[AuctionBot] §fЛегитный режим ЗАПУЩЕН."), false);
            }
        } else {
            state = SniperState.OFF;
            smoothTurnTicks = 0;
            if (client.player != null) {
                client.player.setSneaking(false);
                client.player.sendMessage(Text.literal("§c[AuctionBot] §fБот ОСТАНОВЛЕН."), false);
            }
        }
    }

    public String getHudStatus() {
        if (state == SniperState.OFF) return "§cВЫКЛ";
        return "§aЛегит-скупка §7(" + refreshCycles + "/50 | База: " + BotConfig.INSTANCE.items.size() + ")";
    }

    public void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null || client.interactionManager == null) return;
        if (state == SniperState.OFF) return;

        long now = System.currentTimeMillis();

        // 1. Плавный естественный Анти-АФК (раз в 25-45 секунд, без резких прыжков и дерганий)
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

        // Проверка хранилища раз в ~65-80 секунд
        if ((state == SniperState.OPENING_AH || state == SniperState.SCANNING_AH)
                && (now - lastStorageCheckTime >= 65_000L + random.nextInt(15_000))) {
            lastStorageCheckTime = now;
            state = SniperState.STORAGE_OPEN_AH;
            nextActionTime = now + jitter(300, 150, 400);
            return;
        }

        switch (state) {
            case OPENING_AH -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(speed.guiReactDelayMs, 150, 350);
                    return;
                }
                client.getNetworkHandler().sendChatCommand("ah");
                stateStartTime = now;
                state = SniperState.WAIT_HUMAN_REACTION;
                nextActionTime = now + jitter(speed.guiReactDelayMs, 220, 420);
            }

            case WAIT_HUMAN_REACTION -> {
                if (client.currentScreen instanceof GenericContainerScreen) {
                    state = SniperState.SCANNING_AH;
                    nextActionTime = now + jitter(180, 140, 340);
                } else if (now - stateStartTime > 3500) {
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + jitter(500, 200, 500);
                }
            }
            case SELLING_BOUGHT -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(350, 150, 350);
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
                nextActionTime = now + jitter(750, 300, 650);
            }

            case SWITCHING_HUB -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                }
                client.getNetworkHandler().sendChatCommand("hub");
                state = SniperState.SWITCHING_ANARCHY;
                nextActionTime = now + jitter(2200, 600, 1400);
            }

            case SWITCHING_ANARCHY -> {
                int anarchyNum = 103 + random.nextInt(6);
                client.getNetworkHandler().sendChatCommand("an" + anarchyNum);
                client.player.sendMessage(Text.literal("§d[AuctionBot] §fПереход на /an" + anarchyNum), false);
                state = SniperState.OPENING_AH;
                nextActionTime = now + jitter(3000, 800, 1600);
            }
            case STORAGE_OPEN_AH -> {
                if (client.currentScreen != null) {
                    client.player.closeHandledScreen();
                    nextActionTime = now + jitter(350, 150, 300);
                    return;
                }
                client.getNetworkHandler().sendChatCommand("ah");
                stateStartTime = now;
                state = SniperState.STORAGE_WAIT_MAIN;
                nextActionTime = now + jitter(500, 200, 400);
            }

            case STORAGE_WAIT_MAIN -> {
                if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
                    if (now - stateStartTime > 3500) state = SniperState.OPENING_AH;
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
                    nextActionTime = now + jitter(550, 200, 400);
                } else {
                    client.player.closeHandledScreen();
                    state = SniperState.OPENING_AH;
                    nextActionTime = now + jitter(600, 200, 400);
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
                nextActionTime = now + jitter(650, 250, 500);
            }
        }
    }

    private int findButtonSlot(GenericContainerScreenHandler handler, String... keywords) {
        int totalSlots = handler.getRows() * 9;
        int startSlot = Math.max(0, totalSlots - 9);

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
