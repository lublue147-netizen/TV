package com.github.catvod.qrcode;

/**
 * The error correction level in a QR Code symbol.
 */
public enum Ecc {
	// Must be declared in ascending order of error protection
	// so that the implicit ordinal() and values() work properly
	/** The QR Code can tolerate about  7% erroneous codewords. */ LOW(1),
	/** The QR Code can tolerate about 15% erroneous codewords. */ MEDIUM(0),
	/** The QR Code can tolerate about 25% erroneous codewords. */ QUARTILE(3),
	/** The QR Code can tolerate about 30% erroneous codewords. */ HIGH(2);
	
	// In the range 0 to 3 (unsigned 2-bit integer).
	final int formatBits;
	
	// Constructor.
	private Ecc(int fb) {
		formatBits = fb;
	}
}
