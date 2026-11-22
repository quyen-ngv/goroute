package com.ds.goroute.utils;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Reads how long an MP4/MOV video is from its {@code moov/mvhd} header, without decoding it.
 *
 * <p>Phones put the {@code moov} box either before or after the media data; the media data is
 * skipped, not read, so both cost about the same. Anything that does not look like a plain
 * ISO media file (fragmented MP4 with an empty duration, WEBM, a truncated upload) gives
 * {@code null} rather than a guess.
 */
public final class Mp4Duration {

    /** Enough for any real file; stops a crafted one from looping on tiny boxes. */
    private static final int MAX_BOXES = 1000;

    private Mp4Duration() {
    }

    /**
     * Whole seconds, rounded to the nearest (a recording stopped at 60 s runs a frame or two
     * over), or null when the header cannot be read.
     */
    public static Integer seconds(InputStream input) {
        try {
            DataInputStream in = new DataInputStream(input);
            for (int boxes = 0; boxes < MAX_BOXES; boxes++) {
                long size = Integer.toUnsignedLong(in.readInt());
                String type = readType(in);
                long header = 8;
                if (size == 1) {
                    size = in.readLong();
                    header = 16;
                }
                if (size == 0 || size < header) {
                    return null;
                }
                if ("moov".equals(type)) {
                    // Step into moov rather than over it: mvhd is one of its children.
                    continue;
                }
                if ("mvhd".equals(type)) {
                    return readMvhd(in);
                }
                in.skipNBytes(size - header);
            }
            return null;
        } catch (IOException exception) {
            // EOFException included: a truncated or non-MP4 file has no readable length.
            return null;
        }
    }

    private static Integer readMvhd(DataInputStream in) throws IOException {
        int version = in.readUnsignedByte();
        in.skipNBytes(3);
        long timescale;
        long duration;
        if (version == 1) {
            in.skipNBytes(16);
            timescale = Integer.toUnsignedLong(in.readInt());
            duration = in.readLong();
        } else {
            in.skipNBytes(8);
            timescale = Integer.toUnsignedLong(in.readInt());
            duration = Integer.toUnsignedLong(in.readInt());
            if (duration == 0xFFFFFFFFL) {
                return null;
            }
        }
        if (timescale == 0 || duration <= 0) {
            return null;
        }
        return (int) Math.min(Integer.MAX_VALUE, (duration + timescale / 2) / timescale);
    }

    private static String readType(DataInputStream in) throws IOException {
        byte[] type = new byte[4];
        in.readFully(type);
        return new String(type, StandardCharsets.US_ASCII);
    }
}
