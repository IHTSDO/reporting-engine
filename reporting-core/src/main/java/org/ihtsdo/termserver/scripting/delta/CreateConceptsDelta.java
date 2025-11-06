package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.Map;

public class CreateConceptsDelta extends DeltaGenerator {

	protected static final DefinitionStatus defStatus = DefinitionStatus.PRIMITIVE;
	protected static boolean usLangOnly = true;

	private final String[] fsns = new String[] {
			"NUVA Extension Module",
			"NUVA code identifier (core metadata concept)",
			"Valence (valence)",
			"Has valence (attribute)",};

	private final String[] parents = new String[] {
			"1201891009 |SNOMED CT Community content module|",
			"900000000000453004 |Identifier scheme|",
			"362981000 |Qualifier value (qualifier value)|",
			"762705008 |Concept model object attribute|"
	};

	public static void main(String[] args) throws TermServerScriptException {
		new CreateConceptsDelta().standardExecutionWithIds(args);
	}

	@Override
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		targetModuleId = sourceModuleIds.iterator().next();
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Details," + additionalReportColumns,
				"SCTID, Descriptions, Expression, Reference"
		};

		String[] tabNames = new String[]{
				"Actions Taken",
				"Concepts Modelled"
		};
		postInit(googleFolder, tabNames, columnHeadings);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (int x = 0; x < fsns.length; x++) {
			Concept c = new Concept(conIdGenerator.getSCTID());
			c.setActive(true);
			c.setDefinitionStatus(defStatus);
			c.setModuleId(targetModuleId);
			addFsnAndCounterpart(c, fsns[x], true, null);
			addRelationships(c, parents[x]);
			report(c, Severity.LOW, ReportActionType.CONCEPT_ADDED);
			SnomedUtils.setAllComponentsDirty(c, true);
			gl.registerConcept(c);
			outputRF2(c, true);
		}
	}

	@Override
	public boolean outputRF2(Concept c, boolean alwaysCheckSubComponents) throws TermServerScriptException {
		if (!c.isDirty()) {
			return false;
		}

		//Log the concept created on a separate tab
		report(SECONDARY_REPORT, c.getId(), SnomedUtils.getDescriptionsFull(c), c.toExpression(CharacteristicType.STATED_RELATIONSHIP), c.getIssues());
		return super.outputRF2(c, alwaysCheckSubComponents);
	}

	protected void addFsnAndCounterpart(Concept c, String term, boolean fsnCounterpartIsPt, CaseSignificance caseSig) throws TermServerScriptException {
		addDescription(c, DescriptionType.FSN, term, true, caseSig);
		String ptTerm = SnomedUtilsBase.deconstructFSN(term)[0];
		addDescription(c, DescriptionType.SYNONYM, ptTerm,fsnCounterpartIsPt, caseSig);
	}

	protected void addDescription(Concept c, DescriptionType type, String term, boolean isPreferred, CaseSignificance caseSig) throws TermServerScriptException {
		CaseSensitivityUtils csUtils = CaseSensitivityUtils.get();
		Description d = Description.withDefaults(term, type, getAcceptabilityMap(isPreferred));
		d.setDescriptionId(descIdGenerator.getSCTID());
		d.setConceptId(c.getConceptId());
		d.setModuleId(targetModuleId);
		alignLangRefsetToDescription(d);
		d.setCaseSignificance(caseSig == null ? csUtils.suggestCorrectCaseSignificance(c, d) : caseSig);
		c.addDescription(d);
		report(c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, d);
	}

	private Map<String, Acceptability> getAcceptabilityMap(boolean isPreferred) {
		if (usLangOnly) {
			return Map.of(
					US_ENG_LANG_REFSET, isPreferred ? Acceptability.PREFERRED : Acceptability.ACCEPTABLE
			);
		} else {
			return Map.of(
					US_ENG_LANG_REFSET, isPreferred ? Acceptability.PREFERRED : Acceptability.ACCEPTABLE,
					GB_ENG_LANG_REFSET, isPreferred ? Acceptability.PREFERRED : Acceptability.ACCEPTABLE
			);
		}
	}

	private void alignLangRefsetToDescription(Description d) {
		for (LangRefsetEntry lre : d.getLangRefsetEntries()) {
			lre.setModuleId(targetModuleId);
			lre.setReferencedComponentId(d.getId());
		}
	}

	protected void addRelationships(Concept c, String parentStr) throws TermServerScriptException {
		Concept parent = gl.getConcept(parentStr);
		Relationship r = new Relationship(c, IS_A, parent, UNGROUPED);
		r.setActive(true);
		r.setModuleId(targetModuleId);
		r.setIntendedForAxiom(true);  //Avoids problems trying to register this component
		c.addRelationship(r);
		report(c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, r);
	}

}
