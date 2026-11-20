package com.example.aimbot.modules.zaimbot;

import com.example.aimbot.AimbotMod;
import com.example.aimbot.utils.config.MeteoristConfigManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.EntityAgeTest;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

import static com.example.aimbot.utils.MeteoristUtils.calculateFov;

public class ZAimbot extends Module {
    private final SettingGroup sgFilter = settings.createGroup("Filter");
    private final SettingGroup sgAim = settings.createGroup("Aim");
    private final SettingGroup sgBypass = settings.createGroup("Bypass (FunTime/SpaceTime)");

    private final Random random = new Random();

    // --- Filter ---
    private final Setting<Set<EntityType<?>>> entities = sgFilter.add(new EntityTypeListSetting.Builder()
            .name("entities").description("Entities to aim at.").onlyAttackable().defaultValue(EntityType.PLAYER).build()
    );
    private final Setting<Double> range = sgFilter.add(new DoubleSetting.Builder()
            .name("range").description("The maximum range.").defaultValue(4.5).min(0).sliderMax(6).build()
    );
    private final Setting<EntityAgeTest> passiveMobAgeFilter = sgFilter.add(new EnumSetting.Builder<EntityAgeTest>()
            .name("passive-mob-age-filter").defaultValue(EntityAgeTest.Adult).build()
    );
    private final Setting<EntityAgeTest> hostileMobAgeFilter = sgFilter.add(new EnumSetting.Builder<EntityAgeTest>()
            .name("hostile-mob-age-filter").defaultValue(EntityAgeTest.Both).build()
    );
    private final Setting<Boolean> ignoreNamed = sgFilter.add(new BoolSetting.Builder().name("ignore-named").defaultValue(false).build());
    private final Setting<Boolean> ignorePassive = sgFilter.add(new BoolSetting.Builder().name("ignore-passive").defaultValue(true).build());
    private final Setting<Boolean> ignoreTamed = sgFilter.add(new BoolSetting.Builder().name("ignore-tamed").defaultValue(false).build());
    private final Setting<Boolean> ignoreCreative = sgFilter.add(new BoolSetting.Builder().name("ignore-creative").defaultValue(true).build());
    private final Setting<Boolean> ignoreFriends = sgFilter.add(new BoolSetting.Builder().name("ignore-friends").defaultValue(true).build());
    private final Setting<Boolean> ignoreShield = sgFilter.add(new BoolSetting.Builder().name("ignore-shield").defaultValue(false).build());
    private final Setting<Boolean> useFovRange = sgFilter.add(new BoolSetting.Builder().name("use-fov-range").defaultValue(true).build());
    private final Setting<Double> fovRange = sgFilter.add(new DoubleSetting.Builder().name("fov-range").sliderRange(0, 180).defaultValue(90).visible(useFovRange::get).build());
    private final Setting<Boolean> ignoreWalls = sgFilter.add(new BoolSetting.Builder().name("ignore-walls").defaultValue(false).build());
    private final Setting<SortPriority> priority = sgFilter.add(new EnumSetting.Builder<SortPriority>().name("priority").defaultValue(SortPriority.ClosestAngle).build());
    
    private final Setting<OperationMode> itemFilteringMode = sgFilter.add(new EnumSetting.Builder<OperationMode>().name("item-filtering-mode").defaultValue(OperationMode.Blacklist).build());
    private final Setting<List<Item>> itemBlacklist = sgFilter.add(new ItemListSetting.Builder().name("item-blacklist").defaultValue(Items.COBWEB).visible(() -> itemFilteringMode.get() == OperationMode.Blacklist).build());
    private final Setting<List<Item>> itemWhitelist = sgFilter.add(new ItemListSetting.Builder().name("item-whitelist").defaultValue(Items.BOW, Items.CROSSBOW, Items.TRIDENT).visible(() -> itemFilteringMode.get() == OperationMode.Whitelist).build());

    // НАША НОВАЯ НАСТРОЙКА
    private final Setting<Boolean> onlySword = sgFilter.add(new BoolSetting.Builder()
            .name("only-sword").description("Aimbot works only when holding a sword.").defaultValue(false).build()
    );

    // --- Aim ---
    public final Setting<Double> targetMovementPrediction = sgAim.add(new DoubleSetting.Builder().name("target-movement-prediction").defaultValue(0).min(0).sliderMax(20).build());
    private final Setting<AimAxes> aimAxes = sgAim.add(new EnumSetting.Builder<AimAxes>().name("aim-axes").defaultValue(AimAxes.Both).build());
    
    private final Setting<AimPoint> aimPoint = sgAim.add(new EnumSetting.Builder<AimPoint>()
            .name("aim-point").description("Part of the body to aim at.").defaultValue(AimPoint.Head).build()
    );
    
    private final Setting<Boolean> instantYaw = sgAim.add(new BoolSetting.Builder().name("instant-yaw").defaultValue(false).build());
    private final Setting<Boolean> instantPitch = sgAim.add(new BoolSetting.Builder().name("instant-pitch").defaultValue(false).build());
    private final Setting<Boolean> syncYawWithCooldown = sgAim.add(new BoolSetting.Builder().name("sync-yaw-with-cooldown").defaultValue(false).visible(() -> !instantYaw.get()).build());
    private final Setting<Boolean> syncPitchWithCooldown = sgAim.add(new BoolSetting.Builder().name("sync-pitch-with-cooldown").defaultValue(false).visible(() -> !instantPitch.get()).build());
    private final Setting<Double> speedYaw = sgAim.add(new DoubleSetting.Builder().name("speed-yaw").defaultValue(1).min(0).sliderRange(0.1, 10).visible(() -> !instantYaw.get()).build());
    private final Setting<Double> speedPitch = sgAim.add(new DoubleSetting.Builder().name("speed-pitch").defaultValue(1).min(0).sliderRange(0.1, 10).visible(() -> !instantPitch.get()).build());

    // --- Bypass ---
    private final Setting<BypassMode> bypassMode = sgBypass.add(new EnumSetting.Builder<BypassMode>()
            .name("bypass-mode").description("Anti-cheat bypass preset.").defaultValue(BypassMode.None).build()
    );
    private final Setting<Double> smoothness = sgBypass.add(new DoubleSetting.Builder()
            .name("smoothness").description("How smooth the rotation is.").defaultValue(0.5).min(0.1).sliderRange(0.1, 0.9).build()
    );
    private final Setting<Double> jitter = sgBypass.add(new DoubleSetting.Builder()
            .name("jitter").description("Random micro-movements.").defaultValue(0.0).min(0).sliderRange(0, 3).build()
    );

    private final List<AimTrajectory> trajectories = new ArrayList<>();

    public ZAimbot() {
        super(AimbotMod.CATEGORY, "z-aimbot", "Smart aimbot with bypasses.");
    }

    public enum AimPoint { Head, Chest, Legs, Random }
    public enum BypassMode { None, FunTime_Polar, SpaceTime_Shard }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        NbtList list = new NbtList();
        for (AimTrajectory trajectory : trajectories) {
            NbtCompound tTag = new NbtCompound();
            tTag.put("trajectory", trajectory.toTag());
            list.add(tTag);
        }
        tag.put("trajectories", list);
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);
        trajectories.clear();
        NbtList list = tag.getList("trajectories", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tagI = list.getCompound(i);
            AimTrajectory trajectory = new AimTrajectory();
            if (tagI.contains("trajectory")) {
                trajectory.fromTag(tagI.getCompound("trajectory"));
            }
            trajectories.add(trajectory);
        }
        return this;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        fillWidget(theme, list);
        return list;
    }

    private void fillWidget(GuiTheme theme, WVerticalList list) {
        list.clear();
        for (AimTrajectory trajectory : trajectories) {
            list.add(theme.settings(trajectory.settings)).expandX();
            WContainer controls = list.add(theme.horizontalList()).widget();
            if (trajectories.size() > 1) {
                WContainer moveContainer = controls.add(theme.horizontalList()).expandX().widget();
                int index = trajectories.indexOf(trajectory);
                if (index > 0) {
                    WButton moveUp = moveContainer.add(theme.button("^")).expandX().widget();
                    moveUp.action = () -> { trajectories.remove(index); trajectories.add(index - 1, trajectory); fillWidget(theme, list); };
                }
                if (index < trajectories.size() - 1) {
                    WButton moveDown = moveContainer.add(theme.button("v")).expandX().widget();
                    moveDown.action = () -> { trajectories.remove(index); trajectories.add(index + 1, trajectory); fillWidget(theme, list); };
                }
            }
            WButton copy = controls.add(theme.button("Copy")).widget();
            copy.action = () -> { trajectories.add(trajectories.indexOf(trajectory), trajectory.copy()); fillWidget(theme, list); };
            WMinus remove = controls.add(theme.minus()).widget();
            remove.action = () -> { trajectories.remove(trajectory); fillWidget(theme, list); };
        }
        if (!trajectories.isEmpty()) list.add(theme.horizontalSeparator()).expandX();
        WContainer controls = list.add(theme.horizontalList()).expandX().widget();
        WButton add = controls.add(theme.button("Add Trajectory")).expandX().widget();
        add.action = () -> { trajectories.add(new AimTrajectory()); fillWidget(theme, list); };
        WButton removeAll = controls.add(theme.button("Remove All")).expandX().widget();
        removeAll.action = () -> { trajectories.clear(); fillWidget(theme, list); };
        MeteoristConfigManager.configManager(theme, list, this);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Item heldItem = mc.player.getMainHandStack().getItem();

        if (onlySword.get()) {
            if (!(heldItem == Items.WOODEN_SWORD || heldItem == Items.STONE_SWORD || heldItem == Items.IRON_SWORD || heldItem == Items.GOLDEN_SWORD || heldItem == Items.DIAMOND_SWORD || heldItem == Items.NETHERITE_SWORD)) {
                return;
            }
        }

        if (itemFilteringMode.get() == OperationMode.Blacklist ? itemBlacklist.get().contains(heldItem) : !itemWhitelist.get().contains(heldItem)) return;

        Entity target = TargetUtils.get(this::entityCheck, priority.get());
        if (target == null) return;
        aim(mc.player, target);
    }

    private boolean entityCheck(Entity entity) {
        if (entity == mc.player || entity == mc.getCameraEntity() || !entity.isAlive()) return false;
        if (entity instanceof LivingEntity livingEntity && livingEntity.isDead()) return false;
        if (!PlayerUtils.isWithin(entity, range.get())) return false;
        if (!entities.get().contains(entity.getType())) return false;
        if (ignoreNamed.get() && entity.hasCustomName()) return false;
        if (ignoreTamed.get() && entity instanceof Tameable ownable && Objects.equals(ownable.getOwner(), mc.player)) return false;
        if (ignorePassive.get() && isPassive(entity)) return false;

        if (entity instanceof PlayerEntity player) {
            if (ignoreCreative.get() && player.isCreative()) return false;
            if (ignoreFriends.get() && !Friends.get().shouldAttack(player)) return false;
            if (ignoreShield.get() && player.isBlocking()) return false;
        }

        if (entity instanceof LivingEntity livingEntity) {
            if (entity instanceof ZombieEntity || entity instanceof PiglinEntity || entity instanceof HoglinEntity || entity instanceof ZoglinEntity) return hostileMobAgeFilter.get().test(livingEntity);
            if (entity instanceof PassiveEntity && (!(entity instanceof FrogEntity || entity instanceof ParrotEntity))) return passiveMobAgeFilter.get().test(livingEntity);
        }

        if (useFovRange.get() && calculateFov(mc.player, entity) > fovRange.get()) return false;
        return ignoreWalls.get() || PlayerUtils.canSeeEntity(entity);
    }

    private boolean isPassive(Entity entity) {
        return switch (entity) {
            case EndermanEntity enderman -> !enderman.isCreepy();
            case ZombifiedPiglinEntity piglin -> !piglin.isAggressive();
            case WolfEntity wolf -> !wolf.isAggressive();
            default -> false;
        };
    }

    private void aim(LivingEntity player, Entity target) {
        Vec3d predictedPos = target.getPos().add(0, getAimYOffset(target), 0)
                .add(target.getVelocity().multiply(targetMovementPrediction.get()));
        
        float yaw = (float) Rotations.getYaw(predictedPos);
        float pitch;

        Item usedItem = mc.player.getMainHandStack().getItem();
        AimTrajectory matchingTrajectory = trajectories.stream().filter(t -> t.items.get().contains(usedItem)).findFirst().orElse(null);

        if (matchingTrajectory != null) {
            pitch = calculatePitchWithTrajectory(player, target, matchingTrajectory);
        } else {
            pitch = (float) Rotations.getPitch(predictedPos);
        }

        float yawDifference = MathHelper.wrapDegrees(yaw - player.getYaw());
        float pitchDifference = MathHelper.wrapDegrees(pitch - player.getPitch());

        float finalSpeedYaw = speedYaw.get().floatValue();
        float finalSpeedPitch = speedPitch.get().floatValue();
        float finalSmooth = smoothness.get().floatValue();
        float finalJitter = jitter.get().floatValue();

        if (bypassMode.get() == BypassMode.FunTime_Polar) {
            finalSpeedYaw *= (0.9f + random.nextFloat() * 0.2f);
            finalSpeedPitch *= (0.9f + random.nextFloat() * 0.2f);
            finalJitter = Math.max(finalJitter, 0.2f);
        } else if (bypassMode.get() == BypassMode.SpaceTime_Shard) {
            finalSmooth = Math.max(finalSmooth, 0.7f);
            finalJitter = Math.max(finalJitter, 0.5f);
        }

        if (instantYaw.get()) {
            if (aimAxes.get() == AimAxes.Both || aimAxes.get() == AimAxes.OnlyYaw) player.setYaw(yaw);
        } else if (aimAxes.get() == AimAxes.Both || aimAxes.get() == AimAxes.OnlyYaw) {
            float cooldownProgress = syncYawWithCooldown.get() ? mc.player.getAttackCooldownProgress(0) : 1;
            float yawSpeedFactor = (finalSpeedYaw / 10) * (1.0f - finalSmooth);
            float jitteredYawDiff = yawDifference + (random.nextFloat() - 0.5f) * finalJitter;
            player.setYaw(player.getYaw() + jitteredYawDiff * cooldownProgress * yawSpeedFactor);
        }

        if (instantPitch.get()) {
            if (aimAxes.get() == AimAxes.Both || aimAxes.get() == AimAxes.OnlyPitch) player.setPitch(pitch);
        } else if (aimAxes.get() == AimAxes.Both || aimAxes.get() == AimAxes.OnlyPitch) {
            float cooldownProgress = syncPitchWithCooldown.get() ? mc.player.getAttackCooldownProgress(0) : 1;
            float pitchSpeedFactor = (finalSpeedPitch / 10) * (1.0f - finalSmooth);
            float jitteredPitchDiff = pitchDifference + (random.nextFloat() - 0.5f) * finalJitter;
            player.setPitch(player.getPitch() + jitteredPitchDiff * cooldownProgress * pitchSpeedFactor);
        }
    }

    private double getAimYOffset(Entity target) {
        double height = target.getBoundingBox().getLengthY();
        return switch (aimPoint.get()) {
            case Head -> height - 0.2;
            case Chest -> height * 0.7;
            case Legs -> height * 0.3;
            case Random -> {
                double min = height * 0.4;
                double max = height * 0.9;
                yield min + random.nextDouble() * (max - min);
            }
        };
    }

    private float calculatePitchWithTrajectory(LivingEntity player, Entity target, AimTrajectory traj) {
        Vec3d playerEye = player.getEyePos();
        Vec3d targetPos = target.getPos().add(0, target.getBoundingBox().getLengthY() / 2, 0);
        double dx = targetPos.x - playerEye.x;
        double dy = targetPos.y - playerEye.y;
        double dz = targetPos.z - playerEye.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        if (hDist == 0) return 0;

        double g = 0.006 * traj.gravity.get();
        double v = traj.velocityScale.get();
        double vSq = v * v;
        double sqrtPart = vSq * vSq - g * (g * hDist * hDist + 2 * dy * vSq);
        
        if (sqrtPart < 0) return (float) Rotations.getPitch(targetPos);

        double pitchRad = Math.atan((vSq - Math.sqrt(sqrtPart)) / (g * hDist));
        return (float) -Math.toDegrees(pitchRad);
    }

    public enum OperationMode { Blacklist, Whitelist }
    public enum AimAxes { Both, OnlyYaw, OnlyPitch }
}
