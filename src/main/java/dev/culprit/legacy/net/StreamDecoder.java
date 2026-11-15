package dev.culprit.legacy.net;

/** Minecraft 1.21's stream decoder. */
@FunctionalInterface
public interface StreamDecoder<I, T> {
    T decode(I buf);
}
