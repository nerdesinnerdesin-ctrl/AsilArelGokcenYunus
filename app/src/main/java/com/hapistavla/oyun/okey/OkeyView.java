package com.hapistavla.oyun.okey;

import com.hapistavla.oyun.*;
import com.hapistavla.oyun.salon.CardArt;
import com.hapistavla.oyun.tavla.Game;
import com.hapistavla.oyun.salon.SalonBase;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Okey masası: 3 bilgisayar rakip, 2 sıralı ıstaka, deste, gösterge, atılan taş kutuları. */
public final class OkeyView extends SalonBase {
    private static final int K_EL = K_CUSTOM + 1;
    private static final int K_SCORES = K_CUSTOM + 2;

    private static final int B_ARR = 40;
    private static final int B_PAIR = 44;
    private static final int B_FIN = 41;
    private static final int B_NEXT = 42;
    private static final int B_ELOK = 43;

    public static final int SLOTS = 30;
    public static final int PER_ROW = 15;
    private float HANDLE_H = 20f;
    private final List<float[]> handleR = new ArrayList<float[]>();   // {sol, üst, sağ, alt, ilk göz, son göz}
    private int[] dragTiles = null;                                    // sürüklenen perin taşları

    private final int start;
    public Okey ok;
    private final int[] slot = new int[SLOTS];
    private int sel = -1;
    private boolean resultSent = false;
    private final RectF[] slotR = new RectF[SLOTS];
    private final RectF[] discR = new RectF[4];
    private final RectF deckR = new RectF();
    private float tw;
    private float th;

    public OkeyView(Context ctx, int start) {
        super(ctx, false, "OKEY");
        this.start = start;
        for (int i = 0; i < SLOTS; i++) {
            slotR[i] = new RectF();
        }
        for (int i = 0; i < 4; i++) {
            discR[i] = new RectF();
        }
    }

    public void start() {
        setupOffline();
        newMatch(new Random().nextLong());
    }

    public void startOnline(Link l, int mySeat, String[] nm, int[] lv, String[] cd, long sd) {
        setupOnline(l, mySeat, nm, lv, cd, sd);
        newMatch(sd);
    }

    private void newMatch(long sd) {
        cancelTimers();
        closeModal();
        ok = new Okey(start, new Random(sd));
        resultSent = false;
        newEl();
    }

    private void newEl() {
        ok.newEl();
        sel = -1;
        for (int i = 0; i < SLOTS; i++) {
            slot[i] = -1;
        }
        List<Integer> h = ok.hand[me];
        List<List<Integer>> g = ok.arrange(h);
        layout(g);
        banner = names[ok.dealer] + " dağıttı";
        step();
    }

    /** Grupları ıstakaya dizer: gruplar arasında bir boşluk, sığmayan grup alt sıraya geçer. */
    private void layout(List<List<Integer>> groups) {
        for (int i = 0; i < SLOTS; i++) {
            slot[i] = -1;
        }
        int pos = 0;
        for (int gi = 0; gi < groups.size(); gi++) {
            List<Integer> g = groups.get(gi);
            if (g.isEmpty()) {
                continue;
            }
            if (gi == groups.size() - 1 && groups.size() > 1 && pos < PER_ROW) {
                pos = PER_ROW;
            }
            int col = pos % PER_ROW;
            if (col != 0 && col + g.size() > PER_ROW) {
                pos = (pos / PER_ROW + 1) * PER_ROW;
            }
            for (int t : g) {
                if (pos < SLOTS) {
                    slot[pos++] = t;
                }
            }
            pos++;
        }
        // sığmayanları boş yerlere koy
        List<Integer> placed = new ArrayList<Integer>();
        for (int i = 0; i < SLOTS; i++) {
            if (slot[i] >= 0) {
                placed.add(slot[i]);
            }
        }
        for (int t : ok.hand[me]) {
            if (!placed.contains(t)) {
                putFirstEmpty(t);
            }
        }
    }

    /** Çekilen taşı ıstakanın en sağ boş yerine koyar; yeri döndürür (oyuncu istediği boşluğa taşır). */
    private int putNew(int t) {
        for (int i = SLOTS - 1; i >= 0; i--) {
            if (slot[i] < 0) {
                slot[i] = t;
                return i;
            }
        }
        return -1;
    }

    private void putFirstEmpty(int t) {
        for (int i = 0; i < SLOTS; i++) {
            if (slot[i] < 0) {
                slot[i] = t;
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Akış
    // ------------------------------------------------------------------

    private boolean paused() {
        if (online()) {
            return false;
        }
        return modalKind == K_MENU || modalKind == K_RULES || modalKind == K_EXIT || modalKind == K_SCORES;
    }

    private void delayed(final int tk, final Runnable r, final long ms) {
        later(new Runnable() {
            @Override
            public void run() {
                if (tk != token) {
                    return;
                }
                if (paused()) {
                    delayed(tk, r, 300);
                } else {
                    r.run();
                }
            }
        }, ms);
    }

    @Override
    public void onSeatChanged() {
        step();
    }

    public void step() {
        invalidate();
        final int tk = ++token;
        if (ok == null) {
            return;
        }
        if (ok.phase == Okey.EL_OVER || ok.phase == Okey.MATCH_OVER) {
            clearTimer();
        }
        if (ok.phase == Okey.EL_OVER) {
            delayed(tk, new Runnable() {
                @Override
                public void run() {
                    if (ok.phase == Okey.EL_OVER && modalKind != K_EL) {
                        openElOver();
                    }
                }
            }, 900);
            if (online() && authority()) {
                delayed(tk, new Runnable() {
                    @Override
                    public void run() {
                        submit(me, "N");
                    }
                }, 9000);
            }
            return;
        }
        if (ok.phase == Okey.MATCH_OVER) {
            delayed(tk, new Runnable() {
                @Override
                public void run() {
                    if (modalKind != K_CUSTOM + 3) {
                        openMatchOver();
                    }
                }
            }, 900);
            return;
        }
        final int tp = ok.turn;
        armTimer(tk, tp, moveNo, new Runnable() {
            @Override
            public void run() {
                if (ok.turn == tp && (ok.phase == Okey.DRAW || ok.phase == Okey.DISCARD)) {
                    botMove(tp);
                }
            }
        });
        if (ok.turn == me) {
            banner = ok.phase == Okey.DRAW ? "Sıra sende: desteden çek ya da atılan taşı al" : "Bir taş seç ve atılan kutusuna dokun";
            return;
        }
        banner = "";
        if (authority() && botSeat(ok.turn)) {
            final int p = ok.turn;
            if (ok.phase == Okey.DRAW) {
                delayed(tk, new Runnable() {
                    @Override
                    public void run() {
                        submit(p, ok.botTakeDiscard(p) ? "K" : "D");
                    }
                }, 900);
            } else {
                delayed(tk, new Runnable() {
                    @Override
                    public void run() {
                        int ft = ok.botFinishTile(p);
                        if (ft >= 0 && submit(p, "F " + ft)) {
                            return;
                        }
                        submit(p, "X " + ok.botDiscard(p));
                    }
                }, 800);
            }
        }
    }

    /** Botun yapacağı hamle (süre dolan insan oyuncu yerine de kullanılır). */
    private void botMove(int p) {
        if (ok.phase == Okey.DRAW) {
            submit(p, ok.botTakeDiscard(p) ? "K" : "D");
        } else {
            int ft = ok.botFinishTile(p);
            if (ft >= 0 && submit(p, "F " + ft)) {
                return;
            }
            submit(p, "X " + ok.botDiscard(p));
        }
    }

    private void removeSlot(int t) {
        for (int i = 0; i < SLOTS; i++) {
            if (slot[i] == t) {
                slot[i] = -1;
            }
        }
    }

    @Override
    public boolean applyCmd(int seat, String cmd) {
        if (ok == null || cmd.length() == 0) {
            return false;
        }
        String[] f = cmd.split(" ");
        try {
            switch (f[0].charAt(0)) {
                case 'D': {
                    if (ok.phase != Okey.DRAW || ok.turn != seat) {
                        return false;
                    }
                    int t = ok.drawPile();
                    if (t < 0 && ok.phase != Okey.EL_OVER) {
                        return false;
                    }
                    if (t >= 0 && seat == me) {
                        sel = placeNew(t);
                    }
                    sound(Game.SND_MOVE);
                    step();
                    return true;
                }
                case 'K': {
                    if (ok.phase != Okey.DRAW || ok.turn != seat || !ok.canTakeDiscard()) {
                        return false;
                    }
                    int t = ok.takeDiscard();
                    if (t < 0) {
                        return false;
                    }
                    if (seat == me) {
                        sel = placeNew(t);
                    }
                    sound(Game.SND_MOVE);
                    step();
                    if (seat != me) {
                        banner = names[seat] + " atılan taşı aldı";
                    }
                    return true;
                }
                case 'X': {
                    int t = Integer.parseInt(f[1]);
                    if (ok.phase != Okey.DISCARD || ok.turn != seat || !ok.discard(t)) {
                        return false;
                    }
                    if (seat == me) {
                        removeSlot(t);
                        sel = -1;
                    }
                    sound(Game.SND_MOVE);
                    step();
                    return true;
                }
                case 'F': {
                    int t = Integer.parseInt(f[1]);
                    if (ok.phase != Okey.DISCARD || ok.turn != seat || !ok.tryFinish(seat, t)) {
                        return false;
                    }
                    if (seat == me) {
                        removeSlot(t);
                        sel = -1;
                    }
                    sound(Game.SND_PIN);
                    step();
                    return true;
                }
                case 'N': {
                    if (ok.phase != Okey.EL_OVER) {
                        return false;
                    }
                    if (modalKind == K_EL) {
                        closeModal();
                    }
                    newEl();
                    return true;
                }
                case 'M': {
                    if (ok.phase != Okey.MATCH_OVER) {
                        return false;
                    }
                    newMatch(Long.parseLong(f[1]));
                    return true;
                }
                default:
                    return false;
            }
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Pencereler
    // ------------------------------------------------------------------

    private void openElOver() {
        String t;
        String sub;
        if (ok.elWinner < 0) {
            t = "EL BERABERE";
            sub = "Deste bitti, puan değişmedi";
        } else {
            t = ok.elWinner == me ? "BİTİRDİN!" : names[ok.elWinner] + " BİTİRDİ";
            sub = (ok.elPairs ? "Çiftle bitirdi · " : "") + (ok.elOkeyFinish ? "Okey atarak bitirdi · " : "")
                    + "rakipler " + (-ok.elDelta[(ok.elWinner + 1) & 3]) + " puan düştü";
            if (!ok.elPairs && !ok.elOkeyFinish) {
                sub = "Rakipler " + (-ok.elDelta[(ok.elWinner + 1) & 3]) + " puan düştü";
            }
        }
        sound(ok.elWinner == me ? Game.SND_WIN : Game.SND_PIN);
        openModal(K_EL, t, sub, online() && !authority() ? new MB(B_ELOK, "TAMAM", true) : new MB(B_NEXT, "SONRAKİ EL", true));
    }

    private void openMatchOver() {
        boolean win = ok.matchWinner == me;
        if (!resultSent) {
            resultSent = true;
            sound(win ? Game.SND_WIN : Game.SND_LOSE);
            if (host != null) {
                if (online()) {
                    host.onSalonOnlineResult(SalonBase.G_OKEY, win, 1);
                } else {
                    host.onSalonResult(SalonBase.G_OKEY, win, 1);
                }
            }
        }
        openModal(K_CUSTOM + 3, win ? "KAZANDIN!" : names[ok.matchWinner] + " KAZANDI",
                "Biri puanını bitirdi · en çok puanı kalan kazanır", new MB(B_REMATCH, online() ? "RÖVANŞ" : "YENİ MAÇ", true),
                new MB(B_EXIT_YES, "SALONA DÖN", false));
    }

    @Override
    public RectF modalRect(int kind) {
        float w = getWidth();
        if (kind >= K_CUSTOM) {
            float mw = Math.min(w * 0.6f, 1000f * S);
            float mh = 640f * S;
            float top = (getHeight() - mh) / 2f;
            return new RectF(w / 2f - mw / 2f, top, w / 2f + mw / 2f, top + mh);
        }
        return super.modalRect(kind);
    }

    @Override
    public void drawCustomModal(Canvas c, int kind, RectF body) {
        boolean delta = kind == K_EL;
        float rowH = Math.min(70f * S, body.height() / 5f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        float x0 = body.left + 20f * S;
        float xs = body.right - 20f * S;
        float xd = body.right - 190f * S;
        leftText(c, "OYUNCU", x0, body.top + rowH * 0.4f, 0xFFC9B58A, 22f * S, 300f * S);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFFC9B58A);
        text.setTextSize(22f * S);
        if (delta) {
            c.drawText("EL", xd, body.top + rowH * 0.4f + 8f * S, text);
        }
        c.drawText("PUAN", xs, body.top + rowH * 0.4f + 8f * S, text);
        for (int p = 0; p < 4; p++) {
            float cy = body.top + rowH * (p + 1.4f);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            leftText(c, names[p], x0, cy, 0xFFFFF4D6, 30f * S, 400f * S);
            text.setTextAlign(Paint.Align.RIGHT);
            if (delta) {
                int d = ok.elDelta[p];
                text.setColor(d < 0 ? 0xFFFF8A80 : 0xFFC9B58A);
                text.setTextSize(32f * S);
                c.drawText(d == 0 ? "—" : String.valueOf(d), xd, cy + 11f * S, text);
            }
            text.setColor(0xFFE9C46A);
            text.setTextSize(34f * S);
            c.drawText(String.valueOf(ok.score[p]), xs, cy + 12f * S, text);
        }
    }

    // ------------------------------------------------------------------
    // Düğmeler ve dokunma
    // ------------------------------------------------------------------

    @Override
    public MB menuExtra() {
        return new MB(B_SCORES, "PUANLAR", false);
    }

    @Override
    public void onGameButton(int id, int arg) {
        switch (id) {
            case B_SCORES:
                openModal(K_SCORES, "PUAN DURUMU", "Başlangıç " + start + " puan", new MB(B_MODAL_BACK, "‹ GERİ", true));
                break;
            case B_NEXT:
                submit(me, "N");
                break;
            case B_ELOK:
                closeModal();
                break;
            case B_REMATCH:
                closeModal();
                if (!online()) {
                    newMatch(new Random().nextLong());
                } else if (authority()) {
                    submit(me, "M " + new Random().nextLong());
                } else {
                    link.req(me, "M");
                    notice("Rövanş istendi…");
                }
                break;
            case B_ARR:
                if (ok.phase == Okey.DRAW || ok.phase == Okey.DISCARD) {
                    layout(ok.arrange(ok.hand[me]));
                    sel = -1;
                }
                break;
            case B_PAIR:
                if (ok.phase == Okey.DRAW || ok.phase == Okey.DISCARD) {
                    layout(ok.arrangePairs(ok.hand[me]));
                    sel = -1;
                }
                break;
            case B_FIN:
                finishTap();
                break;
            default:
                break;
        }
    }

    private void finishTap() {
        if (ok.turn != me || ok.phase != Okey.DISCARD) {
            banner = "Bitirmek için önce taş çekmelisin";
            return;
        }
        int t = sel >= 0 ? slot[sel] : ok.botFinishTile(me);
        if (t < 0 || !ok.canFinish(me, t)) {
            banner = "Elin bitmeye uygun değil";
            return;
        }
        submit(me, "F " + t);
    }

    @Override
    public boolean onGameTap(float x, float y) {
        if (ok == null || (ok.phase != Okey.DRAW && ok.phase != Okey.DISCARD)) {
            return false;
        }
        boolean mine = ok.turn == me;
        if (mine && ok.phase == Okey.DRAW) {
            if (deckR.contains(x, y)) {
                dropAt = -1;
                submit(me, "D");
                return true;
            }
            if (hit(discR[1], x, y)) {
                if (ok.canTakeDiscard()) {
                    submit(me, "K");
                } else {
                    banner = "Alınacak taş yok";
                }
                return true;
            }
        }
        if (mine && ok.phase == Okey.DISCARD && hit(discR[0], x, y)) {
            if (sel >= 0 && slot[sel] >= 0) {
                submit(me, "X " + slot[sel]);
            } else {
                banner = "Önce atacağın taşı seç";
            }
            return true;
        }
        for (int i = 0; i < SLOTS; i++) {
            if (slotR[i].contains(x, y)) {
                if (sel < 0) {
                    if (slot[i] >= 0) {
                        sel = i;
                    }
                } else if (sel == i) {
                    sel = -1;
                } else {
                    int mv = slot[sel];
                    slot[sel] = -1;
                    insertAt(slot, i, mv);
                    sel = -1;
                }
                return true;
            }
        }
        sel = -1;
        return true;
    }


    // ---- sürükleyerek çekme / ıstaka içinde taşıma (araya girerek) ----
    private int dragSrc = 0;          // 0 yok, 1 ıstaka, 2 deste, 3 atılan taş
    private int dragSlot = -1;
    private int dragTile = -1;
    private float dragX;
    private float dragY;
    private boolean dragLive = false;
    private int[] dragPrev = null;    // sürüklerken ıstakanın önizlemesi (-2: taşın bırakılacağı yer)
    private int dropAt = -1;

    private boolean discTakeOk() {
        List<Integer> dl = ok.discards[ok.prev(me)];
        if (dl.isEmpty()) {
            return false;
        }
        if (!ok.canTakeDiscard()) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean dragStart(float x, float y) {
        dragSrc = 0;
        dragLive = false;
        dragPrev = null;
        if (ok == null || (ok.phase != Okey.DRAW && ok.phase != Okey.DISCARD)) {
            return false;
        }
        if (ok.turn == me && ok.phase == Okey.DRAW) {
            if (deckR.contains(x, y)) {
                dragSrc = 2;
                return true;
            }
            if (hit(discR[1], x, y) && discTakeOk()) {
                List<Integer> dl = ok.discards[ok.prev(me)];
                dragTile = dl.get(dl.size() - 1);
                dragSrc = 3;
                return true;
            }
        }
        for (float[] h : handleR) {
            if (x >= h[0] && x <= h[2] && y >= h[1] - 14f * S && y <= h[3] + 4f * S) {
                int s0 = (int) h[4];
                int e0 = (int) h[5];
                dragTiles = new int[e0 - s0 + 1];
                for (int k = 0; k < dragTiles.length; k++) {
                    dragTiles[k] = slot[s0 + k];
                    if (dragTiles[k] < 0) {
                        dragTiles = null;
                        return false;
                    }
                }
                dragSrc = 4;
                dragSlot = s0;
                return true;
            }
        }
        for (int i = 0; i < SLOTS; i++) {
            if (slotR[i].contains(x, y) && slot[i] >= 0) {
                dragSrc = 1;
                dragSlot = i;
                dragTile = slot[i];
                return true;
            }
        }
        return false;
    }

    /** Parmağın altındaki ıstaka gözü; ıstakanın dışındaysa -1. */
    private int rackIndexAt(float x, float y) {
        float m = 40f * S;
        if (x < rackL - m || x > rackL + rackW + m || y < rackT - 16f * S || y > rackT + rackH + m) {
            return -1;
        }
        float rowMid = rackT + 14f * S + th + HANDLE_H + 4f * S;
        int row = y < rowMid ? 0 : 1;
        int col = Math.round((x - rackL - 14f * S - tw / 2f) / (tw + 8f * S));
        col = Math.max(0, Math.min(PER_ROW - 1, col));
        return row * PER_ROW + col;
    }

    /** v'yi i. göze koyar: doluysa yandaki en yakın boşluğa kadar taşları kaydırır (araya girer). */
    private static void insertAt(int[] a, int i, int v) {
        RackUtil.insertAt(a, i, v, PER_ROW);
    }

    @Override
    protected void dragMove(float x, float y) {
        dragX = x;
        dragY = y;
        if (!dragLive) {
            dragLive = true;
            if (dragSrc == 1) {
                sel = -1;
            }
        }
        int i = rackIndexAt(x, y);
        if (dragSrc == 1 && ok != null && ok.turn == me && ok.phase == Okey.DISCARD && hit(discR[0], x, y)) {
            i = -1;
        }
        int[] a = slot.clone();
        if (dragSrc == 1) {
            a[dragSlot] = -1;
        }
        if (dragSrc == 4) {
            for (int k = 0; k < dragTiles.length; k++) {
                a[dragSlot + k] = -1;
            }
            if (i >= 0) {
                int[] ph = new int[dragTiles.length];
                java.util.Arrays.fill(ph, -2);
                RackUtil.insertBlock(a, i, ph, PER_ROW);
            }
            dragPrev = a;
            return;
        }
        if (i >= 0 && (dragSrc == 1 || (ok != null && ok.turn == me && ok.phase == Okey.DRAW))) {
            insertAt(a, i, -2);
        }
        dragPrev = a;
    }

    @Override
    protected void dragEnd(float x, float y) {
        int src = dragSrc;
        int i = rackIndexAt(x, y);
        if (src == 1 && ok != null && ok.turn == me && ok.phase == Okey.DISCARD && hit(discR[0], x, y)) {
            i = -1;
        }
        dragSrc = 0;
        dragLive = false;
        dragPrev = null;
        if (ok == null) {
            return;
        }
        if (src == 4) {
            int[] tl = dragTiles;
            dragTiles = null;
            if (tl == null || i < 0) {
                return;
            }
            for (int k = 0; k < tl.length; k++) {
                if (slot[dragSlot + k] != tl[k]) {
                    return;
                }
            }
            for (int k = 0; k < tl.length; k++) {
                slot[dragSlot + k] = -1;
            }
            RackUtil.insertBlock(slot, i, tl, PER_ROW);
            sel = -1;
            return;
        }
        if (src == 1) {
            if (slot[dragSlot] != dragTile) {
                return;
            }
            if (i >= 0) {
                slot[dragSlot] = -1;
                insertAt(slot, i, dragTile);
                sel = -1;
            } else {
                sel = dragSlot;
                onGameTap(x, y);
            }
        } else if ((src == 2 || src == 3) && i >= 0 && ok.turn == me && ok.phase == Okey.DRAW) {
            dropAt = i;
            submit(me, src == 2 ? "D" : "K");
            if (ok.phase == Okey.DRAW && ok.turn == me) {
                dropAt = -1;
            }
        }
    }

    @Override
    protected void dragCancel() {
        dragSrc = 0;
        dragLive = false;
        dragPrev = null;
    }

    /** Çekilen taşı, sürüklenerek bırakıldıysa bırakılan yere, değilse en sağ boşluğa koyar. */
    private int placeNew(int t) {
        int i = dropAt;
        dropAt = -1;
        if (i >= 0 && i < SLOTS) {
            insertAt(slot, i, t);
            return slotOf(t);
        }
        return putNew(t);
    }

    /** Istakadaki taş: okey (joker) taşı sayısı görünmeden arka yüzüyle durur. */
    private void rackFace(Canvas c, RectF r, int t) {
        if (ok.isJoker(t)) {
            CardArt.okeyBack(c, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, r.width() * 0.03f));
            paint.setColor(0xFF9A7B3A);
            float m = r.width() * 0.2f;
            c.drawRoundRect(new RectF(r.left + m, r.top + m, r.right - m, r.bottom - m), r.width() * 0.1f, r.width() * 0.1f, paint);
        } else {
            tileFace(c, r, t, false);
        }
    }

    private void drawGhost(Canvas c) {
        if (!dragLive || dragSrc == 0) {
            return;
        }
        if (dragSrc == 4 && dragTiles != null) {
            float gap2 = 8f * S;
            float total = dragTiles.length * tw + (dragTiles.length - 1) * gap2;
            float x0 = dragX - total / 2f;
            float top = dragY - th * 0.95f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x55000000);
            c.drawRoundRect(new RectF(x0 + 6f * S, top + 8f * S, x0 + total + 6f * S, top + th + 8f * S), tw * 0.16f, tw * 0.16f, paint);
            for (int k = 0; k < dragTiles.length; k++) {
                RectF r = new RectF(x0 + k * (tw + gap2), top, x0 + k * (tw + gap2) + tw, top + th);
                rackFace(c, r, dragTiles[k]);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4f * S);
            paint.setColor(0xFFFFE282);
            c.drawRoundRect(new RectF(x0 - 4f * S, top - 4f * S, x0 + total + 4f * S, top + th + 4f * S), tw * 0.2f, tw * 0.2f, paint);
            return;
        }
        float gw = tw * 1.12f;
        float gh = th * 1.12f;
        RectF r = new RectF(dragX - gw / 2f, dragY - gh * 0.95f, dragX + gw / 2f, dragY + gh * 0.05f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x55000000);
        c.drawRoundRect(new RectF(r.left + 6f * S, r.top + 8f * S, r.right + 6f * S, r.bottom + 8f * S), gw * 0.16f, gw * 0.16f, paint);
        if (dragSrc == 2) {
            CardArt.okeyBack(c, r, paint);
        } else {
            rackFace(c, r, dragTile);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * S);
        paint.setColor(0xFFFFE282);
        c.drawRoundRect(r, gw * 0.16f, gw * 0.16f, paint);
    }

    /** Destenin üstünde kalan taş sayısı. */
    private void pileCount(Canvas c, RectF deck) {
        RectF pr = new RectF(deck.centerX() - 62f * S, deck.top - 54f * S, deck.centerX() + 62f * S, deck.top - 14f * S);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xCC2E1A0B);
        c.drawRoundRect(pr, 14f * S, 14f * S, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * S);
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(pr, 14f * S, 14f * S, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, ok.pile.size() + " TAŞ", pr, 0xFFFFE282, 22f * S);
    }

    /** Gösterge: destenin hemen yanında. */
    private void drawIndicator(Canvas c, RectF deck) {
        RectF ind = new RectF(deck.right + 22f * S, deck.top, deck.right + 22f * S + tw, deck.bottom);
        tileFace(c, ind, ok.indicator, false);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, "GÖSTERGE · OKEY: " + CardArt.OKEY_COLOR_NAMES[ok.okeyColor] + " " + ok.okeyNum,
                new RectF(ind.centerX() - 150f * S, ind.bottom + 6f * S, ind.centerX() + 150f * S, ind.bottom + 34f * S),
                0xFFE9C46A, 19f * S);
    }

    public void tapSlot(int i) { RectF r = slotR[i]; onGameTap(r.centerX(), r.centerY()); }

    // ------------------------------------------------------------------
    // Çizim
    // ------------------------------------------------------------------

    @Override
    public float[] seatCenter(int p) {
        return seat(p);
    }

    private float rackL, rackT, rackW, rackH;

    private boolean hit(RectF r, float x, float y) {
        float m = 16f * S;
        return x >= r.left - m && x <= r.right + m && y >= r.top - m && y <= r.bottom + m;
    }

    /** Istaka ölçüleri: ıstaka ekranın tam ortasında, düğmeler sağında. */
    private void computeRack() {
        HANDLE_H = 20f * S;
        float gap = 8f * S;
        float pad = 14f * S;
        float btnW = 190f * S;
        float half = ix1 - cx() - btnW - 2f * pad - 6f * S;       // ıstakanın yarı genişliği üst sınırı
        float availRack = 2f * half;
        tw = Math.min(62f * S, (availRack - 14f * gap - 2f * pad) / PER_ROW);
        th = tw * 1.35f;
        rackW = PER_ROW * tw + 14f * gap + 2f * pad;
        rackH = 2f * (th + HANDLE_H) + gap + 2f * pad;
        rackL = cx() - rackW / 2f;
        rackT = iy1 - rackH - 10f * S;
    }

    private float[] seat(int p) {
        float w = getWidth();
        switch (rel(p)) {
            case 1:
                return new float[] {ix0 + 190f * S, 395f * S};
            case 2:
                return new float[] {w / 2f, iy0 + 78f * S};
            case 3:
                return new float[] {ix1 - 190f * S, 395f * S};
            default:
                computeRack();
                return new float[] {cx(), rackT - 58f * S - 50f * S};
        }
    }

    public float deckCenterX() { return deckR.centerX(); }
    public float deckCenterY() { return deckR.centerY(); }
    public float discardCenterX(int p) { return discR[p].centerX(); }
    public float discardCenterY(int p) { return discR[p].centerY(); }
    public RectF slotRect(int i) { return slotR[i]; }
    public int slotOf(int t) { for (int i = 0; i < SLOTS; i++) { if (slot[i] == t) { return i; } } return -1; }

    public void tileFace(Canvas c, RectF r, int t, boolean dim) {
        if (t >= Okey.FAKE1) {
            CardArt.okeyFace(c, r, ok.okeyNum, ok.okeyColor, true, dim, paint, text);
        } else {
            CardArt.okeyFace(c, r, Okey.num(t), Okey.color(t), false, dim, paint, text);
        }
    }

    private void box(Canvas c, RectF r, boolean hot) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x44000000);
        c.drawRoundRect(r, 14f * S, 14f * S, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(hot ? 5f * S : 3f * S);
        paint.setColor(hot ? 0xFFFFE282 : 0x88D4AF37);
        c.drawRoundRect(r, 14f * S, 14f * S, paint);
    }

    @Override
    public void drawGame(Canvas c) {
        if (ok == null) {
            return;
        }
        if (modalKind == K_NONE) {
            drawMenuButton(c);
        }
        boolean live = ok.phase == Okey.DRAW || ok.phase == Okey.DISCARD;
        computeRack();
        // oyuncu kutuları
        for (int p = 0; p < 4; p++) {
            float[] s = seat(p);
            boolean turn = live && ok.turn == p;
            profileBox(c, s[0], s[1], names[p] + (online() && !human[p] ? " (BOT)" : ""), "Puan " + ok.score[p], AVATARS[p % AVATARS.length], levels[p], turn, turnFrac(p));
            if (turn && rel(p) != 0 && turnFrac(p) < 0f) {
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, "SIRADA", new RectF(s[0] - 60f * S, s[1] - 90f * S, s[0] + 60f * S, s[1] - 58f * S),
                        0xFFFFE282, 20f * S);
            }
        }
        // bilgi paneli
        RectF ip = new RectF(ix1 - 320f * S, iy0 + 22f * S, ix1 - 22f * S, iy0 + 22f * S + 130f * S);
        infoPanel(c, ip, new String[] {"EL " + ok.elNo, "DESTE " + ok.pile.size(), "BAŞLANGIÇ " + start},
                new int[] {0xFFE9C46A, 0xFFFFF4D6, 0xFFFFF4D6}, new float[] {26f, 22f, 22f});

        // ıstaka (ekranın tam ortasında)
        float gap = 8f * S;
        float pad = 14f * S;
        float btnW = 190f * S;
        RectF rack = new RectF(rackL, rackT, rackL + rackW, rackT + rackH);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF4A2C14);
        c.drawRoundRect(rack, 22f * S, 22f * S, paint);
        paint.setColor(0xFF2E1A0B);
        c.drawRoundRect(new RectF(rack.left + 8f * S, rack.top + 8f * S, rack.right - 8f * S, rack.bottom - 8f * S),
                16f * S, 16f * S, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * S);
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(rack, 22f * S, 22f * S, paint);
        for (int i = 0; i < SLOTS; i++) {
            int row = i / PER_ROW;
            int col = i % PER_ROW;
            float x = rackL + pad + col * (tw + gap);
            float y = rackT + pad + row * (th + HANDLE_H + gap) + HANDLE_H;
            slotR[i].set(x, y, x + tw, y + th);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33000000);
            c.drawRoundRect(slotR[i], tw * 0.16f, tw * 0.16f, paint);
        }
        int[] ds = dragPrev != null ? dragPrev : slot;
        for (int i = 0; i < SLOTS; i++) {
            if (ds[i] == -2) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5f * S);
                paint.setColor(0xFF78FF8C);
                c.drawRoundRect(slotR[i], tw * 0.16f, tw * 0.16f, paint);
            } else if (ds[i] >= 0) {
                RectF r = new RectF(slotR[i]);
                if (i == sel) {
                    r.offset(0, -14f * S);
                }
                rackFace(c, r, ds[i]);
                if (i == sel) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(4f * S);
                    paint.setColor(0xFFFFE282);
                    c.drawRoundRect(r, tw * 0.16f, tw * 0.16f, paint);
                }
            }
        }
        // gerçek perlerin üstünde tutamaç
        handleR.clear();
        if (ok.phase == Okey.DRAW || ok.phase == Okey.DISCARD) {
            for (int[] sp : RackUtil.perSpans(ok, ds, PER_ROW)) {
                RectF a0 = slotR[sp[0]];
                RectF a1 = slotR[sp[1]];
                RectF hb = new RectF(a0.left, a0.top - HANDLE_H + 3f * S, a1.right, a0.top - 3f * S);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFC9A04A);
                c.drawRoundRect(hb, hb.height() / 2f, hb.height() / 2f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2f * S);
                paint.setColor(0xFFFFE282);
                c.drawRoundRect(hb, hb.height() / 2f, hb.height() / 2f, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF5A3A10);
                for (int k = -1; k <= 1; k++) {
                    c.drawCircle(hb.centerX() + k * 12f * S, hb.centerY(), 2.6f * S, paint);
                }
                if (dragPrev == null) {
                    handleR.add(new float[] {hb.left, hb.top, hb.right, hb.bottom, sp[0], sp[1]});
                }
            }
        }
        // düğmeler (ıstakanın sağında)
        float bx = rack.right + pad + 6f * S;
        float gb = 10f * S;
        float bh3 = (rack.height() - 12f * S - 2f * gb) / 3f;
        RectF arr = new RectF(bx, rackT + 6f * S, bx + btnW, rackT + 6f * S + bh3);
        RectF par = new RectF(bx, arr.bottom + gb, bx + btnW, arr.bottom + gb + bh3);
        RectF fin = new RectF(bx, par.bottom + gb, bx + btnW, par.bottom + gb + bh3);
        woodButton(c, arr, "SERİ DİZ", false, live);
        woodButton(c, par, "ÇİFT DİZ", false, live);
        woodButton(c, fin, "BİTİR", true, live);
        addBtn(arr, B_ARR, 0);
        addBtn(par, B_PAIR, 0);
        addBtn(fin, B_FIN, 0);

        // masa ortası: deste ve hemen yanında gösterge
        float cx = cx();
        float cy = 285f * S;
        float dcx = cx - tw / 2f - 12f * S;
        deckR.set(dcx - tw / 2f, cy - th / 2f, dcx + tw / 2f, cy + th / 2f);
        RectF d2 = new RectF(deckR);
        d2.offset(-6f * S, -6f * S);
        CardArt.okeyBack(c, d2, paint);
        d2.offset(6f * S, 6f * S);
        CardArt.okeyBack(c, d2, paint);
        boolean canDraw = live && ok.turn == me && ok.phase == Okey.DRAW;
        if (canDraw) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(5f * S);
            paint.setColor(0xFFFFE282);
            c.drawRoundRect(deckR, tw * 0.16f, tw * 0.16f, paint);
        }
        pileCount(c, deckR);
        drawIndicator(c, deckR);

        // atılan taşlar: taşı atan oyuncunun profil kutusunun sağ alt köşesinde, köşe köşe
        float dw = tw * 0.66f;
        float dh = th * 0.66f;
        for (int p = 0; p < 4; p++) {
            // p: ekran konumu (0 ben, 1 sol, 2 üst, 3 sağ); mutlak koltuk (me - p)
            int ab = (me - p + 4) & 3;
            float[] sc = seat(ab);
            float ax = sc[0] + 150f * S - 34f * S;       // kutunun sağ kenarına taşan konum
            float ay = sc[1] + 50f * S - 16f * S;
            discR[p].set(ax, ay, ax + dw, ay + dh);
            boolean hot = live && ((p == 1 && ok.turn == me && ok.phase == Okey.DRAW && !ok.discards[ab].isEmpty())
                    || (p == 0 && ok.turn == me && ok.phase == Okey.DISCARD && sel >= 0));
            List<Integer> dl = ok.discards[ab];
            int show = Math.min(3, dl.size());
            if (show == 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth((hot ? 4f : 2f) * S);
                paint.setColor(hot ? 0xFFFFE282 : 0x55FFF4D6);
                c.drawRoundRect(discR[p], dw * 0.16f, dw * 0.16f, paint);
            }
            for (int k = 0; k < show; k++) {
                int back = show - 1 - k;     // 0 = en son atılan (önde)
                RectF r = new RectF(discR[p]);
                r.offset(-14f * S * back, -11f * S * back);
                tileFace(c, r, dl.get(dl.size() - 1 - back), false);
            }
            if (hot) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5f * S);
                paint.setColor(0xFFFFE282);
                c.drawRoundRect(discR[p], dw * 0.16f, dw * 0.16f, paint);
            }
            if (p == 0 && live && ok.turn == me && ok.phase == Okey.DISCARD) {
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, "AT ▾", new RectF(discR[p].right + 4f * S, discR[p].top, discR[p].right + 70f * S, discR[p].top + 30f * S),
                        0xFFFFE282, 18f * S);
            }
        }
        drawBubbles(c);
        drawGhost(c);
        // bildirim
        if (banner.length() > 0 && ok.phase != Okey.MATCH_OVER) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(26f * S);
            float bww = Math.min(text.measureText(banner) + 60f * S, getWidth() * 0.7f);
            float[] mySeat = seat(me);
            RectF b = new RectF(cx - bww / 2f, mySeat[1] - 50f * S - 58f * S, cx + bww / 2f, mySeat[1] - 50f * S - 12f * S);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1E120A);
            c.drawRoundRect(b, 23f * S, 23f * S, paint);
            centerText(c, banner, b, 0xFFFFF4D6, 26f * S);
        }
    }

    @Override
    public String rulesText() {
        return rules();
    }

    public static String rules() {
        return "## Taşlar\n"
                + "106 taş: 4 renk (sarı, mavi, siyah, kırmızı) × 1-13 × 2 takım ve 2 sahte okey. Masada bir gösterge taşı açılır. "
                + "Gösterge ile aynı renkte, bir büyük sayılı taş (13'ün üstü 1) OKEYdir ve joker yerine geçer. "
                + "Sahte okey, okeyin kendi yerine geçen sıradan bir taştır.\n\n"
                + "## Dağıtım\n"
                + "Herkese 14 taş verilir; dağıtandan sonraki oyuncu 15 taş alır ve oyuna taş atarak başlar.\n\n"
                + "## Sıra\n"
                + "Sıran gelince ya desteden bir taş çekersin ya da senden önceki oyuncunun attığı son taşı alırsın. "
                + "Sonra elinden bir taşı atılan kutusuna bırakırsın.\n\n"
                + "## Bitirmek\n"
                + "14 taşın tamamı geçerli gruplardan oluşuyorsa ve 15. taşı atarsan el biter. Gruplar:\n"
                + "• Seri: aynı renkten ardışık en az 3 taş (1-2-3, 11-12-13; 12-13-1 de olur, 13-1-2 olmaz).\n"
                + "• Per: aynı sayıdan farklı renkli 3 ya da 4 taş.\n"
                + "• Çift: 7 çift taş (aynı renk ve sayıdan iki taş).\n"
                + "Okey her yerde joker olarak kullanılır.\n\n"
                + "## Puan\n"
                + "Seçtiğin başlangıç puanından (11, 9, 7, 5, 3 ya da 1) başlanır. Eli biten oyuncu kaybetmez; diğer üç oyuncu 2 puan düşer. "
                + "Çiftle bitirirsen ya da son taş olarak okey atarak bitirirsen puan 2 katına çıkar (ikisi birlikte 4 katı). "
                + "Deste biterse el berabere kalır. Biri 0 puana inince maç biter; en çok puanı kalan kazanır.\n\n"
                + "## Kullanım\n"
                + "Taşı sürükleyerek ya da önce dokunup sonra hedefe dokunarak taşırsın; taşlar yer değiştirmez, araya girer (yer yoksa taşan taş öbür satırın boş yerine geçer). Gerçek perlerin üstünde çıkan altın tutamaçtan sürüklersen per bütün olarak taşınır. DİZ elini otomatik gruplar. "
                + "Atmak için taşı seçip masa ortasının altındaki atılan kutuna dokun. BİTİR, seçili taşı atarak elini bitirmeyi dener; taş seçmezsen uygun taşı kendisi bulur.";
    }
}
