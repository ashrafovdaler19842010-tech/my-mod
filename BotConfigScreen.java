package com.auctionbot;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BotConfigScreen extends Screen {

    private TextFieldWidget searchField;
    private String searchQuery = "";
    private int scrollOffset = 0;

    private final List<Map.Entry<String, BotConfig.ItemData>> filteredItems = new ArrayList<>();
    private final List<TextFieldWidget> visiblePriceFields = new ArrayList<>();
    private final List<ButtonWidget> visibleToggleButtons = new ArrayList<>();

    private static final int ROW_HEIGHT = 24;
    private static final int LIST_TOP = 92;

    public BotConfigScreen() {
        super(Text.literal("Настройки AuctionBot"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        // 1. Кнопка Старт / Стоп бота
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(AuctionSniper.INSTANCE.isBotEnabled() ? "§aБот: ВКЛ" : "§cБот: ВЫКЛ"),
                btn -> {
                    AuctionSniper.INSTANCE.toggleBot(this.client);
                    btn.setMessage(Text.literal(AuctionSniper.INSTANCE.isBotEnabled() ? "§aБот: ВКЛ" : "§cБот: ВЫКЛ"));
                }
        ).dimensions(centerX - 180, 14, 115, 20).build());

        // 2. Кнопка Сканировать рынок сейчас
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("§bСкан цен (TAB)"),
                btn -> {
                    if (this.client != null) {
                        MarketScanner.INSTANCE.startFullScan(this.client);
                        this.close();
                    }
                }
        ).dimensions(centerX - 60, 14, 120, 20).build());

        // 3. Кнопка выбора скорости
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Скорость: " + BotConfig.INSTANCE.speedMode.displayName),
                btn -> {
                    BotConfig.INSTANCE.speedMode = BotConfig.INSTANCE.speedMode.next();
                    BotConfig.save();
                    btn.setMessage(Text.literal("Скорость: " + BotConfig.INSTANCE.speedMode.displayName));
                }
        ).dimensions(centerX + 65, 14, 115, 20).build());

        // 4. Ползунок процента покупки (от 1% до 100%)
        double initialSliderVal = (BotConfig.INSTANCE.buyPercent - 1) / 99.0;
        this.addDrawableChild(new SliderWidget(centerX - 180, 38, 360, 20,
                Text.literal("Покупать от цены: " + BotConfig.INSTANCE.buyPercent + "%"), initialSliderVal) {
            @Override
            protected void updateMessage() {
                this.setMessage(Text.literal("Покупать от цены: " + BotConfig.INSTANCE.buyPercent + "%"));
            }

            @Override
            protected void applyValue() {
                BotConfig.INSTANCE.buyPercent = 1 + (int) Math.round(this.value * 99.0);
                BotConfig.save();
            }
        });

        // 5. Поле поиска по предметам
        this.searchField = new TextFieldWidget(this.textRenderer, centerX - 180, 64, 360, 18, Text.literal("Поиск..."));
        this.searchField.setPlaceholder(Text.literal("§7Поиск предмета (например: булыжник)..."));
        this.searchField.setText(searchQuery);
        this.searchField.setChangedListener(text -> {
            this.searchQuery = text;
            this.scrollOffset = 0;
            rebuildItemList();
        });
        this.addDrawableChild(this.searchField);

        rebuildItemList();
    }
    private void rebuildItemList() {
        for (TextFieldWidget field : visiblePriceFields) {
            this.remove(field);
        }
        for (ButtonWidget btn : visibleToggleButtons) {
            this.remove(btn);
        }
        visiblePriceFields.clear();
        visibleToggleButtons.clear();
        filteredItems.clear();

        String q = searchQuery.trim().toLowerCase();
        for (Map.Entry<String, BotConfig.ItemData> entry : BotConfig.INSTANCE.items.entrySet()) {
            if (q.isEmpty() || entry.getValue().displayName.toLowerCase().contains(q)) {
                filteredItems.add(entry);
            }
        }

        int listBottom = this.height - 28;
        int maxVisibleRows = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
        if (scrollOffset > Math.max(0, filteredItems.size() - maxVisibleRows)) {
            scrollOffset = Math.max(0, filteredItems.size() - maxVisibleRows);
        }

        int centerX = this.width / 2;

        for (int i = 0; i < maxVisibleRows; i++) {
            int dataIndex = scrollOffset + i;
            if (dataIndex >= filteredItems.size()) break;

            BotConfig.ItemData itemData = filteredItems.get(dataIndex).getValue();
            int rowY = LIST_TOP + i * ROW_HEIGHT;

            // Поле редактирования цены за 1 шт.
            TextFieldWidget priceField = new TextFieldWidget(
                    this.textRenderer, centerX + 20, rowY, 95, 18, Text.literal("Цена")
            );
            priceField.setText(String.valueOf(Math.round(itemData.unitPrice)));
            priceField.setChangedListener(val -> {
                String digits = val.replaceAll("[^0-9.]", "");
                if (!digits.isEmpty()) {
                    try {
                        itemData.unitPrice = Double.parseDouble(digits);
                        BotConfig.save();
                    } catch (NumberFormatException ignored) {
                    }
                }
            });
            this.addDrawableChild(priceField);
            visiblePriceFields.add(priceField);

            // Кнопка ВКЛ / ВЫКЛ для предмета (убрать булыжник и т.д.)
            ButtonWidget toggleBtn = ButtonWidget.builder(
                    Text.literal(itemData.enabled ? "§aВКЛ" : "§cВЫКЛ"),
                    btn -> {
                        itemData.enabled = !itemData.enabled;
                        BotConfig.save();
                        btn.setMessage(Text.literal(itemData.enabled ? "§aВКЛ" : "§cВЫКЛ"));
                    }
            ).dimensions(centerX + 122, rowY, 58, 18).build();
            this.addDrawableChild(toggleBtn);
            visibleToggleButtons.add(toggleBtn);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listBottom = this.height - 28;
        int maxVisibleRows = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredItems.size() - maxVisibleRows);

        if (verticalAmount < 0 && scrollOffset < maxScroll) {
            scrollOffset++;
            rebuildItemList();
            return true;
        } else if (verticalAmount > 0 && scrollOffset > 0) {
            scrollOffset--;
            rebuildItemList();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int listBottom = this.height - 28;
        int maxVisibleRows = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);

        if (filteredItems.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("§7Предметов пока нет. Нажмите «Скан цен (TAB)»."),
                    centerX, LIST_TOP + 20, 0xAAAAAA
            );
        } else {
            for (int i = 0; i < maxVisibleRows; i++) {
                int dataIndex = scrollOffset + i;
                if (dataIndex >= filteredItems.size()) break;

                BotConfig.ItemData itemData = filteredItems.get(dataIndex).getValue();
                int rowY = LIST_TOP + i * ROW_HEIGHT;

                String nameColor = itemData.enabled ? "§f" : "§8";
                String displayName = itemData.displayName;
                if (displayName.length() > 26) {
                    displayName = displayName.substring(0, 26) + "...";
                }

                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(nameColor + displayName),
                        centerX - 180, rowY + 5, 0xFFFFFF
                );
            }
        }

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("§7Всего предметов: " + filteredItems.size() + " | Статус: " + AuctionSniper.INSTANCE.getHudStatus()),
                centerX, this.height - 16, 0xCCCCCC
        );
    }

    @Override
    public void close() {
        BotConfig.save();
        super.close();
    }
}
