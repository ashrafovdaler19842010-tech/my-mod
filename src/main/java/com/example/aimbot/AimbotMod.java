package com.example.aimbot;

import com.example.aimbot.gui.AimbotScreen;
import com.example.aimbot.modules.AimbotModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import org.lwjgl.glfw.GLFW;

public class AimbotMod implements ClientModInitializer {
    public static AimbotModule module;
    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        module = new AimbotModule();

        // Right Ctrl для GUI
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimbot.opengui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, "Aimbot"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Открытие GUI
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AimbotScreen(module));
                }
            }

            // Включение/Выключение по бинду из модуля
            if (client.player != null && client.currentScreen == null) {
                // InputUtil.isKeyPressed проверяет, зажата ли кнопка, которую мы забиндили
                if (InputUtil.isKeyPressed(client.getWindow().getHandle(), module.aimbotKey)) {
                    module.enabled = !module.enabled;
                    
                    // ЗВУК SYSTEM DLC
                    if (module.enabled) {
                        client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 2.0f); // Высокий звук (Вкл)
                    } else {
                        client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f); // Низкий звук (Выкл)
                    }

                    // Костыль, чтобы кнопка не зажималась на несколько тиков
                    try { Thread.sleep(200); } catch (InterruptedException e) {}
                }
            }

            module.tick();
        });

        // HUD Индикатор (Всегда на экране)
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            int x = mc.getWindow().getScaledWidth() - 80;
            int y = mc.getWindow().getScaledHeight() - 30;
            
            context.fill(x, y, x + 70, y + 18, 0xCC0A0A0F);
            context.fill(x, y, x + 2, y + 18, module.enabled ? 0xFF00FF00 : 0xFFFF0000);
            context.drawTextWithShadow(mc.textRenderer, "AIM: " + (module.enabled ? "ON" : "OFF"), x + 8, y + 5, 0xFFFFFFFF);
        });
    }
}
