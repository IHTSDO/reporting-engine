package org.ihtsdo.termserver.scripting.util;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

import static org.ihtsdo.termserver.scripting.util.SnomedUtils.translateAcceptability;

public class LanguageHelper implements RF2Constants {

	private static final Logger LOGGER = LoggerFactory.getLogger(LanguageHelper.class);

	private LanguageHelper() {
		//No need to instantiate, all methods are static
	}

	private static final BiMap<String, String> knownPrimaryLanguageReferenceSets = HashBiMap.create();
	static {
		knownPrimaryLanguageReferenceSets.put("fr", "722131000");
		knownPrimaryLanguageReferenceSets.put("gb", GB_ENG_LANG_REFSET);
		knownPrimaryLanguageReferenceSets.put("us", US_ENG_LANG_REFSET);
	}

	public static void setLangResetOverride(String lang, String refsetId) {
		knownPrimaryLanguageReferenceSets.put(lang, refsetId);
	}

	public static boolean isLanguageCode(String lang) {
		return knownPrimaryLanguageReferenceSets.containsKey(lang);
	}

	public static Description alignToLanguage(Description d, String lang) throws TermServerScriptException {
		if (knownPrimaryLanguageReferenceSets.containsKey(lang)) {
			d.setLang(lang);
			//We'll change the US LangRefset and delete anything else
			//Loop round a copy of the refset so we don't get lost when we delete one
			for (LangRefsetEntry l : new ArrayList<>(d.getLangRefsetEntries())) {
				if (l.getRefsetId().equals(US_ENG_LANG_REFSET)) {
					l.setRefsetId(getLangRefset(lang));
				} else {
					d.removeLangRefsetEntry(l);
				}
			}
			d.calculateAcceptabilityMap();
		} else {
			throw new IllegalArgumentException("Language code not recognised: " + lang);
		}
		return d;
	}

	public static String getLangRefset(String lang) {
		return knownPrimaryLanguageReferenceSets.get(lang);
	}

	public static String getLang(String refsetid) {
		return knownPrimaryLanguageReferenceSets.inverse().get(refsetid);
	}

	public static String toString(Map<String, Acceptability> acceptabilityMap) {
		if (acceptabilityMap == null) {
			return "";
		}
		try {
			StringBuilder sb = new StringBuilder();
			boolean isFirst = true;
			for (Map.Entry<String, Acceptability> entry : acceptabilityMap.entrySet()) {
				if (!isFirst) {
					sb.append(", ");
				}
				sb.append(getLang(entry.getKey()).toUpperCase());
				sb.append(": ");
				sb.append(translateAcceptability(entry.getValue()));
				isFirst = false;
			}

			return sb.toString();
		} catch (TermServerScriptException e) {
			LOGGER.error("Failed to convert acceptability map to string ", e);
		}
		return "";
	}
}
