package com.venddair.interpres;

import android.content.Context;
import android.util.Log;

import com.google.mlkit.nl.translate.internal.TranslateJni;
import com.google.mlkit.nl.translate.internal.zzk;
import com.google.mlkit.nl.translate.internal.zzm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class MlKitTranslator {
    private static final String TAG = "MlKitTranslator";
    private static final String BASE_URL = "https://dl.google.com/translate/offline/v5/high/r29/";
    private static final byte[] NMT_PB = android.util.Base64.decode("CgIQAQ==", 0);

    private final File modelBaseDir;
    private final String[] mlkitCodes;

    private final Object lock = new Object();

    private TranslateJni jni;
    private String loadedSrc;
    private String loadedTgt;

    MlKitTranslator(Context ctx) {
        this.modelBaseDir = new File(ctx.getFilesDir(), "mlkit_translate");
        if (!modelBaseDir.exists()) modelBaseDir.mkdirs();
        this.mlkitCodes = ctx.getResources().getStringArray(R.array.language_mlkit_codes);
    }

    String translate(String text, int srcIdx, int tgtIdx) throws Exception {
        synchronized (lock) {
            String srcCode = mlkitCodes[srcIdx];
            String tgtCode = mlkitCodes[tgtIdx];

            if (srcCode.isEmpty() || tgtCode.isEmpty()) {
                throw new IllegalArgumentException("Language not supported by ML Kit translator");
            }

            if (!srcCode.equals(loadedSrc) || !tgtCode.equals(loadedTgt)) loadModel(srcCode, tgtCode);

            String result = translateJni(text);
            return result.isEmpty() ? "(empty result)" : result;
        }
    }

    private void loadModel(String srcCode, String tgtCode) throws Exception {
        if (jni != null) {
            jni.destroy();
            jni = null;
            loadedSrc = null;
            loadedTgt = null;
        }

        jni = new TranslateJni();

        List<File> dirs = new ArrayList<>();
        List<String> pairs = new ArrayList<>();
        boolean direct = srcCode.equals("en") || tgtCode.equals("en");
        if (direct) {
            String[] a = {srcCode, tgtCode}; Arrays.sort(a);
            String pair = a[0] + "_" + a[1];
            dirs.add(new File(modelBaseDir, pair));
            pairs.add(pair);
        } else {
            for (String[] pair : new String[][]{{srcCode, "en"}, {"en", tgtCode}}) {
                Arrays.sort(pair);
                String p = pair[0] + "_" + pair[1];
                dirs.add(new File(modelBaseDir, p));
                pairs.add(p);
            }
        }
        for (int i = 0; i < dirs.size(); i++) ensureModelReady(pairs.get(i), dirs.get(i));
        initJni(srcCode, tgtCode,
            dirs.get(0).getAbsolutePath(), dirs.size() > 1 ? dirs.get(1).getAbsolutePath() : null,
            pbPath(dirs.get(0), "nmt", pairs.get(0)), dirs.size() > 1 ? pbPath(dirs.get(1), "nmt", pairs.get(1)) : null,
            pbPath(dirs.get(0), "fallback", pairs.get(0)), dirs.size() > 1 ? pbPath(dirs.get(1), "fallback", pairs.get(1)) : null,
            pbPath(dirs.get(0), "stt", pairs.get(0)), dirs.size() > 1 ? pbPath(dirs.get(1), "stt", pairs.get(1)) : null);
        loadedSrc = srcCode;
        loadedTgt = tgtCode;
    }

    private String pbPath(File dir, String prefix, String pair) {
        File f = new File(dir, prefix + "_rapid_response_" + pair + ".pb.bin");
        return f.exists() ? f.getAbsolutePath() : null;
    }

    private void ensureModelReady(String pair, File modelDir) throws IOException {
        File marker = new File(modelDir, ".ready");
        if (marker.exists()) return;

        String url = BASE_URL + pair + ".zip";
        File tmpZip = new File(modelBaseDir, pair + ".zip.tmp");
        downloadFile(url, tmpZip);
        extractZip(tmpZip, modelDir);
        tmpZip.delete();
        writeFile(new File(modelDir, "nmt_rapid_response_" + pair + ".pb.bin"), NMT_PB);
        try (FileOutputStream ignored = new FileOutputStream(marker)) {}
    }

    private void downloadFile(String urlStr, File dest) throws IOException {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(30000);
            c.setReadTimeout(120000);
            c.setRequestProperty("Accept-Encoding", "identity");
            try (InputStream in = c.getInputStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        try (FileInputStream fis = new FileInputStream(zipFile);
             ZipInputStream zis = new ZipInputStream(fis)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                int slash = name.indexOf('/');
                if (slash >= 0) name = name.substring(slash + 1);
                if (name.isEmpty()) continue;
                File outFile = new File(destDir, name);
                outFile.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(outFile)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = zis.read(buf)) > 0) out.write(buf, 0, len);
                }
            }
        }
    }

    private void writeFile(File file, byte[] data) throws IOException {
        if (file.exists()) return;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(data);
        }
    }

    private void initJni(String srcLang, String tgtLang, String dictPath,
                          String dictPath2,
                          String nmtPb1, String nmtPb2,
                          String fallbackPb1, String fallbackPb2,
                          String sttPb1, String sttPb2) {
        try {
            jni.init(srcLang, tgtLang, dictPath, dictPath2,
                    nmtPb1, nmtPb2, fallbackPb1, fallbackPb2, sttPb1, sttPb2);
        } catch (zzk e) {
            Log.e(TAG, "Init error code=" + e.getErrorCode(), e);
            throw new RuntimeException("Init failed: " + e.getMessage(), e);
        }
    }

    private String translateJni(String text) {
        try {
            return jni.translate(text);
        } catch (zzm e) {
            Log.e(TAG, "Translate error code=" + e.getErrorCode(), e);
            throw new RuntimeException("Translation failed: " + e.getMessage(), e);
        }
    }

    void close() {
        synchronized (lock) {
            if (jni != null) {
                jni.destroy();
                jni = null;
                loadedSrc = null;
                loadedTgt = null;
            }
        }
    }
}
