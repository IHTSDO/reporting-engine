package org.ihtsdo.termserver.scripting.util;

public class RandomWords {

	public static String generate() {
		String words = "";
		for (int i = 0; i < randomWordNumber(); i++) {
			String word = "";
			for (int a = 0; a < randomWordLength(); a++) {
				word += randomLetter();
			}
			words += word + " ";
		}
		return words.replaceFirst(" $", "");
	}

	private static char randomLetter() {
		return (char) (Math.round(Math.random() * 25) + (int)'a');
	}

	private static long randomWordNumber() {
		return Math.round((Math.random() * 10) / 3) + 1;
	}

	private static long randomWordLength() {
		return Math.round(Math.random() * 10) + 3;
	}

}
