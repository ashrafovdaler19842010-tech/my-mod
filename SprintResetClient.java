package com.sprintreset;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class SprintResetClient implements ClientModInitializer {

    public enum ResetMode {
        W_TAP("W-TAP (Отпустить Ц)"),
        S_TAP("S-TAP (Нажать Ы)"),
        SPRINT_KEY("SPRINT (Сброс Ctrl)");

        public final String label;
        ResetMode(String label) { this.label = label; }

        public ResetMode next() {
            ResetMode[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    public static boolean enabled = true;
    public static boolean autoSprint = true;
    public static boolean showHud = true;
    public static ResetMode currentMode = ResetMode.W_TAP;
    public static long startDelayMs = 15;
    public static long holdDurationMs = 55;

    private static long resetStartTime = 0;
    private static long resetEndTime = 0;
    private static boolean isResetting = false;
    private static boolean rCtrlWasDown = false;

    @Override
    public void onInitializeClient() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (enabled && world.isClient && player == mc.player && player.isSprinting()) {
                long now = System.currentTimeMillis();
                resetStartTime = now + startDelayMs;
                resetEndTime = resetStartTime + holdDurationMs;
                isResetting = true;
            }
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getWindow() == null) return;

            boolean rCtrlDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_CONTROL);
            if (rCtrlDown && !rCtrlWasDown) {
                if (client.currentScreen instanceof SprintResetScreen) {
                    client.setScreen(null);
                } else if (client.currentScreen == null) {
                    client.setScreen(new SprintResetScreen());
                }
            }
            rCtrlWasDown = rCtrlDown;

            long now = System.currentTimeMillis();
            boolean activeResetPhase = false;

            if (enabled && isResetting) {
                if (now >= resetStartTime && now <= resetEndTime) {
                    activeResetPhase = true;
                    applyResetAction(client, true);
                } else if (now > resetEndTime) {
                    applyResetAction(client, false);
                    isResetting = false;
                }
            }

            if (autoSprint && client.currentScreen == null && !activeResetPhase) {
                if (client.options.forwardKey.isPressed() && !client.player.isSneaking()) {
                    client.options.sprintKey.setPressed(true);
                } else {
                    restorePhysicalKeyState(client, client.options.sprintKey);
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (!showHud || mc.player == null || mc.options.hudHidden) return;

            int x = 8, y = 8, w = 148, h = 42;
            context.fill(x, y, x + w, y + h, 0x90101218);
            context.fill(x, y, x + w, y + 2, enabled ? 0xFF00E5FF : 0xFFFF4D4D);

            String statusText = enabled ? "§bSprintReset §aON" : "§bSprintReset §cOFF";
            String modeText = "§7Mode: §f" + currentMode.name() + " §8(" + startDelayMs + "/" + holdDurationMs + "ms)";
            String sprintText = "§7AutoSprint: " + (autoSprint ? "§aON §8[W->Ctrl]" : "§cOFF");

            context.drawTextWithShadow(mc.textRenderer, statusText, x + 6, y + 6, 0xFFFFFF);
            context.drawTextWithShadow(mc.textRenderer, modeText, x + 6, y + 18, 0xFFFFFF);
            context.drawTextWithShadow(mc.textRenderer, sprintText, x + 6, y + 29, 0xFFFFFF);
        });
    }

    private void applyResetAction(MinecraftClient client, boolean active) {
        switch (currentMode) {
            case W_TAP -> {
                if (active) client.options.forwardKey.setPressed(false);
                else restorePhysicalKeyState(client, client.options.forwardKey);
            }
            case S_TAP -> {
                if (active) {
                    client.options.forwardKey.setPressed(false);
                    client.options.backKey.setPressed(true);
                } else {
                    restorePhysicalKeyState(client, client.options.forwardKey);
                    restorePhysicalKeyState(client, client.options.backKey);
                }
            }
            case SPRINT_KEY -> {
                if (active) {
                    client.player.setSprinting(false);
                    client.options.sprintKey.setPressed(false);
                } else {
                    restorePhysicalKeyState(client, client.options.sprintKey);
                }
            }
        }
    }

    public static void restorePhysicalKeyState(MinecraftClient client, KeyBinding keyBinding) {
        InputUtil.Key boundKey = InputUtil.fromTranslationKey(keyBinding.getBoundKeyTranslationKey());
        if (boundKey.getCategory() == InputUtil.Type.KEYSYM) {
            boolean isPhysicallyDown = InputUtil.isKeyPressed(client.getWindow().getHandle(), boundKey.getCode());
            keyBinding.setPressed(isPhysicallyDown);
        }
    }
}
