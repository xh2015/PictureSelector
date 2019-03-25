package com.luck.picture.lib.tools;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Author：gary
 * <p/>
 * Email: xuhaozv@163.com
 * <p/>
 * description:
 * <p/>
 * Date: 2019/3/22 3:31 PM
 */
public class ImageUtils {

    /**
     * Return bitmap.
     *
     * @param file The file.
     * @return bitmap
     */
    public static Bitmap getBitmap(final File file) {
        if (file == null) return null;
        return BitmapFactory.decodeFile(file.getAbsolutePath());
    }

    public static boolean addTextWatermark(final File file,
                                        final String content) {
        Bitmap src = getBitmap(file);
        Bitmap bitmap = addTextWatermark(src, content, false);
        if (!isEmptyBitmap(bitmap)) {
            return save(bitmap, file);
        }
        return false;
    }

    /**
     * Return the bitmap with text watermarking.
     *
     * @param src     The source of bitmap.
     * @param content The content of text.
     * @param recycle True to recycle the source of bitmap, false otherwise.
     * @return the bitmap with text watermarking
     */
    public static Bitmap addTextWatermark(final Bitmap src,
                                          final String content,
                                          final boolean recycle) {
        if (isEmptyBitmap(src) || content == null) return null;
        Bitmap ret = src.copy(src.getConfig(), true);
        //获取图片宽和高
        int height = ret.getHeight();
        int textSize = ret.getWidth() / 20;
        //新建一个Bitmap，加水印后的图片写入到新Bitmap中
        Canvas canvas = new Canvas(ret);
        // 新建一个文字画笔
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
        textPaint.setTextSize(textSize);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(Color.RED);
        textPaint.setAntiAlias(true);

        StaticLayout layout = new StaticLayout(content, textPaint, canvas.getWidth() - 20,
                Layout.Alignment.ALIGN_NORMAL, 1.0F, 0.0F, true);
        canvas.save();
        canvas.translate(20, height - layout.getHeight() - 20);
        layout.draw(canvas);
        canvas.restore();//别忘了restore
        if (recycle && !src.isRecycled() && ret != src) src.recycle();
        return ret;
    }

    private static boolean isEmptyBitmap(final Bitmap src) {
        return src == null || src.getWidth() == 0 || src.getHeight() == 0;
    }

    /**
     * Save the bitmap.
     *
     * @param src  The source of bitmap.
     * @param file The file.
     * @return {@code true}: success<br>{@code false}: fail
     */
    public static boolean save(final Bitmap src,
                               final File file) {
        if (isEmptyBitmap(src)) return false;
        OutputStream os = null;
        boolean ret = false;
        try {
            os = new BufferedOutputStream(new FileOutputStream(file));
            ret = src.compress(Bitmap.CompressFormat.JPEG, 100, os);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }
}
