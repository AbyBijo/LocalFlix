package com.media.localflix;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Code-drawn icons (no resource files needed). 24x24 grid. */
public class Icon extends Drawable {
    public static final int HOME = 0, FOLDER = 1, HEART = 2, GEAR = 3, PLAY = 4,
        PAUSE = 5, BACK = 6, LOCK = 7, ROTATE = 8, PREV = 9, NEXT = 10, REW = 11, FWD = 12,
        VOL = 13, MUTE = 14;

    private final int type, size;
    private final boolean fill;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Icon(int type, int color, int size, boolean fill) {
        this.type = type;
        this.size = size;
        this.fill = fill;
        p.setColor(color);
        p.setStrokeWidth(2f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    private void stroke() {
        p.setStyle(Paint.Style.STROKE);
    }

    private void solid() {
        p.setStyle(Paint.Style.FILL_AND_STROKE);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) / 24f;
        c.save();
        c.translate(b.left, b.top);
        c.scale(s, s);
        Path path = new Path();
        switch (type) {
            case HOME:
                stroke();
                path.moveTo(3, 11);
                path.lineTo(12, 3);
                path.lineTo(21, 11);
                path.moveTo(5, 9.5f);
                path.lineTo(5, 21);
                path.lineTo(19, 21);
                path.lineTo(19, 9.5f);
                c.drawPath(path, p);
                break;
            case FOLDER:
                stroke();
                path.moveTo(3, 6);
                path.lineTo(9, 6);
                path.lineTo(11, 8.5f);
                path.lineTo(21, 8.5f);
                path.lineTo(21, 19);
                path.lineTo(3, 19);
                path.close();
                c.drawPath(path, p);
                break;
            case HEART:
                if (fill) solid(); else stroke();
                path.moveTo(12, 21);
                path.cubicTo(2, 13, 4, 4, 12, 8);
                path.cubicTo(20, 4, 22, 13, 12, 21);
                path.close();
                c.drawPath(path, p);
                break;
            case GEAR:
                stroke();
                c.drawCircle(12, 12, 3.5f, p);
                c.drawCircle(12, 12, 7.5f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float cx = (float) Math.cos(a), cy = (float) Math.sin(a);
                    c.drawLine(12 + cx * 7.5f, 12 + cy * 7.5f, 12 + cx * 10.5f, 12 + cy * 10.5f, p);
                }
                break;
            case PLAY:
                solid();
                path.moveTo(7, 4);
                path.lineTo(20, 12);
                path.lineTo(7, 20);
                path.close();
                c.drawPath(path, p);
                break;
            case PAUSE:
                solid();
                c.drawRect(6, 4, 10, 20, p);
                c.drawRect(14, 4, 18, 20, p);
                break;
            case BACK:
                stroke();
                path.moveTo(20, 12);
                path.lineTo(5, 12);
                path.moveTo(11, 6);
                path.lineTo(5, 12);
                path.lineTo(11, 18);
                c.drawPath(path, p);
                break;
            case LOCK:
                stroke();
                c.drawRect(5, 11, 19, 21, p);
                c.drawArc(new RectF(8, 3, 16, 12), 180, 180, false, p);
                c.drawLine(8, 7.5f, 8, 11, p);
                c.drawLine(16, 7.5f, 16, 11, p);
                break;
            case ROTATE:
                stroke();
                c.drawArc(new RectF(4, 4, 20, 20), -60, 270, false, p);
                path.moveTo(20, 2.5f);
                path.lineTo(20, 8);
                path.lineTo(14.5f, 8);
                c.drawPath(path, p);
                break;
            case PREV:
                solid();
                c.drawRect(5, 5, 7.5f, 19, p);
                path.moveTo(19, 5);
                path.lineTo(19, 19);
                path.lineTo(8.5f, 12);
                path.close();
                c.drawPath(path, p);
                break;
            case NEXT:
                solid();
                c.drawRect(16.5f, 5, 19, 19, p);
                path.moveTo(5, 5);
                path.lineTo(5, 19);
                path.lineTo(15.5f, 12);
                path.close();
                c.drawPath(path, p);
                break;
            case REW:
            case FWD:
                stroke();
                c.save();
                if (type == REW) c.scale(-1, 1, 12, 12);
                c.drawArc(new RectF(4, 4, 20, 20), -60, 270, false, p);
                path.moveTo(20, 2.5f);
                path.lineTo(20, 8);
                path.lineTo(14.5f, 8);
                c.drawPath(path, p);
                c.restore();
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(8f);
                p.setTextAlign(Paint.Align.CENTER);
                c.drawText("10", 12, 14.8f, p);
                break;
            case VOL:
            case MUTE:
                stroke();
                path.moveTo(3, 9);
                path.lineTo(7, 9);
                path.lineTo(12, 5);
                path.lineTo(12, 19);
                path.lineTo(7, 15);
                path.lineTo(3, 15);
                path.close();
                c.drawPath(path, p);
                if (type == VOL) {
                    c.drawArc(new RectF(10, 8, 17, 16), -60, 120, false, p);
                    c.drawArc(new RectF(8, 4.5f, 21, 19.5f), -60, 120, false, p);
                } else {
                    c.drawLine(16, 9, 22, 15, p);
                    c.drawLine(22, 9, 16, 15, p);
                }
                break;
            default:
                break;
        }
        c.restore();
    }

    @Override
    public void setAlpha(int a) {
        p.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        p.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
