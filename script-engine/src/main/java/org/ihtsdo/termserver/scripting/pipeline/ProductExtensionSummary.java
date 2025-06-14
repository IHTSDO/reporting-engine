package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProductExtensionSummary extends TermServerReport implements ReportClass {

	private static final String ACTIVE_CONCEPTS = "Active Concepts";
	private static final String ACTIVE_DESCRIPTIONS = "Active Descriptions";
	private static final String ACTIVE_AXIOMS = "Active Axioms";
	private static final String ACTIVE_RELATIONSHIPS = "Active Relationships";
	private static final String ACTIVE_REFSET_MEMBERS = "Active Refset Members";

	private static final String INACTIVE_CONCEPTS = "Inactive Concepts";
	private static final String INACTIVE_DESCRIPTIONS = "Inactive Descriptions";
	private static final String INACTIVE_AXIOMS = "Inactive Axioms";
	private static final String INACTIVE_RELATIONSHIPS = "Inactive Relationships";
	private static final String INACTIVE_REFSET_MEMBERS = "Inactive Refset Members";

	private List<Concept> inScopeConcepts;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put(MODULES, SCTID_LOINC_EXTENSION_MODULE);
		TermServerScript.run(ProductExtensionSummary.class, args, parameters);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Summary Counts",
				"Concept Details",
				"Concepts without Alternate Identifiers",
				"Concepts with multiple Axioms",
				"Text Definitions",
				"Inactive Components"
		};
		String[] columnHeadings = new String[] {
				"Item, Count",
				"Concept, FSN, SemTag, Alternate Identifier, Descriptions, Inferred Model, , ",
				"Concept, FSN, SemTag",
				"Concept, FSN, SemTag",
				"Concept, FSN, SemTag, Definition",
				"Component, EffectiveTime, Active, Module, Author"
		};
		postInit(tabNames, columnHeadings);
		inScopeConcepts = gl.getAllConcepts().stream()
				.filter(this::inScope)
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Product Extension Summary")
				.withDescription("This report list summary counts for a particular product extension, with cross checks.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(new JobParameters())
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		getSummaryCounts();
		populateSummaryTabAndTotal(PRIMARY_REPORT);

		getConceptDetails(SECONDARY_REPORT);
		getConceptsWithoutAltIds(TERTIARY_REPORT);
		getConceptsWithMultipleAxioms(QUATERNARY_REPORT);
		getTextDefinitions(QUINARY_REPORT);
		getInactiveComponents(SENARY_REPORT);
	}

	private void getSummaryCounts() {
		for (Concept c : inScopeConcepts) {
			getSummaryCounts(c);
		}
	}

	private void getSummaryCounts(Concept c) {
		incrementSummaryInformation(c.isActiveSafely() ? ACTIVE_CONCEPTS : INACTIVE_CONCEPTS);

		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			incrementSummaryInformation(d.isActiveSafely() ? ACTIVE_DESCRIPTIONS : INACTIVE_DESCRIPTIONS);
		}
		for (AxiomEntry ax : c.getAxiomEntries()) {
			incrementSummaryInformation(ax.isActiveSafely() ? ACTIVE_AXIOMS : INACTIVE_AXIOMS);
		}
		for (Relationship r : c.getRelationships()) {
			incrementSummaryInformation(r.isActiveSafely() ? ACTIVE_RELATIONSHIPS : INACTIVE_RELATIONSHIPS);
		}
		for (RefsetMember rm : c.getOtherRefsetMembers()) {
			incrementSummaryInformation(rm.isActiveSafely() ? ACTIVE_REFSET_MEMBERS : INACTIVE_REFSET_MEMBERS);
		}
	}

	private void getConceptDetails(int tabIdx) throws TermServerScriptException {
		for (Concept c : inScopeConcepts) {
			report(tabIdx, c, SnomedUtils.getAlternateIdentifiers(c, false), SnomedUtils.getDescriptions(c), c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
		}
	}

	private void getConceptsWithoutAltIds(int tabIdx) throws TermServerScriptException {
		for (Concept c : inScopeConcepts) {
			if (c.getAlternateIdentifiers().isEmpty()) {
				incrementSummaryInformation("Concepts without AltIds");
				report(tabIdx, c);
			}
		}
	}

	private void getConceptsWithMultipleAxioms(int tabIdx) throws TermServerScriptException {
		// Only interested in multiple axioms where one of them is in scope.
		// Watch out that we might have an unexpected LOINC axiom on an International Concept
		List<Concept> conceptsOfInterest = gl.getAllConcepts().stream()
				.filter(c -> c.getAxiomEntries().size() > 1)
				.filter(c -> c.getAxiomEntries().stream().anyMatch(ax -> inScope(ax)))
				.sorted(SnomedUtils::compareSemTagFSN)
				.toList();

		for (Concept c : conceptsOfInterest) {
			String axiomStr = c.getAxiomEntries().stream()
					.map(AxiomEntry::toString)
					.collect(Collectors.joining(",\n"));
			report(tabIdx, c, axiomStr);
		}
	}

	private void getTextDefinitions(int tabIdx) throws TermServerScriptException {
		for (Concept c : inScopeConcepts) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, List.of(DescriptionType.TEXT_DEFINITION))) {
				report(tabIdx, c, d);
			}
		}
	}

	private void getInactiveComponents(int tabIdx) throws TermServerScriptException {
		for (Component c : inScopeConcepts) {
			if (!c.isActiveSafely()) {
				Concept parent = gl.getComponentOwner(c.getId());
				report(tabIdx, c, c.getEffectiveTime(), c.isActive(), c.getModuleId(), parent);
			}
		}
	}

}
