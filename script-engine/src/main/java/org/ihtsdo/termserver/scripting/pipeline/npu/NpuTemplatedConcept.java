package org.ihtsdo.termserver.scripting.pipeline.npu;

import java.util.*;

import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.*;


public abstract class NpuTemplatedConcept extends TemplatedConcept implements ContentPipeLineConstants {


	@Override
	protected String getCodeSystemSctId() {
		return SCTID_NPU_SCHEMA;
	}

	public static void initialise(ContentPipelineManager cpm) {
		TemplatedConcept.cpm = cpm;
	}

	@Override
	public String getSemTag() {
		return "(observable entity)";
	}

	protected NpuTemplatedConcept(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	protected void applyTemplateSpecificRules(List<RelationshipTemplate> attributes, RelationshipTemplate rt) throws TermServerScriptException {
		//Do we need to apply any specific rules to the modeling?
		//Override this function if so
	}
	
	protected NpuConcept getNpuConcept() {
		return (NpuConcept)externalConcept;
	}

	protected void applyTemplateSpecificTermingRules(Description d) {
		//Do we need to apply any specific rules to the description?
		//Override this function if so
	}

	@Override
	protected void populateParts() throws TermServerScriptException {
		throw new NotImplementedException();
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		return false;
	}
	
}
