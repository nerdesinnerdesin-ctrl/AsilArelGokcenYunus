package com.hapistavla.oyun.batak;

import com.hapistavla.oyun.*;
import com.hapistavla.oyun.salon.Cards;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Batak (4 kişilik, ihaleli). Oyuncu 0 insandır, 1..3 bilgisayardır; oyun saat yönünün tersine ilerler.
 *
 * Kurallar:
 *  - 52 kart, herkese 13. Dağıtanın solundaki oyuncudan başlayarak ihale yapılır; ihale en az 5
 *    ile başlar (klasik tekli batak). Kimse girmezse ilk sorulan oyuncu 5 ile kalır.
 *  - İhaleyi alan kozu seçer ve ilk eli açar.
 *  - Renge uymak, oynanan kartı geçmek (varsa) ve rengi yoksa koz atmak zorunludur; kozla kesilmişse
 *    üstüne büyük koz atılır (büyüğü yoksa küçük koz atılır).
 *  - İhaleci ihale kadar el alırsa +ihale, alamazsa -ihale puan alır; diğerleri aldıkları el kadar
 *    puan alır. Hedef puana (81 / 101) ilk ulaşan kazanır.
 */
public final class Batak {
    public static final int BID = 0;
    public static final int TRUMP = 1;
    public static final int PLAY = 2;
    public static final int HAND_OVER = 3;
    public static final int MATCH_OVER = 4;

    public static final int MIN_BID = 4;
    public static final int FORCED_BID = 3;
    /** Açmalı (eşli) batak: eşin kartları açılır; ihale en az 8, kimse girmezse 7. */
    public static final int MIN_BID_OPEN = 8;
    public static final int FORCED_BID_OPEN = 7;
    public static final int MAX_BID = 13;

    public final Random rnd;
    public final int target;
    /** Açmalı batak (eşli): karşıdaki oyuncu eştir, ihaleci kozu seçince eşinin kartları açılır ve onları da ihaleci oynar. */
    public final boolean open;

    public int[][] hand = new int[4][];
    public int dealer;
    public int phase = HAND_OVER;
    public int turn;
    public int handNo;

    // ihale
    public final int[] bids = new int[4];          // -1 sorulmadı, 0 pas, >0 teklif
    public final boolean[] out = new boolean[4];   // pas geçenler
    public int highBid;
    public int highBidder = -1;
    public int firstAsked;
    public boolean forced;
    public int declarer = -1;
    public int bid;
    public int trump = -1;

    // el
    public final int[] trick = new int[4];
    public int trickLeader;
    public int trickCount;                          // bu elde atılan kart
    public int trickNo;                             // biten el sayısı
    public final int[] tricksWon = new int[4];
    public int lastWinner = -1;
    public final boolean[] played = new boolean[64];

    // puan
    public final int[] score = new int[4];
    public final int[] handDelta = new int[4];
    public int winner = -1;
    public boolean declarerMade;

    public Batak(int target, Random rnd) {
        this(target, rnd, false);
    }

    public Batak(int target, Random rnd, boolean open) {
        this.target = target;
        this.rnd = rnd;
        this.open = open;
        this.dealer = rnd.nextInt(4);
        this.handNo = 0;
    }

    // ------------------------------------------------------------------
    // Dağıtım ve ihale
    // ------------------------------------------------------------------

    public void newHand() {
        if (handNo > 0) {
            dealer = (dealer + 1) & 3;
        }
        handNo++;
        // El iptal: elinde 10'dan büyük (J, Q, K, A) kart olmayan oyuncu varsa dağıtım iptal edilir, yeniden dağıtılır.
        cancelCount = 0;
        cancelSeat = -1;
        while (true) {
            List<Integer> d = Cards.deck(rnd);
            for (int p = 0; p < 4; p++) {
                hand[p] = new int[13];
                for (int i = 0; i < 13; i++) {
                    hand[p][i] = d.get(p * 13 + i);
                }
                Cards.sort(hand[p]);
            }
            int bad = -1;
            for (int p = 0; p < 4 && bad < 0; p++) {
                if (!hasHigh(hand[p])) {
                    bad = p;
                }
            }
            if (bad < 0 || cancelCount >= 20) {
                break;
            }
            cancelCount++;
            cancelSeat = bad;
        }
        for (int i = 0; i < 4; i++) {
            bids[i] = -1;
            out[i] = false;
            tricksWon[i] = 0;
            trick[i] = -1;
            handDelta[i] = 0;
        }
        java.util.Arrays.fill(played, false);
        highBid = 0;
        highBidder = -1;
        forced = false;
        declarer = -1;
        bid = 0;
        trump = -1;
        trickCount = 0;
        trickNo = 0;
        lastWinner = -1;
        firstAsked = (dealer + 1) & 3;
        turn = firstAsked;
        phase = BID;
    }

    /** Bu dağıtımdan önce kaç kez el iptal edildi; en son hangi oyuncunun elinde 10'dan büyük kart yoktu. */
    public int cancelCount = 0;
    public int cancelSeat = -1;

    public static boolean hasHigh(int[] h) {
        for (int c : h) {
            if (Cards.rank(c) > 10) {
                return true;
            }
        }
        return false;
    }

    /** Verilebilecek en küçük teklif. */
    public int minBid() {
        return highBid == 0 ? minBase() : highBid + 1;
    }

    public int minBase() {
        return open ? MIN_BID_OPEN : MIN_BID;
    }

    public int forcedBid() {
        return open ? FORCED_BID_OPEN : FORCED_BID;
    }

    public static int partner(int p) {
        return (p + 2) & 3;
    }

    /** Açmalı batakta, ihaleci belli olduktan sonra eşi (açık oynanan el); yoksa -1. */
    public int dummy() {
        return open && declarer >= 0 ? partner(declarer) : -1;
    }

    /** Sıradaki kartı kim seçer: açık eşin sırasında ihaleci. */
    public int actor() {
        return phase == PLAY && open && turn == dummy() ? declarer : turn;
    }

    public boolean sameTeam(int a, int b) {
        return a == b || (open && a == partner(b));
    }

    /** Maçı p oyuncusu (ya da takımı) kazandı mı. */
    public boolean won(int p) {
        return winner >= 0 && sameTeam(p, winner);
    }

    public boolean canBid(int value) {
        return phase == BID && (value == 0 || (value >= minBid() && value <= MAX_BID));
    }

    /** turn sırasındaki oyuncu için teklif (0 = pas). Geçerliyse true. */
    public boolean bid(int value) {
        if (!canBid(value)) {
            return false;
        }
        int p = turn;
        bids[p] = value;
        if (value == 0) {
            out[p] = true;
        } else {
            highBid = value;
            highBidder = p;
        }
        // ihale bitti mi?
        int left = 0;
        for (int i = 0; i < 4; i++) {
            if (!out[i]) {
                left++;
            }
        }
        boolean end = false;
        if (highBid == MAX_BID) {
            end = true;
        } else if (highBid > 0 && left == 1) {
            end = true;
        } else if (left == 0) {
            end = true;
        }
        if (end) {
            if (highBid == 0) {
                forced = true;
                declarer = firstAsked;
                bid = forcedBid();
            } else {
                declarer = highBidder;
                bid = highBid;
            }
            phase = TRUMP;
            turn = declarer;
        } else {
            int n = (p + 1) & 3;
            while (out[n]) {
                n = (n + 1) & 3;
            }
            turn = n;
        }
        return true;
    }

    public boolean setTrump(int suit) {
        if (phase != TRUMP || suit < 0 || suit > 3) {
            return false;
        }
        trump = suit;
        phase = PLAY;
        trickLeader = declarer;
        turn = declarer;
        trickCount = 0;
        for (int i = 0; i < 4; i++) {
            trick[i] = -1;
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Oyun
    // ------------------------------------------------------------------

    private int highestOfSuit(int suit) {
        int best = -1;
        for (int i = 0; i < 4; i++) {
            int c = trick[i];
            if (c >= 0 && Cards.suit(c) == suit && (best < 0 || Cards.rank(c) > Cards.rank(best))) {
                best = c;
            }
        }
        return best;
    }

    /** p oyuncusunun atabileceği kartlar. */
    public int[] legal(int p) {
        int[] h = hand[p];
        if (trickCount == 0) {
            return h.clone();
        }
        int led = Cards.suit(trick[trickLeader]);
        List<Integer> same = new ArrayList<Integer>();
        for (int c : h) {
            if (Cards.suit(c) == led) {
                same.add(c);
            }
        }
        int hiTrump = highestOfSuit(trump);
        if (!same.isEmpty()) {
            if (led != trump && hiTrump >= 0) {
                return toArray(same);        // kozla kesilmiş: rengi atmak yeter
            }
            int hi = highestOfSuit(led);
            List<Integer> higher = new ArrayList<Integer>();
            for (int c : same) {
                if (Cards.rank(c) > Cards.rank(hi)) {
                    higher.add(c);
                }
            }
            return toArray(higher.isEmpty() ? same : higher);
        }
        // rengi yok
        List<Integer> trumps = new ArrayList<Integer>();
        for (int c : h) {
            if (Cards.suit(c) == trump) {
                trumps.add(c);
            }
        }
        if (trumps.isEmpty()) {
            return h.clone();
        }
        if (hiTrump >= 0) {
            List<Integer> higher = new ArrayList<Integer>();
            for (int c : trumps) {
                if (Cards.rank(c) > Cards.rank(hiTrump)) {
                    higher.add(c);
                }
            }
            return higher.isEmpty() ? toArray(trumps) : toArray(higher);   // büyük koz yoksa da koz atmak zorunlu
        }
        return toArray(trumps);
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    public boolean isLegal(int p, int card) {
        if (phase != PLAY || p != turn || trickCount >= 4) {
            return false;
        }
        return Cards.has(legal(p), card);
    }

    /** turn oyuncusu kartı oynar. Geçerliyse true. */
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
            boolean cTr = Cards.suit(c) == trump;
            boolean bTr = Cards.suit(b) == trump;
            if (cTr && !bTr) {
                best = p;
            } else if (cTr == bTr && Cards.suit(c) == Cards.suit(b) && Cards.rank(c) > Cards.rank(b)) {
                best = p;
            }
        }
        if (Cards.suit(trick[best]) != led && Cards.suit(trick[best]) != trump) {
            return trickLeader;
        }
        return best;
    }

    /** Biten eli toplar; el bitmişse puanları hesaplar. Eli alan oyuncuyu döndürür. */
    public int collectTrick() {
        if (!trickComplete()) {
            return -1;
        }
        int w = trickWinner();
        tricksWon[w]++;
        lastWinner = w;
        trickNo++;
        for (int i = 0; i < 4; i++) {
            trick[i] = -1;
        }
        trickCount = 0;
        trickLeader = w;
        turn = w;
        if (trickNo == 13) {
            finishHand();
        }
        return w;
    }

    private void finishHand() {
        int dm = dummy();
        declarerMade = (open ? tricksWon[declarer] + tricksWon[dm] : tricksWon[declarer]) >= bid;
        int oppTricks = open ? tricksWon[(declarer + 1) & 3] + tricksWon[(declarer + 3) & 3] : 0;
        for (int p = 0; p < 4; p++) {
            int d;
            if (open) {
                d = sameTeam(p, declarer) ? (declarerMade ? bid : -bid) : oppTricks;
            } else if (p == declarer) {
                d = declarerMade ? bid : -bid;
            } else {
                d = tricksWon[p];
            }
            handDelta[p] = d;
            score[p] += d;
        }
        phase = HAND_OVER;
        if (open && bid == MAX_BID) {
            // 13 ihale: tutan takım maçı kazanır, yatıran kaybeder
            winner = declarerMade ? declarer : (declarer + 1) & 3;
            phase = MATCH_OVER;
            return;
        }
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        boolean tie = false;
        for (int p = 0; p < 4; p++) {
            if (score[p] >= target) {
                if (score[p] > bestScore) {
                    bestScore = score[p];
                    best = p;
                    tie = false;
                } else if (score[p] == bestScore && !(open && p == partner(best))) {
                    tie = true;
                }
            }
        }
        if (best >= 0 && !tie) {
            winner = best;
            phase = MATCH_OVER;
        }
    }

    // ------------------------------------------------------------------
    // Bilgisayar oyuncu
    // ------------------------------------------------------------------

    /** Elin yaklaşık el sayısı ve en iyi koz. */
    public static double estimate(int[] h, int[] bestTrumpOut) {
        double bestEst = -1;
        int bestSuit = 0;
        for (int t = 0; t < 4; t++) {
            double e = 0;
            int tl = Cards.countSuit(h, t);
            for (int c : h) {
                int s = Cards.suit(c);
                int r = Cards.rank(c);
                int len = Cards.countSuit(h, s);
                if (s == t) {
                    if (r == 14) {
                        e += 0.95;
                    } else if (r == 13) {
                        e += len >= 2 ? 0.85 : 0.4;
                    } else if (r == 12) {
                        e += len >= 3 ? 0.7 : 0.3;
                    } else if (r == 11) {
                        e += len >= 4 ? 0.55 : 0.2;
                    } else if (r == 10) {
                        e += len >= 5 ? 0.45 : 0.1;
                    } else if (len >= 6) {
                        e += 0.4;
                    }
                } else {
                    if (r == 14) {
                        e += 0.85;
                    } else if (r == 13) {
                        e += len >= 2 ? 0.5 : 0.1;
                    } else if (r == 12 && len >= 3) {
                        e += 0.2;
                    }
                }
            }
            if (tl >= 5) {
                e += (tl - 4) * 0.45;
            }
            // kısa renklerle kesme
            if (tl >= 3) {
                for (int s = 0; s < 4; s++) {
                    if (s == t) {
                        continue;
                    }
                    int len = Cards.countSuit(h, s);
                    if (len == 0) {
                        e += 0.9;
                    } else if (len == 1) {
                        e += 0.45;
                    } else if (len == 2) {
                        e += 0.15;
                    }
                }
            }
            if (e > bestEst) {
                bestEst = e;
                bestSuit = t;
            }
        }
        if (bestTrumpOut != null) {
            bestTrumpOut[0] = bestSuit;
        }
        return bestEst;
    }

    /** Bilgisayarın teklifi (0 = pas). */
    /** Bot ihale ayarı: kazanan lanetine karşı yüksek tekliflerde ek pay (tavlama testleriyle seçildi). */
    public static double BID_BONUS = 2.4, BID_SLOPE = 0.3, BID_BONUS_OPEN = 4.2, BID_SLOPE_OPEN = 0.4;

    public int botBid(int p) {
        double e = estimate(hand[p], null) + (open ? BID_BONUS_OPEN : BID_BONUS);
        int need = minBid();
        if (need > MAX_BID) {
            return 0;
        }
        double extra = open ? BID_SLOPE_OPEN * Math.max(0, need - MIN_BID_OPEN) : BID_SLOPE * Math.max(0, need - 5);
        if (Math.floor(e) < need + extra) {
            return 0;
        }
        // kendi önceki teklifini yükseltmek yerine gereksiz yükseltme yapma
        if (highBidder == p) {
            return 0;
        }
        return need;
    }

    public int botTrump(int p) {
        int[] o = new int[1];
        estimate(hand[p], o);
        return o[0];
    }

    private boolean isBoss(int card) {
        int s = Cards.suit(card);
        for (int r = Cards.rank(card) + 1; r <= 14; r++) {
            if (!played[Cards.make(s, r)] && !Cards.has(hand[turn], Cards.make(s, r))) {
                return false;
            }
        }
        return true;
    }

    private int lowest(int[] cs, boolean avoidTrump) {
        int best = -1;
        for (int c : cs) {
            if (best < 0) {
                best = c;
                continue;
            }
            boolean cT = Cards.suit(c) == trump;
            boolean bT = Cards.suit(best) == trump;
            if (avoidTrump && cT != bT) {
                if (!cT) {
                    best = c;
                }
                continue;
            }
            if (Cards.rank(c) < Cards.rank(best)) {
                best = c;
            }
        }
        return best;
    }

    /** Bu kart şu an elde en yüksek mi (şimdiye kadar atılanlara göre kazanır mı)? */
    private boolean wins(int card) {
        int best = -1;
        for (int i = 0; i < 4; i++) {
            if (trick[i] >= 0) {
                if (best < 0) {
                    best = trick[i];
                } else {
                    best = beats(trick[i], best) ? trick[i] : best;
                }
            }
        }
        return best < 0 || beats(card, best);
    }

    private boolean beats(int c, int b) {
        boolean cT = Cards.suit(c) == trump;
        boolean bT = Cards.suit(b) == trump;
        if (cT && !bT) {
            return true;
        }
        if (!cT && bT) {
            return false;
        }
        return Cards.suit(c) == Cards.suit(b) && Cards.rank(c) > Cards.rank(b);
    }

    /** Bilgisayarın oynayacağı kart (turn sırasındaki oyuncu). */
    public int botPlay() {
        int p = turn;
        int[] lg = legal(p);
        if (lg.length == 1) {
            return lg[0];
        }
        boolean isDecl = p == declarer || (open && p == dummy());
        if (trickCount == 0) {
            // açış
            int best = -1;
            // kazanacak kartlar (boss) önce; koz çekmek ihalecinin işidir
            for (int c : lg) {
                if (isBoss(c) && (Cards.suit(c) != trump || isDecl)) {
                    if (best < 0 || Cards.rank(c) > Cards.rank(best)) {
                        best = c;
                    }
                }
            }
            if (best >= 0) {
                return best;
            }
            if (isDecl) {
                // kozlar bitmediyse en yüksek kozla çek
                int tr = -1;
                for (int c : lg) {
                    if (Cards.suit(c) == trump && (tr < 0 || Cards.rank(c) > Cards.rank(tr))) {
                        tr = c;
                    }
                }
                int trumpsOut = 0;
                for (int r = 2; r <= 14; r++) {
                    int c = Cards.make(trump, r);
                    if (!played[c] && !Cards.has(hand[p], c)) {
                        trumpsOut++;
                    }
                }
                if (tr >= 0 && trumpsOut > 0 && Cards.rank(tr) >= 11) {
                    return tr;
                }
            }
            // yan renkten en kısa/alçak
            int bestC = -1;
            int bestScore = Integer.MAX_VALUE;
            for (int c : lg) {
                if (Cards.suit(c) == trump) {
                    continue;
                }
                int len = Cards.countSuit(hand[p], Cards.suit(c));
                int sc = len * 20 + Cards.rank(c);
                if (sc < bestScore) {
                    bestScore = sc;
                    bestC = c;
                }
            }
            return bestC >= 0 ? bestC : lowest(lg, false);
        }
        // takip
        int winnerNow = currentWinner();
        // alabilecek en ucuz kart
        int cheapest = -1;
        for (int c : lg) {
            if (wins(c)) {
                if (cheapest < 0 || cost(c) < cost(cheapest)) {
                    cheapest = c;
                }
            }
        }
        boolean lastToPlay = trickCount == 3;
        if (cheapest >= 0) {
            // son oynayansa ya da eli almak değerliyse al
            boolean declWinning = open ? sameTeam(winnerNow, declarer) : winnerNow == declarer;
            if (open && !isDecl && winnerNow == partner(p) && !lastToPlay) {
                return lowest(lg, true);   // eşim alıyor, üstüne çıkma
            }
            if (lastToPlay || isDecl || declWinning || Cards.rank(cheapest) >= 11 || Cards.suit(cheapest) != trump) {
                return cheapest;
            }
            // kozla almak pahalı: ihaleci almıyorsa bırak
            if (!declWinning && !isDecl) {
                return lowest(lg, true);
            }
            return cheapest;
        }
        return lowest(lg, true);
    }

    private int cost(int c) {
        return (Cards.suit(c) == trump ? 100 : 0) + Cards.rank(c);
    }

    private int currentWinner() {
        int best = -1;
        for (int k = 0; k < trickCount; k++) {
            int p = (trickLeader + k) & 3;
            if (best < 0 || beats(trick[p], trick[best])) {
                best = p;
            }
        }
        return best;
    }
}
