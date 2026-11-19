package com.example.aimbot.hud;

import com.example.aimbot.modules.AimbotModule;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class HudRenderer implements HudRenderCallback {
    private final AimbotModule module;

    public HudRenderer(AimbotModule module) { this.module = module; }

    @Override
    public void onHudRender(DrawContext ctx, float tickDelta) {
        if (!module.isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;

        int x = mc.getWindow().getScaledWidth() - 80;
        int y = mc.getWindow().getScaledHeight() - 30;
        ctx.fill(x, y, x + 70, y + 18, 0xCC0A0A0F);
        ctx.fill(x, y, x + 2, y + 18, 0xFF7B2FBE);
        ctx.drawTextWithShadow(mc.textRenderer, "AIM: ON", x + 8, y + 5, 0xFF7B2FBE);
    }
}
