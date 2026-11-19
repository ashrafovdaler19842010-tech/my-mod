package com.example.aimbot.gui;

import com.example.aimbot.modules.AimbotModule;
import com.example.aimbot.settings.BoolSetting;
import com.example.aimbot.settings.DoubleSetting;
import com.example.aimbot.settings.EnumSetting;
import com.example.aimbot.settings.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class AimbotScreen extends Screen {
    private static final int BG = 0xE60A0A0F;
    private static final int ACCENT = 0xFF7B2FBE;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_W = 400;
    private static final int PANEL_H = 300;

    private final AimbotModule module;
    private int startX, startY;

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
        // Отрисовка фона (стиль SystemDLC)
        context.fill(startX, startY, startX + PANEL_W, startY + PANEL_H, BG);
        context.fill(startX, startY, startX + PANEL_W, startY + 2, ACCENT); // Фиолетовая полоска
        context.drawTextWithShadow(textRenderer, "§lAIMBOT CONFIG", startX + 10, startY + 10, ACCENT);

        int y = startY + 30;
        for (Setting<?> setting : module.settings) {
            // Отрисовка фона строки настроек
            context.fill(startX + 5, y, startX + PANEL_W - 5, y + 16, 0xFF1A1A24);
            context.drawTextWithShadow(textRenderer, setting.name, startX + 10, y + 4, TEXT_COLOR);

            // Отрисовка значения (справа)
            String valStr = "";
            if (setting instanceof DoubleSetting ds) {
                valStr = String.format("%.2f", ds.get());
            } else if (setting instanceof BoolSetting bs) {
                valStr = bs.isEnabled() ? "§aON" : "§cOFF";
            } else if (setting instanceof EnumSetting<?> es) {
                valStr = es.get().name();
            }
            
            int valWidth = textRenderer.getWidth(valStr);
            context.drawTextWithShadow(textRenderer, valStr, startX + PANEL_W - 20 - valWidth, y + 4, 0xFFAAAAAA);

            // Логика кликов (ЛКМ - переключить, ПКМ - бинд)
            if (mouseX >= startX + 5 && mouseX <= startX + PANEL_W - 5 && mouseY >= y && mouseY <= y + 16) {
                context.fill(startX + 5, y, startX + PANEL_W - 5, y + 16, 0x33FFFFFF); // Подсветка при наведении
            }

            y += 20; // Отступ для следующей настройки
        }
        
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = startY + 30;
        for (Setting<?> setting : module.settings) {
            if (mouseX >= startX + 5 && mouseX <= startX + PANEL_W - 5 && mouseY >= y && mouseY <= y + 16) {
                
                if (button == 0) { // ЛКМ
                    if (setting instanceof BoolSetting bs) bs.toggle();
                    else if (setting instanceof DoubleSetting ds) ds.set(ds.get() + 0.5); // Тестовое увеличение
                    else if (setting instanceof EnumSetting<?> es) es.next();
                } 
                else if (button == 1) { // ПКМ - Тут будет логика бинда клавиши
                    // Для реализации бинда нужно перехватывать keyPressed, добавим это позже
                    System.out.println("Right click on: " + setting.name);
                }
                return true;
            }
            y += 20;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false; // Чтобы игра не вставала на паузу
    }
}
