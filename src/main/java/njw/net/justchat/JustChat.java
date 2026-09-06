package njw.net.justchat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import njw.net.justchat.config.ClientConfig;

@Mod(JustChat.MOD_ID)
public final class JustChat {
    public static final String MOD_ID = "njw_just_chat";

    public JustChat(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(JustChat::onConfigLoading);
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != ClientConfig.SPEC) return;
        ClientConfig.migrateLegacyDefaults();
    }
}
