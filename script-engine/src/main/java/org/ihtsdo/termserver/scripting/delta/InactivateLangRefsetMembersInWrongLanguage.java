package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InactivateLangRefsetMembersInWrongLanguage extends DeltaGenerator implements ScriptConstants {

	/*private String refsetToCheck = "2021000195106";  //FR-CH Langrefset id
	private String expectedLangCode = "fr";*/
	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateLangRefsetMembersInWrongLanguage.class);

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		InactivateLangRefsetMembersInWrongLanguage delta = new InactivateLangRefsetMembersInWrongLanguage();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			TermServerClient.supportsIncludeUnpublished = false;   //This code not yet available in MS
			delta.init(args);
			delta.loadProjectSnapshot(false);  //Just FSN, not working with all descriptions here
			delta.postInit();
			delta.startTimer();
			delta.process();
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				LOGGER.info(delta.descIdGenerator.finish());
			}
		}
	}

	private void process() throws TermServerScriptException {
		Map<String, String> refsetLangCodeMap = generateRefsetLangCodeMap();

		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				//It's OK - for example - to have an English term in the Dutch LangRefset
				//So skip 'en' terms, unless it's the FSN
				if (d.getType().equals(DescriptionType.FSN) || !d.getLang().equals("en")) {
					for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
						String expectedLangCode = refsetLangCodeMap.get(l.getRefsetId());
						if (expectedLangCode == null) {
							throw new TermServerScriptException("Unable to determine appropriate langCode for Langrefset: " + gl.getConcept(l.getRefsetId()));
						}
						if (!d.getLang().equals(expectedLangCode)) {
							l.setActive(false);
							l.setEffectiveTime(null);
							c.setModified();
							report(c, c.getFSNDescription(), Severity.LOW, ReportActionType.LANG_REFSET_INACTIVATED, d, l);
						}
					}
				}
			}
		/*}



		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				//Skip any descriptions in the expected language
				if (d.getLang().equals(expectedLangCode)) {
					continue;
				}
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE, refsetToCheck)) {
					if (l.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
						l.setActive(false);
						l.setEffectiveTime(null);
						c.setModified();
						report(c, Severity.LOW, ReportActionType.LANG_REFSET_INACTIVATED, d, l);
					}
				}
			}*/

			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(c);  //Will only output dirty fields.
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
