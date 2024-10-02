package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.RF2Constants;

public interface ScriptConstants extends RF2Constants {
	
	int PRIMARY_REPORT = 0;
	int SECONDARY_REPORT = 1;
	int TERTIARY_REPORT = 2;
	int QUATERNARY_REPORT = 3;
	int QUINARY_REPORT = 4;
	int SENARY_REPORT = 5;  //One of the few times I'd prefer 1 based indexing!
	int SEPTENARY_REPORT = 6;
	int OCTONARY_REPORT = 7;
	int NONARY_REPORT = 8;
	int DENARY_REPORT = 9;

	String GFOLDER_QI = "1ndqzuQs7C-8ODbARPWh4xJVshWIDF9gN"; //QI
	String GFOLDER_DRUGS_MISSING = "1SQw8vYXeB-LYPfoVzWwyGFjGp1yre2cT"; // Drugs Missing
	String GFOLDER_ADHOC_REPORTS = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
	String GFOLDER_ADHOC_UPDATES = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
	String GFOLDER_MS = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
	String GFOLDER_RELEASE_QA = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA Reports
	String GFOLDER_QI_STATS = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
	String GFOLDER_RELEASE_STATS = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
	String GFOLDER_LOINC = "1yF2g_YsNBepOukAu2vO0PICqJMAyURwh"; //LOINC
	
	Concept IS_A =  new Concept ("116680003");  // | Is a (attribute) |
	Concept NULL_CONCEPT = new Concept ("-1");
	Concept MULTI_CONCEPT = new Concept ("-2");
	
	String INT = "INT";
	String MS = "MS";
	
	//Drugs and Substances
	Concept PHARM_BIO_PRODUCT = new Concept ("373873005") ; //Pharmaceutical / biologic product (product)
	Concept MEDICINAL_PRODUCT = new Concept ("763158003", "Medicinal product (product)"); 
	Concept HAS_ACTIVE_INGRED = new Concept ("127489000","Has active ingredient (attribute)");
	Concept HAS_PRECISE_INGRED = new Concept ("762949000", "Has precise active ingredient (attribute)");
	Concept SUBSTANCE = new Concept("105590001", "Substance (substance)");
	Concept HAS_MANUFACTURED_DOSE_FORM = new Concept ("411116001","Has manufactured dose form (attribute)");
	Concept HAS_BOSS = new Concept ("732943007","Has basis of strength substance (attribute)");
	Concept PHARM_DOSE_FORM = new Concept ("736542009", "Pharmaceutical dose form (dose form)");
	
	Concept HAS_PRES_STRENGTH_UNIT = new Concept ("732945000","Has presentation strength numerator unit (attribute)");
	Concept HAS_PRES_STRENGTH_DENOM_UNIT = new Concept ("732947008","Has presentation strength denominator unit (attribute)");
	
	Concept HAS_CONC_STRENGTH_UNIT = new Concept ("733725009","Has concentration strength numerator unit (attribute)");
	Concept HAS_CONC_STRENGTH_DENOM_UNIT = new Concept ("733722007","Has concentration strength denominator unit (attribute)");

	Concept HAS_UNIT_OF_PRESENTATION = new Concept ("763032000","Has unit of presentation (attribute)");
	Concept IS_MODIFICATION_OF = new Concept ("738774007", "Is modification of (attribute)");
	Concept DRUG_PREPARATION = new Concept("105904009","Type of drug preparation (qualifier value)");
	Concept HAS_DISPOSITION = new Concept ("726542003", "Has disposition (attribute)");
	Concept PLAYS_ROLE = new Concept("766939001","Plays role (attribute)");

	//Concrete Domain Attributes
	Concept HAS_PRES_STRENGTH_VALUE = new Concept ("1142135004","Has presentation strength numerator value (attribute)");
	Concept HAS_PRES_STRENGTH_DENOM_VALUE = new Concept ("1142136003","Has presentation strength denominator value (attribute)");
	Concept HAS_CONC_STRENGTH_VALUE = new Concept ("1142138002","Has concentration strength numerator value (attribute)");
	Concept HAS_CONC_STRENGTH_DENOM_VALUE = new Concept ("1142137007","Has concentration strength denominator value (attribute)");
	Concept COUNT_BASE_ACTIVE_INGREDIENT = new Concept ("1142139005", "Count of base of active ingredient (attribute)");
	Concept COUNT_OF_BASE_AND_MODIFICATION = new Concept ("1142141006", "Count of base and modification pair (attribute)");
	
	Concept PATHOLOGICAL_PROCESS = new Concept ("370135005", "Pathological process (attribute)");
	
	Concept NUMBER = new Concept("260299005", "Number (qualifier value)");
	Concept PICOGRAM = new Concept ("258687006", "picogram (qualifier value)");
	Concept NANOGRAM = new Concept ("258686002", "nanogram (qualifier value)");
	Concept MICROGRAM = new Concept ("258685003", "microgram (qualifier value)"); 
	Concept MICROEQUIVALENT = new Concept ("258728009", "microequivalent (qualifier value)"); 
	Concept MILLIGRAM = new Concept ("258684004", "milligram (qualifier value)"); 
	Concept MILLILITER = new Concept ("258773002","Milliliter (qualifier value)");
	Concept MICROLITER = new Concept ("258774008","Microliter (qualifier value)|");
	Concept UEQ = new Concept ("258728009","microequivalent (qualifier value)");
	Concept MEQ = new Concept ("258727004","milliequivalent (qualifier value)");
	Concept GRAM = new Concept("258682000","gram (qualifier value)");
	Concept LITER = new Concept ("258770004","liter (qualifier value)");
	Concept INTERNATIONAL_UNIT = new Concept ("258997004", "international unit (qualifier value)"); 
	Concept UNIT = new Concept("767525000", "Unit (qualifier value)");
	Concept MILLION_UNIT = new Concept("396186001", "Million unit (qualifier value)");
	
	//Top Level hierarchies
	Concept ROOT_CONCEPT = new Concept (SCTID_ROOT_CONCEPT.toString(), "SNOMED CT Concept (SNOMED RT+CTV3)");
	Concept ORGANISM = new Concept ("410607006", "Organism (organism)");
	Concept BODY_STRUCTURE = new Concept ("123037004","Body structure (body structure)|");
	Concept CLINICAL_FINDING = new Concept ("404684003", "Clinical finding (finding)");
	Concept PROCEDURE = new Concept ("71388002", "Procedure (procedure)");
	Concept SITN_WITH_EXP_CONTXT = new Concept ("243796009","Situation with explicit context (situation)");
	Concept SPECIMEN = new Concept("123038009","Specimen (specimen)");
	Concept COMPLICATION = new Concept ("116223007", "Complication (disorder)");
	Concept DISEASE = new Concept ("64572001", "Disease (disorder)");
	Concept EVENT = new Concept("272379006", "Event (event)");
	Concept DEVICE = new Concept ("49062001","Device (physical object)");
	
	Concept CAUSE_AGENT = new Concept ("246075003","Causative agent (attribute)");
	Concept ASSOC_MORPH = new Concept ("116676008", "Associated morphology (attribute)");
	Concept OBSERVABLE_ENTITY = new Concept("363787002","Observable entity (observable entity)");
	Concept DUE_TO = new Concept("42752001", "Due to (attribute)");
	Concept METHOD = new Concept("260686004","Method (attribute)");
	Concept AFTER = new Concept("255234002", "After (attribute)");
	Concept ASSOC_WITH = new Concept ("47429007", "Associated with (attribute)");
	Concept PART_OF = new Concept("123005000"); // |Part of (attribute)|
	Concept FINDING_SITE = new Concept ("363698007", "Finding site (attribute)");
	Concept OCCURRENCE = new Concept ("246454002", "Occurrence (attribute)");
	Concept ANAT_OR_ACQ_BODY_STRUCT = new Concept("442083009", "Anatomical or acquired body structure (body structure)");
	Concept NAMESPACE_CONCEPT = new Concept("370136006", "Namespace concept (nameespace concept)");
	Concept SPECIAL_CONCEPT = new Concept("370115009", "Special concept (special concept)");
	Concept HAS_DEVICE_CHARAC = new Concept("840562008", "Has device characteristic (attribute)");
	Concept HAS_COMP_MATERIAL = new Concept("840560000", "Has compositional material (attribute)");
	Concept DIRECT_SUBST = new Concept("363701004", "Direct substance (attribute)");
	Concept DIRECT_SITE = new Concept("704327008","Direct site (attribute)");
	Concept TECHNIQUE = new Concept("246501002","Technique (attribute)");
	Concept USING_SUBST = new Concept("424361007", "Using substance (attribute)");
	Concept USING_DEVICE = new Concept("424226004", "Using device (attribute)");
	Concept INTERPRETS = new Concept("363714003", "Interprets (attribute)");
	Concept HAS_INTERPRETATION = new Concept("363713009","Has interpretation (attribute)");
	Concept COMPONENT = new Concept("246093002", "Component (attribute)");
	Concept PROPERTY_ATTRIB = new Concept ("370130000", "Property (attribute)");
	Concept INHERES_IN = new Concept("704319004", "Inheres in (attribute)");

	Concept LEFT = new Concept("7771000", "Left (qualifier value)");
	Concept RIGHT = new Concept("24028007", "Right (qualifier value)|");
	Concept BILATERAL = new Concept("51440002", "Right and left (qualifier value)|");

	//Drug Terms
	String ACETAMINOPHEN = "acetaminophen";
	String PARACETAMOL = "paracetamol";

}
