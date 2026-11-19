package com.example.aimbot.utils;

import net.minecraft.util.math.MathHelper;
import java.util.Random;

public class RotationUtils {
    private static final Random random = new Random();

    // Вычисление разницы углов
    public static float wrapDegrees(float angle) {
        return MathHelper.wrapDegrees(angle);
    }

    // Плавность: чем выше smoothness, тем медленнее (плавнее) поворот
    public static float applySmoothness(float current, float target, double smoothness) {
        float delta = wrapDegrees(target - current);
        return current + delta * (1.0f - (float)smoothness);
    }

    // Адаптивная скорость: если цель далеко по градусам, крутим быстрее
    public static float applyAdaptiveSpeed(float delta, double baseSpeed) {
        float absDelta = Math.abs(delta);
        float speedMultiplier = 1.0f + (absDelta / 45.0f); // Чем больше угол, тем быстрее
        return (float) (delta * baseSpeed * speedMultiplier);
    }

    // Обход Grim (GCD)
    public static float applyGCD(float delta, double gcd) {
        if (gcd <= 0.01) return delta;
        return Math.round(delta / gcd) * (float)gcd;
    }

    // Джиттер (дрожание) для естественности
    public static float applyJitter(float delta, double jitterAmount) {
        if (jitterAmount <= 0.01) return delta;
        float jitter = (float) ((random.nextDouble() - 0.5) * 2.0 * jitterAmount);
        return delta + jitter;
    }
}
