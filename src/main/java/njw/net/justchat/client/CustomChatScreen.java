package njw.net.justchat.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import njw.net.justchat.config.ClientConfig;
import njw.net.justchat.network.RequestChatHistoryPayload;
import njw.net.justchat.network.SendChatPayload;
import org.lwjgl.glfw.GLFW;

public final class CustomChatScreen extends Screen {
    private static final int INPUT_HEIGHT = 20;
    private static final int INPUT_SIDE_MARGIN = 8;
    private static final int INPUT_BOTTOM_MARGIN = 8;
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MESSAGE_LINE_HEIGHT = 12;
    private static final int DATE_LINE_HEIGHT = 14;
    private EditBox messageInput;
    private boolean commandWarning;

    public CustomChatScreen() {
        super(Component.translatable("screen.njw_just_chat.title"));
    }

    @Override
    protected void init() {
        super.init();
        int inputY = this.height - INPUT_HEIGHT - INPUT_BOTTOM_MARGIN;
        int inputWidth = this.width - INPUT_SIDE_MARGIN * 2;
        this.messageInput = new EditBox(
                this.font,
                INPUT_SIDE_MARGIN,
                inputY,
                inputWidth,
                INPUT_HEIGHT,
                Component.translatable("screen.njw_just_chat.message_input")
        );
        this.messageInput.setMaxLength(MAX_MESSAGE_LENGTH);
        this.messageInput.setResponder(value -> this.commandWarning = value.trim().startsWith("/"));
        this.addRenderableWidget(this.messageInput);
        this.setInitialFocus(this.messageInput);
        ClientPacketDistributor.sendToServer(RequestChatHistoryPayload.latest());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            sendMessage();
            return true;
        }
        return super.keyPressed(event);
    }

    private void sendMessage() {
        if (this.messageInput == null) return;
        String content = this.messageInput.getValue().trim();
        if (content.isEmpty() || content.startsWith("/")) return;
        ClientPacketDistributor.sendToServer(new SendChatPayload(content));
        this.messageInput.setValue("");
        if (ClientConfig.CLOSE_CHAT_AFTER_SEND.get() && this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, this.title, 8, 8, 0xFFFFFFFF, true);
        renderMessages(graphics);
        Component notice = this.commandWarning
                ? Component.translatable("screen.njw_just_chat.command_notice")
                : Component.translatable("screen.njw_just_chat.test_notice");
        int color = this.commandWarning ? 0xFFFFAA00 : 0xFFAAAAAA;
        graphics.text(this.font, notice, 8, this.height - 42, color, false);
    }

    private void renderMessages(GuiGraphicsExtractor graphics) {
        int y = this.height - 58;
        for (int i = ChatClientState.size() - 1; i >= 0 && y >= 24; i--) {
            ChatClientEntry entry = ChatClientState.get(i);
            String time = ChatTimeFormatter.formatTime(entry.createdAt());
            Component line = Component.literal("[" + time + "] ").append(entry.displayMessage());
            graphics.text(this.font, line, 8, y, 0xFFFFFFFF, false);
            y -= MESSAGE_LINE_HEIGHT;
            boolean firstOfDate = i == 0 || !ChatTimeFormatter.isSameDate(
                    entry.createdAt(),
                    ChatClientState.get(i - 1).createdAt()
            );
            if (!firstOfDate) continue;
            if (y < 24) break;
            Component date = Component.literal("--- " + ChatTimeFormatter.formatDate(entry.createdAt()) + " ---");
            int dateX = (this.width - this.font.width(date)) / 2;
            graphics.text(this.font, date, dateX, y, 0xFFAAAAAA, false);
            y -= DATE_LINE_HEIGHT;
        }
    }
}