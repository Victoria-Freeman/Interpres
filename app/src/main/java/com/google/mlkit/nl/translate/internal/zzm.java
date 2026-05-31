package com.google.mlkit.nl.translate.internal;

public class zzm extends Exception {
    private final int errorCode;

    public zzm(int errorCode, Object unused) {
        super("Translate error: " + errorCode);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
