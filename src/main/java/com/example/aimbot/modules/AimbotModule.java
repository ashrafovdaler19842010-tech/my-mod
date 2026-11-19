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

    // --- Скорость и плавность (Значения по умолчанию сделаны безопасными) ---
    public DoubleSetting aimSpeed = new DoubleSetting("Aim Speed", "Общая скорость", 1.0, 0.1, 3.0);
    public DoubleSetting aimStrength = new DoubleSetting("Aim Strength", "Сила притяжения", 0.10, 0.01, 1.0);
    public DoubleSetting smoothness = new DoubleSetting("Smoothness", "Плавность (0.1 быстро, 0.9 плавно)", 0.65, 0.1, 0.9);
    public DoubleSetting dynamicSpeed = new DoubleSetting("Dynamic Speed", "Ускорение если цель далеко", 1.5, 1.0, 3.0);
    
    // --- Человекоподобность и FunTime ---
    public BoolSetting randomPoint = new BoolSetting("Random Point", "Случайная точка в хитбоксе", true);
    public DoubleSetting saccade = new DoubleSetting("Saccade", "Микро-движения глаз", 0.0, 0.0, 2.0);
    public DoubleSetting humanization = new DoubleSetting("Humanization", "Задержка реакции", 0.0, 0.0, 1.0);
    public DoubleSetting gcd = new DoubleSetting("Rotation GCD", "Шаг мыши (обход Grim)", 0.0, 0.0, 5.0);
    public DoubleSetting jitter = new DoubleSetting("Jitter", "Случайное дрожание", 0.0, 0.0, 5.0);
    public DoubleSetting randomSpeedMin = new DoubleSetting("Rand Speed Min", "Мин. рандом скорость", 0.8, 0.1, 3.0);
    public DoubleSetting randomSpeedMax = new DoubleSetting("Rand Speed Max", "Макс. рандом скорость", 1.2, 0.1, 3.0);

    // --- Настройки HUD ---
    public DoubleSetting hudX = new DoubleSetting("HUD X", "Позиция HUD по X", 50.0, 0.0, 1920.0);
    public DoubleSetting hudY = new DoubleSetting("HUD Y", "Позиция HUD по Y", 50.0, 0.0, 1080.0);

    private float targetYaw = 0;
    private float targetPitch = 0;
    private float lastYaw = 0;
    private float lastPitch = 0;
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
        settings.add(humanization);
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

        // 1. Выбираем точку наведения
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

        // 2. Считаем углы
        double dx = targetPos.x - mc.player.getX();
        double dy = targetPos.y - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = targetPos.z - mc.player.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);

        float calcYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        float calcPitch = (float) -Math.toDegrees(Math.atan2(dy, dist));

        // 3. Сглаживаем вход (Humanization)
        float factor = (1.0f - smoothness.get().floatValue());
        targetYaw = MathHelper.lerp(factor, mc.player.getYaw(), calcYaw);
        targetPitch = MathHelper.lerp(factor, mc.player.getPitch(), calcPitch);

        // 4. Проверка на рывок (если угол слишком большой, режем шаг)
        float yawDelta = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDelta = MathHelper.wrapDegrees(targetPitch - mc.player.getPitch());
        
        if (Math.abs(yawDelta) > 180) yawDelta = MathHelper.wrapDegrees(yawDelta);

        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        needsInterp = true;

        // Применяем на сервер (Minecraft сам отправит пакет)
        mc.player.setYaw(mc.player.getYaw() + yawDelta * 0.2f * aimSpeed.get().floatValue());
        mc.player.setPitch(mc.player.getPitch() + pitchDelta * 0.2f * aimSpeed.get().floatValue());
    }

    // Этот метод вызывается каждый кадр рендера для плавности
    public void applyInterpolation(float tickDelta) {
        if (!needsInterp || mc.player == null) return;
        // Плавно интерполируем между прошлым и текущим углом
        float smoothYaw = MathHelper.lerp(tickDelta, lastYaw, mc.player.getYaw());
        float smoothPitch = MathHelper.lerp(tickDelta, lastPitch, mc.player.getPitch());
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
