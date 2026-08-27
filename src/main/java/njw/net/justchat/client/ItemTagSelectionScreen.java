package njw.net.justchat.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

public final class ItemTagSelectionScreen extends InventoryScreen {
    private final CustomChatScreen parent;
    private final Player player;

    public ItemTagSelectionScreen(CustomChatScreen parent, Player player) {
        super(player);
        this.parent = parent;
        this.player = player;
    }

    @Override
    protected void slotClicked(
            Slot slot,
            int slotId,
            int buttonNum,
            ContainerInput containerInput
    ) {
        if (slot == null || !slot.hasItem()) return;
        if (slot.container != player.getInventory()) return;
        if (this.minecraft == null) return;

        int inventorySlot = slot.getContainerSlot();
        this.minecraft.setScreen(parent);
        parent.requestItemTag(inventorySlot);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(
                this.font,
                Component.translatable("screen.njw_just_chat.select_item"),
                this.width / 2,
                8,
                0xFFFFFFFF
        );
    }
}