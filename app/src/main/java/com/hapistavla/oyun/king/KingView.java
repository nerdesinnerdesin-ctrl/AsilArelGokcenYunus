package com.hapistavla.oyun.king;

import com.hapistavla.oyun.*;
import com.hapistavla.oyun.salon.CardArt;
import com.hapistavla.oyun.salon.Cards;
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

/** King (Rıfkı) masası: 20 el, her el dağıtan oyunu seçer; 3 bilgisayar rakip. */
public final class KingView extends SalonBase {
    private static final int K_PICK = K_CUSTOM + 1;
    private static final int K_EL = K_CUSTOM + 2;
    private static final int K_TABLE = K_CUSTOM + 3;
    private static final int K_MATCH = K_CUSTOM + 4;

    private static final int B_PICK = 50;
    private static final int B_NEXT = 51;
    private static final int B_TABLE = 52;
    private static final int B_ELOK = 53;

    public King king;
    private int sel = -1;
    private boolean resultSent = false;
    public final List<RectF> handRects = new ArrayList<RectF>();
    public final List<Integer> handCards = new ArrayList<Integer>();

    public KingView(Context ctx) {
        super(ctx, true, "KING");
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
        king = new King(new Random(sd));
        resultSent = false;
        newEl();
    }

    private void newEl() {
        king.newEl();
        sel = -1;
        banner = names[king.chooser] + " oyunu seçiyor";
        step();
    }

    // ------------------------------------------------------------------
    // Akış
    // ------------------------------------------------------------------

    private boolean paused() {
        if (online()) {
            return false;
        }
        return modalKind == K_MENU || modalKind == K_RULES || modalKind == K_EXIT || modalKind == K_TABLE;
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
        if (king == null) {
            return;
        }
        switch (king.phase) {
            case King.CHOOSE:
                armTimer(tk, king.chooser, moveNo, new Runnable() {
                    @Override
                    public void run() {
                        if (king.phase == King.CHOOSE) {
                            submit(king.chooser, "S " + king.botChoose(king.chooser));
                        }
                    }
                });
                if (king.chooser == me) {
                    if (modalKind != K_PICK) {
                        openPick();
                    }
                } else if (authority() && botSeat(king.chooser)) {
                    final int p = king.chooser;
                    delayed(tk, new Runnable() {
                        @Override
                        public void run() {
                            submit(p, "S " + king.botChoose(p));
                        }
                    }, 1100);
                }
                break;
            case King.PLAY:
                if (!king.trickComplete()) {
                    armTimer(tk, king.turn, moveNo, new Runnable() {
                        @Override
                        public void run() {
                            if (king.phase == King.PLAY && !king.trickComplete()) {
                                submit(king.turn, "P " + king.botPlay());
                            }
                        }
                    });
                } else {
                    clearTimer();
                }
                if (king.trickComplete()) {
                    if (authority()) {
                        delayed(tk, new Runnable() {
                            @Override
                            public void run() {
                                submit(king.turn, "C");
                            }
                        }, 1100);
                    }
                } else if (king.turn != me) {
                    banner = "";
                    if (authority() && botSeat(king.turn)) {
                        final int p = king.turn;
                        delayed(tk, new Runnable() {
                            @Override
                            public void run() {
                                submit(p, "P " + king.botPlay());
                            }
                        }, 700);
                    }
                } else {
                    banner = "Sıra sende";
                }
                break;
            case King.EL_OVER:
                clearTimer();
                delayed(tk, new Runnable() {
                    @Override
                    public void run() {
                        if (king.phase == King.EL_OVER && modalKind != K_EL) {
                            openElOver();
                        }
                    }
                }, 800);
                if (online() && authority()) {
                    delayed(tk, new Runnable() {
                        @Override
                        public void run() {
                            submit(me, "N");
                        }
                    }, 9000);
                }
                break;
            case King.MATCH_OVER:
                clearTimer();
                delayed(tk, new Runnable() {
                    @Override
                    public void run() {
                        if (modalKind != K_MATCH) {
                            openMatchOver();
                        }
                    }
                }, 800);
                break;
            default:
                break;
        }
    }

    @Override
    public boolean applyCmd(int seat, String cmd) {
        if (king == null || cmd.length() == 0) {
            return false;
        }
        String[] f = cmd.split(" ");
        try {
            switch (f[0].charAt(0)) {
                case 'S': {
                    int g = Integer.parseInt(f[1]);
                    if (king.phase != King.CHOOSE || king.chooser != seat || !king.canChoose(seat, g) || !king.choose(g)) {
                        return false;
                    }
                    if (modalKind == K_PICK) {
                        closeModal();
                    }
                    banner = seat == me ? "Seçtin: " + King.NAMES[g] + " · ilk eli sen aç"
                            : names[seat] + " seçti: " + King.NAMES[g] + " · ilk eli o açar";
                    step();
                    return true;
                }
                case 'P': {
                    int card = Integer.parseInt(f[1]);
                    if (king.phase != King.PLAY || king.turn != seat || king.trickComplete() || !king.isLegal(seat, card)
                            || !king.play(card)) {
                        return false;
                    }
                    if (seat == me) {
                        sel = -1;
                    }
                    sound(Game.SND_MOVE);
                    step();
                    return true;
                }
                case 'C': {
                    if (king.phase != King.PLAY || !king.trickComplete()) {
                        return false;
                    }
                    int w = king.collectTrick();
                    banner = names[w] + " eli aldı";
                    step();
                    return true;
                }
                case 'N': {
                    if (king.phase != King.EL_OVER) {
                        return false;
                    }
                    if (modalKind == K_EL) {
                        closeModal();
                    }
                    newEl();
                    return true;
                }
                case 'M': {
                    if (king.phase != King.MATCH_OVER) {
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

    private void openPick() {
        int c = King.MAX_CEZA - king.cezaUsed[me];
        int k = King.MAX_KOZ - king.kozUsed[me];
        openModal(K_PICK, "OYUN SEÇ", "Kalan hakların: " + c + " ceza · " + k + " koz (el " + king.elNo + "/" + King.ELS + ")",
                new MB[0]);
    }

    private void openElOver() {
        openModal(K_EL, "EL BİTTİ", King.NAMES[king.game] + " · " + King.SHORT[king.game],
                online() && !authority() ? new MB(B_ELOK, "TAMAM", true) : new MB(B_NEXT, "SONRAKİ EL", true));
    }

    private void openMatchOver() {
        boolean win = king.winner == me;
        if (!resultSent) {
            resultSent = true;
            sound(win ? Game.SND_WIN : Game.SND_LOSE);
            if (host != null) {
                if (online()) {
                    host.onSalonOnlineResult(SalonBase.G_KING, win, 3);
                } else {
                    host.onSalonResult(SalonBase.G_KING, win, 3);
                }
            }
        }
        openModal(K_MATCH, win ? "KAZANDIN!" : names[king.winner] + " KAZANDI", "20 el bitti · en yüksek puan kazanır",
                new MB(B_REMATCH, online() ? "RÖVANŞ" : "YENİ MAÇ", true), new MB(B_EXIT_YES, "SALONA DÖN", false));
    }

    @Override
    public RectF modalRect(int kind) {
        float w = getWidth();
        float h = getHeight();
        if (kind == K_PICK) {
            float mw = Math.min(w * 0.72f, 1250f * S);
            float top = iy0 + 70f * S;
            return new RectF(w / 2f - mw / 2f, top, w / 2f + mw / 2f, top + 560f * S);
        }
        if (kind == K_TABLE) {
            float mw = Math.min(w * 0.7f, 1250f * S);
            float mh = h - 2f * iy0 + 30f * S;
            return new RectF(w / 2f - mw / 2f, (h - mh) / 2f, w / 2f + mw / 2f, (h + mh) / 2f);
        }
        if (kind == K_EL || kind == K_MATCH) {
            float mw = Math.min(w * 0.6f, 1000f * S);
            float mh = 640f * S;
            return new RectF(w / 2f - mw / 2f, (h - mh) / 2f, w / 2f + mw / 2f, (h + mh) / 2f);
        }
        return super.modalRect(kind);
    }

    @Override
    public boolean lightDim(int kind) {
        return kind == K_PICK;
    }

    @Override
    public void drawCustomModal(Canvas c, int kind, RectF body) {
        if (kind == K_PICK) {
            float gap = 14f * S;
            text.setTypeface(Typeface.DEFAULT_BOLD);
            centerText(c, "CEZA OYUNLARI", new RectF(body.left, body.top, body.right, body.top + 30f * S), 0xFFE9C46A, 22f * S);
            float bw = (body.width() - gap * 5) / 6f;
            float bh = 96f * S;
            float y = body.top + 36f * S;
            for (int g = 0; g < 6; g++) {
                RectF b = new RectF(body.left + g * (bw + gap), y, body.left + g * (bw + gap) + bw, y + bh);
                boolean ok = king.canChoose(me, g);
                woodButton(c, b, "", ok, ok);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, King.NAMES[g], new RectF(b.left, b.top + 8f * S, b.right, b.top + bh * 0.58f),
                        ok ? 0xFFFFF4D6 : 0xFF9C8C70, 26f * S);
                centerText(c, King.SHORT[g], new RectF(b.left, b.top + bh * 0.58f, b.right, b.bottom - 6f * S),
                        ok ? 0xFFF1D9A0 : 0xFF84765E, 17f * S);
                if (ok) {
                    addBtn(b, B_PICK, g);
                }
            }
            float y2 = y + bh + 24f * S;
            centerText(c, "KOZ OYUNLARI  (alınan el başı +50)", new RectF(body.left, y2, body.right, y2 + 30f * S), 0xFFE9C46A, 22f * S);
            int[] order = {CardArt.SPADES, CardArt.HEARTS, CardArt.DIAMONDS, CardArt.CLUBS};
            float kw = (body.width() - gap * 3) / 4f;
            float ky = y2 + 36f * S;
            for (int i = 0; i < 4; i++) {
                int su = order[i];
                RectF b = new RectF(body.left + i * (kw + gap), ky, body.left + i * (kw + gap) + kw, ky + bh);
                boolean ok = king.canChoose(me, King.KOZ0 + su);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ok ? 0xFFFBF7EA : 0x88FBF7EA);
                c.drawRoundRect(b, 22f * S, 22f * S, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(4f * S);
                paint.setColor(ok ? 0xFFD4AF37 : 0x88D4AF37);
                c.drawRoundRect(b, 22f * S, 22f * S, paint);
                CardArt.suit(c, su, b.left + kw * 0.3f, b.centerY(), bh * 0.5f,
                        ok ? (CardArt.isRed(su) ? CardArt.RED : CardArt.INK) : 0xFF9C9C9C, paint);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, "KOZ " + CardArt.SUIT_NAMES[su], new RectF(b.left + kw * 0.45f, b.top, b.right - 8f * S, b.bottom),
                        ok ? 0xFF3C230A : 0xFF8A8A8A, 26f * S);
                if (ok) {
                    addBtn(b, B_PICK, King.KOZ0 + su);
                }
            }
        } else if (kind == K_EL || kind == K_MATCH) {
            drawTotals(c, body, kind == K_EL);
        } else if (kind == K_TABLE) {
            drawTable(c, body);
        }
    }

    private void drawTotals(Canvas c, RectF body, boolean delta) {
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
                int d = king.elDelta[p];
                text.setColor(d > 0 ? 0xFF8FE08F : (d < 0 ? 0xFFFF8A80 : 0xFFC9B58A));
                text.setTextSize(32f * S);
                c.drawText(d == 0 ? "—" : (d > 0 ? "+" + d : String.valueOf(d)), xd, cy + 11f * S, text);
            }
            text.setColor(0xFFE9C46A);
            text.setTextSize(34f * S);
            c.drawText(String.valueOf(king.score[p]), xs, cy + 12f * S, text);
        }
    }

    /** 20 satırlık puan tablosu. */
    private void drawTable(Canvas c, RectF body) {
        int rows = King.ELS + 2;
        float rh = body.height() / rows;
        float fs = Math.min(22f * S, rh * 0.75f);
        float xNo = body.left + 12f * S;
        float xGame = body.left + 70f * S;
        float colW = (body.width() - 380f * S) / 4f;
        text.setTypeface(Typeface.DEFAULT_BOLD);
        leftText(c, "#", xNo, body.top + rh * 0.5f, 0xFFC9B58A, fs, 50f * S);
        leftText(c, "OYUN", xGame, body.top + rh * 0.5f, 0xFFC9B58A, fs, 280f * S);
        for (int p = 0; p < 4; p++) {
            float cx = body.left + 380f * S + colW * (p + 0.5f);
            centerText(c, names[p], new RectF(cx - colW / 2f, body.top, cx + colW / 2f, body.top + rh), 0xFFFFF4D6, fs);
        }
        for (int i = 0; i < King.ELS; i++) {
            float y = body.top + rh * (i + 1.5f);
            boolean played = i < king.elNo - (king.phase == King.EL_OVER || king.phase == King.MATCH_OVER ? 0 : 1);
            if (i % 2 == 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x22FFFFFF);
                c.drawRect(body.left, body.top + rh * (i + 1), body.right, body.top + rh * (i + 2), paint);
            }
            leftText(c, String.valueOf(i + 1), xNo, y, 0xFFC9B58A, fs, 50f * S);
            if (played) {
                leftText(c, King.NAMES[king.gameOf[i]], xGame, y, 0xFFF1E4C4, fs, 290f * S);
                for (int p = 0; p < 4; p++) {
                    int d = king.history[i][p];
                    float cx = body.left + 380f * S + colW * (p + 0.5f);
                    centerText(c, d == 0 ? "—" : (d > 0 ? "+" + d : String.valueOf(d)),
                            new RectF(cx - colW / 2f, y - rh / 2f, cx + colW / 2f, y + rh / 2f),
                            d > 0 ? 0xFF8FE08F : (d < 0 ? 0xFFFF8A80 : 0xFFC9B58A), fs);
                }
            } else {
                leftText(c, "—", xGame, y, 0x66C9B58A, fs, 290f * S);
            }
        }
        float y = body.top + rh * (King.ELS + 1.5f);
        leftText(c, "TOPLAM", xGame, y, 0xFFE9C46A, fs * 1.1f, 290f * S);
        for (int p = 0; p < 4; p++) {
            float cx = body.left + 380f * S + colW * (p + 0.5f);
            centerText(c, String.valueOf(king.score[p]), new RectF(cx - colW / 2f, y - rh / 2f, cx + colW / 2f, y + rh / 2f),
                    0xFFE9C46A, fs * 1.1f);
        }
    }

    // ------------------------------------------------------------------
    // Düğmeler ve dokunma
    // ------------------------------------------------------------------

    @Override
    public MB menuExtra() {
        return new MB(B_TABLE, "PUAN TABLOSU", false);
    }

    @Override
    public void onGameButton(int id, int arg) {
        switch (id) {
            case B_TABLE:
                openModal(K_TABLE, "PUAN TABLOSU", "", new MB(B_MODAL_BACK, "‹ GERİ", true));
                break;
            case B_PICK:
                submit(me, "S " + arg);
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
            default:
                break;
        }
    }

    @Override
    public boolean onGameTap(float x, float y) {
        if (king == null || king.phase != King.PLAY || king.turn != me || king.trickComplete()) {
            return false;
        }
        for (int i = handRects.size() - 1; i >= 0; i--) {
            if (handRects.get(i).contains(x, y)) {
                int card = handCards.get(i);
                if (!king.isLegal(me, card)) {
                    if (king.trickCount == 0) {
                        banner = king.trump >= 0 ? "Koz düşmeden koz ile başlanamaz" : "Kupa açılmadan kupa ile başlanamaz";
                    } else if (king.trump >= 0 && Cards.suit(king.trick[king.trickLeader]) == king.trump
                            && Cards.suit(card) == king.trump) {
                        banner = "Koz çekilmiş: masadaki en büyük kozdan büyük koz atmalısın";
                    } else {
                        banner = "Bu kartı atamazsın: elindeki renge uymalısın";
                    }
                    return true;
                }
                if (sel == card) {
                    submit(me, "P " + card);
                } else {
                    sel = card;
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Çizim
    // ------------------------------------------------------------------

    @Override
    public float[] seatCenter(int p) {
        return seat(p);
    }

    private float[] seat(int p) {
        float w = getWidth();
        switch (rel(p)) {
            case 1:
                return new float[] {ix0 + 190f * S, 330f * S};
            case 2:
                return new float[] {w / 2f, iy0 + 78f * S};
            case 3:
                return new float[] {ix1 - 190f * S, 330f * S};
            default:
                return new float[] {cx(), 606f * S};
        }
    }

    private float[] slot(int p) {
        float cx = cx();
        switch (rel(p)) {
            case 1:
                return new float[] {cx - 135f * S, 420f * S};
            case 2:
                return new float[] {cx, 320f * S};
            case 3:
                return new float[] {cx + 135f * S, 420f * S};
            default:
                return new float[] {cx, 470f * S};
        }
    }

    @Override
    public void drawGame(Canvas c) {
        if (king == null) {
            return;
        }
        if (modalKind == K_NONE) {
            drawMenuButton(c);
        }
        boolean playing = king.phase == King.PLAY;
        for (int p = 0; p < 4; p++) {
            float[] s = seat(p);
            String l2 = playing || king.phase == King.EL_OVER ? "Puan " + king.score[p] + " · El " + king.tricksWon[p]
                    : "Puan " + king.score[p];
            boolean turn = (playing && king.turn == p && !king.trickComplete())
                    || (king.phase == King.CHOOSE && king.chooser == p);
            profileBox(c, s[0], s[1], names[p] + (online() && !human[p] ? " (BOT)" : ""), l2, AVATARS[p % AVATARS.length], levels[p], turn, turnFrac(p));
            if (turn && turnFrac(p) < 0f) {
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, king.phase == King.CHOOSE ? "SEÇİYOR" : "SIRADA",
                        new RectF(s[0] - 60f * S, s[1] - 90f * S, s[0] + 60f * S, s[1] - 58f * S), 0xFFFFE282, 20f * S);
            }
        }
        // bilgi paneli
        RectF ip = new RectF(ix1 - 340f * S, iy0 + 22f * S, ix1 - 22f * S, iy0 + 22f * S + 150f * S);
        String g = king.game >= 0 ? King.NAMES[king.game] : "—";
        String r = king.game >= 0 ? King.SHORT[king.game] : "";
        infoPanel(c, ip, new String[] {"EL " + king.elNo + " / " + King.ELS, g, r},
                new int[] {0xFFE9C46A, 0xFFFFF4D6, 0xFFC9B58A}, new float[] {24f, 24f, 19f});
        drawBubbles(c);
        if (banner.length() > 0 && king.phase != King.MATCH_OVER) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(26f * S);
            float bw = Math.min(text.measureText(banner) + 60f * S, getWidth() * 0.62f);
            RectF b = new RectF(cx() - bw / 2f, 196f * S, cx() + bw / 2f, 196f * S + 46f * S);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1E120A);
            c.drawRoundRect(b, 23f * S, 23f * S, paint);
            centerText(c, banner, b, 0xFFFFF4D6, 26f * S);
        }
        float tw = 100f * S;
        float th = 146f * S;
        for (int p = 0; p < 4; p++) {
            int cd = king.trick[p];
            if (cd >= 0) {
                float[] s = slot(p);
                RectF rr = new RectF(s[0] - tw / 2f, s[1] - th / 2f, s[0] + tw / 2f, s[1] + th / 2f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x55000000);
                c.drawRoundRect(new RectF(rr.left + 5 * S, rr.top + 7 * S, rr.right + 5 * S, rr.bottom + 7 * S), 12f * S, 12f * S, paint);
                CardArt.cardFace(c, rr, Cards.rank(cd), Cards.suit(cd), false, paint, text);
            }
        }
        handRects.clear();
        handCards.clear();
        if (king.phase == King.CHOOSE || king.phase == King.PLAY) {
            int[] h = king.hand[me];
            int n = h.length;
            if (n > 0) {
                float cw = 122f * S;
                float ch = 176f * S;
                float avail = getWidth() - 2f * ix0 - 80f * S;
                float step = n > 1 ? Math.min(cw * 0.86f, (avail - cw) / (n - 1)) : 0f;
                float x0 = cx() - (step * (n - 1) + cw) / 2f;
                float yb = iy1 - ch - 12f * S;
                boolean myTurn = playing && king.turn == me && !king.trickComplete();
                int[] lg = myTurn ? king.legal(me) : new int[0];
                for (int i = 0; i < n; i++) {
                    int cd = h[i];
                    boolean legal = !myTurn || Cards.has(lg, cd);
                    float lift = (cd == sel) ? 34f * S : 0f;
                    RectF rr = new RectF(x0 + i * step, yb - lift, x0 + i * step + cw, yb - lift + ch);
                    CardArt.cardFace(c, rr, Cards.rank(cd), Cards.suit(cd), myTurn && !legal, paint, text);
                    handRects.add(rr);
                    handCards.add(cd);
                }
            }
        }
    }

    @Override
    public String rulesText() {
        return rules();
    }

    public static String rules() {
        return "## Genel\n"
                + "King (Rıfkı) 4 kişiyle, 52 kartla oynanır; herkese 13 kart. Oyun 20 eldir: her oyuncu 5 kez dağıtır ve o elin oyununu seçer. "
                + "Her oyuncu 3 ceza ve 2 koz oyunu seçer; aynı oyunu bir kez seçer, her oyun masada toplam 2 kez oynanır (6 ceza x 2 + 4 koz x 2 = 20 el). Seçen oyuncu ilk kartı açar, eli alan sonraki eli açar. "
                + "Oynanan renge uymak zorundasın, rengin yoksa istediğin kartı atarsın.\n\n"
                + "## Ceza oyunları (puan düşer)\n"
                + "• Kupa almaz: aldığın her kupa kartı -30. İlk kupa oynanmadan kupa ile el açılamaz (elinde yalnız kupa varsa açabilirsin).\n"
                + "• Erkek almaz: aldığın her papaz ve vale -60.\n"
                + "• Kız almaz: aldığın her kız (Q) -100.\n"
                + "• Rıfkı: kupa papazını alırsan -320. Burada da kupa açılmadan kupa ile el açılamaz.\n"
                + "• El almaz: aldığın her el -50.\n"
                + "• Son iki: son iki eli alırsan her biri -180.\n\n"
                + "## Koz oyunları (puan kazanılır)\n"
                + "Seçilen takım kozdur. Aldığın her el +50. Koz, diğer renkleri keser; kozlar arasında büyük olan kazanır. Koz düşmeden koz ile el açılamaz (elinde yalnız koz varsa açabilirsin). Koz çekilmişse ve elinde varsa masadaki en büyük kozdan büyük koz atmak zorundasın; rengin yoksa kozla kesmek serbesttir.\n\n"
                + "## Bitiş\n"
                + "20 el sonunda en yüksek toplam puanı olan kazanır. PUAN TABLOSU menüde her elin sonucunu gösterir.\n\n"
                + "## Kullanım\n"
                + "Sıra sende iken bir karta dokunarak yukarı kaldır, ikinci kez dokunarak at. Soluk kartlar atılamaz.";
    }
}
