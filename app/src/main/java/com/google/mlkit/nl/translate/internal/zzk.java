package com.google.mlkit.nl.translate.internal;

public class zzk extends Exception {
    private final int errorCode;

    public zzk(int errorCode, Object unused) {
        super("Translate init error: " + errorCode);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
