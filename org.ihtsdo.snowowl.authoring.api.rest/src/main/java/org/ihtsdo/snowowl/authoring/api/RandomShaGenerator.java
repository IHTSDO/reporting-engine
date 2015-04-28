package org.ihtsdo.snowowl.authoring.api;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.UUID;

public class RandomShaGenerator {

	private static final String SHA_1 = "SHA-1";
	private static final String FORMAT = "%02x";

	public String generateRandomSha() {
		String sha = shaSum(UUID.randomUUID().toString().getBytes());
		return sha.substring(0, 6);
	}

	private String shaSum(byte[] bytes) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance(SHA_1);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(SHA_1 + " algorithm not implemented.", e);
		}
		return byteArray2Hex(md.digest(bytes));
	}

	private String byteArray2Hex(final byte[] hash) {
		Formatter formatter = new Formatter();
		for (byte b : hash) {
			formatter.format(FORMAT, b);
		}
		return formatter.toString();
	}

}
