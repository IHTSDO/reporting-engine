package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.*;

import java.util.HashMap;
import java.util.Map;

public class InactivateLangRefsetMembersInWrongLanguage extends DeltaGenerator implements ScriptConstants {

	private static final boolean ALLOW_ENGLISH_TERMS_IN_LANG_REFSET = false;

	public static void main(String[] args) throws TermServerScriptException {
		InactivateLangRefsetMembersInWrongLanguage delta = new InactivateLangRefsetMembersInWrongLanguage();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing members
			TermServerClient.supportsIncludeUnpublished = false;   //This code not yet available in MS
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.startTimer();
			delta.process();
			delta.createOutputArchive();
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		Map<String, String> refsetLangCodeMap = generateRefsetLangCodeMap();
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				processDescription(c, d, refsetLangCodeMap);
			}

			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified");
			}
		}
	}

	private void processDescription(Concept c, Description d, Map<String, String> refsetLangCodeMap) throws TermServerScriptException {
		//It's OK - for example - to have an English term in the Dutch LangRefset
		//So skip 'en' terms, unless it's the FSN
		if (!ALLOW_ENGLISH_TERMS_IN_LANG_REFSET ||
				d.getType().equals(DescriptionType.FSN) ||
				!d.getLang().equals("en")) {
			for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
				String expectedLangCode = refsetLangCodeMap.get(l.getRefsetId());
				if (expectedLangCode == null) {
					throw new TermServerScriptException("Unable to determine appropriate langCode for Langrefset: " + gl.getConcept(l.getRefsetId()));
				}
				if (!d.getLang().equals(expectedLangCode)) {
					l.setActive(false);
					l.setEffectiveTime(null);
					c.setModified();
					report(c, Severity.LOW, ReportActionType.LANG_REFSET_INACTIVATED, d, l);
				}
			}
		}
	}

	private Map<String, String> generateRefsetLangCodeMap() {
		Map<String, String> refsetLangCodeMap = new HashMap<>();
		//First populate en-gb and en-us since we always know about those
		refsetLangCodeMap.put(US_ENG_LANG_REFSET, "en");
		refsetLangCodeMap.put(GB_ENG_LANG_REFSET, "en");

		//Now the optionalLanguageRefsets are laid out nicely
		Metadata metadata = project.getMetadata();
		refsetLangCodeMap.putAll(metadata.getLangRefsetLangMapping());
		return refsetLangCodeMap;
	}

}
