package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.Collection;
import java.util.HashSet;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * LOINC-383 List LOINC concepts where some associated specimen has been modified
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecentSpecimenChanges extends TermServerScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(RecentSpecimenChanges.class);

	Collection<Concept> targetAttributes = null;
	String sinceEffectiveDate = "20180131";
	
	public static void main(String[] args) throws TermServerScriptException {
		RecentSpecimenChanges report = new RecentSpecimenChanges();
		try {
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.headers="SCTID, FSN, SemTag, PT, LoincNum, Correlation, Update Made,Expression,";
			report.init(args);
			report.loadProjectSnapshot(false);
			report.postInit();
			report.reportMatchingConcepts();
		} finally {
			report.finish();
		}
	}

	private void reportMatchingConcepts() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (c.getModuleId() == null) {
				LOGGER.warn ("Invalid concept loaded through reference? " + c.getId());
			} else if (c.isActive() && 
					c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE) &&
					hasRecentlyModifiedTargetAttribute(c)) {
				report(c, 
						c.getPreferredSynonym(),
						LoincUtils.getLoincNumFromDescription(c),
						LoincUtils.getCorrelation(c),
						recentlyModifiedTargetAttributeDetails(c),
						c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
		}
	}

	private boolean hasRecentlyModifiedTargetAttribute(Concept c) throws TermServerScriptException {
		return !StringUtils.isEmpty(recentlyModifiedTargetAttributeDetails(c));
	}
	
	private String recentlyModifiedTargetAttributeDetails(Concept c) throws TermServerScriptException {
		String details = "";
		if (targetAttributes == null) {
			targetAttributes = findConcepts("<< 123038009 |Specimen (specimen)| ");
		}
		
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (targetAttributes.contains(r.getTarget()) && hasRecentModifications(r.getTarget()) ) {
				if (details.length() > 0) {
					details += "\n";
				}
				details += r + " where " + recentModificationDetails(r.getTarget());
			}
		}
		return details;
	}

	private boolean hasRecentModifications(Concept c) throws TermServerScriptException {
		return !StringUtils.isEmpty(recentModificationDetails(c));
	}

	private String recentModificationDetails(Concept c) {
		String details = "";
		details += componentRecentlyUpdated(c, details);
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			details += componentRecentlyUpdated(d, details);
			break;
		}
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			details += componentRecentlyUpdated(r, details);
		}
		return details;
	}

	private String componentRecentlyUpdated(Component c, String details) {
		String LF = StringUtils.isEmpty(details)? "":"\n";
		if (c.getEffectiveTime().compareTo(sinceEffectiveDate) > 0) {
			return LF + c.getClass().getSimpleName() + ": " + c + " updated " + c.getEffectiveTime();
		}
		return "";
	}

}
