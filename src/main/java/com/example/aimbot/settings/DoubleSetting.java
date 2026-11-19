package com.example.aimbot.settings;

public class DoubleSetting extends Setting<Double> {
    public final double min, max;

    public DoubleSetting(String name, String description, double defaultValue, double min, double max) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
    }
}
