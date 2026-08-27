package njw.net.justchat;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import njw.net.justchat.config.ClientConfig;

@Mod(JustChat.MOD_ID)
public final class JustChat {
    public static final String MOD_ID = "njw_just_chat";

    public JustChat(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}