package com.hapistavla.oyun.king;

import com.hapistavla.oyun.*;
import com.hapistavla.oyun.salon.CardArt;
import com.hapistavla.oyun.salon.Cards;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * King (Rıfkı), 4 kişi, 20 el. Oyuncu 0 insandır; sıra 0 → 1 → 2 → 3.
 * Her oyuncu 5 kez dağıtır: en çok 3 ceza + 2 koz oyunu seçer (her oyun bir oyuncu için bir kez).
 *
 * Ceza oyunları: Kupa almaz (-30 kupa başı), Erkek almaz (-60 papaz/vale başı), Kız almaz (-100 kız başı),
 * Rıfkı (-320 kupa papazı), El almaz (-50 el başı), Son iki (-180 son iki elin her biri).
 * Koz oyunu: koz seçilir, alınan el başı +50.
 */
public final class King {
    public static final int KUPA = 0;
    public static final int ERKEK = 1;
    public static final int KIZ = 2;
    public static final int RIFKI = 3;
    public static final int ELALMAZ = 4;
    public static final int SONIKI = 5;
    public static final int KOZ0 = 6;           // 6..9: koz (kart takımı numarası + 6)

    public static final int CHOOSE = 0;
    public static final int PLAY = 1;
    public static final int EL_OVER = 2;
    public static final int MATCH_OVER = 3;

    public static final int ELS = 20;
    public static final int MAX_CEZA = 3;
    public static final int MAX_KOZ = 2;

    public static final String[] NAMES = {"KUPA ALMAZ", "ERKEK ALMAZ", "KIZ ALMAZ", "RIFKI", "EL ALMAZ", "SON İKİ",
        "KOZ MAÇA", "KOZ KUPA", "KOZ KARO", "KOZ SİNEK"};
    public static final String[] SHORT = {"Kupa başı -30", "Papaz/vale başı -60", "Kız başı -100", "Kupa papazı -320",
        "El başı -50", "Son iki el -180", "El başı +50", "El başı +50", "El başı +50", "El başı +50"};
    public static final int[] UNIT = {30, 60, 100, 320, 50, 180, 50, 50, 50, 50};

    public final Random rnd;
    /** Bot kararları için ayrı rastgelelik: dağıtım tohumla aynı kalır, botlar sadece ev sahibinde çalışır. */
    public final Random brnd = new Random();
    public final int[] score = new int[4];
    public final int[][] history = new int[ELS][4];
    public final int[] gameOf = new int[ELS];
    public final boolean[][] used = new boolean[4][10];
    public final int[] cezaUsed = new int[4];
    public final int[] kozUsed = new int[4];
    /** Her oyun türü masada en çok 2 kez seçilir (6 ceza x 2 + 4 koz x 2 = 20 el). */
    public final int[] typeUsed = new int[10];
    public static final int MAX_PER_TYPE = 2;

    public int elNo = 0;                 // oynanan el sayısı (1'den başlar)
    public int chooser;
    public int phase = EL_OVER;
    public int game = -1;
    public int trump = -1;
    public int turn;
    public int[][] hand = new int[4][];
    public final int[] trick = new int[4];
    public int trickLeader;
    public int trickCount;
    public int trickNo;
    public final int[] tricksWon = new int[4];
    public final boolean[] lastTwo = new boolean[4];
    public final List<Integer>[] taken;
    public final boolean[] played = new boolean[64];
    public final int[] elDelta = new int[4];
    public int lastWinner = -1;
    public int winner = -1;

    @SuppressWarnings("unchecked")
    public King(Random rnd) {
        this.rnd = rnd;
        taken = new List[4];
        for (int i = 0; i < 4; i++) {
            taken[i] = new ArrayList<Integer>();
        }
        chooser = rnd.nextInt(4);
    }

    public static boolean isKoz(int g) {
        return g >= KOZ0;
    }

    public void newEl() {
        if (elNo > 0) {
            chooser = (chooser + 1) & 3;
        }
        elNo++;
        List<Integer> d = Cards.deck(rnd);
        for (int p = 0; p < 4; p++) {
            hand[p] = new int[13];
            for (int i = 0; i < 13; i++) {
                hand[p][i] = d.get(p * 13 + i);
            }
            Cards.sort(hand[p]);
            taken[p].clear();
            tricksWon[p] = 0;
            lastTwo[p] = false;
            elDelta[p] = 0;
            trick[p] = -1;
        }
        java.util.Arrays.fill(played, false);
        game = -1;
        trump = -1;
        trickCount = 0;
        trickNo = 0;
        lastWinner = -1;
        turn = chooser;
        phase = CHOOSE;
    }

    /** p oyuncusu g oyununu seçebilir mi. */
    public boolean canChoose(int p, int g) {
        if (used[p][g] || typeUsed[g] >= MAX_PER_TYPE) {
            return false;
        }
        int left = 5 - cezaUsed[p] - kozUsed[p];
        int needCeza = MAX_CEZA - cezaUsed[p];
        int needKoz = MAX_KOZ - kozUsed[p];
        boolean ok;
        if (isKoz(g)) {
            ok = kozUsed[p] < MAX_KOZ && left > 0;
        } else {
            ok = cezaUsed[p] < MAX_CEZA && left > 0 && needCeza + needKoz == left;
        }
        if (!ok) {
            return false;
        }
        // bu seçimden sonra herkesin kalan seçimlerini tamamlamak hâlâ mümkün olmalı
        used[p][g] = true;
        typeUsed[g]++;
        if (isKoz(g)) {
            kozUsed[p]++;
        } else {
            cezaUsed[p]++;
        }
        boolean feas = feasible(0, 6, false) && feasible(6, 10, true);
        used[p][g] = false;
        typeUsed[g]--;
        if (isKoz(g)) {
            kozUsed[p]--;
        } else {
            cezaUsed[p]--;
        }
        return feas;
    }

    /** g0..g1 arası oyun türleri için kalan seçimler tüm oyuncularda tamamlanabilir mi (akış hesabı). */
    private boolean feasible(int g0, int g1, boolean koz) {
        int n = g1 - g0;
        int nn = 2 + 4 + n;
        int[][] cap = new int[nn][nn];
        int need = 0;
        for (int p = 0; p < 4; p++) {
            int d = koz ? MAX_KOZ - kozUsed[p] : MAX_CEZA - cezaUsed[p];
            cap[0][1 + p] = d;
            need += d;
            for (int t = 0; t < n; t++) {
                if (!used[p][g0 + t]) {
                    cap[1 + p][5 + t] = 1;
                }
            }
        }
        for (int t = 0; t < n; t++) {
            cap[5 + t][nn - 1] = MAX_PER_TYPE - typeUsed[g0 + t];
        }
        int flow = 0;
        while (true) {
            int[] prev = new int[nn];
            java.util.Arrays.fill(prev, -1);
            prev[0] = 0;
            java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<Integer>();
            q.add(0);
            while (!q.isEmpty() && prev[nn - 1] < 0) {
                int u = q.poll();
                for (int v = 0; v < nn; v++) {
                    if (prev[v] < 0 && cap[u][v] > 0) {
                        prev[v] = u;
                        q.add(v);
                    }
                }
            }
            if (prev[nn - 1] < 0) {
                break;
            }
            for (int v = nn - 1; v != 0; v = prev[v]) {
                cap[prev[v]][v]--;
                cap[v][prev[v]]++;
            }
            flow++;
        }
        return flow == need;
    }

    public boolean choose(int g) {
        if (phase != CHOOSE || g < 0 || g > 9 || !canChoose(chooser, g)) {
            return false;
        }
        used[chooser][g] = true;
        typeUsed[g]++;
        if (isKoz(g)) {
            kozUsed[chooser]++;
            trump = g - KOZ0;
        } else {
            cezaUsed[chooser]++;
        }
        game = g;
        gameOf[elNo - 1] = g;
        phase = PLAY;
        trickLeader = chooser;
        turn = chooser;
        trickCount = 0;
        for (int i = 0; i < 4; i++) {
            trick[i] = -1;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Oyun
    // ------------------------------------------------------------------

    public int[] legal(int p) {
        int[] h = hand[p];
        if (trickCount == 0) {
            if ((game == KUPA || game == RIFKI) && !heartsPlayed()) {
                // kupa açılmadan kupa ile başlanamaz (elinde yalnız kupa varsa serbest)
                int n = 0;
                for (int c : h) {
                    if (Cards.suit(c) != CardArt.HEARTS) {
                        n++;
                    }
                }
                if (n > 0 && n < h.length) {
                    int[] r = new int[n];
                    int k = 0;
                    for (int c : h) {
                        if (Cards.suit(c) != CardArt.HEARTS) {
                            r[k++] = c;
                        }
                    }
                    return r;
                }
            }
            if (trump >= 0 && !trumpPlayed()) {
                // koz düşmeden koz ile başlanamaz (elinde yalnız koz varsa serbest)
                int n = 0;
                for (int c : h) {
                    if (Cards.suit(c) != trump) {
                        n++;
                    }
                }
                if (n > 0 && n < h.length) {
                    int[] r = new int[n];
                    int k = 0;
                    for (int c : h) {
                        if (Cards.suit(c) != trump) {
                            r[k++] = c;
                        }
                    }
                    return r;
                }
            }
            return h.clone();
        }
        int led = Cards.suit(trick[trickLeader]);
        List<Integer> l = new ArrayList<Integer>();
        for (int c : h) {
            if (Cards.suit(c) == led) {
                l.add(c);
            }
        }
        if (l.isEmpty()) {
            return h.clone();
        }
        if (trump >= 0 && led == trump) {
            // koz çekilmişse masadaki en büyük kozu geçmek zorunlu (geçebiliyorsan)
            int top = 0;
            for (int q = 0; q < 4; q++) {
                int pp = (trickLeader + q) & 3;
                if (q < trickCount && trick[pp] >= 0 && Cards.suit(trick[pp]) == trump) {
                    top = Math.max(top, Cards.rank(trick[pp]));
                }
            }
            List<Integer> higher = new ArrayList<Integer>();
            for (int c : l) {
                if (Cards.rank(c) > top) {
                    higher.add(c);
                }
            }
            if (!higher.isEmpty()) {
                l = higher;
            }
        }
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = l.get(i);
        }
        return r;
    }

    /** Bu elde şimdiye kadar koz oynandı mı. */
    public boolean trumpPlayed() {
        for (int c = 0; c < played.length; c++) {
            if (played[c] && trump >= 0 && Cards.suit(c) == trump) {
                return true;
            }
        }
        return false;
    }

    /** Bu elde şimdiye kadar herhangi bir kupa oynandı mı. */
    public boolean heartsPlayed() {
        for (int c = 0; c < played.length; c++) {
            if (played[c] && Cards.suit(c) == CardArt.HEARTS) {
                return true;
            }
        }
        return false;
    }

    public boolean isLegal(int p, int card) {
        return Cards.has(legal(p), card);
    }

    public boolean play(int card) {
        if (phase != PLAY || trickCount >= 4 || !isLegal(turn, card)) {
            return false;
        }
        int p = turn;
        hand[p] = Cards.remove(hand[p], card);
        trick[p] = card;
        played[card] = true;
        trickCount++;
        if (trickCount < 4) {
            turn = (p + 1) & 3;
        }
        return true;
    }

    public boolean trickComplete() {
        return phase == PLAY && trickCount == 4;
    }

    public int trickWinner() {
        int led = Cards.suit(trick[trickLeader]);
        int best = trickLeader;
        for (int k = 1; k < 4; k++) {
            int p = (trickLeader + k) & 3;
            int c = trick[p];
            int b = trick[best];
            boolean cTr = trump >= 0 && Cards.suit(c) == trump;
            boolean bTr = trump >= 0 && Cards.suit(b) == trump;
            if (cTr && !bTr) {
                best = p;
            } else if (cTr == bTr && Cards.suit(c) == Cards.suit(b) && Cards.rank(c) > Cards.rank(b)) {
                best = p;
            } else if (!cTr && !bTr && Cards.suit(b) != led && Cards.suit(c) == led) {
                best = p;
            }
        }
        return best;
    }

    public int collectTrick() {
        if (!trickComplete()) {
            return -1;
        }
        int w = trickWinner();
        tricksWon[w]++;
        for (int i = 0; i < 4; i++) {
            taken[w].add(trick[i]);
        }
        if (trickNo >= 11) {
            lastTwo[w] = true;
            lastTwoCount[w]++;
        }
        lastWinner = w;
        trickNo++;
        for (int i = 0; i < 4; i++) {
            trick[i] = -1;
        }
        trickCount = 0;
        trickLeader = w;
        turn = w;
        if (trickNo == 13) {
            finishEl();
        }
        return w;
    }

    private final int[] lastTwoCount = new int[4];

    private void finishEl() {
        for (int p = 0; p < 4; p++) {
            int d = 0;
            switch (game) {
                case KUPA:
                    for (int c : taken[p]) {
                        if (Cards.suit(c) == CardArt.HEARTS) {
                            d -= 30;
                        }
                    }
                    break;
                case ERKEK:
                    for (int c : taken[p]) {
                        if (Cards.rank(c) == 11 || Cards.rank(c) == 13) {
                            d -= 60;
                        }
                    }
                    break;
                case KIZ:
                    for (int c : taken[p]) {
                        if (Cards.rank(c) == 12) {
                            d -= 100;
                        }
                    }
                    break;
                case RIFKI:
                    for (int c : taken[p]) {
                        if (Cards.suit(c) == CardArt.HEARTS && Cards.rank(c) == 13) {
                            d -= 320;
                        }
                    }
                    break;
                case ELALMAZ:
                    d -= 50 * tricksWon[p];
                    break;
                case SONIKI:
                    d -= 180 * lastTwoCount[p];
                    break;
                default:
                    d += 50 * tricksWon[p];
                    break;
            }
            elDelta[p] = d;
            score[p] += d;
            history[elNo - 1][p] = d;
            lastTwoCount[p] = 0;
        }
        phase = EL_OVER;
        if (elNo >= ELS) {
            int best = 0;
            for (int p = 1; p < 4; p++) {
                if (score[p] > score[best]) {
                    best = p;
                }
            }
            winner = best;
            phase = MATCH_OVER;
        }
    }

    // ------------------------------------------------------------------
    // Bilgisayar
    // ------------------------------------------------------------------

    private static int count(int[] h, int suit) {
        return Cards.countSuit(h, suit);
    }

    /** Bilgisayarın oyun seçimi. */
    public int botChoose(int p) {
        int[] h = hand[p];
        int left = 5 - cezaUsed[p] - kozUsed[p];
        int needKoz = MAX_KOZ - kozUsed[p];
        // en iyi koz
        int bestKoz = -1;
        double bestPot = -1;
        for (int s = 0; s < 4; s++) {
            if (!canChoose(p, KOZ0 + s)) {
                continue;
            }
            int n = count(h, s);
            double pot = n * 1.6;
            for (int c : h) {
                if (Cards.suit(c) == s && Cards.rank(c) >= 11) {
                    pot += (Cards.rank(c) - 9) * 0.8;
                }
            }
            for (int c : h) {
                if (Cards.suit(c) != s && Cards.rank(c) == 14) {
                    pot += 1.2;
                }
            }
            if (pot > bestPot) {
                bestPot = pot;
                bestKoz = KOZ0 + s;
            }
        }
        // en güvenli ceza
        int bestCeza = -1;
        double bestLoss = Double.MAX_VALUE;
        for (int g = 0; g < 6; g++) {
            if (!canChoose(p, g)) {
                continue;
            }
            double loss = estimateLoss(h, g) * (0.55 + brnd.nextDouble() * 0.9);
            if (g == ELALMAZ) {
                loss *= 0.55;
            } else if (g == SONIKI) {
                loss *= 0.8;
            } else if (g == RIFKI) {
                loss *= 1.5;
            }
            if (loss < bestLoss) {
                bestLoss = loss;
                bestCeza = g;
            }
        }
        if (bestKoz < 0) {
            return bestCeza;
        }
        if (bestCeza < 0) {
            return bestKoz;
        }
        if (needKoz == left) {
            return bestKoz;
        }
        // koz iyi ise koz, ceza çok güvenliyse ceza
        if (bestPot >= 17 || bestLoss > 140) {
            return bestKoz;
        }
        return bestCeza;
    }

    private static double estimateLoss(int[] h, int g) {
        double risk = 0;
        switch (g) {
            case KUPA:
                for (int c : h) {
                    if (Cards.suit(c) == CardArt.HEARTS) {
                        int r = Cards.rank(c);
                        risk += r >= 11 ? 2.4 : (r >= 8 ? 1.0 : 0.25);
                    }
                }
                return risk * 30;
            case ERKEK:
                for (int c : h) {
                    int r = Cards.rank(c);
                    if (r == 13 || r == 11) {
                        risk += 0.9 + (count(h, Cards.suit(c)) <= 2 ? 0.8 : 0);
                    }
                    if (r == 14) {
                        risk += 0.3;
                    }
                }
                return risk * 60;
            case KIZ:
                for (int c : h) {
                    int r = Cards.rank(c);
                    if (r == 12) {
                        risk += 1.0 + (count(h, Cards.suit(c)) <= 2 ? 1.0 : 0);
                    }
                    if (r == 13 || r == 14) {
                        risk += 0.15;
                    }
                }
                return risk * 100;
            case RIFKI:
                for (int c : h) {
                    if (Cards.suit(c) == CardArt.HEARTS) {
                        int r = Cards.rank(c);
                        if (r == 13) {
                            risk += 1.0 + (count(h, CardArt.HEARTS) <= 3 ? 1.5 : 0);
                        } else if (r == 14) {
                            risk -= 0.0;
                        } else if (r <= 6) {
                            risk -= 0.1;
                        }
                    }
                }
                return Math.max(0.6, risk) * 320 * 0.45;
            case ELALMAZ:
                for (int c : h) {
                    int r = Cards.rank(c);
                    risk += r >= 13 ? 1.5 : (r >= 11 ? 0.8 : (r >= 9 ? 0.25 : 0));
                }
                return risk * 50 * 0.9;
            default:
                for (int c : h) {
                    risk += Cards.rank(c) >= 12 ? 0.55 : 0.1;
                }
                return risk * 180 * 0.4;
        }
    }

    private int penaltyOf(int c) {
        switch (game) {
            case KUPA:
                return Cards.suit(c) == CardArt.HEARTS ? 30 : 0;
            case ERKEK:
                return (Cards.rank(c) == 11 || Cards.rank(c) == 13) ? 60 : 0;
            case KIZ:
                return Cards.rank(c) == 12 ? 100 : 0;
            case RIFKI:
                return (Cards.suit(c) == CardArt.HEARTS && Cards.rank(c) == 13) ? 320 : 0;
            default:
                return 0;
        }
    }

    /** Masadaki kartlarla birlikte (c dahil) elin ceza değeri. */
    private int trickPenalty(int c) {
        int sum = penaltyOf(c);
        for (int i = 0; i < 4; i++) {
            if (trick[i] >= 0) {
                sum += penaltyOf(trick[i]);
            }
        }
        if (game == ELALMAZ) {
            sum += 50;
        } else if (game == SONIKI && trickNo >= 11) {
            sum += 180;
        }
        return sum;
    }

    /** c kartı şu anda masadaki en yüksek kart mı (benden sonra oynayacaklar hariç tutulmadan). */
    private boolean beatsTable(int c) {
        if (trickCount == 0) {
            return true;
        }
        int led = Cards.suit(trick[trickLeader]);
        int bestC = -1;
        for (int i = 0; i < 4; i++) {
            int t = trick[i];
            if (t < 0) {
                continue;
            }
            if (bestC < 0) {
                bestC = t;
                continue;
            }
            boolean tTr = trump >= 0 && Cards.suit(t) == trump;
            boolean bTr = trump >= 0 && Cards.suit(bestC) == trump;
            if (tTr && !bTr) {
                bestC = t;
            } else if (tTr == bTr && Cards.suit(t) == Cards.suit(bestC) && Cards.rank(t) > Cards.rank(bestC)) {
                bestC = t;
            } else if (!tTr && !bTr && Cards.suit(bestC) != led && Cards.suit(t) == led) {
                bestC = t;
            }
        }
        boolean cTr = trump >= 0 && Cards.suit(c) == trump;
        boolean bTr = trump >= 0 && Cards.suit(bestC) == trump;
        if (cTr && !bTr) {
            return true;
        }
        if (bTr && !cTr) {
            return false;
        }
        return Cards.suit(c) == Cards.suit(bestC) && Cards.rank(c) > Cards.rank(bestC);
    }

    /** Kartın bu elde kesin kazanıp kazanmayacağı (yalnızca renk üstünlüğüne bakar). */
    private boolean sureWin(int p, int c) {
        if (!beatsTable(c)) {
            return false;
        }
        int s = Cards.suit(c);
        int r = Cards.rank(c);
        int remainingToPlay = 3 - trickCount;
        if (remainingToPlay == 0) {
            return true;
        }
        // benden sonra oynayacak oyuncuların bu renkten daha yüksek kartı olabilir mi
        boolean higherUnseen = false;
        for (int q = 14; q > r; q--) {
            int card = Cards.make(s, q);
            if (!played[card] && !Cards.has(hand[p], card) && !onTable(card)) {
                higherUnseen = true;
            }
        }
        if (higherUnseen) {
            return false;
        }
        // koz oyunu: kozla kesilebilir
        if (trump >= 0 && s != trump) {
            for (int q = 1; q <= remainingToPlay; q++) {
                int o = (p + q) & 3;
                if (hand[o].length > 0 && count(hand[o], s) == 0 && count(hand[o], trump) > 0) {
                    return false;
                }
            }
            // bilmediğimiz için kesin sayma
            return trickCount > 0 ? false : true;
        }
        return true;
    }

    private boolean onTable(int card) {
        for (int i = 0; i < 4; i++) {
            if (trick[i] == card) {
                return true;
            }
        }
        return false;
    }

    /** Bilgisayarın atacağı kart. */
    public int botPlay() {
        int p = turn;
        int[] lg = legal(p);
        if (lg.length == 1) {
            return lg[0];
        }
        boolean koz = isKoz(game);
        int best = lg[0];
        double bestCost = Double.MAX_VALUE;
        for (int c : lg) {
            double cost;
            int rank = Cards.rank(c);
            boolean follows = trickCount == 0 || Cards.suit(c) == Cards.suit(trick[trickLeader]);
            if (koz) {
                boolean win = beatsTable(c);
                boolean sure = sureWin(p, c);
                if (trickCount == 0) {
                    boolean isTr = Cards.suit(c) == trump;
                    // ellerini kazanacağı kartlarla aç
                    cost = sure ? -60 + rank * 0.1 : (isTr ? 8 - rank * 0.8 : 12 + rank * -0.3);
                    if (!sure && rank >= 12) {
                        cost -= 6;
                    }
                } else if (win) {
                    cost = (sure ? -50 : -30) + rank * 0.5 + (Cards.suit(c) == trump ? 4 : 0);
                } else {
                    cost = rank * 0.6 + (Cards.suit(c) == trump ? 6 : 0);
                }
            } else {
                int pen = penaltyOf(c);
                if (trickCount == 0) {
                    boolean sure = sureWin(p, c);
                    double winChance = sure ? 1.0 : Math.max(0.0, (rank - 6) / 9.0) * 0.55;
                    cost = winChance * (UNIT[game] * 0.5 + pen) + pen * 0.4;
                    // uzun rengi az uzatma: elde çok kartı olan rengin alçak kartını aç
                    cost += rank * 0.2;
                    if (game == KUPA && Cards.suit(c) == CardArt.HEARTS) {
                        cost += 8;
                    }
                } else if (beatsTable(c) && (follows)) {
                    boolean last = trickCount == 3;
                    double pw = last ? 1.0 : (sureWin(p, c) ? 1.0 : 0.6);
                    cost = pw * trickPenalty(c) + rank * 0.05;
                } else {
                    // kazanmıyor: ceza kartını rakibe at
                    cost = -pen * 0.5 - rank * (pen > 0 ? 0.5 : 0.15);
                    if (!follows) {
                        cost -= 2;
                    }
                }
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = c;
            }
        }
        return best;
    }
}
