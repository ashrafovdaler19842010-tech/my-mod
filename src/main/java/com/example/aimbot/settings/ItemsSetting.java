package com.example.aimbot.settings;

import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.item.Item;

import java.util.List;
import java.util.function.Predicate;

public class ItemsSetting extends Setting<List<Item>> {
    public ItemsSetting(String name, String description, List<Item> defaultValue, Predicate<Item> filter) {
        super(name, description, defaultValue);
    }
    
    // Для совместимости с Builder из Meteorist
    public static class Builder {
        private String name, description;
        private List<Item> defaultValue;
        private Predicate<Item> filter;

        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder defaultValue(List<Item> defaultValue) { this.defaultValue = defaultValue; return this; }
        public Builder filter(Predicate<Item> filter) { this.filter = filter; return this; }

        public ItemsSetting build() {
            return new ItemsSetting(name, description, defaultValue, filter);
        }
    }
}
