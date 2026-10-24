package com.hapistavla.oyun;

import com.hapistavla.oyun.tavla.Board;
import java.util.ArrayList;
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
 * Çevrimiçi oyunun iletişim katmanı: oda kurma, odaya katılma, otomatik eşleşme, arkadaş davetleri
 * ve oyun mesajları. Android'den bağımsızdır.
 *
 * Bütün durum değişiklikleri tek bir iş parçacığında (exec) yapılır; dinleyici (Listener)
 * metotları da bu iş parçacığından çağrılır, arayüz tarafı kendi iş parçacığına geçirmelidir.
 *
 * Kimlik: her oyuncunun kalıcı bir "oyuncu kodu" vardır. Konular:
 *   hapistavla/v1/lobby     : eşleşme arayanların herkese açık ilanı
 *   hapistavla/v1/u/KOD     : oyuncunun kişisel posta kutusu (eşleşme ve arkadaş davetleri)
 *   hapistavla/v1/r/ODA     : oda; iki oyuncu arasındaki oyun mesajları
 */
public final class Online {
    public interface Listener {
        public void onStatus(String text);

        /** Oda kuruldu, rakip bekleniyor. */
        public void onRoomCreated(String code);

        /** Oyun başlıyor. myColor: Board.WHITE (oda sahibi) ya da Board.BLACK. */
        public void onMatched(int myColor, int starter, String oppCode, String oppName);

        public void onRoll(int d1, int d2);

        public void onMove(int from, int to, int die);

        public void onEnd(String boardCode);

        public void onRematch();

        public void onEmote(int id);

        /** Rakipten yazılı sohbet mesajı geldi. */
        public default void onChat(String text) {
        }

        public void onOpponentLeft(String text);

        public void onError(String text);

        /** Bir arkadaş seni oyuna davet etti. */
        public void onInvite(String fromCode, String fromName, String room);

        /** Açık masalar listesi değişti (masaları izlerken). */
        public default void onTables(List<Table> tables) {
        }

        /** Bağlantı durumu: self=true kendi bağlantın, false rakibin. secondsLeft: dönmesi için kalan süre. */
        public default void onLink(boolean self, boolean away, int secondsLeft) {
        }

        /** Kendi bağlantın yeniden kuruldu: oyun durumunu eşitle. */
        public default void onReconnected() {
        }

        /** Rakip "eşitle" dedi: kendi hamle sayısı seq. */
        public default void onSync(int seq) {
        }

        /** Rakipten tam oyun durumu geldi. */
        public default void onState(String payload) {
        }

        /** Rakip oyunu bıraktı (süre aşımı ya da çıkış): sen kazandın. */
        public default void onOpponentForfeit(String text) {
        }

        /** Rakip süre içinde geri dönmedi: sen kazandın. */
        public default void onOpponentTimeout(String text) {
        }

        /** Senin bağlantın süre içinde geri gelmedi: kaybettin. */
        public default void onSelfTimeout() {
        }
    }

    /** Herkese açık, rakip bekleyen bir masa. */
    public static final class Table {
        public final String room;
        public final String name;
        public final String host;
        public final int level;
        /** Maç hedefi: 1, 3 ya da 5 puan. */
        public final int target;
        public long seen;

        public Table(String room, String name, String host, int level, int target) {
            this.target = target;
            this.room = room;
            this.name = name;
            this.host = host;
            this.level = level;
        }
    }

    public static final int IDLE = 0;
    public static final int CONNECTING = 1;
    public static final int HOST_WAIT = 2;
    public static final int JOIN_WAIT = 3;
    public static final int SEEK = 4;
    public static final int MATCH_WAIT = 5;
    public static final int PLAYING = 6;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final String PREFIX = "hapistavla/v1/";

    private final String myCode;
    private volatile String myName;
    private final Listener listener;
    private final Mqtt.Server[] servers;
    private final Random rnd = new Random();
    private final ScheduledThreadPoolExecutor exec;

    private Mqtt mqtt;
    private volatile int mode = IDLE;
    private boolean presenceWanted = false;
    private boolean inboxSubscribed = false;
    private boolean lobbySubscribed = false;
    private String room = "";
    private String oppCode = "";
    private int myColor = Board.WHITE;
    private long lastHeard = 0;
    private int joinTries = 0;
    private String pendingInviteTo = "";
    private String pendingInviteRoom = "";
    private long pendingInviteExpire = 0;
    private int session = 0;

    private ScheduledFuture<?> seekTask;
    private ScheduledFuture<?> hbTask;
    private ScheduledFuture<?> joinTask;
    private ScheduledFuture<?> timeoutTask;
    private ScheduledFuture<?> reconnectTask;
    private ScheduledFuture<?> tableTask;
    private ScheduledFuture<?> purgeTask;
    private volatile int myLevel = 1;
    /** Oyun türü (Board.PLAKOTO / Board.CLASSIC): lobi ve masa listeleri türe göre ayrıdır. */
    public volatile int variant = Board.PLAKOTO;
    /** Maç hedefi (1, 3, 5 puan): masayı kuran belirler, katılan kabul eder. */
    public volatile int target = 1;
    private boolean publicTable = false;
    /** Rakibin sessizliği bu kadar sürerse "bağlantısı koptu" uyarısı çıkar. */
    public static long SILENT_MS = 12000;
    /** Kopan tarafın geri dönmesi için tanınan süre. */
    public static long GRACE_MS = 60000;
    public static long HB_MS = 4000;
    private boolean oppAway = false;
    private boolean selfAway = false;
    private long selfAwayAt = 0;
    private boolean tablesSubscribed = false;
    private final Map<String, Table> tables = new LinkedHashMap<String, Table>();

    public void setLevel(int l) {
        myLevel = Math.max(1, l);
    }

    public void setName(String n) {
        myName = clean(n);
    }

    public Online(String myCode, String myName, Listener listener, Mqtt.Server[] servers) {
        this.myCode = myCode;
        this.myName = clean(myName);
        this.listener = listener;
        this.servers = servers;
        this.exec = new ScheduledThreadPoolExecutor(1, new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "online");
                t.setDaemon(true);
                return t;
            }
        });
    }

    public static String randomCode(int n, Random r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(ALPHABET.charAt(r.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Uygulama başında bir kez çağrılır: sunucuya bağlanır ve kişisel posta kutusunu dinler. */
    public void start() {
        run(new Runnable() {
            @Override
            public void run() {
                presenceWanted = true;
                ensurePresence();
            }
        });
    }

    /** Oda kurar. herkeseAcik=true ise masa, "Masalar" listesinde görünür ve biri katılana kadar bekler. */
    public void createTable(final boolean pub) {
        run(new Runnable() {
            @Override
            public void run() {
                resetSession();
                mode = CONNECTING;
                listener.onStatus("Sunucuya bağlanılıyor…");
                if (!freshConnection()) {
                    fail("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
                    return;
                }
                room = randomCode(5, rnd);
                if (!mqtt.subscribe(roomTopic(room))) {
                    fail("Oda kurulamadı, tekrar dene.");
                    return;
                }
                mode = HOST_WAIT;
                if (pub) {
                    publicTable = true;
                    final int s = session;
                    tableTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
                        @Override
                        public void run() {
                            if (s == session && mode == HOST_WAIT) {
                                send(tablesTopic(), "table|" + room + "|" + myName + "|" + myLevel + "|" + myCode + "|" + target);
                            }
                        }
                    }), 0, 3000, TimeUnit.MILLISECONDS);
                }
                listener.onRoomCreated(room);
            }
        });
    }

    public void joinRoom(final String code) {
        run(new Runnable() {
            @Override
            public void run() {
                resetSession();
                mode = CONNECTING;
                listener.onStatus("Sunucuya bağlanılıyor…");
                if (!freshConnection()) {
                    fail("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
                    return;
                }
                room = code.trim().toUpperCase();
                if (room.length() < 4 || !mqtt.subscribe(roomTopic(room))) {
                    fail("Odaya katılınamadı.");
                    return;
                }
                mode = JOIN_WAIT;
                joinTries = 0;
                listener.onStatus("Odaya katılınıyor…");
                final int s = session;
                joinTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
                    @Override
                    public void run() {
                        if (s != session || mode != JOIN_WAIT) {
                            return;
                        }
                        if (joinTries >= 6) {
                            fail("Oda bulunamadı ya da dolu. Kodu doğru yazdığından ve arkadaşının odayı açık tuttuğundan emin ol." + serverInfo());
                            return;
                        }
                        joinTries++;
                        send(roomTopic(room), "join|" + myCode + "|" + myName + "|" + variant);
                    }
                }), 0, 2000, TimeUnit.MILLISECONDS);
            }
        });
    }

    public void findMatch() {
        run(new Runnable() {
            @Override
            public void run() {
                resetSession();
                mode = CONNECTING;
                listener.onStatus("Sunucuya bağlanılıyor…");
                if (!freshConnection()) {
                    fail("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
                    return;
                }
                if (!mqtt.subscribe(lobbyTopic())) {
                    fail("Eşleşme başlatılamadı.");
                    return;
                }
                lobbySubscribed = true;
                mode = SEEK;
                listener.onStatus("Rakip aranıyor…");
                final int s = session;
                seekTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
                    @Override
                    public void run() {
                        if (s == session && mode == SEEK) {
                            send(lobbyTopic(), "seek|" + myCode);
                        }
                    }
                }), 0, 1500, TimeUnit.MILLISECONDS);
                timeoutTask = exec.schedule(guard(new Runnable() {
                    @Override
                    public void run() {
                        if (s == session && mode == SEEK) {
                            fail("Şu an rakip bulunamadı. Biraz sonra tekrar dene ya da oda kurup arkadaşını çağır." + serverInfo());
                        }
                    }
                }), 90, TimeUnit.SECONDS);
            }
        });
    }

    /** Oda kurma, katılma ya da eşleşme aramayı iptal eder; bağlantı açık kalır. */
    public void cancel() {
        run(new Runnable() {
            @Override
            public void run() {
                if (mode != PLAYING) {
                    resetSession();
                }
            }
        });
    }

    /** Oyundan ayrılır (rakibe haber verilir). */
    public void leave() {
        run(new Runnable() {
            @Override
            public void run() {
                if (mode == PLAYING) {
                    send(roomTopic(room), "leave|" + myCode);
                }
                resetSession();
            }
        });
    }

    /** Tamamen kapatır. */
    public void shutdown() {
        run(new Runnable() {
            @Override
            public void run() {
                presenceWanted = false;
                if (mode == PLAYING) {
                    send(roomTopic(room), "leave|" + myCode);
                }
                resetSession();
                if (mqtt != null) {
                    mqtt.close();
                    mqtt = null;
                }
                inboxSubscribed = false;
                exec.shutdown();
            }
        });
    }

    /** Kurulmuş odaya bir arkadaşı davet eder (oda kurma ekranındayken). */
    public void inviteFriend(final String friendCode) {
        run(new Runnable() {
            @Override
            public void run() {
                if ((mode == HOST_WAIT) && mqtt != null) {
                    send(userTopic(friendCode), "finv|" + myCode + "|" + myName + "|" + room + "|" + variant);
                }
            }
        });
    }

    public void sendRoll(final int d1, final int d2) {
        game("roll|" + myCode + "|" + d1 + "|" + d2);
    }

    public void sendMove(final int from, final int to, final int die) {
        game("move|" + myCode + "|" + from + "|" + to + "|" + die);
    }

    public void sendEnd(final String boardCode) {
        game("end|" + myCode + "|" + boardCode);
    }

    public void sendRematch() {
        game("rematch|" + myCode);
    }

    public void sendSync(int seq) {
        game("sync|" + myCode + "|" + seq);
    }

    public void sendState(String payload) {
        game("state|" + myCode + "|" + payload);
    }

    /** Oyunu bırakır (rakip kazanır); ardından oturum kapanır. */
    public void forfeit(final String text) {
        run(new Runnable() {
            @Override
            public void run() {
                if (mode == PLAYING) {
                    send(roomTopic(room), "forfeit|" + myCode + "|" + clean(text));
                }
                resetSession();
            }
        });
    }

    public void sendEmote(final int id) {
        game("emote|" + myCode + "|" + id);
    }

    public void sendChat(String text) {
        game("chat|" + myCode + "|" + text.replace('|', '/'));
    }

    private void game(final String msg) {
        run(new Runnable() {
            @Override
            public void run() {
                if (mode == PLAYING) {
                    send(roomTopic(room), msg);
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // İç işleyiş (hepsi exec iş parçacığında)
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
                    // tek bir hata zamanlayıcıları durdurmasın
                }
            }
        };
    }

    private static String clean(String s) {
        if (s == null) {
            return "Oyuncu";
        }
        String c = s.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').trim();
        if (c.length() > 16) {
            c = c.substring(0, 16);
        }
        return c.isEmpty() ? "Oyuncu" : c;
    }

    private String vsuffix() {
        return variant == Board.CLASSIC ? "c" : "";
    }

    private String tablesTopic() {
        return PREFIX + "tables" + vsuffix();
    }

    /** Açık masa ilanını kaldırır (biri katıldı ya da masa kapandı). */
    private void closeAdvert() {
        if (publicTable) {
            publicTable = false;
            if (mqtt != null && mqtt.isConnected() && !room.isEmpty()) {
                send(tablesTopic(), "tclose|" + room);
            }
        }
    }

    /** Açık masaları izlemeye başlar; liste değiştikçe onTables çağrılır. */
    public void watchTables() {
        run(new Runnable() {
            @Override
            public void run() {
                if (mode != IDLE && mode != HOST_WAIT) {
                    return;
                }
                if (!ensureConnected()) {
                    listener.onError("Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.");
                    return;
                }
                if (!tablesSubscribed) {
                    if (!mqtt.subscribe(tablesTopic())) {
                        listener.onError("Masa listesi alınamadı.");
                        return;
                    }
                    tablesSubscribed = true;
                }
                if (purgeTask == null) {
                    purgeTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
                        @Override
                        public void run() {
                            purgeTables();
                        }
                    }), 2, 2, TimeUnit.SECONDS);
                }
                notifyTables();
            }
        });
    }

    public void unwatchTables() {
        run(new Runnable() {
            @Override
            public void run() {
                stopWatching();
            }
        });
    }

    private void stopWatching() {
        if (purgeTask != null) {
            purgeTask.cancel(false);
            purgeTask = null;
        }
        if (tablesSubscribed && mqtt != null && mqtt.isConnected()) {
            mqtt.unsubscribe(tablesTopic());
        }
        tablesSubscribed = false;
        tables.clear();
    }

    private void purgeTables() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        Iterator<Table> it = tables.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().seen > 11000) {
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            notifyTables();
        }
    }

    private void notifyTables() {
        listener.onTables(new ArrayList<Table>(tables.values()));
    }

    private void handleTables(String[] f) {
        if (!tablesSubscribed) {
            return;
        }
        if (f[0].equals("table") && f.length >= 5) {
            String room = f[1];
            String host = f[4];
            if (host.equals(myCode) || room.length() < 4) {
                return;
            }
            int lv = 1;
            try {
                lv = Math.max(1, Math.min(99, Integer.parseInt(f[3])));
            } catch (NumberFormatException e) {
                lv = 1;
            }
            Table old = tables.get(room);
            int tg = 1;
            if (f.length >= 6) {
                try {
                    tg = Integer.parseInt(f[5]);
                } catch (NumberFormatException e) {
                    tg = 1;
                }
                tg = (tg == 3 || tg == 5) ? tg : 1;
            }
            Table t = new Table(room, clean(f[2]), host, lv, tg);
            t.seen = System.currentTimeMillis();
            tables.put(room, t);
            if (old == null || !old.name.equals(t.name) || old.level != t.level || old.target != t.target) {
                if (tables.size() <= 40) {
                    notifyTables();
                } else {
                    tables.remove(room);
                }
            }
        } else if (f[0].equals("tclose")) {
            if (tables.remove(f[1]) != null) {
                notifyTables();
            }
        }
    }

    private String lobbyTopic() {
        return PREFIX + "lobby" + vsuffix();
    }

    private static String userTopic(String code) {
        return PREFIX + "u/" + code;
    }

    private static String roomTopic(String code) {
        return PREFIX + "r/" + code;
    }

    private void send(String topic, String payload) {
        if (mqtt != null) {
            mqtt.publish(topic, payload);
        }
    }

    private void fail(String text) {
        resetSession();
        listener.onError(text);
    }

    private void cancelTasks() {
        ScheduledFuture<?>[] all = {seekTask, hbTask, joinTask, timeoutTask, tableTask};
        for (ScheduledFuture<?> f : all) {
            if (f != null) {
                f.cancel(false);
            }
        }
        seekTask = null;
        hbTask = null;
        joinTask = null;
        timeoutTask = null;
        tableTask = null;
    }

    /** Oturumu bırakır: bütün zamanlayıcılar durur, oda ve ilan aboneliği kalkar; bağlantı açık kalır. */
    private void resetSession() {
        session++;
        cancelTasks();
        closeAdvert();
        stopWatching();
        if (mqtt != null && mqtt.isConnected()) {
            if (!room.isEmpty()) {
                mqtt.unsubscribe(roomTopic(room));
            }
            if (lobbySubscribed) {
                mqtt.unsubscribe(lobbyTopic());
            }
        }
        lobbySubscribed = false;
        room = "";
        oppCode = "";
        pendingInviteTo = "";
        pendingInviteRoom = "";
        mode = IDLE;
    }

    /** Oda işlemlerinden önce bağlantıyı tazeler: uzun süre açık kalmış (sessizce kopmuş) bağlantıya güvenmeyiz. */
    private boolean freshConnection() {
        if (mqtt != null) {
            mqtt.close();
            mqtt = null;
        }
        inboxSubscribed = false;
        tablesSubscribed = false;
        return ensureConnected();
    }

    private String serverInfo() {
        return mqtt == null ? "" : " [" + mqtt.info() + "]";
    }

    private boolean ensureConnected() {
        if (mqtt != null && mqtt.isConnected()) {
            return ensureInbox();
        }
        mqtt = new Mqtt(myCode, new Mqtt.Listener() {
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
            public void onClosed(final String reason) {
                run(new Runnable() {
                    @Override
                    public void run() {
                        connectionLost();
                    }
                });
            }
        });
        inboxSubscribed = false;
        if (!mqtt.connect(servers, 7000)) {
            return false;
        }
        return ensureInbox();
    }

    private boolean ensureInbox() {
        if (inboxSubscribed) {
            return true;
        }
        if (mqtt.subscribe(userTopic(myCode))) {
            inboxSubscribed = true;
            return true;
        }
        return false;
    }

    private void ensurePresence() {
        if (!presenceWanted) {
            return;
        }
        if (mode == IDLE && !ensureConnected()) {
            scheduleReconnect(8000);
        }
    }

    private void scheduleReconnect(long delayMs) {
        if (reconnectTask != null) {
            reconnectTask.cancel(false);
        }
        reconnectTask = exec.schedule(guard(new Runnable() {
            @Override
            public void run() {
                ensurePresence();
            }
        }), delayMs, TimeUnit.MILLISECONDS);
    }

    private void connectionLost() {
        inboxSubscribed = false;
        tablesSubscribed = false;
        lobbySubscribed = false;
        int old = mode;
        if (old == PLAYING) {
            // oyun sürüyor: oturumu koru, süre dolana kadar yeniden bağlanmayı dene (hb görevi yapar)
            if (!selfAway) {
                selfAway = true;
                selfAwayAt = System.currentTimeMillis();
                listener.onLink(true, true, (int) (GRACE_MS / 1000));
            }
            return;
        } else if (old != IDLE) {
            resetSession();
            listener.onError("Bağlantı koptu.");
        }
        mode = IDLE;
        if (presenceWanted) {
            scheduleReconnect(4000);
        }
    }

    private void handle(String topic, String payload) {
        String[] f = payload.split("\\|", -1);
        if (f.length < 2) {
            return;
        }
        if (topic.equals(tablesTopic())) {
            handleTables(f);
        } else if (topic.equals(lobbyTopic())) {
            handleLobby(f);
        } else if (topic.equals(userTopic(myCode))) {
            handleInbox(f);
        } else if (!room.isEmpty() && topic.equals(roomTopic(room))) {
            handleRoom(f);
        }
    }

    private void handleLobby(String[] f) {
        if (!f[0].equals("seek") || mode != SEEK) {
            return;
        }
        String from = f[1];
        if (from.equals(myCode) || from.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (myCode.compareTo(from) < 0 && (pendingInviteTo.isEmpty() || now > pendingInviteExpire)) {
            pendingInviteTo = from;
            pendingInviteRoom = randomCode(5, rnd);
            pendingInviteExpire = now + 6000;
            send(userTopic(from), "invite|" + myCode + "|" + myName + "|" + pendingInviteRoom);
        }
    }

    private void handleInbox(String[] f) {
        String type = f[0];
        if (type.equals("invite") && f.length >= 4) {
            // eşleşme daveti: kabul et, odaya gir
            if (mode != SEEK) {
                return;
            }
            final String from = f[1];
            final String inviteRoom = f[3];
            cancelTasks();
            mode = MATCH_WAIT;
            room = inviteRoom;
            oppCode = from;
            if (!mqtt.subscribe(roomTopic(room))) {
                fail("Eşleşme başarısız, tekrar dene.");
                return;
            }
            send(userTopic(from), "accept|" + myCode + "|" + myName + "|" + inviteRoom);
            final int s = session;
            timeoutTask = exec.schedule(guard(new Runnable() {
                @Override
                public void run() {
                    if (s == session && mode == MATCH_WAIT) {
                        fail("Eşleşme tamamlanamadı, tekrar dene.");
                    }
                }
            }), 9, TimeUnit.SECONDS);
        } else if (type.equals("accept") && f.length >= 4) {
            String from = f[1];
            String guestName = f[2];
            String acceptedRoom = f[3];
            if (mode != SEEK || !from.equals(pendingInviteTo) || !acceptedRoom.equals(pendingInviteRoom)
                    || System.currentTimeMillis() > pendingInviteExpire + 3000) {
                return;
            }
            cancelTasks();
            room = acceptedRoom;
            if (!mqtt.subscribe(roomTopic(room))) {
                fail("Eşleşme başarısız, tekrar dene.");
                return;
            }
            becomeHost(from, guestName);
        } else if (type.equals("finv") && f.length >= 4) {
            // arkadaş daveti
            listener.onInvite(f[1], f[2], f.length >= 5 ? f[3] + ":" + f[4] : f[3]);
        }
    }

    private void becomeHost(String guestCode, String guestName) {
        oppCode = guestCode;
        myColor = Board.WHITE;
        int starter = rnd.nextInt(2);
        send(roomTopic(room), "start|" + myCode + "|" + guestCode + "|" + starter + "|" + myName + "|" + variant + "|" + target);
        lastStarter = starter;
        beginPlaying();
        listener.onMatched(Board.WHITE, starter, guestCode, guestName);
    }

    private int lastStarter = 0;

    private void handleRoom(String[] f) {
        String type = f[0];
        String from = f[1];
        if (from.equals(myCode)) {
            return;
        }
        if (type.equals("join") && f.length >= 3) {
            if (f.length >= 4 && !String.valueOf(variant).equals(f[3])) {
                send(roomTopic(room), "wrongvar|" + myCode + "|" + from + "|" + variant);
            } else if (mode == HOST_WAIT) {
                cancelTasks();
                becomeHost(from, f[2]);
            } else if (mode == PLAYING && from.equals(oppCode)) {
                send(roomTopic(room), "start|" + myCode + "|" + oppCode + "|" + lastStarter + "|" + myName + "|" + variant + "|" + target);
            } else {
                send(roomTopic(room), "full|" + myCode + "|" + from);
            }
            return;
        }
        if (type.equals("start") && f.length >= 5) {
            String guest = f[2];
            if ((mode == JOIN_WAIT || mode == MATCH_WAIT) && guest.equals(myCode)) {
                if (f.length >= 6 && !String.valueOf(variant).equals(f[5])) {
                    fail("Oda farklı bir oyun türüne ait.");
                    return;
                }
                cancelTasks();
                if (f.length >= 7 && ("1".equals(f[6]) || "3".equals(f[6]) || "5".equals(f[6]))) {
                    target = Integer.parseInt(f[6]);
                }
                oppCode = from;
                myColor = Board.BLACK;
                int starter = "1".equals(f[3]) ? 1 : 0;
                beginPlaying();
                listener.onMatched(Board.BLACK, starter, from, f[4]);
            }
            return;
        }
        if (type.equals("wrongvar") && f.length >= 3) {
            if (mode == JOIN_WAIT && f[2].equals(myCode)) {
                fail("Bu oda " + (variant == Board.CLASSIC ? "Hapis Tavla" : "Klasik Tavla")
                        + " odası. Ana ekrandan diğer oyunu seçip tekrar dene.");
            }
            return;
        }
        if (type.equals("full") && f.length >= 3) {
            if (mode == JOIN_WAIT && f[2].equals(myCode)) {
                fail("Oda dolu.");
            }
            return;
        }
        if (mode != PLAYING || !from.equals(oppCode)) {
            return;
        }
        lastHeard = System.currentTimeMillis();
        if (oppAway) {
            oppAway = false;
            listener.onLink(false, false, 0);
        }
        try {
            if (type.equals("roll") && f.length >= 4) {
                listener.onRoll(Integer.parseInt(f[2]), Integer.parseInt(f[3]));
            } else if (type.equals("move") && f.length >= 5) {
                listener.onMove(Integer.parseInt(f[2]), Integer.parseInt(f[3]), Integer.parseInt(f[4]));
            } else if (type.equals("end") && f.length >= 3) {
                listener.onEnd(f[2]);
            } else if (type.equals("rematch")) {
                listener.onRematch();
            } else if (type.equals("emote") && f.length >= 3) {
                listener.onEmote(Integer.parseInt(f[2]));
            } else if (type.equals("chat") && f.length >= 3) {
                listener.onChat(f[2]);
            } else if (type.equals("sync") && f.length >= 3) {
                listener.onSync(Integer.parseInt(f[2]));
            } else if (type.equals("state") && f.length >= 3) {
                listener.onState(f[2]);
            } else if (type.equals("forfeit")) {
                resetSession();
                listener.onOpponentForfeit(f.length >= 3 ? f[2] : "");
            } else if (type.equals("leave")) {
                resetSession();
                listener.onOpponentLeft("Rakip oyundan ayrıldı.");
            }
        } catch (NumberFormatException e) {
            // bozuk mesajı yok say
        }
    }

    private void beginPlaying() {
        closeAdvert();
        stopWatching();
        mode = PLAYING;
        lastHeard = System.currentTimeMillis();
        if (mqtt != null && lobbySubscribed) {
            mqtt.unsubscribe(lobbyTopic());
            lobbySubscribed = false;
        }
        oppAway = false;
        selfAway = false;
        final int s = session;
        hbTask = exec.scheduleWithFixedDelay(guard(new Runnable() {
            @Override
            public void run() {
                if (s != session || mode != PLAYING) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (mqtt == null || !mqtt.isConnected()) {
                    // kendi bağlantımız yok: süre dolana kadar yeniden dene
                    if (!selfAway) {
                        selfAway = true;
                        selfAwayAt = now;
                        listener.onLink(true, true, (int) (GRACE_MS / 1000));
                    }
                    if (now - selfAwayAt > GRACE_MS) {
                        resetSession();
                        listener.onSelfTimeout();
                        return;
                    }
                    if (tryRejoin(s)) {
                        selfAway = false;
                        lastHeard = System.currentTimeMillis();
                        listener.onLink(true, false, 0);
                        listener.onReconnected();
                    }
                    return;
                }
                if (selfAway) {
                    // bağlantı (kendiliğinden) geri geldi
                    selfAway = false;
                    lastHeard = now;
                    listener.onLink(true, false, 0);
                    listener.onReconnected();
                }
                send(roomTopic(room), "hb|" + myCode);
                long silence = now - lastHeard;
                if (silence > GRACE_MS) {
                    resetSession();
                    listener.onOpponentTimeout("Rakibin bağlantısı 1 dakika içinde geri gelmedi.");
                    return;
                }
                if (silence > SILENT_MS && !oppAway) {
                    oppAway = true;
                    listener.onLink(false, true, (int) Math.max(1, (GRACE_MS - silence) / 1000));
                }
            }
        }), 3000, HB_MS, TimeUnit.MILLISECONDS);
    }

    /** Oyun sırasında kopan bağlantıyı yeniden kurar ve oda kanalına tekrar abone olur. */
    private boolean tryRejoin(int s) {
        if (mqtt != null) {
            mqtt.close();
            mqtt = null;
        }
        inboxSubscribed = false;
        if (!ensureConnected() || s != session || mode != PLAYING) {
            return false;
        }
        return mqtt.subscribe(roomTopic(room));
    }
}
