package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import njw.net.justchat.data.ChatFilter;
import njw.net.justchat.network.ChatFilterSelection;

import java.util.function.Consumer;

public final class ChatFilterDropdownButton extends AbstractWidget {
    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON = 0xC84A4A4A;
    private static final int BUTTON_HOVERED = 0xCC666666;
    private static final int TEXT = 0xFFFFFFFF;
    private final Consumer<ChatFilter> onSelect;
    private boolean open;

    public ChatFilterDropdownButton(int x, int y, int width, int height, Consumer<ChatFilter> onSelect) {
        super(x, y, width, height, label(ChatFilterSelection.current()));
        this.onSelect = onSelect;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        if (insideButton(mouseX, mouseY)) {
            open = !open;
            return true;
        }
        if (!open) return false;
        int index = dropdownIndexAt(mouseX, mouseY);
        if (index >= 0) {
            ChatFilter filter = ChatFilter.values()[index];
            onSelect.accept(filter);
            setMessage(label(ChatFilterSelection.current()));
        }
        open = false;
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = insideButton(mouseX, mouseY);
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? BUTTON_HOVERED : BUTTON);
        drawCentered(graphics, getMessage(), getY(), getHeight());
    }

    public void renderDropdownOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!open) return;
        ChatFilter[] filters = ChatFilter.values();
        int top = getY() + getHeight();
        for (int i = 0; i < filters.length; i++) {
            int rowY = top + i * ROW_HEIGHT;
            boolean hovered = mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            graphics.fill(getX(), rowY, getX() + getWidth(), rowY + ROW_HEIGHT, hovered ? BUTTON_HOVERED : BUTTON);
            drawCentered(graphics, label(filters[i]), rowY, ROW_HEIGHT);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    private void drawCentered(GuiGraphicsExtractor graphics, Component text, int top, int height) {
        var font = Minecraft.getInstance().font;
        int x = getX() + Math.max(2, (getWidth() - font.width(text)) / 2);
        int y = top + (height - font.lineHeight) / 2;
        graphics.text(font, text, x, y, TEXT, false);
    }

    private boolean insideButton(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    private boolean insideDropdown(double mouseX, double mouseY) {
        int top = getY() + getHeight();
        int bottom = top + ChatFilter.values().length * ROW_HEIGHT;
        return mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= top && mouseY < bottom;
    }

    private int dropdownIndexAt(double mouseX, double mouseY) {
        if (!insideDropdown(mouseX, mouseY)) return -1;
        return (int) ((mouseY - (getY() + getHeight())) / ROW_HEIGHT);
    }

    private static Component label(ChatFilter filter) {
        return Component.translatable(switch (filter) {
            case ALL -> "screen.njw_just_chat.filter_all";
            case GLOBAL -> "screen.njw_just_chat.filter_global";
            case DIRECT -> "screen.njw_just_chat.filter_direct";
            case WHISPER -> "screen.njw_just_chat.filter_whisper";
        });
    }
}
