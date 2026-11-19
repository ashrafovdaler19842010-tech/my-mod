package com.example.aimbot.settings;

public class EnumSetting<T extends Enum<T>> extends Setting<T> {
    public final T[] modes;

    public EnumSetting(String name, String description, T defaultValue) {
        super(name, description, defaultValue);
        this.modes = defaultValue.getDeclaringClass().getEnumConstants();
    }

    public void next() {
        int index = (value.ordinal() + 1) % modes.length;
        this.value = modes[index];
    }
}
