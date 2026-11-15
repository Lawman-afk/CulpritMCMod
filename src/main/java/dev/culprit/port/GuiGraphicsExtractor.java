package dev.culprit.port;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Minecraft 26.x's GUI graphics as main's screens and HUD layers draw with them, over 1.21.1's {@link GuiGraphics}
 * (this branch's own: what main uses of it). A tooltip set for the next frame is drawn after the rest of the screen, as
 * on 26.x (the screen's own tooltip pass), so nothing drawn after it covers it.
 */
public final class GuiGraphicsExtractor {
    private final GuiGraphics g;

    public GuiGraphicsExtractor(GuiGraphics g) {
        this.g = g;
    }

    /** 1.21.1's graphics underneath. */
    public GuiGraphics unwrap() {
        return this.g;
    }

    public int guiWidth() {
        return this.g.guiWidth();
    }

    public int guiHeight() {
        return this.g.guiHeight();
    }

    public void fill(int x0, int y0, int x1, int y1, int col) {
        this.g.fill(x0, y0, x1, y1, col);
    }

    public void outline(int x, int y, int width, int height, int color) {
        this.g.renderOutline(x, y, width, height, color);
    }

    public void text(Font font, @Nullable String str, int x, int y, int color) {
        this.g.drawString(font, str, x, y, color);
    }

    public void text(Font font, FormattedCharSequence str, int x, int y, int color) {
        this.g.drawString(font, str, x, y, color);
    }

    public void text(Font font, Component str, int x, int y, int color) {
        this.g.drawString(font, str, x, y, color);
    }

    public void text(Font font, Component str, int x, int y, int color, boolean dropShadow) {
        this.g.drawString(font, str, x, y, color, dropShadow);
    }

    public void setTooltipForNextFrame(Component text, int x, int y) {
        Font font = Minecraft.getInstance().font;
        tooltip(font.split(text, 10_000), x, y);
    }

    public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
        tooltip(lines, x, y);
    }

    private void tooltip(List<FormattedCharSequence> lines, int x, int y) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) {
            screen.setTooltipForNextRenderPass(lines);
        } else {
            this.g.renderTooltip(Minecraft.getInstance().font, lines, x, y);
        }
    }
}
