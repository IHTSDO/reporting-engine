package org.ihtsdo.termserver.scripting.pipeline;

public interface ContentPipeLineConstants {
	
	enum ProcessingFlag { ALLOW_SPECIMEN, MARK_AS_PRIMITIVE, DROP_OUT,
		SPLIT_TO_GROUP_PER_COMPONENT, ALLOW_BLANK_COMPONENT, 
		SUPPRESS_METHOD_TERM, ALLOW_BLANK_DIVISOR, SUPPRESS_DIVISOR_TERM,
		SUPPRESS_CHARACTERIZES_TERM}

	public final String TAB_SUMMARY = "Summary";
	public final String TAB_LOINC_DETAIL_MAP_NOTES = "LOINC Detail Map Notes";
	public final String TAB_MODELING_ISSUES = "Modeling Issues";
	public final String TAB_PROPOSED_MODEL_COMPARISON = "Proposed Model Comparison";
	public final String TAB_MAP_ME = "Map Me!";
	public final String TAB_IMPORT_STATUS = "Import Status";
	public final String TAB_IOI = "Items of Interest";
	public final String TAB_STATS = "Property Stats";
}
