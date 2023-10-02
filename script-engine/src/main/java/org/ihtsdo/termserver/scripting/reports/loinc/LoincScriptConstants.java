package org.ihtsdo.termserver.scripting.reports.loinc;

public interface LoincScriptConstants {
	
	enum ProcessingFlag { ALLOW_SPECIMEN, MARK_AS_PRIMITIVE, DROP_OUT }

	public final String TAB_SUMMARY = "Summary";
	public final String TAB_LOINC_DETAIL_MAP_NOTES = "LOINC Detail Map Notes";
	public final String TAB_MODELING_ISSUES = "Modeling Issues";
	public final String TAB_PROPOSED_MODEL_COMPARISON = "Proposed Model Comparison";
	public final String TAB_MAP_ME = "Map Me!";
	public final String TAB_IMPORT_STATUS = "Import Status";
	public final String TAB_IOI = "Items of Interest";

	public int IDX_LOINC_NUM = 0;
	public int IDX_COMPONENT = 1;
	public int IDX_PROPERTY = 2;
	public int IDX_TIME_ASPCT = 3;
	public int IDX_SYSTEM = 4;
	public int IDX_SCALE_TYP = 5;
	public int IDX_METHOD_TYP = 6;
	public int IDX_CLASS = 7;
	public int IDX_VERSIONLASTCHANGED = 8;
	public int IDX_CHNG_TYPE = 9;
	public int IDX_DEFINITIONDESCRIPTION = 10;
	public int IDX_STATUS = 11;
	public int IDX_CONSUMER_NAME = 12;
	public int IDX_CLASSTYPE = 13;
	public int IDX_FORMULA = 14;
	public int IDX_EXMPL_ANSWERS = 15;
	public int IDX_SURVEY_QUEST_TEXT = 16;
	public int IDX_SURVEY_QUEST_SRC = 17;
	public int IDX_UNITSREQUIRED = 18;
	public int IDX_RELATEDNAMES2 = 19;
	public int IDX_SHORTNAME = 20;
	public int IDX_ORDER_OBS = 21;
	public int IDX_HL7_FIELD_SUBFIELD_ID = 22;
	public int IDX_EXTERNAL_COPYRIGHT_NOTICE = 23;
	public int IDX_EXAMPLE_UNITS = 24;
	public int IDX_LONG_COMMON_NAME = 25;
	public int IDX_EXAMPLE_UCUM_UNITS = 26;
	public int IDX_STATUS_REASON = 27;
	public int IDX_STATUS_TEXT = 28;
	public int IDX_CHANGE_REASON_PUBLIC = 29;
	public int IDX_COMMON_TEST_RANK = 30;
	public int IDX_COMMON_ORDER_RANK = 31;
	public int IDX_COMMON_SI_TEST_RANK = 32;
	public int IDX_HL7_ATTACHMENT_STRUCTURE = 33;
	public int IDX_EXTERNAL_COPYRIGHT_LINK = 34;
	public int IDX_PANELTYPE = 35;
	public int IDX_ASKATORDERENTRY = 36;
	public int IDX_ASSOCIATEDOBSERVATIONS = 37;
	public int IDX_VERSIONFIRSTRELEASED = 38;
	public int IDX_VALIDHL7ATTACHMENTREQUEST = 39;
	public int IDX_DISPLAYNAME = 40;
}
