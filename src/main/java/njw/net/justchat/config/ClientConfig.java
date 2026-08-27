package njw.net.justchat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CLOSE_CHAT_AFTER_SEND =
            BUILDER.define("closeChatAfterSend", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}
}