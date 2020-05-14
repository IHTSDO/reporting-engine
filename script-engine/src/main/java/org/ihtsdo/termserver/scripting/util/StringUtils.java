package org.ihtsdo.termserver.scripting.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.ihtsdo.termserver.scripting.domain.RF2Constants;

import com.google.common.base.Splitter;

public class StringUtils implements RF2Constants {

	public static List<String> removeBlankLines(List<String> lines) {
		List<String> unixLines = new ArrayList<String>();
		for (String thisLine : lines) {
			if (!thisLine.isEmpty()) {
				unixLines.add(thisLine);
			}
		}
		return unixLines;
	}
	
	public static boolean isEmpty(final String string) {
		if (string == null || string.length() == 0) {
			return true;
		}
		
		for (int i = 0; i < string.length(); i++) {
			if (!Character.isWhitespace(string.charAt(i))) {
				return false;
			}
		}
		
		return true;
	}
	
	public static boolean initialLetterLowerCase(String term) {
		String first = term.substring(0,1);
		
		//Being a number doesn't make you lower case
		if (!Character.isLetter(first.charAt(0))) {
			return false;
		}
		return first.equals(first.toLowerCase());
	}

	public static boolean isCaseSensitive(String term) {
		String afterFirst = term.substring(1);
		boolean allLowerCase = afterFirst.equals(afterFirst.toLowerCase());
		
		//Also case sensitive if we start with a lower case letter
		return !allLowerCase || initialLetterLowerCase(term);
	}
	
	/**
	 * Capitalizes the first letter of the passed in string. If the passed word
	 * is an empty word or contains only whitespace characters, then this passed
	 * word is returned. If the first letter is already capitalized returns the
	 * passed word. Otherwise capitalizes the first letter of the this word.
	 * 
	 * @param word
	 * @return
	 */
	public static String capitalizeFirstLetter(final String word) {
		if (isEmpty(word)) return word;
		if (Character.isUpperCase(word.charAt(0)))
			return word;
		if (word.length() == 1)
			return word.toUpperCase();
		return Character.toUpperCase(word.charAt(0)) + word.substring(1);
	}

	public static String deCapitalize (String str) {
		if (str == null || str.isEmpty() || str.length() < 2) {
			return str;
		}
		return str.substring(0, 1).toLowerCase() + str.substring(1);
	}

	public static CaseSignificance calculateCaseSignificance(String term) {
		//Any term that starts with a lower case letter
		//can be considered CS.   Otherwise if it is case sensitive then cI
		String firstLetter = term.substring(0, 1);
		if (firstLetter.equals(firstLetter.toLowerCase())) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		} else if (isCaseSensitive(term)) {
			return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
		}
		return CaseSignificance.CASE_INSENSITIVE;
	}

	public static String capitalize (String str) {
		if (str == null || str.isEmpty() || str.length() < 2) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}

	public static String substitute(String str, Map<String, String> wordSubstitution) {
		//Replace any instances of the map key with the corresponding value
		for (Map.Entry<String, String> substitution : wordSubstitution.entrySet()) {
			//Check for the word existing in lower case, and then replace with same setting
			if (str.toLowerCase().contains(substitution.getKey().toLowerCase())) {
				//Did we match as is, do direct replacement if so
				if (str.contains(substitution.getKey())) {
					str = str.replace(substitution.getKey(), substitution.getValue());
				} else {
					//Otherwise, we should capitalize
					String find = capitalize(substitution.getKey());
					String subst = capitalize(substitution.getValue());
					str = str.replace(find, subst);
				}
			}
		}
		return str;
	}

	static void remove (StringBuffer haystack, char needle) {
		for (int idx = 0; idx < haystack.length(); idx++) {
			if (haystack.charAt(idx) == needle) {
				haystack.deleteCharAt(idx);
				idx --;
			}
		}
	}

	static int indexOf (StringBuffer haystack, char[] needles, int startFrom) {
		for (int idx = startFrom; idx < haystack.length(); idx++) {
			for (char thisNeedle : needles) {
				if (haystack.charAt(idx) == thisNeedle) {
					return idx;
				}
			}
		}
		return -1;
	}

	public static List<String> csvSplit(String line) {
		List<String> list = new ArrayList<>();
		for(String item : Splitter.on(csvPattern).split(line)) {
			//Trim leading and trailing double quotes - not needed as already split
			if (line.charAt(0)=='"') {
				item = line.substring(1,item.length()-1);
			}
			list.add(item);
		}
		return list;
	}

	public static List<Object> csvSplitAsObject(String line) {
		List<Object> list = new ArrayList<>();
		for(String item : Splitter.on(csvPattern).split(line)) {
			//Trim leading and trailing double quotes - not needed as already split
			if (item.length() > 0) {
				if (item.charAt(0)=='"') {
					item = item.substring(1,item.length()-1);
				}
			} else {
				item = "";
			}
			// This is required as valid CSV will have "" for a quote
			// i.e "F1", "F2", "F-""sometect""-END"
			item = item.replaceAll("\"\"", "\"");
			list.add(item);
		}
		return list;
	}

	private static Pattern csvPattern = Pattern.compile(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

	/*
	 * @return the search term matched
	 */
	public static String containsAny(String term, String[] searches) {
		for (String search : searches) {
			if (term.contains(search)) {
				return search;
			}
		}
		return null;
	}
	
	//Taken from org.apache.commons.lang to avoid name conflict with StringUtils
	public static boolean isNumeric(String str) {
		if (str == null) {
			return false;
		}
		int sz = str.length();
		for (int i = 0; i < sz; i++) {
			if (Character.isDigit(str.charAt(i)) == false) {
				return false;
			}
		}
		return true;
	}

	public static String safelyTrim(String str) {
		if (StringUtils.isEmpty(str)) {
			return "";
		}
		return str.trim();
	}
	
	public static boolean containsSingleLetter(String term) {
		for (int i=1; i<term.length(); i++) {
			//Note that we're not going to count the s in "Smith's" as a single letter
			if (Character.isLetter(term.charAt(i)) 
					&& (!Character.isLetter(term.charAt(i-1)) && term.charAt(i-1) != '\'' )
					&& (i == term.length() -1 || !Character.isLetter(term.charAt(i+1)))) {
						return true;
					}
		}
		return false;
	}
	
	public static boolean containsSingleLowerCaseLetter(String term) {
		for (int i=1; i<term.length(); i++) {
			//Note that we're not going to count the s in "Smith's" as a single letter
			if (Character.isLetter(term.charAt(i))
					&& Character.isLowerCase(term.charAt(i)) 
					&& (!Character.isLetter(term.charAt(i-1)) && term.charAt(i-1) != '\'' )
					&& (i == term.length() -1 || !Character.isLetter(term.charAt(i+1)))) {
						return true;
					}
		}
		return false;
	}

}
