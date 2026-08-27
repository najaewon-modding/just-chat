package njw.net.justchat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import njw.net.justchat.config.ClientConfig;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.network.DeleteChatPayload;
import njw.net.justchat.network.RequestChatHistoryPayload;
import njw.net.justchat.network.RequestPlayerSuggestionsPayload;
import njw.net.justchat.network.SendChatPayload;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CustomChatScreen extends Screen {
    private static final Pattern PLAYER_TAG_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{0,16})$");
    private static final int INPUT_HEIGHT = 20;
    private static final int INPUT_SIDE_MARGIN = 8;
    private static final int INPUT_BOTTOM_MARGIN = 8;
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int MESSAGE_LINE_HEIGHT = 12;
    private static final int DATE_LINE_HEIGHT = 14;
    private static final int MESSAGE_TOP = 24;
    private static final int MESSAGE_BOTTOM_OFFSET = 58;
    private static final int SCROLL_LINES = 3;
    private static final int DELETE_RIGHT_MARGIN = 8;
    private static final int SUGGESTION_ROW_HEIGHT = 12;
    private static final int SUGGESTION_PADDING = 4;
    private static final int SUGGESTION_MIN_WIDTH = 100;
    private EditBox messageInput;
    private boolean commandWarning;
    private int scrollOffset;
    private List<String> playerSuggestions = List.of();
    private String requestedSuggestionQuery;
    private int selectedSuggestion;

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
        this.messageInput.setResponder(this::onInputChanged);
        this.addRenderableWidget(this.messageInput);
        this.setInitialFocus(this.messageInput);
        if (ChatClientState.beginInitialHistoryRequest()) {
            ClientPacketDistributor.sendToServer(RequestChatHistoryPayload.latest());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!playerSuggestions.isEmpty()) {
            if (event.key() == GLFW.GLFW_KEY_UP) {
                selectedSuggestion = Math.floorMod(selectedSuggestion - 1, playerSuggestions.size());
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_DOWN) {
                selectedSuggestion = (selectedSuggestion + 1) % playerSuggestions.size();
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_TAB) {
                applySuggestion(selectedSuggestion);
                return true;
            }
        }

        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            sendMessage();
            return true;
        }

        boolean handled = super.keyPressed(event);
        refreshSuggestions();
        return handled;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int suggestion = findSuggestionAt(event.x(), event.y());
            if (suggestion >= 0) {
                applySuggestion(suggestion);
                return true;
            }

            ChatMessage message = findDeleteTarget(event.x(), event.y());
            if (message != null) {
                ClientPacketDistributor.sendToServer(new DeleteChatPayload(message.id()));
                return true;
            }
        }

        boolean handled = super.mouseClicked(event, doubleClick);
        refreshSuggestions();
        return handled;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY == 0.0) return super.mouseScrolled(x, y, scrollX, scrollY);
        int amount = Math.max(1, (int) Math.round(Math.abs(scrollY) * SCROLL_LINES));
        int maxOffset = getMaxScrollOffset();
        if (scrollY > 0.0) scrollOffset = Math.min(maxOffset, scrollOffset + amount);
        else scrollOffset = Math.max(0, scrollOffset - amount);
        if (scrollY > 0.0 && scrollOffset >= maxOffset) requestOlderHistory();
        return true;
    }

    public void updatePlayerSuggestions(String query, List<String> names) {
        TagQuery active = getActiveTagQuery();
        if (active == null || !active.query().equalsIgnoreCase(query)) return;
        playerSuggestions = List.copyOf(names);
        selectedSuggestion = 0;
    }

    private void onInputChanged(String value) {
        commandWarning = value.trim().startsWith("/");
        refreshSuggestions();
    }

    private void refreshSuggestions() {
        TagQuery active = getActiveTagQuery();
        if (active == null) {
            clearSuggestions();
            return;
        }

        if (active.query().equals(requestedSuggestionQuery)) return;
        requestedSuggestionQuery = active.query();
        playerSuggestions = List.of();
        selectedSuggestion = 0;
        ClientPacketDistributor.sendToServer(new RequestPlayerSuggestionsPayload(active.query()));
    }

    private TagQuery getActiveTagQuery() {
        if (messageInput == null) return null;
        int cursor = messageInput.getCursorPosition();
        String value = messageInput.getValue();
        if (cursor < 0 || cursor > value.length()) return null;
        Matcher matcher = PLAYER_TAG_PATTERN.matcher(value.substring(0, cursor));
        if (!matcher.find()) return null;
        return new TagQuery(matcher.start(1) - 1, matcher.group(1));
    }

    private void applySuggestion(int index) {
        if (index < 0 || index >= playerSuggestions.size()) return;
        TagQuery active = getActiveTagQuery();
        if (active == null) return;

        String value = messageInput.getValue();
        int cursor = messageInput.getCursorPosition();
        String replacement = "@" + playerSuggestions.get(index) + " ";
        String newValue = value.substring(0, active.start()) + replacement + value.substring(cursor);
        int newCursor = active.start() + replacement.length();

        messageInput.setValue(newValue);
        messageInput.setCursorPosition(newCursor);
        messageInput.setHighlightPos(newCursor);
        clearSuggestions();
    }

    private void clearSuggestions() {
        playerSuggestions = List.of();
        requestedSuggestionQuery = null;
        selectedSuggestion = 0;
    }

    private void sendMessage() {
        if (this.messageInput == null) return;
        String content = this.messageInput.getValue().trim();
        if (content.isEmpty() || content.startsWith("/")) return;
        ClientPacketDistributor.sendToServer(new SendChatPayload(content));
        this.messageInput.setValue("");
        clearSuggestions();
        if (ClientConfig.CLOSE_CHAT_AFTER_SEND.get() && this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    private void requestOlderHistory() {
        if (!ChatClientState.beginOlderHistoryRequest()) return;
        long beforeId = ChatClientState.oldestPersistentId();
        ClientPacketDistributor.sendToServer(
                new RequestChatHistoryPayload(beforeId, RequestChatHistoryPayload.DEFAULT_LIMIT)
        );
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
        scrollOffset = Math.min(scrollOffset, getMaxScrollOffset());
        graphics.text(this.font, this.title, 8, 8, 0xFFFFFFFF, true);
        renderMessages(graphics, mouseX, mouseY);
        Component notice = this.commandWarning
                ? Component.translatable("screen.njw_just_chat.command_notice")
                : Component.translatable("screen.njw_just_chat.test_notice");
        int color = this.commandWarning ? 0xFFFFAA00 : 0xFFAAAAAA;
        graphics.text(this.font, notice, 8, this.height - 42, color, false);
        renderSuggestions(graphics);
    }

    private void renderMessages(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = this.height - MESSAGE_BOTTOM_OFFSET;
        int start = ChatClientState.size() - 1 - scrollOffset;
        long now = System.currentTimeMillis();
        UUID playerUuid = getPlayerUuid();
        ActiveTextCollector textRenderer = graphics.textRenderer(
                GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR
        );

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            ChatClientEntry entry = ChatClientState.get(i);
            Component line = createDisplayLine(entry);
            textRenderer.accept(8, y, line);

            if (canDelete(entry, playerUuid, now) && isMouseOverRow(mouseX, mouseY, y)) {
                Component delete = Component.translatable("screen.njw_just_chat.delete")
                        .withStyle(ChatFormatting.RED);
                int deleteX = this.width - DELETE_RIGHT_MARGIN - this.font.width(delete);
                graphics.text(this.font, delete, deleteX, y, 0xFFFF5555, false);
            }

            y -= MESSAGE_LINE_HEIGHT;
            if (!isFirstOfDate(i)) continue;
            if (y < MESSAGE_TOP) break;
            Component date = Component.literal(
                    "--- " + ChatTimeFormatter.formatDate(entry.createdAt()) + " ---"
            );
            int dateX = (this.width - this.font.width(date)) / 2;
            graphics.text(this.font, date, dateX, y, 0xFFAAAAAA, false);
            y -= DATE_LINE_HEIGHT;
        }
    }

    private void renderSuggestions(GuiGraphicsExtractor graphics) {
        if (playerSuggestions.isEmpty()) return;
        int inputY = this.height - INPUT_HEIGHT - INPUT_BOTTOM_MARGIN;
        int width = getSuggestionBoxWidth();
        int height = playerSuggestions.size() * SUGGESTION_ROW_HEIGHT;
        int top = inputY - SUGGESTION_PADDING - height;
        int left = INPUT_SIDE_MARGIN;

        graphics.fill(left, top, left + width, top + height, 0xE0101010);

        for (int i = 0; i < playerSuggestions.size(); i++) {
            int y = top + i * SUGGESTION_ROW_HEIGHT;
            if (i == selectedSuggestion) {
                graphics.fill(left, y, left + width, y + SUGGESTION_ROW_HEIGHT, 0xA0505050);
            }
            graphics.text(this.font, "@" + playerSuggestions.get(i), left + 4, y + 2, 0xFFFFFFFF, false);
        }
    }

    private int findSuggestionAt(double mouseX, double mouseY) {
        if (playerSuggestions.isEmpty()) return -1;
        int inputY = this.height - INPUT_HEIGHT - INPUT_BOTTOM_MARGIN;
        int width = getSuggestionBoxWidth();
        int height = playerSuggestions.size() * SUGGESTION_ROW_HEIGHT;
        int top = inputY - SUGGESTION_PADDING - height;
        int left = INPUT_SIDE_MARGIN;

        if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= top + height) return -1;
        int index = (int) ((mouseY - top) / SUGGESTION_ROW_HEIGHT);
        return index >= 0 && index < playerSuggestions.size() ? index : -1;
    }

    private int getSuggestionBoxWidth() {
        int width = SUGGESTION_MIN_WIDTH;
        for (String name : playerSuggestions) {
            width = Math.max(width, this.font.width("@" + name) + 8);
        }
        return Math.min(width, this.width - INPUT_SIDE_MARGIN * 2);
    }

    private ChatMessage findDeleteTarget(double mouseX, double mouseY) {
        UUID playerUuid = getPlayerUuid();
        if (playerUuid == null) return null;
        long now = System.currentTimeMillis();
        int y = this.height - MESSAGE_BOTTOM_OFFSET;
        int start = ChatClientState.size() - 1 - scrollOffset;

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            ChatClientEntry entry = ChatClientState.get(i);
            if (canDelete(entry, playerUuid, now) && isMouseOverDelete(mouseX, mouseY, y)) {
                return entry.chatMessage();
            }
            y -= MESSAGE_LINE_HEIGHT;
            if (isFirstOfDate(i)) y -= DATE_LINE_HEIGHT;
        }
        return null;
    }

    private Component createDisplayLine(ChatClientEntry entry) {
        String time = ChatTimeFormatter.formatTime(entry.createdAt());
        if (entry.isPlayer() && entry.chatMessage().deleted()) {
            return Component.literal("[" + time + "] ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(entry.displayMessage());
        }
        return Component.literal("[" + time + "] ").append(entry.displayMessage());
    }

    private boolean canDelete(ChatClientEntry entry, UUID playerUuid, long now) {
        return playerUuid != null && entry.isPlayer() && entry.chatMessage().canDelete(playerUuid, now);
    }

    private boolean isMouseOverRow(double mouseX, double mouseY, int y) {
        return mouseX >= 8 && mouseX < this.width - 8
                && mouseY >= y && mouseY < y + this.font.lineHeight;
    }

    private boolean isMouseOverDelete(double mouseX, double mouseY, int y) {
        Component delete = Component.translatable("screen.njw_just_chat.delete");
        int deleteX = this.width - DELETE_RIGHT_MARGIN - this.font.width(delete);
        return mouseX >= deleteX && mouseX < this.width - DELETE_RIGHT_MARGIN
                && mouseY >= y && mouseY < y + this.font.lineHeight;
    }

    private UUID getPlayerUuid() {
        if (this.minecraft == null || this.minecraft.player == null) return null;
        return this.minecraft.player.getUUID();
    }

    private int getMaxScrollOffset() {
        int size = ChatClientState.size();
        if (size == 0) return 0;
        int availableHeight = Math.max(
                MESSAGE_LINE_HEIGHT,
                this.height - MESSAGE_BOTTOM_OFFSET - MESSAGE_TOP + MESSAGE_LINE_HEIGHT
        );
        int usedHeight = 0;
        int visibleEntries = 0;

        for (int i = 0; i < size; i++) {
            int entryHeight = MESSAGE_LINE_HEIGHT;
            if (isFirstOfDate(i)) entryHeight += DATE_LINE_HEIGHT;
            if (usedHeight + entryHeight > availableHeight) break;
            usedHeight += entryHeight;
            visibleEntries++;
        }

        return Math.max(0, size - Math.max(1, visibleEntries));
    }

    private boolean isFirstOfDate(int index) {
        if (index == 0) return true;
        return !ChatTimeFormatter.isSameDate(
                ChatClientState.get(index).createdAt(),
                ChatClientState.get(index - 1).createdAt()
        );
    }

    private record TagQuery(int start, String query) {}
}