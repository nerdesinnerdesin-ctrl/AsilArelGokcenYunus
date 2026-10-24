package com.hapistavla.oyun;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Sıra süresi halkası: bir kutunun çevresinde, üst ortadan saat yönünde kalan süre kadar çizilir. */
public final class TurnRing {
    private TurnRing() {
    }

    public static int color(float frac) {
        return frac > 0.33f ? 0xFF78D25A : (frac > 0.166f ? 0xFFF0C83C : 0xFFE63C32);
    }

    /** Yanıp sönme: 0..1 arası değer; son 10 saniyede hızlı. */
    public static float pulse(float frac) {
        long per = frac >= 0f && frac < 0.166f ? 350L : 1000L;
        float ph = (System.currentTimeMillis() % per) / (float) per;
        return 0.5f + 0.5f * (float) Math.sin(ph * 2 * Math.PI);
    }

    public static void draw(Canvas c, Paint paint, RectF r, float frac, float pad, float rad, float stroke) {
        
        float x0 = r.left - pad;
        float y0 = r.top - pad;
        float x1 = r.right + pad;
        float y1 = r.bottom + pad;
        float hw = (x1 - x0) / 2f - rad;
        float hh = (y1 - y0) / 2f - rad;
        float arc = (float) Math.PI * rad / 2f;
        float per = 4f * hw + 4f * hh + 4f * arc;
        float cxm = (x0 + x1) / 2f;
        float cym = (y0 + y1) / 2f;
        int col = frac > 0.33f ? 0xFF78D25A : (frac > 0.166f ? 0xFFF0C83C : 0xFFE63C32);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(0x66000000);
        c.drawRoundRect(new RectF(x0, y0, x1, y1), rad + stroke * 0.25f, rad + stroke * 0.25f, paint);
        paint.setColor(col);
        Path p = new Path();
        int n = Math.max(2, (int) (per * frac / Math.max(3f, stroke * 0.75f)));
        for (int i = 0; i <= n; i++) {
            float d = per * frac * i / n;
            float[] pt = ringPoint(d, hw, hh, rad, arc, cxm, cym);
            if (i == 0) {
                p.moveTo(pt[0], pt[1]);
            } else {
                p.lineTo(pt[0], pt[1]);
            }
        }
        c.drawPath(p, paint);
    }

    private static float[] ringPoint(float d, float hw, float hh, float rad, float arc, float cx, float cy) {
        // üst orta -> sağ üst köşe -> sağ kenar -> sağ alt köşe -> alt -> sol alt -> sol -> sol üst -> üst sol yarı
        if (d < hw) {
            return new float[] {cx + d, cy - hh - rad};
        }
        d -= hw;
        if (d < arc) {
            double a = d / arc * Math.PI / 2;
            return new float[] {cx + hw + (float) Math.sin(a) * rad, cy - hh - (float) Math.cos(a) * rad};
        }
        d -= arc;
        if (d < 2 * hh) {
            return new float[] {cx + hw + rad, cy - hh + d};
        }
        d -= 2 * hh;
        if (d < arc) {
            double a = d / arc * Math.PI / 2;
            return new float[] {cx + hw + (float) Math.cos(a) * rad, cy + hh + (float) Math.sin(a) * rad};
        }
        d -= arc;
        if (d < 2 * hw) {
            return new float[] {cx + hw - d, cy + hh + rad};
        }
        d -= 2 * hw;
        if (d < arc) {
            double a = d / arc * Math.PI / 2;
            return new float[] {cx - hw - (float) Math.sin(a) * rad, cy + hh + (float) Math.cos(a) * rad};
        }
        d -= arc;
        if (d < 2 * hh) {
            return new float[] {cx - hw - rad, cy + hh - d};
        }
        d -= 2 * hh;
        if (d < arc) {
            double a = d / arc * Math.PI / 2;
            return new float[] {cx - hw - (float) Math.cos(a) * rad, cy - hh - (float) Math.sin(a) * rad};
        }
        d -= arc;
        return new float[] {cx - hw + d, cy - hh - rad};
    }
}
