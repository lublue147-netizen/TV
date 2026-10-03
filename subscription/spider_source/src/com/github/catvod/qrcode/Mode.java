package com.github.catvod.qrcode;

/**
 * Describes how a segment's data bits are interpreted.
 */
public final class Mode {
	
	/*-- Constants --*/
	
	public static final Mode NUMERIC     = new Mode(0x1, 10, 12, 14);
	public static final Mode ALPHANUMERIC= new Mode(0x2,  9, 11, 13);
	public static final Mode BYTE        = new Mode(0x4,  8, 16, 16);
	public static final Mode KANJI       = new Mode(0x8,  8, 10, 12);
	public static final Mode ECI         = new Mode(0x7,  0,  0,  0);
	
	
	/*-- Fields --*/
	
	// The mode indicator bits, which is a uint4 value (range 0 to 15).
	final int modeBits;
	
	// Number of character count bits for three different version ranges.
	private final int[] numBitsCharCount;
	
	
	/*-- Constructor --*/
	
	private Mode(int mode, int... ccbits) {
		modeBits = mode;
		numBitsCharCount = ccbits;
	}
	
	
	/*-- Method --*/
	
	// Returns the bit width of the character count field for a segment in this mode
	// in a QR Code at the given version number. The result is in the range [0, 16].
	int numCharCountBits(int ver) {
		if (ver < QrCode.MIN_VERSION || ver > QrCode.MAX_VERSION) {
			throw new IllegalArgumentException("Version out of range: " + ver);
		}
		return numBitsCharCount[(ver + 7) / 17];
	}
	
}
