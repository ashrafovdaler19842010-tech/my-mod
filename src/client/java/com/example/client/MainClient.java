package com.example.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class MainClient implements ClientModInitializer {
    // Настройки
    public static boolean killAuraEnabled = false;
    public static boolean autoTune = false;
    public static boolean moveCorrection = true;
    public static double range = 3.0;
    public static float fov = 360.0f;

    // Клавиши
    private static boolean xKeyWasDown = false;
    private static boolean rCtrlWasDown = false;

    // Ротация
    private static float lastYaw = 0;
    private static float lastPitch = 0;
    private static float accel = 0;
    private static int attackCooldown = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.getWindow() == null || client.level == null) return;
            long win = client.getWindow().getWindow();

            // Меню (Right Ctrl)
            boolean rCtrl = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_CONTROL);
            if (rCtrl && !rCtrlWasDown) {
                if (client.screen instanceof GuiScreen) client.setScreen(null);
                else if (client.screen == null) client.setScreen(new GuiScreen());
            }
            rCtrlWasDown = rCtrl;

            // Вкл/Выкл (X)
            boolean xDown = InputConstants.isKeyDown(win, GLFW.GLFW_KEY_X);
            if (xDown && !xKeyWasDown && client.screen == null) {
                killAuraEnabled = !killAuraEnabled;
                playSound(killAuraEnabled);
                if (!killAuraEnabled) {
                    lastYaw = client.player.getYRot();
                    lastPitch = client.player.getXRot();
                }
            }
            xKeyWasDown = xDown;

            if (!killAuraEnabled) return;

            // Авто-подбор (Auto-Tune)
            float gcdMult = 1.0f;
            float speedMult = 1.0f;
            if (autoTune) {
                // Каждые полсекунды меняем параметры, чтобы найти незаметные
                gcdMult = 0.3f + (System.currentTimeMillis() % 1000) / 1000.0f;
                speedMult = 0.5f + (System.currentTimeMillis() % 500) / 500.0f;
            }

            // Логика поиска цели
            double closestDist = range;
            Entity target = null;
            Vec3 eyePos = client.player.getEyePosition();

            for (Entity entity : client.level.getEntities(client.player, client.player.getBoundingBox().inflate(range))) {
                if (entity instanceof LivingEntity living && living != client.player && living.isAlive()) {
                    // Проверка FOV (угла обзора)
                    Vec3 targetVec = living.getEyePosition().subtract(eyePos).normalize();
                    Vec3 lookVec = client.player.getViewVector(1.0f);
                    double angle = Math.toDegrees(Math.acos(lookVec.dot(targetVec)));

                    if (angle <= fov / 2.0) {
                        double dist = client.player.distanceTo(living);
                        if (dist <= closestDist) {
                            closestDist = dist;
                            target = living;
                        }
                    }
                }
            }

            if (target != null) {
                // Коррекция движения (предсказание позиции)
                Vec3 targetPos = target.position();
                if (moveCorrection) {
                    targetPos = targetPos.add(target.getDeltaMovement().scale(2.0));
                }
                
                AABB box = target.getBoundingBox();
                double targetX = Mth.clamp(targetPos.x, box.minX, box.maxX);
                double targetY = Mth.clamp(targetPos.y + target.getBbHeight() / 2.0, box.minY, box.maxY);
                double targetZ = Mth.clamp(targetPos.z, box.minZ, box.maxZ);

                double dx = targetX - eyePos.x;
                double dy = targetY - eyePos.y;
                double dz = targetZ - eyePos.z;
                double dist2D = Math.sqrt(dx * dx + dz * dz);

                float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
                float targetPitch = (float) (-Math.toDegrees(Math.atan2(dy, dist2D)));

                float deltaYaw = Mth.wrapDegrees(targetYaw - lastYaw);
                float deltaPitch = targetPitch - lastPitch;

                float distToTarget = (float) Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
                accel = Math.max(0.05f, Math.min(1.0f, (distToTarget / 30.0f) * speedMult));

                float newYaw = lastYaw + deltaYaw * accel;
                float newPitch = lastPitch + deltaPitch * accel;

                // GCD (Шаги мыши)
                float gcd = getGCD() * gcdMult;
                newYaw -= (newYaw - lastYaw) % gcd;
                newPitch -= (newPitch - lastPitch) % gcd;

                client.player.setYRot(newYaw);
                client.player.setXRot(newPitch);
                lastYaw = newYaw;
                lastPitch = newPitch;

                // Логика атаки (Только криты)
                if (attackCooldown > 0) {
                    attackCooldown--;
                } else {
                    if (client.player.getAttackStrengthScale(0.5F) >= 1.0F) {
                        // Бьем ТОЛЬКО если падаем (крит) и не на земле
                        if (!client.player.onGround() && client.player.fallDistance > 0.0F && client.player.getDeltaMovement().y < 0) {
                            client.gameMode.attack(client.player, target);
                            client.player.swing(InteractionHand.MAIN_HAND);
                            
                            // Быстрые криты: сбрасываем кулдаун атаки
                            client.player.attackStrengthTicker = 0; 
                            
                            attackCooldown = 2; 
                        }
                    }
                }
            }
        });

        // HP Bypass (Рендер ХП над головой)
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!killAuraEnabled) return;
            var matrices = context.matrixStack();
            if (matrices == null) return;
            
            for (Entity entity : context.world().entitiesForRendering()) {
                if (entity instanceof LivingEntity living && living != Minecraft.getInstance().player && living.isAlive()) {
                    double dist = Minecraft.getInstance().player.distanceTo(living);
                    if (dist <= range + 10) {
                        Vec3 pos = living.position().add(0, living.getBbHeight() + 0.4, 0);
                        matrices.pushPose();
                        matrices.translate(pos.x - context.camera().getPosition().x, pos.y - context.camera().getPosition().y, pos.z - context.camera().getPosition().z);
                        matrices.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
                        matrices.scale(-0.025F, -0.025F, 0.025F);
                        
                        float realHp = getRealHealth(living);
                        String text = String.format("§c%.1f ❤", realHp);
                        
                        Minecraft.getInstance().font.drawInBatch(text, -Minecraft.getInstance().font.width(text) / 2.0f, 0, 0xFFFFFF, false, matrices.last().pose(), context.consumers(), false, 0, 0xFFFFFFFF);
                        matrices.popPose();
                    }
                }
            }
        });
    }

    // Обход скрытия ХП через скорборд
    private static float getRealHealth(LivingEntity living) {
        var scoreboard = living.level().getScoreboard();
        var objective = scoreboard.getObjective("health");
        if (objective != null) {
            var score = scoreboard.getOrCreatePlayerScore(living.getScoreboardName(), objective);
            if (score != null) {
                return score.getScore();
            }
        }
        return living.getHealth();
    }

    private float getGCD() {
        double sensitivity = Minecraft.getInstance().options.sensitivity().get();
        double f = sensitivity * 0.6 + 0.2;
        double gcd = f * f * f * 8.0 * 0.15;
        return (float) gcd;
    }

    private void playSound(boolean enabled) {
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                enabled ? SoundEvents.EXPERIENCE_ORB_PICKUP : SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f
            )
        );
    }
}
