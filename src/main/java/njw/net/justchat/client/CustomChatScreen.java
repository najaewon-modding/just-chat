package njw.net.justchat.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStackTemplate;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import njw.net.justchat.ChatRules;
import njw.net.justchat.config.ClientConfig;
import njw.net.justchat.data.ChatMessage;
import njw.net.justchat.network.ChatReadStateRequestPayload;
import njw.net.justchat.network.CreateItemTagPayload;
import njw.net.justchat.network.DeleteChatPayload;
import njw.net.justchat.network.ItemTagReference;
import njw.net.justchat.network.PlayerSuggestionsPayload;
import njw.net.justchat.network.RequestChatHistoryPayload;
import njw.net.justchat.network.RequestNewerChatHistoryPayload;
import njw.net.justchat.network.RequestPlayerSuggestionsPayload;
import njw.net.justchat.network.SendChatPayload;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CustomChatScreen extends Screen {
    private static final Pattern PLAYER_TAG_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_])@([A-Za-z0-9_]{0,16})$");

    private static final int INPUT_HEIGHT = 20;
    private static final int INPUT_SIDE_MARGIN = 8;
    private static final int INPUT_BOTTOM_MARGIN = 8;
    private static final int ITEM_BUTTON_WIDTH = 44;
    private static final int INPUT_GAP = 4;
    private static final int MESSAGE_LINE_HEIGHT = 12;
    private static final int DATE_LINE_HEIGHT = 14;
    private static final int READ_BOUNDARY_LINE_HEIGHT = 14;
    private static final int MESSAGE_TOP = 8;
    private static final int MESSAGE_BOTTOM_OFFSET = 58;
    private static final int MESSAGE_LEFT = 8;
    private static final int SCROLL_LINES = 3;
    private static final int PREFETCH_TARGET_ROWS = 30;
    private static final int DELETE_RIGHT_MARGIN = 8;
    private static final int JUMP_BUTTON_SIZE = 20;
    private static final int JUMP_BUTTON_RIGHT_MARGIN = 8;
    private static final int JUMP_BUTTON_GAP = 4;
    private static final long NEW_MESSAGE_NOTICE_MILLIS = 5000L;
    private static final int DEFAULT_NOTICE_COLOR = 0xFFAAAAAA;
    private static final int COMMAND_NOTICE_COLOR = 0xFFFFAA00;
    private static final int NEW_MESSAGE_NOTICE_COLOR = 0xFFFFFF55;
    private static final int SUGGESTION_ROW_HEIGHT = 12;
    private static final int SUGGESTION_PADDING = 4;
    private static final int SUGGESTION_MIN_WIDTH = 100;
    private static final int ONLINE_SUGGESTION_COLOR = 0xFFFFFFFF;
    private static final int OFFLINE_SUGGESTION_COLOR = 0xFF777777;

    private final List<ItemTagReference> itemTagReferences = new ArrayList<>();
    private final Set<UUID> pendingItemRequests = new HashSet<>();

    private EditBox messageInput;
    private Button itemTagButton;
    private Button jumpToLatestButton;
    private boolean commandWarning;
    private int scrollOffset;
    private List<PlayerSuggestionsPayload.Suggestion> playerSuggestions = List.of();
    private String requestedSuggestionQuery;
    private int selectedSuggestion;
    private String draft = "";
    private String lastInputValue = "";
    private int draftCursor;
    private long historyAnchorId = Long.MIN_VALUE;
    private int historyAnchorLineIndex = -1;
    private ViewportAnchor liveViewportAnchor;
    private boolean liveMessageHidden;
    private boolean readStateRequested;
    private boolean switchingToItemPicker;
    private boolean jumpingToLatest;
    private boolean initializedOnce;
    private boolean hasUnseenLiveMessages;
    private long newMessageNoticeUntil;

    public CustomChatScreen() {
        super(Component.translatable("screen.njw_just_chat.title"));
        ChatReadClientState.beginSession();
    }

    @Override
    protected void init() {
        super.init();
        boolean firstInit = !initializedOnce;
        initializedOnce = true;
        switchingToItemPicker = false;

        int inputY = this.height - INPUT_HEIGHT - INPUT_BOTTOM_MARGIN;
        int buttonX = getItemButtonX();
        int inputWidth = getInputWidth();

        this.itemTagButton = Button.builder(
                Component.translatable("screen.njw_just_chat.item_tag"),
                button -> openItemPicker()
        ).bounds(buttonX, inputY, ITEM_BUTTON_WIDTH, INPUT_HEIGHT).build();

        this.jumpToLatestButton = Button.builder(Component.literal("↓"), button -> {}).bounds(
                getJumpButtonX(), getJumpButtonY(), JUMP_BUTTON_SIZE, JUMP_BUTTON_SIZE
        ).build();

        this.messageInput = new EditBox(
                this.font, INPUT_SIDE_MARGIN, inputY, inputWidth, INPUT_HEIGHT,
                Component.translatable("screen.njw_just_chat.message_input")
        );

        this.messageInput.setMaxLength(ChatRules.MAX_MESSAGE_LENGTH);
        this.lastInputValue = this.draft;
        this.messageInput.setResponder(this::onInputChanged);
        this.messageInput.setValue(this.draft);

        int cursor = Math.max(0, Math.min(this.draftCursor, this.draft.length()));
        this.messageInput.setCursorPosition(cursor);
        this.messageInput.setHighlightPos(cursor);

        this.addRenderableWidget(this.messageInput);
        this.addRenderableWidget(this.itemTagButton);
        this.addRenderableOnly(this.jumpToLatestButton);
        this.setInitialFocus(this.messageInput);

        updateItemTagButtonState();
        updateJumpButtonState();

        if (ChatClientState.beginInitialHistoryRequest()) {
            ClientPacketDistributor.sendToServer(
                    new RequestChatHistoryPayload(
                            ChatClientState.activeHistoryRequestId(),
                            Long.MAX_VALUE,
                            ChatClientState.initialHistoryLimit()
                    )
            );
        } else if (firstInit && (ChatClientState.hasNewerHistory() || ChatClientState.historyLoading())) {
            jumpingToLatest = true;
            requestLatestHistory();
        }

        if (!readStateRequested) {
            readStateRequested = true;
            ClientPacketDistributor.sendToServer(ChatReadStateRequestPayload.request());
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (switchingToItemPicker) return;
        if (this.minecraft == null || this.minecraft.player == null) return;
        ClientPacketDistributor.sendToServer(
                ChatReadStateRequestPayload.markRead(ChatReadClientState.readThroughMessageId())
        );
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

            if (isMouseOverJumpButton(event.x(), event.y())) {
                jumpToLatest();
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

        List<RenderRow> rows = buildRenderRows();
        int amount = Math.max(1, (int) Math.round(Math.abs(scrollY) * SCROLL_LINES));
        int maxOffset = getMaxScrollOffset(rows);

        if (scrollY > 0.0) {
            scrollOffset = Math.min(maxOffset, scrollOffset + amount);
        } else {
            scrollOffset = Math.max(0, scrollOffset - amount);
        }

        prefetchHistory(scrollY, rows);
        updateJumpButtonState();
        return true;
    }

    public void beforeLivePersistentMessage(boolean ownMessage) {
        liveMessageHidden = scrollOffset > 0 || ChatClientState.hasNewerHistory() || jumpingToLatest;

        if (liveMessageHidden && !ownMessage) {
            hasUnseenLiveMessages = true;
            newMessageNoticeUntil = System.currentTimeMillis() + NEW_MESSAGE_NOTICE_MILLIS;
        }

        liveViewportAnchor = scrollOffset > 0 ? captureViewportAnchor() : null;
    }

    public void afterLivePersistentMessage(long messageId) {
        if (!liveMessageHidden) ChatReadClientState.markSeen(messageId);

        if (liveViewportAnchor != null) {
            restoreViewportAnchor(liveViewportAnchor);
            liveViewportAnchor = null;
        }

        liveMessageHidden = false;
        updateJumpButtonState();
    }

    public void updatePlayerSuggestions(String query, List<PlayerSuggestionsPayload.Suggestion> suggestions) {
        TagQuery active = getActiveTagQuery();
        if (active == null || !active.query().equalsIgnoreCase(query)) return;
        playerSuggestions = List.copyOf(suggestions);
        selectedSuggestion = 0;
    }

    public void insertItemTag(UUID requestId, UUID token, ItemStackTemplate item) {
        if (!pendingItemRequests.remove(requestId) || messageInput == null) return;

        if (itemTagReferences.size() >= ChatRules.MAX_ITEM_TAGS_PER_MESSAGE) {
            updateItemTagButtonState();
            return;
        }

        String displayText = "[" + item.create().getHoverName().getString() + "]";
        int cursor = messageInput.getCursorPosition();
        String value = messageInput.getValue();
        String insertion = displayText + " ";

        if (value.length() + insertion.length() > ChatRules.MAX_MESSAGE_LENGTH) {
            updateItemTagButtonState();
            return;
        }

        String newValue = value.substring(0, cursor) + insertion + value.substring(cursor);
        int tagStart = cursor;
        int tagEnd = cursor + displayText.length();
        int newCursor = cursor + insertion.length();

        messageInput.setValue(newValue);
        messageInput.setCursorPosition(newCursor);
        messageInput.setHighlightPos(newCursor);
        itemTagReferences.add(new ItemTagReference(token, tagStart, tagEnd, displayText));
        itemTagReferences.sort(Comparator.comparingInt(ItemTagReference::start));
        draftCursor = newCursor;
        clearSuggestions();
        updateItemTagButtonState();
    }

    void requestItemTag(int inventorySlot) {
        if (getItemTagUsage() >= ChatRules.MAX_ITEM_TAGS_PER_MESSAGE) {
            updateItemTagButtonState();
            return;
        }

        UUID requestId = UUID.randomUUID();
        pendingItemRequests.add(requestId);
        ClientPacketDistributor.sendToServer(new CreateItemTagPayload(requestId, inventorySlot));
        updateItemTagButtonState();
    }

    public void onHistoryUpdated() {
        if (jumpingToLatest) {
            if (ChatClientState.hasNewerHistory()) {
                requestLatestHistory();
                return;
            }

            jumpingToLatest = false;
            clearHistoryAnchor();
            scrollOffset = 0;
            updateJumpButtonState();
            return;
        }

        if (historyAnchorId == Long.MIN_VALUE) {
            updateJumpButtonState();
            return;
        }

        restoreViewportAnchor(new ViewportAnchor(historyAnchorId, historyAnchorLineIndex));
        clearHistoryAnchor();
        updateJumpButtonState();
    }

    private void prefetchHistory(double scrollY, List<RenderRow> rows) {
        int maxOffset = getMaxScrollOffset(rows);
        if (maxOffset <= 0) return;

        int threshold = getPrefetchThreshold(maxOffset);

        if (scrollY > 0.0 && maxOffset - scrollOffset <= threshold) {
            requestOlderHistory();
            return;
        }

        if (scrollY < 0.0 && scrollOffset <= threshold) requestNewerHistory();
    }

    private int getPrefetchThreshold(int maxOffset) {
        return Math.max(1, Math.min(PREFETCH_TARGET_ROWS, Math.max(1, maxOffset / 4)));
    }

    private void jumpToLatest() {
        clearSuggestions();
        clearHistoryAnchor();

        if (!ChatClientState.hasNewerHistory() && !ChatClientState.historyLoading()) {
            jumpingToLatest = false;
            scrollOffset = 0;
            updateJumpButtonState();
            return;
        }

        jumpingToLatest = true;
        requestLatestHistory();
        updateJumpButtonState();
    }

    private void requestLatestHistory() {
        if (!jumpingToLatest || !ChatClientState.beginLatestHistoryRequest()) return;
        ClientPacketDistributor.sendToServer(
                new RequestChatHistoryPayload(
                        ChatClientState.activeHistoryRequestId(),
                        Long.MAX_VALUE,
                        ChatClientState.initialHistoryLimit()
                )
        );
    }

    private void updateJumpButtonState() {
        if (isViewingLatest()) hasUnseenLiveMessages = false;
        if (jumpToLatestButton == null) return;

        jumpToLatestButton.visible =
                scrollOffset > 0 || ChatClientState.hasNewerHistory() || jumpingToLatest || hasUnseenLiveMessages;
        jumpToLatestButton.active = !jumpingToLatest;
    }

    private boolean isViewingLatest() {
        return scrollOffset == 0 && !ChatClientState.hasNewerHistory() && !jumpingToLatest;
    }

    private void markCurrentLatestSeen() {
        if (!isViewingLatest()) return;
        ChatReadClientState.markSeen(ChatClientState.newestPersistentId());
    }

    private boolean isMouseOverJumpButton(double mouseX, double mouseY) {
        if (jumpToLatestButton == null || !jumpToLatestButton.visible || !jumpToLatestButton.active) return false;
        int x = getJumpButtonX();
        int y = getJumpButtonY();
        return mouseX >= x && mouseX < x + JUMP_BUTTON_SIZE && mouseY >= y && mouseY < y + JUMP_BUTTON_SIZE;
    }

    private boolean jumpButtonOverlapsRow(int y) {
        if (jumpToLatestButton == null || !jumpToLatestButton.visible) return false;
        int buttonTop = getJumpButtonY();
        int buttonBottom = buttonTop + JUMP_BUTTON_SIZE;
        int rowBottom = y + this.font.lineHeight;
        return y < buttonBottom && rowBottom > buttonTop;
    }

    private void renderJumpButtonPulse(GuiGraphicsExtractor graphics) {
        if (!hasUnseenLiveMessages || jumpToLatestButton == null || !jumpToLatestButton.visible) return;

        int left = getJumpButtonX() - 1;
        int top = getJumpButtonY() - 1;
        int right = left + JUMP_BUTTON_SIZE + 2;
        int bottom = top + JUMP_BUTTON_SIZE + 2;
        int color = getJumpPulseColor();

        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);
    }

    private int getJumpPulseColor() {
        double pulse = (Math.sin(System.currentTimeMillis() / 180.0) + 1.0) * 0.5;
        int green = 170 + (int) Math.round(85.0 * pulse);
        int blue = 32 + (int) Math.round(53.0 * pulse);
        return 0xFFFF0000 | (green << 8) | blue;
    }

    private void openItemPicker() {
        if (this.minecraft == null || this.minecraft.player == null || this.messageInput == null) return;
        if (getItemTagUsage() >= ChatRules.MAX_ITEM_TAGS_PER_MESSAGE) return;

        int cursor = this.messageInput.getCursorPosition();
        this.messageInput.setHighlightPos(cursor);
        this.draft = this.messageInput.getValue();
        this.lastInputValue = this.draft;
        this.draftCursor = cursor;
        this.switchingToItemPicker = true;
        this.minecraft.setScreen(new ItemTagSelectionScreen(this, this.minecraft.player));
    }

    private void onInputChanged(String value) {
        updateItemTagReferences(this.lastInputValue, value);
        this.lastInputValue = value;
        this.draft = value;
        this.commandWarning = value.stripLeading().startsWith("/");
        updateItemTagButtonState();
        refreshSuggestions();
    }

    private void updateItemTagReferences(String oldValue, String newValue) {
        if (oldValue.equals(newValue) || itemTagReferences.isEmpty()) return;

        int prefix = findCommonPrefix(oldValue, newValue);
        int oldEnd = oldValue.length();
        int newEnd = newValue.length();

        while (oldEnd > prefix && newEnd > prefix && oldValue.charAt(oldEnd - 1) == newValue.charAt(newEnd - 1)) {
            oldEnd--;
            newEnd--;
        }

        int delta = newEnd - oldEnd;

        for (int i = 0; i < itemTagReferences.size();) {
            ItemTagReference reference = itemTagReferences.get(i);

            if (oldEnd <= reference.start()) {
                itemTagReferences.set(i, reference.shifted(delta));
                i++;
                continue;
            }

            if (prefix >= reference.end()) {
                i++;
                continue;
            }

            itemTagReferences.remove(i);
        }

        itemTagReferences.removeIf(reference -> !matchesReference(newValue, reference));
        itemTagReferences.sort(Comparator.comparingInt(ItemTagReference::start));
    }

    private int findCommonPrefix(String first, String second) {
        int length = Math.min(first.length(), second.length());
        int index = 0;
        while (index < length && first.charAt(index) == second.charAt(index)) index++;
        return index;
    }

    private boolean matchesReference(String content, ItemTagReference reference) {
        int start = reference.start();
        int end = reference.end();
        if (start < 0 || start >= end || end > content.length()) return false;
        return content.substring(start, end).equals(reference.displayText());
    }

    private void updateItemTagButtonState() {
        if (this.itemTagButton == null) return;
        this.itemTagButton.active = getItemTagUsage() < ChatRules.MAX_ITEM_TAGS_PER_MESSAGE;
    }

    private int getItemTagUsage() {
        return itemTagReferences.size() + pendingItemRequests.size();
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
        String replacement = "@" + playerSuggestions.get(index).name() + " ";
        String newValue = value.substring(0, active.start()) + replacement + value.substring(cursor);
        int newCursor = active.start() + replacement.length();

        messageInput.setValue(newValue);
        messageInput.setCursorPosition(newCursor);
        messageInput.setHighlightPos(newCursor);
        draftCursor = newCursor;
        clearSuggestions();
    }

    private void clearSuggestions() {
        playerSuggestions = List.of();
        requestedSuggestionQuery = null;
        selectedSuggestion = 0;
    }

    private void sendMessage() {
        if (this.messageInput == null) return;

        String content = this.messageInput.getValue();
        if (content.isBlank() || content.stripLeading().startsWith("/")) return;

        ClientPacketDistributor.sendToServer(new SendChatPayload(content, List.copyOf(itemTagReferences)));
        this.messageInput.setValue("");
        this.itemTagReferences.clear();
        this.pendingItemRequests.clear();
        this.draft = "";
        this.lastInputValue = "";
        this.draftCursor = 0;

        clearSuggestions();
        updateItemTagButtonState();

        if (ClientConfig.CLOSE_CHAT_AFTER_SEND.get() && this.minecraft != null) this.minecraft.setScreen(null);
    }

    private void requestOlderHistory() {
        if (!ChatClientState.beginOlderHistoryRequest()) return;

        captureHistoryAnchor();
        ClientPacketDistributor.sendToServer(
                new RequestChatHistoryPayload(
                        ChatClientState.activeHistoryRequestId(),
                        ChatClientState.oldestPersistentId(),
                        ChatClientState.pagingHistoryLimit()
                )
        );
    }

    private void requestNewerHistory() {
        if (!ChatClientState.beginNewerHistoryRequest()) return;

        captureHistoryAnchor();
        ClientPacketDistributor.sendToServer(
                new RequestNewerChatHistoryPayload(
                        ChatClientState.activeHistoryRequestId(),
                        ChatClientState.newestPersistentId(),
                        ChatClientState.pagingHistoryLimit()
                )
        );
    }

    private ViewportAnchor captureViewportAnchor() {
        List<RenderRow> rows = buildRenderRows();
        if (rows.isEmpty()) return null;

        int offset = Math.min(scrollOffset, getMaxScrollOffset(rows));
        int start = rows.size() - 1 - offset;
        int y = this.height - MESSAGE_BOTTOM_OFFSET;

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            RenderRow row = rows.get(i);

            if (row.message()) {
                long id = persistentId(row.entry());
                if (id != Long.MIN_VALUE) return new ViewportAnchor(id, row.lineIndex());
            }

            y -= row.height();
        }

        return null;
    }

    private void restoreViewportAnchor(ViewportAnchor anchor) {
        List<RenderRow> rows = buildRenderRows();

        for (int i = 0; i < rows.size(); i++) {
            RenderRow row = rows.get(i);
            if (!row.message()) continue;
            if (persistentId(row.entry()) != anchor.messageId() || row.lineIndex() != anchor.lineIndex()) continue;

            scrollOffset = Math.max(0, Math.min(rows.size() - 1 - i, getMaxScrollOffset(rows)));
            return;
        }

        scrollOffset = Math.min(scrollOffset, getMaxScrollOffset(rows));
    }

    private void captureHistoryAnchor() {
        ViewportAnchor anchor = captureViewportAnchor();

        if (anchor == null) {
            clearHistoryAnchor();
            return;
        }

        historyAnchorId = anchor.messageId();
        historyAnchorLineIndex = anchor.lineIndex();
    }

    private void clearHistoryAnchor() {
        historyAnchorId = Long.MIN_VALUE;
        historyAnchorLineIndex = -1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        handleHistoryRequestTimeout();
        List<RenderRow> rows = buildRenderRows();
        scrollOffset = Math.min(scrollOffset, getMaxScrollOffset(rows));
        markCurrentLatestSeen();
        updateJumpButtonState();

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        renderMessages(graphics, mouseX, mouseY, rows);
        renderJumpButtonPulse(graphics);
        renderNotice(graphics);
        renderSuggestions(graphics);
    }

    private void handleHistoryRequestTimeout() {
        if (!ChatClientState.consumeHistoryRequestTimeout()) return;
        jumpingToLatest = false;
        clearHistoryAnchor();

        if (ChatClientState.beginInitialHistoryRequest()) {
            ClientPacketDistributor.sendToServer(
                    new RequestChatHistoryPayload(
                            ChatClientState.activeHistoryRequestId(),
                            Long.MAX_VALUE,
                            ChatClientState.initialHistoryLimit()
                    )
            );
        }

        updateJumpButtonState();
    }

    private void renderNotice(GuiGraphicsExtractor graphics) {
        Component notice;
        int color;

        if (System.currentTimeMillis() < newMessageNoticeUntil) {
            notice = Component.translatable("screen.njw_just_chat.new_message_notice");
            color = NEW_MESSAGE_NOTICE_COLOR;
        } else if (this.commandWarning) {
            notice = Component.translatable("screen.njw_just_chat.command_notice");
            color = COMMAND_NOTICE_COLOR;
        } else {
            notice = Component.translatable("screen.njw_just_chat.test_notice");
            color = DEFAULT_NOTICE_COLOR;
        }

        graphics.text(this.font, notice, 8, this.height - 42, color, false);
    }

    private List<RenderRow> buildRenderRows() {
        List<RenderRow> rows = new ArrayList<>();
        int messageWidth = getMessageTextWidth();
        long readBoundaryId = ChatReadClientState.readBoundaryMessageId();
        boolean showReadBoundary = ChatReadClientState.readBoundaryVisible()
                && ChatClientState.canDisplayReadBoundary(readBoundaryId);
        boolean readBoundaryInserted = false;

        for (int i = 0; i < ChatClientState.size(); i++) {
            ChatClientEntry entry = ChatClientState.get(i);
            long persistentId = persistentId(entry);

            if (showReadBoundary && !readBoundaryInserted && persistentId != Long.MIN_VALUE
                    && persistentId > readBoundaryId) {
                Component boundary = Component.literal("--- ")
                        .append(Component.translatable("screen.njw_just_chat.read_boundary"))
                        .append(" ---");
                rows.add(RenderRow.readBoundary(boundary.getVisualOrderText()));
                readBoundaryInserted = true;
            }

            if (isFirstOfDate(i)) {
                Component date = Component.literal("--- " + ChatTimeFormatter.formatDate(entry.createdAt()) + " ---");
                rows.add(RenderRow.date(date.getVisualOrderText()));
            }

            Component message = createDisplayLine(entry);
            List<FormattedCharSequence> lines = this.font.split(message, messageWidth);

            for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                rows.add(RenderRow.message(entry, lines.get(lineIndex), lineIndex));
            }
        }

        return rows;
    }

    private void renderMessages(GuiGraphicsExtractor graphics, int mouseX, int mouseY, List<RenderRow> rows) {
        if (rows.isEmpty()) return;

        ChatClientEntry hoveredEntry = findHoveredEntry(mouseX, mouseY, rows);
        int y = this.height - MESSAGE_BOTTOM_OFFSET;
        int start = rows.size() - 1 - scrollOffset;
        long now = System.currentTimeMillis();
        UUID playerUuid = getPlayerUuid();
        ActiveTextCollector textRenderer = graphics.textRenderer(
                GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR
        );

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            RenderRow row = rows.get(i);

            if (row.date() || row.readBoundary()) {
                int x = (this.width - this.font.width(row.text())) / 2;
                graphics.text(this.font, row.text(), x, y, 0xFFAAAAAA, false);
            } else {
                textRenderer.accept(MESSAGE_LEFT, y, row.text());

                if (row.firstLine() && !jumpButtonOverlapsRow(y) && sameEntry(row.entry(), hoveredEntry)
                        && canDelete(row.entry(), playerUuid, now)) {
                    renderDelete(graphics, y);
                }
            }

            y -= row.height();
        }
    }

    private ChatClientEntry findHoveredEntry(double mouseX, double mouseY, List<RenderRow> rows) {
        int y = this.height - MESSAGE_BOTTOM_OFFSET;
        int start = rows.size() - 1 - Math.min(scrollOffset, getMaxScrollOffset(rows));

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            RenderRow row = rows.get(i);
            if (row.message() && isMouseOverRow(mouseX, mouseY, y)) return row.entry();
            y -= row.height();
        }

        return null;
    }

    private boolean sameEntry(ChatClientEntry first, ChatClientEntry second) {
        if (first == null || second == null) return false;

        if (first.isPlayer() && second.isPlayer()) {
            return first.playerMessageId() == second.playerMessageId();
        }

        if (first.isSystem() && second.isSystem()) {
            return first.systemMessageId() == second.systemMessageId();
        }

        return first == second;
    }

    private long persistentId(ChatClientEntry entry) {
        if (entry == null) return Long.MIN_VALUE;
        if (entry.isPlayer()) return entry.playerMessageId();
        if (entry.isSystem()) return entry.systemMessageId();
        return Long.MIN_VALUE;
    }

    private void renderDelete(GuiGraphicsExtractor graphics, int y) {
        Component delete = Component.translatable("screen.njw_just_chat.delete").withStyle(ChatFormatting.RED);
        int deleteX = this.width - DELETE_RIGHT_MARGIN - this.font.width(delete);
        graphics.text(this.font, delete, deleteX, y, 0xFFFF5555, false);
    }

    private int getMessageTextWidth() {
        Component delete = Component.translatable("screen.njw_just_chat.delete");
        int deleteReserved = DELETE_RIGHT_MARGIN + this.font.width(delete) + 8;
        int jumpReserved = JUMP_BUTTON_RIGHT_MARGIN + JUMP_BUTTON_SIZE + 8;
        int reserved = Math.max(deleteReserved, jumpReserved);
        return Math.max(40, this.width - MESSAGE_LEFT - reserved);
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
            PlayerSuggestionsPayload.Suggestion suggestion = playerSuggestions.get(i);

            if (i == selectedSuggestion) {
                graphics.fill(left, y, left + width, y + SUGGESTION_ROW_HEIGHT, 0xA0505050);
            }

            int color = suggestion.online() ? ONLINE_SUGGESTION_COLOR : OFFLINE_SUGGESTION_COLOR;
            graphics.text(this.font, "@" + suggestion.name(), left + 4, y + 2, color, false);
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

        for (PlayerSuggestionsPayload.Suggestion suggestion : playerSuggestions) {
            width = Math.max(width, this.font.width("@" + suggestion.name()) + 8);
        }

        return Math.min(width, getInputWidth());
    }

    private ChatMessage findDeleteTarget(double mouseX, double mouseY) {
        UUID playerUuid = getPlayerUuid();
        if (playerUuid == null) return null;

        List<RenderRow> rows = buildRenderRows();
        int y = this.height - MESSAGE_BOTTOM_OFFSET;
        int start = rows.size() - 1 - Math.min(scrollOffset, getMaxScrollOffset(rows));
        long now = System.currentTimeMillis();

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            RenderRow row = rows.get(i);

            if (row.message() && row.firstLine() && !jumpButtonOverlapsRow(y)
                    && canDelete(row.entry(), playerUuid, now) && isMouseOverDelete(mouseX, mouseY, y)) {
                return row.entry().chatMessage();
            }

            y -= row.height();
        }

        return null;
    }

    private Component createDisplayLine(ChatClientEntry entry) {
        String time = ChatTimeFormatter.formatTime(entry.createdAt());

        if (entry.isPlayer() && entry.chatMessage().deleted()) {
            return Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY).append(entry.displayMessage());
        }

        return Component.literal("[" + time + "] ").append(entry.displayMessage());
    }

    private boolean canDelete(ChatClientEntry entry, UUID playerUuid, long now) {
        return playerUuid != null && entry != null && entry.isPlayer()
                && entry.chatMessage().canDelete(playerUuid, now);
    }

    private boolean isMouseOverRow(double mouseX, double mouseY, int y) {
        return mouseX >= MESSAGE_LEFT && mouseX < this.width - DELETE_RIGHT_MARGIN
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

    private int getItemButtonX() {
        return this.width - INPUT_SIDE_MARGIN - ITEM_BUTTON_WIDTH;
    }

    private int getInputWidth() {
        return getItemButtonX() - INPUT_GAP - INPUT_SIDE_MARGIN;
    }

    private int getJumpButtonX() {
        return this.width - JUMP_BUTTON_RIGHT_MARGIN - JUMP_BUTTON_SIZE;
    }

    private int getJumpButtonY() {
        return this.height - MESSAGE_BOTTOM_OFFSET - JUMP_BUTTON_SIZE - JUMP_BUTTON_GAP;
    }

    private int getMaxScrollOffset(List<RenderRow> rows) {
        if (rows.isEmpty()) return 0;

        int availableHeight = Math.max(
                MESSAGE_LINE_HEIGHT,
                this.height - MESSAGE_BOTTOM_OFFSET - MESSAGE_TOP + MESSAGE_LINE_HEIGHT
        );

        int usedHeight = 0;
        int visibleRows = 0;

        for (RenderRow row : rows) {
            if (usedHeight + row.height() > availableHeight) break;
            usedHeight += row.height();
            visibleRows++;
        }

        return Math.max(0, rows.size() - Math.max(1, visibleRows));
    }

    private boolean isFirstOfDate(int index) {
        if (index == 0) return !ChatClientState.hasOlderHistory();
        return !ChatTimeFormatter.isSameDate(
                ChatClientState.get(index).createdAt(), ChatClientState.get(index - 1).createdAt()
        );
    }

    private record TagQuery(int start, String query) {}

    private record ViewportAnchor(long messageId, int lineIndex) {}

    private record RenderRow(ChatClientEntry entry, FormattedCharSequence text, RowType type, int lineIndex) {
        private static RenderRow message(ChatClientEntry entry, FormattedCharSequence text, int lineIndex) {
            return new RenderRow(entry, text, RowType.MESSAGE, lineIndex);
        }

        private static RenderRow date(FormattedCharSequence text) {
            return new RenderRow(null, text, RowType.DATE, -1);
        }

        private static RenderRow readBoundary(FormattedCharSequence text) {
            return new RenderRow(null, text, RowType.READ_BOUNDARY, -1);
        }

        private boolean message() {
            return type == RowType.MESSAGE;
        }

        private boolean date() {
            return type == RowType.DATE;
        }

        private boolean readBoundary() {
            return type == RowType.READ_BOUNDARY;
        }

        private boolean firstLine() {
            return message() && lineIndex == 0;
        }

        private int height() {
            if (date()) return DATE_LINE_HEIGHT;
            if (readBoundary()) return READ_BOUNDARY_LINE_HEIGHT;
            return MESSAGE_LINE_HEIGHT;
        }
    }

    private enum RowType {
        MESSAGE,
        DATE,
        READ_BOUNDARY
    }
}
