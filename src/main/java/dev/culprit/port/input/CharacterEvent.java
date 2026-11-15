package dev.culprit.port.input;

import net.minecraft.util.StringUtil;

/** Minecraft 26.x's typed-character event as a screen is given it; made from 1.21.1's character and modifiers (this branch's own). */
public record CharacterEvent(char character, int modifiers) {
    /** Whether the character may go in chat (as 26.x asks of its event). */
    public boolean isAllowedChatCharacter() {
        return net.minecraft.SharedConstants.isAllowedChatCharacter(this.character);
    }

    /** The character as a code point, as 26.x's event gives it. */
    public int codepoint() {
        return this.character;
    }
}
