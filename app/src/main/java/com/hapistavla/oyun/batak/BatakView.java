package com.hapistavla.oyun.batak;

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

/** Batak masası: 3 bilgisayar rakip, ihale, koz seçimi, el oynama. Oyuncu 0 = sen (altta). */
public final class BatakView extends SalonBase {
    private static final int K_BID = K_CUSTOM + 1;
    private static final int K_TRUMP = K_CUSTOM + 2;
    private static final int K_FORCED = K_CUSTOM + 3;
    private static final int K_HAND = K_CUSTOM + 4;
    private static final int K_MATCH = K_CUSTOM + 5;
    private static final int K_SCORES = K_CUSTOM + 6;

    private static final int B_BID = 30;
    private static final int B_PAS = 31;
    private static final int B_TRUMP = 32;
    private static final int B_FORCED_OK = 33;
    private static final int B_NEXT = 34;

    private final int target;
    public Batak bat;
    private final String[] tag = new String[4];
    private int sel = -1;
    private boolean resultSent = false;
    public final List<RectF> handRects = new ArrayList<RectF>();
    public final List<Integer> handCards = new ArrayList<Integer>();

    private final boolean open;

    public BatakView(Context ctx, int target) {
        this(ctx, target, false);
    }

    public BatakView(Context ctx, int target, boolean open) {
        super(ctx, true, open ? "AÇMALI BATAK" : "BATAK");
        this.target = target;
        this.open = open;
    }

    /** Çevrimdışı: 3 bilgisayar rakip. */
    public void start() {
        setupOffline();
        newMatch(new java.util.Random().nextLong());
    }

    /** Çevrimiçi: koltuklar ve tohum belli. */
    public void startOnline(Link l, int mySeat, String[] nm, int[] lv, String[] cd, long sd) {
        setupOnline(l, mySeat, nm, lv, cd, sd);
        newMatch(sd);
    }

    private void newMatch(long sd) {
        cancelTimers();
        closeModal();
        bat = new Batak(target, new Random(sd), open);
        resultSent = false;
        newHand();
    }

    private void newHand() {
        bat.newHand();
        for (int i = 0; i < 4; i++) {
            tag[i] = "";
        }
        sel = -1;
        banner = bat.cancelCount > 0 ? names[bat.cancelSeat] + " elinde 10 üstü kart yok: el iptal, yeniden dağıtıldı"
                : names[bat.dealer] + " dağıttı";
        step();
    }

    // ------------------------------------------------------------------
    // Oyun akışı
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

    /** Durumu ekrana yansıtır; sıra bottaysa (ve oyunu yürüten bizsek) bot hamlesini planlar. */
    public void step() {
        invalidate();
        final int tk = ++token;
        if (bat == null) {
            return;
        }
        switch (bat.phase) {
            case Batak.BID:
                armTimer(tk, bat.turn, moveNo, new Runnable() {
                    @Override
                    public void run() {
                        if (bat.phase == Batak.BID) {
                            submit(bat.turn, "B " + bat.botBid(bat.turn));
                        }
                    }
                });
                if (bat.turn == me) {
                    if (modalKind != K_BID) {
                        openBid();
                    }
                } else {
                    if (modalKind == K_BID) {
                        closeModal();
                    }
                    if (authority() && botSeat(bat.turn)) {
                        final int p = bat.turn;
                        delayed(tk, new Runnable() {
                            @Override
                            public void run() {
                                submit(p, "B " + bat.botBid(p));
                            }
                        }, 800);
                    }
                }
                break;
            case Batak.TRUMP:
                armTimer(tk, bat.declarer, moveNo, new Runnable() {
                    @Override
                    public void run() {
                        if (bat.phase == Batak.TRUMP) {
                            submit(bat.declarer, "T " + bat.botTrump(bat.declarer));
                        }
                    }
                });
                if (bat.declarer == me) {
                    if (modalKind != K_TRUMP && modalKind != K_FORCED) {
                        openTrump();
                    }
                } else if (authority() && botSeat(bat.declarer)) {
                    final int p = bat.declarer;
                    delayed(tk, new Runnable() {
                        @Override
                        public void run() {
                            submit(p, "T " + bat.botTrump(p));
                        }
                    }, bat.forced ? 2600 : 900);
                }
                break;
            case Batak.PLAY:
                if (!bat.trickComplete()) {
                    armTimer(tk, bat.turn, bat.actor(), moveNo, new Runnable() {
                        @Override
                        public void run() {
                            if (bat.phase == Batak.PLAY && !bat.trickComplete()) {
                                submit(bat.actor(), "P " + bat.botPlay());
                            }
                        }
                    });
                } else {
                    clearTimer();
                }
                if (bat.trickComplete()) {
                    if (authority()) {
                        delayed(tk, new Runnable() {
                            @Override
                            public void run() {
                                submit(bat.turn, "C");
                            }
                        }, 1100);
                    }
                } else if (bat.actor() != me) {
                    banner = "";
                    if (authority() && botSeat(bat.actor())) {
                        final int p = bat.actor();
                        delayed(tk, new Runnable() {
                            @Override
                            public void run() {
                                submit(p, "P " + bat.botPlay());
                            }
                        }, 700);
                    }
                } else {
                    banner = bat.turn == me ? "Sıra sende" : "Eşinin kartını sen oyna: " + names[bat.turn];
                }
                break;
            case Batak.HAND_OVER:
                clearTimer();
                delayed(tk, new Runnable() {
                    @Override
                    public void run() {
                        if (bat.phase == Batak.HAND_OVER && modalKind != K_HAND) {
                            openHandOver();
                        }
                    }
                }, 700);
                if (online() && authority()) {
                    delayed(tk, new Runnable() {
                        @Override
                        public void run() {
                            submit(me, "N");
                        }
                    }, 9000);
                }
                break;
            case Batak.MATCH_OVER:
                clearTimer();
                delayed(tk, new Runnable() {
                    @Override
                    public void run() {
                        if (modalKind != K_MATCH) {
                            openMatchOver();
                        }
                    }
                }, 700);
                break;
            default:
                break;
        }
    }

    /** Tek yer: bir komutu her cihazda aynı şekilde uygular. */
    @Override
    public boolean applyCmd(int seat, String cmd) {
        if (bat == null || cmd.length() == 0) {
            return false;
        }
        String[] f = cmd.split(" ");
        try {
            switch (f[0].charAt(0)) {
                case 'B': {
                    int v = Integer.parseInt(f[1]);
                    if (bat.phase != Batak.BID || bat.turn != seat || !bat.bid(v)) {
                        return false;
                    }
                    tag[seat] = v == 0 ? "PAS" : v + (seat == me ? " VERDİN" : " VERDİ");
                    if (seat == me && modalKind == K_BID) {
                        closeModal();
                    }
                    if (bat.phase == Batak.TRUMP && bat.forced) {
                        openForced();
                    }
                    step();
                    return true;
                }
                case 'T': {
                    int t = Integer.parseInt(f[1]);
                    if (bat.phase != Batak.TRUMP || bat.declarer != seat || !bat.setTrump(t)) {
                        return false;
                    }
                    if (modalKind == K_TRUMP || modalKind == K_FORCED) {
                        closeModal();
                    }
                    for (int i = 0; i < 4; i++) {
                        tag[i] = "";
                    }
                    banner = seat == me ? "Koz: " + CardArt.SUIT_NAMES[t] + " · ilk eli sen aç"
                            : names[seat] + " kozu seçti: " + CardArt.SUIT_NAMES[t];
                    step();
                    return true;
                }
                case 'P': {
                    int card = Integer.parseInt(f[1]);
                    if (bat.phase != Batak.PLAY || bat.actor() != seat || bat.trickComplete() || !bat.isLegal(bat.turn, card)
                            || !bat.play(card)) {
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
                    if (bat.phase != Batak.PLAY || !bat.trickComplete()) {
                        return false;
                    }
                    int w = bat.collectTrick();
                    banner = names[w] + " eli aldı";
                    step();
                    return true;
                }
                case 'N': {
                    if (bat.phase != Batak.HAND_OVER) {
                        return false;
                    }
                    if (modalKind == K_HAND) {
                        closeModal();
                    }
                    newHand();
                    return true;
                }
                case 'M': {
                    if (bat.phase != Batak.MATCH_OVER) {
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

    private void openBid() {
        String hi = bat.highBid > 0 ? "En yüksek: " + bat.highBid + " (" + names[bat.highBidder] + ")" : "En az " + bat.minBase();
        openModal(K_BID, "İHALE", hi, new MB(B_PAS, "PAS", false));
    }

    private void openTrump() {
        openModal(K_TRUMP, "KOZ SEÇ", "İhaleyi " + bat.bid + " ile aldın", new MB[0]);
    }

    private void openForced() {
        boolean mine = bat.declarer == me;
        for (int i = 0; i < 4; i++) {
            tag[i] = "";
        }
        openModal(K_FORCED, "KİMSE İHALEYE GİRMEDİ",
                mine ? "Sen " + bat.forcedBid() + " ile başlıyorsun" : names[bat.declarer] + " " + bat.forcedBid() + " ile başlıyor",
                new MB(B_FORCED_OK, mine ? "KOZ SEÇ" : "TAMAM", true));
    }

    private void openHandOver() {
        boolean made = bat.declarerMade;
        String sub = names[bat.declarer] + " " + bat.bid + " ihale " + (made ? "tuttu" : "yattı");
        if (online() && !authority()) {
            openModal(K_HAND, "EL BİTTİ", sub, new MB(B_FORCED_OK, "TAMAM", true));
        } else {
            openModal(K_HAND, "EL BİTTİ", sub, new MB(B_NEXT, "SONRAKİ EL", true));
        }
    }

    private void openMatchOver() {
        boolean win = bat.won(me);
        if (!resultSent) {
            resultSent = true;
            sound(win ? Game.SND_WIN : Game.SND_LOSE);
            if (host != null) {
                if (online()) {
                    host.onSalonOnlineResult(open ? SalonBase.G_BATAKA : open ? SalonBase.G_BATAKA : SalonBase.G_BATAK, win, target >= 101 ? 3 : 1);
                } else {
                    host.onSalonResult(SalonBase.G_BATAK, win, target >= 101 ? 3 : 1);
                }
            }
        }
        openModal(K_MATCH, win ? "KAZANDIN!" : (open ? "RAKİPLER" : names[bat.winner]) + " KAZANDI",
                (open && bat.bid == Batak.MAX_BID ? "13 ihale " + (bat.declarerMade ? "tuttu" : "yattı") + " · maç bitti" : "Hedef " + target + " puan"), new MB(B_REMATCH, online() ? "RÖVANŞ" : "YENİ MAÇ", true),
                new MB(B_EXIT_YES, "SALONA DÖN", false));
    }

    @Override
    public RectF modalRect(int kind) {
        float w = getWidth();
        if (kind == K_BID || kind == K_TRUMP) {
            float mw = Math.min(w * 0.7f, 1150f * S);
            float top = iy0 + 140f * S;
            float mh = kind == K_BID ? 400f * S : 440f * S;
            return new RectF(w / 2f - mw / 2f, top, w / 2f + mw / 2f, top + mh);
        }
        if (kind == K_HAND || kind == K_SCORES || kind == K_MATCH) {
            float mw = Math.min(w * 0.6f, 1000f * S);
            float mh = 640f * S;
            float top = (getHeight() - mh) / 2f;
            return new RectF(w / 2f - mw / 2f, top, w / 2f + mw / 2f, top + mh);
        }
        if (kind == K_FORCED) {
            float mw = Math.min(w * 0.55f, 900f * S);
            float mh = 360f * S;
            float top = (getHeight() - mh) / 2f - 40f * S;
            return new RectF(w / 2f - mw / 2f, top, w / 2f + mw / 2f, top + mh);
        }
        return super.modalRect(kind);
    }

    @Override
    public boolean lightDim(int kind) {
        return kind == K_BID || kind == K_TRUMP || kind == K_HAND;
    }

    @Override
    public void drawCustomModal(Canvas c, int kind, RectF body) {
        if (kind == K_BID) {
            int n = Batak.MAX_BID - bat.minBase() + 1;
            float gap = 22f * S;
            float bw = Math.min(150f * S, (body.width() - gap * (n - 1)) / n);
            float bh = 120f * S;
            float tot = bw * n + gap * (n - 1);
            float y = body.top + (body.height() - bh) / 2f - 10f * S;
            for (int i = 0; i < n; i++) {
                int v = bat.minBase() + i;
                RectF b = new RectF(body.centerX() - tot / 2f + i * (bw + gap), y,
                        body.centerX() - tot / 2f + i * (bw + gap) + bw, y + bh);
                boolean ok = bat.canBid(v);
                woodButton(c, b, String.valueOf(v), ok && v == bat.minBid(), ok);
                if (ok) {
                    addBtn(b, B_BID, v);
                }
            }
        } else if (kind == K_TRUMP) {
            int[] order = {CardArt.SPADES, CardArt.HEARTS, CardArt.DIAMONDS, CardArt.CLUBS};
            float gap = 30f * S;
            float bw = 190f * S;
            float bh = Math.min(body.height(), 190f * S);
            float tot = bw * 4 + gap * 3;
            float y = body.top + (body.height() - bh) / 2f;
            for (int i = 0; i < 4; i++) {
                int su = order[i];
                RectF b = new RectF(body.centerX() - tot / 2f + i * (bw + gap), y,
                        body.centerX() - tot / 2f + i * (bw + gap) + bw, y + bh);
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFFBF7EA);
                c.drawRoundRect(b, 24f * S, 24f * S, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5f * S);
                paint.setColor(0xFFD4AF37);
                c.drawRoundRect(b, 24f * S, 24f * S, paint);
                CardArt.suit(c, su, b.centerX(), b.top + bh * 0.42f, bh * 0.45f,
                        CardArt.isRed(su) ? CardArt.RED : CardArt.INK, paint);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, CardArt.SUIT_NAMES[su], new RectF(b.left, b.top + bh * 0.72f, b.right, b.bottom - 6f * S),
                        0xFF3C230A, 28f * S);
                addBtn(b, B_TRUMP, su);
            }
        } else if (kind == K_HAND || kind == K_SCORES || kind == K_MATCH) {
            drawScoreTable(c, body, kind == K_HAND);
        }
    }

    private void drawScoreTable(Canvas c, RectF body, boolean showDelta) {
        float rowH = Math.min(70f * S, body.height() / 5f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        float x0 = body.left + 20f * S;
        float xs = body.right - 20f * S;
        float xd = body.right - 190f * S;
        leftText(c, "OYUNCU", x0, body.top + rowH * 0.4f, 0xFFC9B58A, 22f * S, 300f * S);
        text.setTextAlign(Paint.Align.RIGHT);
        if (showDelta) {
            text.setColor(0xFFC9B58A);
            text.setTextSize(22f * S);
            c.drawText("EL", xd, body.top + rowH * 0.4f + 8f * S, text);
        }
        text.setColor(0xFFC9B58A);
        text.setTextSize(22f * S);
        c.drawText("TOPLAM", xs, body.top + rowH * 0.4f + 8f * S, text);
        for (int p = 0; p < 4; p++) {
            float cy = body.top + rowH * (p + 1.4f);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            String nm = names[p] + (showDelta && p == bat.declarer ? "  (" + bat.bid + ")" : "");
            leftText(c, nm, x0, cy, 0xFFFFF4D6, 30f * S, 400f * S);
            text.setTextAlign(Paint.Align.RIGHT);
            if (showDelta) {
                int d = bat.handDelta[p];
                text.setColor(d >= 0 ? 0xFF8FE08F : 0xFFFF8A80);
                text.setTextSize(32f * S);
                c.drawText((d > 0 ? "+" : "") + d, xd, cy + 11f * S, text);
            }
            text.setColor(0xFFE9C46A);
            text.setTextSize(34f * S);
            c.drawText(String.valueOf(bat.score[p]), xs, cy + 12f * S, text);
        }
    }

    // ------------------------------------------------------------------
    // Düğmeler
    // ------------------------------------------------------------------

    @Override
    public MB menuExtra() {
        return new MB(B_SCORES, "PUANLAR", false);
    }

    @Override
    public void onGameButton(int id, int arg) {
        switch (id) {
            case B_SCORES:
                openModal(K_SCORES, "PUAN DURUMU", "Hedef " + target + " puan", new MB(B_MODAL_BACK, "‹ GERİ", true));
                break;
            case B_BID:
                submit(me, "B " + arg);
                break;
            case B_PAS:
                submit(me, "B 0");
                break;
            case B_TRUMP:
                submit(me, "T " + arg);
                break;
            case B_FORCED_OK:
                closeModal();
                step();
                break;
            case B_NEXT:
                submit(me, "N");
                break;
            case B_REMATCH:
                closeModal();
                if (!online()) {
                    newMatch(new java.util.Random().nextLong());
                } else if (authority()) {
                    submit(me, "M " + new java.util.Random().nextLong());
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
        if (bat == null || bat.phase != Batak.PLAY || bat.actor() != me || bat.trickComplete()) {
            return false;
        }
        for (int i = handRects.size() - 1; i >= 0; i--) {
            if (handRects.get(i).contains(x, y)) {
                int card = handCards.get(i);
                if (!bat.isLegal(bat.turn, card)) {
                    banner = "Bu kartı atamazsın (renge uy / yüksek at / koz at)";
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

    /** p: mutlak koltuk; konum bana göre döner. */
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
        float side = open ? 450f * S : 420f * S;
        float top = open ? 350f * S : 320f * S;
        float own = open ? 494f * S : 470f * S;
        switch (rel(p)) {
            case 1:
                return new float[] {cx - 135f * S, side};
            case 2:
                return new float[] {cx, top};
            case 3:
                return new float[] {cx + 135f * S, side};
            default:
                return new float[] {cx, own};
        }
    }

    @Override
    public void drawGame(Canvas c) {
        if (bat == null) {
            return;
        }
        if (modalKind == K_NONE) {
            drawMenuButton(c);
        }
        // oyuncu kutuları
        for (int p = 0; p < 4; p++) {
            float[] s = seat(p);
            String l2;
            if (bat.phase == Batak.BID && tag[p].length() > 0) {
                l2 = tag[p];
            } else if (bat.phase == Batak.PLAY || bat.phase == Batak.HAND_OVER) {
                l2 = "Puan " + bat.score[p] + " · El " + bat.tricksWon[p];
            } else {
                l2 = "Puan " + bat.score[p];
            }
            boolean turn = (bat.phase == Batak.BID || bat.phase == Batak.PLAY) && bat.turn == p
                    && !(bat.phase == Batak.PLAY && bat.trickComplete());
            if (bat.phase == Batak.TRUMP && bat.declarer == p) {
                turn = true;
            }
            profileBox(c, s[0], s[1], names[p] + (online() && !human[p] ? " (BOT)" : ""), l2, AVATARS[p % AVATARS.length], levels[p], turn, turnFrac(p));
            if (turn && turnFrac(p) < 0f) {
                text.setTypeface(Typeface.DEFAULT_BOLD);
                centerText(c, "SIRADA", new RectF(s[0] - 60f * S, s[1] - 90f * S, s[0] + 60f * S, s[1] - 58f * S),
                        0xFFFFE282, 20f * S);
            }
        }
        // bilgi paneli
        RectF ip = new RectF(ix1 - 320f * S, iy0 + 22f * S, ix1 - 22f * S, iy0 + 22f * S + 150f * S);
        String l2 = bat.declarer >= 0 && bat.phase >= Batak.TRUMP ? "İHALE " + bat.bid + " · " + names[bat.declarer] : "İHALE —";
        infoPanel(c, ip, new String[] {"HEDEF " + target + " · EL " + bat.handNo, l2, bat.trump >= 0 ? "KOZ" : "KOZ —"},
                new int[] {0xFFE9C46A, 0xFFFFF4D6, 0xFFFFF4D6}, new float[] {24f, 22f, 22f});
        if (bat.trump >= 0) {
            CardArt.suit(c, bat.trump, ip.centerX() + 60f * S, ip.top + ip.height() * 5f / 6f, 30f * S,
                    CardArt.isRed(bat.trump) ? CardArt.RED : 0xFFFFFFFF, paint);
        }
        drawBubbles(c);
        // bildirim
        if (banner.length() > 0 && bat.phase != Batak.MATCH_OVER) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(26f * S);
            float bw = Math.min(text.measureText(banner) + 60f * S, getWidth() * 0.6f);
            RectF b = new RectF(cx() - bw / 2f, 196f * S, cx() + bw / 2f, 196f * S + 46f * S);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xCC1E120A);
            c.drawRoundRect(b, 23f * S, 23f * S, paint);
            centerText(c, banner, b, 0xFFFFF4D6, 26f * S);
        }
        // masadaki kartlar
        float tw = 100f * S;
        float th = 146f * S;
        for (int p = 0; p < 4; p++) {
            int cd = bat.trick[p];
            if (cd >= 0) {
                float[] s = slot(p);
                RectF r = new RectF(s[0] - tw / 2f, s[1] - th / 2f, s[0] + tw / 2f, s[1] + th / 2f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x55000000);
                c.drawRoundRect(new RectF(r.left + 5 * S, r.top + 7 * S, r.right + 5 * S, r.bottom + 7 * S), 12f * S, 12f * S, paint);
                CardArt.cardFace(c, r, Cards.rank(cd), Cards.suit(cd), false, paint, text);
            }
        }
        // elin
        handRects.clear();
        handCards.clear();
        if (bat.phase == Batak.BID || bat.phase == Batak.TRUMP || bat.phase == Batak.PLAY) {
            int[] h = bat.hand[me];
            int n = h.length;
            if (n > 0) {
                float cw = 122f * S;
                float ch = 176f * S;
                float avail = getWidth() - 2f * ix0 - 80f * S;
                float step = n > 1 ? Math.min(cw * 0.86f, (avail - cw) / (n - 1)) : 0f;
                float x0 = cx() - (step * (n - 1) + cw) / 2f;
                float yb = iy1 - ch - 12f * S;
                boolean myTurn = bat.phase == Batak.PLAY && bat.actor() == me && bat.turn == me && !bat.trickComplete();
                int[] lg = myTurn ? bat.legal(me) : new int[0];
                for (int i = 0; i < n; i++) {
                    int cd = h[i];
                    boolean legal = !myTurn || Cards.has(lg, cd);
                    float lift = (cd == sel) ? 34f * S : 0f;
                    RectF r = new RectF(x0 + i * step, yb - lift, x0 + i * step + cw, yb - lift + ch);
                    CardArt.cardFace(c, r, Cards.rank(cd), Cards.suit(cd), myTurn && !legal, paint, text);
                    handRects.add(r);
                    handCards.add(cd);
                }
            }
        }
        drawDummy(c);
    }

    /** Açmalı batak: ihaleci kozu seçince eşinin kartları herkese açık, masanın üstünde durur. */
    private void drawDummy(Canvas c) {
        int dm = bat.dummy();
        if (!open || bat.phase != Batak.PLAY || dm < 0 || dm == me) {
            return;
        }
        int[] h = bat.hand[dm];
        int n = h.length;
        if (n == 0) {
            return;
        }
        float cw = 64f * S;
        float ch = 92f * S;
        float avail = getWidth() - 2f * ix0 - 900f * S;
        float step = n > 1 ? Math.min(cw * 0.8f, (avail - cw) / (n - 1)) : 0f;
        float x0 = cx() - (step * (n - 1) + cw) / 2f;
        float y0 = 188f * S;
        boolean mine = bat.actor() == me && bat.turn == dm && !bat.trickComplete();
        int[] lg = mine ? bat.legal(dm) : new int[0];
        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(0xFFFFE282);
        text.setTextSize(20f * S);
        c.drawText("AÇIK EL · " + names[dm], x0 - 14f * S, y0 + ch / 2f + 7f * S, text);
        for (int i = 0; i < n; i++) {
            int cd = h[i];
            RectF r = new RectF(x0 + i * step, y0, x0 + i * step + cw, y0 + ch);
            if (mine && cd == sel) {
                r.offset(0, 20f * S);
            }
            CardArt.cardFace(c, r, Cards.rank(cd), Cards.suit(cd), mine && !Cards.has(lg, cd), paint, text);
            if (mine) {
                handRects.add(r);
                handCards.add(cd);
            }
        }
    }

    @Override
    public String rulesText() {
        return rules(open);
    }

    public static String rules(final boolean open) {
        if (open) {
            return "## Açmalı Batak\n"
                    + "Dört kişiyle iki takım olarak oynanır: karşındaki oyuncu eşindir. Kurallar batakla aynıdır, farklar şunlardır.\n\n"
                    + "## El iptal\n"
                    + "Bir oyuncunun elinde 10'dan büyük kart (J, Q, K, A) yoksa dağıtım iptal edilir ve yeniden dağıtılır.\n\n"
                    + "## İhale\n"
                    + "İhale en az 8 ile başlar. Kimse girmezse ilk teklif sorulan oyuncu 7 ile başlar.\n\n"
                    + "## Açık el\n"
                    + "İhaleci kozu seçtikten sonra eşinin kartları herkese açılır. İhaleci hem kendi hem eşinin kartlarını oynar. "
                    + "Rakipler iki kapalı el ile oynar ama açık eli görerek karar verir.\n\n"
                    + "## Puan\n"
                    + "İhaleci ve eşinin aldığı eller toplanır. İhaleye yetişirse takım ihale kadar puan kazanır, yetişmezse o kadar kaybeder. "
                    + "Rakip takım aldığı eller kadar puan yazar.\n\n"
                    + "## 13 ihale\n"
                    + "13 ihale veren takım 13 eli de alırsa maçı hemen kazanır; alamazsa maçı hemen kaybeder.\n\n"
                    + "## Kullanım\n"
                    + "Kartına bir kez dokunarak kaldır, ikinci kez dokunarak at. Eşinin sırası geldiğinde açık eldeki kartlara dokun.";
        }
        return "## Genel\n"
                + "Batak 4 kişiyle, 52 kartla oynanır; herkese 13 kart dağıtılır. Sen altta oturursun, 3 rakip bilgisayardır. "
                + "Oyun saat yönünün tersine ilerler: sıra sağındaki oyuncuya geçer. As en büyük karttır.\n\n"
                + "## El iptal\n"
                + "Bir oyuncunun elinde 10'dan büyük kart (J, Q, K, A) yoksa dağıtım iptal edilir ve kartlar yeniden dağıtılır.\n\n"
                + "## İhale\n"
                + "Dağıtanın solundaki oyuncudan başlayarak herkes kaç el alacağını söyler. İhale en az " + Batak.MIN_BID + " ile başlar, en çok 13 olur. "
                + "Her oyuncu bir kez yükseltir ya da PAS der; PAS diyen o elde bir daha teklif veremez. "
                + "Kimse ihaleye girmezse ilk teklif sorulan oyuncu " + Batak.FORCED_BID + " ile başlamak zorunda kalır.\n\n"
                + "## Koz\n"
                + "İhaleyi alan oyuncu kozu seçer ve ilk kartı o atar.\n\n"
                + "## Oynanış\n"
                + "Atılan renge uymak zorundasın; varsa masadaki en büyük karttan daha büyüğünü atmalısın. "
                + "Rengin yoksa koz atmak zorundasın; masada koz varsa ondan büyük koz atarsın (büyüğün yoksa küçük koz). "
                + "Hem renk hem koz yoksa dilediğin kartı atarsın. Eli, en büyük koz ya da koz yoksa en büyük kart alır; eli alan sonraki eli açar.\n\n"
                + "## Puan\n"
                + "İhaleci, ihale ettiği eli alırsa ihale kadar puan kazanır, alamazsa o kadar puan kaybeder. "
                + "Diğer oyuncular aldıkları el kadar puan kazanır.\n\n"
                + "## Kazanmak\n"
                + "Seçtiğin hedef puana (81 ya da 101) ilk ulaşan kazanır. Aynı anda ulaşılırsa oyun sürer.\n\n"
                + "## Kullanım\n"
                + "Elinden bir karta bir kez dokunarak yukarı kaldır, ikinci kez dokunarak at. Soluk kartlar şu an atılamaz.";
    }
}
