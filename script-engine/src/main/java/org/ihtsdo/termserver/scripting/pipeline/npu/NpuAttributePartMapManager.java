package org.ihtsdo.termserver.scripting.pipeline.npu;

import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.AttributePartMapManager;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NpuAttributePartMapManager extends AttributePartMapManager {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NpuAttributePartMapManager.class);

	protected NpuAttributePartMapManager(ContentPipelineManager cpm, Map<String, Part> parts,
			Map<String, String> partMapNotes) {
		super(cpm, parts, partMapNotes);
	}

	@Override
	protected void populateKnownMappings() throws TermServerScriptException {
		LOGGER.warn("NPU has no mapping overrides");
	}

	@Override
	protected void populateHardCodedMappings() throws TermServerScriptException {
		hardCodedMappings.put("Ratio", List.of(
				gl.getConcept("30766002|Quantitative (qualifier value)|")));
		hardCodedMappings.put("Ordinal", List.of(
				gl.getConcept("117363000 |Ordinal value|")));
	}

}
