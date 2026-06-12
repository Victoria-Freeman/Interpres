package com.venddair.interpres;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Toast;
import android.app.Activity;
import com.venddair.interpres.databinding.ActivityMainBinding;
import com.venddair.ocr.OcrEngine;
import com.venddair.ocr.OcrLine;
import com.venddair.tegula.core.TegulaEdgeToEdge;
import com.venddair.tegula.dialogs.TegulaDialog;
import com.venddair.tegula.dropdowns.TegulaDropdown;
import java.util.List;

public class MainActivity extends Activity {
    private ActivityMainBinding b;
    private MlKitTranslator translator;
    private OcrEngine ocrEngine;
    // en=10, es=38
    private int src = 10, tgt = 38;
    private static final int PICK_IMAGE = 1001, CAPTURE_IMAGE = 1002, REQUEST_CAMERA = 1003;
    private Uri cameraUri;
    private enum UiState { DEFAULT, CROPPING, RESULT }
    private UiState uiState = UiState.DEFAULT;
    private Bitmap currentBitmap;
    private volatile boolean destroyed;

    private void ui(Runnable r) {
        runOnUiThread(() -> { if (!destroyed) r.run(); });
    }

    private void setState(UiState state) {
        uiState = state;
        b.clearButton.setVisibility(View.VISIBLE);
        b.cameraButton.setVisibility(View.VISIBLE);
        b.imageContainer.setVisibility(state == UiState.RESULT ? View.GONE : View.VISIBLE);
        b.inputText.setVisibility(state == UiState.DEFAULT ? View.VISIBLE : View.GONE);
        b.overlayImage.setVisibility(state == UiState.CROPPING ? View.VISIBLE : View.GONE);
        b.overlayImage.setCropMode(state == UiState.CROPPING);
        b.translateButton.setText(state == UiState.RESULT ? R.string.crop_again : R.string.translate);
        b.translateButton.setEnabled(state != UiState.CROPPING);
        b.outputText.setVisibility(state == UiState.CROPPING ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        TegulaEdgeToEdge.setupEdgeToEdge(this);
        translator = new MlKitTranslator(this);
        try {
            ocrEngine = new OcrEngine(this);
        } catch (RuntimeException e) {
            ocrEngine = null;
            Toast.makeText(this, R.string.ocr_init_failed, Toast.LENGTH_LONG).show();
        }
        if (s != null) {
            src = s.getInt("src", 10);
            tgt = s.getInt("tgt", 38);
            b.inputText.setText(s.getString("input", ""));
            b.outputText.setText(s.getString("output", ""));
        }
        setupDropdowns();
        setupButtons();
        setState(s != null
            ? UiState.valueOf(s.getString("uiState", UiState.DEFAULT.name()))
            : UiState.DEFAULT);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("src", src);
        out.putInt("tgt", tgt);
        out.putString("uiState", uiState.name());
        out.putString("input", b.inputText.getText().toString());
        out.putString("output", b.outputText.getText().toString());
    }

    private void setupDropdowns() {
        String[] names = getResources().getStringArray(R.array.language_names);
        TegulaDropdown.setItems(b.sourceDropdown, names);
        TegulaDropdown.setItems(b.targetDropdown, names);
        TegulaDropdown.setSelected(b.sourceDropdown, src);
        TegulaDropdown.setSelected(b.targetDropdown, tgt);
        b.sourceDropdown.setOnClickListener(v -> TegulaDropdown.toggle(b.sourceDropdown));
        b.targetDropdown.setOnClickListener(v -> TegulaDropdown.toggle(b.targetDropdown));
        TegulaDropdown.setOnItemSelectedListener(b.sourceDropdown, (i, t) -> {
            src = i; TegulaDropdown.setSelected(b.sourceDropdown, i);
        });
        TegulaDropdown.setOnItemSelectedListener(b.targetDropdown, (i, t) -> {
            tgt = i; TegulaDropdown.setSelected(b.targetDropdown, i);
        });
    }

    private void setupButtons() {
        b.clearButton.setOnClickListener(v -> {
            b.inputText.setText("");
            if (uiState == UiState.DEFAULT) return;
            if (currentBitmap != null) { currentBitmap.recycle(); currentBitmap = null; }
            b.overlayImage.setImageDrawable(null);
            b.outputText.setText("");
            setState(UiState.DEFAULT);
        });
        b.cameraButton.setOnClickListener(v -> {
            // camera or gallery?
            TegulaDialog.Item[] items = {
                new TegulaDialog.Item(android.R.drawable.ic_menu_camera, getString(R.string.camera)),
                new TegulaDialog.Item(android.R.drawable.ic_menu_gallery, getString(R.string.gallery)),
            };
            TegulaDialog.show(this, getString(R.string.scan_image), items,
                (index, t) -> { if (index == 0) openCamera(); else openGallery(); });
        });
        b.swapButton.setOnClickListener(v -> {
            int tmp = src; src = tgt; tgt = tmp;
            TegulaDropdown.setSelected(b.sourceDropdown, src);
            TegulaDropdown.setSelected(b.targetDropdown, tgt);
            String o = b.outputText.getText().toString();
            if (!o.isEmpty()) b.inputText.setText(o);
        });
        b.translateButton.setOnClickListener(v -> {
            if (uiState == UiState.RESULT) { enterCropMode(currentBitmap); return; }
            String text = b.inputText.getText().toString().trim();
            if (text.isEmpty()) {
                Toast.makeText(this, R.string.enter_text_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            if (src == tgt) {
                Toast.makeText(this, R.string.languages_must_differ, Toast.LENGTH_SHORT).show();
                return;
            }
            b.translateButton.setEnabled(false);
            b.outputText.setText(R.string.translating);
            new Thread(() -> {
                try {
                    String result = runTranslation(text);
                    ui(() -> b.outputText.setText(result));
                } catch (Exception e) {
                    ui(() -> {
                        b.outputText.setText(getString(R.string.error_translation));
                        Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                } finally {
                    ui(() -> b.translateButton.setEnabled(true));
                }
            }).start();
        });
    }

    private String runTranslation(String text) throws Exception {
        return translator.translate(text, src, tgt);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (translator != null) translator.close();
        if (ocrEngine != null) ocrEngine.close();
        if (currentBitmap != null) { currentBitmap.recycle(); currentBitmap = null; }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(code, perms, grants);
        if (code == REQUEST_CAMERA && grants.length > 0
                && grants[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;
        Uri uri = requestCode == PICK_IMAGE && data != null ? data.getData()
            : requestCode == CAPTURE_IMAGE ? cameraUri : null;
        if (uri == null) return;
        try {
            enterCropMode(decodeBitmapFromUri(uri));
        } catch (Exception e) {
            Toast.makeText(this, R.string.ocr_error, Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap decodeBitmapFromUri(Uri uri) throws Exception {
        Bitmap bitmap = Build.VERSION.SDK_INT >= 28
            ? ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri))
            : MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        return bitmap.getConfig() == Bitmap.Config.ARGB_8888
            ? bitmap : bitmap.copy(Bitmap.Config.ARGB_8888, false);
    }

    private void enterCropMode(Bitmap bitmap) {
        currentBitmap = bitmap;
        b.overlayImage.setImageBitmap(bitmap);
        b.overlayImage.setCropListener(r -> new Thread(() -> doOcrAndTranslate(r)).start());
        setState(UiState.CROPPING);
    }

    private void doOcrAndTranslate(Rect cropRect) {
        Bitmap bitmap = currentBitmap;
        if (bitmap == null) return;
        try {
            Bitmap cropped = Bitmap.createBitmap(bitmap,
                cropRect.left, cropRect.top, cropRect.width(), cropRect.height());
            List<OcrLine> lines = ocrEngine.recognizeText(cropped);
            cropped.recycle();
            if (destroyed) return;
            if (lines.isEmpty()) {
                ui(() -> Toast.makeText(this,
                    R.string.no_text_detected, Toast.LENGTH_SHORT).show());
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (OcrLine l : lines) sb.append(l.getText()).append(" ");
            if (destroyed) return;
            String translation = runTranslation(sb.toString().trim());
            ui(() -> {
                b.outputText.setText(translation);
                setState(UiState.RESULT);
            });
        } catch (Exception e) {
            ui(() -> {
                b.outputText.setText(getString(R.string.error_translation));
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
            return;
        }
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cameraUri = getContentResolver().insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (cameraUri == null) {
            Toast.makeText(this, R.string.ocr_error, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
        startActivityForResult(intent, CAPTURE_IMAGE);
    }

    private void openGallery() {
        Intent intent = new Intent(Build.VERSION.SDK_INT >= 33
            ? MediaStore.ACTION_PICK_IMAGES : Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE);
    }
}
