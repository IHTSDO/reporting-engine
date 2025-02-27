package org.ihtsdo.termserver.scripting.pipeline.npu;

import java.util.Map;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.AttributePartMapManager;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NpuAttributePartMapManager extends AttributePartMapManager {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(NpuAttributePartMapManager.class);

	protected NpuAttributePartMapManager(ContentPipelineManager cpm, Map<String, Part> loincParts,
			Map<String, String> partMapNotes) {
		super(cpm, loincParts, partMapNotes);
	}

	@Override
	protected void populateKnownMappings() throws TermServerScriptException {
		LOGGER.warn("NPU has no mapping overrides");
	}

	@Override
	protected void populateHardCodedMappings() throws TermServerScriptException {
		LOGGER.warn("NPU has no mapping hard coded mappings");
	}

}
