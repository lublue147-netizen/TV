package com.github.catvod.qrcode;

/**
 * Describes how a segment's data bits are interpreted.
 */
public enum Mode {
	
	/*-- Constants --*/
	
	NUMERIC     (0x1, 10, 12, 14),
	ALPHANUMERIC(0x2,  9, 11, 13),
	BYTE        (0x4,  8, 16, 16),
	KANJI       (0x8,  8, 10, 12),
	ECI         (0x7,  0,  0,  0);
	
	
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
		assert QrCode.MIN_VERSION <= ver && ver <= QrCode.MAX_VERSION;
		return numBitsCharCount[(ver + 7) / 17];
	}
	
}
