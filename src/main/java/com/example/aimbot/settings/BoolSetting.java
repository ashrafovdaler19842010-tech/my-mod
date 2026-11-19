package com.example.aimbot.settings;

public class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String name, String description, boolean defaultValue) {
        super(name, description, defaultValue);
    }
    public boolean isEnabled() { return value; }
    public void toggle() { this.value = !this.value; }
}
