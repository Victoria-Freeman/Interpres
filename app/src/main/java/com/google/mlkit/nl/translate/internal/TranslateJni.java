package com.google.mlkit.nl.translate.internal;

public class TranslateJni {
    static {
        System.loadLibrary("translate_jni");
    }

    private long nativePtr;

    public native long nativeInit(String srcLang, String tgtLang, String dictPath,
                                   String dictPath2,
                                   String nmtPb1, String nmtPb2,
                                   String fallbackPb1, String fallbackPb2,
                                   String sttPb1, String sttPb2) throws zzk;

    public native void nativeDestroy(long ptr);

    public native byte[] nativeTranslate(long ptr, byte[] input) throws zzm;

    private static Exception newLoadingException(int errorCode) {
        return new zzk(errorCode, null);
    }

    private static Exception newTranslateException(int errorCode) {
        return new zzm(errorCode, null);
    }

    public void init(String srcLang, String tgtLang, String dictPath,
                     String dictPath2,
                     String nmtPb1, String nmtPb2,
                     String fallbackPb1, String fallbackPb2,
                     String sttPb1, String sttPb2) throws zzk {
        nativePtr = nativeInit(srcLang, tgtLang, dictPath, dictPath2,
                nmtPb1, nmtPb2, fallbackPb1, fallbackPb2, sttPb1, sttPb2);
    }

    public String translate(String text) throws zzm {
        byte[] result = nativeTranslate(nativePtr, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new String(result, java.nio.charset.StandardCharsets.UTF_8);
    }

    public void destroy() {
        if (nativePtr != 0) {
            nativeDestroy(nativePtr);
            nativePtr = 0;
        }
    }
}
