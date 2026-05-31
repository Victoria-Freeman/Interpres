package com.venddair.interpres;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ZoomImageView extends View {

    private Drawable drawable;
    private final Matrix matrix = new Matrix();
    private final PointF last = new PointF();
    private float lastSpan = 0;

    private static final Paint OVERLAY_PAINT = new Paint();
    private static final Paint BORDER_PAINT = new Paint();
    static {
        OVERLAY_PAINT.setColor(0x80000000);
        BORDER_PAINT.setColor(0xFFFFFFFF);
        BORDER_PAINT.setStyle(Paint.Style.STROKE);
        BORDER_PAINT.setStrokeWidth(3f);
    }

    private final PointF cropStart = new PointF();
    private final PointF cropEnd = new PointF();
    private boolean cropEnabled = false, cropActive = false;

    public interface CropListener {
        void onCropCommitted(Rect cropBitmapRect);
    }

    private CropListener cropListener;

    public void setCropListener(CropListener l) { cropListener = l; }

    public ZoomImageView(Context ctx, AttributeSet attrs) { super(ctx, attrs); }

    public void setImageDrawable(Drawable d) {
        drawable = d;
        if (d != null) d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.reset();
        fitToView();
        invalidate();
    }

    public void setImageBitmap(Bitmap bm) {
        setImageDrawable(new BitmapDrawable(getResources(), bm));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fitToView();
    }

    private void fitToView() {
        if (drawable == null || getWidth() == 0 || getHeight() == 0) return;
        float scale = Math.min(
            (float) getWidth() / drawable.getIntrinsicWidth(),
            (float) getHeight() / drawable.getIntrinsicHeight());
        float dx = (getWidth() - drawable.getIntrinsicWidth() * scale) / 2f;
        float dy = (getHeight() - drawable.getIntrinsicHeight() * scale) / 2f;
        matrix.reset();
        matrix.postScale(scale, scale);
        matrix.postTranslate(dx, dy);
        invalidate();
    }

    public void setCropMode(boolean enabled) {
        cropEnabled = enabled;
        if (!enabled) { invalidate(); return; }
        fitToView();
        cropActive = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (drawable == null) return;
        int save = canvas.save();
        canvas.concat(matrix);
        drawable.draw(canvas);
        canvas.restoreToCount(save);
        if (cropEnabled && cropActive) drawCropOverlay(canvas);
    }

    private void drawCropOverlay(Canvas canvas) {
        float l = Math.min(cropStart.x, cropEnd.x), t = Math.min(cropStart.y, cropEnd.y);
        float r = Math.max(cropStart.x, cropEnd.x), b = Math.max(cropStart.y, cropEnd.y);
        canvas.drawRect(0, 0, getWidth(), t, OVERLAY_PAINT);
        canvas.drawRect(0, b, getWidth(), getHeight(), OVERLAY_PAINT);
        canvas.drawRect(0, t, l, b, OVERLAY_PAINT);
        canvas.drawRect(r, t, getWidth(), b, OVERLAY_PAINT);
        canvas.drawRect(l, t, r, b, BORDER_PAINT);
    }

    private Rect computeCropBitmapRect() {
        Matrix inverse = new Matrix();
        if (!matrix.invert(inverse)) return null;
        int bw = drawable.getIntrinsicWidth(), bh = drawable.getIntrinsicHeight();
        float[] pts = {
            Math.min(cropStart.x, cropEnd.x), Math.min(cropStart.y, cropEnd.y),
            Math.max(cropStart.x, cropEnd.x), Math.max(cropStart.y, cropEnd.y)
        };
        inverse.mapPoints(pts);
        return new Rect(
            clamp((int) pts[0], 0, bw), clamp((int) pts[1], 0, bh),
            clamp((int) pts[2], 0, bw), clamp((int) pts[3], 0, bh));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (drawable == null) return false;
        if (cropEnabled) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    cropStart.set(ev.getX(), ev.getY());
                    cropEnd.set(ev.getX(), ev.getY());
                    cropActive = true;
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    cropEnd.set(ev.getX(), ev.getY());
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    if (cropListener != null) {
                        Rect r = computeCropBitmapRect();
                        if (r != null && r.width() > 0 && r.height() > 0)
                            cropListener.onCropCommitted(r);
                    }
                    cropActive = false;
                    invalidate();
                    break;
                case MotionEvent.ACTION_CANCEL:
                    cropActive = false;
                    invalidate();
                    break;
            }
            return true;
        }
        float x = ev.getX(), y = ev.getY();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                last.set(x, y);
                break;
            case MotionEvent.ACTION_MOVE:
                if (ev.getPointerCount() == 1) {
                    matrix.postTranslate(x - last.x, y - last.y);
                    last.set(x, y);
                    invalidate();
                } else if (ev.getPointerCount() == 2) {
                    float span = (float) Math.hypot(
                        ev.getX(0) - ev.getX(1), ev.getY(0) - ev.getY(1));
                    if (lastSpan > 0) {
                        matrix.postScale(span / lastSpan, span / lastSpan,
                            (ev.getX(0) + ev.getX(1)) / 2f, (ev.getY(0) + ev.getY(1)) / 2f);
                        invalidate();
                    }
                    lastSpan = span;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_POINTER_UP:
                lastSpan = 0;
                break;
        }
        return true;
    }
}
