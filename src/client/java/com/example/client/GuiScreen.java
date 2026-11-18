package com.example.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GuiScreen extends Screen {
    public GuiScreen() { super(Component.literal("KillAura Settings")); }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 60;

        this.addRenderableWidget(Button.builder(Component.literal("KillAura: " + (MainClient.killAuraEnabled ? "§aON" : "§cOFF")), b -> {
            MainClient.killAuraEnabled = !MainClient.killAuraEnabled;
            b.setMessage(Component.literal("KillAura: " + (MainClient.killAuraEnabled ? "§aON" : "§cOFF")));
        }).bounds(x, y, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Range: " + MainClient.range), b -> {
            MainClient.range += 0.5;
            if (MainClient.range > 6.0) MainClient.range = 3.0;
            b.setMessage(Component.literal("Range: " + MainClient.range));
        }).bounds(x, y + 25, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("FOV: " + MainClient.fov + "°"), b -> {
            MainClient.fov += 30;
            if (MainClient.fov > 360) MainClient.fov = 180;
            b.setMessage(Component.literal("FOV: " + MainClient.fov + "°"));
        }).bounds(x, y + 50, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Correction: " + (MainClient.moveCorrection ? "§aON" : "§cOFF")), b -> {
            MainClient.moveCorrection = !MainClient.moveCorrection;
            b.setMessage(Component.literal("Correction: " + (MainClient.moveCorrection ? "§aON" : "§cOFF")));
        }).bounds(x, y + 75, 200, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Auto-Tune: " + (MainClient.autoTune ? "§aON" : "§cOFF")), b -> {
            MainClient.autoTune = !MainClient.autoTune;
            b.setMessage(Component.literal("Auto-Tune: " + (MainClient.autoTune ? "§aON" : "§cOFF")));
        }).bounds(x, y + 100, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, "§l§bKillAura Bypass Menu", this.width / 2, this.height / 2 - 80, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean isPauseScreen() { return false; }
}
