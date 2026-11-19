package com.example.aimbot.modules;

import com.example.aimbot.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AimbotModule {
    public final MinecraftClient mc = MinecraftClient.getInstance();
    public boolean enabled = false;
    public int aimbotKey = 82; // R
    public final Random random = new Random();
    
    public final List<Setting<?>> settings = new ArrayList<>();

    // --- Фильтры ---
    public DoubleSetting range = new DoubleSetting("Range", "Максимальная дистанция аима", 4.5, 1.0, 6.0);
    public DoubleSetting fov = new DoubleSetting("FOV", "Угол обзора (до 360°)", 120.0, 10.0, 360.0);
    public BoolSetting ignoreWalls = new BoolSetting("Ignore Walls", "Бить сквозь стены", false);

    // --- Скорость и плавность ---
    public DoubleSetting aimSpeed = new DoubleSetting("Aim Speed", "Скорость наводки", 1.0, 0.1, 3.0);
    public DoubleSetting aimStrength = new DoubleSetting("Aim Strength", "Сила притяжения", 0.15, 0.01, 1.0);
    public DoubleSetting smoothness = new DoubleSetting("Smoothness", "Плавность (0.1 быстро, 0.9 плавно)", 0.4, 0.1, 0.9);
    public DoubleSetting dynamicSpeed = new DoubleSetting("Dynamic Speed", "Ускорение если цель далеко", 1.5, 1.0, 3.0);
    
    // --- Человекоподобность и FunTime ---
    public BoolSetting randomPoint = new BoolSetting("Random Point", "Случайная точка в хитбоксе", true);
    public DoubleSetting saccade = new DoubleSetting("Saccade", "Микро-движения глаз", 0.0, 0.0, 2.0);
    public DoubleSetting gcd = new DoubleSetting("Rotation GCD", "Шаг мыши (обход Grim)", 0.0, 0.0, 5.0);
    public DoubleSetting jitter = new DoubleSetting("Jitter", "Случайное дрожание", 0.0, 0.0, 5.0);
    public DoubleSetting randomSpeedMin = new DoubleSetting("Rand Speed Min", "Мин. рандом скорость", 0.8, 0.1, 3.0);
    public DoubleSetting randomSpeedMax = new DoubleSetting("Rand Speed Max", "Макс. рандом скорость", 1.2, 0.1, 3.0);

    // --- Настройки HUD ---
    public DoubleSetting hudX = new DoubleSetting("HUD X", "Позиция HUD по горизонтали (от левого края)", 50.0, 0.0, 1920.0);
    public DoubleSetting hudY = new DoubleSetting("HUD Y", "Позиция HUD по вертикали (от верхнего края)", 50.0, 0.0, 1080.0);

    // Для интерполяции между кадрами
    private float interpYaw, interpPitch;
    private float lastYaw, lastPitch;
    private boolean needsInterp = false;

    public AimbotModule() {
        settings.add(range);
        settings.add(fov);
        settings.add(ignoreWalls);
        settings.add(aimSpeed);
        settings.add(aimStrength);
        settings.add(smoothness);
        settings.add(dynamicSpeed);
        settings.add(randomPoint);
        settings.add(saccade);
        settings.add(gcd);
        settings.add(jitter);
        settings.add(randomSpeedMin);
        settings.add(randomSpeedMax);
        settings.add(hudX);
        settings.add(hudY);
    }

    public void tick() {
        if (!enabled || mc.player == null || mc.world == null) {
            needsInterp = false;
            return;
        }

        Entity target = findTarget();
        if (target == null) {
            needsInterp = false;
            return;
        }

        Vec3d targetPos;
        if (randomPoint.get()) {
            Box box = target.getBoundingBox();
            double x = box.minX + random.nextDouble() * (box.maxX - box.minX);
            double y = box.minY + (0.7 + random.nextDouble() * 0.2) * (box.maxY - box.minY);
            double z = box.minZ + random.nextDouble() * (box.maxZ - box.minZ);
            targetPos = new Vec3d(x, y, z);
        } else {
            Vec3d eyePos = mc.player.getEyePos();
            Box box = target.getBoundingBox();
            targetPos = new Vec3d(
                MathHelper.clamp(eyePos.x, box.minX, box.maxX),
                MathHelper.clamp(eyePos.y, box.minY, box.maxY),
                MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
            );
        }

        double dx = targetPos.x - mc.player.getX();
        double dy = targetPos.y - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = targetPos.z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - currentPitch);

        // ИСПРАВЛЕНО: используем .floatValue() для объектов Double
        float currentSpeed = aimSpeed.get().floatValue();
        if (Math.abs(yawDelta) > 30) currentSpeed *= dynamicSpeed.get().floatValue();
        
        if (randomSpeedMax.get() > randomSpeedMin.get()) {
            float rand = randomSpeedMin.get().floatValue() + random.nextFloat() * (randomSpeedMax.get().floatValue() - randomSpeedMin.get().floatValue());
            currentSpeed *= rand;
        }

        float logNudgeYaw = (float) (Math.log(1 + Math.abs(yawDelta)) * aimStrength.get());
        float logNudgePitch = (float) (Math.log(1 + Math.abs(pitchDelta)) * aimStrength.get());
        
        logNudgeYaw = MathHelper.clamp(logNudgeYaw, 0, 45f);
        logNudgePitch = MathHelper.clamp(logNudgePitch, 0, 45f);

        if (gcd.get() > 0.01) {
            logNudgeYaw = (float) (Math.round(logNudgeYaw / gcd.get()) * gcd.get());
            logNudgePitch = (float) (Math.round(logNudgePitch / gcd.get()) * gcd.get());
        }
        if (jitter.get() > 0.01) {
            logNudgeYaw += (float) ((random.nextDouble() - 0.5) * jitter.get());
            logNudgePitch += (float) ((random.nextDouble() - 0.5) * jitter.get());
        }
        if (saccade.get() > 0.01) {
            logNudgeYaw += (float) (Math.sin(System.currentTimeMillis() / 50.0) * saccade.get());
            logNudgePitch += (float) (Math.cos(System.currentTimeMillis() / 50.0) * saccade.get());
        }

        float factor = (1.0f - smoothness.get().floatValue()) * currentSpeed;
        
        float stepYaw = yawDelta * factor * Math.signum(yawDelta) * logNudgeYaw;
        float stepPitch = pitchDelta * factor * Math.signum(pitchDelta) * logNudgePitch;

        // ИСПРАВЛЕНО: убраны prevYaw / prevPitch
        lastYaw = currentYaw;
        lastPitch = currentPitch;
        interpYaw = currentYaw + stepYaw;
        interpPitch = currentPitch + stepPitch;
        needsInterp = true;

        mc.player.setYaw(interpYaw);
        mc.player.setPitch(interpPitch);
    }

    public void applyInterpolation(float tickDelta) {
        if (!needsInterp || mc.player == null) return;
        float smoothYaw = MathHelper.lerp(tickDelta, lastYaw, interpYaw);
        float smoothPitch = MathHelper.lerp(tickDelta, lastPitch, interpPitch);
        mc.player.setYaw(smoothYaw);
        mc.player.setPitch(smoothPitch);
    }

    private Entity findTarget() {
        Entity bestTarget = null;
        double bestDistance = range.get();
        float currentYaw = mc.player.getYaw();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity) || entity == mc.player || !entity.isAlive()) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist > bestDistance) continue;

            double dx = entity.getX() - mc.player.getX();
            double dz = entity.getZ() - mc.player.getZ();
            float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            float yawDiff = Math.abs(MathHelper.wrapDegrees(targetYaw - currentYaw));
            
            if (yawDiff > fov.get() / 2.0) continue;
            if (!ignoreWalls.isEnabled() && !mc.player.canSee(entity)) continue;

            bestTarget = entity;
            bestDistance = dist;
        }
        return bestTarget;
    }
}
