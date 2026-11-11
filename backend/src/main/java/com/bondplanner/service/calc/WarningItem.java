package com.bondplanner.service.calc;

public record WarningItem(String code, String severity, String message) {

    public static WarningItem warn(String code, String message) {
        return new WarningItem(code, "WARN", message);
    }

    public static WarningItem info(String code, String message) {
        return new WarningItem(code, "INFO", message);
    }

    public static WarningItem error(String code, String message) {
        return new WarningItem(code, "ERROR", message);
    }
}
