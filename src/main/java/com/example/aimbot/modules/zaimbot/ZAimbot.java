package com.example.aimbot.modules.zaimbot;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.utils.misc.ISerializable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

public class AimTrajectory implements ISerializable<AimTrajectory> {
    public final Settings settings = new Settings();
    SettingGroup sgAimTrajectory = settings.createGroup("Aim Trajectory");

    public final Setting<List<Item>> items = sgAimTrajectory.add(new ItemListSetting.Builder()
            .name("items")
            .description("The projectile items this trajectory applies to.")
            .defaultValue(List.of(Items.SNOWBALL, Items.EGG))
            .build()
    );

    public final Setting<Double> gravity = sgAimTrajectory.add(new DoubleSetting.Builder()
            .name("gravity")
            .description("Gravity multiplier.")
            .defaultValue(1.0)
            .min(0.0)
            .sliderMax(5.0)
            .build()
    );

    public final Setting<Double> velocityScale = sgAimTrajectory.add(new DoubleSetting.Builder()
            .name("velocity-scale")
            .description("Base velocity scale.")
            .defaultValue(0.6)
            .min(0.1)
            .sliderMax(10.0)
            .build()
    );

    public AimTrajectory() {}

    @Override
    public NbtCompound toTag() { return settings.toTag(); }

    @Override
    public AimTrajectory fromTag(NbtCompound tag) {
        settings.fromTag(tag);
        return this;
    }

    public AimTrajectory copy() { return new AimTrajectory().fromTag(toTag()); }
}
