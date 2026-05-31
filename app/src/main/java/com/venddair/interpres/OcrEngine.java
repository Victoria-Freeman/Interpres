package com.venddair.interpres;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbaaj;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbaai;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbabj;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbabl;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbbe;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbbb;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbix;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbki;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbow;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbpg;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbpk;
import com.google.android.gms.internal.mlkit_vision_text_bundled_common.zbpb;
import com.google.android.libraries.vision.visionkit.pipeline.AndroidAssetUtil;
import com.google.android.libraries.vision.visionkit.pipeline.zbcv;
import com.google.android.libraries.vision.visionkit.pipeline.zbcw;
import com.google.android.libraries.vision.visionkit.pipeline.zbcz;
import com.google.android.libraries.vision.visionkit.pipeline.zbca;
import com.google.android.libraries.vision.visionkit.pipeline.zbbz;
import com.google.android.libraries.vision.visionkit.pipeline.zbdo;
import com.google.android.libraries.vision.visionkit.pipeline.zbdl;
import com.google.android.libraries.vision.visionkit.pipeline.zbfc;
import com.google.android.libraries.vision.visionkit.pipeline.alt.PipelineException;
import com.google.android.libraries.vision.visionkit.pipeline.alt.zbc;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

class OcrEngine {
    private volatile OcrPipeline pipeline;

    OcrEngine(Context ctx) {
        pipeline = new OcrPipeline(ctx);
    }

    List<OcrLine> recognizeText(Bitmap bitmap) throws Exception {
        OcrPipeline p = pipeline;
        if (p == null) throw new IllegalStateException("OCR engine closed");
        return p.recognize(bitmap);
    }

    void close() {
        if (pipeline != null) { pipeline.close(); pipeline = null; }
    }

    private static class OcrPipeline {
        private static final String TAG = "OcrPipeline";
        private final zbc delegate;

        OcrPipeline(Context ctx) {
            System.loadLibrary("mlkit_google_ocr_pipeline");
            AndroidAssetUtil.zba(ctx);

            zbbb textConfigBuilder = zbbe.zba();
            textConfigBuilder.zbd("mlkit-google-ocr-models");
            textConfigBuilder.zba("taser_tflite_gocrlatin_mbv2_scriptid_aksara_layout_gcn_mobile");
            textConfigBuilder.zbe(true);
            textConfigBuilder.zbb(true);
            textConfigBuilder.zbc(zbpg.zba().zba(zbpk.zba().zba("en")));

            zbdl componentBuilder = zbdo.zba();
            componentBuilder.zbb(textConfigBuilder);
            componentBuilder.zbc(zbcw.zba().zba(zbcv.zba(4)));
            componentBuilder.zba(zbix.zba().zba("PassThroughCoarseClassifier"));

            zbbz configBuilder = zbca.zbc();
            configBuilder.zba(componentBuilder);
            configBuilder.zbb(zbfc.zba().zba(2));

            delegate = new zbc((zbca) configBuilder.zbi(), "mlkit_google_ocr_pipeline");
            try {
                delegate.zbg();
            } catch (PipelineException e) {
                throw new RuntimeException("Failed to start OCR pipeline", e);
            }
        }

        List<OcrLine> recognize(Bitmap bitmap) throws Exception {
            Bitmap bm = bitmap;
            boolean copied = false;
            if (bm.getConfig() != Bitmap.Config.ARGB_8888) {
                bm = bm.copy(Bitmap.Config.ARGB_8888, false);
                copied = true;
            }
            zbki result = delegate.zbi(System.nanoTime(), bm, 1);
            if (copied) bm.recycle();
            if (!result.zbc()) throw new RuntimeException("OCR returned error");

            return parseLines((zbcz) result.zba());
        }

        private List<OcrLine> parseLines(zbcz result) {
            zbabl textResult = result.zbe();
            List<?> elements = textResult.zbf();
            ArrayList<OcrLine> lines = new ArrayList<>();
            for (int i = 0; i < elements.size(); i++) {
                zbabj element = (zbabj) elements.get(i);
                if (element.zbI() != 3) continue;
                String t = element.zbH();
                if (t == null) continue;
                t = t.trim();
                if (t.isEmpty()) continue;
                lines.add(new OcrLine(t, extractBox(element)));
            }
            return lines;
        }

        private Rect extractBox(zbabj element) {
            try {
                zbaaj frame = element.zbf();

                // discriminant determines box type (ML Kit internal)
                int disc = reflectInt(frame, "zbd");
                zbpb r;
                if (disc == 3)       r = (zbpb) reflectObj(frame.zbc(), "zbh");
                else if (disc == 1)  r = (zbpb) reflectObj(frame.zbf(), "zbf");
                else                 r = frame.zbe();
                if (r == null) return new Rect();

                return axisAlignedBoundingBox(
                    reflectInt(r, "zbe"), reflectInt(r, "zbf"),
                    reflectInt(r, "zbg"), reflectInt(r, "zbh"),
                    reflectFloat(r, "zbi"));
            } catch (Exception e) {
                Log.e(TAG, "extractBox FAILED", e);
                return new Rect();
            }
        }

        private static Object reflectObj(Object o, String name) throws Exception {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true); return f.get(o);
        }

        private static int reflectInt(Object o, String name) throws Exception {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true); return f.getInt(o);
        }

        private static float reflectFloat(Object o, String name) throws Exception {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true); return f.getFloat(o);
        }

        private static Rect axisAlignedBoundingBox(int x, int y, int dx, int dy, float deg) {
            double rad = Math.toRadians(deg), cos = Math.cos(rad), sin = Math.sin(rad);
            int x2 = (int) (x + dx * cos), y2 = (int) (y + dx * sin);
            int x3 = (int) (x2 - dy * sin), y3 = (int) (y2 + dy * cos);
            int x4 = x + (x3 - x2), y4 = y + (y3 - y2);
            return new Rect(
                Math.min(Math.min(x, x2), Math.min(x3, x4)),
                Math.min(Math.min(y, y2), Math.min(y3, y4)),
                Math.max(Math.max(x, x2), Math.max(x3, x4)),
                Math.max(Math.max(y, y2), Math.max(y3, y4)));
        }

        void close() {
            delegate.zbf();
        }
    }
}
