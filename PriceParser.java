package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceParser {

    private static final Pattern PRICE_LINE_PATTERN = Pattern.compile(
            "(?i)(цена|price|стоимость|\\$|coins|монет)[^0-9]*([0-9][0-9\\s.,]*)"
    );

    public static boolean isSoldPlaceholder(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return true;
        Item item = stack.getItem();
        return item == Items.GRAY_STAINED_GLASS_PANE
                || item == Items.LIGHT_GRAY_STAINED_GLASS_PANE
                || item == Items.BLACK_STAINED_GLASS_PANE
                || item == Items.BARRIER;
    }

    public static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "minecraft:barrier";
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    /**
     * Считывает все зачарования предмета в отсортированную строку (например: "Защита V, Прочность IV")
     */
    public static String getEnchantmentsKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        List<String> enchants = new ArrayList<>();

        ItemEnchantmentsComponent normalEnch = stack.get(DataComponentTypes.ENCHANTMENTS);
        if (normalEnch != null) {
            for (RegistryEntry<Enchantment> entry : normalEnch.getEnchantments()) {
                int lvl = normalEnch.getLevel(entry);
                String name = stripFormatting(Enchantment.getName(entry, lvl).getString()).trim();
                if (!name.isEmpty()) enchants.add(name);
            }
        }

        ItemEnchantmentsComponent storedEnch = stack.get(DataComponentTypes.STORED_ENCHANTMENTS);
        if (storedEnch != null) {
            for (RegistryEntry<Enchantment> entry : storedEnch.getEnchantments()) {
                int lvl = storedEnch.getLevel(entry);
                String name = stripFormatting(Enchantment.getName(entry, lvl).getString()).trim();
                if (!name.isEmpty()) enchants.add(name);
            }
        }

        Collections.sort(enchants);
        return String.join(", ", enchants);
    }

    /**
     * Уникальный ключ предмета: Точное скопированное название + Зачарования
     * (Талисман Крушителя и Талисман Карателя никогда не перепутаются!)
     */
    public static String buildUniqueKey(ItemStack stack) {
        String exactName = getCleanItemName(stack);
        String ench = getEnchantmentsKey(stack);
        return (exactName + "|" + ench).toLowerCase();
    }

    public static double parseUnitPrice(ItemStack stack, MinecraftClient client) {
        if (isSoldPlaceholder(stack) || client.world == null) return -1;

        List<Text> tooltip = stack.getTooltip(
                Item.TooltipContext.create(client.world),
                client.player,
                TooltipType.BASIC
        );

        for (int i = 1; i < tooltip.size(); i++) {
            String rawLine = stripFormatting(tooltip.get(i).getString()).trim();
            if (rawLine.isEmpty()) continue;

            Matcher matcher = PRICE_LINE_PATTERN.matcher(rawLine);
            if (matcher.find()) {
                String numStr = matcher.group(2).replaceAll("[^0-9]", "");
                if (!numStr.isEmpty()) {
                    try {
                        double totalPrice = Double.parseDouble(numStr);
                        int count = Math.max(1, stack.getCount());
                        return totalPrice / count;
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        return -1;
    }

    public static String getCleanItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        return stripFormatting(stack.getName().getString()).trim();
    }

    public static String stripFormatting(String input) {
        return input == null ? "" : input.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
