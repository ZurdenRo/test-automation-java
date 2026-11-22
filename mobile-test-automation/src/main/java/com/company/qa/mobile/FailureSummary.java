package com.company.qa.mobile;

import java.util.List;

/**
 * Says why a test failed in a few lines: each exception of the chain of causes with its message, cut where Selenium
 * starts its boilerplate (documentation link, build and system info), and the first frame of the consumer's code.
 */
final class FailureSummary {

    private static final int MAX_CAUSES = 10;
    private static final List<String> BOILERPLATE = List.of("\nFor documentation", "\nBuild info:", "\nSystem info:");
    private static final List<String> NOT_CONSUMER_CODE = List.of("com.company.qa.mobile.", "org.openqa.",
            "io.appium.", "org.testng.", "java.", "jdk.", "sun.");

    private FailureSummary() {
    }

    static String of(Throwable failure) {
        StringBuilder summary = new StringBuilder();
        Throwable current = failure;
        for (int n = 0; current != null && n < MAX_CAUSES; n++, current = current.getCause()) {
            summary.append(n == 0 ? "" : "  caused by ").append(current.getClass().getSimpleName());
            String message = shortMessage(current.getMessage());
            if (!message.isEmpty()) {
                summary.append(": ").append(message);
            }
            summary.append('\n');
        }
        consumerFrame(failure).ifPresent(frame -> summary.append("  at ").append(frame).append('\n'));
        return summary.toString();
    }

    private static String shortMessage(String message) {
        if (message == null) {
            return "";
        }
        String cut = message;
        for (String marker : BOILERPLATE) {
            int at = cut.indexOf(marker);
            if (at >= 0) {
                cut = cut.substring(0, at);
            }
        }
        return cut.strip();
    }

    private static java.util.Optional<StackTraceElement> consumerFrame(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            for (StackTraceElement frame : t.getStackTrace()) {
                if (NOT_CONSUMER_CODE.stream().noneMatch(frame.getClassName()::startsWith)) {
                    return java.util.Optional.of(frame);
                }
            }
        }
        return java.util.Optional.empty();
    }
}
