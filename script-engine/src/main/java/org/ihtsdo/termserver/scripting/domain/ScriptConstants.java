package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.RF2Constants;

public interface ScriptConstants extends RF2Constants {
	
	final int PRIMARY_REPORT = 0;
	final int SECONDARY_REPORT = 1;
	final int TERTIARY_REPORT = 2;
	final int QUATERNARY_REPORT = 3;
	final int QUINARY_REPORT = 4;
	final int SENARY_REPORT = 5;  //One of the few times I'd prefer 1 based indexing!
	final int SEPTENARY_REPORT = 6;
	final int OCTONARY_REPORT = 7;
	final int NONARY_REPORT = 8;
	final int DENARY_REPORT = 9;
	
	static Concept IS_A =  new Concept ("116680003");  // | Is a (attribute) |
	static Concept NULL_CONCEPT = new Concept ("-1");
	static Concept MULTI_CONCEPT = new Concept ("-2");
	
	static final String INT = "INT";
	static final String MS = "MS";
	
	//Top Level hierarchies
	static Concept ORGANISM = new Concept ("410607006", "Organism (organism)");
	
	//Drugs and Substances
	static Concept PHARM_BIO_PRODUCT = new Concept ("373873005") ; //Pharmaceutical / biologic product (product)
	static Concept MEDICINAL_PRODUCT = new Concept ("763158003", "Medicinal product (product)"); 
	static Concept HAS_ACTIVE_INGRED = new Concept ("127489000","Has active ingredient (attribute)");
	static Concept HAS_PRECISE_INGRED = new Concept ("762949000", "Has precise active ingredient (attribute)");
	static Concept SUBSTANCE = new Concept("105590001", "Substance (substance)");
	static Concept HAS_MANUFACTURED_DOSE_FORM = new Concept ("411116001","Has manufactured dose form (attribute)");
	static Concept HAS_BOSS = new Concept ("732943007","Has basis of strength substance (attribute)");
	static Concept PHARM_DOSE_FORM = new Concept ("736542009", "Pharmaceutical dose form (dose form)");
	
	//static Concept HAS_PRES_STRENGTH_VALUE = new Concept ("732944001","Has presentation strength numerator value (attribute)");
	static Concept HAS_PRES_STRENGTH_UNIT = new Concept ("732945000","Has presentation strength numerator unit (attribute)");
	//static Concept HAS_PRES_STRENGTH_DENOM_VALUE = new Concept ("732946004","Has presentation strength denominator value (attribute)");
	static Concept HAS_PRES_STRENGTH_DENOM_UNIT = new Concept ("732947008","Has presentation strength denominator unit (attribute)");
	
	//static Concept HAS_CONC_STRENGTH_VALUE = new Concept ("733724008","Has concentration strength numerator value (attribute)");
	static Concept HAS_CONC_STRENGTH_UNIT = new Concept ("733725009","Has concentration strength numerator unit (attribute)");
	//static Concept HAS_CONC_STRENGTH_DENOM_VALUE = new Concept ("733723002","Has concentration strength denominator value (attribute)");
	static Concept HAS_CONC_STRENGTH_DENOM_UNIT = new Concept ("733722007","Has concentration strength denominator unit (attribute)");

	static Concept HAS_UNIT_OF_PRESENTATION = new Concept ("763032000","Has unit of presentation (attribute)");
	static Concept IS_MODIFICATION_OF = new Concept ("738774007", "Is modification of (attribute)");
	static Concept DRUG_PREPARATION = new Concept("105904009","Type of drug preparation (qualifier value)");
	static Concept HAS_DISPOSITION = new Concept ("726542003", "Has disposition (attribute)");
	//static Concept COUNT_BASE_ACTIVE_INGREDIENT = new Concept ("766952006", "Count of base of active ingredient (attribute)");
	//static Concept COUNT_OF_BASE_AND_MODIFICATION = new Concept ("766954007", "Count of base and modification pair (attribute)");
	static Concept PLAYS_ROLE = new Concept("766939001","Plays role (attribute)");

	//Concrete Domain Attributes
	static Concept HAS_PRES_STRENGTH_VALUE = new Concept ("1142135004","Has presentation strength numerator value (attribute)");
	static Concept HAS_PRES_STRENGTH_DENOM_VALUE = new Concept ("1142136003","Has presentation strength denominator value (attribute)");
	static Concept HAS_CONC_STRENGTH_VALUE = new Concept ("1142138002","Has concentration strength numerator value (attribute)");
	static Concept HAS_CONC_STRENGTH_DENOM_VALUE = new Concept ("1142137007","Has concentration strength denominator value (attribute)");
	static Concept COUNT_BASE_ACTIVE_INGREDIENT = new Concept ("1142139005", "Count of base of active ingredient (attribute)");
	static Concept COUNT_OF_BASE_AND_MODIFICATION = new Concept ("1142141006", "Count of base and modification pair (attribute)");
	
	static Concept PATHOLOGICAL_PROCESS = new Concept ("370135005", "Pathological process (attribute)");
	
	static Concept NUMBER = new Concept("260299005", "Number (qualifier value)");
	static Concept PICOGRAM = new Concept ("258687006", "picogram (qualifier value)");
	static Concept NANOGRAM = new Concept ("258686002", "nanogram (qualifier value)");
	static Concept MICROGRAM = new Concept ("258685003", "microgram (qualifier value)"); 
	static Concept MICROEQUIVALENT = new Concept ("258728009", "microequivalent (qualifier value)"); 
	static Concept MILLIGRAM = new Concept ("258684004", "milligram (qualifier value)"); 
	static Concept MILLILITER = new Concept ("258773002","Milliliter (qualifier value)");
	static Concept MICROLITER = new Concept ("258774008","Microliter (qualifier value)|");
	static Concept UEQ = new Concept ("258728009","microequivalent (qualifier value)");
	static Concept MEQ = new Concept ("258727004","milliequivalent (qualifier value)");
	static Concept GRAM = new Concept("258682000","gram (qualifier value)");
	static Concept LITER = new Concept ("258770004","liter (qualifier value)");
	static Concept INTERNATIONAL_UNIT = new Concept ("258997004", "international unit (qualifier value)"); 
	static Concept UNIT = new Concept("767525000", "Unit (qualifier value)");
	static Concept MILLION_UNIT = new Concept("396186001", "Million unit (qualifier value)");
	
	static Concept ROOT_CONCEPT = new Concept (SCTID_ROOT_CONCEPT.toString(), "SNOMED CT Concept (SNOMED RT+CTV3)");
	static Concept BODY_STRUCTURE = new Concept ("123037004","Body structure (body structure)|");
	static Concept CLINICAL_FINDING = new Concept ("404684003", "Clinical finding (finding)");
	static Concept PROCEDURE = new Concept ("71388002", "Procedure (procedure)");
	static Concept SITN_WITH_EXP_CONTXT = new Concept ("243796009","Situation with explicit context (situation)");
	static Concept SPECIMEN = new Concept("123038009","Specimen (specimen)");
	static Concept COMPLICATION = new Concept ("116223007", "Complication (disorder)");
	static Concept DISEASE = new Concept ("64572001", "Disease (disorder)");
	static Concept EVENT = new Concept("272379006", "Event (event)");
	static Concept DEVICE = new Concept ("49062001","Device (physical object)");
	
	static Concept CAUSE_AGENT = new Concept ("246075003","Causative agent (attribute)");
	static Concept ASSOC_MORPH = new Concept ("116676008", "Associated morphology (attribute)");
	static Concept OBSERVABLE_ENTITY = new Concept("363787002","Observable entity (observable entity)");
	static Concept DUE_TO = new Concept("42752001", "Due to (attribute)");
	static Concept METHOD = new Concept("260686004","Method (attribute)");
	static Concept AFTER = new Concept("255234002", "After (attribute)");
	static Concept ASSOC_WITH = new Concept ("47429007", "Associated with (attribute)");
	static Concept PART_OF = new Concept("123005000"); // |Part of (attribute)|
	static Concept FINDING_SITE = new Concept ("363698007", "Finding site (attribute)");
	static Concept ANAT_OR_ACQ_BODY_STRUCT = new Concept("442083009", "Anatomical or acquired body structure (body structure)");
	static Concept NAMESPACE_CONCEPT = new Concept("370136006", "Namespace concept (nameespace concept)");
	static Concept SPECIAL_CONCEPT = new Concept("370115009", "Special concept (special concept)");
	static Concept HAS_DEVICE_CHARAC = new Concept("840562008", "Has device characteristic (attribute)");
	static Concept HAS_COMP_MATERIAL = new Concept("840560000", "Has compositional material (attribute)");
	
	static Concept LEFT = new Concept("7771000", "Left (qualifier value)");
	static Concept RIGHT = new Concept("24028007", "Right (qualifier value)|");
	
	//Drug Terms
	static String ACETAMINOPHEN = "acetaminophen";
	static String PARACETAMOL = "paracetamol";
	static String productPrefix = "Product containing ";
	

}
