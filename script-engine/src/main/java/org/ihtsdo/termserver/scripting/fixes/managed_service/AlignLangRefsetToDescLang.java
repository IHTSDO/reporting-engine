package org.ihtsdo.termserver.scripting.fixes.managed_service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;


/*
MSSP-860 Address issue with language refset entries referring to description in wrong language
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlignLangRefsetToDescLang extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(AlignLangRefsetToDescLang.class);

	BiMap<String, String> refsetLangMap = HashBiMap.create();
	BiMap<String, String> gpRefsetLangMap = HashBiMap.create();
	Map<String, String> allRefsets = new HashMap<>();
	
	protected AlignLangRefsetToDescLang(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException {
		AlignLangRefsetToDescLang fix = new AlignLangRefsetToDescLang(null);
		try {
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		gpRefsetLangMap.put("701000172104", "nl");  //NL GP
		gpRefsetLangMap.put("711000172101" , "fr");  //FR GP
		refsetLangMap.put("31000172101", "nl");
		refsetLangMap.put("21000172104" , "fr");
		allRefsets.putAll(gpRefsetLangMap);
		allRefsets.putAll(refsetLangMap);
		super.init(args);
	}
	

	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept localConcept = gl.getConcept(c.getId());
		Collection<String> targetLanguages = refsetLangMap.values();
		for (Description d : localConcept.getDescriptions()) {
			if (d.isActive() && inScope(d) && targetLanguages.contains(d.getLang())) {
				//Does this description have language refset entries in the wrong refset?
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
					//Are we working with the GP LangRefset or the Traditional ones?
					boolean isGP = gpRefsetLangMap.containsKey(l.getRefsetId());
					BiMap<String, String> relevantMap = isGP ? gpRefsetLangMap : refsetLangMap;
					if (!relevantMap.get(l.getRefsetId()).equals(d.getLang())) {
						//Do we already have a langrefset entry for this description in the correct refset?
						if (d.hasAcceptability(Acceptability.BOTH, relevantMap.get(d.getLang()))) {
							//We already have an acceptability for this language
							deleteRefsetMember(t, l.getId());
							report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, "Redundant langrefset in wrong language", d, l);
						} else {
							//What was the acceptability of this entry
							Acceptability origAccept = SnomedUtils.translateAcceptability(l.getAcceptabilityId());
							LOGGER.debug("here");
							Description loadedDescription = c.getDescription(d.getId());
							Map<String, Acceptability> acceptabilityMap = loadedDescription.getAcceptabilityMap();
							acceptabilityMap.remove(l.getRefsetId());
							acceptabilityMap.put(relevantMap.inverse().get(d.getLang()), origAccept);
							d.setAcceptabilityMap(acceptabilityMap);
							String msg = "LangRefset switched from "+ relevantMap.get(l.getRefsetId()) + " to " + d.getLang();
							report(t, c, Severity.LOW, ReportActionType.LANG_REFSET_MODIFIED, msg, d, l);
							updateConcept(t, c, "");
						}
						changesMade++;
					}
				}
			}
		}
		validateDescriptions(t, c.getId());
		return changesMade;
	}


	private void validateDescriptions(Task t, String sctid) throws TermServerScriptException {
		Concept c = loadConcept(sctid, t.getBranchPath());
		//Ensure we only have one preferred term in each lang refset and no entries for an innappropriate language
		
		for (String refsetId : allRefsets.keySet()) {
			List<Description> descsPref = c.getDescriptions(refsetId, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
			List<Description> descs = c.getDescriptions(refsetId, Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE);
			if (descsPref.size() > 1) {
				String descStr = descsPref.stream().map(Description::toString).collect(Collectors.joining(",\n"));
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "POST-FIX! Concept has multiple description entries in a single refset", gl.getConcept(refsetId), descStr);
			}
			
			for (Description d : descs) {
				if (!d.getLang().equals(allRefsets.get(refsetId))) {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "POST-FIX! Description has wrong language refset entry", gl.getConcept(refsetId), d);
				}
			}
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Collection<String> targetLanguages = refsetLangMap.values();
		List<Component> conceptsToProcess = new ArrayList<>();
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				if (d.isActive() && inScope(d) && targetLanguages.contains(d.getLang())) {
					//Does this description have language refset entries in the wrong refset?
					for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
						if (!allRefsets.containsKey(l.getRefsetId())) {
							LOGGER.debug("Unexpected language refset: " + gl.getConcept(l.getRefsetId()));
						}
						if (!allRefsets.get(l.getRefsetId()).equals(d.getLang())) {
							conceptsToProcess.add(c);
							continue nextConcept;
						}
					}
				}
			}
		}
		return conceptsToProcess;
	}

}
