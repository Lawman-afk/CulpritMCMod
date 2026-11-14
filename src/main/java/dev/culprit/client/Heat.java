package dev.culprit.client;

/**
 * The colour scale for costs (DESIGN §4.4): pale yellow for a few microseconds a tick, through orange and red, to deep
 * magenta for many milliseconds. Its lightness falls all the way, so it reads as one scale for colour-blind players
 * too, and the costliest things look the most alarming. Costs are placed on it by their logarithm: a hundred times
 * the cost is a long step.
 */
public final class Heat {
    private static final int[] STOPS = {0xFFF1A8, 0xFFD166, 0xFFA040, 0xFF6B3D, 0xF0304A, 0xC8178A};
    /** The cost at the top of the scale: 5 ms a tick (a tenth of the tick). */
    private static final double TOP_US = 5000.0;

    private Heat() {}

    /** 0 to 1 for a cost per tick (or per frame), from 1 µs or less to {@link #TOP_US} or more. */
    public static float of(long nsPerTick) {
        double us = Math.max(nsPerTick / 1000.0, 1.0);
        return (float) Math.clamp(Math.log10(us) / Math.log10(TOP_US), 0.0, 1.0);
    }

    /** The scale's colour at {@code t} (0 to 1), as 0xRRGGBB. */
    public static int rgb(float t) {
        float x = Math.clamp(t, 0f, 1f) * (STOPS.length - 1);
        int i = Math.min((int) x, STOPS.length - 2);
        float f = x - i;
        int a = STOPS[i];
        int b = STOPS[i + 1];
        int r = Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
        int bl = Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
        return (r << 16) | (g << 8) | bl;
    }

    public static int argb(float t, int alpha) {
        return (alpha << 24) | rgb(t);
    }

    /** The colour for a cost per tick, opaque, for text. */
    public static int text(long nsPerTick) {
        return 0xFF000000 | rgb(of(nsPerTick));
    }
}
