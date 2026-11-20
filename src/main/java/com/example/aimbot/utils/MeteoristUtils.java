package com.example.aimbot.utils;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

public class MeteoristUtils {
    public static double calculateFov(PlayerEntity player, Entity entity) {
        double dx = entity.getX() - player.getX();
        double dz = entity.getZ() - player.getZ();
        double angle = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        return Math.abs(MathHelper.wrapDegrees((float)angle - player.getYaw()));
    }
}
