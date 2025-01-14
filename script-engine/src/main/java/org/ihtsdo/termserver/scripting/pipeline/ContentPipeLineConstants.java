package org.ihtsdo.termserver.scripting.pipeline;

public interface ContentPipeLineConstants {
	
	enum ProcessingFlag { ALLOW_SPECIMEN, MARK_AS_PRIMITIVE, DROP_OUT,
		SPLIT_TO_GROUP_PER_COMPONENT, ALLOW_BLANK_COMPONENT, 
		SUPPRESS_METHOD_TERM, ALLOW_BLANK_DIVISOR, SUPPRESS_DIVISOR_TERM,
		SUPPRESS_CHARACTERIZES_TERM, ALLOW_TECHNIQUE}

	int GROUP_1 = 1;

	String TAB_SUMMARY = "Summary";
	String TAB_LOINC_DETAIL_MAP_NOTES = "LOINC Detail Map Notes";
	String TAB_MODELING_ISSUES = "Modeling Issues";
	String TAB_PROPOSED_MODEL_COMPARISON = "Proposed Model Comparison";
	String TAB_MAP_ME = "Map Me!";
	String TAB_IMPORT_STATUS = "Import Status";
	String TAB_IOI = "Items of Interest";
	String TAB_STATS = "Property Stats";

	String NAME = "NAME";
}
