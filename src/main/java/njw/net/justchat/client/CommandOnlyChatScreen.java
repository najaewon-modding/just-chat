package njw.net.justchat.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class CommandOnlyChatScreen extends ChatScreen {
    public CommandOnlyChatScreen() {
        super("/", false);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (!isValidCommand()) return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void handleChatInput(String message, boolean addToRecent) {
        if (!message.startsWith("/")) return;
        super.handleChatInput(message, addToRecent);
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!shouldShowWarning()) return;
        Component warning = Component.translatable("screen.njw_just_chat.command_only_warning");
        graphics.text(this.font, warning, 4, this.height - 26, 0xFFFF5555, true);
    }

    private boolean isValidCommand() {
        if (this.input == null) return false;
        String value = this.input.getValue();
        return !value.isBlank() && value.startsWith("/");
    }

    private boolean shouldShowWarning() {
        if (this.input == null) return false;
        String value = this.input.getValue();
        return !value.isEmpty() && !value.startsWith("/");
    }
}