package dev.luminous.api.interfaces;

import net.minecraft.text.Text;

public interface IChatHudHook {
    void alienClient$addMessage(Text message, int id);

    void alienClient$addMessage(Text message);

    void alienClient$addMessageOutSync(Text message, int id);
}
