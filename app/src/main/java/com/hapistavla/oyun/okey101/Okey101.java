package com.hapistavla.oyun.okey101;

import com.hapistavla.oyun.*;
import com.hapistavla.oyun.okey.Okey;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 101 Okey (4 kişi). Herkese 21 taş, başlayana 22. Elini açmak için taşlardan en az 101 puanlık grup indirmek gerekir.
 * Açan oyuncu masadaki gruplara taş işleyebilir. Elindeki son taşı atan eli bitirir.
 * Puan: ceza puanıdır, en az puanı olan kazanır. start alanı oynanacak el sayısıdır.
 * Seçenekler: eşli (karşılıklı oturanlar takım), katlama (okeyle / elden bitirmede puanlar katlanır),
 * ceza (okey atmak ya da masaya işlenebilecek taşı atmak +101).
 */
public final class Okey101 extends Okey {
    @Override
    protected boolean aceHigh() {
        return false;
    }

    public final boolean[] opened = new boolean[4];
    public final List<Meld> table = new ArrayList<Meld>();
    public int lastOpenPoints;

    /** Kural seçenekleri (masa kurulurken belirlenir, herkeste aynıdır). */
    public boolean eshli;
    public boolean katlama;
    public boolean ceza;
    /** Bu elde yazılan ceza puanları (+101'ler). */
    public final int[] penalty = new int[4];
    /** Son atılan taşta ceza yazıldıysa: oyuncu ve neden (1 okey attı, 2 işlenebilir taş attı); yoksa -1. */
    public int penSeat = -1;
    public int penReason;
    /** Oyuncu bu turda açtı ve açtığında başkası açmamıştı (elden bitirme için). */
    private boolean openedNow;
    private boolean soloOpen;
    /** Bitmiş eller: {p0,p1,p2,p3 puanı, ceza maskesi (bit p), katlama çarpanı}. */
    public final List<int[]> history = new ArrayList<int[]>();
    public boolean elSolo;

    public void setRules(boolean eshli, boolean katlama, boolean ceza) {
        this.eshli = eshli;
        this.katlama = katlama;
        this.ceza = ceza;
    }

    public static int teamOf(int p) {
        return p % 2;
    }

    public int teamScore(int t) {
        return score[t] + score[t + 2];
    }

    /** Maç bittiğinde seat kazandı mı (eşliyse takımı). */
    public boolean wins(int seat) {
        if (matchWinner < 0) {
            return false;
        }
        if (eshli) {
            return teamOf(seat) == teamOf(matchWinner);
        }
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            min = Math.min(min, score[i]);
        }
        return score[seat] == min;
    }

    public Okey101(int els, Random rnd) {
        super(els, rnd);
        handSize = 21;
        for (int i = 0; i < 4; i++) {
            score[i] = 0;
        }
    }

    @Override
    public void newEl() {
        super.newEl();
        for (int i = 0; i < 4; i++) {
            opened[i] = false;
            penalty[i] = 0;
        }
        table.clear();
        openedNow = false;
        soloOpen = false;
        penSeat = -1;
        elSolo = false;
    }

    private void markOpened(int p) {
        boolean other = false;
        for (int q = 0; q < 4; q++) {
            if (q != p && opened[q]) {
                other = true;
            }
        }
        opened[p] = true;
        openedNow = true;
        soloOpen = !other;
    }

    public static int tileValue(Okey o, int t) {
        return o.isJoker(t) ? 25 : o.natNum(t);
    }

    /** Eldeki en iyi açılış: grup listesi (en çok puan). */
    public List<List<Integer>> bestGroups(List<Integer> tiles, int mode) {
        List<List<Integer>> g = arrange(tiles, mode);
        List<List<Integer>> out = new ArrayList<List<Integer>>();
        for (int i = 0; i < g.size() - 1; i++) {
            List<Integer> gr = new ArrayList<Integer>(g.get(i));
            // fazla joker sığmıyorsa serbest bırak
            while (gr.size() > 4 && buildMeld(gr) == null && gr.size() > 3) {
                gr.remove(gr.size() - 1);
            }
            if (buildMeld(gr) != null) {
                out.add(gr);
            }
        }
        return out;
    }

    /** Elini açmayı dener. Başarılıysa true. */
    public boolean open(int p) {
        if (phase != DISCARD || turn != p || opened[p]) {
            return false;
        }
        List<List<Integer>> gs = bestGroups(hand[p], 1);
        int total = 0;
        int cnt = 0;
        for (List<Integer> g : gs) {
            total += buildMeld(g).points();
            cnt += g.size();
        }
        if (cnt >= hand[p].size() && !gs.isEmpty()) {
            // elde atılacak taş kalmalı: en küçük puanlı grubun son taşını bırak
            int bi = 0;
            int bv = Integer.MAX_VALUE;
            for (int i = 0; i < gs.size(); i++) {
                int v = buildMeld(gs.get(i)).points();
                if (v < bv) {
                    bv = v;
                    bi = i;
                }
            }
            List<Integer> g = gs.get(bi);
            if (g.size() > 3) {
                g.remove(g.size() - 1);
                if (buildMeld(g) == null) {
                    gs.remove(bi);
                }
            } else {
                gs.remove(bi);
            }
            total = 0;
            for (List<Integer> g2 : gs) {
                total += buildMeld(g2).points();
            }
        }
        lastOpenPoints = total;
        if (total < 101) {
            return false;
        }
        for (List<Integer> g : gs) {
            place(p, g);
        }
        markOpened(p);
        return true;
    }

    /** Bilgisayar için: atılan taşla elini açabilir mi (açmamışsa zorunlu kural). */
    public boolean canOpenWith(int p, int d) {
        List<Integer> h = new ArrayList<Integer>(hand[p]);
        h.add(d);
        int total = 0;
        for (List<Integer> g : bestGroups(h, 1)) {
            total += buildMeld(g).points();
        }
        return total >= 101;
    }

    @Override
    public boolean botTakeDiscard(int p) {
        List<Integer> dl = discards[prev(p)];
        if (dl.isEmpty()) {
            return false;
        }
        if (!opened[p]) {
            return canOpenWith(p, dl.get(dl.size() - 1));
        }
        int d = dl.get(dl.size() - 1);
        for (Meld m : table) {
            if (canAdd(m, d)) {
                return true;
            }
        }
        return false;
    }

    /** İnsan oyuncu: seçtiği gruplarla açar ya da indirir. Mesaj için toplam puanı lastOpenPoints'e yazar. */
    public boolean layGroups(int p, List<List<Integer>> groups) {
        if (phase != DISCARD || turn != p) {
            return false;
        }
        int total = 0;
        List<List<Integer>> ok = new ArrayList<List<Integer>>();
        for (List<Integer> g : groups) {
            Meld m = buildMeld(g);
            if (m != null && hand[p].containsAll(g)) {
                total += m.points();
                ok.add(g);
            }
        }
        lastOpenPoints = total;
        if (ok.isEmpty() || (!opened[p] && total < 101)) {
            return false;
        }
        int used = 0;
        for (List<Integer> g : ok) {
            used += g.size();
        }
        boolean was = opened[p];
        for (List<Integer> g : ok) {
            place(p, g);
        }
        if (!was) {
            markOpened(p);
        }
        if (hand[p].isEmpty()) {
            finishEl(p, false);
        }
        return true;
    }

    private void place(int p, List<Integer> g) {
        Meld m = buildMeld(g);
        m.owner = p;
        for (int t : m.tiles) {
            hand[p].remove(Integer.valueOf(t));
        }
        table.add(m);
    }

    /** Açık oyuncu elindeki geçerli grupları indirir (en az bir taş kalır). İndirilen taş sayısı döner. */
    public int layMore(int p) {
        if (phase != DISCARD || turn != p || !opened[p]) {
            return 0;
        }
        int placed = 0;
        for (List<Integer> g : bestGroups(hand[p], 0)) {
            if (hand[p].size() - g.size() < 1) {
                continue;
            }
            place(p, g);
            placed += g.size();
        }
        return placed;
    }

    public boolean addTile(int p, int meldIdx, int t) {
        if (phase != DISCARD || turn != p || !opened[p] || hand[p].size() < 2 || !hand[p].contains(t)
                || meldIdx < 0 || meldIdx >= table.size()) {
            return false;
        }
        Meld m = table.get(meldIdx);
        if (!canAdd(m, t)) {
            return false;
        }
        addTo(m, t);
        hand[p].remove(Integer.valueOf(t));
        return true;
    }

    /** Bilgisayar: elindeki taşları açık gruplara işler. */
    public int botAdd(int p) {
        int n = 0;
        boolean again = true;
        while (again && hand[p].size() >= 2) {
            again = false;
            for (int t : new ArrayList<Integer>(hand[p])) {
                for (int i = 0; i < table.size(); i++) {
                    if (hand[p].size() >= 2 && addTile(p, i, t)) {
                        n++;
                        again = true;
                        break;
                    }
                }
                if (again) {
                    break;
                }
            }
        }
        return n;
    }

    /** t taşı atılırsa ceza yazılır mı (cezalı oyunda): 0 yok, 1 okey, 2 masaya işlenebilir taş. Son taş (bitiriş) cezasızdır. */
    public int penaltyFor(int p, int t) {
        if (!ceza || (opened[p] && hand[p].size() <= 1)) {
            return 0;
        }
        if (isJoker(t)) {
            return 1;
        }
        for (Meld m : table) {
            if (canAdd(m, t)) {
                return 2;
            }
        }
        return 0;
    }

    @Override
    public int botDiscard(int p) {
        if (!ceza) {
            return super.botDiscard(p);
        }
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
            if (penaltyFor(p, t) != 0) {
                v -= 500;
            }
            if (v > bestV) {
                bestV = v;
                bestT = t;
            }
        }
        return bestT;
    }

    @Override
    public boolean discard(int t) {
        int p = turn;
        if (phase != DISCARD || !hand[p].contains(t)) {
            return false;
        }
        boolean joker = isJoker(t);
        int pen = penaltyFor(p, t);
        boolean ok = super.discard(t);
        if (!ok) {
            return false;
        }
        penSeat = -1;
        if (pen != 0) {
            penalty[p] += 101;
            penSeat = p;
            penReason = pen;
        }
        if (hand[p].isEmpty() && opened[p]) {
            finishEl(p, joker);
        } else if (phase == EL_OVER && elWinner < 0) {
            // deste bitti: yalnızca ceza puanları yazılır
            elMult = 1;
            int[] rec = new int[6];
            for (int q = 0; q < 4; q++) {
                elDelta[q] = penalty[q];
                score[q] += penalty[q];
                rec[q] = elDelta[q];
                if (penalty[q] > 0) {
                    rec[4] |= 1 << q;
                }
            }
            rec[5] = 1;
            history.add(rec);
            checkMatchEnd();
        }
        openedNow = false;
        return true;
    }

    /** Ağdan gelen "indirilen/işlenen" özetini uygular (misafir cihazlar). Eldeki taşlar masaya geçer. */
    public void applyTableSnapshot(int p, boolean nowOpen, int points, String snap) {
        java.util.Set<Integer> old = new java.util.HashSet<Integer>();
        for (Meld m : table) {
            old.addAll(m.tiles);
        }
        loadTable(snap);
        for (Meld m : table) {
            for (int t : m.tiles) {
                if (!old.contains(t)) {
                    hand[p].remove(Integer.valueOf(t));
                }
            }
        }
        if (nowOpen && !opened[p]) {
            markOpened(p);
        }
        opened[p] = nowOpen;
        lastOpenPoints = points;
        if (hand[p].isEmpty() && phase == DISCARD && turn == p) {
            finishEl(p, false);
        }
    }

    /** Masadaki grupları metne çevirir: grup;grup  (grup = run,num,color,owner,taşlar,sayılar; listeler '.' ile). */
    public String tableSnapshot() {
        StringBuilder sb = new StringBuilder();
        for (Meld m : table) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(m.run ? 1 : 0).append(',').append(m.num).append(',').append(m.color).append(',').append(m.owner).append(',');
            for (int i = 0; i < m.tiles.size(); i++) {
                sb.append(i > 0 ? "." : "").append(m.tiles.get(i));
            }
            sb.append(',');
            for (int i = 0; i < m.nums.size(); i++) {
                sb.append(i > 0 ? "." : "").append(m.nums.get(i));
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }

    public void loadTable(String s) {
        table.clear();
        if (s == null || s.isEmpty() || s.equals("-")) {
            return;
        }
        for (String ms : s.split(";")) {
            String[] f = ms.split(",", -1);
            if (f.length < 6) {
                continue;
            }
            Meld m = new Meld();
            m.run = f[0].equals("1");
            m.num = Integer.parseInt(f[1]);
            m.color = Integer.parseInt(f[2]);
            m.owner = Integer.parseInt(f[3]);
            for (String t : f[4].split("\\.")) {
                if (!t.isEmpty()) {
                    m.tiles.add(Integer.parseInt(t));
                }
            }
            for (String t : f[5].split("\\.")) {
                if (!t.isEmpty()) {
                    m.nums.add(Integer.parseInt(t));
                }
            }
            table.add(m);
        }
    }

    /** layGroups için ön kontrol: elde değişiklik yapmaz; lastOpenPoints'i günceller. */
    public boolean canLay(int p, List<List<Integer>> groups) {
        if (phase != DISCARD || turn != p) {
            return false;
        }
        int total = 0;
        int n = 0;
        for (List<Integer> g : groups) {
            Meld m = buildMeld(g);
            if (m != null && hand[p].containsAll(g)) {
                total += m.points();
                n++;
            }
        }
        lastOpenPoints = total;
        return n > 0 && (opened[p] || total >= 101);
    }

    public void finishEl(int p, boolean okeyFinish) {
        boolean solo = openedNow && soloOpen && p >= 0;
        int mult = 1;
        if (katlama) {
            mult = (okeyFinish ? 2 : 1) * (solo ? 2 : 1);
        }
        elWinner = p;
        elOkeyFinish = okeyFinish;
        elSolo = solo;
        elMult = mult;
        int[] rec = new int[6];
        for (int q = 0; q < 4; q++) {
            if (q == p) {
                elDelta[q] = -101 * mult;
            } else if (!opened[q]) {
                elDelta[q] = 202 * mult;
            } else {
                int sum = 0;
                for (int t : hand[q]) {
                    sum += tileValue(this, t);
                }
                elDelta[q] = sum * mult;
            }
            elDelta[q] += penalty[q];
            score[q] += elDelta[q];
            rec[q] = elDelta[q];
            if (penalty[q] > 0) {
                rec[4] |= 1 << q;
            }
        }
        rec[5] = mult;
        history.add(rec);
        phase = EL_OVER;
        checkMatchEnd();
    }

    private void checkMatchEnd() {
        if (elNo >= start) {
            int best = 0;
            for (int i = 1; i < 4; i++) {
                if (score[i] < score[best]) {
                    best = i;
                }
            }
            if (eshli && teamScore(0) != teamScore(1)) {
                int t = teamScore(0) < teamScore(1) ? 0 : 1;
                best = score[t] <= score[t + 2] ? t : t + 2;
            }
            matchWinner = best;
            phase = MATCH_OVER;
        }
    }
}
