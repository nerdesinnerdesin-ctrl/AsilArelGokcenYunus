package com.hapistavla.oyun.salon;

import com.hapistavla.oyun.*;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Kağıt oyunları ve okey için çizim yardımcıları: iskambil kartı, kart arkası, okey taşı ve
 * takım işaretleri (yazı tipine bağlı olmasın diye şekiller kodla çizilir).
 */
public final class CardArt {
    private CardArt() {}

    public static final int SPADES = 0;
    public static final int HEARTS = 1;
    public static final int DIAMONDS = 2;
    public static final int CLUBS = 3;

    public static final String[] SUIT_NAMES = {"MAÇA", "KUPA", "KARO", "SİNEK"};

    public static final int INK = 0xFF1B1B1F;
    public static final int RED = 0xFFD2362A;
    public static final int PAPER = 0xFFFBF7EA;

    public static boolean isRed(int suit) {
        return suit == HEARTS || suit == DIAMONDS;
    }

    public static String rankName(int rank) {
        switch (rank) {
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            case 14: return "A";
            default: return String.valueOf(rank);
        }
    }

    /** Takım işaretini (cx, cy) merkezli, yaklaşık s yüksekliğinde çizer. */
    public static void suit(Canvas c, int suit, float cx, float cy, float s, int color, Paint p) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        float h = s / 2f;
        Path path = new Path();
        switch (suit) {
            case HEARTS: {
                path.moveTo(cx, cy + h);
                path.cubicTo(cx - s * 0.95f, cy + h * 0.05f, cx - s * 0.5f, cy - h * 1.05f, cx, cy - h * 0.35f);
                path.cubicTo(cx + s * 0.5f, cy - h * 1.05f, cx + s * 0.95f, cy + h * 0.05f, cx, cy + h);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case DIAMONDS: {
                path.moveTo(cx, cy - h);
                path.lineTo(cx + s * 0.38f, cy);
                path.lineTo(cx, cy + h);
                path.lineTo(cx - s * 0.38f, cy);
                path.close();
                c.drawPath(path, p);
                break;
            }
            case SPADES: {
                path.moveTo(cx, cy - h);
                path.cubicTo(cx + s * 0.2f, cy - h * 0.55f, cx + s * 0.95f, cy - h * 0.1f, cx + s * 0.55f, cy + h * 0.45f);
                path.cubicTo(cx + s * 0.35f, cy + h * 0.7f, cx + s * 0.08f, cy + h * 0.5f, cx + s * 0.06f, cy + h * 0.38f);
                path.cubicTo(cx + s * 0.08f, cy + h * 0.7f, cx + s * 0.16f, cy + h * 0.9f, cx + s * 0.3f, cy + h);
                path.lineTo(cx - s * 0.3f, cy + h);
                path.cubicTo(cx - s * 0.16f, cy + h * 0.9f, cx - s * 0.08f, cy + h * 0.7f, cx - s * 0.06f, cy + h * 0.38f);
                path.cubicTo(cx - s * 0.08f, cy + h * 0.5f, cx - s * 0.35f, cy + h * 0.7f, cx - s * 0.55f, cy + h * 0.45f);
                path.cubicTo(cx - s * 0.95f, cy - h * 0.1f, cx - s * 0.2f, cy - h * 0.55f, cx, cy - h);
                path.close();
                c.drawPath(path, p);
                break;
            }
            default: {
                float r = s * 0.23f;
                c.drawCircle(cx, cy - h * 0.42f, r, p);
                c.drawCircle(cx - r * 1.1f, cy + h * 0.1f, r, p);
                c.drawCircle(cx + r * 1.1f, cy + h * 0.1f, r, p);
                c.drawCircle(cx, cy - h * 0.05f, r * 0.8f, p);
                path.moveTo(cx - s * 0.05f, cy);
                path.lineTo(cx + s * 0.05f, cy);
                path.lineTo(cx + s * 0.2f, cy + h);
                path.lineTo(cx - s * 0.2f, cy + h);
                path.close();
                c.drawPath(path, p);
                break;
            }
        }
    }

    /** İskambil kartı (ön yüz). dim: oynanamaz kart (soluk). */
    public static void cardFace(Canvas c, RectF r, int rank, int suit, boolean dim, Paint p, Paint text) {
        float w = r.width();
        float rad = w * 0.11f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(new RectF(r.left + w * 0.03f, r.top + w * 0.05f, r.right + w * 0.03f, r.bottom + w * 0.05f),
                rad, rad, p);
        p.setColor(PAPER);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, w * 0.025f));
        p.setColor(0xFFA09680);
        c.drawRoundRect(r, rad, rad, p);
        int col = isRed(suit) ? RED : INK;
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(col);
        text.setTextSize(w * 0.3f);
        String rn = rankName(rank);
        float tw = text.measureText(rn);
        if (tw > w * 0.34f) {
            text.setTextSize(w * 0.3f * (w * 0.34f) / tw);
        }
        float ix = r.left + w * 0.2f;
        c.drawText(rn, ix, r.top + w * 0.36f, text);
        suit(c, suit, ix, r.top + w * 0.55f, w * 0.2f, col, p);
        // ortada büyük takım işareti; J Q K için harf süsü
        float bigS = w * 0.52f;
        suit(c, suit, r.centerX() + w * 0.06f, r.centerY() + w * 0.2f, bigS, col, p);
        if (rank >= 11 && rank <= 13) {
            text.setTextSize(w * 0.4f);
            text.setColor((col & 0x00FFFFFF) | 0x55000000);
            c.drawText(rankName(rank), r.centerX() + w * 0.06f, r.centerY() - w * 0.02f, text);
        }
        if (dim) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x88281C12);
            c.drawRoundRect(r, rad, rad, p);
        }
    }

    // ---------------- okey taşı ----------------

    public static final int[] OKEY_COLORS = {0xFFE0A015, 0xFF2D68C4, 0xFF222222, 0xFFD6342A};
    public static final String[] OKEY_COLOR_NAMES = {"SARI", "MAVİ", "SİYAH", "KIRMIZI"};

    /** Okey taşı (ön yüz). num: 1..13; fake: sahte okey (yıldız). */
    public static void okeyFace(Canvas c, RectF r, int num, int color, boolean fake, boolean dim, Paint p, Paint text) {
        float w = r.width();
        float h = r.height();
        float rad = w * 0.16f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(new RectF(r.left + w * 0.04f, r.top + w * 0.07f, r.right + w * 0.04f, r.bottom + w * 0.07f),
                rad, rad, p);
        p.setColor(0xFFFAF3E1);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, w * 0.035f));
        p.setColor(0xFFBEAA82);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFEFE5CB);
        c.drawRoundRect(new RectF(r.left + w * 0.07f, r.top + h * 0.55f, r.right - w * 0.07f, r.bottom - h * 0.06f),
                rad * 0.7f, rad * 0.7f, p);
        int col = OKEY_COLORS[color & 3];
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(col);
        if (fake) {
            drawStar(c, r.centerX(), r.top + h * 0.34f, w * 0.34f, col, p);
        } else {
            String s = String.valueOf(num);
            text.setTextSize(w * (num >= 10 ? 0.52f : 0.62f));
            float ty = r.top + h * 0.34f - (text.descent() + text.ascent()) / 2f;
            c.drawText(s, r.centerX(), ty, text);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, w * 0.06f));
        p.setColor(col);
        float cy = r.top + h * 0.76f;
        c.drawCircle(r.centerX(), cy, w * 0.14f, p);
        if (dim) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(0x88281C12);
            c.drawRoundRect(r, rad, rad, p);
        }
    }

    public static void drawStar(Canvas c, float x, float y, float R, int col, Paint p) {
        Path s = new Path();
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float rad = (i % 2 == 0) ? R : R * 0.42f;
            float px = x + (float) Math.cos(a) * rad;
            float py = y + (float) Math.sin(a) * rad;
            if (i == 0) {
                s.moveTo(px, py);
            } else {
                s.lineTo(px, py);
            }
        }
        s.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
        c.drawPath(s, p);
    }

    /** Okey taşı arkası (deste / kapalı taş). */
    public static void okeyBack(Canvas c, RectF r, Paint p) {
        float w = r.width();
        float rad = w * 0.16f;
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x66000000);
        c.drawRoundRect(new RectF(r.left + w * 0.04f, r.top + w * 0.07f, r.right + w * 0.04f, r.bottom + w * 0.07f),
                rad, rad, p);
        p.setColor(0xFFEADFC2);
        c.drawRoundRect(r, rad, rad, p);
        p.setColor(0xFFC9B68A);
        c.drawRoundRect(new RectF(r.left + w * 0.1f, r.top + r.height() * 0.08f, r.right - w * 0.1f,
                r.bottom - r.height() * 0.08f), rad * 0.7f, rad * 0.7f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, w * 0.03f));
        p.setColor(0xFFA38F60);
        c.drawRoundRect(r, rad, rad, p);
    }
}
