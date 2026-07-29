package com.axiiontech.billboardble;

import android.graphics.Bitmap;
import android.graphics.Matrix;

/**
 * Converts an arbitrary photo into the exact 128×128 RGB565 byte layout
 * the ESP32 firmware expects (little-endian uint16 per pixel, same as
 * the web UI's canvas-based conversion). Mirrors the two quality fixes
 * applied on the web side:
 *   1) aspect-ratio-preserving center crop instead of squishing
 *   2) Floyd-Steinberg dithering when quantizing down to 5/6/5 bits
 */
public final class ImageUtils {

    public static final int SIZE = 128;
    public static final int IMG_BYTES = SIZE * SIZE * 2;

    private ImageUtils() {}

    /** Crop to a centered square, then downscale in ~2x steps for a sharper result. */
    public static Bitmap toSquare128(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int side = Math.min(w, h);
        int x = (w - side) / 2, y = (h - side) / 2;
        Bitmap cropped = Bitmap.createBitmap(src, x, y, side, side);

        Bitmap current = cropped;
        int curSize = side;
        while (curSize / 2 >= SIZE) {
            int next = curSize / 2;
            Bitmap scaled = Bitmap.createScaledBitmap(current, next, next, true);
            if (current != cropped) current.recycle();
            current = scaled;
            curSize = next;
        }
        Bitmap result = Bitmap.createScaledBitmap(current, SIZE, SIZE, true);
        if (current != cropped) current.recycle();
        return result;
    }

    /** Rotate a bitmap by EXIF degrees (photos from the camera often need this). */
    public static Bitmap rotateIfNeeded(Bitmap src, int degrees) {
        if (degrees == 0) return src;
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    /**
     * Quantizes a 128×128 bitmap to RGB565 with Floyd-Steinberg dithering
     * and packs it little-endian, exactly matching what loadImageToBuf()
     * on the ESP32 reads straight into its uint16_t frame buffer.
     */
    public static byte[] toDitheredRgb565(Bitmap bmp128) {
        int[] px = new int[SIZE * SIZE];
        bmp128.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);

        float[] errR = new float[SIZE * SIZE];
        float[] errG = new float[SIZE * SIZE];
        float[] errB = new float[SIZE * SIZE];
        byte[] out = new byte[IMG_BYTES];

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int i = y * SIZE + x;
                int argb = px[i];
                float r = ((argb >> 16) & 0xFF) + errR[i];
                float g = ((argb >> 8) & 0xFF) + errG[i];
                float b = (argb & 0xFF) + errB[i];
                r = clamp(r); g = clamp(g); b = clamp(b);

                int r5 = Math.round(r / 255f * 31f);
                int g6 = Math.round(g / 255f * 63f);
                int b5 = Math.round(b / 255f * 31f);
                int rgb565 = (r5 << 11) | (g6 << 5) | b5;

                // little-endian: low byte first, matching ESP32's native uint16_t layout
                out[i * 2]     = (byte) (rgb565 & 0xFF);
                out[i * 2 + 1] = (byte) ((rgb565 >> 8) & 0xFF);

                float errRv = r - (r5 * 255f / 31f);
                float errGv = g - (g6 * 255f / 63f);
                float errBv = b - (b5 * 255f / 31f);

                if (x + 1 < SIZE) {
                    errR[i + 1] += errRv * 7f / 16f;
                    errG[i + 1] += errGv * 7f / 16f;
                    errB[i + 1] += errBv * 7f / 16f;
                }
                if (y + 1 < SIZE) {
                    if (x > 0) {
                        errR[i + SIZE - 1] += errRv * 3f / 16f;
                        errG[i + SIZE - 1] += errGv * 3f / 16f;
                        errB[i + SIZE - 1] += errBv * 3f / 16f;
                    }
                    errR[i + SIZE] += errRv * 5f / 16f;
                    errG[i + SIZE] += errGv * 5f / 16f;
                    errB[i + SIZE] += errBv * 5f / 16f;
                    if (x + 1 < SIZE) {
                        errR[i + SIZE + 1] += errRv * 1f / 16f;
                        errG[i + SIZE + 1] += errGv * 1f / 16f;
                        errB[i + SIZE + 1] += errBv * 1f / 16f;
                    }
                }
            }
        }
        return out;
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(255f, v));
    }
}
