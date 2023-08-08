package org.ihtsdo.termserver.scripting.util;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RandomWords {

	private static final Logger LOGGER = LoggerFactory.getLogger(RandomWords.class);

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
