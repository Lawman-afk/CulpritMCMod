package dev.culprit.client;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.culprit.net.CulpritNetwork;
import dev.culprit.scan.FrameScan;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import dev.culprit.port.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The scan menu (F8 by default): a scan of the server's ticks (what lags the server; it needs Culprit on the server,
 * operators only by default) or of this game's frames (what lowers the frame rate; on any server), for 10, 30 or 60
 * seconds, or a stop; each one's progress; and the way to the results. The chat says when a scan starts and ends.
 */
public final class ScanScreen extends Screen {
    private static final int W = 300, H = 204;
    private static final int PANEL = 0xEC101216, EDGE = 0xFF3A3F4A, TEXT = 0xFFFFFFFF, DIM = 0xFFA8AFBA, HEAD = 0xFFFFC34D;
    private static final int[] LENGTHS = {10, 30, 60};

    private final List<Button> tickLengths = new ArrayList<>(), frameLengths = new ArrayList<>();
    private @Nullable Button stopTicks, stopFrames;

    public ScanScreen() {
        super(Component.translatableWithFallback("culprit.scan.title2", "Culprit: scan"));
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
        tickLengths.clear();
        frameLengths.clear();
        int x0 = left() + 12;
        int x = x0;
        for (int seconds : LENGTHS) {
            tickLengths.add(addRenderableWidget(Button.builder(tr("short", "%s s", seconds), b -> startTicks(seconds)).bounds(x, top() + 70, 58, 20).build()));
            x += 62;
        }
        stopTicks = addRenderableWidget(Button.builder(tr("stop_short", "Stop"), b -> PacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(0)))
                .bounds(x, top() + 70, W - 24 - (x - x0), 20).build());
        x = x0;
        for (int seconds : LENGTHS) {
            frameLengths.add(addRenderableWidget(Button.builder(tr("short", "%s s", seconds), b -> startFrames(seconds)).bounds(x, top() + 128, 58, 20).build()));
            x += 62;
        }
        stopFrames = addRenderableWidget(Button.builder(tr("stop_short", "Stop"), b -> FrameScan.stop()).bounds(x, top() + 128, W - 24 - (x - x0), 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("culprit.scan.results", "Results (%s)",
                CulpritKeys.RESULTS.getTranslatedKeyMessage()), b -> Minecraft.getInstance().setScreen(new CulpritScreen()))
                .bounds(x0, top() + H - 28, 140, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("culprit.screen.done", "Done"), b -> onClose())
                .bounds(left() + W - 72, top() + H - 28, 60, 20).build());
        // Ask whether this player may scan the server, so its buttons say so before a press does.
        if (serverHasCulprit()) {
            PacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(-1));
        }
        tick();
    }

    private void startTicks(int seconds) {
        PacketDistributor.sendToServer(new CulpritNetwork.ScanRequest(seconds));
        onClose();                                      // the scan runs on; chat says when it starts and ends
    }

    private void startFrames(int seconds) {
        onClose();                                      // (the menu itself is not in the frames scanned)
        ClientReport.startFrameScan(seconds);
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
        for (Button b : tickLengths) {
            b.active = server && allowed && !running;
        }
        if (stopTicks != null) {
            stopTicks.active = server && allowed && running;
        }
        boolean frames = FrameScan.secondsLeft() >= 0;
        for (Button b : frameLengths) {
            b.active = !frames;
        }
        if (stopFrames != null) {
            stopFrames.active = frames;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(net.minecraft.client.gui.GuiGraphics gg_, int mouseX, int mouseY, float partial) { GuiGraphicsExtractor g = new GuiGraphicsExtractor(gg_);
        super.renderBackground(g.unwrap(), mouseX, mouseY, partial);
        g.fill(left(), top(), left() + W, top() + H, PANEL);
        g.outline(left(), top(), W, H, EDGE);
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics gg_, int mouseX, int mouseY, float partial) { GuiGraphicsExtractor g = new GuiGraphicsExtractor(gg_);
        super.render(g.unwrap(), mouseX, mouseY, partial);
        int x = left() + 12, y = top() + 10;
        g.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD), x, y, HEAD);
        y += 14;
        for (FormattedCharSequence line : this.font.split(tr("about2",
                "Times what the server does, or what this game draws, then shows where the time went. Nothing in your world is changed."),
                W - 24)) {
            g.text(this.font, line, x, y, DIM);
            y += 10;
        }
        g.text(this.font, tr("ticks", "Server ticks: what lags the server").withStyle(ChatFormatting.BOLD), x, top() + 58, TEXT);
        g.text(this.font, tickStatus(), x, top() + 94, TEXT);
        progress(g, top() + 105, ClientReport.secondsLeft(), ClientReport.scanSeconds());
        g.text(this.font, tr("frames", "Frames: what lowers your frame rate").withStyle(ChatFormatting.BOLD), x, top() + 116, TEXT);
        g.text(this.font, frameStatus(), x, top() + 152, TEXT);
        progress(g, top() + 163, FrameScan.secondsLeft(), FrameScan.seconds());
    }

    private void progress(GuiGraphicsExtractor g, int y, int left, int seconds) {
        if (left >= 0 && seconds > 0) {
            float done = 1f - left / (float) seconds;
            int bx = left() + 12, bw = W - 24;
            g.fill(bx, y, bx + bw, y + 3, 0x40FFFFFF);
            g.fill(bx, y, bx + Math.round(bw * Math.clamp(done, 0f, 1f)), y + 3, 0xFFFFC34D);
        }
    }

    private Component tickStatus() {
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

    private Component frameStatus() {
        int left = FrameScan.secondsLeft();
        if (left >= 0) {
            return tr("running", "Scanning: %s s left...", left).withStyle(ChatFormatting.YELLOW);
        }
        return tr("frames_ready", "Pick how long; play as usual while it scans.").withStyle(ChatFormatting.GRAY);
    }
}
