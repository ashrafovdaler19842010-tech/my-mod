package com.example.aimbot;

import com.example.aimbot.gui.AimbotScreen;
import com.example.aimbot.hud.HudRenderer;
import com.example.aimbot.modules.AimbotModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class AimbotMod implements ClientModInitializer {
    public static final String MOD_ID = "aimbot";
    public static KeyBinding openGuiKey;
    public static AimbotModule module;

    @Override
    public void onInitializeClient() {
        module = new AimbotModule();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimbot.opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_CONTROL,
                "category.aimbot"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new AimbotScreen(module));
                }
            }
            module.tick();
        });

        HudRenderCallback.EVENT.register(new HudRenderer(module));
    }
}
