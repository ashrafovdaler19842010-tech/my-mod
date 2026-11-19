package com.example.aimbot.settings;

public abstract class Setting<T> {
    public final String name;
    public final String description;
    protected T value;

    public Setting(String name, String description, T defaultValue) {
        this.name = name;
        this.description = description;
        this.value = defaultValue;
    }

    public T get() { return value; }
    public void set(T newValue) { this.value = newValue; }
}
