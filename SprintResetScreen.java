package com.sprintreset;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class SprintResetScreen extends Screen {

    public SprintResetScreen() {
        super(Text.literal("SprintReset Settings"));
    }

    @Override
    protected void init() {
        int panelW = 230;
        int panelH = 186;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;
        int btnW = panelW - 24;
        int btnX = x + 12;

        // 1. Вкл / Выкл сброса спринта
        this.addDrawableChild(ButtonWidget.builder(
            getEnabledText(),
            btn -> {
                SprintResetClient.enabled = !SprintResetClient.enabled;
                btn.setMessage(getEnabledText());
            }
        ).dimensions(btnX, y + 28, btnW, 20).build());

        // 2. Выбор режима (W-TAP / S-TAP / SPRINT)
        this.addDrawableChild(ButtonWidget.builder(
            getModeText(),
            btn -> {
                SprintResetClient.currentMode = SprintResetClient.currentMode.next();
                btn.setMessage(getModeText());
            }
        ).dimensions(btnX, y + 52, btnW, 20).build());

        // 3. Слайдер задержки перед сбросом (0 - 150 мс)
        this.addDrawableChild(new MsSlider(
            btnX, y + 76, btnW, 20,
            "Задержка до сброса", 0, 150, SprintResetClient.startDelayMs,
            val -> SprintResetClient.startDelayMs = val
        ));

        // 4. Слайдер времени остановки (10 - 200 мс)
        this.addDrawableChild(new MsSlider(
            btnX, y + 100, btnW, 20,
            "Время остановки", 10, 200, SprintResetClient.holdDurationMs,
            val -> SprintResetClient.holdDurationMs = val
        ));

        // 5. AutoSprint (Ц -> Left Ctrl)
        this.addDrawableChild(ButtonWidget.builder(
            getAutoSprintText(),
            btn -> {
                SprintResetClient.autoSprint = !SprintResetClient.autoSprint;
                btn.setMessage(getAutoSprintText());
            }
        ).dimensions(btnX, y + 124, btnW, 20).build());

        // 6. Показ мини-HUD на экране
        this.addDrawableChild(ButtonWidget.builder(
            getHudText(),
            btn -> {
                SprintResetClient.showHud = !SprintResetClient.showHud;
                btn.setMessage(getHudText());
            }
        ).dimensions(btnX, y + 148, btnW, 20).build());
    }

    private Text getEnabledText() {
        return Text.literal("Сброс спринта: " + (SprintResetClient.enabled ? "§aВКЛ" : "§cВЫКЛ"));
    }

    private Text getModeText() {
        return Text.literal("Режим: §b" + SprintResetClient.currentMode.label);
    }

    private Text getAutoSprintText() {
        return Text.literal("Авто-бег (Ц -> Ctrl): " + (SprintResetClient.autoSprint ? "§aВКЛ" : "§cВЫКЛ"));
    }

    private Text getHudText() {
        return Text.literal("Мини-HUD на экране: " + (SprintResetClient.showHud ? "§aВКЛ" : "§cВЫКЛ"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = 230;
        int panelH = 186;
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        context.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, 0xFF00E5FF);
        context.fill(x, y, x + panelW, y + panelH, 0xE612151E);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§b§lSprintReset §f| Настройки"), x + panelW / 2, y + 10, 0xFFFFFF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static class MsSlider extends SliderWidget {
        private final String prefix;
        private final long min;
        private final long max;
        private final java.util.function.LongConsumer onChange;

        public MsSlider(int x, int y, int width, int height, String prefix, long min, long max, long current, java.util.function.LongConsumer onChange) {
            super(x, y, width, height, Text.empty(), (double) (current - min) / (max - min));
            this.prefix = prefix;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            long val = Math.round(min + (max - min) * this.value);
            this.setMessage(Text.literal(prefix + ": §e" + val + " мс"));
        }

        @Override
        protected void applyValue() {
            long val = Math.round(min + (max - min) * this.value);
            onChange.accept(val);
        }
    }
}
