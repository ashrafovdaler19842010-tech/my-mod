package com.example.aimbot.gui;

import com.example.aimbot.modules.AimbotModule;
import com.example.aimbot.settings.BoolSetting;
import com.example.aimbot.settings.DoubleSetting;
import com.example.aimbot.settings.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class AimbotScreen extends Screen {
    private static final int BG = 0xE60A0A0F;
    private static final int ACCENT = 0xFF7B2FBE;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_W = 480;
    private static final int PANEL_H = 420;

    private final AimbotModule module;
    private int startX, startY;
    private DoubleSetting draggingSlider = null;
    private boolean isBindingKey = false;
    
    private boolean expandFilters = true;
    private boolean expandSpeed = true;
    private boolean expandFunTime = true;
    private boolean expandHUD = false;

    public AimbotScreen(AimbotModule module) {
        super(Text.literal("Aimbot GUI"));
        this.module = module;
    }

    @Override
    protected void init() {
        startX = (width - PANEL_W) / 2;
        startY = (height - PANEL_H) / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(startX, startY, startX + PANEL_W, startY + PANEL_H, BG);
        context.fill(startX, startY, startX + PANEL_W, startY + 2, ACCENT);
        
        // Заголовок и Кнопка закрытия (крестик)
        context.drawTextWithShadow(textRenderer, "§lAIMBOT CONFIG", startX + 10, startY + 10, ACCENT);
        boolean hoverClose = mouseX >= startX + PANEL_W - 25 && mouseX <= startX + PANEL_W - 5 && mouseY >= startY + 5 && mouseY <= startY + 20;
        context.drawTextWithShadow(textRenderer, "§c[X]", startX + PANEL_W - 25, startY + 10, hoverClose ? 0xFFFFFFFF : 0xFFFF5555);

        // Верхняя панель
        int topY = startY + 30;
        context.fill(startX + 5, topY, startX + PANEL_W - 5, topY + 20, 0xFF222233);
        context.drawTextWithShadow(textRenderer, "§lAim Assist", startX + 10, topY + 6, TEXT_COLOR);
        
        String state = module.enabled ? "§aON" : "§cOFF";
        context.drawTextWithShadow(textRenderer, state, startX + PANEL_W - 90, topY + 6, TEXT_COLOR);
        
        String keyName = GLFW.glfwGetKeyName(module.aimbotKey, 0);
        if (keyName == null) keyName = "Key " + module.aimbotKey;
        String bindStr = isBindingKey ? "§e[...]" : "§7[" + keyName.toUpperCase() + "]";
        context.drawTextWithShadow(textRenderer, bindStr, startX + PANEL_W - 50, topY + 6, TEXT_COLOR);

        // Отрисовка меню
        int y = startY + 60;
        y = drawHeader(context, "Filters", y, expandFilters, mouseX, mouseY);
        if (expandFilters) {
            y = drawSetting(context, module.range, y, mouseX, mouseY);
            y = drawSetting(context, module.fov, y, mouseX, mouseY);
            y = drawSetting(context, module.ignoreWalls, y, mouseX, mouseY);
            y = drawSetting(context, module.randomPoint, y, mouseX, mouseY);
        }

        y = drawHeader(context, "Speed & Smoothness", y, expandSpeed, mouseX, mouseY);
        if (expandSpeed) {
            y = drawSetting(context, module.aimSpeed, y, mouseX, mouseY);
            y = drawSetting(context, module.aimStrength, y, mouseX, mouseY);
            y = drawSetting(context, module.smoothness, y, mouseX, mouseY);
            y = drawSetting(context, module.dynamicSpeed, y, mouseX, mouseY);
        }

        y = drawHeader(context, "FunTime Bypass", y, expandFunTime, mouseX, mouseY);
        if (expandFunTime) {
            y = drawSetting(context, module.gcd, y, mouseX, mouseY);
            y = drawSetting(context, module.jitter, y, mouseX, mouseY);
            y = drawSetting(context, module.saccade, y, mouseX, mouseY);
            y = drawSetting(context, module.randomSpeedMin, y, mouseX, mouseY);
            y = drawSetting(context, module.randomSpeedMax, y, mouseX, mouseY);
        }

        y = drawHeader(context, "HUD Settings", y, expandHUD, mouseX, mouseY);
        if (expandHUD) {
            y = drawSetting(context, module.hudX, y, mouseX, mouseY);
            y = drawSetting(context, module.hudY, y, mouseX, mouseY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private int drawHeader(DrawContext context, String title, int y, boolean expanded, int mouseX, int mouseY) {
        context.fill(startX + 5, y, startX + PANEL_W - 5, y + 16, 0xFF111118);
        context.drawTextWithShadow(textRenderer, (expanded ? "▼ " : "▶ ") + title, startX + 10, y + 4, ACCENT);
        return y + 18;
    }

    private int drawSetting(DrawContext context, Setting<?> setting, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= startX + 5 && mouseX <= startX + PANEL_W - 5 && mouseY >= y && mouseY <= y + 16;
        context.fill(startX + 15, y, startX + PANEL_W - 5, y + 16, hovered ? 0xFF2A2A38 : 0xFF1A1A24);
        
        if (hovered) context.drawTooltip(textRenderer, Text.literal(setting.description), mouseX, mouseY);

        context.drawTextWithShadow(textRenderer, setting.name, startX + 20, y + 4, TEXT_COLOR);

        if (setting instanceof DoubleSetting ds) {
            int sliderX = startX + 170;
            int sliderW = 180;
            context.fill(sliderX, y + 6, sliderX + sliderW, y + 10, 0xFF000000);
            double percent = (ds.get() - ds.min) / (ds.max - ds.min);
            context.fill(sliderX, y + 6, sliderX + (int)(sliderW * percent), y + 10, ACCENT);
            
            int circleX = sliderX + (int)(sliderW * percent);
            context.fill(circleX - 2, y + 4, circleX + 2, y + 12, 0xFFFFFFFF);
            
            String valStr = ds.getFormattedValue();
            context.drawTextWithShadow(textRenderer, valStr, startX + PANEL_W - 50, y + 4, 0xAAAAAA);
        } 
        else if (setting instanceof BoolSetting bs) {
            context.drawTextWithShadow(textRenderer, bs.isEnabled() ? "§aON" : "§cOFF", startX + PANEL_W - 40, y + 4, TEXT_COLOR);
        }
        return y + 18;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Клик по крестику (Закрыть ГУИ)
        if (mouseX >= startX + PANEL_W - 25 && mouseX <= startX + PANEL_W - 5 && mouseY >= startY + 5 && mouseY <= startY + 20) {
            this.close();
            return true;
        }

        int topY = startY + 30;
        if (mouseY >= topY && mouseY <= topY + 20) {
            if (mouseX >= startX + PANEL_W - 90 && mouseX <= startX + PANEL_W - 60) { module.enabled = !module.enabled; return true; }
            if (mouseX >= startX + PANEL_W - 50 && mouseX <= startX + PANEL_W - 10) { isBindingKey = true; return true; }
        }

        if (isBindingKey) return true;

        int y = startY + 60;
        if (mouseY >= y && mouseY <= y + 16) { expandFilters = !expandFilters; return true; }
        if (expandFilters) y += 18 * 5; else y += 18;
        
        if (mouseY >= y && mouseY <= y + 16) { expandSpeed = !expandSpeed; return true; }
        if (expandSpeed) y += 18 * 5; else y += 18;
        
        if (mouseY >= y && mouseY <= y + 16) { expandFunTime = !expandFunTime; return true; }
        if (expandFunTime) y += 18 * 6; else y += 18;

        if (mouseY >= y && mouseY <= y + 16) { expandHUD = !expandHUD; return true; }

        // Клики по настройкам
        y = startY + 60;
        y = checkSettingClick(module.range, y, mouseX, mouseY, expandFilters);
        y = checkSettingClick(module.fov, y, mouseX, mouseY, expandFilters);
        y = checkSettingClick(module.ignoreWalls, y, mouseX, mouseY, expandFilters);
        y = checkSettingClick(module.randomPoint, y, mouseX, mouseY, expandFilters);
        
        y = startY + 60 + (expandFilters ? 18*4 : 0) + 18;
        y = checkSettingClick(module.aimSpeed, y, mouseX, mouseY, expandSpeed);
        y = checkSettingClick(module.aimStrength, y, mouseX, mouseY, expandSpeed);
        y = checkSettingClick(module.smoothness, y, mouseX, mouseY, expandSpeed);
        y = checkSettingClick(module.dynamicSpeed, y, mouseX, mouseY, expandSpeed);

        y = startY + 60 + (expandFilters ? 18*4 : 0) + (expandSpeed ? 18*4 : 0) + 18*2;
        y = checkSettingClick(module.gcd, y, mouseX, mouseY, expandFunTime);
        y = checkSettingClick(module.jitter, y, mouseX, mouseY, expandFunTime);
        y = checkSettingClick(module.saccade, y, mouseX, mouseY, expandFunTime);
        y = checkSettingClick(module.randomSpeedMin, y, mouseX, mouseY, expandFunTime);
        y = checkSettingClick(module.randomSpeedMax, y, mouseX, mouseY, expandFunTime);

        y = startY + 60 + (expandFilters ? 18*4 : 0) + (expandSpeed ? 18*4 : 0) + (expandFunTime ? 18*5 : 0) + 18*3;
        y = checkSettingClick(module.hudX, y, mouseX, mouseY, expandHUD);
        y = checkSettingClick(module.hudY, y, mouseX, mouseY, expandHUD);

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int checkSettingClick(Setting<?> setting, int y, double mouseX, double mouseY, boolean expanded) {
        if (!expanded) return y;
        if (mouseX >= startX + 15 && mouseX <= startX + PANEL_W - 5 && mouseY >= y && mouseY <= y + 16) {
            if (setting instanceof BoolSetting bs) bs.toggle();
            else if (setting instanceof DoubleSetting ds) {
                draggingSlider = ds; 
                updateSlider(ds, mouseX);
            }
        }
        return y + 18;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider != null) { updateSlider(draggingSlider, mouseX); return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateSlider(DoubleSetting ds, double mouseX) {
        double percent = (mouseX - (startX + 170)) / 180.0;
        percent = MathHelper.clamp(percent, 0.0, 1.0);
        ds.set(ds.min + percent * (ds.max - ds.min));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isBindingKey) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) module.aimbotKey = keyCode;
            isBindingKey = false;
            return true;
        }
        // Закрыть на ESC
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
