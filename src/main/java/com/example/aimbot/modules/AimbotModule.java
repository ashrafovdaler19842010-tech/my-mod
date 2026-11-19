package com.example.aimbot.modules;

import com.example.aimbot.settings.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AimbotModule {
    public final MinecraftClient mc = MinecraftClient.getInstance();
    public boolean enabled = false;
    public int aimbotKey = 82; // R по умолчанию
    public final Random random = new Random();
    
    public final List<Setting<?>> settings = new ArrayList<>();

    // --- Настройки ---
    public DoubleSetting range = new DoubleSetting("Range", "Дистанция", 4.5, 1.0, 6.0);
    public DoubleSetting fov = new DoubleSetting("FOV", "Угол обзора", 90.0, 10.0, 180.0);
    public BoolSetting ignoreWalls = new BoolSetting("Ignore Walls", "Сквозь стены", false);

    public DoubleSetting aimSpeed = new DoubleSetting("Aim Speed", "Скорость", 1.0, 0.1, 2.0);
    public DoubleSetting smoothness = new DoubleSetting("Smoothness", "Плавность", 0.4, 0.1, 0.9);
    public DoubleSetting gcd = new DoubleSetting("Rotation GCD", "Шаг мыши", 0.0, 0.0, 5.0);
    public DoubleSetting jitter = new DoubleSetting("Jitter", "Дрожание", 0.0, 0.0, 5.0);
    
    public EnumSetting<AimPoint> aimPoint = new EnumSetting<>("Aim Point", "Куда бить", AimPoint.HEAD);

    public enum AimPoint { HEAD, BODY, FEET }

    public AimbotModule() {
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

        // Вычисляем нужные углы
        double yOffset = 0;
        if (aimPoint.get() == AimPoint.HEAD) yOffset = target.getStandingEyeHeight() - 0.1;
        else if (aimPoint.get() == AimPoint.BODY) yOffset = target.getHeight() / 2.0;
        
        double dx = target.getX() - mc.player.getX();
        double dy = (target.getY() + yOffset) - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = target.getZ() - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        float currentYaw = mc.player.getYaw();
        float currentPitch = mc.player.getPitch();

        // Разница углов
        float yawDelta = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - currentPitch);

        // Применяем GCD (Обход Grim)
        if (gcd.get() > 0.01) {
            yawDelta = (float) (Math.round(yawDelta / gcd.get()) * gcd.get());
            pitchDelta = (float) (Math.round(pitchDelta / gcd.get()) * gcd.get());
        }

        // Применяем Jitter (Рандом)
        if (jitter.get() > 0.01) {
            yawDelta += (float) ((random.nextDouble() - 0.5) * jitter.get());
            pitchDelta += (float) ((random.nextDouble() - 0.5) * jitter.get());
        }

        // Плавность и скорость (чем выше smoothness, тем меньше шаг)
        float factor = (float) ((1.0 - smoothness.get()) * aimSpeed.get());
        
        // Умный поворот: если цель далеко, крутим быстрее
        if (Math.abs(yawDelta) > 45) factor *= 1.5f;

        mc.player.setYaw(currentYaw + (yawDelta * factor));
        mc.player.setPitch(currentPitch + (pitchDelta * factor));
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

            // Проверка FOV
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
