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
    public DoubleSetting fov = new DoubleSetting("FOV", "Угол обзора в градусах", 90.0, 10.0, 180.0);
    public BoolSetting ignoreWalls = new BoolSetting("Ignore Walls", "Бить сквозь стены", false);

    // --- Скорость и плавность ---
    public DoubleSetting aimSpeed = new DoubleSetting("Aim Speed", "Базовая скорость наводки", 1.0, 0.1, 3.0);
    public DoubleSetting smoothness = new DoubleSetting("Smoothness", "Плавность (0.1 быстро, 0.9 плавно)", 0.4, 0.1, 0.9);
    public DoubleSetting dynamicSpeed = new DoubleSetting("Dynamic Speed", "Ускорение если цель не в прицеле", 1.5, 1.0, 3.0);
    
    // --- FunTime / Grim ---
    public DoubleSetting gcd = new DoubleSetting("Rotation GCD", "Шаг мыши (обход Grim)", 0.0, 0.0, 5.0);
    public DoubleSetting jitter = new DoubleSetting("Jitter", "Рандомное дрожание", 0.0, 0.0, 5.0);
    public DoubleSetting randomSpeedMin = new DoubleSetting("Rand Speed Min", "Мин. рандом скорость", 0.8, 0.1, 3.0);
    public DoubleSetting randomSpeedMax = new DoubleSetting("Rand Speed Max", "Макс. рандом скорость", 1.2, 0.1, 3.0);
    
    // --- Аим поинты ---
    public BoolSetting nearestHitbox = new BoolSetting("Nearest Hitbox", "Целиться в ближайшую точку хитбокса (Raycast)", true);

    public AimbotModule() {
        settings.add(range);
        settings.add(fov);
        settings.add(ignoreWalls);
        settings.add(aimSpeed);
        settings.add(dynamicSpeed);
        settings.add(smoothness);
        settings.add(gcd);
        settings.add(jitter);
        settings.add(randomSpeedMin);
        settings.add(randomSpeedMax);
        settings.add(nearestHitbox);
    }

    public void tick() {
        if (!enabled || mc.player == null || mc.world == null) return;

        Entity target = findTarget();
        if (target == null) return;

        // 1. Вычисляем точку наведения
        Vec3d targetPos;
        if (nearestHitbox.get()) {
            // Ищем ближайшую точку хитбокса к нашим глазам
            Vec3d eyePos = mc.player.getEyePos();
            Box box = target.getBoundingBox();
            targetPos = new Vec3d(
                MathHelper.clamp(eyePos.x, box.minX, box.maxX),
                MathHelper.clamp(eyePos.y, box.minY, box.maxY),
                MathHelper.clamp(eyePos.z, box.minZ, box.maxZ)
            );
        } else {
            // Стандартно в центр головы/тела
            targetPos = target.getPos().add(0, target.getHeight() * 0.8, 0);
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

        // 2. Считаем скорость (базовая + динамическая + рандом)
        // ИСПРАВЛЕНО: используем .floatValue() для объектов Double
        float currentSpeed = aimSpeed.get().floatValue();
        
        // Если цель далеко по градусам, ускоряемся
        if (Math.abs(yawDelta) > 30) {
            currentSpeed *= dynamicSpeed.get().floatValue();
        }
        
        // Рандомизация скорости каждый тик
        if (randomSpeedMax.get() > randomSpeedMin.get()) {
            float rand = randomSpeedMin.get().floatValue() + random.nextFloat() * (randomSpeedMax.get().floatValue() - randomSpeedMin.get().floatValue());
            currentSpeed *= rand;
        }

        // 3. Применяем GCD и Jitter
        if (gcd.get() > 0.01) {
            yawDelta = (float) (Math.round(yawDelta / gcd.get()) * gcd.get());
            pitchDelta = (float) (Math.round(pitchDelta / gcd.get()) * gcd.get());
        }
        if (jitter.get() > 0.01) {
            yawDelta += (float) ((random.nextDouble() - 0.5) * jitter.get());
            pitchDelta += (float) ((random.nextDouble() - 0.5) * jitter.get());
        }

        // 4. Высчитываем итоговый шаг с учетом плавности
        float factor = (1.0f - smoothness.get().floatValue()) * currentSpeed;
        
        // Ограничиваем максимальный шаг за тик, чтобы не было рывков
        float stepYaw = MathHelper.clamp(yawDelta * factor, -45f, 45f);
        float stepPitch = MathHelper.clamp(pitchDelta * factor, -45f, 45f);

        // 5. Устанавливаем новые углы (Minecraft сам их интерполирует при рендере)
        mc.player.setYaw(currentYaw + stepYaw);
        mc.player.setPitch(currentPitch + stepPitch);
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
