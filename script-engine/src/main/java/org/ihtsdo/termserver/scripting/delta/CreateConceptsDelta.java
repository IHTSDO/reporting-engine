package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
<<<<<<< HEAD
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
=======
>>>>>>> d970518c1 (NUVA-4 Refresh code for creating delta of known new/required concepts)
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class CreateConceptsDelta extends DeltaGenerator {

	private static final DefinitionStatus defStatus = DefinitionStatus.PRIMITIVE;

	private final String[] fsns = new String[] {
			"NUVA Extension Module",
			"NUVA code identifier (core metadata concept)",
			"Valence (disposition)"};

	private final String[] parents = new String[] {
			"1201891009 |SNOMED CT Community content module|",
			"900000000000453004 |Identifier scheme|",
			"726711005 |Disposition|"};

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
		Description d = Description.withDefaults(term, type, Acceptability.PREFERRED);
		d.setDescriptionId(descIdGenerator.getSCTID());
		d.setConceptId(c.getConceptId());
		d.setModuleId(targetModuleId);
		alignLangRefsetToDescription(d);
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
