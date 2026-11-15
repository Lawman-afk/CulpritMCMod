package dev.culprit.port.input;

/** Minecraft 26.x's mouse button event as a screen is given it; made from 1.21.1's position and button (this branch's own). */
public record MouseButtonEvent(double x, double y, int button) {
    public int modifiers() {
        return 0;
    }
}
