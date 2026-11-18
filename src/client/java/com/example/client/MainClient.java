package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class MainClient implements ClientModInitializer {
    public static boolean noSlowEnabled = false;
    public static boolean killAuraEnabled = false;
    
    private static boolean jKeyWasDown = false;
    private static boolean kKeyWasDown = false;
    
    private static float lastYaw = 0;
    private static float lastPitch = 0;
    private static float acceleration = 0;
    private static int attackCooldown = 0;

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
                if (!killAuraEnabled) {
                    lastYaw = client.player.getYRot();
                    lastPitch = client.player.getXRot();
                }
            }
            kKeyWasDown = kDown;

            // --- ЛОГИКА NO-SLOW ---
            if (noSlowEnabled && client.player.isUsingItem()) {
                Vec3 motion = client.player.getDeltaMovement();
                client.player.setDeltaMovement(motion.x * 5.0, motion.y, motion.z * 5.0);
            }

            // --- ЛОГИКА KILLAURA ---
            if (killAuraEnabled && client.gameMode != null) {
                if (attackCooldown > 0) attackCooldown--;

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
                    AABB box = target.getBoundingBox();
                    Vec3 eyePos = client.player.getEyePosition();
                    double targetX = Mth.clamp(eyePos.x, box.minX, box.maxX);
                    double targetY = Mth.clamp(eyePos.y, box.minY, box.maxY);
                    double targetZ = Mth.clamp(eyePos.z, box.minZ, box.maxZ);

                    double dx = targetX - eyePos.x;
                    double dy = targetY - eyePos.y;
                    double dz = targetZ - eyePos.z;
                    double dist2D = Math.sqrt(dx * dx + dz * dz);

                    float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
                    float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, dist2D)));

                    float deltaYaw = Mth.wrapDegrees(targetYaw - lastYaw);
                    float deltaPitch = targetPitch - lastPitch;

                    float distToTarget = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
                    acceleration = Math.max(0.05f, Math.min(1.0f, distToTarget / 30.0f));

                    float newYaw = lastYaw + deltaYaw * acceleration;
                    float newPitch = lastPitch + deltaPitch * acceleration;

                    float gcd = getGCD();
                    newYaw -= (newYaw - lastYaw) % gcd;
                    newPitch -= (newPitch - lastPitch) % gcd;

                    client.player.setYRot(newYaw);
                    client.player.setXRot(newPitch);
                    lastYaw = newYaw;
                    lastPitch = newPitch;

                    if (client.player.getAttackStrengthScale(0.5F) >= 1.0F && attackCooldown == 0) {
                        if (client.player.onGround()) {
                            client.player.jumpFromGround();
                        } else if (client.player.fallDistance > 0.0F) {
                            client.gameMode.attack(client.player, target);
                            client.player.swing(InteractionHand.MAIN_HAND);
                            attackCooldown = 2;
                        }
                    }
                }
            }
        });
    }

    private float getGCD() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double f = sensitivity * 0.6 + 0.2;
        double gcd = f * f * f * 8.0 * 0.15;
        return (float) gcd;
    }

    private void playToggleSound(Minecraft client, boolean enabled) {
        if (enabled) {
            client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        } else {
            client.player.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 1.0f);
        }
    }
}
