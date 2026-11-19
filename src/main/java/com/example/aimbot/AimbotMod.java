package com.example.aimbot;

import com.example.aimbot.gui.AimbotScreen;
import com.example.aimbot.modules.AimbotModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class AimbotMod implements ClientModInitializer {
    public static AimbotModule module;
    public static KeyBinding openGuiKey;
    private static float hudAlpha = 0f;
    private static long lastToggleTime = 0;

    @Override
    public void onInitializeClient() {
        module = new AimbotModule();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.aimbot.opengui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_CONTROL, "Aimbot"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new AimbotScreen(module));
            }

            if (client.player != null && client.currentScreen == null) {
                if (InputUtil.isKeyPressed(client.getWindow().getHandle(), module.aimbotKey)) {
                    if (System.currentTimeMillis() - lastToggleTime > 200) {
                        module.enabled = !module.enabled;
                        lastToggleTime = System.currentTimeMillis();
                        
                        if (module.enabled) client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 2.0f);
                        else client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 1.0f, 0.5f);
                    }
                }
            }

            hudAlpha = MathHelper.lerp(0.1f, hudAlpha, module.enabled ? 1.0f : 0.0f);
            module.tick();
        });

        // ИСПРАВЛЕНО: Используем MinecraftClient.getInstance().getTickDelta()
        WorldRenderEvents.END.register(context -> {
            if (module.enabled && MinecraftClient.getInstance().player != null) {
                module.applyInterpolation(MinecraftClient.getInstance().getTickDelta());
            }
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || hudAlpha < 0.01f) return;

            int x = module.hudX.get().intValue();
            int y = module.hudY.get().intValue();
            
            int alphaInt = (int)(hudAlpha * 255) << 24;
            context.fill(x, y, x + 90, y + 28, alphaInt | 0x0A0A0F);
            context.fill(x, y, x + 2, y + 28, alphaInt | 0x7B2FBE);
            
            context.drawTextWithShadow(mc.textRenderer, "FPS: " + mc.getCurrentFps(), x + 8, y + 5, alphaInt | 0xAAAAAA);
            context.drawTextWithShadow(mc.textRenderer, "§lAIM: ON", x + 8, y + 15, alphaInt | 0xFFFFFF);
        });
    }
}
