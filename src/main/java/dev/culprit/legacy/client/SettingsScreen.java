package dev.culprit.legacy.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import dev.culprit.config.CulpritClientConfig;

/**
 * The player's settings on one page (Forge 1.20.1 draws no settings page for a mod, as NeoForge does on the other
 * versions): each setting's name on the left and its control on the right, its line of help on hovering, as NeoForge's
 * page has them; Reset puts them back as they came, Done saves them and goes back. Opened from the results screen's
 * Settings and from the mod list's Config.
 */
public final class SettingsScreen extends Screen {
    private static final int ROW = 25, CONTROL_W = 150, LABEL_W = 160;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();

    private record Row(Component label, AbstractWidget control) {}

    public SettingsScreen(Screen parent) {
        super(Component.translatableWithFallback("culprit.settings.title", "Culprit: your settings"));
        this.parent = parent;
    }

    private static Component name(String key, String english) {
        return Component.translatableWithFallback("culprit.configuration." + key, english);
    }

    private static Tooltip help(String key) {
        return Tooltip.create(Component.translatable("culprit.configuration." + key + ".tooltip"));
    }

    @Override
    protected void init() {
        this.rows.clear();
        int controlX = this.width / 2 + (LABEL_W + CONTROL_W) / 2 - CONTROL_W, y = 40;
        this.rows.add(new Row(name("range", "Box things within"), slider(controlX, y, "range", 16, 128,
                CulpritClientConfig.RANGE::get, CulpritClientConfig.RANGE::set)));
        y += ROW;
        this.rows.add(new Row(name("labels", "Labels"), slider(controlX, y, "labels", 0, 30,
                CulpritClientConfig.LABELS::get, CulpritClientConfig.LABELS::set)));
        y += ROW;
        this.rows.add(new Row(name("hideAfterMinutes", "Hide after (minutes)"), slider(controlX, y, "hideAfterMinutes", 0, 120,
                CulpritClientConfig.HIDE_AFTER::get, CulpritClientConfig.HIDE_AFTER::set)));
        y += ROW;
        CycleButton<Boolean> hud = CycleButton.onOffBuilder(CulpritClientConfig.HUD_LINE.get()).displayOnlyValue()
                .create(controlX, y, CONTROL_W, 20, name("hudLine", "Show the HUD line"), (b, on) -> CulpritClientConfig.HUD_LINE.set(on));
        hud.setTooltip(help("hudLine"));
        this.rows.add(new Row(name("hudLine", "Show the HUD line"), hud));
        y += ROW;
        CycleButton<CulpritClientConfig.Corner> corner = CycleButton.<CulpritClientConfig.Corner>builder(CulpritClientConfig.Corner::getTranslatedName)
                .withValues(CulpritClientConfig.Corner.values()).withInitialValue(CulpritClientConfig.HUD_CORNER.get()).displayOnlyValue()
                .create(controlX, y, CONTROL_W, 20, name("hudCorner", "Corner"), (b, c) -> CulpritClientConfig.HUD_CORNER.set(c));
        corner.setTooltip(help("hudCorner"));
        this.rows.add(new Row(name("hudCorner", "Corner"), corner));
        for (Row row : this.rows) {
            this.addRenderableWidget(row.control());
        }
        int bottom = this.height - 28;
        this.addRenderableWidget(Button.builder(Component.translatable("controls.reset"), b -> reset())
                .bounds(this.width / 2 - 155, bottom, 150, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 + 5, bottom, 150, 20).build());
    }

    private AbstractSliderButton slider(int x, int y, String key, int min, int max, IntSupplier get, Consumer<Integer> set) {
        AbstractSliderButton slider = new AbstractSliderButton(x, y, CONTROL_W, 20, Component.empty(),
                (Mth.clamp(get.getAsInt(), min, max) - min) / (double) (max - min)) {
            {
                this.updateMessage();
            }

            private int value() {
                return min + (int) Math.round(this.value * (max - min));
            }

            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal(Integer.toString(this.value())));
            }

            @Override
            protected void applyValue() {
                set.accept(this.value());
            }
        };
        slider.setTooltip(help(key));
        return slider;
    }

    /** Every setting back as it came. */
    private void reset() {
        CulpritClientConfig.RANGE.set(CulpritClientConfig.RANGE.getDefault());
        CulpritClientConfig.LABELS.set(CulpritClientConfig.LABELS.getDefault());
        CulpritClientConfig.HIDE_AFTER.set(CulpritClientConfig.HIDE_AFTER.getDefault());
        CulpritClientConfig.HUD_LINE.set(CulpritClientConfig.HUD_LINE.getDefault());
        CulpritClientConfig.HUD_CORNER.set(CulpritClientConfig.HUD_CORNER.getDefault());
        this.rebuildWidgets();
    }

    @Override
    public void onClose() {
        CulpritClientConfig.SPEC.save();
        dev.culprit.client.HudLine.sync();                   // (the HUD line asks the server again if it was turned on)
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        int labelX = this.width / 2 - (LABEL_W + CONTROL_W) / 2;
        for (Row row : this.rows) {
            g.drawString(this.font, row.label(), labelX, row.control().getY() + 6, 0xFFFFFF);
        }
        super.render(g, mouseX, mouseY, partial);
    }
}
