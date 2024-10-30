package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants;

public interface LoincScriptConstants extends ContentPipeLineConstants {

	public static final Concept ORD_REFSET = new Concept("635111010000100", "LOINC Orderable Reference Set");
	public static final Concept OBS_REFSET = new Concept("635121010000106", "LOINC Observable Reference Set");

	public static final String LOINC_PART_TYPE_ADJUSTMENT = "ADJUSTMENT";
	public static final String LOINC_PART_TYPE_CHALLENGE = "CHALLENGE";
	public static final String LOINC_PART_TYPE_CLASS = "CLASS";
	public static final String LOINC_PART_TYPE_COMPONENT = "COMPONENT";
	public static final String LOINC_PART_TYPE_COUNT = "COUNT";
	public static final String LOINC_PART_TYPE_DEVICE = "DEVICE";
	public static final String LOINC_PART_TYPE_DIVISORS = "DIVISORS";
	public static final String LOINC_PART_TYPE_METHOD = "METHOD";
	public static final String LOINC_PART_TYPE_PROPERTY = "PROPERTY";
	public static final String LOINC_PART_TYPE_SCALE = "SCALE";
	public static final String LOINC_PART_TYPE_SUFFIX = "SUFFIX";
	public static final String LOINC_PART_TYPE_SUPER_SYSTEM = "SUPER SYSTEM";
	public static final String LOINC_PART_TYPE_SYSTEM = "SYSTEM";
	public static final String LOINC_PART_TYPE_TIME = "TIME";
	public static final String LOINC_PART_TYPE_TIME_MODIFIER = "TIME MODIFIER";


	public static final String COMPONENT_PN = "COMPONENT_PN";
	public static final String COMPONENTCORE_PN = "COMPONENTCORE_PN";
	public static final String COMPSUBPART1_PN = "COMPSUBPART1_PN";
	public static final String COMPSUBPART2_PN = "COMPSUBPART2_PN";
	public static final String COMPSUBPART3_PN = "COMPSUBPART3_PN";
	public static final String COMPSUBPART4_PN = "COMPSUBPART4_PN";
	public static final String COMPNUM_PN = "COMPNUM_PN";
	public static final String COMPDENOM_PN = "COMPDENOM_PN";
	public static final String PROPERTY = "PROPERTYMIXEDCASE_PN";
	public static final String COMPNUMSUFFIX_PN = "COMPNUMSUFFIX_PN";
	public static final String SYSTEM_PN = "SYSTEM_PN";

	public int FILE_IDX_LOINC_PARTS_MAP_BASE_FILE = 1;
	public int FILE_IDX_LOINC_100 = 0;
	public int FILE_IDX_LOINC_100_PRIMARY = 2;
	public int FILE_IDX_LOINC_PARTS = 3;
	public int FILE_IDX_LOINC_FULL = 4;
	public int FILE_IDX_LOINC_DETAIL = 5;
	public int FILE_IDX_PANELS = 6;

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
