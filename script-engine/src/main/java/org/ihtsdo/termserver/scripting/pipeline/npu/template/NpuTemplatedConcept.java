package org.ihtsdo.termserver.scripting.pipeline.npu.template;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;
import org.ihtsdo.termserver.scripting.pipeline.npu.ImportNpuConcepts;
import org.ihtsdo.termserver.scripting.pipeline.npu.NpuScriptConstants;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuConcept;
import org.ihtsdo.termserver.scripting.pipeline.npu.domain.NpuDetail;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;

public abstract class NpuTemplatedConcept extends TemplatedConcept implements NpuScriptConstants {
	private static Concept unitsAttribute;
	private static RelationshipTemplate defaultUnitOfMeasureAttribute;
	private static Map<String, NpuDetail> npuDetailMap;

	public static void initialise(ContentPipelineManager cpm,
	                              Map<String, NpuDetail> npuDetailMap) throws TermServerScriptException {
		TemplatedConcept.cpm = cpm;
		TemplatedConcept.gl = cpm.getGraphLoader();
		Concept unitOfMeasure = gl.getConcept("767524001 |Unit of measure| ");
		unitsAttribute = gl.getConcept("246514001 |Units|");
		defaultUnitOfMeasureAttribute = new RelationshipTemplate(unitsAttribute, unitOfMeasure);
	}

		@Override
	public String getSchemaId() {
		return SCTID_NPU_SCHEMA;
	}

	@Override
	public String getSemTag() {
		return " (observable entity)";
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

	protected void populateTypeMapCommonItems() throws TermServerScriptException {
		typeMap.put(NPU_PART_COMPONENT, gl.getConcept("246093002 |Component (attribute)|"));
		typeMap.put(NPU_PART_PROPERTY, gl.getConcept("370130000 |Property (attribute)|"));
		typeMap.put(NPU_PART_SCALE, gl.getConcept("370132008 |Scale type (attribute)|"));
		typeMap.put(NPU_PART_SYSTEM, gl.getConcept("704319004 |Inheres in|"));
		typeMap.put(NPU_PART_UNIT, unitsAttribute);
	}

	@Override
	protected void populateParts() throws TermServerScriptException {
		prepareConceptDefaultedForModule(SCTID_NPU_EXTENSION_MODULE);
		NpuConcept npuConcept = getNpuConcept();
		NpuDetail npuDetail = ((ImportNpuConcepts)cpm).getDetailsMap().get(getExternalIdentifier());
		for (Part part : npuDetail.getParts(npuConcept)) {
			populatePart(part);
		}

		//Ensure attributes are unique (considering both type and value)
		checkAndRemoveDuplicateAttributes();
	}

	private void populatePart(Part part) throws TermServerScriptException {
		List<RelationshipTemplate> attributesToAdd = new ArrayList<>();
		addAttributeFromDetail(attributesToAdd, part);

		if (part.getPartTypeName().equals(NPU_PART_UNIT) && attributesToAdd.isEmpty()) {
			//NPU Concepts without a unit will be given a default unit of measure
			attributesToAdd.add(defaultUnitOfMeasureAttribute);
			slotTermMap.put(NPU_PART_UNIT, "");
		}

		for (RelationshipTemplate rt : attributesToAdd) {
			addAttributesToConcept(rt, part, false);
		}
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		return false;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		//If we're working with a Unit, and we haven't found a mapping for it, then use the name from the NPU part
		//and make the concept primitive
		if (part.getPartTypeName().equals(NPU_PART_UNIT) && attributes.isEmpty()) {
			addProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE);
			slotTermMap.put(NPU_PART_UNIT, part.getPartName());
		}

	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description pt) throws TermServerScriptException {
		//Do we need to apply any specific rules to the terming?
		//Override this function if so
	}
	
}
