package njw.net.justchat.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

public final class ClientItemIconTooltip implements ClientTooltipComponent {
    private final ItemStack stack;

    public ClientItemIconTooltip(ItemIconTooltip tooltip) {
        this.stack = tooltip.stack().copy();
    }

    @Override
    public int getHeight(Font font) {
        return 18;
    }

    @Override
    public int getWidth(Font font) {
        return 18;
    }

    @Override
    public void extractImage(
            Font font,
            int x,
            int y,
            int width,
            int height,
            GuiGraphicsExtractor graphics
    ) {
        graphics.item(stack, x, y);
        graphics.itemDecorations(font, stack, x, y);
    }
}