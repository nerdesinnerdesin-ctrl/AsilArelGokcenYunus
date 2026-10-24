package com.hapistavla.oyun.okey101;

import com.hapistavla.oyun.*;
import com.hapistavla.oyun.salon.CardArt;
import com.hapistavla.oyun.tavla.Game;
import com.hapistavla.oyun.okey.Okey;
import com.hapistavla.oyun.okey.RackUtil;
import com.hapistavla.oyun.salon.SalonBase;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 101 Okey masası: 3 bilgisayar rakip, 2 sıralı ıstaka, masadaki açılmış gruplar, işleme. */
public final class Okey101View extends SalonBase {
    private static final int K_EL = K_CUSTOM + 1;
    private static final int K_SCORES = K_CUSTOM + 2;
    private static final int K_MATCH = K_CUSTOM + 3;

    private static final int B_ARR = 40;
    private static final int B_PAIR = 44;
    private static final int B_OPEN = 41;
    private static final int B_NEXT = 42;
    private static final int B_ELOK = 43;

    public static final int SLOTS = 30;
    public static final int PER_ROW = 15;
    private float HANDLE_H = 20f;
    private final List<float[]> handleR = new ArrayList<float[]>();   // {sol, üst, sağ, alt, ilk göz, son göz}
    private int[] dragTiles = null;                                    // sürüklenen perin taşları

    private final int els;
    private final boolean rEshli;
    private final boolean rKatlama;
    private final boolean rCeza;
    private final RectF ipR = new RectF();
    public Okey101 ok;
    private final int[] slot = new int[SLOTS];
    private int sel = -1;
    private boolean resultSent = false;
    private final RectF[] slotR = new RectF[SLOTS];
    private final RectF[] discR = new RectF[4];
    private final RectF deckR = new RectF();
    private final List<RectF> meldR = new ArrayList<RectF>();
    private float tw;
    private float th;

    /** value = el sayısı + 100 * (1 eşli + 2 katlama + 4 ceza). */
    public Okey101View(Context ctx, int value) {
        super(ctx, false, "101 OKEY");
        this.els = value % 100;
        int fl = value / 100;
        rEshli = (fl & 1) != 0;
        rKatlama = (fl & 2) != 0;
        rCeza = (fl & 4) != 0;
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
        ok = new Okey101(els, new Random(sd));
        ok.setRules(rEshli, rKatlama, rCeza);
        resultSent = false;
        newEl();
    }

    private void newEl() {
        ok.newEl();
        sel = -1;
        layout(ok.arrange(ok.hand[me]));
        banner = names[ok.dealer] + " dağıttı";
        step();
    }

    /** Grupları ıstakaya dizer: gruplar arasında boşluk; serbest taşlar alt sıraya. */
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

    /** Eldeki taşlardan ıstakada olmayanları temizler (oyun motoru taş indirdiyse). */
    private void syncSlots() {
        for (int i = 0; i < SLOTS; i++) {
            if (slot[i] >= 0 && !ok.hand[me].contains(slot[i])) {
                slot[i] = -1;
            }
        }
        if (sel >= 0 && slot[sel] < 0) {
            sel = -1;
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
        syncSlots();
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
                    if (modalKind != K_MATCH && modalKind != K_EL) {
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
                if (ok.turn != tp) {
                    return;
                }
                if (ok.phase == Okey.DRAW) {
                    submit(tp, ok.botTakeDiscard(tp) ? "K" : "D");
                    return;
                }
                if (ok.phase == Okey.DISCARD) {
                    final boolean acted = submit(tp, "B");
                    delayed(token, new Runnable() {
                        @Override
                        public void run() {
                            if (ok.phase == Okey.DISCARD && ok.turn == tp && !ok.hand[tp].isEmpty()) {
                                submit(tp, "X " + ok.botDiscard(tp));
                            }
                        }
                    }, acted ? 700 : 100);
                }
            }
        });
        if (ok.turn == me) {
            if (ok.phase == Okey.DRAW) {
                banner = "Sıra sende: desteden çek ya da atılan taşı al";
            } else if (!ok.opened[me]) {
                banner = "Gruplarını dizip AÇ'a bas (en az 101 puan) ya da bir taş at";
            } else {
                banner = "Taş işle, grup indir ya da bir taş at";
            }
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
                        // önce açma/indirme/işleme (varsa), sonra taş atma
                        final boolean acted = submit(p, "B");
                        delayed(token, new Runnable() {
                            @Override
                            public void run() {
                                if (ok.phase == Okey.DISCARD && ok.turn == p && !ok.hand[p].isEmpty()) {
                                    submit(p, "X " + ok.botDiscard(p));
                                }
                            }
                        }, acted ? 700 : 100);
                    }
                }, 800);
            }
        }
    }

    /** Ağda taş indirme/işleme komutlarının yerine masanın yeni hali gönderilir. */
    @Override
    public String wire(int seat, String cmd) {
        char k = cmd.charAt(0);
        if (k == 'G' || k == 'A' || k == 'B') {
            return "T " + (ok.opened[seat] ? 1 : 0) + " " + ok.lastOpenPoints + " " + ok.tableSnapshot();
        }
        return cmd;
    }

    private List<List<Integer>> parseGroups(String s) {
        List<List<Integer>> groups = new ArrayList<List<Integer>>();
        for (String gs : s.split(";")) {
            List<Integer> g = new ArrayList<Integer>();
            for (String t : gs.split("\\.")) {
                if (!t.isEmpty()) {
                    g.add(Integer.parseInt(t));
                }
            }
            if (!g.isEmpty()) {
                groups.add(g);
            }
        }
        return groups;
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
                    List<Integer> dl = ok.discards[ok.prev(seat)];
                    if (!ok.opened[seat] && !ok.canOpenWith(seat, dl.get(dl.size() - 1))) {
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
                    if (ok.penSeat == seat) {
                        banner = seat == me
                                ? "Ceza +101: " + (ok.penReason == 1 ? "okey attın" : "işlenebilir taş attın")
                                : names[seat] + " ceza yedi (+101): " + (ok.penReason == 1 ? "okey attı" : "işlenebilir taş attı");
                        sound(Game.SND_PIN);
                    }
                    return true;
                }
                case 'G': {
                    if (!authority()) {
                        return false;
                    }
                    boolean was = ok.opened[seat];
                    if (!ok.layGroups(seat, parseGroups(f[1]))) {
                        return false;
                    }
                    laidBanner(seat, was);
                    return true;
                }
                case 'A': {
                    if (!authority() || !ok.addTile(seat, Integer.parseInt(f[1]), Integer.parseInt(f[2]))) {
                        return false;
                    }
                    laidBanner(seat, true);
                    return true;
                }
                case 'B': {
                    // bilgisayarın açma / indirme / işleme hamlesi (yalnız ev sahibinde)
                    if (!authority() || !botSeat(seat) || ok.phase != Okey.DISCARD || ok.turn != seat) {
                        return false;
                    }
                    boolean was = ok.opened[seat];
                    int before = ok.table.size();
                    int sum = tableTiles();
                    if (!was) {
                        ok.open(seat);
                    }
                    if (ok.opened[seat]) {
                        ok.layMore(seat);
                        ok.botAdd(seat);
                    }
                    if (tableTiles() == sum && ok.table.size() == before) {
                        return false;
                    }
                    laidBanner(seat, was);
                    return true;
                }
                case 'T': {
                    // masanın yeni hali (misafir)
                    if (authority()) {
                        return false;
                    }
                    boolean was = ok.opened[seat];
                    ok.applyTableSnapshot(seat, f[1].equals("1"), Integer.parseInt(f[2]), f.length > 3 ? f[3] : "-");
                    laidBanner(seat, was);
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

    private int tableTiles() {
        int n = 0;
        for (Okey.Meld m : ok.table) {
            n += m.tiles.size();
        }
        return n;
    }

    private void laidBanner(int seat, boolean was) {
        sound(Game.SND_PIN);
        if (seat == me) {
            sel = -1;
        }
        step();
        if (seat != me) {
            banner = was ? names[seat] + " taş indirdi" : names[seat] + " elini açtı (" + ok.lastOpenPoints + ")";
        } else {
            banner = was ? "Gruplar indirildi" : "Elini açtın (" + ok.lastOpenPoints + " puan)";
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
            t = ok.elWinner == me ? "ELİ BİTİRDİN!" : names[ok.elWinner] + " ELİ BİTİRDİ";
            sub = "Rakiplere ceza yazıldı";
            if (ok.elMult > 1) {
                sub = (ok.elSolo ? "Elden bitirme" : "") + (ok.elSolo && ok.elOkeyFinish ? " + " : "")
                        + (ok.elOkeyFinish ? "Okey atarak bitirme" : "") + ": KATLANDI ×" + ok.elMult;
            }
        }
        for (int q = 0; q < 4; q++) {
            if (ok.penalty[q] > 0) {
                sub += " · Ceza +" + ok.penalty[q] + ": " + names[q];
            }
        }
        sound(ok.elWinner == me ? Game.SND_WIN : Game.SND_PIN);
        boolean matchEnd = ok.phase == Okey.MATCH_OVER;
        openModal(K_EL, t, sub, matchEnd ? new MB(B_NEXT, "SONUÇ", true)
                : (online() && !authority() ? new MB(B_ELOK, "TAMAM", true) : new MB(B_NEXT, "SONRAKİ EL", true)));
    }

    private void openScores() {
        String t = "PUAN TABLOSU";
        String sub = (ok.eshli ? "EŞLİ · " : "") + (ok.ceza ? "CEZALI · " : "") + (ok.katlama ? "KATLAMALI · " : "")
                + "en düşük puan kazanır";
        openModal(K_SCORES, t, sub, new MB(B_MODAL_BACK, "‹ GERİ", true));
    }

    private void openMatchOver() {
        boolean win = ok.wins(me);
        if (!resultSent) {
            resultSent = true;
            sound(win ? Game.SND_WIN : Game.SND_LOSE);
            if (host != null) {
                if (online()) {
                    host.onSalonOnlineResult(SalonBase.G_101, win, 2);
                } else {
                    host.onSalonResult(SalonBase.G_101, win, 2);
                }
            }
        }
        String mt = ok.eshli ? (win ? "TAKIMIN KAZANDI!" : "RAKİP TAKIM KAZANDI")
                : (win ? "KAZANDIN!" : names[ok.matchWinner] + " KAZANDI");
        String ms = els + " el bitti · en az ceza puanı kazanır";
        if (ok.eshli) {
            ms += " · A takımı " + ok.teamScore(0) + " · B takımı " + ok.teamScore(1);
        }
        openModal(K_MATCH, mt, ms, new MB(B_REMATCH, online() ? "RÖVANŞ" : "YENİ MAÇ", true),
                new MB(B_EXIT_YES, "SALONA DÖN", false));
    }

    @Override
    public RectF modalRect(int kind) {
        float w = getWidth();
        if (kind >= K_CUSTOM) {
            float mw = Math.min(w * 0.6f, 1000f * S);
            float mh = kind == K_SCORES ? Math.min(getHeight() * 0.94f, 820f * S) : 640f * S;
            float top = (getHeight() - mh) / 2f;
            return new RectF(w / 2f - mw / 2f, top, w / 2f + mw / 2f, top + mh);
        }
        return super.modalRect(kind);
    }

    @Override
    public void drawCustomModal(Canvas c, int kind, RectF body) {
        if (kind == K_SCORES) {
            drawScoreTable(c, body);
            return;
        }
        boolean delta = kind == K_EL;
        float rowH = Math.min(70f * S, body.height() / 5f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        float x0 = body.left + 20f * S;
        float xs = body.right - 20f * S;
        float xd = body.right - 210f * S;
        leftText(c, "OYUNCU", x0, body.top + rowH * 0.4f, 0xFFC9B58A, 22f * S, 300f * S);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFFC9B58A);
        text.setTextSize(22f * S);
        if (delta) {
            c.drawText("BU EL", xd, body.top + rowH * 0.4f + 8f * S, text);
        }
        c.drawText("TOPLAM", xs, body.top + rowH * 0.4f + 8f * S, text);
        for (int p = 0; p < 4; p++) {
            float cy = body.top + rowH * (p + 1.4f);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            leftText(c, names[p], x0, cy, 0xFFFFF4D6, 30f * S, 400f * S);
            text.setTextAlign(Paint.Align.RIGHT);
            if (delta) {
                int d = ok.elDelta[p];
                text.setColor(d < 0 ? 0xFF8FE08F : (d > 0 ? 0xFFFF8A80 : 0xFFC9B58A));
                text.setTextSize(32f * S);
                c.drawText(d == 0 ? "—" : (d > 0 ? "+" + d : String.valueOf(d)), xd, cy + 11f * S, text);
            }
            text.setColor(0xFFE9C46A);
            text.setTextSize(34f * S);
            c.drawText(String.valueOf(ok.score[p]), xs, cy + 12f * S, text);
        }
    }

    private void badge(Canvas c, String t, float cx, float cy, float r, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        c.drawRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), r * 0.4f, r * 0.4f, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, t, new RectF(cx - r, cy - r, cx + r, cy + r), 0xFFFFFFFF, r * 1.25f);
    }

    /** Puan tablosu: her el için oyuncu puanları, toplam ve (eşliyse) takım toplamı. C = ceza, K = katlama. */
    private void drawScoreTable(Canvas c, RectF body) {
        int n = ok.history.size();
        int rows = Math.max(els, n);
        float top = body.top + 4f * S;
        float teamH = ok.eshli ? 26f * S : 0f;
        float footH = (ok.eshli ? 150f : 96f) * S;
        float rowH = Math.min(46f * S, (body.height() - 56f * S - teamH - footH) / rows);
        float x0 = body.left + 24f * S;
        float xr = body.right - 24f * S;
        float labW = 90f * S;
        float colW = (xr - x0 - labW) / 4f;
        float[] cx = new float[4];
        for (int p = 0; p < 4; p++) {
            cx[p] = x0 + labW + colW * (p + 0.5f);
        }
        text.setTypeface(Typeface.DEFAULT_BOLD);
        if (ok.eshli) {
            for (int p = 0; p < 4; p++) {
                boolean a = Okey101.teamOf(p) == 0;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(a ? 0xFF4670AA : 0xFFAA5A3C);
                RectF b = new RectF(cx[p] - colW * 0.42f, top, cx[p] + colW * 0.42f, top + teamH - 4f * S);
                c.drawRoundRect(b, 8f * S, 8f * S, paint);
                centerText(c, a ? "A TAKIMI" : "B TAKIMI", b, 0xFFFFFFFF, 15f * S);
            }
            top += teamH;
        }
        float hy = top + 18f * S;
        centerText(c, "EL", new RectF(x0, hy - 16f * S, x0 + labW, hy + 16f * S), 0xFFC9B58A, 20f * S);
        for (int p = 0; p < 4; p++) {
            RectF nb = new RectF(cx[p] - colW / 2f, hy - 16f * S, cx[p] + colW / 2f, hy + 16f * S);
            centerText(c, names[p], nb, p == me ? 0xFFE9C46A : 0xFFFFF4D6, 22f * S);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF96702F);
        c.drawRect(x0, hy + 22f * S, xr, hy + 24f * S, paint);
        float y = hy + 30f * S;
        for (int r = 0; r < rows; r++) {
            float cy = y + rowH * (r + 0.5f);
            if (r % 2 == 0) {
                paint.setColor(0x22FFFFFF);
                c.drawRect(x0, cy - rowH / 2f, xr, cy + rowH / 2f, paint);
            }
            centerText(c, String.valueOf(r + 1), new RectF(x0, cy - rowH / 2f, x0 + labW * 0.6f, cy + rowH / 2f), 0xFFE9C46A, 22f * S);
            if (r < n) {
                int[] h = ok.history.get(r);
                if (h[5] > 1) {
                    badge(c, "K", x0 + labW * 0.8f, cy, 12f * S, 0xFF9650C8);
                }
                for (int p = 0; p < 4; p++) {
                    String v = String.valueOf(h[p]);
                    RectF vb = new RectF(cx[p] - colW / 2f, cy - rowH / 2f, cx[p] + colW / 2f, cy + rowH / 2f);
                    centerText(c, v, vb, h[p] < 0 ? 0xFF8FE08F : 0xFFFFF4D6, 24f * S);
                    if ((h[4] & (1 << p)) != 0) {
                        badge(c, "C", cx[p] + colW * 0.36f, cy, 12f * S, 0xFFC83C32);
                    }
                }
            }
        }
        float fy = y + rowH * rows + 6f * S;
        paint.setColor(0xFFD4AF37);
        c.drawRect(x0, fy, xr, fy + 3f * S, paint);
        float ty = fy + 28f * S;
        centerText(c, "TOPLAM", new RectF(x0 - 6f * S, ty - 16f * S, x0 + labW + 6f * S, ty + 16f * S), 0xFFE9C46A, 16f * S);
        for (int p = 0; p < 4; p++) {
            centerText(c, String.valueOf(ok.score[p]), new RectF(cx[p] - colW / 2f, ty - 18f * S, cx[p] + colW / 2f, ty + 18f * S),
                    0xFFFFF4D6, 28f * S);
        }
        if (ok.eshli) {
            float by = ty + 28f * S;
            for (int t = 0; t < 2; t++) {
                boolean mine = t == Okey101.teamOf(me);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(t == 0 ? 0xFF4670AA : 0xFFAA5A3C);
                float bl = t == 0 ? x0 + 10f * S : (x0 + xr) / 2f + 6f * S;
                RectF b = new RectF(bl, by, bl + (xr - x0) / 2f - 16f * S, by + 44f * S);
                c.drawRoundRect(b, 12f * S, 12f * S, paint);
                centerText(c, (t == 0 ? "A TAKIMI: " : "B TAKIMI: ") + ok.teamScore(t) + (mine ? " (SİZ)" : ""), b, 0xFFFFFFFF, 22f * S);
            }
        }
        text.setTypeface(Typeface.DEFAULT);
        float ly = ok.eshli ? ty + 28f * S + 44f * S + 6f * S : ty + 24f * S;
        centerText(c, "C = ceza yazıldı   K = katlandı", new RectF(x0, ly, xr, ly + 24f * S), 0xFFC9B58A, 16f * S);
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
                openScores();
                break;
            case B_NEXT:
                if (ok.phase == Okey.MATCH_OVER) {
                    closeModal();
                    openMatchOver();
                } else {
                    submit(me, "N");
                }
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
                    layout(ok.arrange(ok.hand[me], 1));
                    sel = -1;
                }
                break;
            case B_PAIR:
                if (ok.phase == Okey.DRAW || ok.phase == Okey.DISCARD) {
                    layout(ok.arrangePairs(ok.hand[me]));
                    sel = -1;
                }
                break;
            case B_OPEN:
                openTap();
                break;
            default:
                break;
        }
    }

    /** Istakadaki bitişik taş bloklarını grup olarak okur; geçerli olanlarla açar / indirir. */
    private void openTap() {
        if (ok.turn != me || ok.phase != Okey.DISCARD) {
            banner = "Önce desteden taş çekmelisin";
            return;
        }
        List<List<Integer>> groups = rackGroups();
        boolean was = ok.opened[me];
        if (!ok.canLay(me, groups)) {
            if (!was) {
                banner = "Geçerli gruplar " + ok.lastOpenPoints + " puan; açmak için en az 101 gerek";
            } else {
                banner = "İndirilecek geçerli grup yok";
            }
            return;
        }
        StringBuilder sb = new StringBuilder("G ");
        for (int gi = 0; gi < groups.size(); gi++) {
            if (gi > 0) {
                sb.append(';');
            }
            List<Integer> g = groups.get(gi);
            for (int i = 0; i < g.size(); i++) {
                sb.append(i > 0 ? "." : "").append(g.get(i));
            }
        }
        submit(me, sb.toString());
    }

    /** Istakadaki bitişik taş bloklarından geçerli grupları çıkarır. */
    private List<List<Integer>> rackGroups() {
        List<List<Integer>> groups = new ArrayList<List<Integer>>();
        for (int row = 0; row < 2; row++) {
            List<Integer> blk = new ArrayList<Integer>();
            for (int col = 0; col <= PER_ROW; col++) {
                int t = col < PER_ROW ? slot[row * PER_ROW + col] : -1;
                if (t >= 0) {
                    blk.add(t);
                } else {
                    flushBlock(blk, groups);
                    blk = new ArrayList<Integer>();
                }
            }
        }
        return groups;
    }

    private int groupPoints(List<List<Integer>> groups) {
        int total = 0;
        for (List<Integer> g : groups) {
            Okey.Meld m = ok.buildMeld(g);
            if (m != null) {
                total += m.points();
            }
        }
        return total;
    }

    private void flushBlock(List<Integer> blk, List<List<Integer>> groups) {
        if (blk.size() < 3) {
            return;
        }
        if (ok.buildMeld(blk) != null) {
            groups.add(blk);
            return;
        }
        List<List<Integer>> g = ok.arrange(blk, 1);
        for (int i = 0; i < g.size() - 1; i++) {
            if (g.get(i).size() >= 3 && ok.buildMeld(g.get(i)) != null) {
                groups.add(g.get(i));
            }
        }
    }

    @Override
    public boolean onGameTap(float x, float y) {
        if (ok != null && ipR.contains(x, y)) {
            openScores();
            return true;
        }
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
                List<Integer> dl = ok.discards[ok.prev(me)];
                if (dl.isEmpty()) {
                    banner = "Alınacak taş yok";
                } else if (!ok.opened[me] && !ok.canOpenWith(me, dl.get(dl.size() - 1))) {
                    banner = "Bu taşla elini açamazsın; desteden çek";
                } else {
                    submit(me, "K");
                }
                return true;
            }
        }
        if (mine && ok.phase == Okey.DISCARD) {
            if (hit(discR[0], x, y)) {
                if (sel >= 0 && slot[sel] >= 0) {
                    submit(me, "X " + slot[sel]);
                } else {
                    banner = "Önce atacağın taşı seç";
                }
                return true;
            }
            for (int i = 0; i < meldR.size(); i++) {
                if (meldR.get(i).contains(x, y)) {
                    if (sel < 0 || slot[sel] < 0) {
                        banner = "İşlemek için önce ıstakadan bir taş seç";
                    } else if (!ok.opened[me]) {
                        banner = "Taş işlemek için önce elini açmalısın";
                    } else if (ok.hand[me].size() >= 2 && i < ok.table.size() && ok.canAdd(ok.table.get(i), slot[sel])) {
                        submit(me, "A " + i + " " + slot[sel]);
                    } else {
                        banner = "Bu taş oraya işlenemez";
                    }
                    return true;
                }
            }
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
        if (!ok.opened[me] && !ok.canOpenWith(me, dl.get(dl.size() - 1))) {
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
        centerText(c, "GÖSTERGE", new RectF(ind.centerX() - 90f * S, ind.bottom + 6f * S, ind.centerX() + 90f * S, ind.bottom + 34f * S),
                0xFFE9C46A, 19f * S);
        centerText(c, "OKEY: " + CardArt.OKEY_COLOR_NAMES[ok.okeyColor] + " " + ok.okeyNum,
                new RectF(ind.centerX() - 110f * S, ind.bottom + 32f * S, ind.centerX() + 110f * S, ind.bottom + 60f * S),
                0xFFFFF4D6, 19f * S);
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
        float half = ix1 - cx() - btnW - 2f * pad - 6f * S;
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
        paint.setColor(hot ? 0x66FFE282 : 0x44000000);
        c.drawRoundRect(r, 14f * S, 14f * S, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(hot ? 6f * S : 3f * S);
        paint.setColor(hot ? 0xFFFFE282 : 0x88D4AF37);
        c.drawRoundRect(r, 14f * S, 14f * S, paint);
    }

    private void label(Canvas c, String s, RectF r) {
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, s, new RectF(r.left - 30f * S, r.bottom + 6f * S, r.right + 30f * S, r.bottom + 36f * S), 0xFFE9C46A, 19f * S);
    }

    /** Masadaki gruplar: küçük taşlarla, sığmayan grup alt satıra geçer. */
    private void drawMelds(Canvas c, float x0, float y0, float x1, float y1) {
        meldR.clear();
        float mw = 50f * S;
        float mh = 70f * S;
        float gx = 3f * S;
        float gapM = 22f * S;
        float x = x0;
        float y = y0;
        for (int i = 0; i < ok.table.size(); i++) {
            Okey.Meld m = ok.table.get(i);
            int n = m.tiles.size();
            float wdt = n * mw + (n - 1) * gx;
            if (x + wdt > x1 && x > x0) {
                x = x0;
                y += mh + 14f * S;
            }
            RectF area = new RectF(x - 6f * S, y - 6f * S, x + wdt + 6f * S, y + mh + 6f * S);
            meldR.add(area);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x33000000);
            c.drawRoundRect(area, 10f * S, 10f * S, paint);
            for (int k = 0; k < n; k++) {
                int t = m.tiles.get(k);
                RectF r = new RectF(x + k * (mw + gx), y, x + k * (mw + gx) + mw, y + mh);
                if (ok.isJoker(t)) {
                    int num;
                    int col;
                    if (m.run) {
                        int v = m.nums.get(k);
                        num = v == 14 ? 1 : v;
                        col = m.color;
                    } else {
                        num = m.num;
                        col = 0;
                        for (int q = 0; q < 4; q++) {
                            boolean used = false;
                            for (int z : m.tiles) {
                                if (!ok.isJoker(z) && ok.natColor(z) == q) {
                                    used = true;
                                }
                            }
                            if (!used) {
                                col = q;
                                break;
                            }
                        }
                    }
                    CardArt.okeyFace(c, r, num, col, false, false, paint, text);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(3f * S);
                    paint.setColor(0xFFFFE282);
                    c.drawRoundRect(r, mw * 0.16f, mw * 0.16f, paint);
                } else {
                    tileFace(c, r, t, false);
                }
            }
            x += wdt + gapM;
        }
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
        for (int p = 0; p < 4; p++) {
            float[] s = seat(p);
            boolean turn = live && ok.turn == p;
            profileBox(c, s[0], s[1], names[p] + (online() && !human[p] ? " (BOT)" : ""), (ok.eshli && p != me && Okey101.teamOf(p) == Okey101.teamOf(me) ? "EŞİN · " : "") + "Toplam " + ok.score[p] + (ok.opened[p] ? " · AÇIK" : ""),
                    AVATARS[p % AVATARS.length], levels[p], turn, turnFrac(p));
            if (turn && rel(p) != 0 && turnFrac(p) < 0f) {
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, "SIRADA", new RectF(s[0] - 60f * S, s[1] - 90f * S, s[0] + 60f * S, s[1] - 58f * S),
                        0xFFFFE282, 20f * S);
            }
        }
        RectF ip = new RectF(ix1 - 320f * S, iy0 + 22f * S, ix1 - 22f * S, iy0 + 22f * S + 156f * S);
        ipR.set(ip);
        infoPanel(c, ip, new String[] {"EL " + ok.elNo + " / " + els, "DESTE " + ok.pile.size(), "AÇILIŞ EN AZ 101", "PUAN TABLOSU ▸"},
                new int[] {0xFFE9C46A, 0xFFFFF4D6, 0xFFFFF4D6, 0xFFE9C46A}, new float[] {26f, 22f, 22f, 18f});

        // ıstaka (ekranın tam ortasında)
        computeRack();
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
        float bx = rack.right + pad + 6f * S;
        float gb = 8f * S;
        float pillH = 66f * S;
        float bh3 = (rack.height() - 12f * S - pillH - 3f * gb) / 3f;
        RectF arr = new RectF(bx, rackT + 6f * S, bx + btnW, rackT + 6f * S + bh3);
        RectF par = new RectF(bx, arr.bottom + gb, bx + btnW, arr.bottom + gb + bh3);
        RectF opn = new RectF(bx, par.bottom + gb, bx + btnW, par.bottom + gb + bh3);
        woodButton(c, arr, "SERİ DİZ", false, live);
        woodButton(c, par, "ÇİFT DİZ", false, live);
        woodButton(c, opn, ok.opened[me] ? "İNDİR" : "AÇ", true, live);
        addBtn(arr, B_ARR, 0);
        addBtn(par, B_PAIR, 0);
        addBtn(opn, B_OPEN, 0);
        // eldeki dizili grupların toplamı
        {
            int gp = groupPoints(rackGroups());
            RectF pill = new RectF(bx, opn.bottom + gb, bx + btnW, rack.bottom - 6f * S);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1E120A);
            c.drawRoundRect(pill, 16f * S, 16f * S, paint);
            boolean enough = ok.opened[me] || gp >= 101;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * S);
            paint.setColor(enough && gp > 0 ? 0xFF7BD88F : 0xFF8C6937);
            c.drawRoundRect(pill, 16f * S, 16f * S, paint);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            centerText(c, ok.opened[me] ? "İNDİRİLEBİLİR" : "DİZİLİ GRUPLAR",
                    new RectF(pill.left, pill.top + 4f * S, pill.right, pill.top + 30f * S), 0xFFE9C46A, 18f * S);
            centerText(c, ok.opened[me] ? String.valueOf(gp) : gp + " / 101",
                    new RectF(pill.left, pill.top + 28f * S, pill.right, pill.bottom - 4f * S),
                    enough && gp > 0 ? 0xFF9BE8AA : 0xFFFFF4D6, 34f * S);
        }

        float cx = cx();
        // deste (üst orta, üstteki oyuncunun sağında); sayısı üstünde, gösterge hemen yanında
        deckR.set(cx + 250f * S, iy0 + 66f * S, cx + 250f * S + tw, iy0 + 66f * S + th);
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
            int ab = (me - p + 4) & 3;
            float[] sc = seat(ab);
            float ax = sc[0] + 150f * S - 34f * S;
            float ay = sc[1] + 50f * S - 16f * S;
            discR[p].set(ax, ay, ax + dw, ay + dh);
            boolean hot = live && ((p == 1 && ok.turn == me && ok.phase == Okey.DRAW && !ok.discards[ab].isEmpty())
                    || (p == 0 && ok.turn == me && ok.phase == Okey.DISCARD));
            boolean takeBlocked = p == 1 && live && ok.turn == me && ok.phase == Okey.DRAW && !ok.discards[ab].isEmpty()
                    && !ok.opened[me] && !ok.canOpenWith(me, ok.discards[ab].get(ok.discards[ab].size() - 1));
            if (takeBlocked) {
                hot = false;
            }
            List<Integer> dl = ok.discards[ab];
            int show = Math.min(3, dl.size());
            if (show == 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth((hot ? 4f : 2f) * S);
                paint.setColor(hot ? 0xFFFFE282 : 0x55FFF4D6);
                c.drawRoundRect(discR[p], dw * 0.16f, dw * 0.16f, paint);
            }
            for (int k = 0; k < show; k++) {
                int back = show - 1 - k;
                RectF r = new RectF(discR[p]);
                r.offset(-14f * S * back, -11f * S * back);
                tileFace(c, r, dl.get(dl.size() - 1 - back), takeBlocked && back == 0);
            }
            if (hot) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5f * S);
                paint.setColor(0xFFFFE282);
                c.drawRoundRect(discR[p], dw * 0.16f, dw * 0.16f, paint);
            }
            text.setTypeface(Typeface.DEFAULT_BOLD);
            if (hot && p == 1) {
                label(c, "AL ▲", discR[p]);
            } else if (hot && p == 0) {
                centerText(c, "AT ▾", new RectF(discR[p].right + 4f * S, discR[p].top, discR[p].right + 70f * S, discR[p].top + 30f * S),
                        0xFFFFE282, 18f * S);
            } else if (takeBlocked) {
                label(c, "AÇAMAZSIN", discR[p]);
            }
        }
        // masadaki gruplar
        if (ok.table.isEmpty()) {
            meldR.clear();
            text.setTypeface(Typeface.DEFAULT_BOLD);
            centerText(c, "Masada henüz açılmış grup yok", new RectF(cx - 330f * S, 250f * S, cx + 330f * S, 300f * S),
                    0x88FFF4D6, 24f * S);
        } else {
            drawMelds(c, ix0 + 440f * S, 215f * S, ix1 - 440f * S, 400f * S);
        }
        drawBubbles(c);
        drawGhost(c);
        String msg = banner;
        if (msg.length() == 0 && live) {
            if (ok.turn == me) {
                msg = ok.phase == Okey.DRAW ? "SIRA SENDE: DESTEDEN ÇEK ya da ışıklı atılan taşı AL"
                        : "Taşı boş yere taşı ya da seçip AT kutusuna dokun";
            } else {
                msg = names[ok.turn] + " oynuyor…";
            }
        }
        if (msg.length() > 0 && ok.phase != Okey.MATCH_OVER) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(26f * S);
            float bww = Math.min(text.measureText(msg) + 60f * S, getWidth() * 0.8f);
            float[] mySeat = seat(me);
            RectF b = new RectF(cx - bww / 2f, mySeat[1] - 50f * S - 58f * S, cx + bww / 2f, mySeat[1] - 50f * S - 12f * S);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1E120A);
            c.drawRoundRect(b, 23f * S, 23f * S, paint);
            centerText(c, msg, b, 0xFFFFF4D6, 26f * S);
        }
    }

    public float deckCenterX() { return deckR.centerX(); }
    public float deckCenterY() { return deckR.centerY(); }
    public float discardCenterX(int p) { return discR[p].centerX(); }
    public float discardCenterY(int p) { return discR[p].centerY(); }
    public RectF slotRect(int i) { return slotR[i]; }
    public int slotOf(int t) { for (int i = 0; i < SLOTS; i++) { if (slot[i] == t) { return i; } } return -1; }

    @Override
    public String rulesText() {
        return rules();
    }

    public static String rules() {
        return "## Taşlar\n"
                + "106 taş (4 renk × 1-13 × 2 takım + 2 sahte okey). Gösterge ile aynı renkte, bir büyük sayılı taş (13'ün üstü 1) OKEYdir ve joker yerine geçer. "
                + "Sahte okey, okeyin yerine geçen sıradan bir taştır.\n\n"
                + "## Dağıtım\n"
                + "Herkese 21 taş verilir; dağıtandan sonraki oyuncu 22 taş alır ve oyuna taş atarak başlar.\n\n"
                + "## Sıra\n"
                + "Desteden bir taş çekersin ya da önceki oyuncunun attığı taşı alırsın. Elini henüz açmadıysan atılan taşı yalnızca onunla elini açabiliyorsan alabilirsin. "
                + "Sonunda bir taş atarsın.\n\n"
                + "## Elini açmak\n"
                + "Gruplarının (seri ya da per) puan toplamı en az 101 olunca elini açarsın. Grup puanı taşların sayı toplamıdır; joker yerine geçtiği sayıyı taşır. "
                + "Seride 1 yalnız başta kullanılır (1-2-3 olur; 12-13-1 ve 13-1-2 olmaz). Gruplar: aynı renkten ardışık en az 3 taş ya da aynı sayıdan farklı renkli 3-4 taş.\n"
                + "Açmak için gruplarını ıstakada yan yana, aralarında boşluk bırakarak diz (DİZ düğmesi bunu kendisi yapar), sonra AÇ'a bas.\n\n"
                + "## İşleme\n"
                + "Taşlar yer değiştirmez, araya girer; gerçek perlerin üstünde çıkan altın tutamaçtan sürüklersen per bütün olarak taşınır. Elini açtıktan sonra masadaki herhangi bir gruba uyan taşı işleyebilirsin: ıstakadan taşı seç, masadaki gruba dokun. "
                + "Elinde yeni grup oluşursa İNDİR ile masaya indirirsin.\n\n"
                + "## Eli bitirmek\n"
                + "Elindeki son taşı atan (ya da hepsini indiren) eli bitirir.\n\n"
                + "## Puan (ceza)\n"
                + "Eli bitiren -101 yazar. Elini hiç açmamış oyuncu 202 ceza alır. Açmış oyuncu elinde kalan taşların sayı toplamını alır (okey 25). "
                + "Deste biterse el berabere kalır. Seçtiğin el sayısı (7, 9 ya da 11) bitince en az ceza puanı olan kazanır. Oyun içinde sağ üstteki kutuya dokunarak puan tablosunu görürsün.\n\n"
                + "## Kural seçenekleri\n"
                + "EŞLİ: karşılıklı oturanlar takım olur; takımın puanları toplanır, en düşük toplamlı takım kazanır.\n"
                + "KATLAMA: okey atarak bitirirsen puanlar 2 katı, hiçbiri elini açmadan tüm taşlarını tek turda açıp bitirirsen (elden bitirme) 2 katı olur; ikisi birlikte 4 kat. "
                + "Elden bitirmede rakipler 404, okeyle elden bitirmede 808 ceza alır.\n"
                + "CEZA: okey (joker) atmak ya da masadaki bir gruba işlenebilecek taşı atmak +101 ceza yazar. Bitirirken attığın son taş cezasızdır.";
    }
}
