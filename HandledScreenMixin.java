package com.auctionbot.mixin;

import com.auctionbot.BotConfig;
import com.auctionbot.PriceParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected Slot focusedSlot;

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressedInGui(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // Клавиша X (на русской раскладке это буква Ч)
        if (keyCode == GLFW.GLFW_KEY_X) {
            if (this.focusedSlot != null && this.focusedSlot.hasStack()) {
                ItemStack stack = this.focusedSlot.getStack();
                if (PriceParser.isSoldPlaceholder(stack)) return;

                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player == null) return;

                String exactName = PriceParser.getCleanItemName(stack);
                if (exactName.isEmpty()) return;

                String enchantsKey = PriceParser.getEnchantmentsKey(stack);
                String uniqueKey = PriceParser.buildUniqueKey(stack);
                String itemId = PriceParser.getItemId(stack);

                String displayName = enchantsKey.isEmpty()
                        ? exactName
                        : exactName + " [" + enchantsKey + "]";

                double parsedPrice = PriceParser.parseUnitPrice(stack, client);
                double initialPrice = parsedPrice > 0 ? parsedPrice : 1000.0;

                boolean alreadyExists = BotConfig.INSTANCE.items.containsKey(uniqueKey);
                BotConfig.INSTANCE.addOrToggleTemplate(
                        uniqueKey, exactName, enchantsKey, displayName, itemId, initialPrice
                );

                if (alreadyExists) {
                    client.player.sendMessage(Text.literal("§c[AuctionBot] - Удален из списка: §f" + displayName), false);
                } else {
                    client.player.sendMessage(Text.literal(
                            "§a[AuctionBot] + Добавлен эталон: §f" + displayName + " §7(база: $" + Math.round(initialPrice) + "/шт.)"
                    ), false);
                }

                cir.setReturnValue(true);
            }
        }
    }
}
