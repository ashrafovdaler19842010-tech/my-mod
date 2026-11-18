package com.auctionbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class AuctionBotClient implements ClientModInitializer {

    private boolean wasRightCtrlPressed = false;

    @Override
    public void onInitializeClient() {
        BotConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.getWindow() == null) return;

            // Открытие меню настроек по Right Ctrl
            boolean isRightCtrl = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
            if (isRightCtrl && !wasRightCtrlPressed) {
                if (client.currentScreen instanceof BotConfigScreen) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    client.setScreen(new BotConfigScreen());
                }
            }
            wasRightCtrlPressed = isRightCtrl;

            // Тики сканера и снайпера
            MarketScanner.INSTANCE.tick(client);
            AuctionSniper.INSTANCE.tick(client);
        });

        // Мини-HUD статуса в левом верхнем углу экрана
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            String line1 = "§6[AuctionBot] " + AuctionSniper.INSTANCE.getHudStatus();
            String line2 = "§7Порог: §f" + BotConfig.INSTANCE.buyPercent + "% §7| Скорость: §f" + BotConfig.INSTANCE.speedMode.displayName + " §8[R-Ctrl]";

            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line1), 8, 8, 0xFFFFFF);
            drawContext.drawTextWithShadow(client.textRenderer, Text.literal(line2), 8, 19, 0xCCCCCC);
        });
    }
}
