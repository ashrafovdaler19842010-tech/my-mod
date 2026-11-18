package com.auctionbot;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PriceParser {

    private static final Pattern PRICE_LINE_PATTERN = Pattern.compile(
            "(?i)(цена|price|стоимость|\\$|coins|монет)[^0-9]*([0-9][0-9\\s.,]*)"
    );

    /**
     * Проверяет, не является ли слот серой заглушкой уже купленного товара
     */
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
