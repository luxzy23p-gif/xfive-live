package com.xfive.live.music;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mengubah file audio menjadi PCM 16-bit. Tidak memakai javax.sound, jadi bisa jalan di Android.
 * WAV dibaca manual, OGG lewat stb_vorbis (bawaan LWJGL Minecraft), MP3 lewat JLayer.
 */
public final class AudioDecoder {

    public record Pcm(short[] samples, int channels, int sampleRate) {
    }

    private AudioDecoder() {
    }

    public static boolean supports(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".wav") || n.endsWith(".ogg") || n.endsWith(".mp3");
    }

    public static Pcm decode(Path file) throws IOException {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".wav")) {
            return decodeWav(Files.readAllBytes(file));
        }
        if (n.endsWith(".ogg")) {
            return decodeOgg(Files.readAllBytes(file));
        }
        if (n.endsWith(".mp3")) {
            return decodeMp3(file);
        }
        throw new IOException("Format tidak didukung: " + n);
    }

    private static Pcm decodeWav(byte[] b) throws IOException {
        if (b.length < 12 || !"RIFF".equals(ascii(b, 0)) || !"WAVE".equals(ascii(b, 8))) {
            throw new IOException("Bukan file WAV yang valid");
        }
        int pos = 12;
        int channels = 0;
        int sampleRate = 0;
        int bits = 0;
        int format = 0;
        while (pos + 8 <= b.length) {
            String id = ascii(b, pos);
            int size = le32(b, pos + 4);
            int body = pos + 8;
            if ("fmt ".equals(id)) {
                format = le16(b, body);
                channels = le16(b, body + 2);
                sampleRate = le32(b, body + 4);
                bits = le16(b, body + 14);
            } else if ("data".equals(id)) {
                if (channels == 0) {
                    throw new IOException("WAV tidak punya header fmt");
                }
                if (format != 1 || bits != 16) {
                    throw new IOException("WAV harus PCM 16-bit (sekarang " + bits + "-bit)");
                }
                int len = Math.min(size < 0 ? b.length - body : size, b.length - body);
                short[] s = new short[len / 2];
                for (int i = 0; i < s.length; i++) {
                    s[i] = (short) le16(b, body + i * 2);
                }
                return new Pcm(s, channels, sampleRate);
            }
            pos = body + size + (size & 1);
        }
        throw new IOException("WAV tidak punya chunk data");
    }

    private static Pcm decodeOgg(byte[] bytes) throws IOException {
        ByteBuffer in = ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.nativeOrder());
        in.put(bytes).flip();
        IntBuffer channels = BufferUtils.createIntBuffer(1);
        IntBuffer rate = BufferUtils.createIntBuffer(1);
        ShortBuffer out = STBVorbis.stb_vorbis_decode_memory(in, channels, rate);
        if (out == null) {
            throw new IOException("OGG tidak bisa didekode");
        }
        try {
            short[] s = new short[out.remaining()];
            out.get(s);
            return new Pcm(s, channels.get(0), rate.get(0));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    private static Pcm decodeMp3(Path file) throws IOException {
        List<short[]> chunks = new ArrayList<>();
        int channels = 0;
        int rate = 0;
        int total = 0;
        try (InputStream in = Files.newInputStream(file)) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();
            while (true) {
                Header header = bitstream.readFrame();
                if (header == null) {
                    break;
                }
                SampleBuffer frame = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (channels == 0) {
                    channels = frame.getChannelCount();
                    rate = frame.getSampleFrequency();
                }
                short[] buf = frame.getBuffer();
                int len = frame.getBufferLength();
                short[] copy = new short[len];
                System.arraycopy(buf, 0, copy, 0, len);
                chunks.add(copy);
                total += len;
                bitstream.closeFrame();
            }
        } catch (Exception e) {
            throw new IOException("MP3 gagal didekode: " + e.getMessage(), e);
        }
        if (total == 0) {
            throw new IOException("MP3 kosong atau tidak bisa dibaca");
        }
        short[] all = new short[total];
        int off = 0;
        for (short[] c : chunks) {
            System.arraycopy(c, 0, all, off, c.length);
            off += c.length;
        }
        return new Pcm(all, channels, rate);
    }

    private static String ascii(byte[] b, int off) {
        return new String(b, off, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int le16(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static int le32(byte[] b, int off) {
        return le16(b, off) | (le16(b, off + 2) << 16);
    }
}
