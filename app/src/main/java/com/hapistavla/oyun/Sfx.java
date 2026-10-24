package com.hapistavla.oyun;

import com.hapistavla.oyun.tavla.Game;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ses dosyası gerektirmeyen küçük ses efektleri: sesler kodla üretilir ve AudioTrack ile çalınır.
 */
public final class Sfx {
    private static final int RATE = 22050;
    private final short[][] sounds = new short[5][];
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private volatile boolean enabled = true;

    public Sfx() {
        // zar: kısa, tok vuruşlar
        sounds[Game.SND_ROLL] = rattle();
        // taş: tek tok "tık"
        sounds[Game.SND_MOVE] = thud(0, 90, 330);
        // hapis: iki tonlu vuruş
        sounds[Game.SND_PIN] = concat(thud(0, 80, 220), tone(0, 160, 520, 0.35));
        // kazanma: yükselen üç nota
        sounds[Game.SND_WIN] = concat(tone(0, 140, 523, 0.5), tone(0, 140, 659, 0.5), tone(0, 260, 784, 0.5));
        // kaybetme: alçalan iki nota
        sounds[Game.SND_LOSE] = concat(tone(0, 200, 392, 0.5), tone(0, 320, 294, 0.5));
    }

    public void setEnabled(boolean e) {
        enabled = e;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void play(final int kind) {
        if (!enabled || kind < 0 || kind >= sounds.length) {
            return;
        }
        final short[] data = sounds[kind];
        pool.execute(new Runnable() {
            @Override
            public void run() {
                AudioTrack t = null;
                try {
                    t = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, data.length * 2, AudioTrack.MODE_STATIC);
                    t.write(data, 0, data.length);
                    t.play();
                    Thread.sleep(data.length * 1000L / RATE + 60);
                } catch (Exception e) {
                    // ses çalınamazsa oyun etkilenmesin
                } finally {
                    if (t != null) {
                        try {
                            t.release();
                        } catch (Exception e) {
                            // yoksay
                        }
                    }
                }
            }
        });
    }

    public void release() {
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Ses üretimi
    // ------------------------------------------------------------------

    private static short[] concat(short[]... parts) {
        int n = 0;
        for (short[] p : parts) {
            n += p.length;
        }
        short[] r = new short[n];
        int o = 0;
        for (short[] p : parts) {
            System.arraycopy(p, 0, r, o, p.length);
            o += p.length;
        }
        return r;
    }

    /** Kısa, sert bir "tık" (zarın tahtaya çarpması): gürültü + tok ton. */
    private static void addClick(short[] buf, int startMs, int lenMs, double vol, double freq, long seed) {
        int d = RATE * startMs / 1000;
        int n = RATE * lenMs / 1000;
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < n && d + i < buf.length; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-14.0 * i / n);
            double noise = (rnd.nextDouble() * 2 - 1) * Math.exp(-22.0 * i / n);
            double v = (0.45 * Math.sin(2 * Math.PI * freq * t) * env + 0.75 * noise) * vol * 15000;
            int s = buf[d + i] + (int) v;
            buf[d + i] = (short) Math.max(-32000, Math.min(32000, s));
        }
    }

    /** Zar sesi: aralıkları açılan çarpmalar (sekme) ve sonda yerleşme. Süre ~0,75 sn. */
    private static short[] rattle() {
        short[] r = new short[RATE * 780 / 1000];
        int[] at = {0, 40, 85, 135, 195, 265, 345, 440, 560};
        double[] vol = {0.55, 0.7, 0.5, 0.75, 0.45, 0.7, 0.4, 0.6, 0.85};
        double[] fr = {420, 520, 380, 600, 440, 560, 400, 520, 300};
        for (int i = 0; i < at.length; i++) {
            addClick(r, at[i], 55, vol[i], fr[i], 100L + i * 17);
            addClick(r, at[i] + 7, 45, vol[i] * 0.8, fr[i] * 1.18, 300L + i * 29); // ikinci zar
        }
        return r;
    }

    /** Saf nota (hızlı başlangıç, yumuşak sönüm). */
    private static short[] tone(int delayMs, int lenMs, double freq, double vol) {
        int d = RATE * delayMs / 1000;
        int n = RATE * lenMs / 1000;
        short[] r = new short[d + n];
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.min(1.0, i / (RATE * 0.005)) * Math.exp(-4.0 * i / n);
            double v = Math.sin(2 * Math.PI * freq * t) + 0.3 * Math.sin(4 * Math.PI * freq * t);
            r[d + i] = (short) (v * env * vol * 14000);
        }
        return r;
    }

    /** Tahta üstüne düşen sert cisim sesi: kısa gürültü + alçak ton. */
    private static short[] thud(int delayMs, int lenMs, double freq) {
        int d = RATE * delayMs / 1000;
        int n = RATE * lenMs / 1000;
        short[] r = new short[d + n];
        long seed = 12345L + delayMs * 31L + (long) freq;
        java.util.Random rnd = new java.util.Random(seed);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double env = Math.exp(-9.0 * i / n);
            double noise = (rnd.nextDouble() * 2 - 1) * Math.exp(-30.0 * i / n);
            double v = 0.8 * Math.sin(2 * Math.PI * freq * t) * env + 0.5 * noise;
            r[d + i] = (short) (v * 16000);
        }
        return r;
    }
}
