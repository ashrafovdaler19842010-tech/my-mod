package com.example.aimbot.modules;

import com.example.aimbot.settings.*;
import com.example.aimbot.utils.RotationUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayList;
import java.util.List;

public class AimbotModule {
    public final MinecraftClient mc = MinecraftClient.getInstance();
    public boolean enabled = false;
    public int aimbotKey = 82;
    
    // Список для GUI
    public final List<Setting<?>> settings = new ArrayList<>();

    // --- Базовые фильтры ---
    public DoubleSetting range = new DoubleSetting("Range", "Дистанция аима", 4.5, 1.0, 10.0);
    public DoubleSetting fov = new DoubleSetting("FOV", "Угол обзора", 90.0, 10.0, 180.0);
    public BoolSetting ignoreWalls = new BoolSetting("Ignore Walls", "Сквозь стены", false);

    // --- Настройки для FunTime (Буст) ---
    public DoubleSetting aimSpeed = new DoubleSetting("Aim Speed", "Базовая скорость", 1.0, 0.1, 5.0);
    public DoubleSetting smoothness = new DoubleSetting("Smoothness", "Плавность", 0.4, 0.1, 0.9);
    public DoubleSetting gcd = new DoubleSetting("Rotation GCD", "Шаг мыши (обход Grim)", 0.0, 0.0, 5.0);
    public DoubleSetting jitter = new DoubleSetting("Jitter", "Рандомное дрожание", 0.0, 0.0, 5.0);
    
    // --- Аим поинты ---
    public EnumSetting<AimPoint> aimPoint = new EnumSetting<>("Aim Point", "Куда бить", AimPoint.HEAD);

    public enum AimPoint { HEAD, BODY, FEET }

    public AimbotModule() {
        // Добавляем настройки в список для GUI
        settings.add(range);
        settings.add(fov);
        settings.add(ignoreWalls);
        settings.add(aimSpeed);
        settings.add(smoothness);
        settings.add(gcd);
        settings.add(jitter);
        settings.add(aimPoint);
    }

    public void tick() {
        if (!enabled || mc.player == null || mc.world == null) return;

        Entity target = findTarget();
        if (target == null) return;

        float[] angles = calculateAngles(target);
        if (angles == null) return;

        float targetYaw = angles[0];
        float targetPitch = angles[1];
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        float yawDelta = RotationUtils.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = RotationUtils.wrapDegrees(targetPitch - currentPitch);

        yawDelta = RotationUtils.applyAdaptiveSpeed(yawDelta, aimSpeed.get());
        pitchDelta = RotationUtils.applyAdaptiveSpeed(pitchDelta, aimSpeed.get());
        yawDelta = RotationUtils.applyGCD(yawDelta, gcd.get());
        pitchDelta = RotationUtils.applyGCD(pitchDelta, gcd.get());
        yawDelta = RotationUtils.applyJitter(yawDelta, jitter.get());
        pitchDelta = RotationUtils.applyJitter(pitchDelta, jitter.get());

        currentYaw = RotationUtils.applySmoothness(currentYaw, currentYaw + yawDelta, smoothness.get());
        currentPitch = RotationUtils.applySmoothness(currentPitch, currentPitch + pitchDelta, smoothness.get());

        mc.player.setYaw(currentYaw);
        mc.player.setPitch(currentPitch);
    }

    private Entity findTarget() {
        Entity bestTarget = null;
        double bestDistance = range.get();
        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity) || entity == mc.player || !entity.isAlive()) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist > bestDistance) continue;

            float[] angles = getAnglesToEntity(entity);
            float yawDiff = Math.abs(RotationUtils.wrapDegrees(angles[0] - currentYaw));
            float pitchDiff = Math.abs(RotationUtils.wrapDegrees(angles[1] - currentPitch));
            
            if (yawDiff > fov.get() / 2.0 || pitchDiff > fov.get() / 2.0) continue;
            if (!ignoreWalls.isEnabled() && !mc.player.canSee(entity)) continue;

            bestTarget = entity;
            bestDistance = dist;
        }
        return bestTarget;
    }

    private float[] calculateAngles(Entity target) {
        return getAnglesToEntity(target);
    }

    private float[] getAnglesToEntity(Entity target) {
        double yOffset = 0;
        if (aimPoint.get() == AimPoint.HEAD) yOffset = target.getStandingEyeHeight() - 0.1;
        else if (aimPoint.get() == AimPoint.BODY) yOffset = target.getHeight() / 2.0;
        
        double dx = target.getX() - mc.player.getX();
        double dy = (target.getY() + yOffset) - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        
        return new float[]{yaw, pitch};
    }
}
