package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI-10
 * Reports all joints appearing as finding sites in fracture of bone
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class InScopeForTemplatesPerHierarchy extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(InScopeForTemplatesPerHierarchy.class);

	public static void main(String[] args) throws TermServerScriptException {
		InScopeForTemplatesPerHierarchy report = new InScopeForTemplatesPerHierarchy();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.listInScopeForTemplatesPerHierarchy();
		} catch (Exception e) {
			LOGGER.error("Failed to produce InScopeForTemplatesPerHierarchy", e);
		} finally {
			report.finish();
		}
	}

	private void listInScopeForTemplatesPerHierarchy() throws TermServerScriptException {
		Set<Concept> inScopeConcepts = calculateInScopeConcepts();
		DescendantsCache dc = gl.getDescendantsCache();
		for (Concept topLevel : findConcepts("<!" + ROOT_CONCEPT)) {
			int inScope = 0;
			int outOfScope = 0;
			for (Concept c : dc.getDescendantsOrSelf(topLevel)) {
				if (inScopeConcepts.contains(c)) {
					inScope++;
				} else {
					outOfScope++;
				}
			}
			report(topLevel, inScope, outOfScope);
		}
	}
	
	private Set<Concept> calculateInScopeConcepts() throws TermServerScriptException {
		LOGGER.info("Obtaining concepts that are 'in scope'");
		Set<Concept> inScopeConcepts = new HashSet<>();
		Concept[] inScope = new Concept[] { BODY_STRUCTURE, CLINICAL_FINDING,
											PHARM_BIO_PRODUCT, PROCEDURE,
											SITN_WITH_EXP_CONTXT, SPECIMEN,
											OBSERVABLE_ENTITY, EVENT, 
											PHARM_DOSE_FORM};
		Set<Concept> topLevelHierarchies = new HashSet<>();
		//We'll create a set to avoid double counting concepts in multiple TLHs
		for (Concept subHierarchy : inScope) {
			Set<Concept> concepts = gl.getDescendantsCache()
					.getDescendantsOrSelf(subHierarchy)
					.stream()
					.filter(this::inModuleScope)
					.collect(Collectors.toSet());
			topLevelHierarchies = ImmutableSet.copyOf(Iterables.concat(topLevelHierarchies, concepts));
		}
		//Now only count those concepts that have some non-ISA inferred attributes
		for (Concept c : topLevelHierarchies) {
			if (SnomedUtils.countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) > 0) {
				inScopeConcepts.add(c);
			}
		}
		return inScopeConcepts;
	}
	
	protected boolean inModuleScope(Component c) {
		if (project.getKey().equals("MAIN")) {
			return true;
		}
		//Do we have a default module id ie for a managed service project?
		if (project.getMetadata() != null && project.getMetadata().getDefaultModuleId() != null) {
			return c.getModuleId().equals(project.getMetadata().getDefaultModuleId());
		}
		return true;
	}
	
}
