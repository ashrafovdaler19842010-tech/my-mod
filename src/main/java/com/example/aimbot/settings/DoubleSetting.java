package com.example.aimbot.settings;

public class DoubleSetting extends Setting<Double> {
    public final double min, max;

    public DoubleSetting(String name, String description, double defaultValue, double min, double max) {
        super(name, description, defaultValue);
        this.min = min;
        this.max = max;
    }

    @Override
    public void set(Double newValue) {
        this.value = Math.max(min, Math.min(max, newValue));
    }

    public String getFormattedValue() {
        return String.format("%.2f", value);
    }
}
