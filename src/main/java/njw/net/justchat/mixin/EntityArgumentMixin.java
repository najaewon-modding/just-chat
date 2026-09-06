package njw.net.justchat.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import njw.net.justchat.server.SystemMessageCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

@Mixin(EntityArgument.class)
public abstract class EntityArgumentMixin {
    @Inject(method = "getPlayers", at = @At("RETURN"))
    private static void njwJustChat$captureTellrawTargets(CommandContext<CommandSourceStack> context, String name,
                                                           CallbackInfoReturnable<Collection<ServerPlayer>> cir) {
        if (!name.equals("targets")) return;
        boolean tellraw = context.getNodes().stream().anyMatch(node -> {
            String nodeName = node.getNode().getName();
            return nodeName.equals("tellraw") || nodeName.equals("minecraft:tellraw");
        });
        if (!tellraw) return;
        SystemMessageCapture.beginTellraw(context.getSource(), cir.getReturnValue(), isExplicitAllPlayers(context, name));
    }

    private static boolean isExplicitAllPlayers(CommandContext<CommandSourceStack> context, String name) {
        String input = context.getInput();
        return context.getNodes().stream()
                .filter(node -> node.getNode().getName().equals(name))
                .map(node -> node.getRange())
                .anyMatch(range -> range.getStart() >= 0 && range.getEnd() <= input.length()
                        && input.substring(range.getStart(), range.getEnd()).equals("@a"));
    }
}
