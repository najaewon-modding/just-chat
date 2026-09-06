package njw.net.justchat.client;

import net.minecraft.client.gui.components.EditBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import njw.net.justchat.mixin.CustomChatScreenAccessor;
import njw.net.justchat.network.WhisperTargetSelection;
import njw.net.justchat.network.WhisperTargetsPayload;

import java.util.List;

@EventBusSubscriber(modid = "njw_just_chat", value = Dist.CLIENT)
public final class WhisperTargetScreenExtension {
    private static final int TARGET_WIDTH = 64;
    private static final int GAP = 4;
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
        if (event.getScreen() != activeScreen || activeDropdown == null) return;
        activeDropdown.renderDropdownOverlay(event.getGuiGraphics(), event.getMouseX(), event.getMouseY());
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getScreen() != activeScreen || activeDropdown == null) return;
        if (activeDropdown.mouseClicked(event.getMouseButtonEvent(), event.isDoubleClick())) event.setCanceled(true);
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

    public static void clear() {
        activeScreen = null;
        activeDropdown = null;
        WhisperTargetSelection.clear();
    }
}
