package njw.net.justchat.network;

public final class WhisperTargetSelection {
    private static String targetUuid = "";
    private static String targetName = "";

    private WhisperTargetSelection() {}

    public static String targetUuid() {
        return targetUuid;
    }

    public static String targetName() {
        return targetName;
    }

    public static void select(String uuid, String name) {
        targetUuid = uuid == null ? "" : uuid;
        targetName = name == null ? "" : name;
    }

    public static void clear() {
        targetUuid = "";
        targetName = "";
    }
}
