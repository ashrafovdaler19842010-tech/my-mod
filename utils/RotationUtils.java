package com.example.aimbot.utils;

import net.minecraft.util.math.Mth;

public final class RotationUtils {
    private RotationUtils() {}

    /** Экспоненциальное сглаживание: 0 — мгновенно, 1 — очень плавно */
    public static float smooth(float current, float target, float smoothness) {
        float delta = Mth.wrapDegrees(target - current);
        return current + delta * (1.0f - smoothness);
    }

    /** Адаптивная скорость: чем больше угол, тем быстрее поворот */
    public static float adaptiveSpeed(float angleDiff, float baseSpeed, float maxMultiplier) {
        float t = Math.min(angleDiff / 180.0f, 1.0f);
        return baseSpeed * (1.0f + t * (maxMultiplier - 1.0f));
    }

    /** GCD (градусное разделение) — эмуляция шага мыши для обхода Grim */
    public static float applyGCD(float delta, float sensitivity, float gcd) {
        if (gcd <= 0) return delta;
        float step = sensitivity * 0.6f + 0.2f;
        step = step * step * step * 8.0f;
        step = Math.round(step / gcd) * gcd;
        return Math.round(delta / step) * step;
    }

    /** Джиттер — случайное микро-отклонение в градусах */
    public static float applyJitter(float delta, float amount) {
        if (amount <= 0) return delta;
        return delta + (float) ((Math.random() * 2 - 1) * amount);
    }
}
