package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import java.util.*;

public class RetermIllegalCharacters extends DeltaGenerator {

	private static final String EN_DASH = "\u2013";
	private static final String EM_DASH = "\u2014";

	Map<String, String> illegalReplacementMap = new HashMap<>();
	Map<String, String> illegalCharacterNames = new HashMap<>();

	public static void main(String[] args) throws TermServerScriptException {
		RetermIllegalCharacters fix = new RetermIllegalCharacters();
		try {
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit(GFOLDER_ADHOC_UPDATES);
			fix.retermIllegalCharacters();
			fix.createOutputArchive();
		} finally {
			fix.finish();
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		illegalReplacementMap.put(EN_DASH, "-");
		illegalReplacementMap.put(EM_DASH, "-");

		illegalCharacterNames.put(EN_DASH, "En Dash");
		illegalCharacterNames.put(EM_DASH, "Em Dash");
		super.postInit(googleFolder);
	}

	protected void retermIllegalCharacters() throws TermServerScriptException {
		nextConcept:
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
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


	private void retermIllegalCharacters(Concept c) throws TermServerScriptException {
		for (Map.Entry<String,String> entry : illegalReplacementMap.entrySet()) {
			String find = entry.getKey();
			String replace = entry.getValue();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (!inScope(d, false)) {
					continue;
				}
				retermDescription(c, d, find, replace);
			}
		}
	}

	private void retermDescription(Concept c, Description d, String find, String replace) throws TermServerScriptException {
		//In this case we're looking for an entire match
		if (d.getTerm().contains(find)) {
			if (!d.isReleasedSafely()) {
				report(c, Severity.MEDIUM, ReportActionType.INFO, "New description this cycle");
			}
			String replacement = d.getTerm().replaceAll(find, replace);
			Description replacementDesc = d.clone(descIdGenerator.getSCTID());
			replacementDesc.setTerm(replacement);
			d.setActive(false);
			c.addDescription(replacementDesc);
			//Is c inactive?  Add a Concept non current indicator if so, and replace the CNC indicator on the description
			//we're inactivating
			if (!c.isActiveSafely()) {
				InactivationIndicatorEntry cnc = InactivationIndicatorEntry.withDefaults(d, RF2Constants.SCTID_INACT_CONCEPT_NON_CURRENT);
				replacementDesc.addInactivationIndicator(cnc);
				report(c, Severity.LOW, ReportActionType.INACT_IND_ADDED, "Concept non-Current added to " + replacementDesc.getId());

				InactivationIndicatorEntry ii = d.getInactivationIndicatorEntries().iterator().next();
				ii.setInactivationReasonId(RF2Constants.SCTID_INACT_NON_CONFORMANCE);
				report(c, Severity.LOW, ReportActionType.INACT_IND_MODIFIED, "CNC -> NCEP for " + d.getId());
			} else {
				InactivationIndicatorEntry ii = InactivationIndicatorEntry.withDefaults(d, RF2Constants.SCTID_INACT_NON_CONFORMANCE);
				d.addInactivationIndicator(ii);
			}
			report(c, d, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, d);
			report(c, d, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, replacementDesc);
		}
	}

}
