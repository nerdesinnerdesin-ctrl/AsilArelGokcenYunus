package com.hapistavla.oyun.salon;

import com.hapistavla.oyun.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Salon oyunlarının (Batak, Okey, 101 Okey, King) çevrimiçi katmanı. Android'den bağımsızdır.
 *
 * Mimari: masayı kuran oyuncu (koltuk 0) "ev sahibi"dir; oyunun tek gerçek kopyasını o yürütür, botları
 * o oynatır. Her oyuncunun telefonunda aynı oyun motoru aynı tohumla (seed) çalışır; ev sahibi her hamleyi
 * sıra numarasıyla ("act|sıra|komut") yayınlar, herkes aynı sırayla uygular. Misafirler hamlelerini
 * "req" ile ev sahibine yollar. Boş koltuklara bot oturur. Kopan oyuncunun yerine bot geçer.
 *
 * Konular: hapistavla/v1/s/ODA (oda), hapistavla/v1/st1..st4 (oyuna göre açık masalar).
 * Dinleyici metotları ağ iş parçacığından çağrılır; arayüz kendi iş parçacığına geçirmelidir.
 */
public final class SalonNet {
    public interface Listener {
        public void onStatus(String text);

        /** Masa kuruldu (ev sahibi). */
        public void onCreated(String room);

        /** Bir masaya oturuldu (misafir): bekleme odasındasın. */
        public void onJoined(String room);

        public void onTables(List<Table> tables);

        /** Bekleme odasındaki oyuncu listesi değişti. */
        public void onRoster();

        /** Oyun başlıyor (misafirler için; ev sahibi startGame'i kendisi çağırır). */
        public void onStart(int game, int value, long seed);

        /** Misafir: sıradaki komut (sırayla, tekrarsız). */
        public void onAct(int seq, String cmd);

        /** Ev sahibi: bir misafirden komut isteği geldi. */
        public void onReq(int seat, String cmd);

        public void onChat(int seat, String name, String text);

        public void onEmote(int seat, String name, int id);

        /** Ev sahibi: bir oyuncu oyunu bıraktı ya da koptu; koltuğunu bot alır. */
        public void onSeatFree(int seat, String name);

        /** Ev sahibi: kopan oyuncu geri geldi (koltuğunu geri aldı). */
        public void onSeatBack(int seat);

        /** Ev sahibi: masa doldu ya da hızlı eşleşme süresi doldu; oyunu başlat. */
        public void onAutoStart();

        /** Oturum bitti (masa sahibi ayrıldı vb.). */
        public void onClosed(String text);

        public void onError(String text);

        /** Bağlantı durumu: up=false kopuk, yeniden deneniyor. */
        public void onLink(boolean up);
    }

    /** Herkese açık, oyuncu bekleyen bir masa. */
    public static final class Table {
        public final String room;
        public final String name;
        public final String host;
        public final int level;
        public final int game;
        public final int value;
        public final int humans;
        public long seen;

        public Table(String room, String name, String host, int level, int game, int value, int humans) {
            this.room = room;
            this.name = name;
            this.host = host;
            this.level = level;
            this.game = game;
            this.value = value;
            this.humans = humans;
        }
    }

    /** Bekleme odasının anlık görüntüsü (arayüz iş parçacığı için kopya). */
    public static final class Roster {
        public String room = "";
        public boolean host;
        public int mySeat;
        public int game;
        public int value;
        public int humans;
        public final String[] codes = new String[4];
        public final String[] names = new String[4];
        public final int[] levels = new int[4];
    }

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int LOBBY = 2;
    public static final int PLAYING = 3;

    private static final String PREFIX = "hapistavla/v1/";
    public static long LOBBY_DEAD_MS = 16000;
    public static long PLAY_DEAD_MS = 35000;
    public static long HB_MS = 3000;
    /** Hızlı eşleşmede ikinci oyuncu geldikten sonra beklenecek süre. */
    public static long QUICK_WAIT_MS = 20000;

    private final String myCode;
    private volatile String myName;
    private volatile int myLevel = 1;
    private final Listener l;
    private final Mqtt.Server[] servers;
    private final Random rnd = new Random();
    private final ScheduledThreadPoolExecutor exec;

    private Mqtt mqtt;
    private volatile int phase = IDLE;
    private boolean hosting = false;
    private String room = "";
    private int game;
    private int value;
    private boolean pub;
    private boolean quickAuto;
    private int mySeat = 0;
    private final String[] codes = new String[4];
    private final String[] names = new String[4];
    private final int[] levels = new int[4];
    private final long[] lastSeen = new long[4];
    private long seed;
    private long startedAt;
    private long secondAt;
    private final List<String> log = new ArrayList<String>();
    private int nextSeq = 0;
    private final Map<Integer, String> pending = new HashMap<Integer, String>();
    private long hostSeen;
    private long lastSyncAt;
    private int session = 0;
    private boolean tablesSub = false;
    private int watchGame = 0;
    private boolean linkUp = true;
    private int joinTries;
    private final Map<String, Table> tables = new LinkedHashMap<String, Table>();

    private ScheduledFuture<?> hbTask;
    private ScheduledFuture<?> joinTask;
    private ScheduledFuture<?> purgeTask;
    private ScheduledFuture<?> quickTask;
    private ScheduledFuture<?> reconTask;

    public SalonNet(String myCode, String myName, Listener l, Mqtt.Server[] servers) {
        this.myCode = myCode;
        this.myName = clean(myName);
        this.l = l;
        this.servers = servers;
        this.exec = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "salonnet");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public void setName(String n) {
        myName = clean(n);
    }

    public void setLevel(int lv) {
        myLevel = Math.max(1, lv);
    }

    public int getPhase() {
        return phase;
    }

    public boolean isHost() {
        return hosting;
    }

    public String myCode() {
        return myCode;
    }

    public synchronized int mySeat() {
        return mySeat;
    }

    public synchronized Roster roster() {
        Roster r = new Roster();
        r.room = room;
        r.host = hosting;
        r.mySeat = mySeat;
        r.game = game;
        r.value = value;
        for (int i = 0; i < 4; i++) {
            r.codes[i] = codes[i];
            r.names[i] = names[i];
            r.levels[i] = levels[i];
            if (codes[i] != null) {
                r.humans++;
            }
        }
        return r;
    }

    // ------------------------------------------------------------------
    // Genel işlemler
    // ------------------------------------------------------------------

    /** Masa kurar. pub=true ise "açık masalar" listesinde görünür. */
    public void host(final int g, final int v, final boolean isPublic) {
        run(new Runnable() {
            @Override
            public void run() {
                doHost(g, v, isPublic, false);
            }
        });
    }

    /** Odaya kodla katılır. */
    public void join(final String code, final int g) {
        run(new Runnable() {
            @Override
            public void run() {
                doJoin(code.trim().toUpperCase(), g);
            }
        });
    }

    /** Hızlı eşleşme: açık masa varsa oturur, yoksa açık masa kurup bekler. */
    public void quick(final int g, final int v) {
        run(new Runnable() {
            @Override
            public void run() {
                resetSession();
                phase = CONNECTING;
                l.onStatus("Masa aranıyor…");
                if (!connect()) {
                    fail("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
                    return;
                }
                game = g;
                value = v;
                watchGame = g;
                if (!mqtt.subscribe(tablesTopic(g))) {
                    fail("Masa listesi alınamadı.");
                    return;
                }
                tablesSub = true;
                final int s = session;
                quickTask = exec.schedule(guard(new Runnable() {
                    @Override
                    public void run() {
                        if (s != session || phase != CONNECTING) {
                            return;
                        }
                        Table best = null;
                        for (Table t : tables.values()) {
                            if (t.humans < 4 && (best == null || t.humans > best.humans)) {
                                best = t;
                            }
                        }
                        if (best != null) {
                            doJoin(best.room, g);
                        } else {
                            doHost(g, v, true, true);
                        }
                    }
                }), 3500, TimeUnit.MILLISECONDS);
            }
        });
    }

    /** Açık masaları izlemeye başlar (kendi masan açıksa da çalışır). */
    public void watchTables(final int g) {
        run(new Runnable() {
            @Override
            public void run() {
                if (phase != IDLE && !(hosting && phase == LOBBY)) {
                    return;
                }
                if (!ensureConnected()) {
                    l.onError("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
                    return;
                }
                if (tablesSub && watchGame != g) {
                    mqtt.unsubscribe(tablesTopic(watchGame));
                    tablesSub = false;
                    tables.clear();
                }
                watchGame = g;
                if (!tablesSub) {
                    if (!mqtt.subscribe(tablesTopic(g))) {
                        l.onError("Masa listesi alınamadı.");
                        return;
                    }
                    tablesSub = true;
                }
                startPurge();
                notifyTables();
            }
        });
    }

    public void unwatchTables() {
        run(new Runnable() {
            @Override
            public void run() {
                stopWatching();
                if (phase == IDLE && mqtt != null) {
                    mqtt.close();
                    mqtt = null;
                }
            }
        });
    }

    /** Masadan / odadan ayrılır (herkese haber verir). */
    public void leave() {
        run(new Runnable() {
            @Override
            public void run() {
                if (phase == LOBBY || phase == PLAYING) {
                    if (hosting) {
                        send(roomTopic(), "hostleft|" + myCode);
                    } else {
                        send(roomTopic(), "leave|" + mySeat + "|" + myCode);
                    }
                }
                resetSession();
            }
        });
    }

    /** Ev sahibi: oyunu başlatır. Tohumu döndürür (arayüz iş parçacığında çağrılır). */
    public long startGame() {
        final long sd = (rnd.nextLong() & 0x7FFFFFFFFFFFL);
        seed = sd;
        run(new Runnable() {
            @Override
            public void run() {
                if (!hosting || phase != LOBBY) {
                    return;
                }
                phase = PLAYING;
                startedAt = System.currentTimeMillis();
                log.clear();
                if (pub) {
                    send(tablesTopic(game), "tclose|" + room);
                }
                send(roomTopic(), startMsg());
            }
        });
        return sd;
    }

    private synchronized String startMsg() {
        StringBuilder sb = new StringBuilder("start|").append(game).append('|').append(value).append('|').append(seed);
        for (int i = 0; i < 4; i++) {
            sb.append('|');
            if (codes[i] == null) {
                sb.append('-');
            } else {
                sb.append(names[i]).append('~').append(levels[i]).append('~').append(codes[i]);
            }
        }
        return sb.toString();
    }

    /** Ev sahibi: bir komutu sıra numarasıyla yayınlar. */
    public void sendAct(final String cmd) {
        run(new Runnable() {
            @Override
            public void run() {
                if (!hosting || phase != PLAYING) {
                    return;
                }
                int seq = log.size();
                log.add(cmd);
                send(roomTopic(), "act|" + seq + "|" + cmd);
            }
        });
    }

    /** Misafir: ev sahibinden komut ister. */
    public void sendReq(final int seat, final String cmd) {
        run(new Runnable() {
            @Override
            public void run() {
                if (hosting || phase != PLAYING) {
                    return;
                }
                send(roomTopic(), "req|" + seat + "|" + myCode + "|" + cmd);
            }
        });
    }

    public void sendChat(final String text) {
        run(new Runnable() {
            @Override
            public void run() {
                if (phase == LOBBY || phase == PLAYING) {
                    send(roomTopic(), "chat|" + mySeat + "|" + myCode + "|" + text.replace('|', '/'));
                }
            }
        });
    }

    public void sendEmote(final int id) {
        run(new Runnable() {
            @Override
            public void run() {
                if (phase == LOBBY || phase == PLAYING) {
                    send(roomTopic(), "emote|" + mySeat + "|" + myCode + "|" + id);
                }
            }
        });
    }

    /** Bir arkadaşı bu odaya davet eder (arkadaşın kişisel posta kutusuna). */
    public void inviteFriend(final String friendCode) {
        run(new Runnable() {
            @Override
            public void run() {
                if (hosting && phase == LOBBY && mqtt != null) {
                    send(PREFIX + "u/" + friendCode, "finv|" + myCode + "|" + myName + "|" + room + "|S" + game);
                }
            }
        });
    }

    /** Tamamen kapatır (uygulama kapanırken). */
    public void shutdown() {
        run(new Runnable() {
            @Override
            public void run() {
                if (phase == LOBBY || phase == PLAYING) {
                    if (hosting) {
                        send(roomTopic(), "hostleft|" + myCode);
                    } else {
                        send(roomTopic(), "leave|" + mySeat + "|" + myCode);
                    }
                }
                resetSession();
                exec.shutdown();
            }
        });
    }

    // ------------------------------------------------------------------
    // İç işleyiş (exec iş parçacığında)
    // ------------------------------------------------------------------

    private void run(final Runnable r) {
        try {
            exec.execute(guard(r));
        } catch (RuntimeException e) {
            // kapatılmış
        }
    }

    private Runnable guard(final Runnable r) {
        return new Runnable() {
            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable t) {
                    // tek hata zamanlayıcıları durdurmasın
                }
            }
        };
    }

    public static String clean(String s) {
        if (s == null) {
            return "Oyuncu";
        }
        String c = s.replace('|', ' ').replace('~', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (c.length() > 16) {
            c = c.substring(0, 16);
        }
        return c.isEmpty() ? "Oyuncu" : c;
    }

    private static String tablesTopic(int g) {
        return PREFIX + "st" + g;
    }

    private String roomTopic() {
        return PREFIX + "s/" + room;
    }

    private void send(String topic, String payload) {
        if (mqtt != null) {
            mqtt.publish(topic, payload);
        }
    }

    private void fail(String text) {
        resetSession();
        l.onError(text);
    }

    private boolean connect() {
        if (mqtt != null) {
            mqtt.close();
            mqtt = null;
        }
        tablesSub = false;
        return ensureConnected();
    }

    private boolean ensureConnected() {
        if (mqtt != null && mqtt.isConnected()) {
            return true;
        }
        mqtt = new Mqtt(myCode + "s", new Mqtt.Listener() {
            @Override
            public void onMessage(final String topic, final String payload) {
                run(new Runnable() {
                    @Override
                    public void run() {
                        handle(topic, payload);
                    }
                });
            }

            @Override
            public void onClosed(String reason) {
                run(new Runnable() {
                    @Override
                    public void run() {
                        connectionLost();
                    }
                });
            }
        });
        tablesSub = false;
        return mqtt.connect(servers, 7000);
    }

    private void cancelTasks() {
        ScheduledFuture<?>[] all = {hbTask, joinTask, quickTask, reconTask};
        for (ScheduledFuture<?> f : all) {
            if (f != null) {
                f.cancel(false);
            }
        }
        hbTask = null;
        joinTask = null;
        quickTask = null;
        reconTask = null;
    }

    private void stopWatching() {
        if (purgeTask != null) {
            purgeTask.cancel(false);
            purgeTask = null;
        }
        if (tablesSub && mqtt != null && mqtt.isConnected()) {
            mqtt.unsubscribe(tablesTopic(watchGame));
        }
        tablesSub = false;
        tables.clear();
    }

    private synchronized void clearRoster() {
        for (int i = 0; i < 4; i++) {
            codes[i] = null;
            names[i] = null;
            levels[i] = 0;
            lastSeen[i] = 0;
        }
        mySeat = 0;
    }

    private void resetSession() {
        session++;
        cancelTasks();
        if (hosting && pub && phase == LOBBY && mqtt != null && mqtt.isConnected() && !room.isEmpty()) {
            send(tablesTopic(game), "tclose|" + room);
        }
        stopWatching();
        if (mqtt != null && mqtt.isConnected() && !room.isEmpty()) {
            mqtt.unsubscribe(roomTopic());
        }
        if (mqtt != null) {
            mqtt.close();
            mqtt = null;
        }
        room = "";
        hosting = false;
        pub = false;
        quickAuto = false;
        phase = IDLE;
        log.clear();
        pending.clear();
        nextSeq = 0;
        linkUp = true;
        secondAt = 0;
        clearRoster();
    }

    private void doHost(int g, int v, boolean isPublic, boolean quickMode) {
        resetSession();
        phase = CONNECTING;
        l.onStatus("Sunucuya bağlanılıyor…");
        if (!connect()) {
            fail("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
            return;
        }
        String rm = Online.randomCode(5, rnd);
        room = rm;
        if (!mqtt.subscribe(roomTopic())) {
            fail("Masa kurulamadı, tekrar dene.");
            return;
        }
        game = g;
        value = v;
        pub = isPublic;
        quickAuto = quickMode;
        hosting = true;
        synchronized (this) {
            mySeat = 0;
            codes[0] = myCode;
            names[0] = myName;
            levels[0] = myLevel;
        }
        lastSeen[0] = System.currentTimeMillis();
        phase = LOBBY;
        startTimers();
        l.onCreated(rm);
        l.onRoster();
        if (pub) {
            advertise();
        }
    }

    private void doJoin(String code, int g) {
        resetSession();
        phase = CONNECTING;
        l.onStatus("Sunucuya bağlanılıyor…");
        if (!connect()) {
            fail("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
            return;
        }
        room = code;
        game = g;
        if (room.length() < 4 || !mqtt.subscribe(roomTopic())) {
            fail("Masaya katılınamadı.");
            return;
        }
        hosting = false;
        joinTries = 0;
        hostSeen = System.currentTimeMillis();
        l.onStatus("Masaya oturuluyor…");
        final int s = session;
        joinTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
            @Override
            public void run() {
                if (s != session || phase != CONNECTING) {
                    return;
                }
                if (joinTries >= 6) {
                    fail("Masa bulunamadı ya da dolu. Kodu doğru yazdığından ve masanın açık olduğundan emin ol.");
                    return;
                }
                joinTries++;
                send(roomTopic(), "join|" + myCode + "|" + myName + "|" + myLevel + "|" + game);
            }
        }), 0, 2000, TimeUnit.MILLISECONDS);
    }

    private void startTimers() {
        final int s = session;
        if (hbTask != null) {
            hbTask.cancel(false);
        }
        hbTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
            @Override
            public void run() {
                if (s == session) {
                    tick();
                }
            }
        }), 1000, HB_MS, TimeUnit.MILLISECONDS);
    }

    private int humanCount() {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            if (codes[i] != null) {
                n++;
            }
        }
        return n;
    }

    private void advertise() {
        if (hosting && pub && phase == LOBBY) {
            send(tablesTopic(game), "st|" + room + "|" + myName + "|" + myLevel + "|" + myCode + "|" + game + "|"
                    + value + "|" + humanCount());
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (phase != LOBBY && phase != PLAYING) {
            return;
        }
        if (hosting) {
            send(roomTopic(), "hbh|" + log.size() + "|" + (phase == PLAYING ? 1 : 0));
            lastSeen[0] = now;
            if (phase == LOBBY) {
                advertise();
                broadcastRoster();
                int n = humanCount();
                if (n >= 2 && secondAt == 0) {
                    secondAt = now;
                } else if (n < 2) {
                    secondAt = 0;
                }
                if (n == 4 || (quickAuto && n >= 2 && now - secondAt > QUICK_WAIT_MS)) {
                    l.onAutoStart();
                }
            } else if (now - startedAt < 12000) {
                send(roomTopic(), startMsg());
            }
            long dead = phase == LOBBY ? LOBBY_DEAD_MS : PLAY_DEAD_MS;
            for (int i = 1; i < 4; i++) {
                if (codes[i] != null && lastSeen[i] > 0 && now - lastSeen[i] > dead) {
                    dropSeat(i, phase == PLAYING);
                }
            }
        } else {
            send(roomTopic(), "hb|" + mySeat + "|" + myCode);
            if (phase == LOBBY || phase == PLAYING) {
                if (now - hostSeen > PLAY_DEAD_MS) {
                    closeWith("Masa sahibine ulaşılamıyor.");
                }
            }
        }
    }

    private void closeWith(String text) {
        resetSession();
        l.onClosed(text);
    }

    private void dropSeat(int seat, boolean keepName) {
        String nm;
        synchronized (this) {
            nm = names[seat] == null ? "Oyuncu" : names[seat];
            if (!keepName || phase == LOBBY) {
                codes[seat] = null;
                names[seat] = null;
                levels[seat] = 0;
            }
            lastSeen[seat] = 0;
        }
        if (phase == PLAYING) {
            // oyun sürüyor: koltuk bota geçer (kod saklı kalırsa oyuncu geri dönebilir)
            l.onSeatFree(seat, nm);
        }
        broadcastRoster();
        l.onRoster();
    }

    private synchronized String rosterMsg() {
        StringBuilder sb = new StringBuilder("roster|").append(game).append('|').append(value);
        for (int i = 0; i < 4; i++) {
            sb.append('|');
            if (codes[i] == null) {
                sb.append('-');
            } else {
                sb.append(names[i]).append('~').append(levels[i]).append('~').append(codes[i]);
            }
        }
        return sb.toString();
    }

    private void broadcastRoster() {
        if (hosting) {
            send(roomTopic(), rosterMsg());
        }
    }

    private void connectionLost() {
        if (phase == IDLE) {
            return;
        }
        if (phase == CONNECTING) {
            fail("Bağlantı koptu.");
            return;
        }
        tablesSub = false;
        if (linkUp) {
            linkUp = false;
            l.onLink(false);
        }
        scheduleReconnect(2500);
    }

    private void scheduleReconnect(long delay) {
        if (reconTask != null) {
            reconTask.cancel(false);
        }
        final int s = session;
        reconTask = exec.schedule(guard(new Runnable() {
            @Override
            public void run() {
                if (s != session || (phase != LOBBY && phase != PLAYING)) {
                    return;
                }
                if (mqtt != null) {
                    mqtt.close();
                    mqtt = null;
                }
                if (ensureConnected() && mqtt.subscribe(roomTopic())) {
                    linkUp = true;
                    l.onLink(true);
                    hostSeen = System.currentTimeMillis();
                    if (!hosting) {
                        send(roomTopic(), "join|" + myCode + "|" + myName + "|" + myLevel + "|" + game);
                    }
                } else {
                    scheduleReconnect(4000);
                }
            }
        }), delay, TimeUnit.MILLISECONDS);
    }

    private void startPurge() {
        if (purgeTask != null) {
            return;
        }
        purgeTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                boolean ch = false;
                Iterator<Table> it = tables.values().iterator();
                while (it.hasNext()) {
                    if (now - it.next().seen > 11000) {
                        it.remove();
                        ch = true;
                    }
                }
                if (ch) {
                    notifyTables();
                }
            }
        }), 2, 2, TimeUnit.SECONDS);
    }

    private void notifyTables() {
        List<Table> out = new ArrayList<Table>();
        for (Table t : tables.values()) {
            if (t.humans < 4 && !t.host.equals(myCode)) {
                out.add(t);
            }
        }
        l.onTables(out);
    }

    // ------------------------------------------------------------------
    // Gelen mesajlar
    // ------------------------------------------------------------------

    private void handle(String topic, String payload) {
        String[] f = payload.split("\\|", -1);
        if (f.length < 2) {
            return;
        }
        if (tablesSub && topic.equals(tablesTopic(watchGame))) {
            handleTables(f);
        } else if (!room.isEmpty() && topic.equals(roomTopic())) {
            handleRoom(f);
        }
    }

    private static int num(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void handleTables(String[] f) {
        if (f[0].equals("st") && f.length >= 8) {
            String rm = f[1];
            if (rm.length() < 4 || f[4].equals(myCode)) {
                return;
            }
            Table t = new Table(rm, clean(f[2]), f[4], Math.max(1, Math.min(99, num(f[3], 1))), num(f[5], 0),
                    num(f[6], 0), Math.max(1, Math.min(4, num(f[7], 1))));
            if (t.game != watchGame) {
                return;
            }
            Table old = tables.get(rm);
            t.seen = System.currentTimeMillis();
            if (old == null && tables.size() >= 40) {
                return;
            }
            tables.put(rm, t);
            if (old == null || !old.name.equals(t.name) || old.level != t.level || old.value != t.value
                    || old.humans != t.humans) {
                notifyTables();
            }
        } else if (f[0].equals("tclose")) {
            if (tables.remove(f[1]) != null) {
                notifyTables();
            }
        }
    }

    private void handleRoom(String[] f) {
        String type = f[0];
        if (hosting) {
            hostHandle(type, f);
        } else {
            guestHandle(type, f);
        }
        if (type.equals("chat") && f.length >= 4 && !f[2].equals(myCode)) {
            int seat = num(f[1], 0);
            StringBuilder sb = new StringBuilder(f[3]);
            for (int i = 4; i < f.length; i++) {
                sb.append('|').append(f[i]);
            }
            l.onChat(seat, nameOf(seat), sb.toString());
        } else if (type.equals("emote") && f.length >= 4 && !f[2].equals(myCode)) {
            int seat = num(f[1], 0);
            l.onEmote(seat, nameOf(seat), num(f[3], 0));
        }
    }

    private synchronized String nameOf(int seat) {
        return seat >= 0 && seat < 4 && names[seat] != null ? names[seat] : "Oyuncu";
    }

    private void hostHandle(String type, String[] f) {
        long now = System.currentTimeMillis();
        if (type.equals("join") && f.length >= 5) {
            String code = f[1];
            if (code.equals(myCode) || code.isEmpty()) {
                return;
            }
            int seat = -1;
            boolean back = false;
            synchronized (this) {
                for (int i = 1; i < 4; i++) {
                    if (code.equals(codes[i])) {
                        seat = i;
                        back = true;
                    }
                }
                if (seat < 0 && phase == LOBBY && num(f[4], 0) == game) {
                    for (int i = 1; i < 4; i++) {
                        if (codes[i] == null) {
                            seat = i;
                            break;
                        }
                    }
                }
                if (seat >= 0) {
                    codes[seat] = code;
                    names[seat] = clean(f[2]);
                    levels[seat] = Math.max(1, Math.min(99, num(f[3], 1)));
                    lastSeen[seat] = now;
                }
            }
            if (seat < 0) {
                send(roomTopic(), "full|" + code);
                return;
            }
            send(roomTopic(), "welcome|" + code + "|" + seat + "|" + (phase == PLAYING ? 1 : 0));
            broadcastRoster();
            l.onRoster();
            if (back && phase == PLAYING) {
                l.onSeatBack(seat);
            }
            if (phase == LOBBY && pub) {
                advertise();
            }
        } else if (type.equals("leave") && f.length >= 3) {
            int seat = num(f[1], -1);
            if (seat >= 1 && seat < 4 && f[2].equals(codes[seat])) {
                synchronized (this) {
                    // gönüllü ayrılış: koltuk kalıcı olarak boşalır
                    codes[seat] = null;
                }
                String nm = names[seat] == null ? "Oyuncu" : names[seat];
                if (phase == PLAYING) {
                    l.onSeatFree(seat, nm);
                }
                synchronized (this) {
                    if (phase == LOBBY) {
                        names[seat] = null;
                        levels[seat] = 0;
                    }
                }
                broadcastRoster();
                l.onRoster();
                if (phase == LOBBY && pub) {
                    advertise();
                }
            }
        } else if (type.equals("hb") && f.length >= 3) {
            int seat = num(f[1], -1);
            if (seat >= 1 && seat < 4 && f[2].equals(codes[seat])) {
                boolean was = lastSeen[seat] == 0;
                lastSeen[seat] = now;
                if (was && phase == PLAYING) {
                    l.onSeatBack(seat);
                }
            }
        } else if (type.equals("req") && f.length >= 4) {
            int seat = num(f[1], -1);
            if (phase == PLAYING && seat >= 1 && seat < 4 && f[2].equals(codes[seat])) {
                lastSeen[seat] = now;
                StringBuilder sb = new StringBuilder(f[3]);
                for (int i = 4; i < f.length; i++) {
                    sb.append('|').append(f[i]);
                }
                l.onReq(seat, sb.toString());
            }
        } else if (type.equals("sync") && f.length >= 4) {
            int seat = num(f[1], -1);
            if (seat >= 1 && seat < 4 && f[2].equals(codes[seat]) && phase == PLAYING) {
                lastSeen[seat] = now;
                int from = Math.max(0, num(f[3], 0));
                send(roomTopic(), startMsg());
                for (int i = from; i < log.size() && i < from + 400; i++) {
                    send(roomTopic(), "act|" + i + "|" + log.get(i));
                }
            }
        }
    }

    private void guestHandle(String type, String[] f) {
        long now = System.currentTimeMillis();
        if (type.equals("welcome") && f.length >= 4) {
            if (!f[1].equals(myCode)) {
                return;
            }
            hostSeen = now;
            synchronized (this) {
                mySeat = num(f[2], 1);
            }
            if (phase == CONNECTING) {
                if (joinTask != null) {
                    joinTask.cancel(false);
                    joinTask = null;
                }
                phase = LOBBY;
                startTimers();
                l.onJoined(room);
                l.onRoster();
            }
        } else if (type.equals("full") && f.length >= 2) {
            if (f[1].equals(myCode) && phase == CONNECTING) {
                fail("Bu masa dolu ya da oyun başlamış.");
            }
        } else if (type.equals("roster") && f.length >= 7) {
            hostSeen = now;
            synchronized (this) {
                game = num(f[1], game);
                value = num(f[2], value);
            }
            readEntries(f, 3);
            if (phase == LOBBY || phase == PLAYING) {
                l.onRoster();
            }
        } else if (type.equals("start") && f.length >= 8) {
            hostSeen = now;
            if (phase == LOBBY) {
                readEntries(f, 4);
                phase = PLAYING;
                seed = Long.parseLong(f[3].trim());
                nextSeq = 0;
                pending.clear();
                l.onStart(num(f[1], game), num(f[2], value), seed);
            }
        } else if (type.equals("act") && f.length >= 3) {
            hostSeen = now;
            if (phase != PLAYING) {
                return;
            }
            int seq = num(f[1], -1);
            StringBuilder sb = new StringBuilder(f[2]);
            for (int i = 3; i < f.length; i++) {
                sb.append('|').append(f[i]);
            }
            if (seq < nextSeq || seq < 0) {
                return;
            }
            pending.put(seq, sb.toString());
            while (pending.containsKey(nextSeq)) {
                String c = pending.remove(nextSeq);
                l.onAct(nextSeq, c);
                nextSeq++;
            }
            if (!pending.isEmpty()) {
                requestSync(now);
            }
        } else if (type.equals("hbh") && f.length >= 3) {
            hostSeen = now;
            int size = num(f[1], 0);
            boolean started = num(f[2], 0) == 1;
            if ((phase == PLAYING && size > nextSeq) || (phase == LOBBY && started)) {
                requestSync(now);
            }
        } else if (type.equals("hostleft")) {
            closeWith("Masa sahibi masadan ayrıldı.");
        }
    }

    private synchronized void readEntries(String[] f, int from) {
        for (int i = 0; i < 4; i++) {
            String e = f[from + i];
            if (e.equals("-") || e.isEmpty()) {
                codes[i] = null;
                names[i] = null;
                levels[i] = 0;
            } else {
                String[] p = e.split("~", -1);
                names[i] = p.length > 0 ? p[0] : "Oyuncu";
                levels[i] = p.length > 1 ? num(p[1], 1) : 1;
                codes[i] = p.length > 2 ? p[2] : "?";
            }
        }
    }

    private void requestSync(long now) {
        if (now - lastSyncAt < 1500) {
            return;
        }
        lastSyncAt = now;
        send(roomTopic(), "sync|" + mySeat + "|" + myCode + "|" + nextSeq);
    }
}
