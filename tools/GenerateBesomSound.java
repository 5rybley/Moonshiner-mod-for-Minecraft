import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * Synthesises the sound of a wet birch besom slapping skin in a hot banya: a
 * low wet thump, a leafy rustle and the short hiss of water flashing off the
 * hot switches, with a soft tail of steam.
 *
 * <p>The same recipe as the other mod sounds: build the waveform here, then
 * compress it to Ogg Vorbis once with libvorbis. Run from the project root:</p>
 *
 * <pre>
 * java tools/GenerateBesomSound.java
 * ffmpeg -y -i build/besom_sound/besom_hit.wav -c:a libvorbis -q:a 5 \
 *        -ar 44100 -ac 1 src/main/resources/assets/samogon/sounds/besom_hit.ogg
 * </pre>
 *
 * <p>The generator is deterministic: the same seed always yields the same
 * file, so the shipped sound can be reproduced exactly.</p>
 */
public final class GenerateBesomSound {
    private static final int RATE = 44100;
    private static final double SECONDS = 0.85;
    private static final Path OUT = Path.of("build/besom_sound/besom_hit.wav");

    private GenerateBesomSound() {}

    public static void main(String[] args) throws Exception {
        int n = (int) (RATE * SECONDS);
        Random random = new Random(0x5A3B09L);
        double[] white = noise(n, random);
        double[] mix = new double[n];

        // 1. The slap itself: a low body tone dropping from 128 to 70 Hz.
        double phase = 0;
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double freq = 128.0 - 58.0 * Math.min(1.0, t / 0.09);
            phase += 2.0 * Math.PI * freq / RATE;
            double body = Math.sin(phase) * Math.exp(-t / 0.045) * (1.0 - Math.exp(-t / 0.003));
            mix[i] += 0.42 * body;
        }

        // 2. The wet smack: low noise, gone in a blink.
        double[] smack = lowpass(white, 800);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            mix[i] += 0.26 * smack[i] * env(t, 0.003, 0.055);
        }

        // 3. The leaves: a band of noise around 1.8 kHz, rattled by a second,
        //    slow noise so it sounds like many switches and not one hiss.
        double[] rattleNoise = lowpass(noise(n, random), 45);
        double rattlePeak = 1.0e-9;
        for (double v : rattleNoise) rattlePeak = Math.max(rattlePeak, Math.abs(v));
        double[] rustle = bandpass(white, 1800, 0.7);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            double rattle = 0.55 + 0.9 * Math.abs(rattleNoise[i]) / rattlePeak;
            mix[i] += 0.22 * rustle[i] * rattle * env(t, 0.006, 0.09);
        }

        // 4. The hiss: high noise with a sizzling resonance just above 6 kHz,
        //    starting a moment after the slap and drying out over a quarter second.
        double[] hissBand = highpass(white, 3200);
        double[] sizzle = bandpass(white, 6200, 2.5);
        int hissStart = (int) (0.02 * RATE);
        for (int i = hissStart; i < n; i++) {
            double t = (i - hissStart) / (double) RATE;
            double hiss = hissBand[i] + 0.7 * sizzle[i];
            mix[i] += 0.34 * hiss * env(t, 0.025, 0.30);
        }

        // 5. A quiet breath of steam left hanging after the blow.
        double[] air = lowpass(noise(n, random), 2600);
        for (int i = 0; i < n; i++) {
            double t = i / (double) RATE;
            mix[i] += 0.055 * air[i] * env(t, 0.02, 0.4);
        }

        // Remove DC, normalise, then fade the tail so the file never clicks.
        double[] clean = highpass(mix, 35);
        double peak = 1.0e-9;
        for (double v : clean) peak = Math.max(peak, Math.abs(v));
        int fade = (int) (0.07 * RATE);
        for (int i = 0; i < n; i++) {
            double v = clean[i] / peak * 0.9;
            int left = n - 1 - i;
            if (left < fade) v *= left / (double) fade;
            clean[i] = v;
        }

        Files.createDirectories(OUT.getParent());
        writeWav(clean);
        System.out.println("wrote " + OUT + " (" + n + " samples, peak "
                + String.format("%.2f", peak) + ")");
    }

    /** White noise in [-1, 1]. */
    private static double[] noise(int n, Random random) {
        double[] samples = new double[n];
        for (int i = 0; i < n; i++) samples[i] = random.nextDouble() * 2.0 - 1.0;
        return samples;
    }

    /** Attack/decay envelope: silent at zero, a soft rise, then exponential decay. */
    private static double env(double t, double attack, double tau) {
        if (t < 0) return 0;
        return (1.0 - Math.exp(-t / attack)) * Math.exp(-t / tau);
    }

    /** One-pole low-pass. */
    private static double[] lowpass(double[] input, double cutoff) {
        double dt = 1.0 / RATE;
        double rc = 1.0 / (2.0 * Math.PI * cutoff);
        double a = dt / (rc + dt);
        double[] out = new double[input.length];
        double prev = 0;
        for (int i = 0; i < input.length; i++) {
            prev += a * (input[i] - prev);
            out[i] = prev;
        }
        return out;
    }

    /** One-pole high-pass: the input minus its low-passed self. */
    private static double[] highpass(double[] input, double cutoff) {
        double[] low = lowpass(input, cutoff);
        double[] out = new double[input.length];
        for (int i = 0; i < input.length; i++) out[i] = input[i] - low[i];
        return out;
    }

    /** RBJ biquad band-pass, unity gain at the centre frequency. */
    private static double[] bandpass(double[] input, double centre, double q) {
        double w0 = 2.0 * Math.PI * centre / RATE;
        double alpha = Math.sin(w0) / (2.0 * q);
        double a0 = 1.0 + alpha;
        double b0 = alpha / a0;
        double b2 = -alpha / a0;
        double a1 = -2.0 * Math.cos(w0) / a0;
        double a2 = (1.0 - alpha) / a0;
        double[] out = new double[input.length];
        double x1 = 0;
        double x2 = 0;
        double y1 = 0;
        double y2 = 0;
        for (int i = 0; i < input.length; i++) {
            double x = input[i];
            double y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x;
            y2 = y1;
            y1 = y;
            out[i] = y;
        }
        return out;
    }

    /** 16-bit mono little-endian WAV. */
    private static void writeWav(double[] samples) throws Exception {
        byte[] pcm = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int v = (int) Math.round(Math.max(-1.0, Math.min(1.0, samples[i])) * 32767.0);
            pcm[i * 2] = (byte) (v & 0xFF);
            pcm[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (AudioInputStream stream =
                new AudioInputStream(new ByteArrayInputStream(pcm), format, samples.length)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, OUT.toFile());
        }
    }
}