package com.hapistavla.oyun;

import com.hapistavla.oyun.tavla.Ai;
import com.hapistavla.oyun.batak.BatakView;
import com.hapistavla.oyun.tavla.Board;
import com.hapistavla.oyun.tavla.Game;
import com.hapistavla.oyun.king.KingView;
import com.hapistavla.oyun.okey101.Okey101View;
import com.hapistavla.oyun.okey.OkeyView;
import com.hapistavla.oyun.salon.SalonBase;
import com.hapistavla.oyun.salon.SalonNet;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TahtaView.Callbacks {
    private InterstitialAd interstitial;
    private boolean interstitialLoading;
    private long lastInterstitialMs;
    private TahtaView view;
    private Online online;
    private Profile profile;
    private Sfx sfx;
    private Cloud cloud;
    private SharedPreferences prefs;
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private Cloud.Cancel loginCancel;

    // ------------------------------------------------------------------
    // Ekranlar (hepsi oyunun kendi ahşap tasarımıyla çizilir)
    // ------------------------------------------------------------------
    private static final int S_NONE = 0;
    private static final int S_MAIN = 1;
    private static final int S_ONLINE = 3;
    private static final int S_SETTINGS = 4;
    private static final int S_ACCOUNT = 5;
    private static final int S_PROFILE = 6;
    private static final int S_FRIENDS = 7;
    private static final int S_FRIEND = 8;
    private static final int S_THEMES = 9;
    private static final int S_RULES = 11;
    private static final int S_TABLES = 12;
    private static final int S_WAIT = 13;
    private static final int S_MSG = 14;
    private static final int S_INVITE = 15;
    private static final int S_LOGIN = 16;
    private static final int S_KEYPAD = 17;
    private static final int S_GAME = 18;
    private static final int S_CONFIRM = 19;
    private static final int S_TARGET = 20;
    private static final int S_ZAR = 21;
    private static final int S_TAS = 22;
    private static final int S_KAGIT = 23;
    private static final int S_SGAME = 25;    // salon oyunu: çevrimiçi mi, bilgisayara karşı mı
    private static final int S_SLOBBY = 26;   // salon masası bekleme odası
    private static final int S_RULESMENU = 27; // oyun seçerek kurallar
    private static final int S_NTG = 24;      // yeni oyunlar: puan / el hedefi seçimi

    // düğme kimlikleri
    private static final int M_RESUME = 1;
    private static final int M_AI = 2;
    private static final int M_PLAKOTO = 3;
    private static final int M_CLASSIC = 43;
    private static final int M_AUTO = 45;
    private static final int M_EXIT_YES = 46;
    private static final int M_EXIT_NO = 47;
    private static final int M_CHAT = 51;
    private static final int M_PIECE = 52;
    private static final int M_DICE = 53;
    private static final int M_QUIT_APP = 48;
    private static final int M_QUIT_YES = 49;
    private static final int M_QUIT_NO = 50;
    private static final int M_TG_BASE = 500;       // + puan (1, 3, 5)
    private static final int M_CAT_ZAR = 60;
    private static final int M_CAT_TAS = 61;
    private static final int M_CAT_KAGIT = 62;
    private static final int M_OKEY = 63;
    private static final int M_OKEY101 = 64;
    private static final int M_KING = 65;
    private static final int M_BATAK = 66;
    private static final int M_BATAKA = 74;
    private static final int M_TNEXT = 75;
    private int tPage = 0;
    private static final int M_NG_BASE = 600;       // + hedef değeri
    private static final int M_ONLINE = 4;
    private static final int M_FRIENDS = 6;
    private static final int M_SETTINGS = 7;
    private static final int M_RULES = 8;
    private static final int M_BACK = 9;
    private static final int M_OPEN_TABLE = 13;
    private static final int M_TABLES = 14;
    private static final int M_FIND = 17;
    private static final int M_THEME = 18;
    private static final int M_SOUND = 19;
    private static final int M_LEAVE = 20;
    private static final int M_ADDOPP = 22;
    private static final int M_ACCOUNT = 23;
    private static final int M_GOOGLE = 24;
    private static final int M_FACEBOOK = 25;
    private static final int M_SYNC = 26;
    private static final int M_LOGOUT = 27;
    private static final int M_INSTA = 28;
    private static final int M_INFO = 29;
    private static final int M_CANCEL_WAIT = 31;
    private static final int M_MYTABLE = 99;
    private static final int M_SONLINE = 70;
    private static final int M_SAI = 71;
    private static final int M_SSTART = 72;
    private static final int M_SINVITE = 73;
    private static final int M_MSG_OK = 32;
    private static final int M_ACCEPT = 33;
    private static final int M_REJECT = 34;
    private static final int M_RENAME_ME = 35;
    private static final int M_SHARE_CODE = 36;
    private static final int M_ADD_FRIEND = 37;
    private static final int M_FRIEND_INVITE = 38;
    private static final int M_FRIEND_RENAME = 39;
    private static final int M_FRIEND_DELETE = 40;
    private static final int M_OPEN_URL = 41;
    private static final int M_MORE_FRIENDS = 42;
    private static final int M_FRIEND_BASE = 100;   // + sıra
    private static final int M_OPT_ESLI = 1000;
    private static final int M_OPT_KAT = 1001;
    private static final int M_OPT_CEZA = 1002;
    private static final int M_NG_GO = 1003;
    private static final int M_RULE_BASE = 900;     // + oyun sırası (kurallar menüsü)
    private static final int M_TABLE_BASE = 200;    // + sıra
    private static final int M_THEME_BASE = 300;    // + sıra

    // ekran klavyesi kimlikleri
    private static final int K_NAME = 2;
    private static final int K_FRIEND = 3;
    private static final int K_RENAME = 4;
    private static final int K_FRIEND_NAME = 5;

    private static final String CHARS_HEX = "0123456789ABCDEF";
    private static final String CHARS_NAME = "ABCÇDEFGĞHIİJKLMNOÖPRSŞTUÜVYZ0123456789";

    private int screen = S_NONE;
    /** Ana ekranda seçilen oyun: Board.PLAKOTO ya da Board.CLASSIC. */
    private int selVariant = Board.PLAKOTO;
    /** Maç hedefi: 1, 3 ya da 5 puan. */
    private int target = 1;
    /** Puan seçiminden sonra kurulacak masa türü: 0 açık masa, 1 özel oda, 2 arkadaş daveti. */
    private int pendingTable = 0;
    private int pendingAiLevel = Ai.HARD;
    private static final int NG_OKEY = 1;
    private static final int NG_101 = 2;
    private static final int NG_BATAK = 3;
    private static final int NG_BATAKA = 4;
    private int ntGame = NG_OKEY;
    private boolean salonActive = false;
    private SalonBase salon = null;
    private android.widget.FrameLayout stage;

    private void startSalon(int game, int value) {
        startSalon(game, value, null, 0);
    }

    private void startSalon(int game, int value, SalonNet.Roster ro, long seed) {
        dropOldGame();
        closeSalon();
        SalonBase sv;
        if (game == SalonBase.G_BATAK || game == SalonBase.G_BATAKA) {
            sv = new BatakView(this, value, game == SalonBase.G_BATAKA);
        } else if (game == SalonBase.G_OKEY) {
            sv = new OkeyView(this, value);
        } else if (game == SalonBase.G_101) {
            sv = new Okey101View(this, value);
        } else if (game == SalonBase.G_KING) {
            sv = new KingView(this);
        } else {
            Toast.makeText(this, "Bu oyun çok yakında geliyor!", Toast.LENGTH_LONG).show();
            return;
        }
        sv.setHost(new SalonBase.Host() {
            @Override
            public void onSalonExit() {
                if (salon != null && salon.online() && snet != null) {
                    snet.leave();
                    shosting = false;
                    sjoined = false;
                }
                closeSalon();
                showScreen(S_MAIN);
            }

            @Override
            public void onSalonResult(int g, boolean win, int points) {
                profile.recordGame(win, false, points, false, false);
                online.setLevel(profile.level());
                uploadQuiet();
                showInterstitial();
            }

            @Override
            public Sfx sfx() {
                return sfx;
            }

            @Override
            public String playerName() {
                return profile.name;
            }

            @Override
            public int playerLevel() {
                return profile.level();
            }

            @Override
            public int playerAvatar() {
                return 0;
            }

            @Override
            public void onSalonChatInput() {
                salonChatDialog();
            }

            @Override
            public int onSalonAddFriend(String code, String name) {
                return profile.addFriend(code, name);
            }

            @Override
            public void onSalonOnlineResult(int g, boolean win, int points) {
                profile.recordGame(win, false, points, true, false);
                online.setLevel(profile.level());
                uploadQuiet();
                showInterstitial();
            }
        });
        salon = sv;
        salonActive = true;
        screen = S_NONE;
        view.hideMenu();
        stage.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        // Salon oyunu açıkken arkadaki tavla tahtası hiç görünmesin (ilk açılışta tavla kare kare görünüyordu)
        view.setVisibility(android.view.View.INVISIBLE);
        if (ro != null) {
            SalonBase.Link lk = salonLink();
            String[] nm = new String[4];
            int[] lv = new int[4];
            String[] cd = new String[4];
            for (int i = 0; i < 4; i++) {
                nm[i] = ro.names[i];
                lv[i] = ro.levels[i];
                cd[i] = ro.codes[i];
            }
            if (sv instanceof BatakView) {
                ((BatakView) sv).startOnline(lk, ro.mySeat, nm, lv, cd, seed);
            } else if (sv instanceof OkeyView) {
                ((OkeyView) sv).startOnline(lk, ro.mySeat, nm, lv, cd, seed);
            } else if (sv instanceof Okey101View) {
                ((Okey101View) sv).startOnline(lk, ro.mySeat, nm, lv, cd, seed);
            } else if (sv instanceof KingView) {
                ((KingView) sv).startOnline(lk, ro.mySeat, nm, lv, cd, seed);
            }
        } else if (sv instanceof BatakView) {
            ((BatakView) sv).start();
        } else if (sv instanceof OkeyView) {
            ((OkeyView) sv).start();
        } else if (sv instanceof Okey101View) {
            ((Okey101View) sv).start();
        } else if (sv instanceof KingView) {
            ((KingView) sv).start();
        }
    }

    private void closeSalon() {
        if (salon != null) {
            stage.removeView(salon);
            salon = null;
        }
        salonActive = false;
        view.setVisibility(android.view.View.VISIBLE);
    }

    private static boolean cardGame(int g) {
        return g == SalonBase.G_BATAK || g == SalonBase.G_BATAKA || g == SalonBase.G_KING;
    }

    private static int[] ntValues(int g) {
        return g == NG_OKEY ? new int[] {11, 9, 7, 5, 3, 1} : (g == NG_101 ? new int[] {7, 9, 11} : new int[] {81, 101});
    }

    // ------------------------------------------------------------------
    // Salon oyunları: çevrimiçi
    // ------------------------------------------------------------------

    private static String salonTitle(int g) {
        return g == SalonBase.G_BATAK ? "BATAK" : g == SalonBase.G_BATAKA ? "AÇMALI BATAK" : (g == SalonBase.G_KING ? "KING (RIFKI)"
                : (g == SalonBase.G_101 ? "101 OKEY" : "OKEY"));
    }

    private static String salonValueText(int g, int v) {
        if (g == SalonBase.G_101) {
            int fl = v / 100;
            return (v % 100) + " el" + ((fl & 1) != 0 ? " · Eşli" : "") + ((fl & 2) != 0 ? " · Katlamalı" : "")
                    + ((fl & 4) != 0 ? " · Cezalı" : "");
        } else if (g == SalonBase.G_KING) {
            return "20 el";
        } else if (g == SalonBase.G_BATAK || g == SalonBase.G_BATAKA) {
            return v + " puana kadar";
        }
        return v + " puandan";
    }

    /** 101 Okey: el sayısı + 100 * (1 eşli + 2 katlama + 4 ceza) — kurulum ekranındaki seçimlerden. */
    private int value101() {
        int els = prefs.getInt("ng" + NG_101, 9);
        int fl = (prefs.getBoolean("o101e", false) ? 1 : 0) + (prefs.getBoolean("o101k", false) ? 2 : 0)
                + (prefs.getBoolean("o101c", false) ? 4 : 0);
        return els + 100 * fl;
    }

    private int ntGameOf(int g) {
        return g == SalonBase.G_101 ? NG_101 : (g == SalonBase.G_BATAK ? NG_BATAK : (g == SalonBase.G_BATAKA ? NG_BATAKA : NG_OKEY));
    }

    private SalonNet newSalonNet() {
        SalonNet n = new SalonNet(profile.id, profile.name, salonListener(), Mqtt.PUBLIC_SERVERS);
        n.setLevel(profile.level());
        return n;
    }

    private SalonBase.Link salonLink() {
        return new SalonBase.Link() {
            @Override
            public boolean isHost() {
                return snet.isHost();
            }

            @Override
            public void act(String entry) {
                snet.sendAct(entry);
            }

            @Override
            public void req(int seat, String cmd) {
                snet.sendReq(seat, cmd);
            }

            @Override
            public void chat(String text) {
                snet.sendChat(text);
            }

            @Override
            public void emote(int id) {
                snet.sendEmote(id);
            }
        };
    }

    private SalonNet.Listener salonListener() {
        return new SalonNet.Listener() {
            @Override
            public void onStatus(final String text) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (screen == S_WAIT && salonFlow) {
                            showWait(waitTitle, waitBig, text);
                        }
                    }
                });
            }

            @Override
            public void onCreated(final String room) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        shosting = true;
                        sjoined = false;
                        showSalonLobby();
                    }
                });
            }

            @Override
            public void onJoined(final String room) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        shosting = false;
                        sjoined = true;
                        showSalonLobby();
                    }
                });
            }

            @Override
            public void onTables(final List<SalonNet.Table> tables) {
                final List<SalonNet.Table> copy = new ArrayList<SalonNet.Table>(tables);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sTables = copy;
                        if (screen == S_TABLES && salonFlow) {
                            showScreen(S_TABLES);
                        }
                    }
                });
            }

            @Override
            public void onRoster() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null || !salonFlow) {
                            return;
                        }
                        if (screen == S_SLOBBY || (screen == S_TABLES && shosting)) {
                            showSalonLobby();
                        }
                    }
                });
            }

            @Override
            public void onStart(final int game, final int value, final long seed) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sgame = game;
                        salonFlow = true;
                        beginSalonOnline(game, value, seed);
                    }
                });
            }

            @Override
            public void onAct(final int seq, final String cmd) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            salon.netAct(cmd);
                        }
                    }
                });
            }

            @Override
            public void onReq(final int seat, final String cmd) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            salon.netReq(seat, cmd);
                        }
                    }
                });
            }

            @Override
            public void onChat(final int seat, final String name, final String text) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            salon.netChat(seat, name, text);
                        }
                    }
                });
            }

            @Override
            public void onEmote(final int seat, final String name, final int id) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            salon.netEmote(seat, name, id);
                        }
                    }
                });
            }

            @Override
            public void onSeatFree(final int seat, final String name) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            salon.netSeatFree(seat);
                        }
                    }
                });
            }

            @Override
            public void onSeatBack(final int seat) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            salon.netSeatBack(seat);
                        }
                    }
                });
            }

            @Override
            public void onAutoStart() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon == null && shosting && salonFlow) {
                            salonBeginHost();
                        }
                    }
                });
            }

            @Override
            public void onClosed(final String text) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeSalon();
                        shosting = false;
                        sjoined = false;
                        swatching = false;
                        salonFlow = true;
                        showMessage("Çevrimiçi", text, S_ONLINE, null);
                    }
                });
            }

            @Override
            public void onError(final String text) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            Toast.makeText(MainActivity.this, text, Toast.LENGTH_LONG).show();
                            return;
                        }
                        shosting = false;
                        sjoined = false;
                        salonFlow = true;
                        showMessage("Çevrimiçi", text, S_ONLINE, null);
                    }
                });
            }

            @Override
            public void onLink(final boolean up) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (salon != null) {
                            Toast.makeText(MainActivity.this,
                                    up ? "Bağlantı yeniden kuruldu" : "Bağlantı koptu, yeniden deneniyor…",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        };
    }

    /** Salon çevrimiçi menüsündeki düğmeler. İşlendiyse true. */
    private boolean salonMenu(int id) {
        if (id >= M_TABLE_BASE && id < M_TABLE_BASE + 100) {
            int t = id - M_TABLE_BASE;
            if (t < sTables.size()) {
                SalonNet.Table tb = sTables.get(t);
                if (shosting) {
                    snet.leave();
                    shosting = false;
                }
                swatching = false;
                dropOldGame();
                snet.setName(profile.name);
                snet.setLevel(profile.level());
                showWait("Masaya oturuluyor", "", tb.name + " masasına bağlanılıyor…");
                snet.join(tb.room, sgame);
            }
            return true;
        }
        if (id >= M_NG_BASE && id < M_NG_BASE + 200 && sintent != 0) {
            int v = id - M_NG_BASE;
            prefs.edit().putInt("ng" + ntGameOf(sgame), v).apply();
            if (sgame == SalonBase.G_101) {
                showScreen(S_NTG);
                return true;
            }
            salonStartIntent(v);
            return true;
        }
        if (sintent != 0 && sgame == SalonBase.G_101 && id == M_NG_GO) {
            salonStartIntent(value101());
            return true;
        }
        if (id >= M_FRIEND_BASE && id < M_FRIEND_BASE + 100 && sinvitePick) {
            int i = id - M_FRIEND_BASE;
            if (i < profile.friends.size()) {
                Profile.Friend f = profile.friends.get(i);
                snet.inviteFriend(f.id);
                Toast.makeText(this, f.name + " kişisine davet gönderildi", Toast.LENGTH_LONG).show();
                sinvitePick = false;
                showSalonLobby();
            }
            return true;
        }
        switch (id) {
            case M_OPEN_TABLE:
                sintent = 1;
                salonPickValue();
                return true;
            case M_FIND:
                sintent = 3;
                salonStartIntent(sgame == SalonBase.G_KING ? 20 : sgame == SalonBase.G_101 ? value101()
                        : prefs.getInt("ng" + ntGameOf(sgame), ntValues(ntGameOf(sgame))[ntValues(ntGameOf(sgame)).length / 2]));
                return true;
            case M_TABLES:
                sintent = 0;
                tPage = 0;
                swatching = true;
                sTables = new ArrayList<SalonNet.Table>();
                snet.setName(profile.name);
                snet.setLevel(profile.level());
                showScreen(S_TABLES);
                snet.watchTables(sgame);
                return true;
            case M_CANCEL_WAIT:
                cancelWait();
                return true;
            case M_MYTABLE:
                return true;
            case M_SSTART:
                salonBeginHost();
                return true;
            case M_TNEXT:
                tPage++;
                showScreen(S_TABLES);
                return true;
            case M_SINVITE:
                sinvitePick = true;
                friendPage = 0;
                showScreen(S_FRIENDS);
                return true;
            default:
                return false;
        }
    }

    private void salonPickValue() {
        if (sgame == SalonBase.G_KING) {
            salonStartIntent(20);
        } else {
            ntGame = ntGameOf(sgame);
            showScreen(S_NTG);
        }
    }

    private void salonStartIntent(int v) {
        snet.setName(profile.name);
        snet.setLevel(profile.level());
        dropOldGame();
        if (sintent == 3) {
            showWait("Masa aranıyor", "", "Açık bir masa aranıyor; yoksa senin için masa açılır…");
            snet.quick(sgame, v);
        } else {
            showWait(sintent == 1 ? "Masa açılıyor" : "Oda kuruluyor", "", "Bağlanılıyor…");
            snet.host(sgame, v, sintent == 1);
        }
    }

    private void salonBeginHost() {
        if (snet == null || !snet.isHost() || salon != null) {
            return;
        }
        long sd = snet.startGame();
        beginSalonOnline(sgame, snet.roster().value, sd);
    }

    private void beginSalonOnline(int game, int value, long seed) {
        SalonNet.Roster r = snet.roster();
        if (swatching) {
            swatching = false;
            snet.unwatchTables();
        }
        sgame = game;
        startSalon(game, value, r, seed);
    }

    /** Masa kuruldu / bir masaya oturuldu: bekleme ekranı. */
    private void showSalonLobby() {
        if (snet == null) {
            return;
        }
        salonFlow = true;
        if (shosting && sintent == 1) {
            if (!swatching) {
                swatching = true;
                snet.watchTables(sgame);
            }
            showScreen(S_TABLES);
            return;
        }
        SalonNet.Roster r = snet.roster();
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        String body;
        if (shosting) {
            body = "Hazır olunca OYUNU BAŞLAT'a dokun; boş koltuklara bot oturur.";
            add(l, M_SSTART, "OYUNU BAŞLAT", true);
            add(l, M_SINVITE, "ARKADAŞ DAVET ET", false);
            add(l, M_CANCEL_WAIT, "MASAYI KAPAT", false);
        } else {
            body = "Masa sahibinin oyunu başlatması bekleniyor…";
            add(l, M_CANCEL_WAIT, "MASADAN AYRIL", false);
        }
        leaveScreen(S_SLOBBY);
        screen = S_SLOBBY;
        view.setMenuBg(cardGame(sgame) ? 2 : 1);
        int[] lv = new int[4];
        String[] nm = new String[4];
        for (int i = 0; i < 4; i++) {
            nm[i] = r.codes[i] != null ? r.names[i] : null;
            lv[i] = r.levels[i];
        }
        view.showSeats(salonTitle(r.game == 0 ? sgame : r.game) + " · " + salonValueText(r.game == 0 ? sgame : r.game, r.value),
                body, nm, lv, r.mySeat, l);
    }

    private void buildSalonTables(List<TahtaView.MenuItem> l) {
        view.setMenuBg(cardGame(sgame) ? 2 : 1);
        int maxT = shosting ? 4 : 6;
        int pages = Math.max(1, (sTables.size() + maxT - 1) / maxT);
        if (tPage >= pages) {
            tPage = 0;
        }
        int from = tPage * maxT;
        int n = Math.min(maxT, Math.max(0, sTables.size() - from));
        String sub;
        if (shosting) {
            SalonNet.Roster r = snet.roster();
            sub = "Masan açık · " + r.humans + "/4 oyuncu. Başka masaya dokunursan ona oturursun";
            l.add(new TahtaView.MenuItem(M_MYTABLE, "SENİN MASAN", true, 3,
                    salonValueText(sgame, r.value)).seated(r.humans, ""));
            add(l, M_SSTART, r.humans > 1 ? "OYUNU BAŞLAT" : "BOTLARLA BAŞLAT", false);
            add(l, M_SINVITE, "ARKADAŞ DAVET ET", false);
        } else {
            sub = n == 0 ? "Şu an açık masa yok. Kendi masanı aç!" : "Oturmak istediğin masaya dokun";
        }
        if (pages > 1) {
            sub += " · Sayfa " + (tPage + 1) + "/" + pages;
        }
        for (int i = from; i < from + n; i++) {
            SalonNet.Table t = sTables.get(i);
            l.add(new TahtaView.MenuItem(M_TABLE_BASE + i, t.name.toUpperCase(), false, 3,
                    "Sv " + t.level + " · " + salonValueText(t.game, t.value)).seated(t.humans, ""));
        }
        if (shosting) {
            if (pages > 1) {
                add(l, M_TNEXT, "SONRAKİ ›", false);
            }
            add(l, M_CANCEL_WAIT, "MASAMI KAPAT", false);
        } else {
            sintent = 1;
            add(l, M_OPEN_TABLE, "+ MASA AÇ", n == 0);
            if (pages > 1) {
                add(l, M_TNEXT, "SONRAKİ ›", false);
            }
            add(l, M_BACK, "‹ GERİ", false);
        }
        view.showTables(salonTitle(sgame) + " · MASALAR", sub, l);
    }

    private void salonChatDialog() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(100)});
        et.setHint("Mesajını yaz");
        new android.app.AlertDialog.Builder(this)
                .setTitle("Mesaj yaz")
                .setView(et)
                .setPositiveButton("GÖNDER", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        if (salon != null) {
                            salon.sendChatText(et.getText().toString());
                        }
                    }
                })
                .setNegativeButton("VAZGEÇ", null)
                .show();
    }

    private boolean rulesFromSettings = false;
    private int rulesGame = -1;
    private static final String[] RULE_GAMES = {"HAPİS TAVLA", "KLASİK TAVLA", "OKEY", "101 OKEY", "KING", "BATAK", "AÇMALI BATAK"};
    private int inviteVariant = Board.PLAKOTO;
    private String pendingFriendId = "";
    private String inviteFriendId = "";
    private boolean gameStarted = false;
    private int msgParent = S_MAIN;
    private Runnable msgOk;
    private boolean rulesFromGame = false;
    private int friendPage = 0;
    private Profile.Friend curFriend;
    private List<Online.Table> shownTables = new ArrayList<Online.Table>();
    private boolean watching = false;
    /** Kendi masamız açık, rakip bekliyoruz (açık masalar ekranında gösterilir). */
    private boolean hostingTable = false;

    // ---- salon oyunları: çevrimiçi ----
    private SalonNet snet;
    /** Çevrimiçi menüler tavla yerine salon oyunu (sgame) için çalışıyor. */
    private boolean salonFlow = false;
    private int sgame = SalonBase.G_OKEY;
    /** 0 çevrimdışı, 1 masa aç, 2 özel oda, 3 hızlı eşleş. */
    private int sintent = 0;
    private boolean shosting = false;
    private boolean sjoined = false;
    private boolean swatching = false;
    private boolean sinvitePick = false;
    private int inviteSalonGame = 0;
    private List<SalonNet.Table> sTables = new ArrayList<SalonNet.Table>();
    private String waitTitle = "";
    private String waitBig = "";
    private String inviteRoom = "";
    private int inviteReturn = S_NONE;
    private Cloud.DeviceCode loginCode;

    private static final String RULES_PLAKOTO =
        "Hapis tavla (Mahbus / Plakoto) iki kişilik bir tavla çeşididir.\n\n"
        + "• Her oyuncunun 15 taşı vardır. Oyun başında taşların hepsi rakibin başlangıç noktasındadır: "
        + "Beyaz 24'te, Siyah 1'de.\n"
        + "• Beyaz 24'ten 1'e, Siyah 1'den 24'e doğru ilerler. Evin sağ altta (ya da çevrimiçi oyunda her zaman "
        + "senin tarafın sağ altta).\n"
        + "• Taş vurulmaz, hapsedilir. Rakibin TEK taşının durduğu noktaya inersen o taşı hapsedersin. "
        + "Hapsedilen taş, senin taşların o noktadan tamamen ayrılana kadar oynayamaz.\n"
        + "• İki ya da daha fazla taşın olduğu nokta kapalıdır, rakip oraya inemez. "
        + "Hapis yaptığın nokta da rakibe kapalıdır.\n"
        + "• İki zar ayrı ayrı oynanır. Çift gelirse dört kez oynanır. Mümkünse tüm zarlar kullanılmak "
        + "zorundadır; yalnızca biri oynanabiliyorsa büyük olanı oynarsın.\n"
        + "• İLK EL: Her oyuncu ilk elinde en az 2 farklı taş oynamak zorundadır.\n"
        + "• İlk elden sonra zarların toplamını tek taşla oynayabilirsin: taşı seç, mavi işaretli yere dokun "
        + "(ara nokta açık olmalı).\n"
        + "• 15 taşın hepsi evine girince taş toplamaya başlarsın. İlk toplayan kazanır.\n"
        + "• Rakip hiç taş toplayamadıysa MARS: 2 puan. Aksi halde 1 puan.\n"
        + "• Anne taş: başlangıç noktandaki SON taşın hapsedilirse ve rakibin başlangıç noktası "
        + "boşalmışsa oyunu mars ile kaybedersin. Rakibin başlangıç noktasında hâlâ taş varsa oyun sürer. "
        + "İki anne taş da hapisteyse oyun berabere biter.\n\n"
        + "Oynarken: önce taşına, sonra gideceği noktaya dokun. Yeşil: normal hamle, mavi: zar toplamı, "
        + "turuncu: hapis yapan hamle. Kırmızı halkalı taşlar hapistedir.";

    private static final String RULES_CLASSIC =
        "Klasik tavla (Backgammon) iki kişilik bir oyundur. Hapis tavla kuralları burada GEÇERLİ DEĞİLDİR.\n\n"
        + "• Her oyuncunun 15 taşı vardır. Oyun standart diziliştedir: 24. noktada 2, 13'te 5, 8'de 3 ve "
        + "6'da 5 taş (Siyah için aynanın karşısı).\n"
        + "• Beyaz 24'ten 1'e, Siyah 1'den 24'e ilerler. Evin 1-6 (Beyaz) ya da 19-24 (Siyah) noktalarıdır.\n"
        + "• İki zar ayrı ayrı oynanır; çift gelirse dört kez oynanır. Mümkünse iki zar da kullanılmak "
        + "zorundadır; yalnızca biri oynanabiliyorsa büyük olanı oynarsın. Zar toplamını tek taşla "
        + "oynayabilirsin (ara nokta açık olmalı).\n"
        + "• Rakibin TEK taşı olan noktaya inersen o taşı VURURSUN: taş ortadaki bara gider. "
        + "Rakibin 2 ya da daha fazla taşı olan nokta kapalıdır, oraya inemezsin.\n"
        + "• Barda taşın varsa başka hiçbir hamle yapamazsın: önce zarla rakibin evine girmek zorundasın. "
        + "Girilecek nokta kapalıysa o zar kullanılamaz; hiç giremezsen sıra rakibe geçer.\n"
        + "• Tüm 15 taşın evine girince taş toplayabilirsin. Zar, taşın bulunduğu noktanın numarasına eşitse o taş "
        + "toplanır. Daha büyük zar yalnızca arkada (daha uzak) taşın yoksa kullanılabilir.\n"
        + "• 15 taşı ilk toplayan kazanır: 1 puan. Rakip hiç taş toplayamadıysa MARS: 2 puan.\n"
        + "• Katlama küpü ve backgammon (3 puan) yoktur.\n\n"
        + "Oynarken: önce taşına (ya da bardaki taşlara), sonra gideceği noktaya dokun. Yeşil: normal hamle, "
        + "mavi: zar toplamı, turuncu: vuruş yapan hamle.";


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        prefs = getSharedPreferences("hapis", MODE_PRIVATE);
        profile = new Profile(new Profile.Store() {
            @Override
            public String get(String key, String def) {
                return prefs.getString("p_" + key, def);
            }

            @Override
            public void put(String key, String value) {
                prefs.edit().putString("p_" + key, value).apply();
            }
        });
        Cloud.Config cc = new Cloud.Config();
        cc.firebaseApiKey = getString(R.string.firebase_api_key).trim();
        cc.databaseUrl = getString(R.string.firebase_database_url).trim();
        cc.googleClientId = getString(R.string.google_client_id).trim();
        cc.googleClientSecret = getString(R.string.google_client_secret).trim();
        cc.facebookAppId = getString(R.string.facebook_app_id).trim();
        cc.facebookClientToken = getString(R.string.facebook_client_token).trim();
        cloud = new Cloud(cc, new Profile.Store() {
            @Override
            public String get(String key, String def) {
                return prefs.getString("x_" + key, def);
            }

            @Override
            public void put(String key, String value) {
                prefs.edit().putString("x_" + key, value).apply();
            }
        });
        sfx = new Sfx();
        sfx.setEnabled(!"0".equals(prefs.getString("sound", "1")));
        try {
            int tg = Integer.parseInt(prefs.getString("target", "1"));
            target = (tg == 3 || tg == 5) ? tg : 1;
        } catch (NumberFormatException e) {
            target = 1;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF120D0A);

        view = new TahtaView(this);
        view.setCallbacks(this);
        view.setSfx(sfx);
        int th = 0;
        try {
            th = Integer.parseInt(prefs.getString("theme", "0"));
        } catch (NumberFormatException e) {
            th = 0;
        }
        if (th >= 0 && th < Gfx.THEMES.length && profile.level() >= Gfx.THEMES[th].unlockLevel) {
            view.setTheme(th);
        }
        try {
            view.setPieceStyle(Integer.parseInt(prefs.getString("pstyle", "0")));
            view.setDiceStyle(Integer.parseInt(prefs.getString("dstyle", "0")));
        } catch (NumberFormatException e) {
            // varsayılan stil
        }
        stage = new android.widget.FrameLayout(this);
        stage.addView(view, new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(stage, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
        hideSystemUi();

        MobileAds.initialize(this, new OnInitializationCompleteListener() {
            @Override
            public void onInitializationComplete(InitializationStatus initializationStatus) {
                // başlatma bitti
            }
        });
        loadInterstitial();

        online = new Online(profile.id, profile.name, view.game.onlineListener(), Mqtt.PUBLIC_SERVERS);
        online.setLevel(profile.level());
        snet = newSalonNet();
        view.game.setOnline(online);
        online.target = target;
        view.game.autoCollect = "1".equals(prefs.getString("auto", "0"));
        view.game.chatEnabled = !"0".equals(prefs.getString("chat", "1"));
        online.start();

        int bonus = profile.claimDaily(System.currentTimeMillis());
        if (bonus > 0) {
            Toast.makeText(this, "Günlük bonus: +" + bonus + " puan (üst üste " + profile.dailyStreak + ". gün)",
                    Toast.LENGTH_LONG).show();
        }
        showScreen(S_MAIN);
        syncNow(false);
    }

    // ------------------------------------------------------------------
    // Ekran çizimi
    // ------------------------------------------------------------------

    private static void add(List<TahtaView.MenuItem> l, int id, String label, boolean primary) {
        l.add(new TahtaView.MenuItem(id, label, primary));
    }

    private static void addT(List<TahtaView.MenuItem> l, int id, String label, String sub, int icon, boolean primary) {
        l.add(new TahtaView.MenuItem(id, label, primary, icon, sub));
    }

    private boolean canAddOpponent() {
        String oc = view.game.oppCode;
        return view.game.mode == Game.MODE_ONLINE && oc != null && oc.length() == 8
                && profile.findFriend(oc) == null && !oc.equals(profile.id);
    }

    private static String variantName(int v) {
        return v == Board.CLASSIC ? "KLASİK TAVLA" : "HAPİS TAVLA";
    }

    private String subtitle() {
        return "Seviye " + profile.level() + "  •  " + profile.rankTitle() + "  •  " + profile.name;
    }

    private void leaveScreen(int next) {
        if (swatching && next != S_TABLES) {
            swatching = false;
            if (snet != null) {
                snet.unwatchTables();
            }
        }
        if (next == S_MAIN || next == S_ZAR || next == S_TAS || next == S_KAGIT) {
            salonFlow = false;
            sinvitePick = false;
        }
        if (watching && next != S_TABLES) {
            watching = false;
            online.unwatchTables();
        }
    }

    private void showScreen(int s) {
        leaveScreen(s);
        screen = s;
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        boolean inOnline = view.game.mode == Game.MODE_ONLINE;
        String sub = subtitle();
        view.setMenuBg(0);
        view.setHomeCorners(false);
        switch (s) {
            case S_ZAR:
                sub = "Hangi tavla?";
                l.add(new TahtaView.MenuItem(M_PLAKOTO, "HAPİS TAVLA", !gameStarted, 1, "Taşları hapset"));
                l.add(new TahtaView.MenuItem(M_CLASSIC, "KLASİK TAVLA", false, 2, "Vur, topla, kazan"));
                if (gameStarted) {
                    add(l, M_RESUME, "OYUNA DÖN", true);
                }
                add(l, M_BACK, "‹ GERİ", false);
                view.showHome("ZAR OYUNLARI", sub, l);
                return;
            case S_TAS:
                sub = "Hangi okey?";
                view.setMenuBg(1);
                l.add(new TahtaView.MenuItem(M_OKEY, "OKEY", true, 7, "Seri ve çift dizerek bitir"));
                l.add(new TahtaView.MenuItem(M_OKEY101, "101 OKEY", false, 8, "101 puana ulaşınca aç"));
                add(l, M_BACK, "‹ GERİ", false);
                view.showHome("TAŞ OYUNLARI", sub, l);
                return;
            case S_KAGIT:
                sub = "Hangi oyun?";
                view.setMenuBg(2);
                l.add(new TahtaView.MenuItem(M_KING, "KING (RIFKI)", true, 9, "Cezalardan kaç, puan topla"));
                l.add(new TahtaView.MenuItem(M_BATAK, "BATAK", false, 12, "İhale ver, kozu seç"));
                l.add(new TahtaView.MenuItem(M_BATAKA, "AÇMALI BATAK", false, 12, "Eşinin kartları açık"));
                add(l, M_BACK, "‹ GERİ", false);
                view.showHome("KAĞIT OYUNLARI", sub, l);
                return;
            case S_SGAME:
                sub = "Nasıl oynamak istersin?";
                view.setMenuBg(cardGame(sgame) ? 2 : 1);
                addT(l, M_SONLINE, "ÇEVRİMİÇİ OYNA", "gerçek oyuncularla", TileIcons.GLOBE, true);
                addT(l, M_SAI, "BİLGİSAYARA KARŞI", "botlarla", TileIcons.ROBOT, false);
                add(l, M_BACK, "‹ GERİ", false);
                view.showMenu(salonTitle(sgame), sub, l);
                return;
            case S_NTG: {
                int[] vals = ntValues(ntGame);
                view.setMenuBg(ntGame == NG_BATAK || ntGame == NG_BATAKA ? 2 : 1);
                sub = ntGame == NG_OKEY ? "Kaç puandan düşülsün?" : (ntGame == NG_101 ? "Kaç el oynansın?" : "Kaç puana kadar?");
                String unit = ntGame == NG_101 ? " EL" : " PUAN";
                int cur = prefs.getInt("ng" + ntGame, vals[vals.length / 2]);
                for (int i = 0; i < vals.length; i++) {
                    String hint = "";
                    if (ntGame == NG_OKEY) {
                        hint = i == 0 ? "uzun maç" : (i == vals.length - 1 ? "kısa maç" : "");
                    } else if (ntGame == NG_101) {
                        hint = i == 0 ? "kısa" : (i == 1 ? "orta" : "uzun");
                    } else {
                        hint = i == 0 ? "kısa maç" : "uzun maç";
                    }
                    addT(l, M_NG_BASE + vals[i], vals[i] + unit, hint, 100 + vals[i], vals[i] == cur);
                }
                if (ntGame == NG_101) {
                    l.add(new TahtaView.MenuItem(M_OPT_ESLI, "EŞLİ", false, 0, "Karşılıklı oturanlar takım olur").toggled(prefs.getBoolean("o101e", false)));
                    l.add(new TahtaView.MenuItem(M_OPT_KAT, "KATLAMA", false, 0, "Okeyle / elden bitirmede puanlar katlanır").toggled(prefs.getBoolean("o101k", false)));
                    l.add(new TahtaView.MenuItem(M_OPT_CEZA, "CEZA", false, 0, "Okey atmak / işlenebilir taş atmak +101").toggled(prefs.getBoolean("o101c", false)));
                    add(l, M_NG_GO, "OYNA", true);
                }
                add(l, M_BACK, "‹ GERİ", false);
                view.showMenu(ntGame == NG_OKEY ? "OKEY" : (ntGame == NG_101 ? "101 OKEY" : ntGame == NG_BATAKA ? "AÇMALI BATAK" : "BATAK"), sub, l);
                return;
            }
            case S_ONLINE:
                sub = "Çevrimiçi oyun";
                if (salonFlow) {
                    view.setMenuBg(cardGame(sgame) ? 2 : 1);
                }
                addT(l, M_OPEN_TABLE, "MASA AÇ", "rakip bekle", TileIcons.TABLE_ADD, true);
                addT(l, M_TABLES, "AÇIK MASALAR", "birine katıl", TileIcons.TABLE_LIST, false);
                addT(l, M_FIND, "HIZLI EŞLEŞ", "hemen oyna", TileIcons.BOLT, false);
                add(l, M_BACK, "‹ GERİ", false);
                break;
            case S_SETTINGS:
                sub = "Ayarlar";
                addT(l, M_THEME, "TAHTA TEMASI", Gfx.THEMES[view.getTheme()].name.toUpperCase(), TileIcons.PALETTE, false);
                addT(l, M_SOUND, "SES", sfx.isEnabled() ? "AÇIK" : "KAPALI",
                        sfx.isEnabled() ? TileIcons.SPEAKER_ON : TileIcons.SPEAKER_OFF, false);
                addT(l, M_PIECE, "TAŞ STİLİ", Gfx.PIECE_STYLES[view.getPieceStyle()], TileIcons.CHECKER, false);
                addT(l, M_DICE, "ZAR STİLİ", TahtaView.DICE_STYLES[view.getDiceStyle()], TileIcons.DICE, false);
                addT(l, M_AUTO, "OTO TOPLAMA", view.game.autoCollect ? "AÇIK" : "KAPALI", TileIcons.AUTO, false);
                addT(l, M_CHAT, "SOHBET", view.game.chatEnabled ? "AÇIK" : "KAPALI",
                        view.game.chatEnabled ? TileIcons.CHAT_ON : TileIcons.CHAT_OFF, false);
                addT(l, M_RULES, "KURALLAR", "nasıl oynanır", TileIcons.BOOK, false);
                add(l, M_BACK, "‹ GERİ", false);
                break;
            case S_RULESMENU: {
                sub = "Hangi oyunun kuralları?";
                int[] ic = {TileIcons.CHECKER, TileIcons.DICE, TileIcons.BOOK, TileIcons.BOOK, TileIcons.BOOK, TileIcons.BOOK, TileIcons.BOOK};
                for (int i = 0; i < RULE_GAMES.length; i++) {
                    addT(l, M_RULE_BASE + i, RULE_GAMES[i], "", ic[i], false);
                }
                add(l, M_BACK, "‹ GERİ", false);
                view.showMenu("KURALLAR", sub, l);
                return;
            }
            case S_ACCOUNT:
                sub = "Hesap ve ilerleme";
                if (cloud.loggedIn()) {
                    String who = cloud.displayName.isEmpty() ? "HESAP BAĞLI" : cloud.displayName.toUpperCase();
                    add(l, M_INFO, "✔ " + who + (Cloud.GOOGLE.equals(cloud.provider) ? " (GOOGLE)" : " (FACEBOOK)"), true);
                    add(l, M_SYNC, "ŞİMDİ EŞİTLE", false);
                    add(l, M_LOGOUT, "ÇIKIŞ YAP", false);
                } else if (cloud.config().anyReady()) {
                    if (cloud.config().googleReady()) {
                        add(l, M_GOOGLE, "GOOGLE İLE GİR", true);
                    }
                    if (cloud.config().facebookReady()) {
                        add(l, M_FACEBOOK, "FACEBOOK İLE GİR", !cloud.config().googleReady());
                    }
                } else {
                    add(l, M_INFO, "GİRİŞ HENÜZ KURULMADI", false);
                }
                add(l, M_INSTA, "ARKADAŞLARINI DAVET ET", false);
                add(l, M_BACK, "‹ GERİ", false);
                break;
            case S_FRIENDS: {
                sub = "Arkadaşlar (" + profile.friends.size() + ")";
                add(l, M_ADD_FRIEND, "+ ARKADAŞ EKLE (KOD)", true);
                add(l, M_SHARE_CODE, "KODUMU PAYLAŞ", false);
                int per = 4;
                int from = friendPage * per;
                if (from >= profile.friends.size() && friendPage > 0) {
                    friendPage = 0;
                    from = 0;
                }
                for (int i = from; i < profile.friends.size() && i < from + per; i++) {
                    Profile.Friend f = profile.friends.get(i);
                    add(l, M_FRIEND_BASE + i, f.name.toUpperCase() + "  (" + Profile.formatId(f.id) + ")", false);
                }
                if (from + per < profile.friends.size()) {
                    add(l, M_MORE_FRIENDS, "DAHA FAZLA ›", false);
                }
                add(l, M_BACK, "‹ GERİ", false);
                break;
            }
            case S_THEMES: {
                sub = "Tahta teması";
                int lv = profile.level();
                for (int i = 0; i < Gfx.THEMES.length; i++) {
                    Gfx.Theme t = Gfx.THEMES[i];
                    String lab = t.name.toUpperCase();
                    if (lv < t.unlockLevel) {
                        lab += "  (SEVİYE " + t.unlockLevel + ")";
                    } else if (view.getTheme() == i) {
                        lab += "  ✔";
                    }
                    add(l, M_THEME_BASE + i, lab, view.getTheme() == i);
                }
                add(l, M_BACK, "‹ GERİ", false);
                break;
            }
            case S_TABLES: {
                if (salonFlow) {
                    buildSalonTables(l);
                    return;
                }
                shownTables = new ArrayList<Online.Table>(view.game.tables);
                int maxT = hostingTable ? 5 : 6;
                while (shownTables.size() > maxT) {
                    shownTables.remove(shownTables.size() - 1);
                }
                if (hostingTable) {
                    sub = shownTables.isEmpty() ? "Masan açık, rakip bekleniyor…"
                            : "Masan açık. Başka bir masaya oturmak için ona dokun";
                    l.add(new TahtaView.MenuItem(M_MYTABLE, "SENİN MASAN", true, 3,
                            "Rakip bekleniyor… · " + (target > 1 ? target + " puana kadar" : "tek oyun")));
                } else {
                    sub = shownTables.isEmpty() ? "Şu an açık masa yok. Kendi masanı aç, rakip gelsin!"
                            : "Oturmak istediğin masaya dokun";
                }
                for (int i = 0; i < shownTables.size(); i++) {
                    Online.Table t = shownTables.get(i);
                    l.add(new TahtaView.MenuItem(M_TABLE_BASE + i, t.name.toUpperCase(), false, 3,
                            "Seviye " + t.level + " · " + (t.target > 1 ? t.target + " puana kadar" : "tek oyun")));
                }
                if (hostingTable) {
                    add(l, M_CANCEL_WAIT, "MASAMI KAPAT", false);
                } else {
                    add(l, M_OPEN_TABLE, "+ MASA AÇ", shownTables.isEmpty());
                    add(l, M_BACK, "‹ GERİ", false);
                }
                view.showTables(variantName(selVariant) + " · MASALAR", sub, l);
                return;
            }
            case S_TARGET:
                sub = "Kaç puana kadar oynansın?";
                addT(l, M_TG_BASE + 1, "1 PUAN", "tek oyun", TileIcons.MEDAL1, target == 1);
                addT(l, M_TG_BASE + 3, "3 PUAN", "3'e kadar", TileIcons.MEDAL3, target == 3);
                addT(l, M_TG_BASE + 5, "5 PUAN", "5'e kadar", TileIcons.MEDAL5, target == 5);
                add(l, M_BACK, "‹ GERİ", false);
                break;
            case S_GAME:
                sub = "Nasıl oynamak istersin?";
                addT(l, M_ONLINE, "ÇEVRİMİÇİ OYNA", "gerçek rakiplerle", TileIcons.GLOBE, true);
                addT(l, M_AI, "BİLGİSAYARA KARŞI", "tek başına", TileIcons.ROBOT, false);
                add(l, M_BACK, "‹ GERİ", false);
                break;
            default:
                screen = S_MAIN;
                if (inOnline) {
                    // çevrimiçi oyun sürerken: oyun menüsü
                    sub = variantName(view.game.board.variant) + " · çevrimiçi";
                    add(l, M_RESUME, "OYUNA DÖN", true);
                    if (canAddOpponent()) {
                        add(l, M_ADDOPP, "RAKİBİ ARKADAŞ EKLE", false);
                    }
                    add(l, M_RULES, "KURALLAR", false);
                    add(l, M_SETTINGS, "AYARLAR", false);
                    add(l, M_LEAVE, "OYUNDAN ÇIK", false);
                    view.showMenu(variantName(view.game.board.variant), sub, l);
                    return;
                }
                // ana salon: üç kategori (sağ üstte profil ve ayarlar simgeleri)
                view.setMenuBg(1);
                view.setHomeCorners(true);
                boolean active = gameStarted || salonActive;
                l.add(new TahtaView.MenuItem(M_CAT_ZAR, "ZAR OYUNLARI", !active, 4, "Hapis Tavla · Klasik Tavla"));
                l.add(new TahtaView.MenuItem(M_CAT_TAS, "TAŞ OYUNLARI", false, 5, "Okey · 101 Okey"));
                l.add(new TahtaView.MenuItem(M_CAT_KAGIT, "KAĞIT OYUNLARI", false, 6, "King (Rıfkı) · Batak"));
                if (active) {
                    add(l, M_RESUME, "OYUNA DÖN", true);
                }
                add(l, M_QUIT_APP, "ÇIKIŞ", false);
                view.showHome("OYUN SALONU", sub, l);
                return;
        }
        view.showMenu(salonFlow && screen == S_ONLINE ? salonTitle(sgame)
                : (screen == S_ONLINE || screen == S_TARGET || screen == S_GAME || screen == S_TABLES ? variantName(selVariant) : "AYARLAR"), sub, l);
    }

    private void showPanelScreen(int s, String title, String big, String body, List<TahtaView.MenuItem> items) {
        leaveScreen(s);
        screen = s;
        if ((s == S_MSG || s == S_WAIT || s == S_CONFIRM) && (big == null || big.length() == 0)) {
            view.showDialog(title, body, items);
        } else {
            view.showPanel(title, big, body, items);
        }
    }

    private void showMessage(String title, String body, int parent, Runnable ok) {
        msgParent = parent;
        msgOk = ok;
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_MSG_OK, "TAMAM", true);
        showPanelScreen(S_MSG, title, "", body, l);
    }

    private void showWait(String title, String big, String body) {
        waitTitle = title;
        waitBig = big;
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_CANCEL_WAIT, "İPTAL", false);
        showPanelScreen(S_WAIT, title, big, body, l);
    }

    private static String plainRules(String t) {
        StringBuilder sb = new StringBuilder();
        for (String ln : t.split("\n", -1)) {
            if (ln.startsWith("## ")) {
                sb.append("▸ ").append(ln.substring(3).toUpperCase(new java.util.Locale("tr", "TR")));
            } else {
                sb.append(ln);
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void showGameRules(int i) {
        rulesGame = i;
        String body;
        switch (i) {
            case 0: body = RULES_PLAKOTO; break;
            case 1: body = RULES_CLASSIC; break;
            case 2: body = plainRules(OkeyView.rules()); break;
            case 3: body = plainRules(Okey101View.rules()); break;
            case 4: body = plainRules(KingView.rules()); break;
            case 5: body = plainRules(BatakView.rules(false)); break;
            default: body = plainRules(BatakView.rules(true)); break;
        }
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_BACK, "‹ GERİ", true);
        showPanelScreen(S_RULES, "KURALLAR · " + RULE_GAMES[i], "", body, l);
    }

    private void showRules() {
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_BACK, "‹ GERİ", true);
        int rv = (rulesFromGame && gameStarted) ? view.game.board.variant : selVariant;
        showPanelScreen(S_RULES, "KURALLAR · " + variantName(rv), "",
                rv == Board.CLASSIC ? RULES_CLASSIC : RULES_PLAKOTO, l);
    }

    private void showProfile() {
        StringBuilder sb = new StringBuilder();
        int lv = profile.level();
        sb.append("Oyuncu kodu: ").append(profile.displayId()).append("\n");
        sb.append("Puan: ").append(profile.xp).append(" / ").append(Profile.xpForLevel(lv + 1)).append("\n\n");
        sb.append("Oyun: ").append(profile.games).append("   Galibiyet: ").append(profile.wins)
                .append("   Mağlubiyet: ").append(profile.losses).append("   Beraberlik: ").append(profile.ties).append("\n");
        sb.append("Mars: ").append(profile.mars).append("   Hapis: ").append(profile.pins)
                .append("   En iyi seri: ").append(profile.bestStreak).append("\n");
        sb.append("Çevrimiçi: ").append(profile.onlineWins).append(" galibiyet / ").append(profile.onlineGames)
                .append(" oyun\n");
        sb.append("Günlük seri: ").append(profile.dailyStreak).append(" gün\n\n");
        sb.append("Başarımlar (").append(profile.achCount()).append("/").append(Profile.ACH_NAMES.length).append(")\n");
        for (int i = 0; i < Profile.ACH_NAMES.length; i++) {
            sb.append(profile.hasAch(i) ? "✔ " : "○ ").append(Profile.ACH_NAMES[i])
                    .append(" – ").append(Profile.ACH_DESC[i]).append("\n");
        }
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_FRIENDS, "ARKADAŞLAR (" + profile.friends.size() + ")", true);
        add(l, M_ACCOUNT, cloud.loggedIn() ? "HESABIM ✔" : "GİRİŞ YAP / HESAP OLUŞTUR", false);
        add(l, M_RENAME_ME, "ADI DEĞİŞTİR", false);
        add(l, M_SHARE_CODE, "KODUMU PAYLAŞ", false);
        add(l, M_BACK, "‹ GERİ", false);
        showPanelScreen(S_PROFILE, profile.name.toUpperCase(), "Seviye " + lv + " • " + profile.rankTitle(),
                sb.toString(), l);
    }

    private void showFriend(Profile.Friend f) {
        curFriend = f;
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_FRIEND_INVITE, "OYUNA DAVET ET", true);
        add(l, M_FRIEND_RENAME, "YENİDEN ADLANDIR", false);
        add(l, M_FRIEND_DELETE, "SİL", false);
        add(l, M_BACK, "‹ GERİ", false);
        showPanelScreen(S_FRIEND, f.name.toUpperCase(), Profile.formatId(f.id),
                "Davet, arkadaşın uygulamayı açıksa ona ulaşır.", l);
    }

    private void showKeypad(int id, String title, String chars, int min, int max, boolean space, String init,
                            String ok) {
        leaveScreen(S_KEYPAD);
        screen = S_KEYPAD;
        view.showKeypad(id, title, chars, min, max, space, init, ok);
    }

    private void share(String title, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, title));
    }

    /** Yeni bir çevrimiçi işe başlarken yarım kalan eski oyunu (ör. bilgisayara karşı) bitirir. */
    private void dropOldGame() {
        if (gameStarted) {
            if (view.game.mode == Game.MODE_ONLINE) {
                view.game.quitOnline();
            } else {
                view.game.setMode(Game.MODE_AI, view.game.aiLevel);
            }
            gameStarted = false;
        }
    }

    private void startGame() {
        gameStarted = true;
        screen = S_NONE;
        view.hideMenu();
    }

    // ------------------------------------------------------------------
    // Geri çağrıları
    // ------------------------------------------------------------------

    @Override
    public void onMenu() {
        showScreen(S_MAIN);
    }

    @Override
    public void onRules() {
        rulesFromGame = true;
        showRules();
    }

    private void goBack() {
        switch (screen) {
            case S_MAIN:
                if (gameStarted) {
                    screen = S_NONE;
                    view.hideMenu();
                } else {
                    finish();
                }
                break;
            case S_NONE:
                showScreen(S_MAIN);
                break;
            case S_THEMES:
                showScreen(S_SETTINGS);
                break;
            case S_FRIEND:
                showScreen(S_FRIENDS);
                break;
            case S_FRIENDS:
            case S_ACCOUNT:
                if (sinvitePick) {
                    sinvitePick = false;
                    showSalonLobby();
                } else {
                    showProfile();
                }
                break;
            case S_SGAME:
                showScreen(cardGame(sgame) ? S_KAGIT : S_TAS);
                break;
            case S_ONLINE:
                showScreen(salonFlow ? S_SGAME : S_GAME);
                break;
            case S_GAME:
                showScreen(S_ZAR);
                break;
            case S_ZAR:
            case S_TAS:
            case S_KAGIT:
                showScreen(S_MAIN);
                break;
            case S_NTG:
                showScreen(salonFlow && sintent != 0 ? S_ONLINE : S_SGAME);
                break;
            case S_SLOBBY:
                cancelWait();
                break;
            case S_TABLES:
                if (hostingTable || shosting) {
                    cancelWait();
                } else {
                    showScreen(S_ONLINE);
                }
                break;
            case S_TARGET:
                showScreen(pendingTable == 3 ? S_GAME : (pendingTable == 2 ? S_FRIEND : S_ONLINE));
                break;
            case S_CONFIRM:
                screen = S_NONE;
                view.hideMenu();
                break;
            case S_RULESMENU:
                showScreen(S_SETTINGS);
                break;
            case S_RULES:
                if (rulesGame >= 0) {
                    rulesGame = -1;
                    showScreen(S_RULESMENU);
                } else if (rulesFromSettings) {
                    rulesFromSettings = false;
                    rulesFromGame = false;
                    showScreen(S_SETTINGS);
                } else if (rulesFromGame && gameStarted) {
                    rulesFromGame = false;
                    screen = S_NONE;
                    view.hideMenu();
                } else {
                    rulesFromGame = false;
                    showScreen(view.game.mode == Game.MODE_ONLINE ? S_MAIN : S_GAME);
                }
                break;
            case S_WAIT:
                cancelWait();
                break;
            case S_LOGIN:
                cancelLogin();
                break;
            case S_INVITE:
                rejectInvite();
                break;
            case S_MSG:
                runMsgOk();
                break;
            case S_KEYPAD:
                onKeypad(lastKeypadId, null);
                break;
            default:
                showScreen(S_MAIN);
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (salon != null) {
            salon.onBack();
            return;
        }
        if (view.isMenuOpen()) {
            if (screen == S_SETTINGS || screen == S_PROFILE) {
                showScreen(S_MAIN);
            } else {
                goBack();
            }
        } else {
            showScreen(S_MAIN);
        }
    }

    private int lastKeypadId = 0;

    private void cancelWait() {
        if (salonFlow) {
            shosting = false;
            sjoined = false;
            swatching = false;
            snet.leave();
            showScreen(S_ONLINE);
            return;
        }
        hostingTable = false;
        inviteFriendId = "";
        online.cancel();
        showScreen(S_ONLINE);
    }

    private void runMsgOk() {
        Runnable r = msgOk;
        msgOk = null;
        if (r != null) {
            r.run();
        } else {
            showScreen(msgParent);
        }
    }

    private void rejectInvite() {
        if (inviteReturn == S_NONE) {
            screen = S_NONE;
            view.hideMenu();
        } else {
            showScreen(inviteReturn);
        }
    }

    @Override
    public void onKeypad(int id, String text) {
        lastKeypadId = id;
        switch (id) {
            case K_NAME:
                if (text != null) {
                    profile.setName(text);
                    online.setName(profile.name);
                    if (snet != null) {
                        snet.setName(profile.name);
                    }
                }
                showProfile();
                break;
            case K_FRIEND:
                if (text != null && Profile.normalizeId(text).length() == 8
                        && profile.findFriend(Profile.normalizeId(text)) == null
                        && !Profile.normalizeId(text).equals(profile.id)) {
                    // kod geçerli: arkadaşa bir ad ver, sonra kaydet
                    pendingFriendId = Profile.normalizeId(text);
                    showKeypad(K_FRIEND_NAME, "ARKADAŞININ ADI", CHARS_NAME, 1, 14, true, "Arkadaş", "EKLE");
                    break;
                }
                if (text != null) {
                    int res = profile.addFriend(text, "Arkadaş");
                    String msg;
                    switch (res) {
                        case Profile.FRIEND_OK:
                            msg = "Arkadaş eklendi";
                            lookupFriend(Profile.normalizeId(text));
                            break;
                        case Profile.FRIEND_SELF:
                            msg = "Bu senin kodun";
                            break;
                        case Profile.FRIEND_DUPLICATE:
                            msg = "Zaten arkadaşsınız";
                            break;
                        case Profile.FRIEND_LIMIT:
                            msg = "Arkadaş listesi dolu";
                            break;
                        default:
                            msg = "Kod geçersiz";
                            break;
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
                showScreen(S_FRIENDS);
                break;
            case K_FRIEND_NAME:
                if (text == null) {
                    showScreen(S_FRIENDS);
                    break;
                }
                {
                    int res = profile.addFriend(pendingFriendId, text);
                    if (res == Profile.FRIEND_OK) {
                        lookupFriend(pendingFriendId);
                        Toast.makeText(this, "Arkadaş eklendi: " + text, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Eklenemedi", Toast.LENGTH_SHORT).show();
                    }
                    pendingFriendId = "";
                }
                showScreen(S_FRIENDS);
                break;
            case K_RENAME:
                if (text != null && curFriend != null) {
                    profile.renameFriend(curFriend.id, text);
                }
                showScreen(S_FRIENDS);
                break;
            default:
                showScreen(S_MAIN);
                break;
        }
    }

    @Override
    public void onMenuItem(int id) {
        if (salonFlow && salonMenu(id)) {
            return;
        }
        if (id >= M_THEME_BASE && id < M_THEME_BASE + 100) {
            int t = id - M_THEME_BASE;
            if (t < Gfx.THEMES.length) {
                if (profile.level() >= Gfx.THEMES[t].unlockLevel) {
                    view.setTheme(t);
                    prefs.edit().putString("theme", String.valueOf(t)).apply();
                } else {
                    Toast.makeText(this, "Seviye " + Gfx.THEMES[t].unlockLevel + " olunca açılır",
                            Toast.LENGTH_SHORT).show();
                }
                showScreen(S_THEMES);
            }
            return;
        }
        if (id >= M_RULE_BASE && id < M_RULE_BASE + RULE_GAMES.length) {
            showGameRules(id - M_RULE_BASE);
            return;
        }
        if (id >= M_TABLE_BASE && id < M_TABLE_BASE + 100) {
            int t = id - M_TABLE_BASE;
            if (t < shownTables.size()) {
                Online.Table tb = shownTables.get(t);
                hostingTable = false;
                watching = false;
                online.unwatchTables();
                dropOldGame();
                showWait("Masaya katılınıyor", "", tb.name + " masasına bağlanılıyor…");
                online.joinRoom(tb.room);
            }
            return;
        }
        if (id >= M_FRIEND_BASE && id < M_FRIEND_BASE + 100) {
            int i = id - M_FRIEND_BASE;
            if (i < profile.friends.size()) {
                showFriend(profile.friends.get(i));
            }
            return;
        }
        if (id >= M_NG_BASE && id < M_NG_BASE + 200) {
            int v = id - M_NG_BASE;
            prefs.edit().putInt("ng" + ntGame, v).apply();
            if (ntGame == NG_101) {
                showScreen(S_NTG);
                return;
            }
            startSalon(ntGame == NG_OKEY ? SalonBase.G_OKEY : (ntGame == NG_101 ? SalonBase.G_101
                    : (ntGame == NG_BATAKA ? SalonBase.G_BATAKA : SalonBase.G_BATAK)), v);
            return;
        }
        if (ntGame == NG_101 && screen == S_NTG && (id == M_OPT_ESLI || id == M_OPT_KAT || id == M_OPT_CEZA)) {
            String k = id == M_OPT_ESLI ? "o101e" : (id == M_OPT_KAT ? "o101k" : "o101c");
            prefs.edit().putBoolean(k, !prefs.getBoolean(k, false)).apply();
            showScreen(S_NTG);
            return;
        }
        if (id == M_NG_GO && ntGame == NG_101 && sintent == 0) {
            startSalon(SalonBase.G_101, value101());
            return;
        }
        switch (id) {
            case M_RESUME:
                screen = S_NONE;
                view.hideMenu();
                break;
            case TahtaView.CORNER_PROFILE:
                showProfile();
                break;
            case TahtaView.CORNER_SETTINGS:
                showScreen(S_SETTINGS);
                break;
            case M_CAT_ZAR:
                showScreen(S_ZAR);
                break;
            case M_CAT_TAS:
                showScreen(S_TAS);
                break;
            case M_CAT_KAGIT:
                showScreen(S_KAGIT);
                break;
            case M_OKEY:
                ntGame = NG_OKEY;
                sgame = SalonBase.G_OKEY;
                showScreen(S_SGAME);
                break;
            case M_OKEY101:
                ntGame = NG_101;
                sgame = SalonBase.G_101;
                showScreen(S_SGAME);
                break;
            case M_BATAK:
                ntGame = NG_BATAK;
                sgame = SalonBase.G_BATAK;
                showScreen(S_SGAME);
                break;
            case M_BATAKA:
                ntGame = NG_BATAKA;
                sgame = SalonBase.G_BATAKA;
                showScreen(S_SGAME);
                break;
            case M_KING:
                sgame = SalonBase.G_KING;
                showScreen(S_SGAME);
                break;
            case M_SAI:
                salonFlow = false;
                sintent = 0;
                if (sgame == SalonBase.G_KING) {
                    startSalon(SalonBase.G_KING, 20);
                } else {
                    showScreen(S_NTG);
                }
                break;
            case M_SONLINE:
                salonFlow = true;
                sintent = 0;
                showScreen(S_ONLINE);
                break;
            case M_PLAKOTO:
                selVariant = Board.PLAKOTO;
                showScreen(S_GAME);
                break;
            case M_CLASSIC:
                selVariant = Board.CLASSIC;
                showScreen(S_GAME);
                break;
            case M_AI:
                // Zorluk seçimi yok: bilgisayar her zaman en güçlü seviyede oynar
                pendingAiLevel = Ai.HARD;
                pendingTable = 3;
                showScreen(S_TARGET);
                break;
            case TahtaView.EXIT_GAME: {
                boolean onl = view.game.mode == Game.MODE_ONLINE;
                List<TahtaView.MenuItem> cl = new ArrayList<TahtaView.MenuItem>();
                add(cl, M_EXIT_NO, "HAYIR, OYNAMAYA DEVAM", true);
                add(cl, M_EXIT_YES, "EVET, ÇIK", false);
                showPanelScreen(S_CONFIRM, "OYUNDAN ÇIKILSIN MI?", "",
                        onl ? "Oyun sürerken çıkarsan oyunu kaybedersin, rakibin kazanır." : "Bu oyun yarım kalır ve ana ekrana dönersin.", cl);
                break;
            }
            case M_EXIT_NO:
                screen = S_NONE;
                view.hideMenu();
                break;
            case M_EXIT_YES:
                if (view.game.mode == Game.MODE_ONLINE) {
                    view.game.quitOnline();
                }
                gameStarted = false;
                showScreen(S_MAIN);
                break;
            case M_AUTO:
            case TahtaView.TOGGLE_AUTO:
                view.game.autoCollect = !view.game.autoCollect;
                prefs.edit().putString("auto", view.game.autoCollect ? "1" : "0").apply();
                if (id == M_AUTO) {
                    showScreen(S_SETTINGS);
                } else {
                    Toast.makeText(this, view.game.autoCollect ? "Otomatik toplama açık" : "Otomatik toplama kapalı",
                            Toast.LENGTH_SHORT).show();
                }
                break;
            case M_ONLINE:
                online.variant = selVariant;
                online.target = target;
                showScreen(S_ONLINE);
                break;
            case M_SETTINGS:
                showScreen(S_SETTINGS);
                break;
            case M_ACCOUNT:
                showScreen(S_ACCOUNT);
                break;
            case M_BACK:
                if (screen == S_RULESMENU) {
                    showScreen(S_SETTINGS);
                    break;
                }
                goBack();
                break;
            case M_OPEN_TABLE:
                pendingTable = 0;
                showScreen(S_TARGET);
                break;
            case M_TG_BASE + 1:
            case M_TG_BASE + 3:
            case M_TG_BASE + 5:
                target = id - M_TG_BASE;
                prefs.edit().putString("target", String.valueOf(target)).apply();
                dropOldGame();
                online.variant = selVariant;
                online.target = target;
                if (pendingTable == 3) {
                    dropOldGame();
                    view.game.setVariant(selVariant);
                    view.game.target = target;
                    view.game.setMode(Game.MODE_AI, pendingAiLevel);
                    startGame();
                } else if (pendingTable == 2 && curFriend != null) {
                    showWait("Davet gönderiliyor", "", curFriend.name + " kişisine davet gidiyor…\n"
                            + "(Arkadaşın şu an uygulamayı açıksa davet ona ulaşır.)");
                    inviteFriendId = curFriend.id;
                    online.createTable(false);
                } else {
                    showWait("Masa açılıyor", "", "Bağlanılıyor…");
                    online.createTable(true);
                }
                break;
            case M_QUIT_APP: {
                List<TahtaView.MenuItem> cl = new ArrayList<TahtaView.MenuItem>();
                add(cl, M_QUIT_NO, "HAYIR, KAL", true);
                add(cl, M_QUIT_YES, "EVET, ÇIK", false);
                showPanelScreen(S_CONFIRM, "UYGULAMADAN ÇIKILSIN MI?", "",
                        gameStarted ? "Yarım kalan oyun kapanır." : "Uygulama kapanacak.", cl);
                break;
            }
            case M_PIECE:
                view.setPieceStyle((view.getPieceStyle() + 1) % Gfx.PIECE_STYLES.length);
                prefs.edit().putString("pstyle", String.valueOf(view.getPieceStyle())).apply();
                showScreen(S_SETTINGS);
                break;
            case M_DICE:
                view.setDiceStyle((view.getDiceStyle() + 1) % TahtaView.DICE_STYLES.length);
                prefs.edit().putString("dstyle", String.valueOf(view.getDiceStyle())).apply();
                showScreen(S_SETTINGS);
                break;
            case M_CHAT:
            case TahtaView.TOGGLE_CHAT:
                view.game.chatEnabled = !view.game.chatEnabled;
                prefs.edit().putString("chat", view.game.chatEnabled ? "1" : "0").apply();
                if (!view.game.chatEnabled) {
                    view.game.chatUnread = 0;
                }
                if (id == M_CHAT) {
                    showScreen(S_SETTINGS);
                } else {
                    view.invalidate();
                }
                break;
            case M_QUIT_NO:
                showScreen(S_MAIN);
                break;
            case M_QUIT_YES:
                finish();
                break;
            case M_TABLES:
                hostingTable = false;
                watching = true;
                view.game.tables = new ArrayList<Online.Table>();
                showScreen(S_TABLES);
                online.watchTables();
                break;
            case M_FIND:
                dropOldGame();
                showWait("Rakip aranıyor", "", "Seninle aynı anda rakip arayan biri bulunursa oyun başlar…");
                online.findMatch();
                break;
            case M_RENAME_ME:
                showKeypad(K_NAME, "ADINI YAZ", CHARS_NAME, 1, 14, true, profile.name, "KAYDET");
                break;
            case M_SHARE_CODE:
                share("Kodumu paylaş", "Hapis Tavla'da arkadaşım ol! Oyuncu kodum: " + profile.displayId()
                        + "  (Uygulamada sağ üstte profil simgesi > Arkadaşlar > Arkadaş ekle)");
                break;
            case M_FRIENDS:
                friendPage = 0;
                showScreen(S_FRIENDS);
                break;
            case M_MORE_FRIENDS:
                friendPage++;
                showScreen(S_FRIENDS);
                break;
            case M_ADD_FRIEND:
                showKeypad(K_FRIEND, "ARKADAŞININ KODU", CHARS_HEX, 8, 9, false, "", "EKLE");
                break;
            case M_FRIEND_INVITE:
                if (curFriend != null) {
                    if (view.game.mode == Game.MODE_ONLINE) {
                        Toast.makeText(this, "Önce çevrimiçi oyundan çık", Toast.LENGTH_SHORT).show();
                    } else {
                        pendingTable = 2;
                        showScreen(S_TARGET);
                    }
                }
                break;
            case M_FRIEND_RENAME:
                if (curFriend != null) {
                    showKeypad(K_RENAME, "YENİ AD", CHARS_NAME, 1, 14, true, curFriend.name, "KAYDET");
                }
                break;
            case M_FRIEND_DELETE:
                if (curFriend != null) {
                    profile.removeFriend(curFriend.id);
                }
                showScreen(S_FRIENDS);
                break;
            case M_THEME:
                showScreen(S_THEMES);
                break;
            case M_SOUND:
                sfx.setEnabled(!sfx.isEnabled());
                prefs.edit().putString("sound", sfx.isEnabled() ? "1" : "0").apply();
                showScreen(S_SETTINGS);
                break;
            case M_RULES:
                rulesFromSettings = screen == S_SETTINGS;
                rulesFromGame = rulesFromSettings;
                rulesGame = -1;
                if (rulesFromSettings) {
                    showScreen(S_RULESMENU);
                } else {
                    showRules();
                }
                break;
            case M_LEAVE:
                onMenuItem(TahtaView.EXIT_GAME);
                break;
            case M_ADDOPP: {
                int res = profile.addFriend(view.game.oppCode, view.game.oppName);
                Toast.makeText(this, res == Profile.FRIEND_OK ? "Arkadaş eklendi" : "Eklenemedi",
                        Toast.LENGTH_SHORT).show();
                showScreen(S_MAIN);
                break;
            }
            case M_GOOGLE:
                startLogin(Cloud.GOOGLE);
                break;
            case M_FACEBOOK:
                startLogin(Cloud.FACEBOOK);
                break;
            case M_SYNC:
                syncNow(true);
                break;
            case M_LOGOUT:
                cloud.logout();
                Toast.makeText(this, "Çıkış yapıldı. İlerlemen bu telefonda duruyor.", Toast.LENGTH_LONG).show();
                showScreen(S_ACCOUNT);
                break;
            case M_INSTA:
                share("Davet et", "Hapis Tavla oynayalım! Oyuncu kodum: " + profile.displayId()
                        + "  (Uygulamada sağ üstte profil simgesi > Arkadaşlar > Arkadaş ekle)");
                break;
            case M_INFO:
                if (!cloud.config().anyReady() && !cloud.loggedIn()) {
                    showMessage("Giriş henüz kurulmadı",
                            "Google/Facebook girişi için oyunun yapımcısının bir kerelik ayar yapması gerekir "
                            + "(KURULUM_GIRIS.md dosyasında anlatılıyor). O zamana kadar oyun misafir olarak "
                            + "çalışır; ilerlemen bu telefonda saklanır.", S_ACCOUNT, null);
                }
                break;
            case M_MYTABLE:
                break;
            case M_CANCEL_WAIT:
                cancelWait();
                break;
            case M_MSG_OK:
                runMsgOk();
                break;
            case M_ACCEPT:
                if (inviteSalonGame > 0) {
                    sgame = inviteSalonGame;
                    salonFlow = true;
                    sintent = 0;
                    dropOldGame();
                    snet.setName(profile.name);
                    snet.setLevel(profile.level());
                    showWait("Masaya oturuluyor", "", "Davet kabul edildi, bağlanılıyor…");
                    snet.join(inviteRoom, sgame);
                    break;
                }
                online.variant = inviteVariant;
                selVariant = inviteVariant;
                dropOldGame();
                showWait("Odaya katılınıyor", "", "Davet kabul edildi, bağlanılıyor…");
                online.joinRoom(inviteRoom);
                break;
            case M_REJECT:
                rejectInvite();
                break;
            case M_OPEN_URL:
                if (loginCode != null) {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(loginCode.url)));
                    } catch (RuntimeException e) {
                        Toast.makeText(this, "Tarayıcı açılamadı; adresi elle gir.", Toast.LENGTH_LONG).show();
                    }
                }
                break;
            default:
                break;
        }
    }

    // ------------------------------------------------------------------
    // Çevrimiçi olaylar
    // ------------------------------------------------------------------

    @Override
    public void onOnlineEvent(int type, String a, String b) {
        if (salon != null && (type == Game.EV_INVITE || type == Game.EV_MATCHED)) {
            return;
        }
        switch (type) {
            case Game.EV_ROOM:
                if (!inviteFriendId.isEmpty()) {
                    online.inviteFriend(inviteFriendId);
                    inviteFriendId = "";
                }
                if (pendingTable == 0) {
                    hostingTable = true;
                    watching = true;
                    view.game.tables = new ArrayList<Online.Table>();
                    showScreen(S_TABLES);
                    online.watchTables();
                } else {
                    showWait("Masa hazır", "", "Rakip bekleniyor… Masan açık masalar listesinde görünür.");
                }
                break;
            case Game.EV_STATUS:
                if (screen == S_WAIT && a != null && a.length() > 0 && !a.startsWith("Oda kodun")) {
                    showWait(waitTitle, waitBig, a);
                }
                break;
            case Game.EV_TABLES:
                if (screen == S_TABLES) {
                    showScreen(S_TABLES);
                }
                break;
            case Game.EV_MATCHED:
                hostingTable = false;
                if (watching) {
                    watching = false;
                    online.unwatchTables();
                }
                gameStarted = true;
                screen = S_NONE;
                view.hideMenu();
                Toast.makeText(this, "Oyun başladı! Rakip: " + a, Toast.LENGTH_LONG).show();
                break;
            case Game.EV_ERROR:
                hostingTable = false;
                showMessage("Çevrimiçi", a, S_ONLINE, null);
                break;
            case Game.EV_LEFT:
                showMessage("Çevrimiçi", a, S_MAIN, new Runnable() {
                    @Override
                    public void run() {
                        view.game.exitOnline();
                        showScreen(S_MAIN);
                    }
                });
                break;
            case Game.EV_INVITE:
                showInvite(a, b);
                break;
            case Game.EV_EMOTE:
                Toast.makeText(this, b + ": " + a, Toast.LENGTH_SHORT).show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onChatInput() {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setSingleLine(true);
        et.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(100)});
        et.setHint("Mesajını yaz");
        et.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEND);
        final android.app.AlertDialog dlg = new android.app.AlertDialog.Builder(this)
                .setTitle("Mesaj yaz")
                .setView(et)
                .setPositiveButton("GÖNDER", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        view.game.sendChat(et.getText().toString());
                    }
                })
                .setNegativeButton("VAZGEÇ", null)
                .create();
        et.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, android.view.KeyEvent ev) {
                view.game.sendChat(et.getText().toString());
                dlg.dismiss();
                return true;
            }
        });
        dlg.show();
        et.requestFocus();
        if (dlg.getWindow() != null) {
            dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    private void showInvite(String codeAndName, String room) {
        if (view.game.mode == Game.MODE_ONLINE || screen == S_WAIT || screen == S_INVITE || screen == S_KEYPAD
                || (snet != null && snet.getPhase() != SalonNet.IDLE)) {
            return;
        }
        inviteSalonGame = 0;
        String[] parts = codeAndName.split("\\|", 2);
        String name = parts.length > 1 ? parts[1] : "Bir arkadaşın";
        inviteVariant = Board.PLAKOTO;
        if (room != null && room.indexOf(":S") > 0) {
            inviteSalonGame = room.endsWith("S1") ? SalonBase.G_BATAK : room.endsWith("S5") ? SalonBase.G_BATAKA : (room.endsWith("S2") ? SalonBase.G_KING
                    : (room.endsWith("S4") ? SalonBase.G_101 : SalonBase.G_OKEY));
            room = room.substring(0, room.indexOf(':'));
        } else if (room != null && room.indexOf(':') > 0) {
            // oda:oyunTürü
            inviteVariant = room.endsWith(":1") ? Board.CLASSIC : Board.PLAKOTO;
            room = room.substring(0, room.indexOf(':'));
        }
        // arkadaş listesinde varsa ve adı hâlâ varsayılansa, davetteki adı öğren
        Profile.Friend known = profile.findFriend(parts[0]);
        if (known != null && "Arkadaş".equals(known.name) && !name.isEmpty()) {
            profile.renameFriend(known.id, name);
        }
        inviteRoom = room;
        inviteReturn = view.isMenuOpen() ? screen : S_NONE;
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_ACCEPT, "KATIL", true);
        add(l, M_REJECT, "REDDET", false);
        showPanelScreen(S_INVITE, "OYUN DAVETİ", name.toUpperCase(), name + " seni "
                + (inviteSalonGame > 0 ? salonTitle(inviteSalonGame) : variantName(inviteVariant))
                + " oynamaya davet ediyor.", l);
    }

    @Override
    public String onGameOver(Game.Result r) {
        profile.addPins(r.pins);
        String big = r.maxCombo >= 10 ? profile.bigMove(r.maxCombo) : null;
        boolean onl = r.mode == Game.MODE_ONLINE;
        Profile.Reward rw = profile.recordGame(r.iWon, r.tie, r.points, onl, r.iWon && r.mother);
        online.setLevel(profile.level());
        String t = rw.text();
        if (big != null) {
            t += "\nYeni başarım: " + big;
        }
        uploadQuiet();
        showInterstitial();
        return t;
    }

    // ------------------------------------------------------------------
    // Hesap: giriş ve bulut eşitleme
    // ------------------------------------------------------------------

    private void toastUi(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void cancelLogin() {
        if (loginCancel != null) {
            loginCancel.cancelled = true;
        }
        showScreen(S_ACCOUNT);
    }

    private void startLogin(final String prov) {
        if (loginCancel != null) {
            loginCancel.cancelled = true;
        }
        final Cloud.Cancel cancel = new Cloud.Cancel();
        loginCancel = cancel;
        Toast.makeText(this, "Bağlanılıyor…", Toast.LENGTH_SHORT).show();
        net.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Cloud.DeviceCode dc = cloud.beginLogin(prov);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!cancel.cancelled) {
                                showLoginCode(dc);
                            }
                        }
                    });
                    cloud.finishLogin(dc, cancel);
                    final boolean ch = cloud.sync(profile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (ch) {
                                rebuildOnline();
                            }
                            Toast.makeText(MainActivity.this, "Giriş yapıldı: " + cloud.displayName
                                    + (ch ? " (oyuncu kodun hesabına göre güncellendi)" : ""), Toast.LENGTH_LONG).show();
                            if (screen == S_LOGIN || screen == S_ACCOUNT) {
                                showScreen(S_ACCOUNT);
                            }
                        }
                    });
                } catch (final java.io.IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!cancel.cancelled) {
                                showMessage("Giriş yapılamadı", String.valueOf(e.getMessage()), S_ACCOUNT, null);
                            }
                        }
                    });
                }
            }
        });
    }

    private void showLoginCode(Cloud.DeviceCode dc) {
        loginCode = dc;
        String name = Cloud.GOOGLE.equals(dc.provider) ? "Google" : "Facebook";
        List<TahtaView.MenuItem> l = new ArrayList<TahtaView.MenuItem>();
        add(l, M_OPEN_URL, "SİTEYİ AÇ", true);
        add(l, M_CANCEL_WAIT, "İPTAL", false);
        showPanelScreen(S_LOGIN, name.toUpperCase() + " İLE GİRİŞ", dc.userCode,
                "1) Telefonunda ya da bilgisayarda şu adresi aç:\n" + dc.url
                + "\n\n2) Yukarıdaki kodu gir.\n\n3) Hesabını onayla. Bu ekran kendiliğinden kapanır.", l);
    }

    private void rebuildOnline() {
        if (view.game.mode == Game.MODE_ONLINE) {
            return;
        }
        online.shutdown();
        if (salon == null && snet != null) {
            snet.shutdown();
            snet = newSalonNet();
        }
        online = new Online(profile.id, profile.name, view.game.onlineListener(), Mqtt.PUBLIC_SERVERS);
        online.setLevel(profile.level());
        view.game.setOnline(online);
        online.target = target;
        view.game.autoCollect = "1".equals(prefs.getString("auto", "0"));
        view.game.chatEnabled = !"0".equals(prefs.getString("chat", "1"));
        online.start();
    }

    /** Arka planda eşitler. manual=true ise sonucu kullanıcıya bildirir. */
    private void syncNow(final boolean manual) {
        if (!cloud.loggedIn()) {
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final boolean ch = cloud.sync(profile);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (ch) {
                                rebuildOnline();
                            }
                            if (manual) {
                                Toast.makeText(MainActivity.this, "Eşitlendi", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
                } catch (java.io.IOException e) {
                    if (manual) {
                        toastUi("Eşitlenemedi: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void uploadQuiet() {
        if (!cloud.loggedIn()) {
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    cloud.upload(profile);
                } catch (java.io.IOException e) {
                    // çevrimdışı olabilir; bir sonraki eşitlemede yüklenir
                }
            }
        });
    }

    /** Arkadaş eklenince herkese açık karttan gerçek adını getirir. */
    private void lookupFriend(final String code) {
        if (!cloud.config().firebaseReady()) {
            return;
        }
        net.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Map<String, Object> m = cloud.lookup(code);
                    if (m != null) {
                        final String n = Json.str(m, "name", "");
                        if (!n.isEmpty()) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    profile.renameFriend(code, n);
                                }
                            });
                        }
                    }
                } catch (java.io.IOException e) {
                    // bulunamadıysa "Arkadaş" adıyla kalır
                }
            }
        });
    }

    // ------------------------------------------------------------------
    // Tam sayfa reklam (oyun bitince)
    // ------------------------------------------------------------------

    private void loadInterstitial() {
        if (interstitial != null || interstitialLoading) {
            return;
        }
        interstitialLoading = true;
        InterstitialAd.load(this, getString(R.string.admob_interstitial_unit_id), new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(InterstitialAd ad) {
                        interstitialLoading = false;
                        interstitial = ad;
                    }

                    @Override
                    public void onAdFailedToLoad(LoadAdError error) {
                        interstitialLoading = false;
                        interstitial = null;
                    }
                });
    }

    /** Oyun bitince tam sayfa reklam gösterir (en çok 2 dakikada bir; hazır değilse sessizce atlar). */
    private void showInterstitial() {
        long now = System.currentTimeMillis();
        if (interstitial == null || now - lastInterstitialMs < 120000L || isFinishing()) {
            loadInterstitial();
            return;
        }
        InterstitialAd ad = interstitial;
        interstitial = null;
        lastInterstitialMs = now;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                hideSystemUi();
                loadInterstitial();
            }

            @Override
            public void onAdFailedToShowFullScreenContent(com.google.android.gms.ads.AdError e) {
                loadInterstitial();
            }
        });
        ad.show(this);
    }

    // ------------------------------------------------------------------
    // Yaşam döngüsü
    // ------------------------------------------------------------------

    @Override
    public void onPause() {
        SalonBase.appPaused = true;
        Game.appPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        SalonBase.appPaused = false;
        Game.appPaused = false;
    }

    @Override
    public void onDestroy() {
        if (online != null) {
            online.shutdown();
        }
        if (snet != null) {
            snet.shutdown();
        }
        if (sfx != null) {
            sfx.release();
        }
        if (loginCancel != null) {
            loginCancel.cancelled = true;
        }
        net.shutdown();
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemUi() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
