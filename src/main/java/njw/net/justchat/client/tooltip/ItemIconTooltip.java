package njw.net.justchat.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

public record ItemIconTooltip(ItemStack stack) implements TooltipComponent {}