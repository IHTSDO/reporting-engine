package org.ihtsdo.termserver.scripting.pipeline.npu;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants;

public interface NpuScriptConstants extends ContentPipeLineConstants {

	String NPU_PART_COMPONENT = "COMPONENT";
	String NPU_PART_DEVICE = "DEVICE";
	String NPU_PART_METHOD = "METHOD";
	String NPU_PART_PROPERTY = "PROPERTY";
	String NPU_PART_SCALE = "SCALE";
	String NPU_PART_UNIT = "UNIT";
	String NPU_PART_SYSTEM = "SYSTEM";
	String NPU_PART_TIME = "TIME";

	int FILE_IDX_NPU_TECH_PREVIEW_CONCEPTS = 2;
	int FILE_IDX_NPU_FULL = 3;
	int FILE_IDX_NPU_DETAIL = 5;

}
