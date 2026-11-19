package com.example.aimbot.gui;

import com.example.aimbot.modules.AimbotModule;
import com.example.aimbot.settings.BoolSetting;
import com.example.aimbot.settings.DoubleSetting;
import com.example.aimbot.settings.EnumSetting;
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
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 320;

    private final AimbotModule module;
    private int startX, startY;
    private DoubleSetting draggingSlider = null;
    private boolean isBindingKey = false;

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
        // Фон (SystemDLC)
        context.fill(startX, startY, startX + PANEL_W, startY + PANEL_H, BG);
        context.fill(startX, startY, startX + PANEL_W, startY + 2, ACCENT);
        context.drawTextWithShadow(textRenderer, "§lAIMBOT CONFIG", startX + 10, startY + 10, ACCENT);

        // Верхняя панель: Вкл/Выкл и Бинд
        int topY = startY + 30;
        context.fill(startX + 5, topY, startX + PANEL_W - 5, topY + 20, 0xFF222233);
        context.drawTextWithShadow(textRenderer, "§lAim Assist", startX + 10, topY + 6, TEXT_COLOR);
        
        // Кнопка вкл/выкл
        String state = module.enabled ? "§aON" : "§cOFF";
        context.drawTextWithShadow(textRenderer, state, startX + PANEL_W - 90, topY + 6, TEXT_COLOR);
        
        // Кнопка бинда
        String bindStr = isBindingKey ? "§e[BINDING...]" : "§7[" + InputUtil.getKeyName(module.aimbotKey) + "]";
        context.drawTextWithShadow(textRenderer, bindStr, startX + PANEL_W - 50, topY + 6, TEXT_COLOR);

        // Рисуем настройки
        int y = startY + 60;
        for (Setting<?> setting : module.settings) {
            context.fill(startX + 5, y, startX + PANEL_W - 5, y + 16, 0xFF1A1A24);
            context.drawTextWithShadow(textRenderer, setting.name, startX + 10, y + 4, TEXT_COLOR);

            if (setting instanceof DoubleSetting ds) {
                // Ползунок
                int sliderX = startX + 150;
                int sliderW = 150;
                context.fill(sliderX, y + 6, sliderX + sliderW, y + 10, 0xFF000000);
                double percent = (ds.get() - ds.min) / (ds.max - ds.min);
                context.fill(sliderX, y + 6, sliderX + (int)(sliderW * percent), y + 10, ACCENT);
                
                // Кружок
                int circleX = sliderX + (int)(sliderW * percent);
                context.fill(circleX - 2, y + 4, circleX + 2, y + 12, 0xFFFFFFFF);
                
                String valStr = String.format("%.2f", ds.get());
                context.drawTextWithShadow(textRenderer, valStr, startX + PANEL_W - 40, y + 4, 0xAAAAAA);
            } 
            else if (setting instanceof BoolSetting bs) {
                context.drawTextWithShadow(textRenderer, bs.isEnabled() ? "§aON" : "§cOFF", startX + PANEL_W - 40, y + 4, TEXT_COLOR);
            } 
            else if (setting instanceof EnumSetting<?> es) {
                String valStr = es.get().name();
                context.drawTextWithShadow(textRenderer, valStr, startX + PANEL_W - 40 - textRenderer.getWidth(valStr), y + 4, 0xAAAAAA);
            }

            y += 20;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Клик по Верхней панели (Вкл/Выкл и Бинд)
        int topY = startY + 30;
        if (mouseY >= topY && mouseY <= topY + 20) {
            if (mouseX >= startX + PANEL_W - 90 && mouseX <= startX + PANEL_W - 60) {
                module.enabled = !module.enabled;
                return true;
            }
            if (mouseX >= startX + PANEL_W - 50 && mouseX <= startX + PANEL_W - 10) {
                isBindingKey = true;
                return true;
            }
        }

        if (isBindingKey) return true;

        // Клик по настройкам
        int y = startY + 60;
        for (Setting<?> setting : module.settings) {
            if (mouseX >= startX + 5 && mouseX <= startX + PANEL_W - 5 && mouseY >= y && mouseY <= y + 16) {
                if (setting instanceof BoolSetting bs) {
                    bs.toggle();
                } else if (setting instanceof EnumSetting<?> es) {
                    es.next();
                } else if (setting instanceof DoubleSetting ds && button == 0) {
                    // Начинаем тянуть ползунок
                    draggingSlider = ds;
                    updateSlider(ds, mouseX);
                }
                return true;
            }
            y += 20;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            updateSlider(draggingSlider, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingSlider = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateSlider(DoubleSetting ds, double mouseX) {
        double percent = (mouseX - (startX + 150)) / 150.0;
        percent = MathHelper.clamp(percent, 0.0, 1.0);
        ds.set(ds.min + percent * (ds.max - ds.min));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isBindingKey) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) {
                module.aimbotKey = keyCode;
            }
            isBindingKey = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() { return false; }
}
