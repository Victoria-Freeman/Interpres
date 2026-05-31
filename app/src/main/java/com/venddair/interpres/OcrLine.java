package com.venddair.interpres;

import android.graphics.Rect;

class OcrLine {
    final String text;
    final Rect boundingBox;

    OcrLine(String text, Rect boundingBox) {
        this.text = text;
        this.boundingBox = boundingBox;
    }
}
