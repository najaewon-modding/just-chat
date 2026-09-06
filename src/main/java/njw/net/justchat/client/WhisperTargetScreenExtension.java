package njw.net.justchat.client;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import njw.net.justchat.mixin.CustomChatScreenAccessor;
import njw.net.justchat.network.WhisperTargetSelection;
import njw.net.justchat.network.WhisperTargetsPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class WhisperTargetScreenExtension {
    private static final int TARGET_WIDTH = 64;
    private static final int GAP = 4;
    private static final int MESSAGE_LINE_HEIGHT = 12;
    private static final int DATE_LINE_HEIGHT = 14;
    private static final int READ_BOUNDARY_LINE_HEIGHT = 14;
    private static final int MESSAGE_TOP = 36;
    private static final int MESSAGE_BOTTOM_OFFSET = 58;
    private static final int MESSAGE_LEFT = 8;
    private static final int DELETE_RIGHT_MARGIN = 8;
    private static final int JUMP_BUTTON_SIZE = 20;
    private static final int JUMP_BUTTON_RIGHT_MARGIN = 8;
    private static CustomChatScreen activeScreen;
    private static WhisperTargetDropdownButton activeDropdown;

    private WhisperTargetScreenExtension() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CustomChatScreen screen)) return;
        if (activeScreen != screen) WhisperTargetSelection.clear();
        activeScreen = screen;

        EditBox input = ((CustomChatScreenAccessor) (Object) screen).njwJustChat$messageInput();
        if (input == null) return;
        int targetWidth = Math.min(TARGET_WIDTH, Math.max(64, input.getWidth() / 3));
        int remainingWidth = input.getWidth() - targetWidth - GAP;
        if (remainingWidth < 40) return;

        WhisperTargetDropdownButton dropdown = new WhisperTargetDropdownButton(
                input.getX(), input.getY(), targetWidth, input.getHeight());
        input.setX(input.getX() + targetWidth + GAP);
        input.setWidth(remainingWidth);
        activeDropdown = dropdown;
        event.addListener(dropdown);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() != activeScreen) return;
        if (activeDropdown != null) {
            activeDropdown.renderDropdownOverlay(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
        }
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        if (isDropdownCovering(mouseX, mouseY) || ChatFilterScreenExtension.isDropdownCovering(mouseX, mouseY)) return;
        requestPlayerNameCursor(activeScreen, event.getGuiGraphics(), mouseX, mouseY);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() != activeScreen) return;
        MouseButtonEvent mouse = event.getMouseButtonEvent();
        if (activeDropdown != null && activeDropdown.mouseClicked(mouse, event.isDoubleClick())) {
            event.setCanceled(true);
            return;
        }
        if (mouse.button() != 0 || activeScreen == null) return;

        ChatClientEntry reply = findReplyTarget(activeScreen, mouse.x(), mouse.y());
        if (reply == null) return;
        var sender = reply.persistentEntry().sender();
        WhisperTargetSelection.select(sender.uuid().toString(), sender.name());
        if (activeDropdown != null) activeDropdown.refreshSelection();
        EditBox input = ((CustomChatScreenAccessor) (Object) activeScreen).njwJustChat$messageInput();
        if (input != null) input.setFocused(true);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScreen() != activeScreen || activeDropdown == null) return;
        if (activeDropdown.mouseScrolled(event.getMouseX(), event.getMouseY(),
                event.getScrollDeltaX(), event.getScrollDeltaY())) event.setCanceled(true);
    }

    public static void setActiveDropdown(WhisperTargetDropdownButton dropdown) {
        activeDropdown = dropdown;
    }

    public static void updateTargets(List<WhisperTargetsPayload.Target> targets) {
        if (activeDropdown != null) activeDropdown.updateTargets(targets);
    }

    public static boolean isDropdownCovering(double mouseX, double mouseY) {
        return activeDropdown != null && activeDropdown.covers(mouseX, mouseY);
    }

    public static void clear() {
        activeScreen = null;
        activeDropdown = null;
        WhisperTargetSelection.clear();
    }

    private static void requestPlayerNameCursor(CustomChatScreen screen, GuiGraphicsExtractor graphics,
                                                double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        UUID viewerUuid = minecraft.player.getUUID();
        List<ReplyRow> rows = buildRows(screen, minecraft.font);
        if (rows.isEmpty()) return;
        int scrollOffset = ((CustomChatScreenAccessor) (Object) screen).njwJustChat$scrollOffset();
        int start = rows.size() - 1 - Math.min(scrollOffset, getMaxScrollOffset(screen, rows));
        int y = screen.height - MESSAGE_BOTTOM_OFFSET;

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            ReplyRow row = rows.get(i);
            if (row.entry() != null && row.lineIndex() == 0 && isMouseOverRow(screen, minecraft.font, mouseX, mouseY, y)) {
                if (isMouseOverPlayerName(row.entry(), viewerUuid, minecraft.font, mouseX, y)) {
                    graphics.requestCursor(CursorTypes.POINTING_HAND);
                }
                return;
            }
            y -= row.height();
        }
    }

    private static boolean isMouseOverPlayerName(ChatClientEntry entry, UUID viewerUuid, Font font,
                                                 double mouseX, int y) {
        if (!entry.isPlayer() || !entry.isPersistent()) return false;
        var persistent = entry.persistentEntry();
        if (viewerUuid.equals(persistent.sender().uuid())) return false;

        String senderName = persistent.sender().name();
        if (senderName.isEmpty()) return false;
        String timePrefix = "[" + ChatTimeFormatter.formatTime(entry.createdAt()) + "] ";
        int nameX;

        if (!persistent.isWhisper()) {
            nameX = MESSAGE_LEFT + font.width(timePrefix + "<");
        } else {
            String whisperPrefix = Component.translatable(
                    "screen.njw_just_chat.whisper_from", senderName).getString();
            int nameIndex = whisperPrefix.indexOf(senderName);
            if (nameIndex < 0) return false;
            nameX = MESSAGE_LEFT + font.width(timePrefix) + font.width(whisperPrefix.substring(0, nameIndex));
        }

        int nameWidth = font.width(senderName);
        return mouseX >= nameX && mouseX < nameX + nameWidth
                && y >= MESSAGE_TOP;
    }

    private static ChatClientEntry findReplyTarget(CustomChatScreen screen, double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return null;
        UUID viewerUuid = minecraft.player.getUUID();
        List<ReplyRow> rows = buildRows(screen, minecraft.font);
        if (rows.isEmpty()) return null;
        int scrollOffset = ((CustomChatScreenAccessor) (Object) screen).njwJustChat$scrollOffset();
        int start = rows.size() - 1 - Math.min(scrollOffset, getMaxScrollOffset(screen, rows));
        int y = screen.height - MESSAGE_BOTTOM_OFFSET;

        for (int i = start; i >= 0 && y >= MESSAGE_TOP; i--) {
            ReplyRow row = rows.get(i);
            if (row.entry() != null && isMouseOverRow(screen, minecraft.font, mouseX, mouseY, y)) {
                ChatClientEntry entry = row.entry();
                if (entry.isPlayer() && entry.persistentEntry().isWhisper()
                        && !viewerUuid.equals(entry.persistentEntry().sender().uuid())) return entry;
                return null;
            }
            y -= row.height();
        }
        return null;
    }

    private static List<ReplyRow> buildRows(CustomChatScreen screen, Font font) {
        List<ReplyRow> rows = new ArrayList<>();
        int messageWidth = getMessageTextWidth(screen, font);
        long readBoundaryId = ChatReadClientState.readBoundaryMessageId();
        boolean showReadBoundary = ChatReadClientState.readBoundaryVisible()
                && ChatClientState.canDisplayReadBoundary(readBoundaryId);
        boolean readBoundaryInserted = false;

        for (int i = 0; i < ChatClientState.size(); i++) {
            ChatClientEntry entry = ChatClientState.get(i);
            long persistentId = entry.isPersistent() ? entry.persistentMessageId() : Long.MIN_VALUE;
            if (showReadBoundary && !readBoundaryInserted && persistentId != Long.MIN_VALUE
                    && persistentId > readBoundaryId) {
                rows.add(new ReplyRow(null, READ_BOUNDARY_LINE_HEIGHT, -1));
                readBoundaryInserted = true;
            }
            if (isFirstOfDate(i)) rows.add(new ReplyRow(null, DATE_LINE_HEIGHT, -1));

            String time = ChatTimeFormatter.formatTime(entry.createdAt());
            Component line = Component.literal("[" + time + "] ").append(entry.displayMessage());
            int lineCount = Math.max(1, font.split(line, messageWidth).size());
            for (int lineIndex = 0; lineIndex < lineCount; lineIndex++) {
                rows.add(new ReplyRow(entry, MESSAGE_LINE_HEIGHT, lineIndex));
            }
        }
        return rows;
    }

    private static int getMessageTextWidth(CustomChatScreen screen, Font font) {
        Component delete = Component.translatable("screen.njw_just_chat.delete");
        int deleteReserved = DELETE_RIGHT_MARGIN + font.width(delete) + 8;
        int jumpReserved = JUMP_BUTTON_RIGHT_MARGIN + JUMP_BUTTON_SIZE + 8;
        int reserved = Math.max(deleteReserved, jumpReserved);
        return Math.max(40, screen.width - MESSAGE_LEFT - reserved);
    }

    private static int getMaxScrollOffset(CustomChatScreen screen, List<ReplyRow> rows) {
        if (rows.isEmpty()) return 0;
        int availableHeight = Math.max(MESSAGE_LINE_HEIGHT,
                screen.height - MESSAGE_BOTTOM_OFFSET - MESSAGE_TOP + MESSAGE_LINE_HEIGHT);
        int usedHeight = 0;
        int visibleRows = 0;
        for (ReplyRow row : rows) {
            if (usedHeight + row.height() > availableHeight) break;
            usedHeight += row.height();
            visibleRows++;
        }
        return Math.max(0, rows.size() - Math.max(1, visibleRows));
    }

    private static boolean isMouseOverRow(CustomChatScreen screen, Font font,
                                          double mouseX, double mouseY, int y) {
        return mouseX >= MESSAGE_LEFT && mouseX < screen.width - DELETE_RIGHT_MARGIN
                && mouseY >= y && mouseY < y + font.lineHeight;
    }

    private static boolean isFirstOfDate(int index) {
        if (index == 0) return !ChatClientState.hasOlderHistory();
        return !ChatTimeFormatter.isSameDate(
                ChatClientState.get(index).createdAt(), ChatClientState.get(index - 1).createdAt());
    }

    private record ReplyRow(ChatClientEntry entry, int height, int lineIndex) {}
}
