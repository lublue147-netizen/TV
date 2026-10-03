package com.github.catvod.qrcode;

public class QrUtil {

    private static final char[] B64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    public static byte[] createBmp(String text, int scale, int margin) {
        if (text == null || text.isEmpty()) return null;
        try {
            QrCode qr = QrCode.encodeText(text, QrCode.Ecc.MEDIUM);
            int n = qr.size;
            int width = (n + margin * 2) * scale;
            int height = width;
            int rowSize = ((width * 3 + 3) / 4) * 4;
            int imageSize = rowSize * height;
            int fileSize = 54 + imageSize;

            byte[] bmp = new byte[fileSize];
            // BMP Header (14 bytes)
            bmp[0] = 'B';
            bmp[1] = 'M';
            writeInt(bmp, 2, fileSize);
            writeInt(bmp, 6, 0);
            writeInt(bmp, 10, 54);

            // DIB Header (40 bytes - BITMAPINFOHEADER)
            writeInt(bmp, 14, 40);
            writeInt(bmp, 18, width);
            writeInt(bmp, 22, height);
            writeShort(bmp, 26, (short) 1);
            writeShort(bmp, 28, (short) 24);
            writeInt(bmp, 30, 0); // BI_RGB
            writeInt(bmp, 34, imageSize);
            writeInt(bmp, 38, 2835); // 72 DPI
            writeInt(bmp, 42, 2835);
            writeInt(bmp, 46, 0);
            writeInt(bmp, 50, 0);

            // Pixel data: bottom to top
            int offset = 54;
            for (int y = height - 1; y >= 0; y--) {
                int my = (y / scale) - margin;
                int rowStart = offset;
                for (int x = 0; x < width; x++) {
                    int mx = (x / scale) - margin;
                    boolean isDark = false;
                    if (my >= 0 && my < n && mx >= 0 && mx < n) {
                        isDark = qr.getModule(mx, my);
                    }
                    byte color = isDark ? (byte) 0 : (byte) 255;
                    bmp[offset++] = color;
                    bmp[offset++] = color;
                    bmp[offset++] = color;
                }
                int pad = rowSize - (width * 3);
                for (int p = 0; p < pad; p++) {
                    bmp[offset++] = 0;
                }
            }
            return bmp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String createDataUri(String text) {
        byte[] bmp = createBmp(text, 6, 2);
        if (bmp != null) {
            return "data:image/bmp;base64," + encodeBase64(bmp);
        }
        return "";
    }

    public static String encodeBase64(byte[] data) {
        if (data == null) return "";
        StringBuilder sb = new StringBuilder((data.length * 4) / 3 + 4);
        int i = 0;
        while (i < data.length) {
            int b0 = data[i++] & 0xFF;
            int b1 = i < data.length ? data[i++] & 0xFF : -1;
            int b2 = i < data.length ? data[i++] & 0xFF : -1;
            sb.append(B64_CHARS[b0 >>> 2]);
            if (b1 != -1) {
                sb.append(B64_CHARS[((b0 & 0x03) << 4) | (b1 >>> 4)]);
                if (b2 != -1) {
                    sb.append(B64_CHARS[((b1 & 0x0F) << 2) | (b2 >>> 6)]);
                    sb.append(B64_CHARS[b2 & 0x3F]);
                } else {
                    sb.append(B64_CHARS[(b1 & 0x0F) << 2]);
                    sb.append('=');
                }
            } else {
                sb.append(B64_CHARS[(b0 & 0x03) << 4]);
                sb.append("==");
            }
        }
        return sb.toString();
    }

    public static byte[] decodeBase64(String s) {
        if (s == null || s.isEmpty()) return new byte[0];
        try {
            int len = s.length();
            while (len > 0 && s.charAt(len - 1) == '=') len--;
            int b64len = len;
            int byteLen = (b64len * 3) / 4;
            byte[] out = new byte[byteLen];
            int outIdx = 0;
            int buf = 0;
            int bits = 0;
            for (int i = 0; i < len; i++) {
                char c = s.charAt(i);
                int val = decodeChar(c);
                if (val < 0) continue;
                buf = (buf << 6) | val;
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    out[outIdx++] = (byte) ((buf >> bits) & 0xFF);
                }
            }
            if (outIdx == out.length) return out;
            byte[] trimmed = new byte[outIdx];
            System.arraycopy(out, 0, trimmed, 0, outIdx);
            return trimmed;
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static int decodeChar(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '+') return 62;
        if (c == '/') return 63;
        return -1;
    }

    private static void writeInt(byte[] b, int offset, int val) {
        b[offset] = (byte) (val & 0xFF);
        b[offset + 1] = (byte) ((val >> 8) & 0xFF);
        b[offset + 2] = (byte) ((val >> 16) & 0xFF);
        b[offset + 3] = (byte) ((val >> 24) & 0xFF);
    }

    private static void writeShort(byte[] b, int offset, short val) {
        b[offset] = (byte) (val & 0xFF);
        b[offset + 1] = (byte) ((val >> 8) & 0xFF);
    }
}
