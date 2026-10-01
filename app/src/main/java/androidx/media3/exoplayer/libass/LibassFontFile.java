package androidx.media3.exoplayer.libass;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

public final class LibassFontFile {

    private static final int NAME_ID_FAMILY = 1;
    private static final int NAME_ID_PREFERRED_FAMILY = 16;

    private LibassFontFile() {
    }

    @Nullable
    public static String getFamilyName(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            return readFamilyName(raf);
        } catch (IOException e) {
            throw new IOException("Failed to read font family name", e);
        }
    }

    @Nullable
    private static String readFamilyName(RandomAccessFile raf) throws IOException {
        int numTables = seekNameTableOffsetTables(raf);
        if (numTables < 0) return null;
        long nameTableOffset = findNameTableOffset(raf, numTables);
        if (nameTableOffset < 0) return null;
        return readNameTableFamily(raf, nameTableOffset);
    }

    private static int seekNameTableOffsetTables(RandomAccessFile raf) throws IOException {
        long length = raf.length();
        if (length < 12) return -1;
        byte[] header = new byte[4];
        raf.seek(0);
        raf.readFully(header);
        if (!matchesSfnt(header)) return -1;
        int numTables = raf.readUnsignedShort();
        return numTables;
    }

    private static boolean matchesSfnt(byte[] header) {
        if (header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00) return true;
        return (header[0] == 'O' && header[1] == 'T' && header[2] == 'T' && header[3] == 'O')
                || (header[0] == 't' && header[1] == 'r' && header[2] == 'u' && header[3] == 'e')
                || (header[0] == 't' && header[1] == 't' && header[2] == 'c' && header[3] == 'f');
    }

    private static long findNameTableOffset(RandomAccessFile raf, int numTables) throws IOException {
        for (int i = 0; i < numTables; i++) {
            byte[] tag = new byte[4];
            raf.readFully(tag);
            long checksum = Integer.toUnsignedLong(raf.readInt());
            long offset = Integer.toUnsignedLong(raf.readInt());
            long tableLength = Integer.toUnsignedLong(raf.readInt());
            if (tag[0] == 'n' && tag[1] == 'a' && tag[2] == 'm' && tag[3] == 'e') return offset;
            if (checksum == 0 && offset == 0 && tableLength == 0) break;
        }
        return -1;
    }

    @Nullable
    private static String readNameTableFamily(RandomAccessFile raf, long nameTableOffset) throws IOException {
        raf.seek(nameTableOffset + 2);
        int count = raf.readUnsignedShort();
        int stringOffset = raf.readUnsignedShort();
        if (count <= 0 || count > 200) return null;
        String best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int platformId = raf.readUnsignedShort();
            int encodingId = raf.readUnsignedShort();
            int languageId = raf.readUnsignedShort();
            int nameId = raf.readUnsignedShort();
            int stringLength = raf.readUnsignedShort();
            int stringOffsetFromBase = raf.readUnsignedShort();
            if (nameId != NAME_ID_FAMILY && nameId != NAME_ID_PREFERRED_FAMILY) continue;
            byte[] raw = new byte[stringLength];
            raf.seek(nameTableOffset + stringOffset + stringOffsetFromBase);
            raf.readFully(raw);
            String value = decodeName(platformId, encodingId, raw);
            if (value == null || value.isEmpty()) continue;
            int priority = scoreName(platformId, encodingId, languageId, nameId);
            if (priority < bestPriority) {
                bestPriority = priority;
                best = value;
            }
        }
        return best;
    }

    @Nullable
    private static String decodeName(int platformId, int encodingId, byte[] raw) {
        if (platformId == 3) {
            if (raw.length % 2 != 0) return null;
            return new String(raw, StandardCharsets.UTF_16BE);
        }
        if (platformId == 1) {
            return new String(raw, StandardCharsets.ISO_8859_1);
        }
        return null;
    }

    private static int scoreName(int platformId, int encodingId, int languageId, int nameId) {
        int score = 0;
        if (platformId == 3) {
            score += 0;
            if (encodingId == 1) score += 0;
            else score += 10;
            score += languageId == 0x0409 ? 0 : 20;
        } else {
            score += 50;
        }
        if (nameId == NAME_ID_FAMILY) score += 0;
        else score += 5;
        return score;
    }
}
