package njw.net.justchat.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import njw.net.justchat.data.ChatFilter;
import njw.net.justchat.mixin.CustomChatScreenAccessor;
import njw.net.justchat.network.ChatFilterSelection;
import njw.net.justchat.network.RequestChatHistoryPayload;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class ChatFilterScreenExtension {
    private static final int FILTER_X = 8;
    private static final int FILTER_Y = 8;
    private static final int FILTER_WIDTH = 64;
    private static final int FILTER_HEIGHT = 20;
    private static CustomChatScreen activeScreen;
    private static ChatFilterDropdownButton activeDropdown;

    private ChatFilterScreenExtension() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CustomChatScreen screen)) return;
        boolean newlyOpened = activeScreen != screen;
        activeScreen = screen;
        if (newlyOpened && ChatFilterSelection.current() != ChatFilter.ALL) {
            ChatFilterSelection.clear();
            ChatClientState.resetForFilter();
            resetScreenView();
            if (ChatClientState.beginInitialHistoryRequest()) {
                ClientPacketDistributor.sendToServer(new RequestChatHistoryPayload(
                        ChatClientState.activeHistoryRequestId(), Long.MAX_VALUE,
                        ChatClientState.initialHistoryLimit(), ChatFilter.ALL));
            }
        }
        activeDropdown = new ChatFilterDropdownButton(
                FILTER_X, FILTER_Y, FILTER_WIDTH, FILTER_HEIGHT, ChatFilterScreenExtension::selectFilter);
        event.addListener(activeDropdown);
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() != activeScreen || activeDropdown == null) return;
        activeDropdown.renderDropdownOverlay(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() != activeScreen || activeDropdown == null) return;
        if (activeDropdown.mouseClicked(event.getMouseButtonEvent(), event.isDoubleClick())) event.setCanceled(true);
    }

    public static void clear() {
        activeScreen = null;
        activeDropdown = null;
    }

    private static void selectFilter(ChatFilter filter) {
        if (!ChatFilterSelection.select(filter)) return;
        ChatClientState.resetForFilter();
        resetScreenView();
        if (!ChatClientState.beginInitialHistoryRequest()) return;
        ClientPacketDistributor.sendToServer(new RequestChatHistoryPayload(
                ChatClientState.activeHistoryRequestId(), Long.MAX_VALUE, ChatClientState.initialHistoryLimit(), filter));
    }

    private static void resetScreenView() {
        if (activeScreen == null) return;
        CustomChatScreenAccessor accessor = (CustomChatScreenAccessor) (Object) activeScreen;
        accessor.njwJustChat$setScrollOffset(0);
        accessor.njwJustChat$setHistoryAnchorId(Long.MIN_VALUE);
        accessor.njwJustChat$setHistoryAnchorLineIndex(-1);
        accessor.njwJustChat$setJumpingToLatest(false);
        accessor.njwJustChat$setHasUnseenLiveMessages(false);
        accessor.njwJustChat$setNewMessageNoticeUntil(0L);
    }
}
