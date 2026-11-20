package com.example.aimbot;

import com.example.aimbot.modules.zaimbot.ZAimbot;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AimbotMod extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Aimbot");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Aimbot Addon");
        Modules.get().add(new ZAimbot());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.aimbot";
    }
}
