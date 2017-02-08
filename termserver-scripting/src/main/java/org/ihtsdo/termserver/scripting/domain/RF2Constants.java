package org.ihtsdo.termserver.scripting.domain;

public interface RF2Constants {
	
	static int NOT_SET = -1;
	static int IMMEDIATE_CHILD = 1;
	static int NA = -1;
	static String PHARM_BIO_PRODUCT_SCTID = "373873005" ; //Pharmaceutical / biologic product (product)
	static Concept IS_A =  new Concept ("116680003");  // | Is a (attribute) |
	static Concept HAS_ACTIVE_INGRED = new Concept ("127489000","Has active ingredient (attribute)");
	static Concept SUBSTANCE = new Concept("105590001", "Substance (substance)");
	static Concept HAS_DOSE_FORM = new Concept ("411116001","Has dose form (attribute)");
	static Concept DRUG_PREPARATION = new Concept("105904009","Type of drug preparation (qualifier value)");
	static String ACETAMINOPHEN = "acetaminophen";
	static String PARACETAMOL = "paracetamol";
	
	//Description Type SCTIDs
	static String SYN = "900000000000013009";
	static String FSN = "900000000000003001";
	static String DEF = "900000000000550004"; 
	
	static final String FULLY_DEFINED_SCTID = "900000000000073002";
	static final String FULLY_SPECIFIED_NAME = "900000000000003001";
	final Long SCTID_ROOT_CONCEPT = 138875005L;
	final String ADDITIONAL_RELATIONSHIP = "900000000000227009";
	final String SPACE = " ";
	final String COMMA = ",";
	final String COMMA_QUOTE = ",\"";
	final String QUOTE_COMMA = "\",";
	final String QUOTE_COMMA_QUOTE = "\",\"";
	final String TAB = "\t";
	final String CSV_FIELD_DELIMITER = COMMA;
	final String TSV_FIELD_DELIMITER = TAB;
	final String QUOTE = "\"";
	final String INGREDIENT_SEPARATOR = "+";
	final String INGREDIENT_SEPARATOR_ESCAPED = "\\+";
	
	final String CONCEPT_INT_PARTITION = "00";
	final String DESC_INT_PARTITION = "01";
	final String REL_INT_PARTITION = "02";
	
	enum InactivationIndicator {DUPLICATE, OUTDATED, ERRONEOUS, LIMITED, MOVED_ELSEWHERE, 
		PENDING_MOVE, INAPPROPRIATE, CONCEPT_NON_CURRENT, RETIRED, NONCONFORMANCE_TO_EDITORIAL_POLICY};
	
	static final String GB_ENG_LANG_REFSET = "900000000000508004";
	static final String US_ENG_LANG_REFSET = "900000000000509007";
	static final String[] ENGLISH_DIALECTS = {GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET};
	
	static final String PREFERRED_TERM = "900000000000548007";
	static final String ACCEPTABLE_TERM = "900000000000549004";
	
	static final String ENITRE_TERM_CASE_SENSITIVE = "900000000000017005";
	static final String ENTIRE_TERM_CASE_INSENSITIVE = "900000000000448009";
	static final String ONLY_INITIAL_CHAR_CASE_INSENSITIVE = "900000000000020002";
	
	final public String SEMANTIC_TAG_START = "(";
	
	public enum PartionIdentifier {CONCEPT, DESCRIPTION, RELATIONSHIP};
	
	public enum CharacteristicType {	STATED_RELATIONSHIP, INFERRED_RELATIONSHIP, 
										QUALIFYING_RELATIONSHIP, ADDITIONAL_RELATIONSHIP, ALL};
										
	public static String SCTID_STATED_RELATIONSHIP = "900000000000010007";
	public static String SCTID_INFERRED_RELATIONSHIP = "900000000000011006";
	public static String SCTID_QUALIFYING_RELATIONSHIP = "900000000000225001";
	public static String SCTID_ADDITIONAL_RELATIONSHIP = "900000000000227009";

	public enum DefinitionStatus { PRIMITIVE, FULLY_DEFINED };
	public static String SCTID_PRIMITIVE = "900000000000074008";
	public static String SCTID_FULLY_DEFINED = "900000000000073002";
	
	public enum Modifier { EXISTENTIAL, UNIVERSAL};
	public static String SCTID_EXISTENTIAL_MODIFIER = "900000000000451002";
	public static String SCTID_UNIVERSAL_MODIFIER = "900000000000450001";
	
	
	public enum ActiveState { ACTIVE, INACTIVE, BOTH };
	
	public enum Acceptability { ACCEPTABLE, PREFERRED };
	
	public enum ConceptType { PRODUCT_STRENGTH, MEDICINAL_ENTITY, MEDICINAL_FORM, GROUPER, PRODUCT_ROLE, UNKNOWN };
	
	public enum Cardinality { AT_LEAST_ONE, EXACTLY_ONE };
	
	public enum DescriptionType { FSN, SYNONYM, DEFINITION};
	
	public enum ChangeStatus { CHANGE_MADE, CHANGE_NOT_REQUIRED, NO_CHANGE_MADE };
	
	public static final String FIELD_DELIMITER = "\t";
	public static final String LINE_DELIMITER = "\r\n";
	public static final String ACTIVE_FLAG = "1";
	public static final String INACTIVE_FLAG = "0";
	public static final String HEADER_ROW = "id\teffectiveTime\tactive\tmoduleId\tsourceId\tdestinationId\trelationshipGroup\ttypeId\tcharacteristicTypeId\tmodifierId\r\n";

	// Relationship columns
	public static final int REL_IDX_ID = 0;
	public static final int REL_IDX_EFFECTIVETIME = 1;
	public static final int REL_IDX_ACTIVE = 2;
	public static final int REL_IDX_MODULEID = 3;
	public static final int REL_IDX_SOURCEID = 4;
	public static final int REL_IDX_DESTINATIONID = 5;
	public static final int REL_IDX_RELATIONSHIPGROUP = 6;
	public static final int REL_IDX_TYPEID = 7;
	public static final int REL_IDX_CHARACTERISTICTYPEID = 8;
	public static final int REL_IDX_MODIFIERID = 9;
	public static final int REL_MAX_COLUMN = 9;

	// Concept columns
	// id effectiveTime active moduleId definitionStatusId
	public static final int CON_IDX_ID = 0;
	public static final int CON_IDX_EFFECTIVETIME = 1;
	public static final int CON_IDX_ACTIVE = 2;
	public static final int CON_IDX_MODULID = 3;
	public static final int CON_IDX_DEFINITIONSTATUSID = 4;

	// Description columns
	// id effectiveTime active moduleId conceptId languageCode typeId term caseSignificanceId
	public static final int DES_IDX_ID = 0;
	public static final int DES_IDX_EFFECTIVETIME = 1;
	public static final int DES_IDX_ACTIVE = 2;
	public static final int DES_IDX_MODULID = 3;
	public static final int DES_IDX_CONCEPTID = 4;
	public static final int DES_IDX_LANGUAGECODE = 5;
	public static final int DES_IDX_TYPEID = 6;
	public static final int DES_IDX_TERM = 7;
	public static final int DES_IDX_CASESIGNIFICANCEID = 8;
	
	// Language Refset columns
	// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	acceptabilityId
	public static final int LANG_IDX_ID = 0;
	public static final int LANG_IDX_EFFECTIVETIME = 1;
	public static final int LANG_IDX_ACTIVE = 2;
	public static final int LANG_IDX_MODULID = 3;
	public static final int LANG_IDX_REFSETID = 4;
	public static final int LANG_IDX_REFCOMPID = 5;
	public static final int LANG_IDX_ACCEPTABILITY_ID = 6;
	
	// Refset columns
	public static final int REF_IDX_ID = 0;
	public static final int REF_IDX_EFFECTIVETIME = 1;
	public static final int REF_IDX_ACTIVE = 2;
	public static final int REF_IDX_MODULEID = 3;
	public static final int REF_IDX_REFSETID = 4;
	public static final int REF_IDX_REFCOMPID = 5;
	public static final int REF_IDX_FIRST_ADDITIONAL = 6;

}
