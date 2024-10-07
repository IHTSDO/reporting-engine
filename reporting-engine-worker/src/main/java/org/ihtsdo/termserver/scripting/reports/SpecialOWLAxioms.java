package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.owltoolkit.domain.ObjectPropertyAxiomRepresentation;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * RP-289 List "Special" OWL Axioms
 */
public class SpecialOWLAxioms extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(SpecialOWLAxioms.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		headers = "SCTID, FSN, Semtag, Axiom Type, Axiom";
		additionalReportColumns="";
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Object Property Axioms")
				.withDescription("This report lists all concepts which have special OWL axioms like GCIs. For extensions, only additional axioms and GCIs in the appropriate module will be listed, but all special object axioms (like role-chains) will be given.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	@Override
	public void postInit() throws TermServerScriptException {

		String[] columnHeadings = new String[] {"Concept, FSN, SemTag, ConceptActive, isTransitive, isReflexive, isRoleChain, OWL",
				"Concept, FSN, SemTag, DefnStat, OWL, Axiom Ids",
				"Concept, FSN, SemTag, DefnStat, OWL, Axiom Id"};

		String[] tabNames = new String[] {"Special Axioms",
				"Additional Axioms",
				"GCIs"};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (!c.isActive()) {
				continue;
			}
			
			if (c.getObjectPropertyAxiomRepresentation() != null) {
				ObjectPropertyAxiomRepresentation axiom = c.getObjectPropertyAxiomRepresentation();
				report(PRIMARY_REPORT, c,
						c.isActive(),
						axiom.isTransitive(),
						axiom.isReflexive(),
						axiom.isPropertyChain(),
						axiom.getOwl());
				countIssue(c);
				continue;
			}
			String defnStat = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
			
			for (Axiom a : c.getAdditionalAxioms()) {
				if (inScope(a) && a.isActive()) {
					report(SECONDARY_REPORT, c, defnStat, a);
					countIssue(c);
				}
			}
			
			List<AxiomEntry> axioms = c.getAxiomEntries(ActiveState.ACTIVE, false);
			if (axioms.size() > 1 && hasInScopeAxiom(axioms)) {
				Map<String, List<RelationshipGroup>> groupsByAxiom = c.getRelationshipGroupsByAxiom();
				String axiomIds = groupsByAxiom.keySet().stream()
						.collect(Collectors.joining(",\n"));
				String expressions = "";
				boolean isFirst = true;
				for (List<RelationshipGroup> groups : groupsByAxiom.values()) {
					expressions += isFirst?"":"\n\n";
					expressions += SnomedUtils.toExpression(getDefinitionStatus(groups), groups);
					isFirst = false;
				}
				report(SECONDARY_REPORT, c, defnStat, expressions, axiomIds);
				countIssue(c);
			}
			
			
			for (Axiom a : c.getGciAxioms()) {
				if (inScope(a) && a.isActive()) {
					report(TERTIARY_REPORT, c, defnStat, a, a.getId());
					countIssue(c);
				}
			}
			
		}
	}
	
	private boolean hasInScopeAxiom(List<AxiomEntry> axioms) {
		return axioms.stream()
				.anyMatch(this::inScope);
	}

	private DefinitionStatus getDefinitionStatus(List<RelationshipGroup> groups) {
		for (RelationshipGroup g : groups) {
			if (g.getAxiomEntry() != null) {
				return g.getAxiomEntry().getOwlExpression().startsWith("EquivalentClasses")?DefinitionStatus.FULLY_DEFINED:DefinitionStatus.PRIMITIVE;
			}
		}
		return DefinitionStatus.PRIMITIVE;
	}

}
