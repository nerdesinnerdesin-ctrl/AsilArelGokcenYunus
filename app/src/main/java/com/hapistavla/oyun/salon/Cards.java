package com.hapistavla.oyun.salon;

import com.hapistavla.oyun.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Kağıt oyunları için ortak yardımcılar. Kart = takım * 16 + değer (değer 2..14, 14 = As). */
public final class Cards {
    private Cards() {}

    public static int make(int suit, int rank) {
        return suit * 16 + rank;
    }

    public static int suit(int c) {
        return c >> 4;
    }

    public static int rank(int c) {
        return c & 15;
    }

    /** Ekranda sıralama: maça, kupa, sinek, karo (renkler birbirini izlesin); değer büyükten küçüğe. */
    private static final int[] SUIT_ORDER = {0, 1, 3, 2};

    public static int sortKey(int c) {
        return SUIT_ORDER[suit(c)] * 100 - rank(c);
    }

    public static void sort(int[] hand) {
        Integer[] a = new Integer[hand.length];
        for (int i = 0; i < hand.length; i++) {
            a[i] = hand[i];
        }
        Arrays.sort(a, new java.util.Comparator<Integer>() {
            @Override
            public int compare(Integer x, Integer y) {
                return sortKey(x) - sortKey(y);
            }
        });
        for (int i = 0; i < hand.length; i++) {
            hand[i] = a[i];
        }
    }

    /** 52 kartlık deste, karıştırılmış. */
    public static List<Integer> deck(Random rnd) {
        List<Integer> d = new ArrayList<Integer>();
        for (int s = 0; s < 4; s++) {
            for (int r = 2; r <= 14; r++) {
                d.add(make(s, r));
            }
        }
        Collections.shuffle(d, rnd);
        return d;
    }

    public static boolean has(int[] hand, int c) {
        for (int x : hand) {
            if (x == c) {
                return true;
            }
        }
        return false;
    }

    public static int[] remove(int[] hand, int c) {
        int[] out = new int[hand.length - 1];
        int k = 0;
        boolean done = false;
        for (int x : hand) {
            if (x == c && !done) {
                done = true;
                continue;
            }
            if (k < out.length) {
                out[k++] = x;
            }
        }
        return out;
    }

    public static int countSuit(int[] hand, int suit) {
        int n = 0;
        for (int x : hand) {
            if (suit(x) == suit) {
                n++;
            }
        }
        return n;
    }

    public static String name(int c) {
        return CardArt.SUIT_NAMES[suit(c)] + " " + CardArt.rankName(rank(c));
    }
}
