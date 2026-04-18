package com.example.trelloclaudebot.dto.internal;

/**
 * Ergebnis eines einzelnen Datei-Schreibvorgangs durch die ApplyEngine.
 */
public class ApplyResult {

    public enum Status { WRITTEN, SKIPPED, ERROR }

    private final String filePath;
    private final Status status;
    private final String message;

    private ApplyResult(String filePath, Status status, String message) {
        this.filePath = filePath;
        this.status   = status;
        this.message  = message;
    }

    public static ApplyResult written(String filePath) {
        return new ApplyResult(filePath, Status.WRITTEN, "Datei geschrieben");
    }

    public static ApplyResult skipped(String filePath, String reason) {
        return new ApplyResult(filePath, Status.SKIPPED, reason);
    }

    public static ApplyResult error(String filePath, String errorMessage) {
        return new ApplyResult(filePath, Status.ERROR, errorMessage);
    }

    public String getFilePath() { return filePath; }
    public Status getStatus()   { return status; }
    public String getMessage()  { return message; }

    @Override
    public String toString() {
        return "[%s] %s – %s".formatted(status, filePath, message);
    }
}
