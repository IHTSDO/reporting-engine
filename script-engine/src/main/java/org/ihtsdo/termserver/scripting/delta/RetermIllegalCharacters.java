package org.ihtsdo.termserver.scripting.delta;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 */
public class RetermIllegalCharacters extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(RetermIllegalCharacters.class);
	private static final String EN_DASH = "\u2013";
	private static final String EM_DASH = "\u2014";

	Map<String, String> illegalReplacementMap = new HashMap<>();
	Map<String, String> illegalCharacterNames = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RetermIllegalCharacters fix = new RetermIllegalCharacters();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.retermIllegalCharacters();
			fix.createOutputArchive();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		illegalReplacementMap.put(EN_DASH, "-");
		illegalReplacementMap.put(EM_DASH, "-");

		illegalCharacterNames.put(EN_DASH, "En Dash");
		illegalCharacterNames.put(EM_DASH, "Em Dash");
		super.postInit();
	}

	protected void retermIllegalCharacters() throws TermServerScriptException {
		nextConcept:
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
		//for (Concept c : Collections.singleton(gl.getConcept("776207001|Product containing only human regular insulin (medicinal product)|"))) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (inScope(d, false)) {
					for (String illegalCharacter : illegalReplacementMap.keySet()) {
						if (d.getTerm().contains(illegalCharacter)) {
							retermIllegalCharacters(c);
							continue nextConcept;
						}
					}
				}
			}
		}
	}


	private void retermIllegalCharacters(Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (Map.Entry<String,String> entry : illegalReplacementMap.entrySet()) {
			String find = entry.getKey();
			String replace = entry.getValue();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (!inScope(d, false)) {
					continue;
				}
				//In this case we're looking for an entire match
				if (d.getTerm().contains(find)) {
					if (!d.isReleased()) {
						report(c, Severity.MEDIUM, ReportActionType.INFO, "New description this cycle");
					}
					String replacement = d.getTerm().replaceAll(find, replace);
					String msg = "Replaced " + illegalCharacterNames.get(find);
					Description replacementDesc = d.clone(descIdGenerator.getSCTID());
					replacementDesc.setTerm(replacement);
					d.setActive(false);
					InactivationIndicatorEntry ii = InactivationIndicatorEntry.withDefaults(d, RF2Constants.SCTID_INACT_NON_CONFORMANCE);
					d.addInactivationIndicator(ii);
					c.addDescription(replacementDesc);
					report(c, d, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, d);
					report(c, d, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, replacementDesc);
				}
			}
		}
	}

}
