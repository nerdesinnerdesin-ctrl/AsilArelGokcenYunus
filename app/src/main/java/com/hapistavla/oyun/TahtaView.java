package com.hapistavla.oyun;

import com.hapistavla.oyun.tavla.Board;
import com.hapistavla.oyun.salon.CardArt;
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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.DisplayCutout;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.List;

/** Tavla tahtasının çizimi ve dokunma girdisi. Oyun mantığı Game sınıfındadır. */
public class TahtaView extends View implements Game.Host {

    public interface Callbacks {
        public void onMenu();

        /** Menü ekranındaki bir düğmeye basıldı. */
        public void onMenuItem(int id);

        /** Ekran klavyesi bitti: text null ise iptal edildi. */
        public void onKeypad(int id, String text);

        public void onRules();

        public void onOnlineEvent(int type, String a, String b);

        /** Oyun bitti; sonuç ekranında gösterilecek ek metni döndürür (boş olabilir). */
        public String onGameOver(Game.Result r);

        /** Sohbet penceresinde yazı kutusuna dokunuldu: yazı giriş penceresini aç. */
        public void onChatInput();
    }

    // buton kimlikleri
    private static final int B_MENU = 0;
    private static final int B_UNDO = 1;
    private static final int B_HINT = 2;
    private static final int B_RULES = 3;
    private static final int B_AUTO = 4;
    private static final int B_EXIT = 5;
    private static final int B_COUNT = 6;
    /** Oyundan çık düğmesine basıldı (onMenuItem'a gönderilir). */
    public static final int EXIT_GAME = -104;
    /** Otomatik taş toplama düğmesine basıldı (onMenuItem'a gönderilir). */
    public static final int TOGGLE_AUTO = -103;
    private final RectF[] emoteRect = new RectF[10];
    private String bubbleText = null;
    private boolean chatOpen = false;
    private final RectF chatPanel = new RectF(), chatClose = new RectF(), chatToggle = new RectF(),
            chatInput = new RectF(), chatBtn = new RectF(), chatMsgArea = new RectF();
    private final RectF[] chatChip = {new RectF(), new RectF(), new RectF(), new RectF()};
    private int bubbleId = -1;
    private boolean bubbleMine = false;
    private long bubbleUntil = 0;

    /** Menü ekranındaki bir düğme. */
    public static final class MenuItem {
        public final int id;
        public final String label;
        public final boolean primary;
        /** Ana ekrandaki büyük oyun kartları için: 1 = hapis tavla, 2 = klasik tavla. */
        public final int icon;
        public final String sub;
        /** 4 kişilik salon masası kartları için: oturan oyuncu sayısı (-1: tavla masası), masa sahibinin adı. */
        public int seats = -1;
        public String host = "";
        /** Açık/kapalı anahtarlı satır: 0 = değil, 1 = kapalı, 2 = açık. */
        public int toggle = 0;

        public MenuItem toggled(boolean on) {
            toggle = on ? 2 : 1;
            return this;
        }

        public MenuItem seated(int humans, String hostName) {
            seats = humans;
            host = hostName == null ? "" : hostName;
            return this;
        }

        public MenuItem(int id, String label, boolean primary) {
            this(id, label, primary, 0, "");
        }

        public MenuItem(int id, String label, boolean primary, int icon, String sub) {
            this.id = id;
            this.label = label;
            this.primary = primary;
            this.icon = icon;
            this.sub = sub;
        }
    }

    /** Ana ekranın sağ üstündeki simgelerin kimlikleri (onMenuItem'a gönderilir). */
    public static final int TOGGLE_CHAT = -105;
    public static final int CORNER_PROFILE = -101;
    public static final int CORNER_SETTINGS = -102;
    private boolean homeMode = false;
    private final RectF iconProfile = new RectF();
    private final RectF iconSettings = new RectF();

    private boolean menuOpen = false;
    /** Menü arka planı: 0 = tavla tahtası, 1 = ahşap masa, 2 = yeşil çuha masa. */
    private int menuBg = 0;
    private boolean homeCorners = true;
    private Bitmap tableBmp;
    private int tableBmpKey = -1;

    public void setMenuBg(int bg) {
        menuBg = bg;
    }

    public void setHomeCorners(boolean on) {
        homeCorners = on;
    }
    private String menuTitle = "";
    private String menuSub = "";
    private List<MenuItem> menuItems = new ArrayList<MenuItem>();
    private final List<RectF> menuRects = new ArrayList<RectF>();

    private static final int OV_LIST = 1;
    private static final int OV_PANEL = 2;
    private static final int OV_KEYPAD = 3;
    private static final int OV_SEATS = 4;
    private static final int OV_DIALOG = 5;
    private final String[] seatNames = new String[4];
    private final int[] seatLv = new int[4];
    private int seatMine = -1;
    private String seatSub = "";
    private int ovKind = OV_LIST;
    private String panelBig = "";
    private String panelBody = "";
    private float bodyScroll = 0f;
    private float bodyMax = 0f;
    private float lastY = 0f;
    private boolean dragging = false;
    private int kpId;
    private String kpChars = "";
    private int kpMax;
    private int kpMin;
    private boolean kpSpace;
    private String kpText = "";
    private String kpOk = "TAMAM";
    private final List<RectF> keyRects = new ArrayList<RectF>();
    private final List<String> keyLabels = new ArrayList<String>();
    private final RectF kpCancel = new RectF();
    private final RectF kpDone = new RectF();

    public void showMenu(String title, String sub, List<MenuItem> items) {
        menuTitle = title;
        menuSub = sub;
        menuItems = items;
        ovKind = OV_LIST;
        homeMode = false;
        tablesMode = false;
        menuOpen = true;
        invalidate();
    }

    /** Ana ekran: iki büyük oyun kartı + sağ üstte profil ve ayarlar simgeleri. */
    public void showHome(String title, String sub, List<MenuItem> items) {
        showMenu(title, sub, items);
        homeMode = true;
    }

    /** Başlık, isteğe bağlı büyük yazı (ör. oda kodu), kaydırılabilir metin ve düğmeler. */
    public void showPanel(String title, String big, String body, List<MenuItem> items) {
        menuTitle = title;
        panelBig = big == null ? "" : big;
        panelBody = body == null ? "" : body;
        menuItems = items;
        bodyScroll = 0f;
        homeMode = false;
        tablesMode = false;
        ovKind = OV_PANEL;
        menuOpen = true;
        invalidate();
    }

    /** Kısa uyarı: yazı ve düğme boyutunda küçük kutu, ekranın ortasında. */
    public void showDialog(String title, String body, List<MenuItem> items) {
        showPanel(title, "", body, items);
        ovKind = OV_DIALOG;
    }

    /** 4 kişilik masa bekleme ekranı: oturanlar koltuklarda, boş koltukta bot bekler. */
    public void showSeats(String title, String sub, String[] names, int[] levels, int mine, List<MenuItem> items) {
        menuTitle = title;
        seatSub = sub == null ? "" : sub;
        for (int i = 0; i < 4; i++) {
            seatNames[i] = names[i];
            seatLv[i] = levels[i];
        }
        seatMine = mine;
        menuItems = items;
        homeMode = false;
        tablesMode = false;
        ovKind = OV_SEATS;
        menuOpen = true;
        invalidate();
    }

    /** Yazı girişi için ekran klavyesi. */
    public void showKeypad(int id, String title, String chars, int minLen, int maxLen, boolean allowSpace,
                           String initial, String okLabel) {
        kpId = id;
        menuTitle = title;
        kpChars = chars;
        kpMin = minLen;
        kpMax = maxLen;
        kpSpace = allowSpace;
        kpText = initial == null ? "" : initial;
        kpOk = okLabel;
        homeMode = false;
        tablesMode = false;
        ovKind = OV_KEYPAD;
        menuOpen = true;
        invalidate();
    }

    public void hideMenu() {
        menuOpen = false;
        invalidate();
    }

    public boolean isMenuOpen() {
        return menuOpen;
    }

    public final Game game;
    private Callbacks cb;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Object timerToken = new Object();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    // yerleşim
    private int insL;
    private int insT;
    private int insR;
    private boolean layoutReady = false;
    private float bx0;
    private float by0;
    private float bx1;
    private float by1;
    private float cw;
    private float triH;
    private float midH;
    private float r;
    private float ix0;
    private float iy0;
    private float iy1;
    private float u;
    private final RectF leftPanel = new RectF();
    private final RectF rightPanel = new RectF();
    private final RectF statusRect = new RectF();
    private final RectF[] btn = new RectF[B_COUNT];
    private final RectF rollBtn = new RectF();
    private final RectF overNew = new RectF();
    private final RectF overMenu = new RectF();
    private final RectF card0 = new RectF();
    private final RectF card1 = new RectF();

    // önbellek
    private Gfx.Geo geo = new Gfx.Geo();
    private Bitmap boardBmp;
    private Bitmap whiteBmp;
    private Bitmap blackBmp;
    private int chkSize;
    private int themeIndex = 0;
    private Shader bgShader;

    private float downX;
    private float downY;

    private static final float[][][] PIPS = new float[][][] {
        {},
        {{0.5f, 0.5f}},
        {{0.27f, 0.27f}, {0.73f, 0.73f}},
        {{0.27f, 0.27f}, {0.5f, 0.5f}, {0.73f, 0.73f}},
        {{0.27f, 0.27f}, {0.73f, 0.27f}, {0.27f, 0.73f}, {0.73f, 0.73f}},
        {{0.27f, 0.27f}, {0.73f, 0.27f}, {0.5f, 0.5f}, {0.27f, 0.73f}, {0.73f, 0.73f}},
        {{0.27f, 0.27f}, {0.73f, 0.27f}, {0.27f, 0.5f}, {0.73f, 0.5f}, {0.27f, 0.73f}, {0.73f, 0.73f}}
    };

    public TahtaView(Context context) {
        super(context);
        for (int i = 0; i < btn.length; i++) {
            btn[i] = new RectF();
        }
        for (int i = 0; i < emoteRect.length; i++) {
            emoteRect[i] = new RectF();
        }
        text.setTypeface(Typeface.DEFAULT_BOLD);
        game = new Game(this);
    }

    private Sfx sfx;

    public void setSfx(Sfx s) {
        this.sfx = s;
    }

    public int getTheme() {
        return themeIndex;
    }

    public void setCallbacks(Callbacks c) {
        this.cb = c;
    }

    private int pieceStyle = 0;
    private int diceStyle = 0;
    public static final String[] DICE_STYLES = {"FİLDİŞİ", "KIRMIZI", "ALTIN", "MAVİ"};

    public int getPieceStyle() {
        return pieceStyle;
    }

    public int getDiceStyle() {
        return diceStyle;
    }

    public void setPieceStyle(int s) {
        if (s >= 0 && s < Gfx.PIECE_STYLES.length && s != pieceStyle) {
            pieceStyle = s;
            rebuildBitmaps();
            invalidate();
        }
    }

    public void setDiceStyle(int s) {
        if (s >= 0 && s < DICE_STYLES.length && s != diceStyle) {
            diceStyle = s;
            invalidate();
        }
    }

    public void setTheme(int index) {
        if (index >= 0 && index < Gfx.THEMES.length && index != themeIndex) {
            themeIndex = index;
            rebuildBitmaps();
            invalidate();
        }
    }

    // ------------------------------------------------------------------
    // Game.Host
    // ------------------------------------------------------------------

    @Override
    public void postTimer(Runnable run, long delayMs) {
        handler.postAtTime(run, timerToken, SystemClock.uptimeMillis() + delayMs);
    }

    @Override
    public void cancelTimers() {
        handler.removeCallbacksAndMessages(timerToken);
    }

    @Override
    public void postUi(Runnable run) {
        handler.post(run);
    }

    @Override
    public void changed() {
        invalidate();
    }

    @Override
    public void onlineEvent(int type, String a, String b) {
        if (type == Game.EV_CHAT) {
            if (chatOpen) {
                game.chatUnread = 0;
            } else {
                bubbleText = a.length() > 26 ? a.substring(0, 25) + "…" : a;
                bubbleId = 0;
                bubbleMine = false;
                bubbleUntil = SystemClock.uptimeMillis() + 4200;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        invalidate();
                    }
                }, 4300);
            }
            invalidate();
            return;
        }
        if (type == Game.EV_EMOTE) {
            bubbleText = null;
            for (int i = 0; i < Game.EMOTES.length; i++) {
                if (Game.EMOTES[i].equals(a)) {
                    bubbleId = i;
                    bubbleMine = "Sen".equals(b);
                    bubbleUntil = SystemClock.uptimeMillis() + 3200;
                    invalidate();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            invalidate();
                        }
                    }, 3300);
                    return;
                }
            }
        }
        if (cb != null) {
            cb.onOnlineEvent(type, a, b);
        }
    }

    @Override
    public void gameOver(Game.Result res) {
        if (cb != null) {
            String extra = cb.onGameOver(res);
            game.overExtra = extra == null ? "" : extra;
        }
    }

    @Override
    public void sound(int kind) {
        if (sfx != null) {
            sfx.play(kind);
        }
    }

    @Override
    public void haptic() {
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    // ------------------------------------------------------------------
    // Yerleşim
    // ------------------------------------------------------------------

    @Override
    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        int l = insets.getSystemWindowInsetLeft();
        int t = insets.getSystemWindowInsetTop();
        int rr = insets.getSystemWindowInsetRight();
        if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout dc = insets.getDisplayCutout();
            if (dc != null) {
                l = Math.max(l, dc.getSafeInsetLeft());
                t = Math.max(t, dc.getSafeInsetTop());
                rr = Math.max(rr, dc.getSafeInsetRight());
            }
        }
        insL = l;
        insT = t;
        insR = rr;
        computeLayout();
        invalidate();
        return insets;
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        computeLayout();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null);
        super.onDetachedFromWindow();
    }

    private void computeLayout() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float pad = Math.min(w, h) * 0.012f;
        float x0 = insL + pad;
        float y0 = insT + pad;
        float x1 = w - insR - pad;
        float y1 = h - pad;
        float statusH = (y1 - y0) * 0.07f;
        float boardH = (y1 - y0) - statusH - pad;
        float availW = x1 - x0;
        float boardW = Math.min(boardH * 1.8f, availW * 0.78f);
        bx0 = x0 + (availW - boardW) / 2f;
        by0 = y0;
        bx1 = bx0 + boardW;
        by1 = by0 + boardH;
        u = boardH / 870f;

        geo = new Gfx.Geo();
        geo.w = boardW;
        geo.h = boardH;
        geo.fr = boardH * 0.04f;
        geo.ix0 = geo.fr;
        geo.iy0 = geo.fr;
        geo.ix1 = boardW - geo.fr;
        geo.iy1 = boardH - geo.fr;
        geo.cw = (geo.ix1 - geo.ix0) / 14f;
        float innerH = geo.iy1 - geo.iy0;
        geo.triH = innerH * 0.44f;
        geo.midH = innerH - 2f * geo.triH;
        cw = geo.cw;
        triH = geo.triH;
        midH = geo.midH;
        ix0 = bx0 + geo.ix0;
        iy0 = by0 + geo.iy0;
        iy1 = by0 + geo.iy1;
        r = Math.min(cw * 0.46f, triH / 5.6f);

        leftPanel.set(x0, by0, bx0 - pad, by1);
        rightPanel.set(bx1 + pad, by0, x1, by1);
        float sw = Math.min(availW, boardW * 1.3f);
        statusRect.set((x0 + x1) / 2f - sw / 2f, by1 + pad, (x0 + x1) / 2f + sw / 2f, y1);

        // sol panel: iki küçük kart (simge + puan)
        float ph = by1 - by0;
        float cardH = Math.min(ph * 0.30f, leftPanel.width() * 1.0f);
        card0.set(leftPanel.left, by0, leftPanel.right, by0 + cardH);
        card1.set(leftPanel.left, by1 - cardH, leftPanel.right, by1);

        // sağ panel: kısa butonlar, altında emoji / hızlı mesaj alanı
        float bh = Math.min(ph * 0.072f, rightPanel.width() * 0.26f);
        float gap = bh * 0.16f;
        for (int i = 0; i < B_COUNT; i++) {
            btn[i].set(rightPanel.left, by0 + i * (bh + gap), rightPanel.right, by0 + i * (bh + gap) + bh);
        }
        float ey0 = btn[B_COUNT - 1].bottom + gap * 2.5f;
        float avail = by1 - ey0;
        float wR = rightPanel.width();
        float g2 = gap * 0.8f;
        float cell = Math.min((wR - 2f * g2) / 3f, (avail - 5f * g2) / (2f + 4f * 0.72f));
        cell = Math.max(cell, 10f);
        float rowW = 3f * cell + 2f * g2;
        float ex = rightPanel.left + (wR - rowW) / 2f;
        for (int i = 0; i < Game.EMOTE_FACES; i++) {
            int col = i % 3;
            int row = i / 3;
            emoteRect[i].set(ex + col * (cell + g2), ey0 + row * (cell + g2), ex + col * (cell + g2) + cell,
                    ey0 + row * (cell + g2) + cell);
        }
        float ty = ey0 + 2f * (cell + g2);
        for (int i = Game.EMOTE_FACES; i < emoteRect.length; i++) {
            emoteRect[i].set(rightPanel.left, ty, rightPanel.right, ty + cell * 0.72f);
            ty += cell * 0.72f + g2;
        }

        chatBtn.set(rightPanel.left, emoteRect[Game.EMOTE_FACES].top, rightPanel.right,
                emoteRect[Game.EMOTE_FACES + 1].bottom);

        bgShader = new LinearGradient(0, 0, 0, h, new int[] {0xFF241913, 0xFF120D0A},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP);
        rebuildBitmaps();
        layoutReady = true;
    }

    private void rebuildBitmaps() {
        if (geo == null || geo.w <= 0 || geo.h <= 0) {
            return;
        }
        boardBmp = Gfx.buildBoard(geo, Gfx.THEMES[themeIndex]);
        int rad = Math.max(8, (int) r);
        whiteBmp = Gfx.checker(rad, true, pieceStyle);
        blackBmp = Gfx.checker(rad, false, pieceStyle);
        chkSize = whiteBmp.getWidth();
    }

    // ------------------------------------------------------------------
    // Koordinatlar
    // ------------------------------------------------------------------

    private boolean flip() {
        return game.viewColor() == Board.BLACK;
    }

    /** Mantıksal nokta <-> ekrandaki nokta (kendi tersi). */
    private int disp(int pt) {
        return flip() ? 25 - pt : pt;
    }

    private int colOf(int dp) {
        return dp >= 13 ? dp - 13 : 12 - dp;
    }

    private float colLeft(int col) {
        return ix0 + col * cw + (col >= 6 ? 0.6f * cw : 0f);
    }

    private float ptCx(int pt) {
        return colLeft(colOf(disp(pt))) + cw / 2f;
    }

    private boolean ptTop(int pt) {
        return disp(pt) >= 13;
    }

    private float stackSpacing(int total) {
        float sp = 2f * r * 0.98f;
        float avail = triH - 2f * r - 8f * u;
        if (total > 1 && (total - 1) * sp > avail) {
            sp = avail / (total - 1);
        }
        return sp;
    }

    private float slotY(int pt, int k, float sp) {
        float off = r + 5f * u + k * sp;
        return ptTop(pt) ? iy0 + off : iy1 - off;
    }

    private int pointAt(float x, float y) {
        float rel = x - ix0;
        int col;
        if (rel < 0) {
            return 0;
        } else if (rel < 6f * cw) {
            col = (int) (rel / cw);
        } else if (rel < 6.6f * cw) {
            return 0;
        } else if (rel < 12.6f * cw) {
            col = (int) ((rel - 0.6f * cw) / cw);
        } else {
            return 0;
        }
        if (col < 0 || col > 11 || y < iy0 || y > iy1) {
            return 0;
        }
        boolean top = y < (iy0 + iy1) / 2f;
        int dp = top ? 13 + col : 12 - col;
        return disp(dp);
    }

    // ------------------------------------------------------------------
    // Dokunma
    // ------------------------------------------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getAction();
        if (a == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            lastY = downY;
            dragging = false;
        } else if (a == MotionEvent.ACTION_MOVE) {
            if (menuOpen && ovKind == OV_PANEL) {
                float dyy = e.getY() - lastY;
                if (dragging || Math.abs(e.getY() - downY) > 24f * Math.max(u, 0.5f)) {
                    dragging = true;
                    bodyScroll = Math.max(0f, Math.min(bodyMax, bodyScroll - dyy));
                    invalidate();
                }
            }
            lastY = e.getY();
        } else if (a == MotionEvent.ACTION_UP) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            if (!dragging && dx * dx + dy * dy < 40f * 40f * u * u + 900f) {
                handleTap(e.getX(), e.getY());
            }
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void handleTap(float x, float y) {
        if (!layoutReady) {
            return;
        }
        if (chatOpen && !menuOpen) {
            tapChat(x, y);
            return;
        }
        if (menuOpen && ovKind == OV_KEYPAD) {
            tapKeypad(x, y);
            return;
        }
        if (menuOpen && homeMode && ovKind == OV_LIST) {
            if (iconProfile.contains(x, y)) {
                if (cb != null) {
                    cb.onMenuItem(CORNER_PROFILE);
                }
                return;
            }
            if (iconSettings.contains(x, y)) {
                if (cb != null) {
                    cb.onMenuItem(CORNER_SETTINGS);
                }
                return;
            }
        }
        if (menuOpen) {
            for (int i = 0; i < menuRects.size() && i < menuItems.size(); i++) {
                if (menuRects.get(i).contains(x, y)) {
                    if (cb != null) {
                        cb.onMenuItem(menuItems.get(i).id);
                    }
                    return;
                }
            }
            return;
        }
        if (game.mode == Game.MODE_ONLINE) {
            for (int i = 0; i < Game.EMOTE_FACES; i++) {
                if (emoteRect[i].contains(x, y)) {
                    game.sendEmote(i);
                    return;
                }
            }
            if (chatBtn.contains(x, y)) {
                chatOpen = true;
                game.chatUnread = 0;
                invalidate();
                return;
            }
        }
        if (game.phase == Game.PH_OVER) {
            if (overNew.contains(x, y)) {
                game.newGameRequest();
            } else if (overMenu.contains(x, y) && cb != null) {
                cb.onMenu();
            }
            return;
        }
        if (btn[B_MENU].contains(x, y)) {
            if (cb != null) {
                cb.onMenu();
            }
            return;
        }
        if (btn[B_UNDO].contains(x, y)) {
            game.undo();
            return;
        }
        if (btn[B_HINT].contains(x, y)) {
            game.requestHint();
            return;
        }
        if (btn[B_RULES].contains(x, y)) {
            if (cb != null) {
                cb.onRules();
            }
            return;
        }
        if (btn[B_EXIT].contains(x, y)) {
            if (cb != null) {
                cb.onMenuItem(EXIT_GAME);
            }
            return;
        }
        if (btn[B_AUTO].contains(x, y)) {
            if (cb != null) {
                cb.onMenuItem(TOGGLE_AUTO);
            }
            invalidate();
            return;
        }
        boolean onBoard = x >= bx0 && x <= bx1 && y >= by0 && y <= by1;
        if (!onBoard) {
            return;
        }
        if (game.canRoll()) {
            game.tapRoll();
            return;
        }
        if (x >= ix0 + 12.6f * cw) {
            game.tapTray();
            return;
        }
        if (game.board.isClassic() && x >= ix0 + 5.95f * cw && x <= ix0 + 6.65f * cw) {
            game.tapPoint(Board.BAR);
            return;
        }
        int pt = pointAt(x, y);
        if (pt != 0) {
            game.tapPoint(pt);
        }
    }

    // ------------------------------------------------------------------
    // Çizim yardımcıları
    // ------------------------------------------------------------------

    private void centerText(Canvas c, String s, RectF rc, int color, float size) {
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

    private void leftText(Canvas c, String s, float x, float baseline, int color, float size, float maxW) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(color);
        text.setTextSize(size);
        float tw = text.measureText(s);
        if (tw > maxW && tw > 0) {
            text.setTextSize(size * maxW / tw);
        }
        c.drawText(s, x, baseline, text);
    }

    private void woodButton(Canvas c, RectF rc, boolean enabled, boolean highlight) {
        float rad = rc.height() * 0.22f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, rc.top, 0, rc.bottom,
                highlight ? new int[] {0xFFB98B3E, 0xFF7A5420} : new int[] {0xFF6E4527, 0xFF3F2514},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        paint.setAlpha(enabled ? 255 : 120);
        c.drawRoundRect(rc, rad, rad, paint);
        paint.setShader(null);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1.5f, rc.height() * 0.025f));
        paint.setColor(0xFFD4AF37);
        paint.setAlpha(enabled ? 190 : 70);
        c.drawRoundRect(rc, rad, rad, paint);
        paint.setAlpha(255);
    }

    // ------------------------------------------------------------------
    // Çizim
    // ------------------------------------------------------------------

    @Override
    public void onDraw(Canvas canvas) {
        game.pausedUi = menuOpen;
        super.onDraw(canvas);
        if (!layoutReady) {
            computeLayout();
            if (!layoutReady) {
                return;
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(bgShader);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);

        if (menuOpen) {
            int ov = 205;
            if (menuBg != 0) {
                drawTableBg(canvas);
                ov = 70;
            } else {
                drawBoard(canvas);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF0E0906);
            paint.setAlpha(ov);
            canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
            paint.setAlpha(255);
            if (ovKind == OV_PANEL) {
                drawPanel(canvas);
            } else if (ovKind == OV_DIALOG) {
                drawDialog(canvas);
            } else if (ovKind == OV_SEATS) {
                drawSeatLobby(canvas);
            } else if (ovKind == OV_KEYPAD) {
                drawKeypad(canvas);
            } else {
                drawMenu(canvas);
            }
            return;
        }
        if (chatOpen && game.mode != Game.MODE_ONLINE) {
            chatOpen = false;
        }
        drawBoard(canvas);
        drawMarks(canvas);
        drawStacks(canvas);
        drawBar(canvas);
        drawSlides(canvas);
        drawHints(canvas);
        drawTrays(canvas);
        drawDiceOrRoll(canvas);
        drawLeftPanel(canvas);
        drawRightPanel(canvas);
        drawStatus(canvas);
        drawNetBanner(canvas);
        if (game.phase == Game.PH_OVER) {
            drawOver(canvas);
            drawConfetti(canvas);
        }
        if (chatOpen) {
            drawChat(canvas);
        }
    }

    // ------------------------------------------------------------------
    // Metin paneli ve ekran klavyesi
    // ------------------------------------------------------------------

    private List<String> wrap(String s, float size, float maxW) {
        text.setTextSize(size);
        List<String> out = new ArrayList<String>();
        for (String para : s.split("\n", -1)) {
            if (para.isEmpty()) {
                out.add("");
                continue;
            }
            String[] words = para.split(" ");
            StringBuilder line = new StringBuilder();
            for (String w : words) {
                String t = line.length() == 0 ? w : line + " " + w;
                if (line.length() > 0 && text.measureText(t) > maxW) {
                    out.add(line.toString());
                    line = new StringBuilder(w);
                } else {
                    line = new StringBuilder(t);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    private void drawTitle(Canvas c, float cx, float h, float size) {
        RectF tr = new RectF(cx - getWidth() * 0.4f, h * 0.025f, cx + getWidth() * 0.4f, h * 0.025f + size * 1.25f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, menuTitle, shadowRect(tr, h * 0.005f), 0xFF000000, size);
        centerText(c, menuTitle, tr, 0xFFE9C46A, size);
    }

    private void drawButtonRow(Canvas c, float cx, float h, float pw) {
        int n = menuItems.size();
        menuRects.clear();
        if (n == 0) {
            return;
        }
        float gap = h * 0.025f;
        float bh = h * 0.12f;
        float bw = Math.min((pw - gap * (n - 1)) / n, h * 0.7f);
        float total = bw * n + gap * (n - 1);
        float y = h * 0.97f - bh;
        for (int i = 0; i < n; i++) {
            RectF rc = new RectF(cx - total / 2f + i * (bw + gap), y, cx - total / 2f + i * (bw + gap) + bw, y + bh);
            menuRects.add(rc);
            MenuItem it = menuItems.get(i);
            woodButton(c, rc, true, it.primary);
            centerText(c, it.label, rc, 0xFFFFF4D6, bh * 0.36f);
        }
    }

    private void drawDialog(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        float maxW = Math.min((w - insL - insR) * 0.8f, h * 1.15f);
        float pad = h * 0.035f;
        float ts = Math.max(12f, h * 0.045f);
        float lh = ts * 1.3f;
        float tsz = h * 0.058f;
        List<String> lines = wrap(panelBody, ts, maxW - 2 * pad);
        float tw = 0f;
        text.setTextSize(ts);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        for (String ln : lines) {
            tw = Math.max(tw, text.measureText(ln));
        }
        text.setTextSize(tsz);
        tw = Math.max(tw, text.measureText(menuTitle));
        int n = menuItems.size();
        float gap = h * 0.02f;
        float bh = h * 0.1f;
        float bw = h * 0.32f;
        float btnTotal = bw * n + gap * Math.max(0, n - 1);
        float bodyW = Math.min(maxW, Math.max(tw + 2 * pad, btnTotal + 2 * pad));
        float bodyH = pad + tsz * 1.3f + (lines.isEmpty() ? 0f : h * 0.015f + lines.size() * lh) + pad * 0.8f + bh + pad;
        RectF box = new RectF(cx - bodyW / 2f, (h - bodyH) / 2f, cx + bodyW / 2f, (h + bodyH) / 2f);
        float rad = h * 0.03f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, box.top, 0, box.bottom, new int[] {0xFF3A2416, 0xFF24150D},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.004f));
        paint.setColor(0xFFB8913A);
        c.drawRoundRect(box, rad, rad, paint);
        float y = box.top + pad;
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, menuTitle, new RectF(box.left, y, box.right, y + tsz * 1.3f), 0xFFE9C46A, tsz);
        y += tsz * 1.3f + h * 0.015f;
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(ts);
        text.setColor(0xFFF3E6C4);
        for (String ln : lines) {
            c.drawText(ln, cx, y + ts, text);
            y += lh;
        }
        text.setTextAlign(Paint.Align.LEFT);
        menuRects.clear();
        float by = box.bottom - pad - bh;
        for (int i = 0; i < n; i++) {
            float bx = cx - btnTotal / 2f + i * (bw + gap);
            RectF rc = new RectF(bx, by, bx + bw, by + bh);
            menuRects.add(rc);
            MenuItem it = menuItems.get(i);
            woodButton(c, rc, true, it.primary);
            centerText(c, it.label, rc, 0xFFFFF4D6, bh * 0.36f);
        }
    }

    private void drawPanel(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        float pw = Math.min((w - insL - insR) * 0.88f, h * 1.7f);
        drawTitle(c, cx, h, h * 0.085f);
        float top = h * 0.025f + h * 0.085f * 1.25f + h * 0.02f;
        float bottom = h * 0.97f - h * 0.12f - h * 0.03f;
        RectF box = new RectF(cx - pw / 2f, top, cx + pw / 2f, bottom);
        float rad = h * 0.03f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, box.top, 0, box.bottom, new int[] {0xFF3A2416, 0xFF24150D},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(box, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.004f));
        paint.setColor(0xFFB8913A);
        c.drawRoundRect(box, rad, rad, paint);

        float pad = h * 0.03f;
        float ts = Math.max(12f, h * 0.05f);
        float lh = ts * 1.32f;
        float cursor = 0f;
        float innerW = box.width() - 2 * pad;
        Path clip = new Path();
        clip.addRoundRect(box, rad, rad, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        float y = box.top + pad - bodyScroll;
        if (!panelBig.isEmpty()) {
            RectF br = new RectF(box.left, y, box.right, y + h * 0.17f);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            centerText(c, panelBig, shadowRect(br, h * 0.004f), 0xFF000000, h * 0.14f);
            centerText(c, panelBig, br, 0xFFE9C46A, h * 0.14f);
            y += h * 0.19f;
            cursor += h * 0.19f;
        }
        List<String> lines = wrap(panelBody, ts, innerW);
        text.setTextAlign(Paint.Align.LEFT);
        text.setTextSize(ts);
        text.setColor(0xFFF3E6C4);
        for (String ln : lines) {
            if (y + lh > box.top && y < box.bottom) {
                c.drawText(ln, box.left + pad, y + ts, text);
            }
            y += lh;
            cursor += lh;
        }
        c.restore();
        bodyMax = Math.max(0f, cursor + 2 * pad - box.height());
        if (bodyScroll > bodyMax) {
            bodyScroll = bodyMax;
        }
        if (bodyMax > 0f) {
            float trackH = box.height() - 2 * pad;
            float thumbH = Math.max(h * 0.05f, trackH * box.height() / (box.height() + bodyMax));
            float ty = box.top + pad + (trackH - thumbH) * (bodyScroll / bodyMax);
            RectF th = new RectF(box.right - pad * 0.7f, ty, box.right - pad * 0.35f, ty + thumbH);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFB8913A);
            paint.setAlpha(170);
            c.drawRoundRect(th, h * 0.01f, h * 0.01f, paint);
            paint.setAlpha(255);
        }
        drawButtonRow(c, cx, h, pw);
    }

    private void drawKeypad(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        float availW = (w - insL - insR) * 0.94f;
        drawTitle(c, cx, h, h * 0.075f);

        // yazı kutusu
        float fy = h * 0.025f + h * 0.075f * 1.25f + h * 0.015f;
        float fh = h * 0.11f;
        RectF field = new RectF(cx - Math.min(availW, h * 1.3f) / 2f, fy, cx + Math.min(availW, h * 1.3f) / 2f, fy + fh);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1A100A);
        c.drawRoundRect(field, fh * 0.2f, fh * 0.2f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.004f));
        paint.setColor(0xFFB8913A);
        c.drawRoundRect(field, fh * 0.2f, fh * 0.2f, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        String shown = kpText + (kpText.length() < kpMax ? "_" : "");
        centerText(c, shown, field, 0xFFFFF4D6, fh * 0.55f);

        // tuşlar
        int n = kpChars.length();
        int total = n + 1 + (kpSpace ? 1 : 0);
        int cols = n <= 16 ? 8 : (n <= 32 ? 8 : 10);
        int rows = (total + cols - 1) / cols;
        float keysTop = field.bottom + h * 0.03f;
        float keysBottom = h * 0.97f - h * 0.12f - h * 0.03f;
        float gap = h * 0.012f;
        float kh = Math.min((keysBottom - keysTop - gap * (rows - 1)) / rows, h * 0.12f);
        float kw = Math.min((availW - gap * (cols - 1)) / cols, kh * 1.4f);
        float gridW = kw * cols + gap * (cols - 1);
        float gx = cx - gridW / 2f;
        float gridH = kh * rows + gap * (rows - 1);
        float gy = keysTop + Math.max(0f, (keysBottom - keysTop - gridH) / 2f);
        keyRects.clear();
        keyLabels.clear();
        for (int i = 0; i < total; i++) {
            String lab;
            if (i < n) {
                lab = String.valueOf(kpChars.charAt(i));
            } else if (i == n) {
                lab = "⌫";
            } else {
                lab = "BOŞLUK";
            }
            int row = i / cols;
            int col = i % cols;
            RectF rc = new RectF(gx + col * (kw + gap), gy + row * (kh + gap), gx + col * (kw + gap) + kw,
                    gy + row * (kh + gap) + kh);
            keyRects.add(rc);
            keyLabels.add(lab);
            woodButton(c, rc, true, i >= n);
            centerText(c, lab, rc, 0xFFFFF4D6, kh * (lab.length() > 1 ? 0.28f : 0.5f));
        }

        // alt düğmeler
        float bh = h * 0.12f;
        float bw = Math.min(availW * 0.3f, h * 0.7f);
        float by = h * 0.97f - bh;
        kpCancel.set(cx - bw - h * 0.0125f, by, cx - h * 0.0125f, by + bh);
        kpDone.set(cx + h * 0.0125f, by, cx + h * 0.0125f + bw, by + bh);
        woodButton(c, kpCancel, true, false);
        centerText(c, "İPTAL", kpCancel, 0xFFFFF4D6, bh * 0.36f);
        boolean ok = kpText.length() >= kpMin;
        woodButton(c, kpDone, ok, true);
        centerText(c, kpOk, kpDone, ok ? 0xFFFFF4D6 : 0xFF9C8A66, bh * 0.36f);
    }

    private void tapKeypad(float x, float y) {
        if (kpCancel.contains(x, y)) {
            if (cb != null) {
                cb.onKeypad(kpId, null);
            }
            return;
        }
        if (kpDone.contains(x, y)) {
            if (kpText.length() >= kpMin && cb != null) {
                cb.onKeypad(kpId, kpText);
            }
            return;
        }
        for (int i = 0; i < keyRects.size(); i++) {
            if (keyRects.get(i).contains(x, y)) {
                String lab = keyLabels.get(i);
                if ("⌫".equals(lab)) {
                    if (kpText.length() > 0) {
                        kpText = kpText.substring(0, kpText.length() - 1);
                    }
                } else if (lab.equals("BOŞLUK")) {
                    if (kpText.length() < kpMax && kpText.length() > 0 && !kpText.endsWith(" ")) {
                        kpText += " ";
                    }
                } else if (kpText.length() < kpMax) {
                    kpText += lab;
                }
                invalidate();
                return;
            }
        }
    }

    private void drawProfileIcon(Canvas c, RectF rc) {
        float cx = rc.centerX();
        float s = rc.width();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE9C46A);
        c.drawCircle(cx, rc.top + s * 0.37f, s * 0.15f, paint);
        rect.set(cx - s * 0.27f, rc.top + s * 0.56f, cx + s * 0.27f, rc.top + s * 0.56f + s * 0.40f);
        c.drawArc(rect, 180f, 180f, true, paint);
    }

    private void drawGearIcon(Canvas c, RectF rc) {
        float cx = rc.centerX();
        float cy = rc.centerY();
        float s = rc.width();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFE9C46A);
        for (int i = 0; i < 8; i++) {
            c.save();
            c.rotate(i * 45f, cx, cy);
            rect.set(cx - s * 0.06f, cy - s * 0.33f, cx + s * 0.06f, cy - s * 0.18f);
            c.drawRect(rect, paint);
            c.restore();
        }
        c.drawCircle(cx, cy, s * 0.24f, paint);
        paint.setColor(0xFF4A2D18);
        c.drawCircle(cx, cy, s * 0.1f, paint);
    }

    private void cornerButton(Canvas c, RectF rc) {
        woodButton(c, rc, true, false);
    }

    /** Oyun salonu kartlarının resimleri: 4 zar, 5 taş, 6 kağıt, 7 okey, 8 101, 9 king, 12 batak. */
    private void drawSalonArt(Canvas c, float cx, float cy, float s, int icon) {
        float tw = s * 0.27f;
        float th = tw * 1.36f;
        if (icon == 4) {
            float d = s * 0.4f;
            drawDie(c, cx - d * 1.05f, cy - d * 0.7f, d, 5, Board.WHITE, false);
            drawDie(c, cx + d * 0.1f, cy - d * 0.2f, d, 3, Board.BLACK, false);
        } else if (icon == 5 || icon == 7) {
            int[][] t = icon == 7 ? new int[][] {{7, 3}, {12, 1}, {1, 0}} : new int[][] {{9, 0}, {5, 3}, {13, 1}};
            for (int i = 0; i < 3; i++) {
                float x = cx - tw * 1.6f + i * tw * 1.1f;
                float y = cy - th * 0.5f + (i == 1 ? -th * 0.16f : 0f);
                CardArt.okeyFace(c, new RectF(x, y, x + tw, y + th), t[i][0], t[i][1], false, false, paint, text);
            }
        } else if (icon == 8) {
            for (int i = 0; i < 3; i++) {
                float x = cx - tw * 1.6f + i * tw * 1.1f;
                CardArt.okeyFace(c, new RectF(x, cy - th * 0.75f, x + tw, cy - th * 0.75f + th), 5 + i, 3, false, false, paint, text);
            }
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(0xFFFFE9A8);
            text.setTextSize(s * 0.3f);
            c.drawText("101", cx, cy + th * 0.72f, text);
        } else {
            int[][] k = icon == 6 ? new int[][] {{13, 1}, {11, 0}, {14, 3}}
                    : (icon == 9 ? new int[][] {{13, 1}, {12, 0}, {14, 2}} : new int[][] {{14, 0}, {9, 1}, {13, 3}});
            float cw = s * 0.3f;
            float ch = cw * 1.4f;
            for (int i = 0; i < 3; i++) {
                float ang = (i - 1) * 16f;
                c.save();
                c.rotate(ang, cx + (i - 1) * cw * 0.7f, cy + ch * 0.45f);
                float x = cx + (i - 1) * cw * 0.7f - cw / 2f;
                CardArt.cardFace(c, new RectF(x, cy - ch * 0.5f, x + cw, cy + ch * 0.5f), k[i][0], k[i][1], false, paint, text);
                c.restore();
            }
        }
    }

    private void drawTileArt(Canvas c, RectF area, int icon) {
        float cx = area.centerX();
        float cy = area.centerY();
        float s = Math.min(area.width(), area.height());
        if (icon == 1 && whiteBmp != null) {
            // hapis: siyah taş, üstünde beyaz taş; hapsedilen kırmızı halkalı
            float d = s * 0.5f;
            rect.set(cx - d / 2f, cy - d * 0.05f, cx + d / 2f, cy - d * 0.05f + d);
            c.drawBitmap(blackBmp, null, rect, null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(3f, d * 0.07f));
            paint.setColor(0xFFE53935);
            c.drawCircle(cx, cy - d * 0.05f + d / 2f, d * 0.47f, paint);
            rect.set(cx - d / 2f, cy - d * 0.62f, cx + d / 2f, cy - d * 0.62f + d);
            c.drawBitmap(whiteBmp, null, rect, null);
        } else if (icon >= 4 && icon != 2) {
            drawSalonArt(c, cx, cy, s, icon);
        } else if (icon == 2) {
            float d = s * 0.42f;
            drawDie(c, cx - d * 1.05f, cy - d * 0.75f, d, 5, Board.WHITE, false);
            drawDie(c, cx + d * 0.1f, cy - d * 0.25f, d, 3, Board.BLACK, false);
            if (whiteBmp != null) {
                float k = d * 0.62f;
                rect.set(cx - d * 0.95f, cy + d * 0.45f, cx - d * 0.95f + k, cy + d * 0.45f + k);
                c.drawBitmap(whiteBmp, null, rect, null);
                rect.set(cx - d * 0.95f + k * 1.05f, cy + d * 0.45f, cx - d * 0.95f + k * 2.05f, cy + d * 0.45f + k);
                c.drawBitmap(blackBmp, null, rect, null);
            }
        }
    }

    private void drawHome(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        float titleH = h * 0.24f;
        // başlık
        RectF tr = new RectF(cx - w * 0.4f, h * 0.035f, cx + w * 0.4f, h * 0.035f + titleH * 0.62f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, menuTitle, shadowRect(tr, h * 0.006f), 0xFF000000, titleH * 0.5f);
        centerText(c, menuTitle, tr, 0xFFE9C46A, titleH * 0.5f);
        RectF sr = new RectF(cx - w * 0.4f, tr.bottom, cx + w * 0.4f, tr.bottom + titleH * 0.26f);
        centerText(c, menuSub, sr, 0xFFC9B58A, titleH * 0.17f);
        // sağ üst simgeler
        float isz = Math.min(h * 0.13f, w * 0.09f);
        float pad = h * 0.03f;
        iconSettings.set(w - insR - pad - isz, pad, w - insR - pad, pad + isz);
        iconProfile.set(iconSettings.left - pad * 0.6f - isz, pad, iconSettings.left - pad * 0.6f, pad + isz);
        if (homeCorners) {
            cornerButton(c, iconProfile);
            drawProfileIcon(c, iconProfile);
            cornerButton(c, iconSettings);
            drawGearIcon(c, iconSettings);
        } else {
            iconSettings.setEmpty();
            iconProfile.setEmpty();
        }

        menuRects.clear();
        int n = menuItems.size();
        List<MenuItem> tiles = new ArrayList<MenuItem>();
        List<MenuItem> rest = new ArrayList<MenuItem>();
        for (MenuItem it : menuItems) {
            (it.icon != 0 ? tiles : rest).add(it);
        }
        float top = h * 0.03f + titleH;
        float restH = rest.isEmpty() ? 0f : h * 0.115f;
        float gap = h * 0.03f;
        float tileH = Math.min(h * 0.50f, h - top - restH - gap * 2 - h * 0.04f);
        float tileW = Math.min((w - insL - insR) * 0.36f, tileH * 1.15f);
        float totalW = tileW * tiles.size() + gap * Math.max(0, tiles.size() - 1);
        float maxW = (w - insL - insR) * 0.80f;
        if (tiles.size() > 1 && totalW > maxW) {
            tileW = (maxW - gap * (tiles.size() - 1)) / tiles.size();
            totalW = maxW;
        }
        for (int i = 0; i < tiles.size(); i++) {
            MenuItem it = tiles.get(i);
            float x = cx - totalW / 2f + i * (tileW + gap);
            RectF rc = new RectF(x, top, x + tileW, top + tileH);
            woodButton(c, rc, true, it.primary);
            RectF art = new RectF(rc.left + tileW * 0.08f, rc.top + tileH * 0.05f, rc.right - tileW * 0.08f,
                    rc.top + tileH * 0.62f);
            drawTileArt(c, art, it.icon);
            RectF lab = new RectF(rc.left, rc.top + tileH * 0.63f, rc.right, rc.top + tileH * 0.82f);
            centerText(c, it.label, lab, 0xFFFFF4D6, tileH * 0.105f);
            RectF sb = new RectF(rc.left + tileW * 0.05f, rc.top + tileH * 0.82f, rc.right - tileW * 0.05f,
                    rc.top + tileH * 0.95f);
            centerText(c, it.sub, sb, 0xFFE6D3A3, tileH * 0.065f);
            menuRects.add(rc);
        }
        // kalan düğmeler (Oyuna dön vb.) kartların altında yan yana
        if (!rest.isEmpty()) {
            float bh = restH;
            float bw = Math.min(tileW * 1.2f, (totalW - gap * (rest.size() - 1)) / rest.size());
            float tot = bw * rest.size() + gap * (rest.size() - 1);
            float y = top + tileH + gap;
            for (int i = 0; i < rest.size(); i++) {
                RectF rc = new RectF(cx - tot / 2f + i * (bw + gap), y, cx - tot / 2f + i * (bw + gap) + bw, y + bh);
                woodButton(c, rc, true, rest.get(i).primary);
                centerText(c, rest.get(i).label, rc, 0xFFFFF4D6, bh * 0.36f);
                menuRects.add(rc);
            }
        }
        // menuRects sırası menuItems sırasıyla aynı olmalı (dokunma eşlemesi)
        List<MenuItem> ordered = new ArrayList<MenuItem>(tiles);
        ordered.addAll(rest);
        menuItems = ordered;
    }

    /** Açık masalar ekranı: her masa için kart (icon = 3). Diğer öğeler alttaki düğmelerdir. */
    private boolean tablesMode = false;

    public void showTables(String title, String sub, List<MenuItem> items) {
        showMenu(title, sub, items);
        tablesMode = true;
    }

    private void drawTables(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        float titleH = h * 0.2f;
        RectF tr = new RectF(cx - w * 0.4f, h * 0.025f, cx + w * 0.4f, h * 0.025f + titleH * 0.62f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, menuTitle, shadowRect(tr, h * 0.006f), 0xFF000000, titleH * 0.5f);
        centerText(c, menuTitle, tr, 0xFFE9C46A, titleH * 0.5f);
        RectF sr = new RectF(cx - w * 0.4f, tr.bottom, cx + w * 0.4f, tr.bottom + titleH * 0.26f);
        centerText(c, menuSub, sr, 0xFFC9B58A, titleH * 0.17f);

        List<MenuItem> tabs = new ArrayList<MenuItem>();
        List<MenuItem> rest = new ArrayList<MenuItem>();
        for (MenuItem it : menuItems) {
            (it.icon == 3 ? tabs : rest).add(it);
        }
        menuRects.clear();
        float top = h * 0.03f + titleH;
        float btnH = h * 0.11f;
        float gap = h * 0.025f;
        float areaBottom = h - btnH - gap * 2f;
        int n = tabs.size();
        int cols = n <= 3 ? Math.max(1, n) : 3;
        int rows = n == 0 ? 0 : (n + cols - 1) / cols;
        float availW = (w - insL - insR) * 0.92f;
        float availH = areaBottom - top;
        if (rows > 0) {
            float cardW = Math.min((availW - gap * (cols - 1)) / cols, availH / rows * 0.95f * 1.15f);
            float cardH = Math.min((availH - gap * (rows - 1)) / rows, cardW * 1.05f);
            cardW = Math.min(cardW, cardH / 0.95f);
            float totalW = cardW * cols + gap * (cols - 1);
            float totalH = cardH * rows + gap * (rows - 1);
            float x0 = cx - totalW / 2f;
            float y0 = top + Math.max(0f, (availH - totalH) / 2f);
            for (int i = 0; i < n; i++) {
                int col = i % cols;
                int row = i / cols;
                RectF rc = new RectF(x0 + col * (cardW + gap), y0 + row * (cardH + gap),
                        x0 + col * (cardW + gap) + cardW, y0 + row * (cardH + gap) + cardH);
                drawTableCard(c, rc, tabs.get(i));
                menuRects.add(rc);
            }
        } else {
            RectF msg = new RectF(cx - availW / 2f, top, cx + availW / 2f, areaBottom);
            centerText(c, "Şu an boş masa yok", msg, 0xFFE6D3A3, availH * 0.09f);
        }
        float bw = Math.min(availW / Math.max(1, rest.size()) - gap, h * 0.5f);
        float tot = bw * rest.size() + gap * Math.max(0, rest.size() - 1);
        for (int i = 0; i < rest.size(); i++) {
            RectF rc = new RectF(cx - tot / 2f + i * (bw + gap), h - btnH - gap, cx - tot / 2f + i * (bw + gap) + bw,
                    h - gap);
            woodButton(c, rc, true, rest.get(i).primary);
            centerText(c, rest.get(i).label, rc, 0xFFFFF4D6, btnH * 0.38f);
            menuRects.add(rc);
        }
        List<MenuItem> ordered = new ArrayList<MenuItem>(tabs);
        ordered.addAll(rest);
        menuItems = ordered;
    }

    /** Bir masa: üstte oturan oyuncu, ortada tavla tahtası, altta boş sandalye ("OTUR"). */
    private void drawTableCard(Canvas c, RectF rc, MenuItem it) {
        if (it.seats >= 0) {
            drawSeatTable(c, rc, it);
            return;
        }
        float w = rc.width();
        float h = rc.height();
        float rad = Math.min(w, h) * 0.07f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, rc.top, 0, rc.bottom, new int[] {0xFF3A2516, 0xFF1D120A},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rc, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.012f));
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(rc, rad, rad, paint);
        // masa (tahta resmi)
        RectF tb = new RectF(rc.left + w * 0.12f, rc.top + h * 0.32f, rc.right - w * 0.12f, rc.top + h * 0.60f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha(110);
        c.drawRoundRect(shadowRect(tb, h * 0.012f), h * 0.03f, h * 0.03f, paint);
        paint.setAlpha(255);
        if (boardBmp != null) {
            c.drawBitmap(boardBmp, null, tb, null);
        } else {
            paint.setColor(0xFF7A5230);
            c.drawRoundRect(tb, h * 0.03f, h * 0.03f, paint);
        }
        // üst sandalye: oturan oyuncu
        float sr = Math.min(w * 0.11f, h * 0.085f);
        float sy = rc.top + h * 0.205f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF5A3A20);
        c.drawRoundRect(new RectF(rc.centerX() - sr * 1.5f, sy - sr * 1.25f, rc.centerX() + sr * 1.5f, sy + sr * 1.25f),
                sr * 0.6f, sr * 0.6f, paint);
        if (whiteBmp != null) {
            rect.set(rc.centerX() - sr, sy - sr, rc.centerX() + sr, sy + sr);
            c.drawBitmap(whiteBmp, null, rect, null);
        }
        RectF nm = new RectF(rc.left + w * 0.05f, rc.top + h * 0.03f, rc.right - w * 0.05f, rc.top + h * 0.12f);
        centerText(c, it.label, nm, 0xFFFFF4D6, h * 0.075f);
        // alt sandalye: boş yer
        float by = rc.top + h * 0.795f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF5A3A20);
        c.drawRoundRect(new RectF(rc.centerX() - sr * 1.5f, by - sr * 1.25f, rc.centerX() + sr * 1.5f, by + sr * 1.25f),
                sr * 0.6f, sr * 0.6f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.5f, sr * 0.12f));
        paint.setColor(0xFFFFD54F);
        c.drawCircle(rc.centerX(), by, sr * 0.92f, paint);
        RectF ot = new RectF(rc.left + w * 0.05f, rc.top + h * 0.89f, rc.right - w * 0.05f, rc.top + h * 0.98f);
        centerText(c, "OTUR", ot, 0xFFFFD54F, h * 0.085f);
        // seviye bilgisi
        RectF sb = new RectF(tb.left, tb.bottom + h * 0.01f, tb.right, tb.bottom + h * 0.075f);
        centerText(c, it.sub, sb, 0xFFC9B58A, h * 0.055f);
    }

    private static final int[] SEAT_COLORS = {0xFF96503C, 0xFF3C8C5A, 0xFF3C78AA, 0xFF78469A};

    private void seatAvatar(Canvas c, float x, float y, float r, int col) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFC8A046);
        c.drawCircle(x, y, r * 1.12f, paint);
        paint.setColor(col);
        c.drawCircle(x, y, r, paint);
        paint.setColor(0xFFFFF4D6);
        c.drawCircle(x, y - r * 0.28f, r * 0.34f, paint);
        c.save();
        Path clip = new Path();
        clip.addCircle(x, y, r, Path.Direction.CW);
        c.clipPath(clip);
        Path body = new Path();
        body.addCircle(x, y + r * 0.85f, r * 0.62f, Path.Direction.CW);
        c.drawPath(body, paint);
        c.restore();
    }

    private void seatEmpty(Canvas c, float x, float y, float r, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF3A2516);
        c.drawCircle(x, y, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, r * 0.12f));
        paint.setColor(0xFFE9C46A);
        c.drawCircle(x, y, r, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, label, new RectF(x - r, y - r, x + r, y + r), 0xFFE9C46A, label.length() > 1 ? r * 0.5f : r * 0.9f);
    }

    /** Masa yüzeyi: kağıt oyunlarında yeşil çuha, taş oyunlarında ahşap masa. */
    private void seatTableTop(Canvas c, RectF t, boolean small) {
        float rad = Math.min(t.width(), t.height()) * 0.22f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x66000000);
        c.drawRoundRect(shadowRect(t, t.height() * 0.04f), rad, rad, paint);
        paint.setColor(menuBg == 2 ? 0xFF1E6B47 : 0xFF9A6A3C);
        c.drawRoundRect(t, rad, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, t.height() * 0.045f));
        paint.setColor(menuBg == 2 ? 0xFF8C6937 : 0xFFE0B65C);
        c.drawRoundRect(t, rad, rad, paint);
        // masanın üstünde küçük bir süs: taşlar ya da kartlar
        float iw = t.width() * 0.11f;
        float ih = iw * (menuBg == 2 ? 1.4f : 1.35f);
        float x0 = t.centerX() - iw * 1.7f;
        for (int i = 0; i < 3; i++) {
            RectF r = new RectF(x0 + i * iw * 1.15f, t.centerY() - ih / 2f, x0 + i * iw * 1.15f + iw, t.centerY() + ih / 2f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFAF4E2);
            c.drawRoundRect(r, iw * 0.15f, iw * 0.15f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, iw * 0.05f));
            paint.setColor(i == 1 ? 0xFFC83C3C : 0xFF33302A);
            c.drawRoundRect(r, iw * 0.15f, iw * 0.15f, paint);
        }
    }

    /** Açık masalar listesindeki bir 4 kişilik masa: 4 koltuk, oturanlar avatarla, boş koltukta +. */
    private void drawSeatTable(Canvas c, RectF rc, MenuItem it) {
        float w = rc.width();
        float h = rc.height();
        float rad = Math.min(w, h) * 0.07f;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, rc.top, 0, rc.bottom, new int[] {0xFF3A2516, 0xFF1D120A},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rc, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, h * 0.012f));
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(rc, rad, rad, paint);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, it.label, new RectF(rc.left + w * 0.05f, rc.top + h * 0.025f, rc.right - w * 0.05f, rc.top + h * 0.115f),
                0xFFFFF4D6, h * 0.075f);
        float sr = Math.min(w * 0.085f, h * 0.075f);
        RectF t = new RectF(rc.left + w * 0.25f, rc.top + h * 0.34f, rc.right - w * 0.25f, rc.top + h * 0.60f);
        seatTableTop(c, t, true);
        float[][] pos = {{rc.centerX(), rc.top + h * 0.255f}, {rc.left + w * 0.115f, rc.top + h * 0.47f},
            {rc.right - w * 0.115f, rc.top + h * 0.47f}, {rc.centerX(), rc.top + h * 0.675f}};
        for (int i = 0; i < 4; i++) {
            if (i < it.seats) {
                seatAvatar(c, pos[i][0], pos[i][1], sr, SEAT_COLORS[i]);
            } else {
                seatEmpty(c, pos[i][0], pos[i][1], sr, "+");
            }
        }
        if (it.host.length() > 0) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
            centerText(c, it.host, new RectF(rc.left + w * 0.2f, rc.top + h * 0.735f, rc.right - w * 0.2f, rc.top + h * 0.80f),
                    0xFFFFF4D6, h * 0.05f);
        }
        centerText(c, it.sub, new RectF(rc.left + w * 0.04f, rc.top + h * 0.80f, rc.right - w * 0.04f, rc.top + h * 0.875f),
                0xFFC9B58A, h * 0.05f);
        centerText(c, "OTUR", new RectF(rc.left + w * 0.05f, rc.top + h * 0.89f, rc.right - w * 0.05f, rc.top + h * 0.98f),
                0xFFFFD54F, h * 0.085f);
    }

    /** Masa bekleme odası: büyük 4 kişilik masa. */
    private void drawSeatLobby(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        drawTitle(c, cx, h, h * 0.085f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, seatSub, new RectF(cx - w * 0.4f, h * 0.025f + h * 0.085f * 1.25f, cx + w * 0.4f,
                h * 0.025f + h * 0.085f * 1.25f + h * 0.06f), 0xFFC9B58A, h * 0.04f);
        float top = h * 0.21f;
        float bottom = h * 0.80f;
        float ch = bottom - top;
        float sr = ch * 0.085f;
        float tw2 = Math.min(ch * 1.6f, (w - insL - insR) * 0.5f);
        float th2 = ch * 0.34f;
        float ty = top + (ch - th2) / 2f;
        RectF t = new RectF(cx - tw2 / 2f, ty, cx + tw2 / 2f, ty + th2);
        seatTableTop(c, t, false);
        float[][] pos = {{cx, t.top - sr * 1.35f}, {t.left - sr * 1.7f, t.centerY()}, {t.right + sr * 1.7f, t.centerY()},
            {cx, t.bottom + sr * 1.35f}};
        for (int i = 0; i < 4; i++) {
            boolean filled = seatNames[i] != null && seatNames[i].length() > 0;
            String nm = filled ? seatNames[i] + (i == seatMine ? " (sen)" : "") : "boş (bot)";
            if (filled) {
                seatAvatar(c, pos[i][0], pos[i][1], sr, SEAT_COLORS[i]);
            } else {
                seatEmpty(c, pos[i][0], pos[i][1], sr, "BOT");
            }
            text.setTypeface(Typeface.DEFAULT_BOLD);
            int col = filled ? 0xFFFFF4D6 : 0xFFC9B58A;
            if (i == 0 || i == 3) {
                float nx = pos[i][0] + sr * 1.3f;
                centerText(c, nm, new RectF(nx, pos[i][1] - sr * 0.4f, nx + sr * 3.6f, pos[i][1] + sr * 0.4f), col, sr * 0.6f);
            } else {
                float ny = pos[i][1] + sr * 1.5f;
                centerText(c, nm, new RectF(pos[i][0] - sr * 2.5f, ny - sr * 0.4f, pos[i][0] + sr * 2.5f, ny + sr * 0.4f), col, sr * 0.55f);
            }
        }
        drawButtonRow(c, cx, h, Math.min((w - insL - insR) * 0.92f, h * 2.2f));
    }

    private void drawMenu(Canvas c) {
        if (tablesMode) {
            drawTables(c);
            return;
        }
        if (homeMode) {
            drawHome(c);
            return;
        }
        for (MenuItem it : menuItems) {
            if (TileIcons.isTile(it.icon)) {
                drawTileMenu(c);
                return;
            }
        }
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        int n = menuItems.size();
        int cols = n > 6 ? 2 : 1;
        int rows = (n + cols - 1) / cols;
        float titleH = h * 0.24f;
        float availH = h - titleH - h * 0.06f;
        float gap = Math.min(h * 0.022f, 18f);
        float bh = Math.min(h * 0.13f, (availH - gap * (rows - 1)) / rows);
        float bw = Math.min((w - insL - insR) * (cols == 1 ? 0.5f : 0.4f), bh * (cols == 1 ? 7f : 6f));
        float totalW = bw * cols + gap * (cols - 1);
        float y0 = h * 0.03f + titleH + Math.max(0f, (availH - (bh * rows + gap * (rows - 1))) / 2f);

        // başlık
        RectF tr = new RectF(cx - w * 0.4f, h * 0.035f, cx + w * 0.4f, h * 0.035f + titleH * 0.62f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, menuTitle, shadowRect(tr, h * 0.006f), 0xFF000000, titleH * 0.5f);
        centerText(c, menuTitle, tr, 0xFFE9C46A, titleH * 0.5f);
        RectF sr = new RectF(cx - w * 0.4f, tr.bottom, cx + w * 0.4f, tr.bottom + titleH * 0.26f);
        centerText(c, menuSub, sr, 0xFFC9B58A, titleH * 0.17f);

        menuRects.clear();
        for (int i = 0; i < n; i++) {
            int col = cols == 1 ? 0 : i / rows;
            int row = cols == 1 ? i : i % rows;
            float x = cx - totalW / 2f + col * (bw + gap);
            float y = y0 + row * (bh + gap);
            RectF rc = new RectF(x, y, x + bw, y + bh);
            menuRects.add(rc);
            MenuItem it = menuItems.get(i);
            woodButton(c, rc, true, it.primary);
            centerText(c, it.label, rc, 0xFFFFF4D6, bh * 0.36f);
        }
    }

    /** Kare düğmeli menü: simge + iki satıra kadar etiket + küçük durum yazısı; kalanlar alt sırada. */
    private void drawTileMenu(Canvas c) {
        float w = getWidth();
        float h = getHeight();
        float cx = (insL + w - insR) / 2f;
        float titleH = h * 0.24f;
        RectF tr = new RectF(cx - w * 0.4f, h * 0.035f, cx + w * 0.4f, h * 0.035f + titleH * 0.62f);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        centerText(c, menuTitle, shadowRect(tr, h * 0.006f), 0xFF000000, titleH * 0.5f);
        centerText(c, menuTitle, tr, 0xFFE9C46A, titleH * 0.5f);
        RectF sr = new RectF(cx - w * 0.4f, tr.bottom, cx + w * 0.4f, tr.bottom + titleH * 0.26f);
        centerText(c, menuSub, sr, 0xFFC9B58A, titleH * 0.17f);

        int nt = 0;
        int nr = 0;
        int ng = 0;
        for (MenuItem it : menuItems) {
            if (TileIcons.isTile(it.icon)) {
                nt++;
            } else if (it.toggle > 0) {
                ng++;
            } else {
                nr++;
            }
        }
        float togH = h * 0.082f;
        float togGap = h * 0.012f;
        float togBlock = ng == 0 ? 0f : ng * togH + (ng - 1) * togGap + h * 0.02f;
        int cols = nt <= 5 ? nt : (nt + 1) / 2;
        int rows = (nt + cols - 1) / cols;
        float availW = (w - insL - insR) * (nt >= 5 ? 0.72f : 0.92f);
        float top = h * 0.03f + titleH;
        float restH = nr == 0 ? 0f : h * 0.105f;
        float gap = Math.min(h * 0.03f, 26f);
        float availH = h - top - restH - (nr == 0 ? 0f : gap) - h * 0.04f - togBlock;
        float sz = Math.min((availW - gap * (cols - 1)) / cols, (availH - gap * (rows - 1)) / rows);
        sz = Math.min(sz, ng > 0 ? h * 0.2f : h * 0.4f);
        float gridH = sz * rows + gap * (rows - 1);
        float y0 = top + Math.max(0f, (availH - gridH) / 2f);

        menuRects.clear();
        int ti = 0;
        List<RectF> restRects = new ArrayList<RectF>();
        for (MenuItem it : menuItems) {
            if (!TileIcons.isTile(it.icon)) {
                menuRects.add(null);
                continue;
            }
            int row = ti / cols;
            int inRow = Math.min(cols, nt - row * cols);
            int col = ti % cols;
            float rowW = sz * inRow + gap * (inRow - 1);
            float x = cx - rowW / 2f + col * (sz + gap);
            float y = y0 + row * (sz + gap);
            RectF rc = new RectF(x, y, x + sz, y + sz);
            menuRects.add(rc);
            woodButton(c, rc, true, it.primary);
            // simge
            RectF art = new RectF(rc.left + sz * 0.13f, rc.top + sz * 0.07f, rc.right - sz * 0.13f, rc.top + sz * 0.62f);
            TileIcons.draw(c, art, it.icon, paint, text);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            // etiket (uzunsa iki satır)
            String[] lines = splitLabel(it.label);
            boolean hasSub = it.sub != null && it.sub.length() > 0;
            float labTop = rc.top + sz * (hasSub ? 0.63f : 0.68f);
            float labH = sz * (hasSub ? 0.22f : 0.26f);
            float fs = sz * (lines.length > 1 ? 0.092f : 0.115f);
            for (int k = 0; k < lines.length; k++) {
                RectF lr = new RectF(rc.left, labTop + labH * k / lines.length, rc.right,
                        labTop + labH * (k + 1) / lines.length);
                centerText(c, lines[k], lr, 0xFFFFF4D6, fs);
            }
            if (hasSub) {
                RectF sb = new RectF(rc.left + sz * 0.05f, rc.top + sz * 0.84f, rc.right - sz * 0.05f, rc.top + sz * 0.96f);
                centerText(c, it.sub, sb, 0xFFE9C46A, sz * 0.082f);
            }
            ti++;
        }
        if (ng > 0) {
            float tw = Math.min((w - insL - insR) * 0.62f, h * 1.1f);
            float ty = y0 + gridH + h * 0.02f;
            int gi = 0;
            for (int i = 0; i < menuItems.size(); i++) {
                MenuItem it = menuItems.get(i);
                if (it.toggle == 0 || TileIcons.isTile(it.icon)) {
                    continue;
                }
                RectF rc = new RectF(cx - tw / 2f, ty + gi * (togH + togGap), cx + tw / 2f, ty + gi * (togH + togGap) + togH);
                menuRects.set(i, rc);
                woodButton(c, rc, true, false);
                text.setTypeface(Typeface.DEFAULT_BOLD);
                text.setTextAlign(Paint.Align.LEFT);
                text.setColor(0xFFFFF4D6);
                text.setTextSize(togH * 0.36f);
                c.drawText(it.label, rc.left + togH * 0.4f, rc.top + togH * 0.47f, text);
                text.setColor(0xFFC9B58A);
                text.setTextSize(togH * 0.22f);
                c.drawText(it.sub == null ? "" : it.sub, rc.left + togH * 0.4f, rc.top + togH * 0.8f, text);
                // anahtar
                boolean on = it.toggle == 2;
                float sw = togH * 1.35f;
                float sh = togH * 0.5f;
                RectF tr2 = new RectF(rc.right - togH * 0.4f - sw, rc.centerY() - sh / 2f - togH * 0.07f, rc.right - togH * 0.4f,
                        rc.centerY() + sh / 2f - togH * 0.07f);
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(null);
                paint.setColor(on ? 0xFF3CAA5A : 0xFF6E5A46);
                c.drawRoundRect(tr2, sh / 2f, sh / 2f, paint);
                float kx = on ? tr2.right - sh / 2f : tr2.left + sh / 2f;
                paint.setColor(0xFFFFF4D6);
                c.drawCircle(kx, tr2.centerY(), sh * 0.38f, paint);
                text.setTextAlign(Paint.Align.CENTER);
                text.setTextSize(togH * 0.2f);
                text.setColor(on ? 0xFF82E696 : 0xFFBEAA8C);
                c.drawText(on ? "AÇIK" : "KAPALI", tr2.centerX(), rc.bottom - togH * 0.12f, text);
                text.setTextAlign(Paint.Align.LEFT);
                gi++;
            }
        }
        if (nr > 0) {
            float bh = restH;
            float bw = Math.min(sz * 1.5f, availW / nr);
            float tot = bw * nr + gap * (nr - 1);
            float y = h - bh - h * 0.035f;
            int ri = 0;
            for (int i = 0; i < menuItems.size(); i++) {
                MenuItem it = menuItems.get(i);
                if (TileIcons.isTile(it.icon) || it.toggle > 0) {
                    continue;
                }
                float x = cx - tot / 2f + ri * (bw + gap);
                RectF rc = new RectF(x, y, x + bw, y + bh);
                menuRects.set(i, rc);
                woodButton(c, rc, true, it.primary);
                centerText(c, it.label, rc, 0xFFFFF4D6, bh * 0.36f);
                ri++;
            }
        }
    }

    /** Etiketi, uzunsa boşluktan ortaya en yakın yerden iki satıra böler. */
    private static String[] splitLabel(String s) {
        if (s.length() <= 9 || s.indexOf(' ') < 0) {
            return new String[] {s};
        }
        int best = -1;
        int mid = s.length() / 2;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == ' ' && (best < 0 || Math.abs(i - mid) < Math.abs(best - mid))) {
                best = i;
            }
        }
        return new String[] {s.substring(0, best), s.substring(best + 1)};
    }

    private RectF shadowRect(RectF r, float d) {
        return new RectF(r.left + d, r.top + d, r.right + d, r.bottom + d);
    }

    private void drawTableBg(Canvas c) {
        int bw = Math.max(1, Math.round(bx1 - bx0));
        int bh = Math.max(1, Math.round(by1 - by0));
        int key = (bw * 31 + bh) * 4 + menuBg;
        if (tableBmp == null || tableBmpKey != key) {
            tableBmp = Gfx.buildTable(bw, bh, menuBg == 2);
            tableBmpKey = key;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha(110);
        rect.set(bx0 + 6 * u, by0 + 10 * u, bx1 + 6 * u, by1 + 10 * u);
        c.drawRoundRect(rect, geo.fr * 0.9f, geo.fr * 0.9f, paint);
        paint.setAlpha(255);
        c.drawBitmap(tableBmp, bx0, by0, null);
    }

    private void drawBoard(Canvas c) {
        // gölge
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha(110);
        rect.set(bx0 + 6 * u, by0 + 10 * u, bx1 + 6 * u, by1 + 10 * u);
        c.drawRoundRect(rect, geo.fr * 0.9f, geo.fr * 0.9f, paint);
        paint.setAlpha(255);
        if (boardBmp != null) {
            c.drawBitmap(boardBmp, bx0, by0, null);
        }
    }

    private void drawTriangleOverlay(Canvas c, int pt, int color, int alpha) {
        float x = colLeft(colOf(disp(pt)));
        android.graphics.Path t = new android.graphics.Path();
        if (ptTop(pt)) {
            t.moveTo(x + cw * 0.035f, iy0);
            t.lineTo(x + cw * 0.965f, iy0);
            t.lineTo(x + cw * 0.5f, iy0 + triH * 0.94f);
        } else {
            t.moveTo(x + cw * 0.035f, iy1);
            t.lineTo(x + cw * 0.965f, iy1);
            t.lineTo(x + cw * 0.5f, iy1 - triH * 0.94f);
        }
        t.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setAlpha(alpha);
        c.drawPath(t, paint);
        paint.setAlpha(255);
    }

    private void drawMarks(Canvas c) {
        if (game.lastFrom >= 1 && game.lastFrom <= 24) {
            drawTriangleOverlay(c, game.lastFrom, 0xFFFFFFFF, 34);
        }
        if (game.lastTo >= 1 && game.lastTo <= 24) {
            drawTriangleOverlay(c, game.lastTo, 0xFFFFE08A, 52);
        }
    }

    private boolean animActive() {
        return !game.anim.isEmpty() && System.currentTimeMillis() - game.animT0 < game.animTotal;
    }

    /** Animasyon sürerken, henüz yerine varmamış taşların gizleneceği sayılar. */
    private final int[][] hideStack = new int[2][26];
    private final int[] hideBar = new int[2];

    private void computeHides() {
        for (int p = 0; p < 2; p++) {
            java.util.Arrays.fill(hideStack[p], 0);
            hideBar[p] = 0;
        }
        if (!animActive()) {
            return;
        }
        long now = System.currentTimeMillis() - game.animT0;
        List<int[]> segs = game.anim;
        for (int i = 0; i < segs.size(); i++) {
            int[] s = segs.get(i);
            if (now >= s[3] + Game.ANIM_MS) {
                continue; // bu parça bitti
            }
            if (s[4] == 1) {
                hideBar[s[2]]++;
            } else if (s[1] >= 1 && s[1] <= 24) {
                boolean finalLeg = true;
                for (int j = i + 1; j < segs.size(); j++) {
                    int[] o = segs.get(j);
                    if (o[4] == 0 && o[2] == s[2] && o[0] == s[1]) {
                        finalLeg = false;
                    }
                }
                if (finalLeg) {
                    hideStack[s[2]][s[1]]++;
                }
            }
        }
    }

    /** Noktadaki 'color' renginin en üst taşının sıra numarası (yoksa toplam sayı: yığının hemen üstü). */
    private int topIndex(int pt, int color) {
        Board b = game.board;
        int pin = b.pinned[pt];
        int idx = pin != Board.NONE ? 1 : 0;
        int g0 = b.cnt[0][pt] - (pin == 0 ? 1 : 0);
        int g1 = b.cnt[1][pt] - (pin == 1 ? 1 : 0);
        int grp = color == 0 ? g0 : g1;
        if (grp <= 0) {
            return b.cnt[0][pt] + b.cnt[1][pt];
        }
        return idx + g0 + (color == 1 ? g1 : 0) - 1;
    }

    private float[] pointSlot(int pt, int color, boolean beyond) {
        Board b = game.board;
        int total = b.cnt[0][pt] + b.cnt[1][pt];
        int idx = beyond ? total : topIndex(pt, color);
        int cnt = Math.max(total, idx + 1);
        return new float[] {ptCx(pt), slotY(pt, idx, stackSpacing(cnt))};
    }

    /** Kayan taşları çizer (tahta son durumda; taşlar yerlerine varana dek kayar). */
    private void drawSlides(Canvas c) {
        if (!animActive() || whiteBmp == null) {
            return;
        }
        long now = System.currentTimeMillis() - game.animT0;
        boolean any = false;
        for (int[] s : game.anim) {
            long t0 = s[3];
            if (now < t0 || now >= t0 + Game.ANIM_MS) {
                continue;
            }
            any = true;
            int col = s[2];
            float[] a;
            float[] bb;
            boolean off = s[1] == Board.OFF;
            if (s[4] == 1) {
                int pt = s[0];
                a = new float[] {ptCx(pt), slotY(pt, 0, stackSpacing(game.board.cnt[0][pt] + game.board.cnt[1][pt]))};
                bb = barSlot(col, Math.max(0, game.board.bar[col] - 1));
            } else {
                if (s[0] >= 1 && s[0] <= 24) {
                    a = pointSlot(s[0], col, true);
                } else if (s[0] == Board.BAR) {
                    a = barSlot(col, game.board.bar[col]);
                } else {
                    continue;
                }
                if (off) {
                    boolean top = col != game.viewColor();
                    RectF tr = trayRect(top);
                    bb = new float[] {tr.centerX(), top ? tr.top + r : tr.bottom - r};
                } else if (s[1] >= 1 && s[1] <= 24) {
                    bb = pointSlot(s[1], col, false);
                } else {
                    continue;
                }
            }
            float t = (now - t0) / (float) Game.ANIM_MS;
            float e = t * t * (3f - 2f * t);
            float x = a[0] + (bb[0] - a[0]) * e;
            float y = a[1] + (bb[1] - a[1]) * e;
            float lift = (float) Math.sin(Math.PI * t);
            float size = chkSize * (1f + 0.14f * lift) * (off ? (1f - 0.35f * e) : 1f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFF000000);
            paint.setAlpha((int) (70 * lift));
            c.drawOval(new RectF(x - size * 0.42f, y + size * 0.30f + lift * r * 0.5f, x + size * 0.42f,
                    y + size * 0.52f + lift * r * 0.5f), paint);
            paint.setAlpha(255);
            y -= lift * r * 0.55f;
            Bitmap bm = col == Board.WHITE ? whiteBmp : blackBmp;
            rect.set(x - size / 2f, y - size / 2f, x + size / 2f, y + size / 2f);
            c.drawBitmap(bm, null, rect, null);
        }
        if (any || animActive()) {
            handler.removeCallbacks(animTick);
            handler.postDelayed(animTick, 16);
        }
    }

    private final Runnable animTick = new Runnable() {
        @Override
        public void run() {
            invalidate();
        }
    };

    private void drawStacks(Canvas c) {
        if (whiteBmp == null) {
            return;
        }
        Board b = game.board;
        computeHides();
        for (int pt = 1; pt <= 24; pt++) {
            int total = b.cnt[0][pt] + b.cnt[1][pt];
            if (total == 0) {
                continue;
            }
            int pin = b.pinned[pt];
            int[] order = new int[total];
            int n = 0;
            if (pin != Board.NONE) {
                order[n++] = pin;
            }
            for (int p = 0; p < 2; p++) {
                int count = b.cnt[p][pt] - (p == pin ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    order[n++] = p;
                }
            }
            float cx = ptCx(pt);
            float sp = stackSpacing(total);
            int[] firstHidden = {total, total};
            for (int p = 0; p < 2; p++) {
                if (hideStack[p][pt] > 0 && b.cnt[p][pt] > 0) {
                    firstHidden[p] = Math.max(0, topIndex(pt, p) + 1 - hideStack[p][pt]);
                }
            }
            for (int k = 0; k < total; k++) {
                float cy = slotY(pt, k, sp);
                if (k >= firstHidden[order[k]] && !(k == 0 && pin != Board.NONE)) {
                    continue; // bu taş kayarak geliyor
                }
                Bitmap bm = order[k] == Board.WHITE ? whiteBmp : blackBmp;
                c.drawBitmap(bm, cx - chkSize / 2f, cy - chkSize / 2f, null);
                if (k == 0 && pin != Board.NONE) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(Math.max(3f, r * 0.16f));
                    paint.setColor(0xFFE53935);
                    c.drawCircle(cx, cy, r * 0.9f, paint);
                }
            }
            if (total > 5) {
                float cy = slotY(pt, total - 1, sp);
                rect.set(cx - r * 0.5f, cy - r * 0.5f, cx + r * 0.5f, cy + r * 0.5f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xCC000000);
                c.drawCircle(cx, cy, r * 0.5f, paint);
                centerText(c, String.valueOf(total), rect, 0xFFFFFFFF, r * 0.7f);
            }
        }
    }

    private float barSize() {
        return Math.min(0.58f * cw, 2f * r * 0.92f);
    }

    /** Orta şeritte (bar) oyuncunun k. taşının merkezi. Alttaki oyuncu alt yarıda, üsttekisi üst yarıda durur. */
    private float[] barSlot(int player, int k) {
        float size = barSize();
        float mid = (iy0 + iy1) / 2f;
        float sp = size * 1.03f;
        float avail = (iy1 - iy0) / 2f - size * 1.3f;
        if (k * sp > avail && k > 0) {
            sp = avail / k;
        }
        boolean bottom = player == game.viewColor();
        float cy = bottom ? mid + size * 0.72f + k * sp : mid - size * 0.72f - k * sp;
        return new float[] {ix0 + 6.3f * cw, cy};
    }

    private void drawBar(Canvas c) {
        if (!game.board.isClassic() || whiteBmp == null) {
            return;
        }
        float size = barSize();
        for (int p = 0; p < 2; p++) {
            int n = game.board.bar[p];
            Bitmap bm = p == Board.WHITE ? whiteBmp : blackBmp;
            for (int k = 0; k < n - hideBar[p]; k++) {
                float[] pos = barSlot(p, k);
                rect.set(pos[0] - size / 2f, pos[1] - size / 2f, pos[0] + size / 2f, pos[1] + size / 2f);
                c.drawBitmap(bm, null, rect, null);
            }
            if (n > 1) {
                float[] pos = barSlot(p, n - 1);
                rect.set(pos[0] - size / 2f, pos[1] - size / 2f, pos[0] + size / 2f, pos[1] + size / 2f);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xCC000000);
                c.drawCircle(pos[0], pos[1], size * 0.3f, paint);
                centerText(c, String.valueOf(n), rect, 0xFFFFFFFF, size * 0.42f);
            }
        }
    }

    private void drawRing(Canvas c, float cx, float cy, float radius, int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        c.drawCircle(cx, cy, radius, paint);
    }

    private void drawHints(Canvas c) {
        if (game.phase != Game.PH_MOVE || !game.isHumanTurn()) {
            return;
        }
        Board b = game.board;
        if (game.selected == 0) {
            boolean[] from = new boolean[26];
            for (Board.Move m : game.allowed) {
                from[m.from] = true;
            }
            if (from[Board.BAR]) {
                int n = b.bar[game.turn];
                float[] bp = barSlot(game.turn, Math.max(0, n - 1));
                drawRing(c, bp[0], bp[1], barSize() * 0.6f, 0xAAFFD54F, Math.max(3f, r * 0.12f));
            }
            for (int pt = 1; pt <= 24; pt++) {
                if (from[pt]) {
                    int total = b.cnt[0][pt] + b.cnt[1][pt];
                    float sp = stackSpacing(total);
                    drawRing(c, ptCx(pt), slotY(pt, total - 1, sp), r * 1.06f, 0xAAFFD54F, Math.max(3f, r * 0.12f));
                }
            }
            return;
        }
        int sel = game.selected;
        if (sel == Board.BAR) {
            float[] bp = barSlot(game.turn, Math.max(0, b.bar[game.turn] - 1));
            drawRing(c, bp[0], bp[1], barSize() * 0.62f, 0xFFFFD54F, Math.max(4f, r * 0.2f));
        } else {
            int total = b.cnt[0][sel] + b.cnt[1][sel];
            float sp = stackSpacing(total);
            drawRing(c, ptCx(sel), slotY(sel, total - 1, sp), r * 1.08f, 0xFFFFD54F, Math.max(4f, r * 0.2f));
        }
        for (Game.Dest d : game.dests) {
            if (d.to == Board.OFF) {
                continue;
            }
            int col = d.pin ? 0xFFFF9800 : (d.combo ? 0xFF29B6F6 : 0xFF66BB6A);
            drawTriangleOverlay(c, d.to, col, 105);
            int t = b.cnt[0][d.to] + b.cnt[1][d.to];
            float cy = slotY(d.to, Math.min(t, 4), 2f * r);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(col);
            paint.setAlpha(235);
            c.drawCircle(ptCx(d.to), cy, r * 0.56f, paint);
            paint.setAlpha(255);
            rect.set(ptCx(d.to) - r * 0.56f, cy - r * 0.56f, ptCx(d.to) + r * 0.56f, cy + r * 0.56f);
            centerText(c, String.valueOf(d.label), rect, 0xFFFFFFFF, r * 0.78f);
        }
    }

    private RectF trayRect(boolean top) {
        float tx0 = ix0 + 12.6f * cw + cw * 0.08f;
        float tx1 = bx1 - geo.fr;
        float mid = (iy0 + iy1) / 2f;
        if (top) {
            rect.set(tx0, iy0, tx1, mid - geo.fr * 0.15f);
        } else {
            rect.set(tx0, mid + geo.fr * 0.15f, tx1, iy1);
        }
        return rect;
    }

    private void drawTrays(Canvas c) {
        int bottomPlayer = game.viewColor();
        for (int i = 0; i < 2; i++) {
            boolean top = i == 0;
            int player = top ? 1 - bottomPlayer : bottomPlayer;
            RectF tr = new RectF(trayRect(top));
            int n = game.board.off[player];
            float chH = Math.min(r * 0.40f, (tr.height() - r * 1.4f) / 15f);
            float chW = tr.width() * 0.78f;
            for (int k = 0; k < n; k++) {
                float y = top ? tr.top + chH * 0.8f + k * chH : tr.bottom - chH * 0.8f - k * chH;
                rect.set(tr.centerX() - chW / 2f, y - chH * 0.44f, tr.centerX() + chW / 2f, y + chH * 0.44f);
                paint.setStyle(Paint.Style.FILL);
                paint.setShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                        player == Board.WHITE ? new int[] {0xFFFFFFFF, 0xFFC9BFA6} : new int[] {0xFF666666, 0xFF0C0C0C},
                        new float[] {0f, 1f}, Shader.TileMode.CLAMP));
                c.drawRoundRect(rect, chH * 0.3f, chH * 0.3f, paint);
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(1.5f);
                paint.setColor(player == Board.WHITE ? 0xFF8F8570 : 0xFF000000);
                c.drawRoundRect(rect, chH * 0.3f, chH * 0.3f, paint);
            }
            rect.set(tr.left, top ? tr.bottom - r * 0.9f : tr.top, tr.right, top ? tr.bottom : tr.top + r * 0.9f);
            centerText(c, n + "/15", rect, 0xFFF3E6C8, r * 0.62f);
            if (game.phase == Game.PH_MOVE && game.isHumanTurn() && game.selected != 0 && player == game.turn) {
                for (Game.Dest d : game.dests) {
                    if (d.to == Board.OFF) {
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(Math.max(3f, cw * 0.05f));
                        paint.setColor(d.combo ? 0xFF29B6F6 : 0xFF66BB6A);
                        c.drawRoundRect(tr, 8f * u, 8f * u, paint);
                    }
                }
            }
        }
    }

    /** Zar renkleri: [stil][sahip: 0 siyah, 1 beyaz] = {açık, koyu, nokta, kenar}. */
    private static final int[][][] DICE_COL = {
        {{0xFF3A3A3A, 0xFF0E0E0E, 0xFFF7F4EA, 0xFF000000}, {0xFFFFFBEF, 0xFFD8D0BC, 0xFF1E1E1E, 0xFF8F8570}},
        {{0xFFB3261E, 0xFF5E0B08, 0xFFFFF4D6, 0xFF2A0402}, {0xFFFF5A4D, 0xFFC4271D, 0xFFFFFFFF, 0xFF7A120C}},
        {{0xFF8A6A2E, 0xFF3F2C0C, 0xFFFFE9A0, 0xFF1E1404}, {0xFFFFEFA8, 0xFFD3A23A, 0xFF4A3008, 0xFF8A6414}},
        {{0xFF2A4F8C, 0xFF0B1B3A, 0xFFE6F1FF, 0xFF050B1A}, {0xFFD6ECFF, 0xFF7FB3E8, 0xFF12335E, 0xFF3F76B5}},
    };

    private void drawDie(Canvas c, float x, float y, float s, int v, int owner, boolean used) {
        int[] col = DICE_COL[diceStyle][owner == Board.WHITE ? 1 : 0];
        int light = col[0];
        int dark = col[1];
        int pip = col[2];
        int alpha = used ? 90 : 255;
        rect.set(x, y, x + s, y + s);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(x, y, x + s, y + s, new int[] {light, dark}, new float[] {0f, 1f},
                Shader.TileMode.CLAMP));
        paint.setAlpha(alpha);
        c.drawRoundRect(rect, s * 0.18f, s * 0.18f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, s * 0.04f));
        paint.setColor(col[3]);
        paint.setAlpha(alpha);
        c.drawRoundRect(rect, s * 0.18f, s * 0.18f, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pip);
        paint.setAlpha(alpha);
        float pr = s * 0.085f;
        float[][] pts = PIPS[v];
        for (int i = 0; i < pts.length; i++) {
            c.drawCircle(x + pts[i][0] * s, y + pts[i][1] * s, pr, paint);
        }
        paint.setAlpha(255);
    }

    private float halfCenterX(int player) {
        boolean mine = player == game.viewColor();
        return mine ? ix0 + 9.6f * cw : ix0 + 3f * cw;
    }

    private void drawDiceOrRoll(Canvas c) {
        float cy = (iy0 + iy1) / 2f;
        if (game.canRoll()) {
            float w = cw * 3.6f;
            float h = midH * 0.84f;
            float cx = halfCenterX(game.turn);
            rollBtn.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            woodButton(c, rollBtn, true, true);
            centerText(c, "ZAR AT", rollBtn, 0xFFFFF4D6, h * 0.5f);
            return;
        }
        if (!game.diceShown) {
            return;
        }
        boolean dbl = game.phase == Game.PH_ROLLING ? game.rollDouble : game.dice[0] == game.dice[1];
        int slots = dbl ? 4 : 2;
        float ds = Math.min(midH * 0.84f, cw * 0.95f);
        float gap = ds * 0.26f;
        float total = slots * ds + (slots - 1) * gap;
        float x = halfCenterX(game.diceOwner) - total / 2f;
        float y = cy - ds / 2f;
        for (int i = 0; i < slots; i++) {
            boolean used;
            if (game.phase == Game.PH_ROLLING) {
                used = false;
            } else if (dbl) {
                used = i >= game.remaining.size();
            } else {
                used = !game.remaining.contains(game.dice[i]);
            }
            float dx = x + i * (ds + gap);
            float dy = y;
            float deg = 0f;
            if (game.phase == Game.PH_ROLLING) {
                float f = Math.min(1f, Math.max(0f, (System.currentTimeMillis() - game.rollStart) / (float) Game.ROLL_MS));
                float q = Math.min(1f, Math.max(0f, (f - i * 0.05f) / 0.95f));
                float inv = 1f - q;
                dx += ds * 3.4f * inv * inv;
                dy -= ds * 0.85f * inv * inv * Math.abs((float) Math.cos(q * 2.5 * Math.PI));
                deg = inv * inv * 520f * (i % 2 == 0 ? 1f : -1f);
                handler.removeCallbacks(animTick);
                handler.postDelayed(animTick, 16);
            }
            if (deg != 0f) {
                c.save();
                c.rotate(deg, dx + ds / 2f, dy + ds / 2f);
                drawDie(c, dx, dy, ds, game.dice[dbl ? 0 : i], game.diceOwner, used);
                c.restore();
            } else {
                drawDie(c, dx, dy, ds, game.dice[dbl ? 0 : i], game.diceOwner, used);
            }
        }
    }

    // ------------------------------------------------------------------
    // Paneller
    // ------------------------------------------------------------------

    private void drawCard(Canvas c, RectF rc, int player) {
        float rad = rc.height() * 0.08f;
        boolean active = game.phase != Game.PH_OVER && game.turn == player;
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, rc.top, 0, rc.bottom, new int[] {0xF0432A19, 0xF0221409},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(rc, rad, rad, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(active ? Math.max(3f, rc.height() * 0.022f) : Math.max(1.5f, rc.height() * 0.009f));
        paint.setColor(active ? 0xFFFFD54F : 0xFF9C7A33);
        c.drawRoundRect(rc, rad, rad, paint);

        float h = rc.height();
        float ic = Math.min(h * 0.22f, rc.width() * 0.22f);
        Bitmap icon = player == Board.WHITE ? whiteBmp : blackBmp;
        if (icon != null) {
            rect.set(rc.centerX() - ic, rc.top + h * 0.08f, rc.centerX() + ic, rc.top + h * 0.08f + 2 * ic);
            c.drawBitmap(icon, null, rect, null);
        }
        // sıra kimdeyse yanıp söner; insan oyuncuda 60 sn halka + kalan süre
        boolean timedTurn = false;
        float frac = -1f;
        if (active && game.phase != Game.PH_OVER) {
            timedTurn = game.mode == Game.MODE_ONLINE || game.isHumanTurn();
            if (timedTurn) {
                long rem = Math.max(0L, game.turnDeadline - System.currentTimeMillis());
                frac = Math.min(1f, rem / (float) Game.TURN_MS);
            }
            float pulse = timedTurn ? TurnRing.pulse(frac) : TurnRing.pulse(1f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xFFFFD54F);
            paint.setAlpha((int) (26 + 64 * pulse));
            c.drawRoundRect(rc, rad, rad, paint);
            paint.setAlpha(255);
            if (timedTurn) {
                float stroke = Math.max(3f, h * 0.035f);
                float inset = stroke * 0.8f;
                TurnRing.draw(c, paint, rc, frac, -inset, Math.max(4f, rad - inset), stroke);
            }
            handler.removeCallbacks(animTick);
            handler.postDelayed(animTick, 80);
        }
        // puan
        RectF sc = new RectF(rc.left, rc.top + h * 0.08f + 2 * ic + h * 0.02f, rc.right, rc.bottom - h * 0.20f);
        centerText(c, String.valueOf(game.score[player]), sc, 0xFFFFF4D6, h * 0.34f);
        // çevrimiçi: oyuncu adı (bilgisayara karşı oyunda gerekmez)
        RectF nm = new RectF(rc.left + rc.width() * 0.04f, rc.bottom - h * 0.20f, rc.right - rc.width() * 0.04f,
                rc.bottom - h * 0.03f);
        String clock = "";
        if (timedTurn) {
            int secs = (int) Math.ceil(frac * Game.TURN_MS / 1000f);
            clock = (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
        }
        if (game.mode == Game.MODE_ONLINE) {
            centerText(c, timedTurn ? game.playerName(player) + " " + clock : game.playerName(player), nm,
                    active ? 0xFFFFD54F : 0xFFC9B58A, h * 0.11f);
        } else {
            centerText(c, timedTurn ? "SIRA " + clock : (active ? "SIRA" : "PUAN"), nm, active ? 0xFFFFD54F : 0xFFC9B58A, h * 0.09f);
        }
    }

    private String modeLabel() {
        String v = game.variant == Board.CLASSIC ? "Klasik Tavla" : "Hapis Tavla";
        if (game.mode == Game.MODE_AI) {
            String[] n = {"Kolay", "Orta", "Zor"};
            return v + " · " + n[Math.max(0, Math.min(2, game.aiLevel))];
        }
        if (game.mode == Game.MODE_TWO) {
            return v;
        }
        return v + " · Çevrimiçi";
    }

    private void drawLeftPanel(Canvas c) {
        int bottom = game.viewColor();
        drawCard(c, card0, 1 - bottom);
        drawCard(c, card1, bottom);
        RectF mid = new RectF(leftPanel.left, card0.bottom, leftPanel.right, card1.top);
        float mh = Math.min(mid.height() * 0.5f, leftPanel.width() * 0.36f);
        RectF l1 = new RectF(mid.left, mid.centerY() - mh * 0.5f, mid.right, mid.centerY());
        RectF l2 = new RectF(mid.left, mid.centerY(), mid.right, mid.centerY() + mh * 0.5f);
        centerText(c, modeLabel(), l1, 0xFFC9B58A, mh * 0.30f);
        if (game.target > 1) {
            centerText(c, game.target + " puana kadar", l2, 0xFFE9C46A, mh * 0.30f);
        }
        drawBubble(c, mid);
    }

    private void drawBubble(Canvas c, RectF mid) {
        if (chatOpen || bubbleId < 0 || SystemClock.uptimeMillis() > bubbleUntil) {
            if (!chatOpen) {
                bubbleText = null;
            }
            return;
        }
        float w = leftPanel.width() * 0.98f;
        float hh = Math.min(w * 0.62f, mid.height() * 0.46f);
        float top = bubbleMine ? mid.bottom - hh - 4f : mid.top + 4f;
        RectF bb = new RectF(leftPanel.left + (leftPanel.width() - w) / 2f, top,
                leftPanel.left + (leftPanel.width() - w) / 2f + w, top + hh);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bubbleMine ? 0xFFE9D9A6 : 0xFFFFF4D6);
        c.drawRoundRect(bb, hh * 0.28f, hh * 0.28f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, hh * 0.04f));
        paint.setColor(bubbleMine ? 0xFF9C7A33 : 0xFFD4AF37);
        c.drawRoundRect(bb, hh * 0.28f, hh * 0.28f, paint);
        if (bubbleText != null) {
            RectF t = new RectF(bb.left + 6f, bb.top, bb.right - 6f, bb.bottom);
            centerText(c, bubbleText, t, 0xFF2B1A0E, hh * 0.26f);
        } else if (bubbleId < Game.EMOTE_FACES) {
            drawFace(c, bb.centerX(), bb.centerY(), hh * 0.38f, bubbleId);
        } else {
            RectF t = new RectF(bb.left + 4f, bb.top, bb.right - 4f, bb.bottom);
            centerText(c, Game.EMOTES[bubbleId], t, 0xFF2B1A0E, hh * 0.30f);
        }
    }

    /** Yüz ifadesi (emoji) çizer: yazı tipine bağlı değildir. */
    private void drawFace(Canvas c, float cx, float cy, float rad, int id) {
        int base = id == 3 ? 0xFFE5533D : 0xFFFFC83D;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(base);
        c.drawCircle(cx, cy, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, rad * 0.07f));
        paint.setColor(0xFF8A5A12);
        c.drawCircle(cx, cy, rad, paint);
        int ink = 0xFF4A2D12;
        float ex = rad * 0.38f;
        float ey = cy - rad * 0.20f;
        float er = rad * 0.11f;
        paint.setStrokeWidth(Math.max(2.5f, rad * 0.10f));
        paint.setColor(ink);
        RectF a = new RectF();
        switch (id) {
            case 0: // gülümseme
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - ex, ey, er, paint);
                c.drawCircle(cx + ex, ey, er, paint);
                paint.setStyle(Paint.Style.STROKE);
                a.set(cx - rad * 0.5f, cy - rad * 0.28f, cx + rad * 0.5f, cy + rad * 0.55f);
                c.drawArc(a, 25f, 130f, false, paint);
                break;
            case 1: // kahkaha: kapalı gözler, açık ağız
                a.set(cx - ex - er * 1.6f, ey - er * 1.2f, cx - ex + er * 1.6f, ey + er * 1.8f);
                c.drawArc(a, 200f, 140f, false, paint);
                a.set(cx + ex - er * 1.6f, ey - er * 1.2f, cx + ex + er * 1.6f, ey + er * 1.8f);
                c.drawArc(a, 200f, 140f, false, paint);
                paint.setStyle(Paint.Style.FILL);
                a.set(cx - rad * 0.52f, cy - rad * 0.12f, cx + rad * 0.52f, cy + rad * 0.62f);
                c.drawArc(a, 0f, 180f, true, paint);
                paint.setColor(0xFFE8737D);
                a.set(cx - rad * 0.26f, cy + rad * 0.30f, cx + rad * 0.26f, cy + rad * 0.62f);
                c.drawArc(a, 0f, 180f, true, paint);
                break;
            case 2: // şaşkın
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - ex, ey, er * 1.2f, paint);
                c.drawCircle(cx + ex, ey, er * 1.2f, paint);
                c.drawOval(new RectF(cx - rad * 0.16f, cy + rad * 0.22f, cx + rad * 0.16f, cy + rad * 0.66f), paint);
                break;
            case 3: // kızgın
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - ex, ey + er, er, paint);
                c.drawCircle(cx + ex, ey + er, er, paint);
                paint.setStyle(Paint.Style.STROKE);
                c.drawLine(cx - ex - er * 2f, ey - er * 2f, cx - ex + er * 1.8f, ey - er * 0.6f, paint);
                c.drawLine(cx + ex + er * 2f, ey - er * 2f, cx + ex - er * 1.8f, ey - er * 0.6f, paint);
                a.set(cx - rad * 0.4f, cy + rad * 0.32f, cx + rad * 0.4f, cy + rad * 0.9f);
                c.drawArc(a, 210f, 120f, false, paint);
                break;
            case 4: // üzgün
                paint.setStyle(Paint.Style.FILL);
                c.drawCircle(cx - ex, ey, er, paint);
                c.drawCircle(cx + ex, ey, er, paint);
                paint.setStyle(Paint.Style.STROKE);
                a.set(cx - rad * 0.4f, cy + rad * 0.32f, cx + rad * 0.4f, cy + rad * 0.9f);
                c.drawArc(a, 210f, 120f, false, paint);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFF4FC3F7);
                c.drawOval(new RectF(cx + ex - er * 0.6f, ey + er * 1.6f, cx + ex + er * 0.9f, ey + er * 4.2f), paint);
                break;
            default: // havalı: güneş gözlüğü
                paint.setStyle(Paint.Style.FILL);
                RectF gl = new RectF(cx - ex - rad * 0.26f, ey - rad * 0.12f, cx - ex + rad * 0.26f, ey + rad * 0.2f);
                c.drawRoundRect(gl, rad * 0.1f, rad * 0.1f, paint);
                gl.set(cx + ex - rad * 0.26f, ey - rad * 0.12f, cx + ex + rad * 0.26f, ey + rad * 0.2f);
                c.drawRoundRect(gl, rad * 0.1f, rad * 0.1f, paint);
                c.drawRect(cx - ex + rad * 0.2f, ey - rad * 0.05f, cx + ex - rad * 0.2f, ey + rad * 0.03f, paint);
                paint.setStyle(Paint.Style.STROKE);
                a.set(cx - rad * 0.5f, cy - rad * 0.28f, cx + rad * 0.5f, cy + rad * 0.55f);
                c.drawArc(a, 25f, 130f, false, paint);
                break;
        }
    }

    private void drawRightPanel(Canvas c) {
        String[] labels = {"MENÜ", "GERİ AL", "İPUCU", "KURALLAR", game.autoCollect ? "OTO TOPLA ✔" : "OTO TOPLA", "ÇIKIŞ"};
        for (int i = 0; i < B_COUNT; i++) {
            boolean enabled = true;
            if (i == B_UNDO) {
                enabled = game.canUndo();
            } else if (i == B_HINT) {
                enabled = game.canHint();
            }
            woodButton(c, btn[i], enabled, i == B_AUTO && game.autoCollect);
            centerText(c, labels[i], btn[i], enabled ? 0xFFFFF4D6 : 0xFF9C8A6A, btn[i].height() * 0.38f);
        }
        if (game.mode == Game.MODE_ONLINE) {
            for (int i = 0; i < Game.EMOTE_FACES; i++) {
                woodButton(c, emoteRect[i], true, false);
                drawFace(c, emoteRect[i].centerX(), emoteRect[i].centerY(), emoteRect[i].width() * 0.36f, i);
            }
            woodButton(c, chatBtn, game.chatEnabled, game.chatUnread > 0);
            centerText(c, game.chatEnabled ? "SOHBET" : "SOHBET KAPALI", chatBtn,
                    game.chatEnabled ? 0xFFFFF4D6 : 0xFF9C8A6A, chatBtn.height() * 0.22f);
            if (game.chatUnread > 0 && game.chatEnabled) {
                float br = chatBtn.height() * 0.17f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0xFFE5533D);
                c.drawCircle(chatBtn.right - br * 0.9f, chatBtn.top + br * 0.9f, br, paint);
                centerText(c, String.valueOf(Math.min(game.chatUnread, 9)),
                        new RectF(chatBtn.right - br * 1.9f, chatBtn.top, chatBtn.right, chatBtn.top + br * 1.8f),
                        0xFFFFFFFF, br * 1.2f);
            }
        }
    }

    private List<String> wrapText(String s, float maxW, float size) {
        List<String> lines = new ArrayList<String>();
        text.setTextSize(size);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            while (text.measureText(word) > maxW && word.length() > 1) {
                int k = word.length() - 1;
                while (k > 1 && text.measureText(word.substring(0, k)) > maxW) {
                    k--;
                }
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur.setLength(0);
                }
                lines.add(word.substring(0, k));
                word = word.substring(k);
            }
            String tryS = cur.length() == 0 ? word : cur + " " + word;
            if (text.measureText(tryS) > maxW && cur.length() > 0) {
                lines.add(cur.toString());
                cur.setLength(0);
                cur.append(word);
            } else {
                cur.setLength(0);
                cur.append(tryS);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    private void layoutChat() {
        float bw = bx1 - bx0;
        float bh = by1 - by0;
        chatPanel.set(bx0 + bw * 0.08f, by0 + bh * 0.03f, bx1 - bw * 0.08f, by1 - bh * 0.03f);
        float pw = chatPanel.width();
        float ph = chatPanel.height();
        float pad = ph * 0.035f;
        float hh = ph * 0.11f;
        chatClose.set(chatPanel.right - pad - pw * 0.15f, chatPanel.top + pad, chatPanel.right - pad,
                chatPanel.top + pad + hh);
        chatToggle.set(chatClose.left - pad - pw * 0.30f, chatClose.top, chatClose.left - pad, chatClose.bottom);
        float ih = ph * 0.12f;
        chatInput.set(chatPanel.left + pad, chatPanel.bottom - pad - ih, chatPanel.right - pad, chatPanel.bottom - pad);
        float ch = ph * 0.10f;
        float cw = (pw - pad * 2 - pad * 3) / 4f;
        for (int i = 0; i < 4; i++) {
            float x = chatPanel.left + pad + i * (cw + pad);
            chatChip[i].set(x, chatInput.top - pad - ch, x + cw, chatInput.top - pad);
        }
        chatMsgArea.set(chatPanel.left + pad, chatClose.bottom + pad,
                chatPanel.right - pad, (game.chatEnabled ? chatChip[0].top : chatInput.bottom) - pad);
    }

    private void tapChat(float x, float y) {
        layoutChat();
        if (chatClose.contains(x, y) || !chatPanel.contains(x, y)) {
            chatOpen = false;
            invalidate();
            return;
        }
        if (chatToggle.contains(x, y)) {
            if (cb != null) {
                cb.onMenuItem(TOGGLE_CHAT);
            }
            invalidate();
            return;
        }
        if (!game.chatEnabled) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            if (chatChip[i].contains(x, y)) {
                game.sendChat(Game.QUICK[i]);
                invalidate();
                return;
            }
        }
        if (chatInput.contains(x, y) && cb != null) {
            cb.onChatInput();
        }
    }

    private void drawChat(Canvas c) {
        layoutChat();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF0E0906);
        paint.setAlpha(170);
        c.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setAlpha(255);
        float ph = chatPanel.height();
        float rad = ph * 0.05f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF3C2414);
        c.drawRoundRect(chatPanel, rad, rad, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2.5f, ph * 0.008f));
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(chatPanel, rad, rad, paint);
        RectF tr = new RectF(chatPanel.left + ph * 0.035f, chatClose.top, chatToggle.left, chatClose.bottom);
        String who = game.oppName == null || game.oppName.length() == 0 ? "Rakip" : game.oppName;
        text.setTypeface(Typeface.DEFAULT_BOLD);
        leftText(c, "SOHBET · " + who, tr.left, tr.centerY() - (text.descent() + text.ascent()) / 2f + 0f,
                0xFFE9C46A, chatClose.height() * 0.50f, tr.width());
        woodButton(c, chatToggle, true, game.chatEnabled);
        centerText(c, game.chatEnabled ? "SOHBET: AÇIK" : "SOHBET: KAPALI", chatToggle, 0xFFFFF4D6,
                chatToggle.height() * 0.40f);
        woodButton(c, chatClose, true, false);
        centerText(c, "KAPAT", chatClose, 0xFFFFF4D6, chatClose.height() * 0.40f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xA0D4AF37);
        c.drawLine(chatPanel.left + ph * 0.035f, chatClose.bottom + ph * 0.017f, chatPanel.right - ph * 0.035f,
                chatClose.bottom + ph * 0.017f, paint);
        if (!game.chatEnabled) {
            centerText(c, "Sohbet kapalı: mesaj gelmez, mesaj gönderemezsin.", chatMsgArea, 0xFFC9B58A, ph * 0.05f);
            return;
        }
        // mesajlar (en yeni altta)
        float size = ph * 0.052f;
        float maxBub = chatMsgArea.width() * 0.66f;
        float lineH = size * 1.25f;
        float yb = chatMsgArea.bottom;
        c.save();
        c.clipRect(chatMsgArea);
        if (game.chatLog.isEmpty()) {
            centerText(c, "Henüz mesaj yok. Bir şey yaz!", chatMsgArea, 0xFF9C8A6A, ph * 0.045f);
        }
        for (int i = game.chatLog.size() - 1; i >= 0 && yb > chatMsgArea.top; i--) {
            String[] m = game.chatLog.get(i);
            boolean mine = "Sen".equals(m[0]);
            List<String> lines = wrapText(m[1], maxBub - size, size);
            float bwid = 0f;
            for (String ln : lines) {
                bwid = Math.max(bwid, text.measureText(ln));
            }
            bwid += size;
            float bhei = lines.size() * lineH + size * 0.5f;
            float top = yb - bhei;
            float left = mine ? chatMsgArea.right - bwid : chatMsgArea.left;
            RectF bb = new RectF(left, top, left + bwid, yb);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(mine ? 0xFFB0803C : 0xFF281A10);
            c.drawRoundRect(bb, size * 0.6f, size * 0.6f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f);
            paint.setColor(0xCCD4AF37);
            c.drawRoundRect(bb, size * 0.6f, size * 0.6f, paint);
            for (int k = 0; k < lines.size(); k++) {
                text.setTextAlign(Paint.Align.LEFT);
                text.setColor(0xFFFFF4D6);
                text.setTextSize(size);
                c.drawText(lines.get(k), bb.left + size * 0.5f, bb.top + size * 0.25f + (k + 1) * lineH - size * 0.25f, text);
            }
            yb = top - size * 0.35f;
        }
        c.restore();
        for (int i = 0; i < 4; i++) {
            woodButton(c, chatChip[i], true, false);
            centerText(c, Game.QUICK[i], chatChip[i], 0xFFFFF4D6, chatChip[i].height() * 0.40f);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF1A110A);
        c.drawRoundRect(chatInput, chatInput.height() * 0.3f, chatInput.height() * 0.3f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(0xCCD4AF37);
        c.drawRoundRect(chatInput, chatInput.height() * 0.3f, chatInput.height() * 0.3f, paint);
        leftText(c, "Mesaj yazmak için dokun…", chatInput.left + chatInput.height() * 0.4f,
                chatInput.centerY() + chatInput.height() * 0.15f, 0xFFA8946C, chatInput.height() * 0.42f,
                chatInput.width() * 0.9f);
    }

    private void drawStatus(Canvas c) {
        float rad = statusRect.height() * 0.5f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha(150);
        c.drawRoundRect(statusRect, rad, rad, paint);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xFF9C7A33);
        c.drawRoundRect(statusRect, rad, rad, paint);
        centerText(c, game.message, statusRect, 0xFFFFF4D6, statusRect.height() * 0.5f);
    }

    /** Bağlantı sorunu uyarısı: geri sayımla birlikte tahtanın üstünde. */
    private void drawNetBanner(Canvas c) {
        if (game.mode != Game.MODE_ONLINE || game.phase == Game.PH_OVER || !(game.selfAway || game.oppAway)) {
            return;
        }
        long left = Math.max(0L, (game.awayDeadline - System.currentTimeMillis()) / 1000L);
        String msg = game.selfAway ? "Bağlantın koptu, yeniden bağlanılıyor… " + left + " sn"
                : "Rakibin bağlantısı koptu, bekleniyor… " + left + " sn";
        float bw = (bx1 - bx0) * 0.7f;
        float bh = (by1 - by0) * 0.075f;
        RectF rc = new RectF((bx0 + bx1) / 2f - bw / 2f, by0 + (by1 - by0) * 0.03f, (bx0 + bx1) / 2f + bw / 2f,
                by0 + (by1 - by0) * 0.03f + bh);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xF0B34A1A);
        c.drawRoundRect(rc, bh * 0.4f, bh * 0.4f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, bh * 0.05f));
        paint.setColor(0xFFFFD54F);
        c.drawRoundRect(rc, bh * 0.4f, bh * 0.4f, paint);
        centerText(c, msg, rc, 0xFFFFF4D6, bh * 0.45f);
    }

    private static final int CF_N = 150;
    private float[] cfX;
    private float[] cfDelay;
    private float[] cfSpeed;
    private float[] cfSize;
    private float[] cfRot;
    private float[] cfSway;
    private int[] cfColor;

    /** Kazanınca düşen konfeti: maçı kazanınca bol ve uzun, aradaki oyunlarda hafif. */
    private void drawConfetti(Canvas c) {
        if (!game.overWon || game.overTime <= 0) {
            return;
        }
        boolean big = game.matchOver;
        long dur = big ? 6500 : 3600;
        long age = System.currentTimeMillis() - game.overTime;
        if (age < 0 || age > dur) {
            return;
        }
        if (cfX == null) {
            java.util.Random rr = new java.util.Random(2024L);
            cfX = new float[CF_N];
            cfDelay = new float[CF_N];
            cfSpeed = new float[CF_N];
            cfSize = new float[CF_N];
            cfRot = new float[CF_N];
            cfSway = new float[CF_N];
            cfColor = new int[CF_N];
            int[] pal = {0xFFE9C46A, 0xFFFFFFFF, 0xFFE5533D, 0xFF6BCB77, 0xFF4D96FF, 0xFFFFB703};
            for (int i = 0; i < CF_N; i++) {
                cfX[i] = rr.nextFloat();
                cfDelay[i] = rr.nextFloat() * 1.6f;
                cfSpeed[i] = 0.30f + rr.nextFloat() * 0.28f;
                cfSize[i] = 0.7f + rr.nextFloat() * 0.7f;
                cfRot[i] = 200f + rr.nextFloat() * 400f;
                cfSway[i] = 2f + rr.nextFloat() * 3f;
                cfColor[i] = pal[rr.nextInt(pal.length)];
            }
        }
        float areaW = bx1 - bx0;
        float areaH = by1 - by0;
        int n = big ? CF_N : CF_N / 2;
        float unit = Math.max(9f, areaH * 0.036f);
        for (int i = 0; i < n; i++) {
            float t = age / 1000f - cfDelay[i] * (big ? 1.4f : 0.8f);
            if (t < 0) {
                continue;
            }
            float y = by0 - unit + t * cfSpeed[i] * areaH * 1.25f;
            if (y > by1 + unit) {
                continue;
            }
            float x = bx0 + cfX[i] * areaW + (float) Math.sin(t * cfSway[i]) * unit * 1.6f;
            float ang = t * cfRot[i];
            float flip = Math.abs((float) Math.cos(Math.toRadians(ang)));
            float w = unit * cfSize[i];
            float fade = Math.min(1f, (dur - age) / 700f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(cfColor[i]);
            paint.setAlpha((int) (255 * Math.max(0f, fade)));
            c.save();
            c.rotate(ang * 0.35f, x, y);
            float hw = w * Math.max(flip, 0.22f) / 2f;
            c.drawRect(x - hw, y - w * 0.28f, x + hw, y + w * 0.28f, paint);
            c.restore();
        }
        paint.setAlpha(255);
        handler.removeCallbacks(animTick);
        handler.postDelayed(animTick, 16);
    }

    private void drawOver(Canvas c) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha(150);
        c.drawRoundRect(new RectF(bx0, by0, bx1, by1), geo.fr * 0.8f, geo.fr * 0.8f, paint);
        paint.setAlpha(255);
        float cx = (bx0 + bx1) / 2f;
        float cy = (by0 + by1) / 2f;
        float bw = (bx1 - bx0) * 0.62f;
        float bh = (by1 - by0) * 0.66f;
        RectF card = new RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f);
        paint.setShader(new LinearGradient(0, card.top, 0, card.bottom, new int[] {0xFF4A2F1B, 0xFF24150B},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        c.drawRoundRect(card, bh * 0.05f, bh * 0.05f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, bh * 0.008f));
        paint.setColor(0xFFD4AF37);
        c.drawRoundRect(card, bh * 0.05f, bh * 0.05f, paint);

        RectF t = new RectF(card.left, card.top + bh * 0.05f, card.right, card.top + bh * 0.25f);
        centerText(c, game.overTitle, t, 0xFFFFD54F, bh * 0.13f);
        t.set(card.left, card.top + bh * 0.26f, card.right, card.top + bh * 0.38f);
        centerText(c, game.overSub, t, 0xFFFFF4D6, bh * 0.07f);
        if (!game.overExtra.isEmpty()) {
            String[] lines = game.overExtra.split("\n");
            float ly = card.top + bh * 0.42f;
            for (String ln : lines) {
                t.set(card.left, ly, card.right, ly + bh * 0.075f);
                centerText(c, ln, t, 0xFFC9B58A, bh * 0.055f);
                ly += bh * 0.08f;
            }
        }
        boolean online = game.mode == Game.MODE_ONLINE;
        overNew.set(card.left + bw * 0.06f, card.bottom - bh * 0.22f, card.left + bw * 0.52f, card.bottom - bh * 0.06f);
        overMenu.set(card.left + bw * 0.56f, card.bottom - bh * 0.22f, card.right - bw * 0.06f, card.bottom - bh * 0.06f);
        woodButton(c, overNew, !(online && game.myRematch), true);
        String again = game.target > 1 && !game.matchOver ? "Sonraki Oyun" : "RÖVANŞ";
        centerText(c, online && game.myRematch ? "Bekleniyor…" : again, overNew, 0xFFFFF4D6,
                overNew.height() * 0.40f);
        woodButton(c, overMenu, true, false);
        centerText(c, "Menü", overMenu, 0xFFFFF4D6, overMenu.height() * 0.40f);
    }
}
