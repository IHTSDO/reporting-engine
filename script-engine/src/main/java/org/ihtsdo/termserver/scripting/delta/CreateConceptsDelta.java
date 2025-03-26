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

	private static final DefinitionStatus defStatus = DefinitionStatus.PRIMITIVE;

	//Most products we're working with will only support en-US
	protected static final Map<String, Acceptability> DEFAULT_PREF_ACCEPTABILITY_MAP = Map.of(
			US_ENG_LANG_REFSET, Acceptability.PREFERRED
	);

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
	public void process() throws TermServerScriptException {
		for (int x = 0; x < fsns.length; x++) {
			Concept c = new Concept(conIdGenerator.getSCTID());
			c.setActive(true);
			c.setDefinitionStatus(defStatus);
			c.setModuleId(targetModuleId);
			addDescriptions(c, fsns[x]);
			addRelationships(c, parents[x]);
			report(c, Severity.LOW, ReportActionType.CONCEPT_ADDED);
			SnomedUtils.setAllComponentsDirty(c, true);
			outputRF2(c);
		}
	}

	private void addDescriptions(Concept c, String term) throws TermServerScriptException {
		addDescription(c, DescriptionType.FSN, term);
		String ptTerm = SnomedUtilsBase.deconstructFSN(term)[0];
		addDescription(c, DescriptionType.SYNONYM, ptTerm);
	}

	private void addDescription(Concept c, DescriptionType type, String term) throws TermServerScriptException {
		CaseSensitivityUtils csUtils = CaseSensitivityUtils.get();
		Description d = Description.withDefaults(term, type, DEFAULT_PREF_ACCEPTABILITY_MAP);
		d.setDescriptionId(descIdGenerator.getSCTID());
		d.setConceptId(c.getConceptId());
		d.setModuleId(targetModuleId);
		alignLangRefsetToDescription(d);
		d.setCaseSignificance(csUtils.suggestCorrectCaseSignificance(c, d));
		c.addDescription(d);
		report(c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, d);
	}

	private void alignLangRefsetToDescription(Description d) {
		for (LangRefsetEntry lre : d.getLangRefsetEntries()) {
			lre.setModuleId(targetModuleId);
			lre.setReferencedComponentId(d.getId());
		}
	}

	private void addRelationships(Concept c, String parentStr) throws TermServerScriptException {
		Concept parent = gl.getConcept(parentStr);
		Relationship r = new Relationship(c, IS_A, parent, UNGROUPED);
		r.setActive(true);
		r.setModuleId(targetModuleId);
		c.addRelationship(r);
		report(c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, r);
	}

}
