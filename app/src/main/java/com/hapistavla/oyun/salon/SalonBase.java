package com.hapistavla.oyun.salon;

import com.hapistavla.oyun.*;
import com.hapistavla.oyun.tavla.Game;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Oyun salonu masalarının ortak tabanı (okey, 101, batak, king): masa arka planı, ahşap düğmeler,
 * oyuncu kutuları, pencere (modal) sistemi, dokunma ve zamanlayıcılar.
 */
public abstract class SalonBase extends View {
    public static final int G_BATAK = 1;
    public static final int G_KING = 2;
    public static final int G_OKEY = 3;
    public static final int G_101 = 4;
    public static final int G_BATAKA = 5;

    public interface Host {
        public void onSalonExit();

        public void onSalonResult(int game, boolean win, int points);

        public Sfx sfx();

        public String playerName();

        public int playerLevel();

        public int playerAvatar();

        /** Çevrimiçi: sohbet yazma penceresini açar. */
        public void onSalonChatInput();

        /** Çevrimiçi: bir rakibi arkadaş listesine ekler. 0 = eklendi, diğerleri Profile.FRIEND_*. */
        public int onSalonAddFriend(String code, String name);

        /** Çevrimiçi sonuç (XP için). */
        public void onSalonOnlineResult(int game, boolean win, int points);
    }

    /** Çevrimiçi bağlantı (MainActivity SalonNet'e bağlar). */
    public interface Link {
        public boolean isHost();

        /** Ev sahibi: bir komutu ("koltuk:komut") herkese yayınlar. */
        public void act(String entry);

        /** Misafir: ev sahibinden komut ister. */
        public void req(int seat, String cmd);

        public void chat(String text);

        public void emote(int id);
    }

    // taban düğme kimlikleri
    public static final int B_MENU = 1;
    public static final int B_RULES = 2;
    public static final int B_RESUME = 3;
    public static final int B_EXIT = 4;
    public static final int B_EXIT_YES = 5;
    public static final int B_EXIT_NO = 6;
    public static final int B_MODAL_BACK = 7;
    public static final int B_SCORES = 8;
    public static final int B_CHAT = 11;
    public static final int B_EMOTE = 12;
    public static final int B_FRIENDS = 13;
    public static final int B_EMOTE_PICK = 14;   // arg = ifade
    public static final int B_FRIEND_PICK = 15;  // arg = koltuk
    public static final int B_REMATCH = 16;

    // pencere türleri (taban)
    public static final int K_NONE = 0;
    public static final int K_MENU = 1;
    public static final int K_RULES = 2;
    public static final int K_EXIT = 3;
    public static final int K_EMOTE = 4;
    public static final int K_FRIEND = 5;
    public static final int K_CUSTOM = 100;   // 100 ve üstü: alt sınıfın pencereleri

    public Host host;
    public final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    public final Handler handler = new Handler(Looper.getMainLooper());
    public int gen = 0;
    public final boolean felt;
    public final String title;

    public float S = 1f;                 // 900 piksel yüksekliğe göre ölçek
    public float ix0;
    public float iy0;
    public float ix1;
    public float iy1;
    private Bitmap tableBmp;
    private int tableKey = -1;

    public static final class Btn {
        public final RectF r;
        public final int id;
        public final int arg;

        public Btn(RectF r, int id, int arg) {
            this.r = r;
            this.id = id;
            this.arg = arg;
        }
    }

    public final List<Btn> btns = new ArrayList<Btn>();

    public static final class MB {
        public final int id;
        public final String label;
        public final boolean primary;

        public MB(int id, String label, boolean primary) {
            this.id = id;
            this.label = label;
            this.primary = primary;
        }
    }

    public int modalKind = K_NONE;
    public String mTitle = "";
    public String mSub = "";
    public List<MB> mBtns = new ArrayList<MB>();
    public String mBody = "";
    public float mScroll = 0f;
    private float mScrollMax = 0f;
    public RectF mRect = new RectF();

    public long animUntil = 0;
    public String banner = "";

    // ------------------------------------------------------------------
    // Çevrimiçi katman (çevrimdışıyken link == null)
    // ------------------------------------------------------------------

    public Link link;
    /** Benim koltuğum (altta görünen). */
    public int me = 0;
    public final String[] names = new String[4];
    public final int[] levels = new int[4];
    public final String[] codes = new String[4];
    /** Koltukta gerçek oyuncu var mı (yoksa bot oynar). */
    public final boolean[] human = new boolean[4];
    public long seed = 0;
    /** step() her çağrıldığında artar; eski gecikmeli işler kendini iptal eder. */
    public int token = 0;
    private final String[] bubble = new String[4];
    private final long[] bubbleUntil = new long[4];
    public static final String[] BOT_NAMES = {"", "Kerem", "Selin", "Mert"};
    public static final int[] BOT_LEVELS = {0, 7, 12, 5};

    public boolean online() {
        return link != null;
    }

    /** Bu cihaz oyunu yürütüyor mu (çevrimdışı ya da ev sahibi)? Botlar yalnız burada oynar. */
    public boolean authority() {
        return link == null || link.isHost();
    }

    // ------------------------------------------------------------------
    // Sıra süresi: 60 sn, süre dolunca insan oyuncu yerine otomatik hamle
    // ------------------------------------------------------------------

    public static long TURN_MS = 60000L;
    /** Uygulanan komut sayısı: her karar noktasını ayırt etmek için. */
    public long moveNo = 0;
    private int timerSeat = -1;
    private long timerKey = Long.MIN_VALUE;
    private long timerElapsed;
    private long timerLast;
    private int timerTok = -1;
    private Runnable timerAuto;
    private boolean timerTicking;
    private boolean needTick;

    /** Uygulama arka plandayken (çevrimdışı oyunlarda) sıra süresi işlemez. */
    public static boolean appPaused = false;

    /** Oyun duraklatıldı mı (çevrimdışı menü/kurallar penceresi açık)? */
    public boolean timerPaused() {
        return !online() && (appPaused || modalKind == K_MENU || modalKind == K_RULES || modalKind == K_EXIT);
    }

    /**
     * Sıra p oyuncusunda; key bu karar noktasını tanımlar (aynı key ile tekrar çağrılırsa süre sıfırlanmaz).
     * auto: süre dolunca p yerine oynayacak iş (yalnız oyunu yürüten cihazda ve insan koltukta çalışır).
     */
    public void armTimer(int tk, int p, long key, Runnable auto) {
        armTimer(tk, p, p, key, auto);
    }

    /** p: halkanın gösterileceği koltuk; actor: hamleyi yapacak insan (açık elde eş yerine oynayan). */
    public void armTimer(int tk, int p, int actor, long key, Runnable auto) {
        if (p < 0 || actor < 0 || !human[actor]) {
            timerSeat = -1;
            timerAuto = null;
            timerKey = Long.MIN_VALUE;
            return;
        }
        long now = System.currentTimeMillis();
        if (p != timerSeat || key != timerKey) {
            timerElapsed = 0;
            timerLast = now;
        }
        timerSeat = p;
        timerKey = key;
        timerTok = tk;
        timerAuto = authority() ? auto : null;
        if (!timerTicking) {
            timerTicking = true;
            later(timerTick, 200);
        }
        invalidate();
    }

    public void clearTimer() {
        timerSeat = -1;
        timerKey = Long.MIN_VALUE;
        timerAuto = null;
    }

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            timerTicking = false;
            if (timerSeat < 0) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!timerPaused()) {
                timerElapsed += Math.max(0, now - timerLast);
            }
            timerLast = now;
            if (timerTok != token) {
                // karar noktası değişti ama yeni armTimer gelmedi: süreyi bırak
                timerSeat = -1;
                return;
            }
            if (timerElapsed >= TURN_MS) {
                Runnable a = timerAuto;
                timerAuto = null;
                timerSeat = -1;
                timerKey = Long.MIN_VALUE;
                if (a != null) {
                    a.run();
                }
                return;
            }
            timerTicking = true;
            later(timerTick, 200);
        }
    };

    /** p oyuncusunun kalan süre oranı (1 = tam, 0 = bitti); sırası değilse -1. */
    public float turnFrac(int p) {
        if (p != timerSeat) {
            return -1f;
        }
        long el = timerElapsed;
        if (!timerPaused()) {
            el += Math.max(0, System.currentTimeMillis() - timerLast);
        }
        return Math.max(0f, 1f - el / (float) TURN_MS);
    }

    /** Mutlak koltuk -> ekrandaki konum (0 = alt, 1 = sol, 2 = üst, 3 = sağ). */
    public int rel(int p) {
        return (me - p + 4) & 3;   // sıra sağdaki oyuncuya geçer: saat yönünün tersi (geleneksel Türk usulü)
    }

    public boolean isMine(int p) {
        return p == me;
    }

    public boolean botSeat(int p) {
        return !human[p];
    }

    /** Çevrimdışı kurulum: ben 0. koltuktayım, diğerleri bot. */
    public void setupOffline() {
        link = null;
        me = 0;
        for (int i = 0; i < 4; i++) {
            human[i] = i == 0;
            names[i] = i == 0 ? (host != null ? host.playerName() : "Sen") : BOT_NAMES[i];
            levels[i] = i == 0 ? (host != null ? host.playerLevel() : 1) : BOT_LEVELS[i];
            codes[i] = null;
        }
    }

    /** Çevrimiçi kurulum: koltuk listesi (boş koltuk = bot). */
    public void setupOnline(Link l, int mySeat, String[] nm, int[] lv, String[] cd, long sd) {
        link = l;
        me = mySeat;
        seed = sd;
        int b = 0;
        for (int i = 0; i < 4; i++) {
            human[i] = cd[i] != null;
            codes[i] = cd[i];
            if (human[i]) {
                names[i] = nm[i];
                levels[i] = lv[i];
            } else {
                b++;
                names[i] = BOT_NAMES[b % 3 == 0 ? 3 : b % 3] ;
                levels[i] = BOT_LEVELS[b % 3 == 0 ? 3 : b % 3];
            }
        }
    }

    /** Bir komutu uygular; geçerliyse true. Her cihazda aynı sırayla çalışır. */
    public boolean applyCmd(int seat, String cmd) {
        return false;
    }

    /** Ağa gönderilecek metin (varsayılan: komutun kendisi). Uygulandıktan sonra çağrılır. */
    public String wire(int seat, String cmd) {
        return cmd;
    }

    /** Oyuncu ya da bot bir hamle yapmak istiyor. Çevrimdışı/ev sahibi: uygulandıysa true. Misafir: istek gönderildiyse true. */
    public boolean submit(int seat, String cmd) {
        if (link == null) {
            boolean ok = applyCmd(seat, cmd);
            if (ok) {
                moveNo++;
            }
            return ok;
        } else if (link.isHost()) {
            if (applyCmd(seat, cmd)) {
                moveNo++;
                link.act(seat + ":" + wire(seat, cmd));
                return true;
            }
            return false;
        } else if (seat == me) {
            link.req(seat, cmd);
            return true;
        }
        return false;
    }

    /** Misafir: ev sahibinin yayınladığı komut geldi. */
    public void netAct(String entry) {
        int i = entry.indexOf(':');
        if (i <= 0) {
            return;
        }
        try {
            moveNo++;
            applyCmd(Integer.parseInt(entry.substring(0, i)), entry.substring(i + 1));
        } catch (NumberFormatException e) {
            // bozuk giriş
        }
        invalidate();
    }

    /** Ev sahibi: bir misafirden komut isteği geldi. */
    public void netReq(int seat, String cmd) {
        if (link == null || !link.isHost() || seat < 1 || seat > 3 || !human[seat]) {
            return;
        }
        if (cmd.equals("M")) {
            cmd = "M " + new java.util.Random().nextLong();
        }
        submit(seat, cmd);
        invalidate();
    }

    /** Ev sahibi: oyuncu ayrıldı, koltuğu bot devraldı. */
    public void netSeatFree(int seat) {
        if (seat < 0 || seat > 3) {
            return;
        }
        human[seat] = false;
        notice(names[seat] + " masadan ayrıldı, yerine bot geçti");
        onSeatChanged();
        invalidate();
    }

    public void netSeatBack(int seat) {
        if (seat < 0 || seat > 3) {
            return;
        }
        human[seat] = true;
        notice(names[seat] + " geri döndü");
        onSeatChanged();
        invalidate();
    }

    /** Koltuk durumu değişti (bot/insan): sıra bottaysa ev sahibi devam ettirsin. */
    public void onSeatChanged() {
    }

    /** Ekranda kısa bir bildirim göster. */
    public void notice(String s) {
        banner = s;
        invalidate();
    }

    public void netChat(int seat, String name, String t) {
        if (seat >= 0 && seat < 4) {
            bubble[seat] = t;
            bubbleUntil[seat] = System.currentTimeMillis() + 5500;
            animate(5600);
        }
    }

    public void netEmote(int seat, String name, int id) {
        if (seat >= 0 && seat < 4 && id >= 0 && id < Game.EMOTES.length) {
            bubble[seat] = emoteText(id);
            bubbleUntil[seat] = System.currentTimeMillis() + 4000;
            animate(4100);
        }
    }

    public static String emoteText(int id) {
        String[] faces = {"\uD83D\uDE0A", "\uD83D\uDE02", "\uD83D\uDE2E", "\uD83D\uDE20", "\uD83D\uDE22", "\uD83D\uDE0E"};
        return id < faces.length ? faces[id] : Game.EMOTES[id];
    }

    public void sendChatText(String t) {
        if (link != null && t != null && t.trim().length() > 0) {
            link.chat(t.trim());
            netChat(me, names[me], t.trim());
        }
    }

    public void sendEmoteId(int id) {
        if (link != null) {
            link.emote(id);
            netEmote(me, names[me], id);
        }
    }

    /** Konuşma balonları (oyuncu kutularının yanında). Ekran konumu için seatCenter(p) kullanılır. */
    public void drawBubbles(Canvas c) {
        long now = System.currentTimeMillis();
        for (int p = 0; p < 4; p++) {
            if (bubble[p] == null || now > bubbleUntil[p]) {
                continue;
            }
            float[] s = seatCenter(p);
            if (s == null) {
                continue;
            }
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextSize(26f * S);
            String t = bubble[p];
            float bw = Math.min(text.measureText(t) + 44f * S, 420f * S);
            float bh = 54f * S;
            float bx = Math.max(ix0, Math.min(ix1 - bw, s[0] - bw / 2f));
            float by = s[1] + 58f * S;
            if (rel(p) == 2) {
                by = s[1] + 62f * S;
            }
            RectF r = new RectF(bx, by, bx + bw, by + bh);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xF2FFF4D6);
            c.drawRoundRect(r, 22f * S, 22f * S, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * S);
            paint.setColor(0xFF8C6937);
            c.drawRoundRect(r, 22f * S, 22f * S, paint);
            centerText(c, t, r, 0xFF2B1A0E, 26f * S);
        }
    }

    /** Mutlak koltuğun profil kutusu merkezi; yoksa null. */
    public float[] seatCenter(int p) {
        return null;
    }

    public SalonBase(Context ctx, boolean felt, String title) {
        super(ctx);
        this.felt = felt;
        this.title = title;
    }

    public void setHost(Host h) {
        host = h;
    }

    // ------------------------------------------------------------------
    // Zamanlama
    // ------------------------------------------------------------------

    public void later(final Runnable r, long ms) {
        final int g = gen;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (g == gen) {
                    r.run();
                }
            }
        }, ms);
    }

    /** Bekleyen tüm zamanlayıcıları geçersiz kılar (oyun bitti / çıkıldı). */
    public void cancelTimers() {
        gen++;
    }

    public void animate(long ms) {
        long until = System.currentTimeMillis() + ms;
        if (until > animUntil) {
            animUntil = until;
        }
        invalidate();
    }

    @Override
    public void onDetachedFromWindow() {
        cancelTimers();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------------
    // Yerleşim
    // ------------------------------------------------------------------

    @Override
    public void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        S = h / 900f;
        float fr = Math.min(w, h) * 0.055f;
        ix0 = fr;
        iy0 = fr;
        ix1 = w - fr;
        iy1 = h - fr;
        invalidate();
    }

    public float cx() {
        return getWidth() / 2f;
    }

    // ------------------------------------------------------------------
    // Çizim
    // ------------------------------------------------------------------

    @Override
    public void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (ix1 <= ix0) {
            onSizeChanged(w, h, w, h);
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF120D0A);
        c.drawRect(0, 0, w, h, paint);
        int key = (w * 31 + h) * 2 + (felt ? 1 : 0);
        if (tableBmp == null || tableKey != key) {
            tableBmp = Gfx.buildTable(w, h, felt);
            tableKey = key;
        }
        c.drawBitmap(tableBmp, 0, 0, null);
        btns.clear();
        drawGame(c);
        if (modalKind != K_NONE) {
            drawModalFrame(c);
        }
        if (System.currentTimeMillis() < animUntil) {
            postInvalidateDelayedCompat();
        } else if (needTick) {
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, 80);
        }
        needTick = false;
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    private void postInvalidateDelayedCompat() {
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 16);
    }

    public boolean lightDim(int kind) {
        return false;
    }

    public abstract void drawGame(Canvas c);

    /** Oyuna özgü düğme basıldı. */
    public abstract void onGameButton(int id, int arg);

    /** Oyuna özgü pencere gövdesi (kind >= K_CUSTOM). Düğmeleri addBtn ile kaydeder. */
    public void drawCustomModal(Canvas c, int kind, RectF body) {
    }

    /** Oyun alanına dokunma (pencere kapalıyken, düğmeler denendikten sonra). */
    public boolean onGameTap(float x, float y) {
        return false;
    }

    /** Kurallar metni. */
    public abstract String rulesText();

    /** Pencere boyutu: alt sınıf özelleştirebilir. */
    public RectF modalRect(int kind) {
        float w = getWidth();
        float h = getHeight();
        float mw = Math.min(w * 0.72f, 1280f * S);
        float mh = Math.min(h * 0.8f, 700f * S);
        return new RectF(w / 2f - mw / 2f, h / 2f - mh / 2f, w / 2f + mw / 2f, h / 2f + mh / 2f);
    }

    // ------------------------------------------------------------------
    // Pencere (modal)
    // ------------------------------------------------------------------

    public void openModal(int kind, String ttl, String sub, MB... buttons) {
        modalKind = kind;
        mTitle = ttl;
        mSub = sub == null ? "" : sub;
        mBtns = new ArrayList<MB>();
        for (MB b : buttons) {
            mBtns.add(b);
        }
        mScroll = 0f;
        invalidate();
    }

    public void closeModal() {
        modalKind = K_NONE;
        invalidate();
    }

    public void openMenu() {
        if (link != null) {
            boolean others = false;
            for (int i = 0; i < 4; i++) {
                if (i != me && human[i]) {
                    others = true;
                }
            }
            if (others) {
                openModal(K_MENU, "MENÜ", "", new MB(B_RESUME, "DEVAM ET", true), new MB(B_RULES, "KURALLAR", false),
                        menuExtra(), new MB(B_CHAT, "SOHBET", false), new MB(B_EMOTE, "İFADE GÖNDER", false),
                        new MB(B_FRIENDS, "ARKADAŞ EKLE", false), new MB(B_EXIT, "OYUNDAN ÇIK", false));
                return;
            }
        }
        openModal(K_MENU, "MENÜ", "", new MB(B_RESUME, "DEVAM ET", true), new MB(B_RULES, "KURALLAR", false),
                menuExtra(), new MB(B_EXIT, "OYUNDAN ÇIK", false));
    }

    public void openEmotes() {
        openModal(K_EMOTE, "İFADE GÖNDER", "", new MB(B_MODAL_BACK, "‹ GERİ", true));
    }

    public void openFriends() {
        openModal(K_FRIEND, "ARKADAŞ EKLE", "Eklemek istediğin oyuncuya dokun", new MB(B_MODAL_BACK, "‹ GERİ", true));
    }

    /** Menüye eklenecek üçüncü düğme (ör. PUANLAR). */
    public MB menuExtra() {
        return new MB(B_SCORES, "PUANLAR", false);
    }

    public void openRules() {
        mBody = rulesText();
        openModal(K_RULES, "KURALLAR · " + title, "", new MB(B_MODAL_BACK, "‹ GERİ", true));
    }

    public void openExitConfirm() {
        openModal(K_EXIT, "OYUNDAN ÇIKILSIN MI?", "Yarım kalan maç kaydedilmez.", new MB(B_EXIT_NO, "HAYIR, DEVAM", true),
                new MB(B_EXIT_YES, "EVET, ÇIK", false));
    }

    /** Geri tuşu. */
    public void onBack() {
        if (modalKind == K_NONE) {
            openMenu();
        } else if (modalKind == K_MENU) {
            closeModal();
        } else if (modalKind == K_RULES) {
            openMenu();
        } else if (modalKind == K_EXIT || modalKind == K_EMOTE || modalKind == K_FRIEND) {
            openMenu();
        }
    }

    private void drawModalFrame(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha(lightDim(modalKind) ? 70 : 150);
        c.drawRect(0, 0, w, h, paint);
        paint.setAlpha(255);
        RectF r = modalRect(modalKind);
        mRect.set(r);
        float rad = 30f * S;
        paint.setColor(0xFF000000);
        paint.setAlpha(160);
        c.drawRoundRect(new RectF(r.left + 6 * S, r.top + 8 * S, r.right + 6 * S, r.bottom + 8 * S), rad, rad, paint);
        paint.setAlpha(255);
        paint.setColor(0xFF36220F);
        c.drawRoundRect(r, rad, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(3f, 5f * S));
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(r, rad, rad, paint);

        text.setTypeface(Typeface.DEFAULT_BOLD);
        float y = r.top + 52f * S;
        centerText(c, mTitle, new RectF(r.left + 20 * S, r.top + 18 * S, r.right - 20 * S, r.top + 84 * S), 0xFFE9C46A, 44f * S);
        float bodyTop = r.top + 92f * S;
        if (mSub.length() > 0) {
            centerText(c, mSub, new RectF(r.left + 20 * S, r.top + 84 * S, r.right - 20 * S, r.top + 124 * S), 0xFFC9B58A, 24f * S);
            bodyTop = r.top + 130f * S;
        }
        // alt düğmeler
        float bh = 76f * S;
        float bottom = r.bottom - 22f * S;
        RectF body = new RectF(r.left + 34 * S, bodyTop, r.right - 34 * S, bottom - bh - 16f * S);
        if (modalKind == K_RULES) {
            drawScrollText(c, body);
        } else if (modalKind == K_EMOTE) {
            drawEmoteBody(c, body);
        } else if (modalKind == K_FRIEND) {
            drawFriendBody(c, body);
        } else if (modalKind >= K_CUSTOM) {
            drawCustomModal(c, modalKind, body);
        } else if (mBody.length() > 0 && modalKind != K_MENU) {
            drawScrollText(c, body);
        }
        int n = mBtns.size();
        if (n > 0) {
            float gap = 18f * S;
            float bw = Math.min(330f * S, (r.width() - 68f * S - gap * (n - 1)) / n);
            if (modalKind == K_MENU) {
                // menü: dikey liste
                float mh = (r.height() - 130f * S - 24f * S - gap * (n - 1)) / n;
                mh = Math.min(mh, 86f * S);
                float totalH = mh * n + gap * (n - 1);
                float y0 = r.top + 108f * S + ((r.height() - 130f * S) - totalH) / 2f;
                for (int i = 0; i < n; i++) {
                    RectF b = new RectF(r.centerX() - 260f * S, y0 + i * (mh + gap), r.centerX() + 260f * S,
                            y0 + i * (mh + gap) + mh);
                    woodButton(c, b, mBtns.get(i).label, mBtns.get(i).primary, true);
                    btns.add(new Btn(b, mBtns.get(i).id, 0));
                }
            } else {
                float tot = bw * n + gap * (n - 1);
                for (int i = 0; i < n; i++) {
                    RectF b = new RectF(r.centerX() - tot / 2f + i * (bw + gap), bottom - bh,
                            r.centerX() - tot / 2f + i * (bw + gap) + bw, bottom);
                    woodButton(c, b, mBtns.get(i).label, mBtns.get(i).primary, true);
                    btns.add(new Btn(b, mBtns.get(i).id, 0));
                }
            }
        }
    }

    private void drawEmoteBody(Canvas c, RectF body) {
        int n = Game.EMOTES.length;
        int cols = 5;
        int rows = (n + cols - 1) / cols;
        float gap = 14f * S;
        float bw = (body.width() - gap * (cols - 1)) / cols;
        float bh = Math.min(96f * S, (body.height() - gap * (rows - 1)) / rows);
        float y0 = body.top + (body.height() - (bh * rows + gap * (rows - 1))) / 2f;
        for (int i = 0; i < n; i++) {
            int r = i / cols;
            int k = i % cols;
            RectF b = new RectF(body.left + k * (bw + gap), y0 + r * (bh + gap), body.left + k * (bw + gap) + bw,
                    y0 + r * (bh + gap) + bh);
            woodButton(c, b, emoteText(i), false, true);
            addBtn(b, B_EMOTE_PICK, i);
        }
    }

    private void drawFriendBody(Canvas c, RectF body) {
        List<Integer> seats = new ArrayList<Integer>();
        for (int i = 0; i < 4; i++) {
            if (i != me && human[i] && codes[i] != null) {
                seats.add(i);
            }
        }
        float bh = Math.min(84f * S, body.height() / Math.max(1, seats.size()) - 10f * S);
        float y = body.top;
        for (int i : seats) {
            RectF b = new RectF(body.left + 60f * S, y, body.right - 60f * S, y + bh);
            woodButton(c, b, "+ " + names[i].toUpperCase(), false, true);
            addBtn(b, B_FRIEND_PICK, i);
            y += bh + 10f * S;
        }
        if (seats.isEmpty()) {
            centerText(c, "Masada eklenecek başka oyuncu yok", body, 0xFFC9B58A, 28f * S);
        }
    }

    private void drawScrollText(Canvas c, RectF body) {
        c.save();
        c.clipRect(body);
        text.setTypeface(Typeface.DEFAULT);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(0xFFF1E4C4);
        float fs = 25f * S;
        text.setTextSize(fs);
        List<String> lines = wrap(mBody, body.width());
        float lh = fs * 1.4f;
        float total = lines.size() * lh;
        mScrollMax = Math.max(0f, total - body.height() + lh * 0.5f);
        if (mScroll > mScrollMax) {
            mScroll = mScrollMax;
        }
        float y = body.top + fs - mScroll;
        for (String ln : lines) {
            if (ln.startsWith("##")) {
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setColor(0xFFE9C46A);
                c.drawText(ln.substring(2).trim(), body.left, y, text);
                text.setTypeface(Typeface.DEFAULT);
                text.setColor(0xFFF1E4C4);
            } else {
                c.drawText(ln, body.left, y, text);
            }
            y += lh;
        }
        c.restore();
        if (mScrollMax > 0f) {
            float tx = body.right + 14f * S;
            float th = body.height() * (body.height() / (body.height() + mScrollMax));
            float ty = body.top + (body.height() - th) * (mScroll / mScrollMax);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x88E9C46A);
            c.drawRoundRect(new RectF(tx, ty, tx + 6f * S, ty + th), 3f * S, 3f * S, paint);
        }
    }

    public List<String> wrap(String s, float maxW) {
        List<String> out = new ArrayList<String>();
        for (String para : s.split("\n", -1)) {
            if (para.length() == 0) {
                out.add("");
                continue;
            }
            String[] words = para.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String wd : words) {
                String t = cur.length() == 0 ? wd : cur + " " + wd;
                if (text.measureText(t) > maxW && cur.length() > 0) {
                    out.add(cur.toString());
                    cur = new StringBuilder(wd);
                } else {
                    cur = new StringBuilder(t);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Çizim yardımcıları
    // ------------------------------------------------------------------

    public void centerText(Canvas c, String s, RectF rc, int color, float size) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(color);
        text.setTextSize(size);
        float tw = text.measureText(s);
        float maxW = rc.width() * 0.94f;
        if (tw > maxW && tw > 0) {
            text.setTextSize(size * maxW / tw);
        }
        float yy = rc.centerY() - (text.descent() + text.ascent()) / 2f;
        c.drawText(s, rc.centerX(), yy, text);
    }

    public void leftText(Canvas c, String s, float x, float cy, int color, float size, float maxW) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(color);
        text.setTextSize(size);
        float tw = text.measureText(s);
        if (tw > maxW && tw > 0) {
            text.setTextSize(size * maxW / tw);
        }
        float yy = cy - (text.descent() + text.ascent()) / 2f;
        c.drawText(s, x, yy, text);
    }

    public void woodButton(Canvas c, RectF rc, String label, boolean primary, boolean enabled) {
        float rad = rc.height() * 0.22f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, rc.top, 0, rc.bottom,
                primary ? new int[] {0xFFB98B3E, 0xFF7A5420} : new int[] {0xFF6E4527, 0xFF3F2514},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        paint.setAlpha(enabled ? 255 : 120);
        c.drawRoundRect(rc, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, rc.height() * 0.035f));
        paint.setColor(0xFFD4AF37);
        paint.setAlpha(enabled ? 255 : 120);
        c.drawRoundRect(rc, rad, rad, paint);
        paint.setAlpha(255);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, label, rc, enabled ? 0xFFFFF4D6 : 0xFF9C8C70, rc.height() * 0.36f);
    }

    public void addBtn(RectF r, int id, int arg) {
        btns.add(new Btn(r, id, arg));
    }

    /** Oyuncu kutusu: avatar, isim, ikinci satır, seviye rozeti; sırası gelen için altın çerçeve. */
    public void profileBox(Canvas c, float cx, float cy, String name, String line2, int avatarColor, int level,
                              boolean turn) {
        profileBox(c, cx, cy, name, line2, avatarColor, level, turn, -1f);
    }

    private void drawTurnRing(Canvas c, RectF r, float frac) {
        TurnRing.draw(c, paint, r, frac, 9f * S, 28f * S, 8f * S);
    }

    public void profileBox(Canvas c, float cx, float cy, String name, String line2, int avatarColor, int level,
                              boolean turn, float frac) {
        float w = 300f * S;
        float h = 100f * S;
        RectF r = new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
        float rad = 22f * S;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF462B16);
        c.drawRoundRect(r, rad, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(turn ? 6f * S : 3f * S);
        paint.setColor(turn ? 0xFFFFE282 : 0xFF8C6937);
        c.drawRoundRect(r, rad, rad, paint);
        if (turn) {
            // sıra kimdeyse yanıp söner (son 10 saniyede hızlı)
            needTick = true;
            long per = frac >= 0f && frac < 0.166f ? 350L : 1000L;
            float ph = (System.currentTimeMillis() % per) / (float) per;
            float a = 0.5f + 0.5f * (float) Math.sin(ph * 2 * Math.PI);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFE282);
            paint.setAlpha((int) (28 + 70 * a));
            c.drawRoundRect(r, rad, rad, paint);
            paint.setAlpha(255);
        }
        if (turn && frac >= 0f) {
            drawTurnRing(c, r, frac);
        }
        // avatar
        float ax = r.left + 14f * S + 36f * S;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(avatarColor);
        c.drawCircle(ax, cy, 36f * S, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * S);
        paint.setColor(0xFFFFF4D6);
        c.drawCircle(ax, cy, 36f * S, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFF4D6);
        c.drawCircle(ax, cy - 10f * S, 12f * S, paint);
        Path body = new Path();
        body.addCircle(ax, cy + 30f * S, 22f * S, Path.Direction.CW);
        c.save();
        c.clipPath(avatarClip(ax, cy, 36f * S));
        c.drawPath(body, paint);
        c.restore();
        text.setTypeface(Typeface.DEFAULT_BOLD);
        leftText(c, name, ax + 50f * S, cy - 20f * S, 0xFFFFF4D6, 28f * S, w - 150f * S);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        leftText(c, line2, ax + 50f * S, cy + 16f * S, 0xFFE9C46A, 21f * S, w - 150f * S);
        // seviye rozeti
        float bx = r.right - 6f * S;
        float by = r.top + 2f * S;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE9C46A);
        c.drawCircle(bx, by, 24f * S, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * S);
        paint.setColor(0xFF785014);
        c.drawCircle(bx, by, 24f * S, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, String.valueOf(level), new RectF(bx - 20 * S, by - 20 * S, bx + 20 * S, by + 20 * S), 0xFF3C230A, 22f * S);
        if (turn && frac >= 0f) {
            int secs = (int) Math.ceil(frac * TURN_MS / 1000f);
            String t = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
            RectF pill = new RectF(cx - 40f * S, r.top - 18f * S, cx + 40f * S, r.top + 14f * S);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xDD1E120A);
            c.drawRoundRect(pill, 16f * S, 16f * S, paint);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            centerText(c, t, pill, frac > 0.33f ? 0xFFB6F09C : (frac > 0.166f ? 0xFFFFE282 : 0xFFFF8A80), 22f * S);
        }
    }

    private Path avatarClip(float x, float y, float r) {
        Path p = new Path();
        p.addCircle(x, y, r - 2f * S, Path.Direction.CW);
        return p;
    }

    /** Küçük bilgi kutusu (satırlar): sağ panel vb. */
    public void infoPanel(Canvas c, RectF r, String[] lines, int[] colors, float[] sizes) {
        float rad = 18f * S;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF2A1A0E);
        c.drawRoundRect(r, rad, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * S);
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(r, rad, rad, paint);
        float lineH = r.height() / lines.length;
        text.setTypeface(Typeface.DEFAULT_BOLD);
        for (int i = 0; i < lines.length; i++) {
            centerText(c, lines[i], new RectF(r.left, r.top + i * lineH, r.right, r.top + (i + 1) * lineH), colors[i], sizes[i] * S);
        }
    }

    // ------------------------------------------------------------------
    // Dokunma
    // ------------------------------------------------------------------

    private float downX;
    private float downY;
    private float lastY;
    private boolean dragging;

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX();
        float y = e.getY();
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastY = y;
                dragging = false;
                dragMoved = false;
                dragCand = modalKind == K_NONE && dragStart(x, y);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragCand) {
                    if (!dragMoved && Math.hypot(x - downX, y - downY) > 14f * S) {
                        dragMoved = true;
                    }
                    if (dragMoved) {
                        dragMove(x, y);
                        invalidate();
                    }
                    return true;
                }
                if (modalKind != K_NONE && mScrollMax > 0f) {
                    if (Math.abs(y - downY) > 12f) {
                        dragging = true;
                    }
                    if (dragging) {
                        mScroll = Math.max(0f, Math.min(mScrollMax, mScroll - (y - lastY)));
                        lastY = y;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (dragCand && dragMoved) {
                    dragCancel();
                    invalidate();
                }
                dragCand = false;
                dragMoved = false;
                return true;
            case MotionEvent.ACTION_UP:
                if (dragCand) {
                    boolean mv = dragMoved;
                    dragCand = false;
                    dragMoved = false;
                    if (mv) {
                        dragEnd(x, y);
                        invalidate();
                        return true;
                    }
                }
                if (dragging) {
                    return true;
                }
                handleTap(x, y);
                return true;
            default:
                return true;
        }
    }

    private boolean dragCand = false;
    private boolean dragMoved = false;

    /** Sürükleme başlayabilir mi (taşın üstünde miyiz)? Durumu değiştirmemeli; dokunma yine çalışır. */
    protected boolean dragStart(float x, float y) {
        return false;
    }

    protected void dragMove(float x, float y) {
    }

    protected void dragEnd(float x, float y) {
    }

    protected void dragCancel() {
    }

    private void handleTap(float x, float y) {
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (b.r.contains(x, y)) {
                if (b.id == B_EMOTE_PICK) {
                    sendEmoteId(b.arg);
                    closeModal();
                } else if (b.id == B_FRIEND_PICK) {
                    int res = host != null && codes[b.arg] != null ? host.onSalonAddFriend(codes[b.arg], names[b.arg]) : 1;
                    closeModal();
                    notice(res == 0 ? names[b.arg] + " arkadaş listene eklendi"
                            : (res == 3 ? "Zaten arkadaşsınız" : "Eklenemedi"));
                } else if (!baseButton(b.id)) {
                    onGameButton(b.id, b.arg);
                }
                invalidate();
                return;
            }
        }
        if (modalKind == K_NONE) {
            onGameTap(x, y);
            invalidate();
        }
    }

    private boolean baseButton(int id) {
        switch (id) {
            case B_MENU:
                openMenu();
                return true;
            case B_RESUME:
            case B_EXIT_NO:
                closeModal();
                return true;
            case B_RULES:
                openRules();
                return true;
            case B_MODAL_BACK:
                openMenu();
                return true;
            case B_EXIT:
                openExitConfirm();
                return true;
            case B_CHAT:
                closeModal();
                if (host != null) {
                    host.onSalonChatInput();
                }
                return true;
            case B_EMOTE:
                openEmotes();
                return true;
            case B_FRIENDS:
                openFriends();
                return true;
            case B_EXIT_YES:
                cancelTimers();
                if (host != null) {
                    host.onSalonExit();
                }
                return true;
            default:
                return false;
        }
    }

    public void sound(int kind) {
        if (host != null && host.sfx() != null) {
            host.sfx().play(kind);
        }
    }

    /** Düğme ekler: MENÜ (sol üst). */
    public void drawMenuButton(Canvas c) {
        RectF b = new RectF(ix0 + 22f * S, iy0 + 22f * S, ix0 + 22f * S + 170f * S, iy0 + 22f * S + 60f * S);
        woodButton(c, b, "MENÜ", false, true);
        addBtn(b, B_MENU, 0);
    }

    public static int[] AVATARS = {0xFF78469A, 0xFF3C78AA, 0xFFAA5A3C, 0xFF4B9060, 0xFFB08A2E};
}
