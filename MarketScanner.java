package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CommandSuggestionsS2CPacket;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;

public class MarketScanner {
    public static final MarketScanner INSTANCE = new MarketScanner();

    private boolean passiveLearningEnabled = true;
    private int learnedItemsCount = 0;

    public boolean isRunning() {
        return false; // Больше не блокирует снайпер агрессивным перебором
    }

    public String getStatusText() {
        return "Пассивный скан (база: " + BotConfig.INSTANCE.items.size() + ")";
    }

    public void startFullScan(MinecraftClient client) {
        passiveLearningEnabled = true;
        if (client.player != null) {
            client.player.sendMessage(Text.literal(
                    "§b[AuctionBot] §fЛегитный режим: цены обновляются автоматически при просмотре /ah и /ah search."
            ), false);
        }
    }

    public void stopScan() {
        // В пассивном режиме остановка не требуется
    }

    public void tick(MinecraftClient client) {
        if (!passiveLearningEnabled || client.player == null) return;

        // Если открыт любой сундук аукциона — тихо запоминаем минимальные цены без отправки команд
        if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
            learnFromOpenScreen(containerScreen, client);
        }
    }

    public void learnFromOpenScreen(GenericContainerScreen screen, MinecraftClient client) {
        GenericContainerScreenHandler handler = screen.getScreenHandler();
        int totalSlots = handler.getRows() * 9;
        if (totalSlots < 54) return; // Сканируем только страницы аукциона (54 слота)

        Map<String, Double> lowestInPage = new HashMap<>();

        for (int i = 0; i < 45; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;

            double unitPrice = PriceParser.parseUnitPrice(stack, client);
            if (unitPrice <= 0) continue;

            String cleanName = PriceParser.getCleanItemName(stack);
            if (cleanName.isEmpty()) continue;

            double prev = lowestInPage.getOrDefault(cleanName, Double.MAX_VALUE);
            if (unitPrice < prev) {
                lowestInPage.put(cleanName, unitPrice);
            }
        }

        boolean changed = false;
        for (Map.Entry<String, Double> entry : lowestInPage.entrySet()) {
            String key = entry.getKey().toLowerCase();
            BotConfig.ItemData existing = BotConfig.INSTANCE.items.get(key);
            if (existing == null) {
                BotConfig.INSTANCE.updateItemPrice(entry.getKey(), entry.getValue());
                learnedItemsCount++;
                changed = true;
            } else if (entry.getValue() < existing.unitPrice) {
                // Обновляем рыночную цену вниз только если разница разумная (не ошибочный лот за 1 монету)
                if (entry.getValue() >= existing.unitPrice * 0.5) {
                    existing.unitPrice = entry.getValue();
                    changed = true;
                }
            }
        }

        if (changed) {
            BotConfig.save();
        }
    }

    public boolean handleSuggestionsPacket(CommandSuggestionsS2CPacket packet) {
        return false; // Никогда не перехватываем и не блокируем пакеты сервера
    }
}
