package com.company.qa.mobile;

import org.openqa.selenium.logging.LogEntry;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks from the device log (logcat, in {@code threadtime} format) what explains a failure: what the Appium server
 * logged (tag {@code appium}: each command and its outcome) and crashes of the app under test (errors of
 * {@code AndroidRuntime}). Everything else on a device is noise.
 */
final class DeviceLog {

    private static final Pattern LINE = Pattern.compile("^\\S+\\s+\\S+\\s+\\d+\\s+\\d+\\s+([VDIWEF])\\s+(.*?)\\s*:");

    private DeviceLog() {
    }

    /** The last {@code max} lines worth keeping, oldest first; fewer if the device logged fewer. */
    static List<String> lastLines(Iterable<LogEntry> entries, int max) {
        Deque<String> last = new ArrayDeque<>();
        for (LogEntry entry : entries) {
            String line = entry.getMessage();
            if (line != null && worthKeeping(line)) {
                if (last.size() == max) {
                    last.removeFirst();
                }
                last.addLast(line);
            }
        }
        return List.copyOf(last);
    }

    static boolean worthKeeping(String line) {
        Matcher m = LINE.matcher(line);
        if (!m.find()) {
            return false;
        }
        String tag = m.group(2);
        return tag.equals("appium") || (m.group(1).equals("E") && tag.equals("AndroidRuntime"));
    }
}
