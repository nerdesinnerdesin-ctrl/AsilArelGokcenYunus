package com.hapistavla.oyun.tavla;

import com.hapistavla.oyun.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Oyun akışı: sıra, zar, hamle, bilgisayar, çevrimiçi senkronizasyon. Android'den bağımsızdır;
 * çizim ve dokunma işini TahtaView yapar, zamanlayıcıları ve iş parçacığı geçişini Host sağlar.
 */
public final class Game {
    // evreler
    public static final int PH_ROLL = 0;     // zar atılmayı bekliyor
    public static final int PH_ROLLING = 1;  // zar dönüyor
    public static final int PH_MOVE = 2;     // insan hamle yapıyor
    public static final int PH_AI = 3;       // bilgisayar oynuyor
    public static final int PH_PASS = 4;     // sıra geçiyor
    public static final int PH_OVER = 5;     // oyun bitti
    public static final int PH_WAIT = 6;     // çevrimiçi: rakip oynuyor

    // modlar
    public static final int MODE_AI = 0;
    public static final int MODE_TWO = 1;
    public static final int MODE_ONLINE = 2;

    // Host.onlineEvent türleri
    public static final int EV_ROOM = 0;     // a = oda kodu
    public static final int EV_STATUS = 1;   // a = metin
    public static final int EV_MATCHED = 2;  // a = rakip adı
    public static final int EV_ERROR = 3;    // a = metin
    public static final int EV_LEFT = 4;     // a = metin
    public static final int EV_INVITE = 5;   // a = "kod|ad", b = oda
    public static final int EV_EMOTE = 6;    // a = mesaj, b = kim
    public static final int EV_CHAT = 8;     // a = metin, b = kim
    public static final int EV_TABLES = 7;   // açık masalar değişti: tables alanına bak

    // ses türleri
    public static final int SND_ROLL = 0;
    public static final int SND_MOVE = 1;
    public static final int SND_PIN = 2;
    public static final int SND_WIN = 3;
    public static final int SND_LOSE = 4;

    /** Hızlı mesajlar: 0-5 yüz ifadeleri (tahtada çizilir), 6-9 kısa yazılar. Sıra iki tarafta aynı olmalıdır. */
    public static final String[] EMOTES = {"Gülümseme", "Kahkaha", "Şaşkın", "Kızgın", "Üzgün", "Havalı",
            "İyi oyun!", "Güzel hamle!", "Acele et!", "Tekrar oynayalım?"};
    public static final int EMOTE_FACES = 6;

    public interface Host {
        /** Arayüz iş parçacığında, gecikmeli çalıştırır; cancelTimers() ile iptal edilebilir. */
        public void postTimer(Runnable r, long delayMs);

        public void cancelTimers();

        /** Herhangi bir iş parçacığından arayüz iş parçacığına geçirir (iptal edilemez). */
        public void postUi(Runnable r);

        public void changed();

        public void onlineEvent(int type, String a, String b);

        public void gameOver(Result r);

        public void sound(int kind);

        public void haptic();
    }

    /** Bir oyunun sonucu (profil ilerlemesi için). */
    public static final class Result {
        public int mode;
        public int aiLevel;
        public int winner;      // WHITE / BLACK / TIE
        public int points;
        public int myColor;
        public boolean iWon;
        public boolean tie;
        public boolean mars;
        public int variant;      // Board.PLAKOTO / Board.CLASSIC
        public boolean mother;   // kaybeden anne taşı yüzünden kaybetti
        public int pins;
        public int combos;
        public int maxCombo;
        public String oppCode = "";
        public String oppName = "";
    }

    /** Seçili taş için gösterilen hedef. */
    public static final class Dest {
        public final int to;
        public final int label;
        public final boolean pin;
        public final boolean combo;

        public Dest(int to, int label, boolean pin, boolean combo) {
            this.to = to;
            this.label = label;
            this.pin = pin;
            this.combo = combo;
        }
    }

    private static final class Snapshot {
        public final Board board;
        public final List<Integer> remaining;
        public final int lastFrom;
        public final int lastTo;
        public final String eventMsg;
        public final int pins;
        public final int combos;
        public final int maxCombo;

        Snapshot(Board board, List<Integer> remaining, int lastFrom, int lastTo, String eventMsg,
                 int pins, int combos, int maxCombo) {
            this.board = board;
            this.remaining = remaining;
            this.lastFrom = lastFrom;
            this.lastTo = lastTo;
            this.eventMsg = eventMsg;
            this.pins = pins;
            this.combos = combos;
            this.maxCombo = maxCombo;
        }
    }

    private final Host host;
    private final Random rnd = new Random();
    private Online online;

    // herkese açık durum (çizim için)
    /** Hangi oyun: Board.PLAKOTO (hapis tavla) ya da Board.CLASSIC (klasik tavla). */
    public int variant = Board.PLAKOTO;
    public Board board = Board.initial();
    public int turn = Board.WHITE;
    public int mode = MODE_AI;
    public int aiLevel = Ai.NORMAL;
    public int phase = PH_ROLL;
    public final int[] dice = new int[2];
    public boolean diceShown = false;
    public int diceOwner = Board.WHITE;
    public List<Integer> remaining = new ArrayList<Integer>();
    public List<Board.Move> allowed = new ArrayList<Board.Move>();
    public int selected = 0;
    public List<Dest> dests = new ArrayList<Dest>();
    /** Taş kayma animasyonu: parçalar {nereden, nereye, renk, gecikme ms, tür(0 hamle, 1 vurulan taş)}. */
    public static final int ANIM_MS = 260;
    public final List<int[]> anim = new ArrayList<int[]>();
    public long animT0 = 0;
    public int animTotal = 0;
    public static final int ROLL_MS = 720;
    /** Atılan zar çift mi (atış sürerken kaç zar gösterileceğini belirler). */
    public boolean rollDouble = false;
    public boolean overWon = false;
    public long overTime = 0;
    public long rollStart = 0;
    public int lastFrom = 0;
    public int lastTo = 0;
    public final int[] score = new int[2];
    public String message = "";
    public String overTitle = "";
    public String overSub = "";
    public String overExtra = "";
    public int myColor = Board.WHITE;
    /** Maç hedefi: bir tarafın bu puana ulaşması maçı bitirir (1, 3 ya da 5). */
    public int target = 1;
    public boolean matchOver = false;
    /** Otomatik taş toplama: tek seçenek varsa dokunmadan toplar, tek dokunuşla toplar. */
    public boolean autoCollect = false;
    public boolean myRematch = false;
    public boolean oppRematch = false;
    public String oppName = "Rakip";
    public String oppCode = "";
    public Board.Move hintMove = null;

    // çevrimiçi süre ve bağlantı yönetimi
    /** Bir elin süresi (zar atma dahil). Testler küçültebilir. */
    public static long TURN_MS = 60000;
    /** Menü/pencere açıkken (çevrimdışı) süre işlemez; arayüz günceller. */
    public boolean pausedUi = false;
    /** Uygulama arka plandayken (çevrimdışı oyunlarda) sıra süresi işlemez. */
    public static boolean appPaused = false;
    public static final int MAX_TIMEOUTS = 3;
    /** Hamle sayacı: her zar, hamle ve el sonunda artar; bağlantı dönünce hangi tarafın ileride olduğunu belirler. */
    public int seq = 0;
    public long turnDeadline = 0;
    public boolean selfAway = false;
    public boolean oppAway = false;
    public long awayDeadline = 0;
    private boolean syncing = false;
    private long syncUntil = 0;
    private boolean timeoutPlay = false;
    private int myTimeouts = 0;
    private int tickGen = 0;
    private long lastTick = 0;

    // iç durum
    private final int[] finalDice = new int[2];
    private int rollCount = 0;
    private boolean rollDone = false;
    private final List<Snapshot> history = new ArrayList<Snapshot>();
    private String eventMsg = "";
    private List<Board.Move> aiPlan = new ArrayList<Board.Move>();
    private int aiIndex = 0;
    private int nextStarter = 0;
    private int sessionPins = 0;
    private int sessionCombos = 0;
    private int sessionMaxCombo = 0;
    private final ArrayDeque<String[]> oppQueue = new ArrayDeque<String[]>();

    private final Runnable rollStep = new Runnable() {
        @Override
        public void run() {
            if (phase != PH_ROLLING) {
                return;
            }
            if (rollCount < 9) {
                dice[0] = 1 + rnd.nextInt(6);
                dice[1] = 1 + rnd.nextInt(6);
                rollCount++;
                host.changed();
                host.postTimer(this, 40 + rollCount * 8);
            } else {
                dice[0] = finalDice[0];
                dice[1] = finalDice[1];
                afterRoll();
            }
        }
    };

    private final Runnable startRollRunnable = new Runnable() {
        @Override
        public void run() {
            if (phase == PH_ROLL && isAiTurn()) {
                beginRollAnim(1 + rnd.nextInt(6), 1 + rnd.nextInt(6));
            }
        }
    };

    private final Runnable endTurnRunnable = new Runnable() {
        @Override
        public void run() {
            endTurn();
        }
    };

    private final Runnable aiStepRunnable = new Runnable() {
        @Override
        public void run() {
            if (phase != PH_AI || aiIndex >= aiPlan.size()) {
                return;
            }
            Board.Move m = aiPlan.get(aiIndex++);
            playMove(m, false);
            if (board.winner() != Board.NONE) {
                finishGame();
                return;
            }
            if (aiIndex < aiPlan.size()) {
                host.postTimer(this, 650);
            } else {
                phase = PH_PASS;
                host.postTimer(endTurnRunnable, 700);
            }
            host.changed();
        }
    };

    public Game(Host host) {
        this.host = host;
        nextStarter = rnd.nextInt(2);
        newGame();
    }

    // ------------------------------------------------------------------
    // Bilgi
    // ------------------------------------------------------------------

    public boolean isRemoteTurn() {
        return mode == MODE_ONLINE && turn != myColor;
    }

    public boolean isAiTurn() {
        return mode == MODE_AI && turn == Board.BLACK;
    }

    public boolean isHumanTurn() {
        return !isRemoteTurn() && !isAiTurn();
    }

    /** Tahtayı hangi oyuncunun gözünden çizeriz (ev altta sağda). */
    public int viewColor() {
        return mode == MODE_ONLINE ? myColor : Board.WHITE;
    }

    public static String colorName(int p) {
        return p == Board.WHITE ? "Beyaz" : "Siyah";
    }

    public String playerName(int p) {
        if (mode == MODE_AI) {
            return p == Board.WHITE ? "Sen" : "Bilgisayar";
        }
        if (mode == MODE_ONLINE) {
            return p == myColor ? "Sen" : oppName;
        }
        return colorName(p);
    }

    public boolean canRoll() {
        return phase == PH_ROLL && isHumanTurn() && !netBlocked();
    }

    private boolean netBlocked() {
        return mode == MODE_ONLINE && (selfAway || syncing);
    }

    public boolean canUndo() {
        return !history.isEmpty() && isHumanTurn() && mode != MODE_ONLINE && (phase == PH_MOVE || phase == PH_PASS);
    }

    public boolean canHint() {
        return phase == PH_MOVE && isHumanTurn() && !allowed.isEmpty();
    }

    private boolean isMine(int p) {
        if (mode == MODE_AI) {
            return p == Board.WHITE;
        }
        return mode == MODE_ONLINE && p == myColor;
    }

    private int[] remArray() {
        int[] r = new int[remaining.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = remaining.get(i);
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Oyun akışı
    // ------------------------------------------------------------------

    public void newGame() {
        host.cancelTimers();
        tickGen++;
        seq = 0;
        timeoutPlay = false;
        syncing = false;
        board = Board.initial(variant);
        anim.clear();
        animTotal = 0;
        history.clear();
        oppQueue.clear();
        remaining = new ArrayList<Integer>();
        allowed = new ArrayList<Board.Move>();
        dests = new ArrayList<Dest>();
        aiPlan = new ArrayList<Board.Move>();
        selected = 0;
        hintMove = null;
        lastFrom = 0;
        lastTo = 0;
        diceShown = false;
        eventMsg = "";
        overTitle = "";
        overSub = "";
        overExtra = "";
        myRematch = false;
        oppRematch = false;
        sessionPins = 0;
        sessionCombos = 0;
        sessionMaxCombo = 0;
        turn = nextStarter;
        startTurn();
    }

    private void startTurn() {
        timeoutPlay = false;
        if (timed()) {
            turnDeadline = System.currentTimeMillis() + TURN_MS;
            startTick();
        }
        phase = PH_ROLL;
        rollDone = false;
        selected = 0;
        hintMove = null;
        dests = new ArrayList<Dest>();
        remaining = new ArrayList<Integer>();
        allowed = new ArrayList<Board.Move>();
        String prefix = eventMsg.isEmpty() ? "" : eventMsg + " ";
        eventMsg = "";
        if (isRemoteTurn()) {
            phase = PH_WAIT;
            message = prefix + oppName + " zar atıyor…";
            host.changed();
            processOpp();
            return;
        }
        if (isAiTurn()) {
            message = prefix + "Bilgisayar zar atıyor…";
            host.postTimer(startRollRunnable, 800);
        } else if (mode == MODE_ONLINE) {
            message = prefix + "Sıra sende: zar atmak için dokun.";
        } else {
            message = prefix + colorName(turn) + ": zar atmak için dokun.";
        }
        host.changed();
    }

    /** İnsan oyuncunun zar atması. */
    public void tapRoll() {
        if (!canRoll()) {
            return;
        }
        if (!timeoutPlay) {
            myTimeouts = 0;
        }
        int d1 = 1 + rnd.nextInt(6);
        int d2 = 1 + rnd.nextInt(6);
        if (mode == MODE_ONLINE && online != null) {
            online.sendRoll(d1, d2);
        }
        beginRollAnim(d1, d2);
    }

    private void beginRollAnim(int d1, int d2) {
        seq++;
        finalDice[0] = d1;
        finalDice[1] = d2;
        phase = PH_ROLLING;
        diceShown = true;
        diceOwner = turn;
        rollCount = 0;
        selected = 0;
        hintMove = null;
        dests = new ArrayList<Dest>();
        message = "Zar atılıyor…";
        rollStart = System.currentTimeMillis();
        rollDouble = d1 == d2;
        host.sound(SND_ROLL);
        host.postTimer(rollStep, 0);
        host.changed();
    }

    private void afterRoll() {
        remaining = new ArrayList<Integer>();
        if (dice[0] == dice[1]) {
            for (int i = 0; i < 4; i++) {
                remaining.add(dice[0]);
            }
        } else {
            remaining.add(dice[0]);
            remaining.add(dice[1]);
        }
        history.clear();
        selected = 0;
        rollDone = true;
        if (isRemoteTurn()) {
            phase = PH_WAIT;
            message = oppName + " oynuyor…";
            host.changed();
            processOpp();
            return;
        }
        if (isAiTurn()) {
            runAi();
            return;
        }
        computeAllowed();
        if (allowed.isEmpty()) {
            message = playerName(turn) + " oynayamıyor, sıra geçiyor.";
            if (mode != MODE_ONLINE) {
                message = colorName(turn) + " oynayamıyor, sıra geçiyor.";
            }
            phase = PH_PASS;
            host.postTimer(endTurnRunnable, 1500);
        } else {
            phase = PH_MOVE;
            message = moveHint();
            maybeAutoCollect();
            maybeAutoPlay();
        }
        host.changed();
    }

    private void runAi() {
        aiPlan = Ai.chooseTurn(board, turn, dice[0], dice[1], aiLevel);
        aiIndex = 0;
        if (aiPlan.isEmpty()) {
            message = "Bilgisayar oynayamıyor, sıra sende.";
            phase = PH_PASS;
            host.postTimer(endTurnRunnable, 1500);
        } else {
            message = "Bilgisayar oynuyor…";
            phase = PH_AI;
            host.postTimer(aiStepRunnable, 700);
        }
        host.changed();
    }

    private String moveHint() {
        String prefix = eventMsg.isEmpty() ? "" : eventMsg + " ";
        if (mode == MODE_ONLINE) {
            return prefix + "Sıra sende: bir taşa, sonra gideceği yere dokun.";
        }
        return prefix + colorName(turn) + ": bir taşa, sonra gideceği yere dokun.";
    }

    private void computeAllowed() {
        int[] rem = remArray();
        allowed = rem.length == 0 ? new ArrayList<Board.Move>() : Board.allowedMoves(board, turn, rem);
    }

    /** Hamleyi tahtaya işler (insan, bilgisayar ve uzaktaki rakip için ortak). */
    private void playMove(Board.Move m, boolean publish) {
        int opp = 1 - turn;
        boolean pins = m.to != Board.OFF && board.pinned[m.to] != opp && board.cnt[opp][m.to] == 1;
        board.apply(turn, m);
        seq++;
        long nowMs = System.currentTimeMillis();
        if (anim.isEmpty() || nowMs >= animT0 + animTotal) {
            anim.clear();
            animT0 = nowMs;
            animTotal = 0;
        }
        anim.add(new int[] {m.from, m.to, turn, animTotal, 0});
        animTotal += ANIM_MS;
        if (pins && board.isClassic()) {
            anim.add(new int[] {m.to, Board.BAR, opp, animTotal, 1});
            animTotal += ANIM_MS;
        }
        lastFrom = m.from;
        lastTo = m.to;
        remaining.remove(Integer.valueOf(m.die));
        eventMsg = "";
        if (pins && board.isClassic()) {
            eventMsg = colorName(turn) + " bir taşı vurdu!";
            host.sound(SND_PIN);
            host.haptic();
            if (isMine(turn)) {
                sessionPins++;
            }
        } else if (pins) {
            eventMsg = colorName(turn) + " bir taşı hapsetti!";
            host.sound(SND_PIN);
            host.haptic();
            if (isMine(turn)) {
                sessionPins++;
            }
        } else {
            host.sound(SND_MOVE);
        }
        if (publish && mode == MODE_ONLINE && turn == myColor && online != null) {
            online.sendMove(m.from, m.to, m.die);
        }
    }

    private void endTurn() {
        if (phase == PH_OVER) {
            return;
        }
        board.endTurn(turn);
        if (mode == MODE_ONLINE && turn == myColor && online != null) {
            online.sendEnd(board.encode());
        }
        turn = 1 - turn;
        seq++;
        history.clear();
        startTurn();
    }

    private void finishGame() {
        host.cancelTimers();
        tickGen++;
        oppQueue.clear();
        phase = PH_OVER;
        selected = 0;
        hintMove = null;
        dests = new ArrayList<Dest>();
        allowed = new ArrayList<Board.Move>();
        int w = board.winner();
        Result r = new Result();
        r.mode = mode;
        r.variant = variant;
        r.aiLevel = aiLevel;
        r.winner = w;
        r.myColor = viewColor();
        r.pins = sessionPins;
        r.combos = sessionCombos;
        r.maxCombo = sessionMaxCombo;
        r.oppCode = oppCode;
        r.oppName = oppName;
        if (w == Board.TIE) {
            overTitle = "Beraberlik";
            overSub = "İki anne taş da hapiste.";
            nextStarter = 1 - nextStarter;
            r.tie = true;
        } else {
            int pts = board.winPoints();
            int loser = 1 - w;
            boolean motherLoss = (loser == Board.WHITE && board.pinned[24] == Board.WHITE)
                    || (loser == Board.BLACK && board.pinned[1] == Board.BLACK);
            score[w] += pts;
            matchOver = score[w] >= target;
            r.points = pts;
            r.mars = pts == 2;
            r.mother = motherLoss;
            r.iWon = isMine(w);
            if (mode == MODE_AI) {
                overTitle = (w == Board.WHITE) ? "Kazandın!" : "Bilgisayar kazandı";
            } else if (mode == MODE_ONLINE) {
                overTitle = (w == myColor) ? "Kazandın!" : oppName + " kazandı";
            } else {
                overTitle = colorName(w) + " kazandı!";
            }
            if (board.isClassic()) {
                overSub = pts == 2 ? "MARS! Rakip hiç taş toplayamadı. (+2 puan)" : "+1 puan";
            } else if (motherLoss) {
                overSub = "Anne taş hapsedildi. MARS! (+2 puan)";
            } else if (pts == 2) {
                overSub = "MARS! (+2 puan)";
            } else {
                overSub = "+1 puan";
            }
            nextStarter = w;
            if (target > 1) {
                String sc = score[0] + " - " + score[1];
                if (matchOver) {
                    overTitle = (mode == MODE_ONLINE ? (w == myColor ? "MAÇI KAZANDIN!" : oppName + " maçı aldı")
                            : (w == Board.WHITE ? "MAÇI KAZANDIN!" : "Bilgisayar maçı aldı"));
                    overSub = "Maç sonucu " + sc + "  (" + target + " puan)";
                } else {
                    overSub += "   •   Skor " + sc + "  (hedef " + target + ")";
                }
            }
        }
        overWon = !r.tie && (isMine(w) || mode == MODE_TWO);
        overTime = System.currentTimeMillis();
        message = overTitle + " " + overSub;
        host.sound(r.tie ? SND_MOVE : (isMine(w) || mode == MODE_TWO ? SND_WIN : SND_LOSE));
        host.changed();
        host.gameOver(r);
        host.changed();
    }

    // ------------------------------------------------------------------
    // İnsan hamleleri
    // ------------------------------------------------------------------

    private boolean hasMoveFrom(int from) {
        for (Board.Move m : allowed) {
            if (m.from == from) {
                return true;
            }
        }
        return false;
    }

    private Dest destFor(int to) {
        for (Dest d : dests) {
            if (d.to == to) {
                return d;
            }
        }
        return null;
    }

    private void computeDests(int from) {
        dests = new ArrayList<Dest>();
        if (from == 0) {
            return;
        }
        int o = 1 - turn;
        List<Board.Move> sorted = new ArrayList<Board.Move>(allowed);
        Collections.sort(sorted, new Comparator<Board.Move>() {
            @Override
            public int compare(Board.Move a, Board.Move b) {
                return a.die - b.die;
            }
        });
        Set<Integer> seen = new HashSet<Integer>();
        for (Board.Move m : sorted) {
            if (m.from == from && seen.add(m.to)) {
                boolean pin = m.to != Board.OFF && board.pinned[m.to] != o && board.cnt[o][m.to] == 1;
                dests.add(new Dest(m.to, m.die, pin, false));
            }
        }
        for (Board.Combo cb : Board.comboTargets(board, turn, remArray(), from)) {
            if (seen.add(cb.to)) {
                dests.add(new Dest(cb.to, cb.total(), cb.pins > 0, true));
            }
        }
    }

    /** Tahtada (mantıksal) noktaya dokunuldu. */
    public void tapPoint(int pt) {
        if (phase != PH_MOVE || !isHumanTurn() || netBlocked()) {
            return;
        }
        if (selected != 0 && pt != selected && destFor(pt) != null) {
            executeMove(selected, pt);
            return;
        }
        if (pt == selected) {
            selected = 0;
        } else if (hasMoveFrom(pt)) {
            selected = pt;
        } else {
            selected = 0;
        }
        hintMove = null;
        computeDests(selected);
        if (autoCollect && selected != 0 && dests.size() == 1 && dests.get(0).to == Board.OFF) {
            executeMove(selected, Board.OFF); // tek seçenek taş toplamak: tek dokunuşla topla
            return;
        }
        host.changed();
    }

    /** Otomatik toplama açıkken, oynanabilecek tek hamle bir taş toplamaksa onu kendisi oynar. */
    private void maybeAutoCollect() {
        if (!autoCollect || phase != PH_MOVE || !isHumanTurn() || allowed.isEmpty()) {
            return;
        }
        int from = allowed.get(0).from;
        for (Board.Move m : allowed) {
            if (m.to != Board.OFF || m.from != from) {
                return;
            }
        }
        final int f = from;
        host.postTimer(new Runnable() {
            @Override
            public void run() {
                if (autoCollect && phase == PH_MOVE && isHumanTurn() && hasMoveFrom(f) && selected == 0) {
                    executeMove(f, Board.OFF);
                }
            }
        }, 550);
    }

    /** Taş toplama alanına dokunuldu. */
    public void tapTray() {
        if (phase == PH_MOVE && isHumanTurn() && !netBlocked() && selected != 0 && destFor(Board.OFF) != null) {
            executeMove(selected, Board.OFF);
        }
    }

    private List<Board.Move> routeFor(int from, int to) {
        Board.Move single = null;
        for (Board.Move m : allowed) {
            if (m.from == from && m.to == to && (single == null || m.die < single.die)) {
                single = m;
            }
        }
        if (single != null) {
            List<Board.Move> r = new ArrayList<Board.Move>();
            r.add(single);
            return r;
        }
        for (Board.Combo cb : Board.comboTargets(board, turn, remArray(), from)) {
            if (cb.to == to) {
                return cb.moves;
            }
        }
        return null;
    }

    private void executeMove(int from, int to) {
        List<Board.Move> route = routeFor(from, to);
        if (route == null) {
            return;
        }
        if (!timeoutPlay && isHumanTurn()) {
            myTimeouts = 0;
        }
        history.add(new Snapshot(board.copy(), new ArrayList<Integer>(remaining), lastFrom, lastTo, eventMsg,
                sessionPins, sessionCombos, sessionMaxCombo));
        selected = 0;
        hintMove = null;
        dests = new ArrayList<Dest>();
        int total = 0;
        for (Board.Move m : route) {
            playMove(m, true);
            total += m.die;
        }
        if (route.size() >= 2 && isMine(turn)) {
            sessionCombos++;
            sessionMaxCombo = Math.max(sessionMaxCombo, total);
        }
        if (board.winner() != Board.NONE) {
            finishGame();
            return;
        }
        computeAllowed();
        if (allowed.isEmpty()) {
            message = (eventMsg.isEmpty() ? "" : eventMsg + " ") + (remaining.isEmpty() ? "" : "Başka oynanabilir hamle yok.");
            phase = PH_PASS;
            host.postTimer(endTurnRunnable, 900);
        } else {
            message = moveHint();
            maybeAutoCollect();
            maybeAutoPlay();
        }
        host.changed();
    }

    public void undo() {
        if (!canUndo()) {
            return;
        }
        host.cancelTimers();
        Snapshot s = history.remove(history.size() - 1);
        anim.clear();
        animTotal = 0;
        board = s.board;
        remaining = new ArrayList<Integer>(s.remaining);
        lastFrom = s.lastFrom;
        lastTo = s.lastTo;
        eventMsg = s.eventMsg;
        sessionPins = s.pins;
        sessionCombos = s.combos;
        sessionMaxCombo = s.maxCombo;
        selected = 0;
        hintMove = null;
        dests = new ArrayList<Dest>();
        phase = PH_MOVE;
        computeAllowed();
        message = moveHint();
        host.changed();
    }

    /** İpucu: en iyi görünen taşı seçer ve hedefini gösterir. */
    public void requestHint() {
        if (!canHint()) {
            return;
        }
        Board.Move m = Ai.hint(board, turn, remArray());
        if (m == null) {
            return;
        }
        hintMove = m;
        selected = m.from;
        computeDests(selected);
        message = "İpucu: yeşil işaretli yeri dene.";
        host.changed();
    }

    // ------------------------------------------------------------------
    // Modlar, yeni oyun, tekrar oyna
    // ------------------------------------------------------------------

    /** "Yeni Oyun" / "Tekrar Oyna" düğmesi. */
    public void newGameRequest() {
        if (mode == MODE_ONLINE) {
            if (phase == PH_OVER && !myRematch) {
                myRematch = true;
                if (online != null) {
                    online.sendRematch();
                }
                message = "Rakibin onayı bekleniyor…";
                if (oppRematch) {
                    startRematch();
                    return;
                }
                host.changed();
            }
            return;
        }
        if (matchOver) {
            resetMatch();
        }
        nextStarter = rnd.nextInt(2);
        newGame();
    }

    private void resetMatch() {
        score[0] = 0;
        score[1] = 0;
        matchOver = false;
    }

    private void startRematch() {
        myRematch = false;
        oppRematch = false;
        if (matchOver) {
            resetMatch();
        }
        newGame();
    }

    public void setOnline(Online o) {
        this.online = o;
        if (o != null) {
            o.variant = variant;
        }
    }

    /** Bilgisayara karşı ya da aynı telefonda iki kişi moduna geç (skorlar sıfırlanır). */
    public void setMode(int newMode, int level) {
        if (mode == MODE_ONLINE && online != null) {
            online.leave();
        }
        mode = newMode;
        aiLevel = level;
        score[0] = 0;
        score[1] = 0;
        matchOver = false;
        myColor = Board.WHITE;
        oppName = "Rakip";
        oppCode = "";
        nextStarter = rnd.nextInt(2);
        newGame();
    }

    /** Çevrimiçi oyundan çık, bilgisayara karşı yeni oyuna dön. */
    public void exitOnline() {
        if (online != null && mode == MODE_ONLINE) {
            online.leave();
        }
        setMode(MODE_AI, aiLevel);
    }

    /** Çevrimiçi oyundan bilerek çık: oyun sürüyorsa kaybedersin, rakip kazanır. */
    public void quitOnline() {
        if (mode == MODE_ONLINE && online != null && phase != PH_OVER) {
            Result r = forfeitResult(1 - myColor);
            host.gameOver(r);
            online.forfeit("q");
        }
        exitOnline();
    }

    private Result forfeitResult(int winner) {
        Result r = new Result();
        r.mode = mode;
        r.variant = variant;
        r.aiLevel = aiLevel;
        r.winner = winner;
        r.myColor = viewColor();
        r.points = Math.max(1, target);
        r.iWon = isMine(winner);
        r.pins = sessionPins;
        r.combos = sessionCombos;
        r.maxCombo = sessionMaxCombo;
        r.oppCode = oppCode;
        r.oppName = oppName;
        return r;
    }

    /** Oyun, süre ya da bağlantı yüzünden hükmen biter (kazanan tüm maçı alır). */
    private void forfeitGame(int winner, String msg) {
        host.cancelTimers();
        tickGen++;
        oppQueue.clear();
        boolean wasOver = phase == PH_OVER;
        phase = PH_OVER;
        selected = 0;
        hintMove = null;
        dests = new ArrayList<Dest>();
        allowed = new ArrayList<Board.Move>();
        if (!wasOver) {
            host.gameOver(forfeitResult(winner));
        }
        message = msg;
        host.changed();
        host.onlineEvent(EV_LEFT, msg + (overExtra.isEmpty() ? "" : "\n" + overExtra), "");
    }

    // ---- süre sayacı -------------------------------------------------

    private void startTick() {
        tickGen++;
        lastTick = System.currentTimeMillis();
        postTick(tickGen);
    }

    private void postTick(final int gen) {
        host.postTimer(new Runnable() {
            @Override
            public void run() {
                turnTick(gen);
            }
        }, 500);
    }

    private void turnTick(int gen) {
        if (gen != tickGen || !timed() || phase == PH_OVER) {
            return;
        }
        long now = System.currentTimeMillis();
        long dt = now - lastTick;
        lastTick = now;
        if (syncing && now > syncUntil) {
            syncing = false;
        }
        if (selfAway || oppAway || syncing || ((pausedUi || appPaused) && mode != MODE_ONLINE)) {
            turnDeadline += dt; // bağlantı sorunu ya da menü açıkken süre işlemez
        } else if (now > turnDeadline && isHumanTurn() && !timeoutPlay && (phase == PH_ROLL || phase == PH_MOVE)) {
            onTurnTimeout();
            if (gen != tickGen) {
                return;
            }
        }
        host.changed();
        postTick(gen);
    }

    /** Sıra süresi: çevrimiçi, bilgisayara karşı ve iki kişilik oyunda geçerli. */
    private boolean timed() {
        return mode == MODE_ONLINE || mode == MODE_AI || mode == MODE_TWO;
    }

    private void onTurnTimeout() {
        myTimeouts++;
        if (mode == MODE_ONLINE && myTimeouts >= MAX_TIMEOUTS) {
            if (online != null) {
                online.forfeit("t");
            }
            forfeitGame(1 - myColor, "Üst üste " + MAX_TIMEOUTS + " kez süre doldu. Oyunu kaybettin.");
            return;
        }
        timeoutPlay = true;
        selected = 0;
        hintMove = null;
        dests = new ArrayList<Dest>();
        message = "Süre doldu: hamle senin yerine oynanıyor…";
        host.changed();
        if (phase == PH_ROLL) {
            tapRoll();
        } else {
            maybeAutoPlay();
        }
    }

    /** Süre dolduysa kalan zarları sırayla en iyi görünen hamlelerle oynar. */
    private void maybeAutoPlay() {
        if (!timeoutPlay || phase != PH_MOVE || !isHumanTurn()) {
            return;
        }
        host.postTimer(new Runnable() {
            @Override
            public void run() {
                if (!timeoutPlay || phase != PH_MOVE || !isHumanTurn() || netBlocked()) {
                    return;
                }
                Board.Move m = Ai.hint(board, turn, remArray());
                if (m == null && !allowed.isEmpty()) {
                    m = allowed.get(0);
                }
                if (m != null) {
                    executeMove(m.from, m.to);
                }
            }
        }, 650);
    }

    // ---- bağlantı kopması: durum eşitleme ------------------------------

    private String statePayload() {
        List<Integer> rem = remaining;
        int d0 = dice[0];
        int d1 = dice[1];
        boolean rd = rollDone;
        if (phase == PH_ROLLING) {
            d0 = finalDice[0];
            d1 = finalDice[1];
            rd = true;
            rem = new ArrayList<Integer>();
            if (d0 == d1) {
                for (int i = 0; i < 4; i++) {
                    rem.add(d0);
                }
            } else {
                rem.add(d0);
                rem.add(d1);
            }
        }
        StringBuilder rs = new StringBuilder();
        for (int v : rem) {
            if (rs.length() > 0) {
                rs.append('.');
            }
            rs.append(v);
        }
        return seq + "," + board.encode() + "," + turn + "," + d0 + "," + d1 + ","
                + (rs.length() == 0 ? "-" : rs.toString()) + "," + (rd ? 1 : 0) + "," + score[0] + "," + score[1]
                + "," + nextStarter + "," + (matchOver ? 1 : 0);
    }

    private void applyState(String payload) {
        try {
            String[] p = payload.split(",", -1);
            if (p.length < 11 || mode != MODE_ONLINE) {
                return;
            }
            int sq = Integer.parseInt(p[0]);
            Board nb = Board.decode(p[1]);
            if (nb == null || nb.variant != variant || sq <= seq) {
                return;
            }
            host.cancelTimers();
            oppQueue.clear();
            history.clear();
            selected = 0;
            hintMove = null;
            dests = new ArrayList<Dest>();
            allowed = new ArrayList<Board.Move>();
            seq = sq;
            board = nb;
            turn = Integer.parseInt(p[2]) == 1 ? Board.BLACK : Board.WHITE;
            dice[0] = Integer.parseInt(p[3]);
            dice[1] = Integer.parseInt(p[4]);
            remaining = new ArrayList<Integer>();
            if (!"-".equals(p[5])) {
                for (String v : p[5].split("\\.")) {
                    remaining.add(Integer.parseInt(v));
                }
            }
            rollDone = "1".equals(p[6]);
            score[0] = Integer.parseInt(p[7]);
            score[1] = Integer.parseInt(p[8]);
            nextStarter = Integer.parseInt(p[9]);
            matchOver = "1".equals(p[10]);
            diceShown = dice[0] > 0;
            diceOwner = turn;
            int w = board.winner();
            if (w != Board.NONE) {
                if (w != Board.TIE) {
                    score[w] -= board.winPoints();
                }
                finishGame();
                return;
            }
            timeoutPlay = false;
            if (isRemoteTurn()) {
                phase = PH_WAIT;
                message = oppName + (rollDone ? " oynuyor…" : " zar atıyor…");
            } else if (!rollDone) {
                phase = PH_ROLL;
                message = "Sıra sende: zar atmak için dokun.";
            } else {
                computeAllowed();
                if (allowed.isEmpty()) {
                    phase = PH_PASS;
                    message = "Oynanabilir hamle yok, sıra geçiyor.";
                    host.postTimer(endTurnRunnable, 1200);
                } else {
                    phase = PH_MOVE;
                    message = moveHint();
                }
            }
            turnDeadline = System.currentTimeMillis() + TURN_MS;
            startTick();
            host.changed();
        } catch (RuntimeException e) {
            // bozuk durum mesajını yok say
        }
    }

    /** Yazılı sohbet: açık mı, mesaj geçmişi (bu çevrimiçi maç boyunca), okunmamış sayısı. */
    public boolean chatEnabled = true;
    public final List<String[]> chatLog = new ArrayList<String[]>();
    public int chatUnread = 0;
    public static final String[] QUICK = {"İyi oyun!", "Güzel hamle!", "Acele et!", "Tekrar?"};

    private static final String[] BAD_STEMS = {"orospu", "oruspu", "yarrak", "yarak", "amcık", "amcik", "ananı", "anani",
        "piç", "sikik", "sikeyim", "siktir", "götveren", "gotveren", "pezevenk", "kahpe", "ibne", "puşt", "fuck", "bitch"};
    private static final String[] BAD_WORDS = {"amk", "aq", "sik", "göt", "oç", "amq"};

    /** Mesajı kısaltır, '|' ve satır sonlarını temizler, küfürlü sözcükleri *** yapar. */
    public static String cleanChat(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.replace('|', '/').replace('\n', ' ').replace('\r', ' ').trim();
        if (t.length() > 100) {
            t = t.substring(0, 100);
        }
        StringBuilder out = new StringBuilder();
        StringBuilder tok = new StringBuilder();
        for (int i = 0; i <= t.length(); i++) {
            char ch = i < t.length() ? t.charAt(i) : ' ';
            if (Character.isLetter(ch)) {
                tok.append(ch);
            } else {
                if (tok.length() > 0) {
                    String w = tok.toString();
                    String lw = w.toLowerCase(new java.util.Locale("tr"));
                    boolean bad = false;
                    for (String s : BAD_WORDS) {
                        if (lw.equals(s)) {
                            bad = true;
                        }
                    }
                    for (String s : BAD_STEMS) {
                        if (lw.contains(s)) {
                            bad = true;
                        }
                    }
                    out.append(bad ? "***" : w);
                    tok.setLength(0);
                }
                if (i < t.length()) {
                    out.append(ch);
                }
            }
        }
        return out.toString().trim();
    }

    private void addChat(String who, String text) {
        chatLog.add(new String[] {who, text});
        while (chatLog.size() > 60) {
            chatLog.remove(0);
        }
    }

    public void sendChat(String raw) {
        if (mode != MODE_ONLINE || online == null || !chatEnabled) {
            return;
        }
        String t = cleanChat(raw);
        if (t.length() == 0) {
            return;
        }
        online.sendChat(t);
        addChat("Sen", t);
        host.changed();
    }

    public void sendEmote(int id) {
        if (mode == MODE_ONLINE && online != null && id >= 0 && id < EMOTES.length) {
            online.sendEmote(id);
            host.onlineEvent(EV_EMOTE, EMOTES[id], "Sen");
        }
    }

    /** Oyun türünü değiştirir (hapis / klasik). Ardından setMode çağrılmalıdır. */
    public void setVariant(int v) {
        variant = v == Board.CLASSIC ? Board.CLASSIC : Board.PLAKOTO;
        if (online != null) {
            online.variant = variant;
        }
    }

    private void startOnlineMatch(int color, int starter, String code, String name) {
        if (online != null) {
            variant = online.variant;
            target = online.target;
        }
        matchOver = false;
        chatLog.clear();
        chatUnread = 0;
        mode = MODE_ONLINE;
        myColor = color;
        oppCode = code;
        oppName = (name == null || name.isEmpty()) ? "Rakip" : name;
        score[0] = 0;
        score[1] = 0;
        nextStarter = starter;
        newGame();
        host.onlineEvent(EV_MATCHED, oppName, "");
    }

    // ------------------------------------------------------------------
    // Çevrimiçi: gelen olaylar (arayüz iş parçacığında işlenir)
    // ------------------------------------------------------------------

    /** Son bilinen açık masalar (arayüz iş parçacığında güncellenir). */
    public List<Online.Table> tables = new ArrayList<Online.Table>();

    public Online.Listener onlineListener() {
        return new Online.Listener() {
            @Override
            public void onTables(final List<Online.Table> list) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        tables = list;
                        host.onlineEvent(EV_TABLES, "", "");
                    }
                });
            }

            @Override
            public void onStatus(final String text) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        host.onlineEvent(EV_STATUS, text, "");
                    }
                });
            }

            @Override
            public void onRoomCreated(final String code) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        host.onlineEvent(EV_ROOM, code, "");
                    }
                });
            }

            @Override
            public void onMatched(final int color, final int starter, final String code, final String name) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        startOnlineMatch(color, starter, code, name);
                    }
                });
            }

            @Override
            public void onRoll(final int d1, final int d2) {
                queueOpp(new String[] {"roll", String.valueOf(d1), String.valueOf(d2)});
            }

            @Override
            public void onMove(final int from, final int to, final int die) {
                queueOpp(new String[] {"move", String.valueOf(from), String.valueOf(to), String.valueOf(die)});
            }

            @Override
            public void onEnd(final String code) {
                queueOpp(new String[] {"end", code});
            }

            @Override
            public void onRematch() {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode != MODE_ONLINE) {
                            return;
                        }
                        oppRematch = true;
                        if (myRematch && phase == PH_OVER) {
                            startRematch();
                        } else if (phase == PH_OVER) {
                            message = oppName + " tekrar oynamak istiyor.";
                            host.changed();
                        }
                    }
                });
            }

            @Override
            public void onChat(final String text) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode == MODE_ONLINE && chatEnabled) {
                            String t = cleanChat(text);
                            if (t.length() > 0) {
                                addChat(oppName, t);
                                chatUnread++;
                                host.onlineEvent(EV_CHAT, t, oppName);
                            }
                        }
                    }
                });
            }

            @Override
            public void onEmote(final int id) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode == MODE_ONLINE && id >= 0 && id < EMOTES.length) {
                            host.onlineEvent(EV_EMOTE, EMOTES[id], oppName);
                        }
                    }
                });
            }

            @Override
            public void onOpponentLeft(final String text) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode == MODE_ONLINE) {
                            host.onlineEvent(EV_LEFT, text, "");
                        }
                    }
                });
            }

            @Override
            public void onError(final String text) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        host.onlineEvent(EV_ERROR, text, "");
                    }
                });
            }

            @Override
            public void onLink(final boolean self, final boolean away, final int secondsLeft) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode != MODE_ONLINE) {
                            return;
                        }
                        if (self) {
                            selfAway = away;
                        } else {
                            oppAway = away;
                        }
                        awayDeadline = System.currentTimeMillis() + secondsLeft * 1000L;
                        host.changed();
                    }
                });
            }

            @Override
            public void onReconnected() {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode == MODE_ONLINE && online != null && phase != PH_OVER) {
                            syncing = true;
                            syncUntil = System.currentTimeMillis() + 6000;
                            online.sendSync(seq);
                        }
                    }
                });
            }

            @Override
            public void onSync(final int theirSeq) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode != MODE_ONLINE || online == null) {
                            return;
                        }
                        if (seq >= theirSeq) {
                            online.sendState(statePayload());
                        } else {
                            online.sendSync(seq);
                        }
                    }
                });
            }

            @Override
            public void onState(final String payload) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        applyState(payload);
                        syncing = false;
                    }
                });
            }

            @Override
            public void onOpponentForfeit(final String text) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode != MODE_ONLINE) {
                            return;
                        }
                        String m = "q".equals(text) ? "Rakip oyundan çıktı. Oyunu kazandın!"
                                : "Rakip üst üste " + MAX_TIMEOUTS + " kez süreyi kaçırdı. Oyunu kazandın!";
                        if (phase == PH_OVER) {
                            host.onlineEvent(EV_LEFT, "Rakip oyundan ayrıldı.", "");
                        } else {
                            forfeitGame(myColor, m);
                        }
                    }
                });
            }

            @Override
            public void onOpponentTimeout(final String text) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode != MODE_ONLINE) {
                            return;
                        }
                        if (phase == PH_OVER) {
                            host.onlineEvent(EV_LEFT, text, "");
                        } else {
                            forfeitGame(myColor, text + " Oyunu kazandın!");
                        }
                    }
                });
            }

            @Override
            public void onSelfTimeout() {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        if (mode != MODE_ONLINE) {
                            return;
                        }
                        if (phase == PH_OVER) {
                            host.onlineEvent(EV_LEFT, "Bağlantın koptu.", "");
                        } else {
                            forfeitGame(1 - myColor, "Bağlantın 1 dakika içinde geri gelmedi. Oyunu kaybettin.");
                        }
                    }
                });
            }

            @Override
            public void onInvite(final String fromCode, final String fromName, final String room) {
                host.postUi(new Runnable() {
                    @Override
                    public void run() {
                        host.onlineEvent(EV_INVITE, fromCode + "|" + fromName, room);
                    }
                });
            }
        };
    }

    private void queueOpp(final String[] ev) {
        host.postUi(new Runnable() {
            @Override
            public void run() {
                if (mode != MODE_ONLINE || syncing) {
                    return;
                }
                oppQueue.add(ev);
                processOpp();
            }
        });
    }

    private boolean oppMoveAllowed(int from, int to, int die) {
        for (Board.Move m : Board.allowedMoves(board, turn, remArray())) {
            if (m.from == from && m.to == to && m.die == die) {
                return true;
            }
        }
        return false;
    }

    private void processOpp() {
        while (mode == MODE_ONLINE && !oppQueue.isEmpty()) {
            if (phase == PH_ROLLING) {
                return; // animasyon bitince tekrar çağrılır
            }
            String[] ev = oppQueue.peek();
            String t = ev[0];
            boolean waiting = phase == PH_WAIT && isRemoteTurn();
            if (t.equals("roll")) {
                oppQueue.poll();
                if (waiting && !rollDone) {
                    beginRollAnim(Integer.parseInt(ev[1]), Integer.parseInt(ev[2]));
                    return;
                }
            } else if (t.equals("move")) {
                oppQueue.poll();
                if (waiting && rollDone) {
                    int from = Integer.parseInt(ev[1]);
                    int to = Integer.parseInt(ev[2]);
                    int die = Integer.parseInt(ev[3]);
                    if (remaining.contains(die) && oppMoveAllowed(from, to, die)) {
                        playMove(new Board.Move(from, to, die), false);
                        if (board.winner() != Board.NONE) {
                            finishGame();
                            return;
                        }
                        host.changed();
                    }
                }
            } else if (t.equals("end")) {
                oppQueue.poll();
                if (waiting && rollDone) {
                    Board nb = Board.decode(ev[1]);
                    if (nb != null && nb.variant == variant) {
                        board = nb;
                    } else {
                        board.endTurn(turn);
                    }
                    if (board.winner() != Board.NONE) {
                        finishGame();
                        return;
                    }
                    turn = 1 - turn;
                    seq++;
                    history.clear();
                    startTurn();
                    return;
                }
            } else {
                oppQueue.poll();
            }
        }
    }
}
