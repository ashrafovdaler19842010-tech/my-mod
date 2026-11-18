package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class MainClient implements ClientModInitializer {
    public static boolean noSlowEnabled = false;
    public static boolean killAuraEnabled = false;
    
    private static boolean jKeyWasDown = false;
    private static boolean kKeyWasDown = false;
    private static int attackDelay = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getWindow() == null || client.level == null) return;
            
            long win = client.getWindow().getWindow();
            
            // J - NoSlow
            boolean jDown = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_J);
            if (jDown && !jKeyWasDown) {
                noSlowEnabled = !noSlowEnabled;
                playToggleSound(client, noSlowEnabled);
            }
            jKeyWasDown = jDown;

            // K - KillAura
            boolean kDown = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_K);
            if (kDown && !kKeyWasDown) {
                killAuraEnabled = !killAuraEnabled;
                playToggleSound(client, killAuraEnabled);
            }
            kKeyWasDown = kDown;

            // --- ЛОГИКА NO-SLOW ---
            if (noSlowEnabled && client.player.isUsingItem()) {
                Vec3 motion = client.player.getDeltaMovement();
                client.player.setDeltaMovement(motion.x * 5.0, motion.y, motion.z * 5.0);
            }

            // --- ЛОГИКА KILLAURA ---
            if (killAuraEnabled && client.interactionManager != null) {
                if (attackDelay > 0) {
                    attackDelay--;
                } else {
                    double range = 3.0;
                    Entity target = null;
                    double closestDist = range;

                    for (Entity entity : client.level.getEntities(client.player, client.player.getBoundingBox().inflate(range))) {
                        if (entity instanceof LivingEntity living && living != client.player && living.isAlive()) {
                            double dist = client.player.distanceTo(living);
                            if (dist <= closestDist) {
                                closestDist = dist;
                                target = living;
                            }
                        }
                    }

                    if (target != null) {
                        if (client.player.getAttackStrengthScale(0.5F) >= 1.0F) {
                            client.interactionManager.attackEntity(client.player, target);
                            client.player.swing(InteractionHand.MAIN_HAND);
                            attackDelay = 2;
                        }
                    }
                }
            }
        });
    }

    private void playToggleSound(net.minecraft.client.Minecraft client, boolean enabled) {
        if (enabled) {
            client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            client.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f);
        }
    }
}
