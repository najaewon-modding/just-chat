package njw.net.justchat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    private static final int CURRENT_CONFIG_VERSION = 1;
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CLOSE_CHAT_AFTER_SEND = BUILDER
            .translation("config.njw_just_chat.close_chat_after_send")
            .define("closeChatAfterSend", false);

    private static final ModConfigSpec.IntValue CONFIG_VERSION = BUILDER
            .comment("Internal config migration version.")
            .defineInRange("configVersion", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    public static void migrateLegacyDefaults() {
        if (CONFIG_VERSION.get() >= CURRENT_CONFIG_VERSION) return;
        if (CLOSE_CHAT_AFTER_SEND.get()) CLOSE_CHAT_AFTER_SEND.set(false);
        CONFIG_VERSION.set(CURRENT_CONFIG_VERSION);
        SPEC.save();
    }
}
