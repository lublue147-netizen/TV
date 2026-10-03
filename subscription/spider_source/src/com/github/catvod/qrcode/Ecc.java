package com.github.catvod.qrcode;

/**
 * The error correction level in a QR Code symbol.
 */
public final class Ecc {
	public static final Ecc LOW      = new Ecc(0, 1);
	public static final Ecc MEDIUM   = new Ecc(1, 0);
	public static final Ecc QUARTILE = new Ecc(2, 3);
	public static final Ecc HIGH     = new Ecc(3, 2);

	private static final Ecc[] VALUES = {LOW, MEDIUM, QUARTILE, HIGH};

	public static Ecc[] values() {
		return VALUES.clone();
	}

	public int ordinal() {
		return ordinal;
	}

	private final int ordinal;
	final int formatBits;

	private Ecc(int ordinal, int fb) {
		this.ordinal = ordinal;
		this.formatBits = fb;
	}
}
