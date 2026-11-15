package dev.culprit.legacy;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import net.minecraft.client.Camera;
import net.minecraft.util.Mth;

/**
 * The camera's turn as 1.21 gives it, on 1.20.1 (this branch's own). 1.20.1's camera faces +z where 1.21's faces -z
 * (1.21 turned it half round the vertical): the overlay's sums of what is ahead, right and up, and its labels' facing,
 * are made in 1.21's terms. And Forge 1.20.1's after-level render stage gives the projection as its pose, not the view's
 * turn: the view's turn is made here as 1.20.1's game renderer makes it (pitch, then yaw half round).
 */
public final class CameraTurn {
    private static final Quaternionf FACING = new Quaternionf();
    private static final Matrix4f VIEW = new Matrix4f();

    private CameraTurn() {}

    /** The camera's orientation in 1.21's terms (one kept quaternion, set again at each call). */
    public static Quaternionf of(Camera camera) {
        return FACING.set(camera.rotation()).rotateY((float) Math.PI);
    }

    /** The view's turn, world to camera, as 1.20.1's game renderer turns its pose (one kept matrix, set again at each call). */
    public static Matrix4f view(Camera camera) {
        return VIEW.rotationX(camera.getXRot() * Mth.DEG_TO_RAD).rotateY((camera.getYRot() + 180f) * Mth.DEG_TO_RAD);
    }
}
