package org.ihtsdo.termserver.scripting.pipeline.npu;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuConcept;

public class NpuTemplatedConceptPanel extends NpuTemplatedConcept {

	private NpuTemplatedConceptPanel(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	private Concept panelParent;

	public static NpuTemplatedConceptPanel create(ExternalConcept externalConcep) throws TermServerScriptException {
		NpuTemplatedConceptPanel templatedConcept = new NpuTemplatedConceptPanel(externalConcep);
		templatedConcept.createConcept();
		templatedConcept.generateDescriptions();
		return templatedConcept;
	}

	private void createConcept() throws TermServerScriptException {
		concept = Concept.withDefaults(null);
		concept.setModuleId(SCTID_NPU_EXTENSION_MODULE);
		concept.addRelationship(IS_A, getPanelParent());
		concept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
	}

	private Concept getPanelParent() throws TermServerScriptException {
		if (panelParent == null) {
			panelParent = gl.getConcept("540081010000107 |Panel (observable entity)|)", true, false);
		}
		return panelParent;
	}

	private void generateDescriptions() throws TermServerScriptException {
		NpuConcept npuConcept = getNpuConcept();
		String ptStr = npuConcept.getLongDisplayName();

		Description pt = Description.withDefaults(ptStr, DescriptionType.SYNONYM, Acceptability.PREFERRED)
				.withCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);

		Description fsn = Description.withDefaults(ptStr + getSemTag(), DescriptionType.FSN, Acceptability.PREFERRED)
				.withCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);

		concept.addDescription(pt);
		concept.addDescription(fsn);
	}

}
