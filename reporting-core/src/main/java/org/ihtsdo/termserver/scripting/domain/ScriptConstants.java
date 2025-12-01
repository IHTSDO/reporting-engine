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

	String GFOLDER_ADHOC_REPORTS = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
	String GFOLDER_ADHOC_UPDATES = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
	String GFOLDER_BATCH_IMPORTS = "1bO3v1PApVCEc3BWWrKwc525vla7ZMPoE"; //Batch Imports
	String GFOLDER_DRUGS_MISSING = "1SQw8vYXeB-LYPfoVzWwyGFjGp1yre2cT"; // Drugs Missing
	String GFOLDER_EXTRACT_AND_PROMOTE = "12ZyVGxnFVXZfsKIHxr3Ft2Z95Kdb7wPl"; //Extract and Promote
	String GFOLDER_GENERAL_QA = "1PWtDYFfqLoUwk17HlFgNK648Mmra-1GA"; //General QA
	String GFOLDER_LOINC = "1yF2g_YsNBepOukAu2vO0PICqJMAyURwh"; //LOINC
	String GFOLDER_MS = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
	String GFOLDER_NPU = "1019iuglVN-TXmEgEmWYkBKWNebH-8YgT"; //NPU
	String GFOLDER_NUVA = "19OR1N_vtMb0kUi2YyNo6jqT3DiFRfbPO"; //NUVA
	String GFOLDER_QI = "1ndqzuQs7C-8ODbARPWh4xJVshWIDF9gN"; //QI
	String GFOLDER_QI_NORMALIZATION = "1Ay_IwhPD1EkeIYWuU6q7xgWBIzfEf6dl"; //QI / Normalization
	String GFOLDER_QI_STATS = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
	String GFOLDER_RELEASE_QA = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA Reports
	String GFOLDER_RELEASE_STATS = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
	String GFOLDER_TECHNICAL_SPECIALIST = "13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"; //Technical Specialist

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
	Concept HAS_DISPOSITION = new Concept ("726542003", "Has disposition (attribute)");
	Concept PLAYS_ROLE = new Concept("766939001","Plays role (attribute)");

	//Concrete Domain Attributes
	Concept HAS_PRES_STRENGTH_VALUE = new Concept ("1142135004","Has presentation strength numerator value (attribute)");
	Concept HAS_PRES_STRENGTH_DENOM_VALUE = new Concept ("1142136003","Has presentation strength denominator value (attribute)");
	Concept HAS_CONC_STRENGTH_VALUE = new Concept ("1142138002","Has concentration strength numerator value (attribute)");
	Concept HAS_CONC_STRENGTH_DENOM_VALUE = new Concept ("1142137007","Has concentration strength denominator value (attribute)");
	Concept COUNT_BASE_ACTIVE_INGREDIENT = new Concept ("1142139005", "Count of base of active ingredient (attribute)");
	Concept COUNT_OF_BASE_AND_MODIFICATION = new Concept ("1142141006", "Count of base and modification pair (attribute)");
	
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
	Concept BODY_STRUCTURE = new Concept ("123037004","Body structure (body structure)|");
	Concept CLINICAL_FINDING = new Concept ("404684003", "Clinical finding (finding)");
	Concept COMPLICATION = new Concept ("116223007", "Complication (disorder)");
	Concept DEVICE = new Concept ("49062001","Device (physical object)");
	Concept EVENT = new Concept("272379006", "Event (event)");
	Concept ORGANISM = new Concept ("410607006", "Organism (organism)");
	Concept PROCEDURE = new Concept ("71388002", "Procedure (procedure)");
	Concept ROOT_CONCEPT = new Concept (SCTID_ROOT_CONCEPT.toString(), "SNOMED CT Concept (SNOMED RT+CTV3)");
	Concept SITN_WITH_EXP_CONTXT = new Concept ("243796009","Situation with explicit context (situation)");
	Concept SPECIMEN = new Concept("123038009","Specimen (specimen)");

	//Secondary hierarchies
	Concept MORPHOLOGIC_ABNORMALITY = new Concept("118956008", "Body structure, altered from its original anatomical structure (morphologic abnormality)");
	Concept DISEASE = new Concept ("64572001", "Disease (disorder)");

	//Attribute Types
	Concept AFTER = new Concept("255234002", "After (attribute)");
	Concept ASSOC_MORPH = new Concept("116676008", "Associated morphology (attribute)");
	Concept ASSOC_WITH = new Concept("47429007", "Associated with (attribute)");
	Concept CAUSE_AGENT = new Concept("246075003", "Causative agent (attribute)");
	Concept COMPONENT = new Concept("246093002", "Component (attribute)");
	Concept DIRECT_DEVICE = new Concept("363699004", "Direct device (attribute)");
	Concept DIRECT_SITE = new Concept("704327008","Direct site (attribute)");
	Concept DUE_TO = new Concept("42752001", "Due to (attribute)");
	Concept FINDING_SITE = new Concept("363698007", "Finding site (attribute)");
	Concept HAS_DEVICE_CHARAC = new Concept("840562008", "Has device characteristic (attribute)");
	Concept HAS_INTERPRETATION = new Concept("363713009","Has interpretation (attribute)");
	Concept HAS_INTENT = new Concept("363703001", "Has intent (attribute)");
	Concept INHERES_IN = new Concept("704319004", "Inheres in (attribute)");
	Concept INTERPRETS = new Concept("363714003", "Interprets (attribute)");
	Concept METHOD = new Concept("260686004","Method (attribute)");
	Concept NAMESPACE_CONCEPT = new Concept("370136006", "Namespace concept (namespace concept)");
	Concept OBSERVABLE_ENTITY = new Concept("363787002","Observable entity (observable entity)");
	Concept OCCURRENCE = new Concept ("246454002", "Occurrence (attribute)");
	Concept PART_OF = new Concept("123005000"); // |Part of (attribute)|
	Concept PATHOLOGICAL_PROCESS = new Concept("370135005", "Pathological process (attribute)");
	Concept PROCEDURE_SITE = new Concept("363704007", "Procedure site (attribute)|");
	Concept PROCEDURE_SITE_DIRECT = new Concept("405813007", "Procedure site - direct (attribute)|");
	Concept PROCEDURE_SITE_INDIRECT = new Concept("405814001", "Procedure site - indirect (attribute)|");
	Concept PROPERTY_ATTRIB = new Concept("370130000", "Property (attribute)");
	Concept TECHNIQUE = new Concept("246501002", "Technique (attribute)");
	Concept USING_DEVICE = new Concept("424226004", "Using device (attribute)");

	Concept LEFT = new Concept("7771000", "Left (qualifier value)");
	String SCTID_LEFT = "7771000";
	Concept RIGHT = new Concept("24028007", "Right (qualifier value)|");
	String SCTID_RIGHT = "24028007";
	Concept BILATERAL = new Concept("51440002", "Right and left (qualifier value)|");
	String SCTID_BILATERAL = "51440002";

	//Drug Terms
	String ACETAMINOPHEN = "acetaminophen";
	String PARACETAMOL = "paracetamol";

}
