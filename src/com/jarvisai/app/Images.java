package com.jarvisai.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.InputStream;

/** Decodes user-picked images with sampling so the API request stays small. */
public final class Images {

    private Images() {}

    public static Bitmap fromUri(Context c, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int max = Math.max(bounds.outWidth, bounds.outHeight);
            while (max / sample > maxSide * 2) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                bmp = BitmapFactory.decodeStream(in, null, opts);
            }
            if (bmp == null) return null;
            return Util.scale(bmp, maxSide);
        } catch (Exception e) {
            return null;
        }
    }

    public static byte[] jpegFromUri(Context c, Uri uri, int maxSide, int quality) {
        Bitmap b = fromUri(c, uri, maxSide);
        if (b == null) return null;
        byte[] out = Util.jpeg(b, quality);
        b.recycle();
        return out;
    }
}
