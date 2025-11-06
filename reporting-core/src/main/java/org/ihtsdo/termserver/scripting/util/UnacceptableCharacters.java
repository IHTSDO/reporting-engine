package org.ihtsdo.termserver.scripting.util;

public class UnacceptableCharacters {

	private UnacceptableCharacters() {
		// Prevent instantiation
	}

	public static final String NBSPSTR = "\u00A0";
	public static final String ZEROSP = "\u200B";
	public static final String LONG_DASH = "—";
	public static final String EN_DASH = "\u2013";
	public static final String EM_DASH = "\u2014";
	public static final String RIGHT_APOS = "\u2019";
	public static final String LEFT_APOS = "\u2018";
	public static final String RIGHT_QUOTE = "\u201D";
	public static final String LEFT_QUOTE = "\u201C";
	public static final String GRAVE_ACCENT = "\u0060";
	public static final String ACUTE_ACCENT = "\u00B4";
	public static final String SOFT_HYPHEN = "\u00AD";

}
