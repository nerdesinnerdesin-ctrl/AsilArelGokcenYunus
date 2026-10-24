package com.hapistavla.oyun.okey;

import java.util.ArrayList;
import java.util.List;

/** Okey ve 101 Okey ıstakası için ortak yardımcılar: per bulma ve araya girerek yerleştirme (takas yok). */
public final class RackUtil {
    private RackUtil() {
    }

    /**
     * Istakadaki gerçek perleri (aralıksız yan yana duran, geçerli seri ya da takım olan taş dizileri) bulur.
     * Her eleman {ilk göz, son göz}. Boş (-1) ve yer tutucu (-2) gözler boşluk sayılır.
     */
    public static List<int[]> perSpans(Okey ok, int[] a, int perRow) {
        List<int[]> out = new ArrayList<int[]>();
        int rows = a.length / perRow;
        for (int r = 0; r < rows; r++) {
            int lo = r * perRow;
            int hi = lo + perRow - 1;
            int i = lo;
            while (i <= hi) {
                if (a[i] < 0) {
                    i++;
                    continue;
                }
                int e = i;
                while (e < hi && a[e + 1] >= 0) {
                    e++;
                }
                // i..e aralıksız taş bölümü; soldan başlayıp en uzun geçerli peri al
                int cur = i;
                while (cur <= e - 2) {
                    int found = -1;
                    for (int j = e; j >= cur + 2; j--) {
                        List<Integer> l = new ArrayList<Integer>();
                        for (int k = cur; k <= j; k++) {
                            l.add(a[k]);
                        }
                        if (ok.buildMeld(l) != null) {
                            found = j;
                            break;
                        }
                    }
                    if (found >= 0) {
                        out.add(new int[] {cur, found});
                        cur = found + 1;
                    } else {
                        cur++;
                    }
                }
                i = e + 1;
            }
        }
        return out;
    }

    /** v'yi i. göze koyar: doluysa yandaki en yakın boşluğa kadar taşları kaydırır (araya girer, takas olmaz). */
    public static void insertAt(int[] a, int i, int v, int perRow) {
        if (a[i] < 0) {
            a[i] = v;
            return;
        }
        int lo = (i / perRow) * perRow;
        int hi = lo + perRow - 1;
        int r = -1;
        int l = -1;
        for (int j = i + 1; j <= hi; j++) {
            if (a[j] < 0) {
                r = j;
                break;
            }
        }
        for (int j = i - 1; j >= lo; j--) {
            if (a[j] < 0) {
                l = j;
                break;
            }
        }
        if (r < 0 && l < 0) {
            // satır tamamen dolu: sondaki taş öbür satırın ilk boş yerine geçer
            int out = a[hi];
            int dst = firstEmpty(a, i / perRow, perRow);
            if (dst < 0) {
                return;
            }
            for (int j = hi; j > i; j--) {
                a[j] = a[j - 1];
            }
            a[i] = v;
            a[dst] = out;
            return;
        }
        if (l < 0 || (r >= 0 && r - i <= i - l)) {
            for (int j = r; j > i; j--) {
                a[j] = a[j - 1];
            }
        } else {
            for (int j = l; j < i; j++) {
                a[j] = a[j + 1];
            }
        }
        a[i] = v;
    }

    /** Önce başka satırdaki, yoksa herhangi bir boş göz; yoksa -1. */
    private static int firstEmpty(int[] a, int avoidRow, int perRow) {
        for (int j = 0; j < a.length; j++) {
            if (a[j] < 0 && j / perRow != avoidRow) {
                return j;
            }
        }
        for (int j = 0; j < a.length; j++) {
            if (a[j] < 0) {
                return j;
            }
        }
        return -1;
    }

    /**
     * Taş bloğunu (per) i. gözden başlayarak yerleştirir; sağdaki taşlar gerektiği kadar sağa kayar.
     * Sığmayanlar öbür satırın boş gözlerine geçer. Blok taşları a'da önceden çıkarılmış olmalıdır.
     * Yerleştirilen taşlar tiles sırasıyla yazılır (yer tutucu için -2 verilebilir).
     */
    public static void insertBlock(int[] a, int i, int[] tiles, int perRow) {
        int L = tiles.length;
        int row = i / perRow;
        int lo = row * perRow;
        int hi = lo + perRow - 1;
        i = Math.max(lo, Math.min(i - L / 2, hi - L + 1));
        List<int[]> after = new ArrayList<int[]>();   // {konum, taş}: i ve sonrası
        for (int j = i; j <= hi; j++) {
            if (a[j] >= 0) {
                after.add(new int[] {j, a[j]});
                a[j] = -1;
            }
        }
        for (int k = 0; k < L; k++) {
            a[i + k] = tiles[k];
        }
        int prev = i + L - 1;
        List<Integer> overflow = new ArrayList<Integer>();
        for (int[] e : after) {
            int pos = Math.max(e[0], prev + 1);
            if (pos > hi) {
                overflow.add(e[1]);
            } else {
                a[pos] = e[1];
                prev = pos;
            }
        }
        for (int t : overflow) {
            int dst = firstEmpty(a, row, perRow);
            if (dst >= 0) {
                a[dst] = t;
            }
        }
    }
}
