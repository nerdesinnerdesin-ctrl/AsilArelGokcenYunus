package com.hapistavla.oyun;


import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Kare menü düğmelerinin simgeleri. Hepsi kod ile çizilir (resim dosyası yok), altın / krem
 * tonlarında, ahşap düğmenin üstünde okunacak şekilde. Her simge kare bir alana sığar.
 */
public final class TileIcons {
    private TileIcons() {}

    public static final int GLOBE = 10;      // çevrimiçi oyna
    public static final int ROBOT = 11;      // bilgisayara karşı
    public static final int CUP = 12;        // maç puanı
    public static final int BOOK = 13;       // kurallar
    public static final int PALETTE = 14;    // tahta teması
    public static final int SPEAKER_ON = 15;
    public static final int SPEAKER_OFF = 16;
    public static final int CHECKER = 17;    // taş stili
    public static final int DICE = 18;       // zar stili
    public static final int AUTO = 19;       // otomatik taş toplama
    public static final int CHAT_ON = 20;
    public static final int CHAT_OFF = 21;
    public static final int TABLE_ADD = 22;  // masa aç
    public static final int TABLE_LIST = 23; // açık masalar
    public static final int BOLT = 24;       // hızlı eşleş
    public static final int LOCK = 25;       // özel oda
    public static final int KEY = 26;        // kodla katıl
    public static final int STAR1 = 27;      // kolay
    public static final int STAR2 = 28;      // orta
    public static final int STAR3 = 29;      // zor
    public static final int MEDAL1 = 31;     // 1 puan
    public static final int MEDAL3 = 33;     // 3 puan
    public static final int MEDAL5 = 35;     // 5 puan

    private static final int GOLD = 0xFFE9C46A;
    private static final int GOLD_D = 0xFFB98B3E;
    private static final int CREAM = 0xFFFFF4D6;
    private static final int RED = 0xFFE5533D;
    private static final int GREEN = 0xFF7BC96F;
    private static final int BLUE = 0xFF5AA9E6;
    private static final int DARK = 0xFF3A2312;

    public static boolean isTile(int kind) {
        return kind >= 10;
    }

    private static void stroke(Paint p, int col, float w) {
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(w);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(col);
    }

    private static void fill(Paint p, int col) {
        p.setShader(null);
        p.setStyle(Paint.Style.FILL);
        p.setColor(col);
    }

    /** kind simgesini area içine çizer. text: rakam çizmek için kullanılan boya (medalyon). */
    public static void draw(Canvas c, RectF area, int kind, Paint p, Paint text) {
        float cx = area.centerX();
        float cy = area.centerY();
        float r = Math.min(area.width(), area.height()) / 2f;
        float lw = Math.max(3f, r * 0.085f);
        if (kind >= 100) {
            medal(c, cx, cy, r, lw, p, text, String.valueOf(kind - 100));
            return;
        }
        switch (kind) {
            case GLOBE: globe(c, cx, cy, r, lw, p); break;
            case ROBOT: robot(c, cx, cy, r, lw, p); break;
            case CUP: cup(c, cx, cy, r, lw, p); break;
            case BOOK: book(c, cx, cy, r, lw, p); break;
            case PALETTE: palette(c, cx, cy, r, lw, p); break;
            case SPEAKER_ON: speaker(c, cx, cy, r, lw, p, true); break;
            case SPEAKER_OFF: speaker(c, cx, cy, r, lw, p, false); break;
            case CHECKER: checker(c, cx, cy, r, lw, p); break;
            case DICE: dice(c, cx, cy, r, lw, p); break;
            case AUTO: auto(c, cx, cy, r, lw, p); break;
            case CHAT_ON: chat(c, cx, cy, r, lw, p, true); break;
            case CHAT_OFF: chat(c, cx, cy, r, lw, p, false); break;
            case TABLE_ADD: tableAdd(c, cx, cy, r, lw, p); break;
            case TABLE_LIST: tableList(c, cx, cy, r, lw, p); break;
            case BOLT: bolt(c, cx, cy, r, lw, p); break;
            case LOCK: lock(c, cx, cy, r, lw, p); break;
            case KEY: key(c, cx, cy, r, lw, p); break;
            case STAR1: stars(c, cx, cy, r, p, 1); break;
            case STAR2: stars(c, cx, cy, r, p, 2); break;
            case STAR3: stars(c, cx, cy, r, p, 3); break;
            case MEDAL1: medal(c, cx, cy, r, lw, p, text, "1"); break;
            case MEDAL3: medal(c, cx, cy, r, lw, p, text, "3"); break;
            case MEDAL5: medal(c, cx, cy, r, lw, p, text, "5"); break;
            default: break;
        }
    }

    // ------------------------------------------------------------------

    private static void globe(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        float R = r * 0.78f;
        fill(p, 0x33FFFFFF);
        c.drawCircle(cx, cy, R, p);
        stroke(p, GOLD, lw);
        c.drawCircle(cx, cy, R, p);
        RectF o = new RectF(cx - R * 0.46f, cy - R, cx + R * 0.46f, cy + R);
        stroke(p, GOLD, lw * 0.75f);
        c.drawOval(o, p);
        c.drawLine(cx, cy - R, cx, cy + R, p);
        c.drawLine(cx - R, cy, cx + R, cy, p);
        float dy = R * 0.5f;
        float hw = (float) Math.sqrt(R * R - dy * dy);
        c.drawLine(cx - hw, cy - dy, cx + hw, cy - dy, p);
        c.drawLine(cx - hw, cy + dy, cx + hw, cy + dy, p);
        // sinyal noktası: çevrimiçi
        fill(p, GREEN);
        c.drawCircle(cx + R * 0.78f, cy - R * 0.78f, r * 0.17f, p);
        stroke(p, DARK, lw * 0.6f);
        c.drawCircle(cx + R * 0.78f, cy - R * 0.78f, r * 0.17f, p);
    }

    private static void robot(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        RectF head = new RectF(cx - r * 0.62f, cy - r * 0.38f, cx + r * 0.62f, cy + r * 0.62f);
        fill(p, 0x33FFFFFF);
        c.drawRoundRect(head, r * 0.22f, r * 0.22f, p);
        stroke(p, GOLD, lw);
        c.drawRoundRect(head, r * 0.22f, r * 0.22f, p);
        // anten
        c.drawLine(cx, head.top, cx, cy - r * 0.68f, p);
        fill(p, RED);
        c.drawCircle(cx, cy - r * 0.74f, r * 0.11f, p);
        // kulaklar
        stroke(p, GOLD, lw);
        c.drawLine(head.left - r * 0.12f, cy + r * 0.05f, head.left - r * 0.12f, cy + r * 0.3f, p);
        c.drawLine(head.right + r * 0.12f, cy + r * 0.05f, head.right + r * 0.12f, cy + r * 0.3f, p);
        // gözler
        fill(p, CREAM);
        c.drawCircle(cx - r * 0.26f, cy + r * 0.02f, r * 0.13f, p);
        c.drawCircle(cx + r * 0.26f, cy + r * 0.02f, r * 0.13f, p);
        fill(p, DARK);
        c.drawCircle(cx - r * 0.26f, cy + r * 0.02f, r * 0.055f, p);
        c.drawCircle(cx + r * 0.26f, cy + r * 0.02f, r * 0.055f, p);
        // ağız ızgarası
        stroke(p, CREAM, lw * 0.7f);
        float my = cy + r * 0.38f;
        c.drawLine(cx - r * 0.3f, my, cx + r * 0.3f, my, p);
        for (int i = -1; i <= 1; i++) {
            c.drawLine(cx + i * r * 0.15f, my - r * 0.08f, cx + i * r * 0.15f, my + r * 0.08f, p);
        }
    }

    private static void cup(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        Path cp = new Path();
        float top = cy - r * 0.62f;
        float bot = cy + r * 0.05f;
        cp.moveTo(cx - r * 0.5f, top);
        cp.lineTo(cx + r * 0.5f, top);
        cp.cubicTo(cx + r * 0.5f, cy - r * 0.18f, cx + r * 0.28f, bot, cx, bot + r * 0.04f);
        cp.cubicTo(cx - r * 0.28f, bot, cx - r * 0.5f, cy - r * 0.18f, cx - r * 0.5f, top);
        cp.close();
        fill(p, GOLD);
        c.drawPath(cp, p);
        stroke(p, GOLD_D, lw * 0.7f);
        c.drawPath(cp, p);
        // kulplar
        stroke(p, GOLD, lw);
        RectF l = new RectF(cx - r * 0.82f, top + r * 0.1f, cx - r * 0.38f, top + r * 0.62f);
        RectF rr = new RectF(cx + r * 0.38f, top + r * 0.1f, cx + r * 0.82f, top + r * 0.62f);
        c.drawArc(l, 90, 180, false, p);
        c.drawArc(rr, -90, 180, false, p);
        // sap ve taban
        fill(p, GOLD);
        c.drawRect(cx - r * 0.08f, bot + r * 0.04f, cx + r * 0.08f, cy + r * 0.38f, p);
        RectF base = new RectF(cx - r * 0.42f, cy + r * 0.38f, cx + r * 0.42f, cy + r * 0.62f);
        c.drawRoundRect(base, r * 0.06f, r * 0.06f, p);
        // yıldız
        star(c, cx, top + r * 0.36f, r * 0.2f, p, DARK);
    }

    private static void star(Canvas c, float x, float y, float R, Paint p, int col) {
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
        fill(p, col);
        c.drawPath(s, p);
    }

    private static void book(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        float w = r * 0.9f;
        float t = cy - r * 0.5f;
        float b = cy + r * 0.5f;
        Path left = new Path();
        left.moveTo(cx, t + r * 0.08f);
        left.cubicTo(cx - w * 0.35f, t - r * 0.08f, cx - w * 0.8f, t - r * 0.02f, cx - w, t + r * 0.06f);
        left.lineTo(cx - w, b);
        left.cubicTo(cx - w * 0.8f, b - r * 0.08f, cx - w * 0.35f, b - r * 0.14f, cx, b + r * 0.02f);
        left.close();
        Path right = new Path();
        right.moveTo(cx, t + r * 0.08f);
        right.cubicTo(cx + w * 0.35f, t - r * 0.08f, cx + w * 0.8f, t - r * 0.02f, cx + w, t + r * 0.06f);
        right.lineTo(cx + w, b);
        right.cubicTo(cx + w * 0.8f, b - r * 0.08f, cx + w * 0.35f, b - r * 0.14f, cx, b + r * 0.02f);
        right.close();
        fill(p, CREAM);
        c.drawPath(left, p);
        c.drawPath(right, p);
        stroke(p, GOLD_D, lw * 0.8f);
        c.drawPath(left, p);
        c.drawPath(right, p);
        stroke(p, GOLD_D, lw * 0.6f);
        for (int i = 0; i < 3; i++) {
            float y = t + r * (0.3f + i * 0.2f);
            c.drawLine(cx - w * 0.82f, y, cx - w * 0.16f, y + r * 0.03f, p);
            c.drawLine(cx + w * 0.16f, y + r * 0.03f, cx + w * 0.82f, y, p);
        }
        // yer imi
        fill(p, RED);
        Path m = new Path();
        m.moveTo(cx + w * 0.45f, b - r * 0.02f);
        m.lineTo(cx + w * 0.45f, b + r * 0.28f);
        m.lineTo(cx + w * 0.55f, b + r * 0.2f);
        m.lineTo(cx + w * 0.65f, b + r * 0.28f);
        m.lineTo(cx + w * 0.65f, b - r * 0.06f);
        m.close();
        c.drawPath(m, p);
    }

    private static void palette(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        Path pl = new Path();
        pl.moveTo(cx - r * 0.1f, cy - r * 0.72f);
        pl.cubicTo(cx + r * 0.7f, cy - r * 0.78f, cx + r * 1.0f, cy - r * 0.05f, cx + r * 0.7f, cy + r * 0.38f);
        pl.cubicTo(cx + r * 0.55f, cy + r * 0.6f, cx + r * 0.25f, cy + r * 0.45f, cx + r * 0.12f, cy + r * 0.55f);
        pl.cubicTo(cx - r * 0.02f, cy + r * 0.7f, cx - r * 0.25f, cy + r * 0.8f, cx - r * 0.55f, cy + r * 0.62f);
        pl.cubicTo(cx - r * 1.1f, cy + r * 0.3f, cx - r * 1.0f, cy - r * 0.6f, cx - r * 0.1f, cy - r * 0.72f);
        pl.close();
        fill(p, 0xFFD9A55B);
        c.drawPath(pl, p);
        stroke(p, GOLD_D, lw * 0.9f);
        c.drawPath(pl, p);
        // başparmak deliği
        fill(p, 0xFF5A381C);
        c.drawCircle(cx + r * 0.22f, cy + r * 0.2f, r * 0.13f, p);
        // boya lekeleri
        fill(p, RED);
        c.drawCircle(cx - r * 0.5f, cy - r * 0.05f, r * 0.14f, p);
        fill(p, 0xFFF2D43B);
        c.drawCircle(cx - r * 0.2f, cy - r * 0.42f, r * 0.14f, p);
        fill(p, GREEN);
        c.drawCircle(cx + r * 0.22f, cy - r * 0.42f, r * 0.14f, p);
        fill(p, BLUE);
        c.drawCircle(cx + r * 0.52f, cy - r * 0.08f, r * 0.14f, p);
    }

    private static void speaker(Canvas c, float cx, float cy, float r, float lw, Paint p, boolean on) {
        float x0 = cx - r * 0.78f;
        Path sp = new Path();
        sp.moveTo(x0, cy - r * 0.22f);
        sp.lineTo(x0 + r * 0.34f, cy - r * 0.22f);
        sp.lineTo(x0 + r * 0.82f, cy - r * 0.62f);
        sp.lineTo(x0 + r * 0.82f, cy + r * 0.62f);
        sp.lineTo(x0 + r * 0.34f, cy + r * 0.22f);
        sp.lineTo(x0, cy + r * 0.22f);
        sp.close();
        fill(p, GOLD);
        c.drawPath(sp, p);
        stroke(p, GOLD_D, lw * 0.7f);
        c.drawPath(sp, p);
        float wx = x0 + r * 0.9f;
        if (on) {
            stroke(p, CREAM, lw);
            for (int i = 1; i <= 3; i++) {
                float rr = r * 0.26f * i;
                RectF a = new RectF(wx - rr, cy - rr, wx + rr, cy + rr);
                c.drawArc(a, -42, 84, false, p);
            }
        } else {
            stroke(p, RED, lw * 1.25f);
            float d = r * 0.3f;
            c.drawLine(wx + r * 0.1f, cy - d, wx + r * 0.1f + d * 1.6f, cy + d, p);
            c.drawLine(wx + r * 0.1f, cy + d, wx + r * 0.1f + d * 1.6f, cy - d, p);
        }
    }

    private static void checker(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        float R = r * 0.74f;
        // altta siyah, üstte beyaz taş
        fill(p, 0x55000000);
        c.drawCircle(cx + r * 0.05f, cy + r * 0.11f, R, p);
        fill(p, 0xFF2B1B12);
        c.drawCircle(cx + r * 0.2f, cy + r * 0.2f, R * 0.82f, p);
        stroke(p, GOLD_D, lw * 0.7f);
        c.drawCircle(cx + r * 0.2f, cy + r * 0.2f, R * 0.82f, p);
        fill(p, CREAM);
        c.drawCircle(cx - r * 0.12f, cy - r * 0.12f, R * 0.82f, p);
        stroke(p, GOLD_D, lw * 0.8f);
        c.drawCircle(cx - r * 0.12f, cy - r * 0.12f, R * 0.82f, p);
        stroke(p, 0xFFD9C79A, lw * 0.7f);
        c.drawCircle(cx - r * 0.12f, cy - r * 0.12f, R * 0.52f, p);
        c.drawCircle(cx - r * 0.12f, cy - r * 0.12f, R * 0.26f, p);
    }

    private static void pips(Canvas c, float x, float y, float s, Paint p, int n) {
        float d = s * 0.25f;
        float rr = s * 0.075f;
        fill(p, DARK);
        float[][] pos = {{0, 0}, {-d, -d}, {d, d}, {d, -d}, {-d, d}, {-d, 0}, {d, 0}};
        int[][] set = {{0}, {1, 2}, {0, 1, 2}, {1, 2, 3, 4}, {0, 1, 2, 3, 4}, {1, 2, 3, 4, 5, 6}};
        int[] pp = set[n - 1];
        for (int i = 0; i < pp.length; i++) {
            c.drawCircle(x + pos[pp[i]][0], y + pos[pp[i]][1], rr, p);
        }
    }

    private static void die(Canvas c, float x, float y, float s, Paint p, int n, int body) {
        RectF d = new RectF(x - s / 2f, y - s / 2f, x + s / 2f, y + s / 2f);
        fill(p, 0x55000000);
        RectF sh = new RectF(d.left + s * 0.05f, d.top + s * 0.07f, d.right + s * 0.05f, d.bottom + s * 0.07f);
        c.drawRoundRect(sh, s * 0.2f, s * 0.2f, p);
        fill(p, body);
        c.drawRoundRect(d, s * 0.2f, s * 0.2f, p);
        stroke(p, GOLD_D, Math.max(2f, s * 0.035f));
        c.drawRoundRect(d, s * 0.2f, s * 0.2f, p);
        pips(c, x, y, s, p, n);
    }

    private static void dice(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        c.save();
        c.rotate(-14f, cx - r * 0.3f, cy + r * 0.12f);
        die(c, cx - r * 0.3f, cy + r * 0.12f, r * 1.0f, p, 5, CREAM);
        c.restore();
        c.save();
        c.rotate(16f, cx + r * 0.36f, cy - r * 0.2f);
        die(c, cx + r * 0.36f, cy - r * 0.2f, r * 0.9f, p, 3, 0xFFF6E3A8);
        c.restore();
    }

    private static void auto(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        // dairesel oklar + ortada taş: taşlar kendiliğinden toplanır
        float R = r * 0.74f;
        RectF a = new RectF(cx - R, cy - R, cx + R, cy + R);
        stroke(p, GOLD, lw * 1.2f);
        c.drawArc(a, 200, 130, false, p);
        c.drawArc(a, 20, 130, false, p);
        // ok uçları
        fill(p, GOLD);
        arrowHead(c, cx, cy, R, 330f, true, r * 0.26f, p);
        arrowHead(c, cx, cy, R, 150f, true, r * 0.26f, p);
        // ortadaki taş
        fill(p, CREAM);
        c.drawCircle(cx, cy, r * 0.34f, p);
        stroke(p, GOLD_D, lw * 0.7f);
        c.drawCircle(cx, cy, r * 0.34f, p);
        stroke(p, GREEN, lw * 0.95f);
        Path ck = new Path();
        ck.moveTo(cx - r * 0.15f, cy + r * 0.0f);
        ck.lineTo(cx - r * 0.03f, cy + r * 0.13f);
        ck.lineTo(cx + r * 0.17f, cy - r * 0.12f);
        c.drawPath(ck, p);
    }

    private static void arrowHead(Canvas c, float cx, float cy, float R, float deg, boolean cw, float sz, Paint p) {
        double a = Math.toRadians(deg);
        float x = cx + (float) Math.cos(a) * R;
        float y = cy + (float) Math.sin(a) * R;
        double tan = a + (cw ? Math.PI / 2 : -Math.PI / 2);
        float tx = (float) Math.cos(tan);
        float ty = (float) Math.sin(tan);
        float nx = -ty;
        float ny = tx;
        Path h = new Path();
        h.moveTo(x + tx * sz * 0.9f, y + ty * sz * 0.9f);
        h.lineTo(x + nx * sz * 0.6f, y + ny * sz * 0.6f);
        h.lineTo(x - nx * sz * 0.6f, y - ny * sz * 0.6f);
        h.close();
        c.drawPath(h, p);
    }

    private static void chat(Canvas c, float cx, float cy, float r, float lw, Paint p, boolean on) {
        RectF b = new RectF(cx - r * 0.82f, cy - r * 0.6f, cx + r * 0.82f, cy + r * 0.34f);
        Path bubble = new Path();
        bubble.addRoundRect(b, r * 0.3f, r * 0.3f, Path.Direction.CW);
        Path tail = new Path();
        tail.moveTo(cx - r * 0.38f, b.bottom - r * 0.02f);
        tail.lineTo(cx - r * 0.55f, b.bottom + r * 0.4f);
        tail.lineTo(cx - r * 0.06f, b.bottom - r * 0.02f);
        tail.close();
        fill(p, on ? CREAM : 0xFFB9A98A);
        c.drawPath(bubble, p);
        c.drawPath(tail, p);
        stroke(p, GOLD_D, lw * 0.75f);
        c.drawRoundRect(b, r * 0.3f, r * 0.3f, p);
        fill(p, on ? GOLD_D : 0xFF7A6A4A);
        for (int i = -1; i <= 1; i++) {
            c.drawCircle(cx + i * r * 0.36f, cy - r * 0.13f, r * 0.085f, p);
        }
        if (!on) {
            stroke(p, RED, lw * 1.3f);
            c.drawLine(cx - r * 0.78f, cy - r * 0.72f, cx + r * 0.78f, cy + r * 0.62f, p);
        }
    }

    private static void tableAdd(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        // tavla tahtası (üstten) + artı rozeti
        RectF b = new RectF(cx - r * 0.85f, cy - r * 0.55f, cx + r * 0.85f, cy + r * 0.55f);
        fill(p, 0xFF8A5A2B);
        c.drawRoundRect(b, r * 0.12f, r * 0.12f, p);
        stroke(p, GOLD, lw * 0.9f);
        c.drawRoundRect(b, r * 0.12f, r * 0.12f, p);
        stroke(p, GOLD, lw * 0.7f);
        c.drawLine(cx, b.top, cx, b.bottom, p);
        fill(p, CREAM);
        for (int i = 0; i < 3; i++) {
            Path t = new Path();
            float x0 = b.left + r * 0.16f + i * r * 0.2f;
            t.moveTo(x0, b.top + r * 0.06f);
            t.lineTo(x0 + r * 0.18f, b.top + r * 0.06f);
            t.lineTo(x0 + r * 0.09f, b.top + r * 0.5f);
            t.close();
            c.drawPath(t, p);
        }
        fill(p, 0xFF2B1B12);
        for (int i = 0; i < 3; i++) {
            Path t = new Path();
            float x0 = cx + r * 0.1f + i * r * 0.2f;
            t.moveTo(x0, b.bottom - r * 0.06f);
            t.lineTo(x0 + r * 0.18f, b.bottom - r * 0.06f);
            t.lineTo(x0 + r * 0.09f, b.bottom - r * 0.5f);
            t.close();
            c.drawPath(t, p);
        }
        // artı rozeti
        float bx = cx + r * 0.62f;
        float by = cy + r * 0.5f;
        fill(p, GREEN);
        c.drawCircle(bx, by, r * 0.3f, p);
        stroke(p, DARK, lw * 0.6f);
        c.drawCircle(bx, by, r * 0.3f, p);
        stroke(p, CREAM, lw * 1.2f);
        c.drawLine(bx - r * 0.15f, by, bx + r * 0.15f, by, p);
        c.drawLine(bx, by - r * 0.15f, bx, by + r * 0.15f, p);
    }

    private static void tableList(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        for (int i = 0; i < 3; i++) {
            float y = cy - r * 0.5f + i * r * 0.5f;
            RectF row = new RectF(cx - r * 0.85f, y - r * 0.2f, cx + r * 0.85f, y + r * 0.2f);
            fill(p, i == 0 ? 0x66E9C46A : 0x33FFFFFF);
            c.drawRoundRect(row, r * 0.1f, r * 0.1f, p);
            stroke(p, GOLD, lw * 0.6f);
            c.drawRoundRect(row, r * 0.1f, r * 0.1f, p);
            // iki koltuk (oyuncu) + metin çizgisi
            fill(p, i == 2 ? 0x66FFF4D6 : CREAM);
            c.drawCircle(row.left + r * 0.22f, y, r * 0.1f, p);
            c.drawCircle(row.left + r * 0.46f, y, r * 0.1f, p);
            stroke(p, CREAM, lw * 0.8f);
            c.drawLine(row.left + r * 0.7f, y, row.right - r * 0.2f - (i == 1 ? r * 0.3f : 0f), y, p);
        }
    }

    private static void bolt(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        Path b = new Path();
        b.moveTo(cx + r * 0.22f, cy - r * 0.85f);
        b.lineTo(cx - r * 0.5f, cy + r * 0.1f);
        b.lineTo(cx - r * 0.06f, cy + r * 0.1f);
        b.lineTo(cx - r * 0.24f, cy + r * 0.85f);
        b.lineTo(cx + r * 0.52f, cy - r * 0.2f);
        b.lineTo(cx + r * 0.06f, cy - r * 0.2f);
        b.close();
        fill(p, GOLD);
        c.drawPath(b, p);
        stroke(p, GOLD_D, lw * 0.8f);
        c.drawPath(b, p);
    }

    private static void lock(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        RectF sh = new RectF(cx - r * 0.42f, cy - r * 0.86f, cx + r * 0.42f, cy + r * 0.0f);
        stroke(p, GOLD, lw * 1.6f);
        c.drawArc(sh, 180, 180, false, p);
        c.drawLine(cx - r * 0.42f, cy - r * 0.43f, cx - r * 0.42f, cy - r * 0.05f, p);
        c.drawLine(cx + r * 0.42f, cy - r * 0.43f, cx + r * 0.42f, cy - r * 0.05f, p);
        RectF b = new RectF(cx - r * 0.66f, cy - r * 0.1f, cx + r * 0.66f, cy + r * 0.78f);
        fill(p, GOLD);
        c.drawRoundRect(b, r * 0.14f, r * 0.14f, p);
        stroke(p, GOLD_D, lw * 0.7f);
        c.drawRoundRect(b, r * 0.14f, r * 0.14f, p);
        fill(p, DARK);
        c.drawCircle(cx, cy + r * 0.28f, r * 0.11f, p);
        c.drawRect(cx - r * 0.04f, cy + r * 0.3f, cx + r * 0.04f, cy + r * 0.54f, p);
    }

    private static void key(Canvas c, float cx, float cy, float r, float lw, Paint p) {
        c.save();
        c.rotate(-35f, cx, cy);
        float rx = cx - r * 0.5f;
        stroke(p, GOLD, lw * 1.5f);
        c.drawCircle(rx, cy, r * 0.3f, p);
        fill(p, DARK);
        c.drawCircle(rx, cy, r * 0.09f, p);
        stroke(p, GOLD, lw * 1.5f);
        c.drawLine(rx + r * 0.3f, cy, cx + r * 0.85f, cy, p);
        c.drawLine(cx + r * 0.55f, cy, cx + r * 0.55f, cy + r * 0.3f, p);
        c.drawLine(cx + r * 0.85f, cy, cx + r * 0.85f, cy + r * 0.22f, p);
        c.restore();
        // oda kodu: birkaç harf kutusu
        fill(p, CREAM);
        for (int i = 0; i < 3; i++) {
            RectF k = new RectF(cx - r * 0.64f + i * r * 0.46f, cy + r * 0.62f, cx - r * 0.32f + i * r * 0.46f, cy + r * 0.9f);
            c.drawRoundRect(k, r * 0.06f, r * 0.06f, p);
        }
    }

    private static void stars(Canvas c, float cx, float cy, float r, Paint p, int n) {
        float R = n == 1 ? r * 0.62f : (n == 2 ? r * 0.46f : r * 0.4f);
        float gap = R * 2.1f;
        for (int i = 0; i < n; i++) {
            float x = cx + (i - (n - 1) / 2f) * gap;
            float y = cy + (n == 3 && i == 1 ? -R * 0.35f : (n == 3 ? R * 0.2f : 0f));
            star(c, x, y + R * 0.05f, R, p, GOLD);
            star(c, x, y, R * 0.55f, p, 0xFFFFF0B8);
        }
    }

    private static void medal(Canvas c, float cx, float cy, float r, float lw, Paint p, Paint text, String num) {
        // kurdele
        fill(p, RED);
        Path rb = new Path();
        rb.moveTo(cx - r * 0.42f, cy - r * 0.2f);
        rb.lineTo(cx - r * 0.62f, cy + r * 0.85f);
        rb.lineTo(cx - r * 0.3f, cy + r * 0.66f);
        rb.lineTo(cx - r * 0.08f, cy + r * 0.85f);
        rb.lineTo(cx + r * 0.1f, cy - r * 0.1f);
        rb.close();
        c.drawPath(rb, p);
        Path rb2 = new Path();
        rb2.moveTo(cx + r * 0.42f, cy - r * 0.2f);
        rb2.lineTo(cx + r * 0.62f, cy + r * 0.85f);
        rb2.lineTo(cx + r * 0.3f, cy + r * 0.66f);
        rb2.lineTo(cx + r * 0.08f, cy + r * 0.85f);
        rb2.lineTo(cx - r * 0.1f, cy - r * 0.1f);
        rb2.close();
        fill(p, 0xFFC13E2B);
        c.drawPath(rb2, p);
        // madalyon
        float R = r * 0.58f;
        float my = cy - r * 0.18f;
        fill(p, GOLD);
        c.drawCircle(cx, my, R, p);
        stroke(p, GOLD_D, lw);
        c.drawCircle(cx, my, R, p);
        stroke(p, 0xFFFFF0B8, lw * 0.6f);
        c.drawCircle(cx, my, R * 0.78f, p);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(DARK);
        text.setTextSize(R * (num.length() == 1 ? 1.25f : (num.length() == 2 ? 0.95f : 0.72f)));
        float yy = my - (text.descent() + text.ascent()) / 2f;
        c.drawText(num, cx, yy, text);
    }
}
