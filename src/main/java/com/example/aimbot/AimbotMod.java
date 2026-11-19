package com.example.aimbot;

import com.example.aimbot.modules.AimbotModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class AimbotMod implements ClientModInitializer {
    public static AimbotModule module;
    public static MinecraftClient mc;

    @Override
    public void onInitializeClient() {
        module = new AimbotModule();
        mc = MinecraftClient.getInstance();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // ТЕСТОВЫЙ ВКЛЮЧАТЕЛЬ АИМБОТА (Клавиша R)
            // Позже мы заменим это на бинд из GUI
            if (client.options.useKey.isPressed() != false) { /* Пустышка */ }
            
            // Тик модуля (крутит камеру)
            module.tick();
        });
        
        System.out.println("Aimbot Mod Initialized! Engine Ready.");
    }
}
