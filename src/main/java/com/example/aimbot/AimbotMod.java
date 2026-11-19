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
import org.lwjgl.glfw.GLFW;

public class AimbotMod implements ClientModInitializer {
    public static AimbotModule module;
    public static KeyBinding openGuiKey;
    public static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        module = new AimbotModule();

        // Бинд на открытие GUI (Right Ctrl)
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimbot.opengui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, "Aimbot"
        ));

        // Бинд на вкл/выкл аима (R)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimbot.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "Aimbot"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AimbotScreen(module));
                }
            }
            while (toggleKey.wasPressed()) {
                module.enabled = !module.enabled;
                if (client.player != null) {
                    client.player.sendMessage(net.minecraft.text.Text.literal("Aimbot: " + (module.enabled ? "§aON" : "§cOFF")), true);
                }
            }
            module.tick();
        });

        // HUD Индикатор
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            int x = mc.getWindow().getScaledWidth() - 80;
            int y = mc.getWindow().getScaledHeight() - 30;
            
            context.fill(x, y, x + 70, y + 18, 0xCC0A0A0F);
            context.fill(x, y, x + 2, y + 18, module.enabled ? 0xFF00FF00 : 0xFFFF0000); // Зеленый если вкл, красный если выкл
            context.drawTextWithShadow(mc.textRenderer, "AIM: " + (module.enabled ? "ON" : "OFF"), x + 8, y + 5, 0xFFFFFFFF);
        });
        
        System.out.println("Aimbot Mod Loaded! Engine & GUI Ready.");
    }
}
