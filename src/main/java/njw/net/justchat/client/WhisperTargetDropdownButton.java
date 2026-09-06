package njw.net.justchat.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import njw.net.justchat.network.RequestWhisperTargetsPayload;
import njw.net.justchat.network.WhisperTargetSelection;
import njw.net.justchat.network.WhisperTargetsPayload;

import java.util.List;

public final class WhisperTargetDropdownButton extends AbstractWidget {
    private static final int ROW_HEIGHT = 20;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int BACKGROUND = 0xE0101010;
    private static final int BUTTON = 0xC84A4A4A;
    private static final int BUTTON_HOVERED = 0xCC666666;
    private static final int ROW_HOVERED = 0xCC505050;
    private static final int TEXT = 0xFFFFFFFF;

    private List<WhisperTargetsPayload.Target> targets = List.of();
    private boolean open;
    private int scrollOffset;

    public WhisperTargetDropdownButton(int x, int y, int width, int height) {
        super(x, y, width, height, currentLabel());
    }

    public void updateTargets(List<WhisperTargetsPayload.Target> targets) {
        this.targets = List.copyOf(targets);
        String selectedUuid = WhisperTargetSelection.targetUuid();
        if (!selectedUuid.isBlank() && this.targets.stream().noneMatch(target -> target.uuid().equals(selectedUuid))) {
            WhisperTargetSelection.clear();
        }
        scrollOffset = Math.min(scrollOffset, maxScrollOffset());
        setMessage(currentLabel());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return false;
        double mouseX = event.x();
        double mouseY = event.y();

        if (insideButton(mouseX, mouseY)) {
            open = !open;
            if (open) {
                WhisperTargetScreenExtension.setActiveDropdown(this);
                ClientPacketDistributor.sendToServer(new RequestWhisperTargetsPayload());
            }
            return true;
        }

        if (!open) return false;
        int absoluteIndex = dropdownIndexAt(mouseX, mouseY);
        if (absoluteIndex >= 0) {
            if (absoluteIndex == 0) WhisperTargetSelection.clear();
            else {
                WhisperTargetsPayload.Target target = targets.get(absoluteIndex - 1);
                WhisperTargetSelection.select(target.uuid(), target.name());
            }
            setMessage(currentLabel());
            open = false;
            return true;
        }

        open = false;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!open || scrollY == 0.0 || !insideDropdown(mouseX, mouseY)) return false;
        int max = maxScrollOffset();
        if (scrollY > 0.0) scrollOffset = Math.max(0, scrollOffset - 1);
        else scrollOffset = Math.min(max, scrollOffset + 1);
        return true;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hovered = insideButton(mouseX, mouseY);
        graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? BUTTON_HOVERED : BUTTON);
        drawCentered(graphics, getMessage(), getY() + (getHeight() - Minecraft.getInstance().font.lineHeight) / 2);
    }

    public void renderDropdownOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!open) return;
        int visibleRows = visibleRows();
        int top = dropdownTop();
        graphics.fill(getX(), top, getX() + getWidth(), getY(), BACKGROUND);

        for (int row = 0; row < visibleRows; row++) {
            int absoluteIndex = scrollOffset + row;
            int rowY = top + row * ROW_HEIGHT;
            if (mouseX >= getX() && mouseX < getX() + getWidth() && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                graphics.fill(getX(), rowY, getX() + getWidth(), rowY + ROW_HEIGHT, ROW_HOVERED);
            }
            Component label = absoluteIndex == 0
                    ? Component.translatable("screen.njw_just_chat.target_all")
                    : Component.literal(targets.get(absoluteIndex - 1).name());
            drawCentered(graphics, label, rowY + (ROW_HEIGHT - Minecraft.getInstance().font.lineHeight) / 2);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}

    private void drawCentered(GuiGraphicsExtractor graphics, Component text, int y) {
        int textWidth = Minecraft.getInstance().font.width(text);
        int x = getX() + Math.max(2, (getWidth() - textWidth) / 2);
        graphics.text(Minecraft.getInstance().font, text, x, y, TEXT, false);
    }

    private boolean insideButton(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight();
    }

    private boolean insideDropdown(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= dropdownTop() && mouseY < getY();
    }

    private int dropdownIndexAt(double mouseX, double mouseY) {
        if (!insideDropdown(mouseX, mouseY)) return -1;
        int row = (int) ((mouseY - dropdownTop()) / ROW_HEIGHT);
        int index = scrollOffset + row;
        return index >= 0 && index < totalRows() ? index : -1;
    }

    private int totalRows() {
        return targets.size() + 1;
    }

    private int visibleRows() {
        return Math.min(MAX_VISIBLE_ROWS, totalRows());
    }

    private int maxScrollOffset() {
        return Math.max(0, totalRows() - visibleRows());
    }

    private int dropdownTop() {
        return getY() - visibleRows() * ROW_HEIGHT;
    }

    private static Component currentLabel() {
        String targetName = WhisperTargetSelection.targetName();
        return targetName.isBlank()
                ? Component.translatable("screen.njw_just_chat.target_all")
                : Component.literal(targetName);
    }
}
