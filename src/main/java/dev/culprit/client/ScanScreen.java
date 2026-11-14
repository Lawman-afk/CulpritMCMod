package dev.culprit.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.culprit.net.CulpritNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The scan menu (F8 by default; DESIGN §4.4): pick how long to scan the server and start it; while it runs, how long
 * is left and a Stop button. The scan goes on with the menu closed; chat says when it starts and ends, and the results
 * key opens what it found.
 */
public final class ScanScreen extends Screen {
    private static final int W = 280, H = 150;
    private static final int PANEL = 0xEC101216, EDGE = 0xFF3A3F4A, TEXT = 0xFFFFFFFF, DIM = 0xFFA8AFBA, HEAD = 0xFFFFC34D;
    private static final int[] LENGTHS = {10, 30, 60};

    private final List<Button> lengths = new ArrayList<>();
    private @Nullable Button stop;

    public ScanScreen() {
        super(Component.translatableWithFallback("culprit.scan.title", "Culprit: scan the server"));
    }

    private static MutableComponent tr(String key, String english, Object... args) {
        return Component.translatableWithFallback("culprit.scan." + key, english, args);
    }

    private int left() {
        return (this.width - W) / 2;
    }

    private int top() {
        return (this.height - H) / 2;
    }

    @Override
    protected void init() {
        lengths.clear();
        int x = left() + 12, y = top() + 74;
        for (int seconds : LENGTHS) {
            lengths.add(addRenderableWidget(Button.builder(tr("length", "%s seconds", seconds), b -> start(seconds)).bounds(x, y, 82, 20).build()));
            x += 86;
        }
        stop = addRenderableWidget(Button.builder(tr("stop", "Stop the scan"), b -> ClientPacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(0)))
                .bounds(left() + 12, top() + 98, 120, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("culprit.scan.results", "Results (%s)",
                CulpritKeys.RESULTS.getTranslatedKeyMessage()), b -> Minecraft.getInstance().gui.setScreen(new CulpritScreen()))
                .bounds(left() + 136, top() + 98, 132, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("culprit.screen.done", "Done"), b -> onClose())
                .bounds(left() + W - 72, top() + H - 26, 60, 20).build());
        // Ask whether this player may scan here, so the buttons say so before a press does.
        if (serverHasCulprit()) {
            ClientPacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(-1));
        }
        tick();
    }

    private void start(int seconds) {
        ClientPacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(seconds));
        onClose();                                      // the scan runs on; chat says when it starts and ends
    }

    static boolean serverHasCulprit() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getConnection() != null && mc.getConnection().hasChannel(CulpritNetwork.ScanRequest.TYPE);
    }

    @Override
    public void tick() {
        boolean server = serverHasCulprit();
        boolean running = ClientReport.secondsLeft() >= 0;
        boolean allowed = ClientReport.state() != CulpritNetwork.Status.DENIED;
        for (Button b : lengths) {
            b.active = server && allowed && !running;
        }
        if (stop != null) {
            stop.active = server && allowed && running;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractBackground(g, mouseX, mouseY, partial);
        g.fill(left(), top(), left() + W, top() + H, PANEL);
        g.outline(left(), top(), W, H, EDGE);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        super.extractRenderState(g, mouseX, mouseY, partial);
        int x = left() + 12, y = top() + 10;
        g.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, y, HEAD);
        y += 14;
        for (FormattedCharSequence line : this.font.split(tr("about",
                "Times everything the server does while it runs, then shows where the tick went. Nothing in your world is changed."),
                W - 24)) {
            g.text(this.font, line, x, y, DIM);
            y += 10;
        }
        g.text(this.font, status(), x, top() + 124, TEXT);
        int left = ClientReport.secondsLeft();
        if (left >= 0 && ClientReport.scanSeconds() > 0) {
            float done = 1f - left / (float) ClientReport.scanSeconds();
            int bx = left() + 12, by = top() + 136, bw = W - 96;
            g.fill(bx, by, bx + bw, by + 4, 0x40FFFFFF);
            g.fill(bx, by, bx + Math.round(bw * Math.clamp(done, 0f, 1f)), by + 4, 0xFFFFC34D);
        }
    }

    private Component status() {
        if (!serverHasCulprit()) {
            return tr("no_server", "This server does not have Culprit: its ticks cannot be scanned.").withStyle(ChatFormatting.GRAY);
        }
        if (ClientReport.state() == CulpritNetwork.Status.DENIED) {
            return tr("denied", "Only operators can scan this server.").withStyle(ChatFormatting.RED);
        }
        int left = ClientReport.secondsLeft();
        if (left >= 0) {
            return tr("running", "Scanning: %s s left...", left).withStyle(ChatFormatting.YELLOW);
        }
        return tr("ready", "Pick how long to scan.").withStyle(ChatFormatting.GRAY);
    }
}
