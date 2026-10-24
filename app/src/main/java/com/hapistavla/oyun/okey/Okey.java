package com.hapistavla.oyun.okey;

import com.hapistavla.oyun.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Okey (4 kişi). Oyuncu 0 insan, 1..3 bilgisayar; sıra 0 → 1 → 2 → 3.
 *
 * Taş numarası: 0..103 = (kopya*4 + renk)*13 + (sayı-1); 104 ve 105 sahte okeydir.
 * Renkler: 0 sarı, 1 mavi, 2 siyah, 3 kırmızı. Gösterge taşından bir büyük sayı (13'ten sonra 1) aynı renkteki taş
 * okeydir (joker). Sahte okey, okey taşının yerine geçen sıradan bir taştır.
 */
public class Okey {
    public static final int DRAW = 0;
    public static final int DISCARD = 1;
    public static final int EL_OVER = 2;
    public static final int MATCH_OVER = 3;

    public static final int FAKE1 = 104;

    public final Random rnd;
    /** Bot kararları için ayrı rastgelelik (dağıtım tohumla aynı kalsın diye). */
    public final Random brnd = new Random();
    public final int start;                         // başlangıç puanı (11/9/7/5/3/1) ya da el sayısı (101)
    public int handSize = 14;
    public final int[] score = new int[4];
    public int dealer = 0;
    public int elNo = 0;

    public int indicator;
    public int okeyColor;
    public int okeyNum;
    public final List<Integer>[] hand;
    public final List<Integer>[] discards;
    public final List<Integer> pile = new ArrayList<Integer>();
    public int turn;
    public int phase = EL_OVER;

    // el sonucu
    public int elWinner = -1;       // -1: berabere (deste bitti)
    public int elMult = 1;
    public boolean elPairs;
    public boolean elOkeyFinish;
    public final int[] elDelta = new int[4];
    public int matchWinner = -1;
    public int lastDrawnFromDiscard = -1;

    @SuppressWarnings("unchecked")
    public Okey(int start, Random rnd) {
        this.start = start;
        this.rnd = rnd;
        hand = new List[4];
        discards = new List[4];
        for (int i = 0; i < 4; i++) {
            hand[i] = new ArrayList<Integer>();
            discards[i] = new ArrayList<Integer>();
            score[i] = start;
        }
        dealer = rnd.nextInt(4);
    }

    // ------------------------------------------------------------------
    // Taş bilgisi
    // ------------------------------------------------------------------

    public int natColor(int t) {
        return t >= 104 ? okeyColor : (t % 52) / 13;
    }

    public int natNum(int t) {
        return t >= 104 ? okeyNum : t % 13 + 1;
    }

    /** 12-13-1 gibi 1'in seri sonunda kullanılmasına izin verilir mi (101 Okey'de verilmez). */
    protected boolean aceHigh() {
        return true;
    }

    public boolean isJoker(int t) {
        return t < 104 && (t % 52) / 13 == okeyColor && t % 13 + 1 == okeyNum;
    }

    public static int color(int t) {
        return (t % 52) / 13;
    }

    public static int num(int t) {
        return t % 13 + 1;
    }

    // ------------------------------------------------------------------
    // El başlat
    // ------------------------------------------------------------------

    public void newEl() {
        if (elNo > 0) {
            dealer = (dealer + 1) & 3;
        }
        elNo++;
        List<Integer> d = new ArrayList<Integer>();
        for (int i = 0; i < 106; i++) {
            d.add(i);
        }
        Collections.shuffle(d, rnd);
        int ind = -1;
        for (int i = 0; i < d.size(); i++) {
            if (d.get(i) < 104) {
                ind = d.remove(i);
                break;
            }
        }
        indicator = ind;
        okeyColor = color(ind);
        okeyNum = num(ind) == 13 ? 1 : num(ind) + 1;
        int starter = (dealer + 1) & 3;
        for (int p = 0; p < 4; p++) {
            hand[p].clear();
            discards[p].clear();
            int n = p == starter ? handSize + 1 : handSize;
            for (int i = 0; i < n; i++) {
                hand[p].add(d.remove(d.size() - 1));
            }
        }
        pile.clear();
        pile.addAll(d);
        turn = starter;
        phase = DISCARD;
        elWinner = -1;
        elMult = 1;
        elPairs = false;
        elOkeyFinish = false;
        for (int i = 0; i < 4; i++) {
            elDelta[i] = 0;
        }
    }

    public int prev(int p) {
        return (p + 3) & 3;
    }

    public boolean canTakeDiscard() {
        return phase == DRAW && !discards[prev(turn)].isEmpty();
    }

    /** Desteden çek. Deste bittiyse el berabere biter ve -1 döner. */
    public int drawPile() {
        if (phase != DRAW) {
            return -1;
        }
        if (pile.isEmpty()) {
            endDraw();
            return -1;
        }
        int t = pile.remove(pile.size() - 1);
        hand[turn].add(t);
        phase = DISCARD;
        lastDrawnFromDiscard = -1;
        return t;
    }

    public int takeDiscard() {
        if (!canTakeDiscard()) {
            return -1;
        }
        List<Integer> dl = discards[prev(turn)];
        int t = dl.remove(dl.size() - 1);
        hand[turn].add(t);
        phase = DISCARD;
        lastDrawnFromDiscard = t;
        return t;
    }

    public boolean discard(int t) {
        if (phase != DISCARD || !hand[turn].contains(t)) {
            return false;
        }
        hand[turn].remove(Integer.valueOf(t));
        discards[turn].add(t);
        turn = (turn + 1) & 3;
        phase = DRAW;
        if (pile.isEmpty()) {
            endDraw();
        }
        return true;
    }

    public void endDraw() {
        elWinner = -1;
        phase = EL_OVER;
    }

    /** Bitirme mümkün mü (elde değişiklik yapmadan). */
    public boolean canFinish(int p, int t) {
        if (phase != DISCARD || turn != p || !hand[p].contains(t)) {
            return false;
        }
        List<Integer> rest = new ArrayList<Integer>(hand[p]);
        rest.remove(Integer.valueOf(t));
        return finishKind(rest) != 0;
    }

    /** Bitirme denemesi: t taşı atılır, kalan 14 taş açılabiliyorsa el biter. */
    public boolean tryFinish(int p, int t) {
        if (phase != DISCARD || turn != p || !hand[p].contains(t)) {
            return false;
        }
        List<Integer> rest = new ArrayList<Integer>(hand[p]);
        rest.remove(Integer.valueOf(t));
        int kind = finishKind(rest);
        if (kind == 0) {
            return false;
        }
        hand[p].remove(Integer.valueOf(t));
        discards[p].add(t);
        elWinner = p;
        elPairs = kind == 2;
        elOkeyFinish = isJoker(t);
        elMult = 1 * (elPairs ? 2 : 1) * (elOkeyFinish ? 2 : 1);
        int loss = 2 * elMult;
        for (int i = 0; i < 4; i++) {
            if (i != p) {
                score[i] -= loss;
                elDelta[i] = -loss;
            }
        }
        phase = EL_OVER;
        int low = Integer.MAX_VALUE;
        boolean any = false;
        for (int i = 0; i < 4; i++) {
            if (score[i] <= 0) {
                any = true;
            }
            low = Math.min(low, score[i]);
        }
        if (any) {
            int best = -1;
            int bs = Integer.MIN_VALUE;
            for (int i = 0; i < 4; i++) {
                if (score[i] > bs) {
                    bs = score[i];
                    best = i;
                }
            }
            matchWinner = best;
            phase = MATCH_OVER;
        }
        return true;
    }

    /** 0: açılmaz, 1: seri/per, 2: çift. */
    public int finishKind(List<Integer> tiles) {
        if (tiles.size() != 14) {
            return 0;
        }
        int[][] cnt = new int[4][14];
        int j = 0;
        for (int t : tiles) {
            if (isJoker(t)) {
                j++;
            } else {
                cnt[natColor(t)][natNum(t)]++;
            }
        }
        if (cover(cnt, j)) {
            return 1;
        }
        int singles = 0;
        for (int c = 0; c < 4; c++) {
            for (int n = 1; n <= 13; n++) {
                singles += cnt[c][n] & 1;
            }
        }
        return j >= singles ? 2 : 0;
    }

    // ------------------------------------------------------------------
    // Per / seri araması
    // ------------------------------------------------------------------

    /** Ilk (en küçük) taşı içeren olası grupları üretir: {jokerSayısı, c1,n1, c2,n2, ...}. */
    public List<int[]> options(int[][] cnt, int c, int n, int J) {
        List<int[]> out = new ArrayList<int[]>();
        // aynı sayı, farklı renkler
        List<Integer> others = new ArrayList<Integer>();
        for (int c2 = c + 1; c2 < 4; c2++) {
            if (cnt[c2][n] > 0) {
                others.add(c2);
            }
        }
        int m = others.size();
        for (int mask = 0; mask < (1 << m); mask++) {
            int s = Integer.bitCount(mask);
            int total = 1 + s;
            for (int jk = Math.max(0, 3 - total); jk <= 4 - total && jk <= J; jk++) {
                int[] o = new int[1 + 2 * total];
                o[0] = jk;
                o[1] = c;
                o[2] = n;
                int k = 3;
                for (int i = 0; i < m; i++) {
                    if ((mask & (1 << i)) != 0) {
                        o[k++] = others.get(i);
                        o[k++] = n;
                    }
                }
                out.add(o);
            }
        }
        // ileri seri: n, n+1, ... (önüne joker de eklenebilir)
        for (int pre = 0; pre <= Math.min(J, n - 1); pre++) {
            List<Integer> tiles = new ArrayList<Integer>();
            tiles.add(n);
            int jk = pre;
            for (int m2 = n + 1; m2 <= 13; m2++) {
                if (cnt[c][m2] > 0) {
                    tiles.add(m2);
                } else {
                    jk++;
                    if (jk > J) {
                        break;
                    }
                }
                int len = tiles.size() + jk;
                if (len >= 3) {
                    int[] o = new int[1 + 2 * tiles.size()];
                    o[0] = jk;
                    for (int i = 0; i < tiles.size(); i++) {
                        o[1 + 2 * i] = c;
                        o[2 + 2 * i] = tiles.get(i);
                    }
                    out.add(o);
                }
            }
        }
        // 12-13-1 türü: 1 serinin sonunda
        if (n == 1 && aceHigh()) {
            List<Integer> t2 = new ArrayList<Integer>();
            t2.add(1);
            int jk2 = 0;
            for (int m2 = 13; m2 >= 2; m2--) {
                if (cnt[c][m2] > 0) {
                    t2.add(m2);
                } else {
                    jk2++;
                    if (jk2 > J) {
                        break;
                    }
                }
                int len = t2.size() + jk2;
                if (len >= 3) {
                    int[] o = new int[1 + 2 * t2.size()];
                    o[0] = jk2;
                    for (int i = 0; i < t2.size(); i++) {
                        o[1 + 2 * i] = c;
                        o[2 + 2 * i] = t2.get(i);
                    }
                    out.add(o);
                }
            }
        }
        return out;
    }

    private static void apply(int[][] cnt, int[] o, int d) {
        for (int i = 1; i + 1 < o.length; i += 2) {
            cnt[o[i]][o[i + 1]] += d;
        }
    }

    public boolean cover(int[][] cnt, int J) {
        int c = -1;
        int n = -1;
        outer:
        for (int c0 = 0; c0 < 4; c0++) {
            for (int n0 = 1; n0 <= 13; n0++) {
                if (cnt[c0][n0] > 0) {
                    c = c0;
                    n = n0;
                    break outer;
                }
            }
        }
        if (c < 0) {
            return true;
        }
        for (int[] o : options(cnt, c, n, J)) {
            apply(cnt, o, -1);
            boolean ok = cover(cnt, J - o[0]);
            apply(cnt, o, +1);
            if (ok) {
                return true;
            }
        }
        return false;
    }

    private static String key(int[][] cnt, int J) {
        StringBuilder sb = new StringBuilder(60);
        for (int c = 0; c < 4; c++) {
            for (int n = 1; n <= 13; n++) {
                sb.append((char) ('0' + cnt[c][n]));
            }
        }
        sb.append(J);
        return sb.toString();
    }

    /** En çok taşı gruplara yerleştirme (atlanan taşlar serbest). */
    public int bestCover(int[][] cnt, int J, Map<String, Integer> memo, int mode) {
        int c = -1;
        int n = -1;
        outer:
        for (int c0 = 0; c0 < 4; c0++) {
            for (int n0 = 1; n0 <= 13; n0++) {
                if (cnt[c0][n0] > 0) {
                    c = c0;
                    n = n0;
                    break outer;
                }
            }
        }
        if (c < 0) {
            return 0;
        }
        String k = mode + key(cnt, J);
        Integer m = memo.get(k);
        if (m != null) {
            return m;
        }
        int best;
        cnt[c][n]--;
        best = bestCover(cnt, J, memo, mode);
        cnt[c][n]++;
        for (int[] o : options(cnt, c, n, J)) {
            apply(cnt, o, -1);
            int v = optVal(o, mode) + bestCover(cnt, J - o[0], memo, mode);
            apply(cnt, o, +1);
            if (v > best) {
                best = v;
            }
        }
        memo.put(k, best);
        return best;
    }

    /** Elin değeri: gruplanan taş sayısı ve komşuluk (bilgisayar için). */
    public int value(List<Integer> tiles) {
        int[][] cnt = new int[4][14];
        int j = 0;
        for (int t : tiles) {
            if (isJoker(t)) {
                j++;
            } else {
                cnt[natColor(t)][natNum(t)]++;
            }
        }
        int cov = bestCover(cnt, j, new HashMap<String, Integer>(), 0);
        if (cov > 0) {
            cov += j > 0 ? 0 : 0;
        }
        int pot = 0;
        for (int c = 0; c < 4; c++) {
            for (int n = 1; n <= 13; n++) {
                if (cnt[c][n] == 0) {
                    continue;
                }
                boolean near = cnt[c][n] > 1;
                for (int d = -2; d <= 2 && !near; d++) {
                    if (d != 0) {
                        int m = n + d;
                        if (m == 0 && aceHigh()) {
                            m = 13;
                        } else if (m == 14 && aceHigh()) {
                            m = 1;
                        }
                        if (m >= 1 && m <= 13 && cnt[c][m] > 0) {
                            near = true;
                        }
                    }
                }
                for (int c2 = 0; c2 < 4 && !near; c2++) {
                    if (c2 != c && cnt[c2][n] > 0) {
                        near = true;
                    }
                }
                if (near) {
                    pot++;
                }
            }
        }
        return cov * 12 + pot + j * 6;
    }

    /** ÇİFT DİZ: aynı taşları ikişer ikişer eşler (okey jokeri tek kalan taşa eş olur); son grup serbest taşlardır. */
    public List<List<Integer>> arrangePairs(List<Integer> tiles) {
        Map<Integer, List<Integer>> pool = new java.util.TreeMap<Integer, List<Integer>>();
        List<Integer> jokers = new ArrayList<Integer>();
        for (int t : tiles) {
            if (isJoker(t)) {
                jokers.add(t);
            } else {
                int key = natColor(t) * 14 + natNum(t);
                if (!pool.containsKey(key)) {
                    pool.put(key, new ArrayList<Integer>());
                }
                pool.get(key).add(t);
            }
        }
        List<List<Integer>> groups = new ArrayList<List<Integer>>();
        List<Integer> singles = new ArrayList<Integer>();
        for (List<Integer> l : pool.values()) {
            while (l.size() >= 2) {
                List<Integer> g = new ArrayList<Integer>();
                g.add(l.remove(l.size() - 1));
                g.add(l.remove(l.size() - 1));
                groups.add(g);
            }
            singles.addAll(l);
        }
        while (!jokers.isEmpty() && !singles.isEmpty()) {
            List<Integer> g = new ArrayList<Integer>();
            g.add(singles.remove(singles.size() - 1));
            g.add(jokers.remove(jokers.size() - 1));
            groups.add(g);
        }
        List<Integer> free = new ArrayList<Integer>(singles);
        free.addAll(jokers);
        groups.add(free);
        return groups;
    }

    /** Elini gruplara ayırır (DİZ düğmesi): grup listesi; son grup serbest taşlardır. */
    public List<List<Integer>> arrange(List<Integer> tiles) {
        return arrange(tiles, 0);
    }

    public List<List<Integer>> arrange(List<Integer> tiles, int mode) {
        Map<Integer, List<Integer>> pool = new HashMap<Integer, List<Integer>>();
        List<Integer> jokers = new ArrayList<Integer>();
        int[][] cnt = new int[4][14];
        for (int t : tiles) {
            if (isJoker(t)) {
                jokers.add(t);
            } else {
                int key = natColor(t) * 14 + natNum(t);
                if (!pool.containsKey(key)) {
                    pool.put(key, new ArrayList<Integer>());
                }
                pool.get(key).add(t);
                cnt[natColor(t)][natNum(t)]++;
            }
        }
        int J = jokers.size();
        Map<String, Integer> memo = new HashMap<String, Integer>();
        List<List<Integer>> groups = new ArrayList<List<Integer>>();
        List<Integer> free = new ArrayList<Integer>();
        while (true) {
            int c = -1;
            int n = -1;
            outer:
            for (int c0 = 0; c0 < 4; c0++) {
                for (int n0 = 1; n0 <= 13; n0++) {
                    if (cnt[c0][n0] > 0) {
                        c = c0;
                        n = n0;
                        break outer;
                    }
                }
            }
            if (c < 0) {
                break;
            }
            int cur = bestCover(cnt, J, memo, mode);
            boolean done = false;
            for (int[] o : options(cnt, c, n, J)) {
                apply(cnt, o, -1);
                int v = optVal(o, mode) + bestCover(cnt, J - o[0], memo, mode);
                if (v == cur) {
                    List<Integer> g = new ArrayList<Integer>();
                    for (int i = 1; i + 1 < o.length; i += 2) {
                        List<Integer> pl = pool.get(o[i] * 14 + o[i + 1]);
                        g.add(pl.remove(pl.size() - 1));
                    }
                    for (int i = 0; i < o[0]; i++) {
                        g.add(jokers.remove(jokers.size() - 1));
                    }
                    J -= o[0];
                    groups.add(g);
                    done = true;
                    break;
                }
                apply(cnt, o, +1);
            }
            if (!done) {
                cnt[c][n]--;
                List<Integer> pl = pool.get(c * 14 + n);
                free.add(pl.remove(pl.size() - 1));
            }
        }
        if (!jokers.isEmpty()) {
            if (!groups.isEmpty()) {
                groups.get(groups.size() - 1).addAll(jokers);
            } else {
                free.addAll(jokers);
            }
        }
        Collections.sort(free, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                int ca = natColor(a);
                int cb = natColor(b);
                if (ca != cb) {
                    return ca - cb;
                }
                return natNum(a) - natNum(b);
            }
        });
        groups.add(free);
        return groups;
    }


    /** Seçenek değeri: mod 0 = taş sayısı, mod 1 = puan (101 açılışı). */
    public static int optVal(int[] o, int mode) {
        return mode == 0 ? (o.length - 1) / 2 + o[0] : pts(o);
    }

    public static int pts(int[] o) {
        int k = (o.length - 1) / 2;
        int len = k + o[0];
        int mn = 99;
        int mx = 0;
        boolean same = true;
        boolean has2 = false;
        boolean has1 = false;
        int first = o[2];
        for (int i = 0; i < k; i++) {
            int n = o[2 + 2 * i];
            mn = Math.min(mn, n);
            mx = Math.max(mx, n);
            if (n != first) {
                same = false;
            }
            has2 |= n == 2;
            has1 |= n == 1;
        }
        int run;
        if (has1 && mx >= 12 && !has2 && k >= 2) {
            run = 11;
            for (int i = 0; i < len - 1; i++) {
                run += 13 - i;
            }
        } else {
            int s = mn;
            if (s + len - 1 > 13) {
                s = 14 - len;
            }
            run = 0;
            for (int i = 0; i < len; i++) {
                run += s + i;
            }
        }
        if (same && k >= 2) {
            return first * len;
        }
        if (k == 1) {
            return Math.max(first * len, run);
        }
        return run;
    }

    // ------------------------------------------------------------------
    // Masadaki açılmış gruplar (101)
    // ------------------------------------------------------------------

    public static final class Meld {
        public boolean run;
        public int num;                       // per için sayı
        public int color;                     // seri için renk
        public int owner;
        public final List<Integer> tiles = new ArrayList<Integer>();
        public final List<Integer> nums = new ArrayList<Integer>();   // seri: her taşın temsil ettiği sayı (14 = sondaki 1)

        public int points() {
            if (!run) {
                return num * tiles.size();
            }
            int p = 0;
            for (int n : nums) {
                p += n == 14 ? 11 : n;
            }
            return p;
        }
    }

    /** Taş listesinden grup kurar; geçersizse null. */
    public Meld buildMeld(List<Integer> tiles) {
        List<Integer> nonJ = new ArrayList<Integer>();
        List<Integer> jk = new ArrayList<Integer>();
        for (int t : tiles) {
            if (isJoker(t)) {
                jk.add(t);
            } else {
                nonJ.add(t);
            }
        }
        if (tiles.size() < 3) {
            return null;
        }
        Meld m = new Meld();
        boolean sameNum = true;
        for (int t : nonJ) {
            if (natNum(t) != natNum(nonJ.get(0))) {
                sameNum = false;
            }
        }
        if (nonJ.size() <= 1 || (sameNum && nonJ.size() >= 2)) {
            if (tiles.size() > 4) {
                return null;
            }
            boolean[] used = new boolean[4];
            for (int t : nonJ) {
                if (used[natColor(t)]) {
                    return null;
                }
                used[natColor(t)] = true;
            }
            m.run = false;
            m.num = nonJ.isEmpty() ? 13 : natNum(nonJ.get(0));
            m.tiles.addAll(nonJ);
            m.tiles.addAll(jk);
            return m;
        }
        int col = natColor(nonJ.get(0));
        boolean has1 = false;
        boolean has2 = false;
        boolean has3 = false;
        boolean hi = false;
        for (int t : nonJ) {
            if (natColor(t) != col) {
                return null;
            }
            int n = natNum(t);
            has1 |= n == 1;
            has2 |= n == 2;
            has3 |= n == 3;
            hi |= n >= 12;
        }
        boolean wrap = aceHigh() && has1 && hi && !has2 && !has3;
        int lo = 99;
        int mx = 0;
        boolean[] seen = new boolean[16];
        for (int t : nonJ) {
            int n = natNum(t);
            if (wrap && n == 1) {
                n = 14;
            }
            if (seen[n]) {
                return null;
            }
            seen[n] = true;
            lo = Math.min(lo, n);
            mx = Math.max(mx, n);
        }
        int gaps = mx - lo + 1 - nonJ.size();
        int extra = jk.size() - gaps;
        if (extra < 0) {
            return null;
        }
        int s = lo;
        int e = mx;
        while (extra > 0) {
            if (e < 13) {
                e++;
            } else if (s > 1) {
                s--;
            } else {
                return null;
            }
            extra--;
        }
        m.run = true;
        m.color = col;
        int ji = 0;
        for (int n = s; n <= e; n++) {
            int found = -1;
            for (int t : nonJ) {
                int tn = natNum(t);
                if (wrap && tn == 1) {
                    tn = 14;
                }
                if (tn == n) {
                    found = t;
                }
            }
            if (found >= 0) {
                m.tiles.add(found);
            } else {
                m.tiles.add(jk.get(ji++));
            }
            m.nums.add(n);
        }
        return m;
    }

    public boolean canAdd(Meld m, int t) {
        if (!m.run) {
            if (m.tiles.size() >= 4) {
                return false;
            }
            if (isJoker(t)) {
                return true;
            }
            if (natNum(t) != m.num) {
                return false;
            }
            for (int x : m.tiles) {
                if (!isJoker(x) && natColor(x) == natColor(t)) {
                    return false;
                }
            }
            return true;
        }
        if (m.nums.size() >= 13) {
            return false;
        }
        int lo = m.nums.get(0);
        int hi = m.nums.get(m.nums.size() - 1);
        if (isJoker(t)) {
            return lo > 1 || hi < 13;
        }
        if (natColor(t) != m.color) {
            return false;
        }
        int n = natNum(t);
        if (n == hi + 1 && hi < 13) {
            return true;
        }
        if (n == 1 && hi == 13 && lo > 1 && aceHigh()) {
            return true;
        }
        return n == lo - 1 && lo > 1;
    }

    public void addTo(Meld m, int t) {
        if (!m.run) {
            if (isJoker(t)) {
                m.tiles.add(t);
            } else {
                m.tiles.add(0, t);
            }
            return;
        }
        int lo = m.nums.get(0);
        int hi = m.nums.get(m.nums.size() - 1);
        if (isJoker(t)) {
            if (hi < 13) {
                m.tiles.add(t);
                m.nums.add(hi + 1);
            } else {
                m.tiles.add(0, t);
                m.nums.add(0, lo - 1);
            }
            return;
        }
        int n = natNum(t);
        if (n == hi + 1 && hi < 13) {
            m.tiles.add(t);
            m.nums.add(n);
        } else if (n == 1 && hi == 13 && aceHigh()) {
            m.tiles.add(t);
            m.nums.add(14);
        } else {
            m.tiles.add(0, t);
            m.nums.add(0, n);
        }
    }

    // ------------------------------------------------------------------
    // Bilgisayar
    // ------------------------------------------------------------------

    /** Çekme kararı: true = atılan taşı al, false = desteden çek. */
    public boolean botTakeDiscard(int p) {
        List<Integer> dl = discards[prev(p)];
        if (dl.isEmpty()) {
            return false;
        }
        int d = dl.get(dl.size() - 1);
        List<Integer> h = new ArrayList<Integer>(hand[p]);
        h.add(d);
        for (int t : h) {
            List<Integer> r = new ArrayList<Integer>(h);
            r.remove(Integer.valueOf(t));
            if (finishKind(r) != 0) {
                return true;
            }
        }
        if (isJoker(d)) {
            return true;
        }
        int cur = value(hand[p]);
        int best = Integer.MIN_VALUE;
        for (int t : h) {
            if (t == d && false) {
                continue;
            }
            List<Integer> r = new ArrayList<Integer>(h);
            r.remove(Integer.valueOf(t));
            if (t == d) {
                continue;     // aldığı taşı hemen geri atmasın
            }
            best = Math.max(best, value(r));
        }
        return best > cur + 3;
    }

    /** 15 taşlı elde bitirilebiliyorsa atılacak taş, yoksa -1. */
    public int botFinishTile(int p) {
        int found = -1;
        for (int t : hand[p]) {
            List<Integer> r = new ArrayList<Integer>(hand[p]);
            r.remove(Integer.valueOf(t));
            if (finishKind(r) != 0) {
                if (isJoker(t)) {
                    return t;
                }
                if (found < 0) {
                    found = t;
                }
            }
        }
        return found;
    }

    public int botDiscard(int p) {
        int bestT = hand[p].get(0);
        int bestV = Integer.MIN_VALUE;
        for (int t : hand[p]) {
            if (t == lastDrawnFromDiscard && p == turn) {
                continue;
            }
            List<Integer> r = new ArrayList<Integer>(hand[p]);
            r.remove(Integer.valueOf(t));
            int v = value(r) + brnd.nextInt(2);
            if (isJoker(t)) {
                v -= 30;
            }
            if (v > bestV) {
                bestV = v;
                bestT = t;
            }
        }
        return bestT;
    }
}
