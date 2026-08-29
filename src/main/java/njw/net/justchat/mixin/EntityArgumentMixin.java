package njw.net.justchat.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import njw.net.justchat.server.GlobalSystemMessageRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mixin(EntityArgument.class)
public abstract class EntityArgumentMixin {
    @Inject(method = "getPlayers", at = @At("RETURN"))
    private static void njwJustChat$captureGlobalTargets(
            CommandContext<CommandSourceStack> context,
            String name,
            CallbackInfoReturnable<Collection<ServerPlayer>> cir
    ) {
        if (!name.equals("targets")) return;

        String input = context.getInput();

        if (!isTellraw(input)) return;

        EntitySelector selector = context.getArgument(name, EntitySelector.class);

        if (!selector.usesSelector()) return;
        if (selector.isSelfSelector()) return;
        if (selector.getMaxResults() <= 1) return;

        Collection<ServerPlayer> targets = cir.getReturnValue();

        if (targets.isEmpty()) return;

        MinecraftServer server = context.getSource().getServer();

        if (!isAllOnlinePlayers(server, targets)) return;

        GlobalSystemMessageRouter.beginSelectorGlobal(server, targets);
    }

    private static boolean isTellraw(String input) {
        return input.startsWith("tellraw ")
                || input.startsWith("minecraft:tellraw ")
                || input.contains(" run tellraw ")
                || input.contains(" run minecraft:tellraw ");
    }

    private static boolean isAllOnlinePlayers(
            MinecraftServer server,
            Collection<ServerPlayer> targets
    ) {
        Set<UUID> expected = new HashSet<>();
        Set<UUID> actual = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            expected.add(player.getUUID());
        }

        for (ServerPlayer player : targets) {
            actual.add(player.getUUID());
        }

        return !expected.isEmpty() && expected.equals(actual);
    }
}