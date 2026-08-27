package njw.net.justchat.client;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ChatTimeFormatter {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy. MM. dd");

    private ChatTimeFormatter() {}

    public static String formatTime(long createdAt) {
        return Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    public static String formatDate(long createdAt) {
        return Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).format(DATE_FORMAT);
    }

    public static boolean isSameDate(long first, long second) {
        return toDate(first).equals(toDate(second));
    }

    private static LocalDate toDate(long createdAt) {
        return Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}