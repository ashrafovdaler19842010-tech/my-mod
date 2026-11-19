package com.example.aimbot.gui;

import com.example.aimbot.modules.AimbotModule;
import com.example.aimbot.settings.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AimbotScreen extends Screen {
    private static final int BG = 0xE60A0A0F;
    private static final int ACCENT = 0xFF7B2FBE;
    private static final int PANEL_W = 460, PANEL_H = 340;

    private final AimbotModule module;
    private final List<SettingWidget> widgets = new ArrayList<>();
    private boolean bindingMode = false;
    private SettingWidget bindingTarget;

    public AimbotScreen(AimbotModule module) {
        super(Text.literal("Aimbot"));
        this.module = module;
        rebuild();
    }

    private void rebuild() {
        widgets.clear();
        for (Setting<?> s : module.settings.getAll()) {
            widgets.add(new SettingWidget(s, this));
        }
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);
        int x = (width - PANEL_W) / 2, y = (height - PANEL_H) / 2;

        ctx.fill(x, y, x + PANEL_W, y + PANEL_H, BG);
        ctx.fill(x, y, x + PANEL_W, y + 2, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "§lAIMBOT", x + 10, y + 8, 0xFFFFFF);

        int cy = y + 30;
        for (SettingWidget w : widgets) {
            cy = w.render(ctx, x + 10, cy, PANEL_W - 20, textRenderer, mx, my);
        }
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (SettingWidget w : widgets) {
            if (w.mouseClicked(mx, my, button)) return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (bindingMode && bindingTarget != null) {
            bindingTarget.setBoundKey(keyCode);
            bindingMode = false;
            bindingTarget = null;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void startBinding(SettingWidget target) {
        bindingMode = true;
        bindingTarget = target;
    }

    @Override
    public boolean shouldPause() { return false; }
}
