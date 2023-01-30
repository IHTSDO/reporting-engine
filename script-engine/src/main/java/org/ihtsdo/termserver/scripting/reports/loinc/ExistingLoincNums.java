package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.*;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * LE-3
 */
public class ExistingLoincNums extends TermServerScript {
	//-f "G:\My Drive\018_Loinc\2023\LOINC Top 100 - loinc.tsv" 
	//-f2 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - Parts Map 2017.tsv"  
	//-f3 "G:\My Drive\018_Loinc\2023\LOINC Top 100 - LoincPartLink_Supplementary.tsv"
	//-f4 "C:\Users\peter\Backup\Loinc_2.73\AccessoryFiles\PartFile\Part.csv"
	private int FILE_IDX_LOINC_100 = 0;
	private int FILE_IDX_LOINC_100_PARTS_MAP = 1;
	private int FILE_IDX_LOINC_100_Supplement = 2;
	private int FILE_IDX_LOINC_PARTS = 3;
	
	private static Map<String, Concept> loincNumMap = new HashMap<>();
	private static Map<String, RelationshipTemplate> loincPartAttributeMap = new HashMap<>();
	private Map<Concept, Concept> knownReplacementMap = new HashMap<>();
	private Map<String, LoincPart> loincParts = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ExistingLoincNums report = new ExistingLoincNums();
		try {
			report.runStandAlone = false;
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.init(args);
			report.loadProjectSnapshot(false);
			report.postInit();
			report.runReport();
		} finally {
			report.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"LoincNum, LongCommonName, Concept, PT, Correlation, Expression, , , ,",
				"LoincNum, LoincPartNum, Advice, LoincPartName, SNOMED Attribute, ",
				"LoincPartNum, LoincPartName, PartStatus, Advice, Detail, Detail",
				"LoincPartNum, LoincPartName, PartStatus, Advice, Detail, Detail",
				"LoincNum, Existing Concept, Template, Proposed FSN, Current Model, Proposed Model, Difference"
		};
		String[] tabNames = new String[] {
				"LoincNums to Model",
				"All Mapping Notes",
				"Part Map Notes",
				"Template Based Map Results",
				"Proposed Model Comparison"
		};
		super.postInit(tabNames, columnHeadings, false);
		
		knownReplacementMap.put(gl.getConcept("720309005 |Immunoglobulin G antibody to Streptococcus pneumoniae 43 (substance)|"), gl.getConcept("767402003 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 43 (substance)|"));
		knownReplacementMap.put(gl.getConcept("720308002 |Immunoglobulin G antibody to Streptococcus pneumoniae 34 (substance)|"), gl.getConcept("767408004 |Immunoglobulin G antibody to Streptococcus pneumoniae Danish serotype 34 (substance)|"));
		knownReplacementMap.put(gl.getConcept("54708003 |Extended zinc insulin (substance)|"), gl.getConcept("10329000 |Zinc insulin (substance)|"));
		knownReplacementMap.put(gl.getConcept("409258004 |Hydroxocobalamin (substance)|"), gl.getConcept("1217427007 |Aquacobalamin (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("301892007 |Biopterin analyte (substance)|"), gl.getConcept("1231481007 |Substance with biopterin structure (substance)|"));
		knownReplacementMap.put(gl.getConcept("27192005 |Aminosalicylic acid (substance)|"), gl.getConcept("255666002 |Para-aminosalicylic acid (substance)|"));
		knownReplacementMap.put(gl.getConcept("250428009 |Substance with antimicrobial mechanism of action (substance)|"), gl.getConcept("419241000 |Substance with antibacterial mechanism of action (substance)|"));
		knownReplacementMap.put(gl.getConcept("119306004 |Drain device specimen (specimen)|"), gl.getConcept("1003707004 |Drain device submitted as specimen (specimen)|"));
	
		//Just temporarily, we need to create some concepts that aren't visible yet
		gl.registerConcept("10021010000100 |Platelet poor plasma or whole blood specimen (specimen)|"); 
		gl.registerConcept("10051010000107 |Plasma specimen or whole blood specimen (specimen)|");
		gl.registerConcept("10031010000102 |Bromocresol purple dye binding technique (qualifier value)|");
		gl.registerConcept("10041010000105 |Oximetry technique (qualifier value)|");
		gl.registerConcept("10061010000109 |Screening technique (qualifier value)|");
	}

	private void runReport() throws TermServerScriptException {
		populateLoincNumMap();
		loadLoincParts();
		populatePartAttributeMap();
		LoincTemplatedConcept.initialise(this, gl, loincPartAttributeMap);
		determineExistingConcepts();
		determineExistingParts();
		LoincTemplatedConcept.reportStats();
	}
	
	private void determineExistingConcepts() throws TermServerScriptException {
		try {
			info ("Loading " + getInputFile(FILE_IDX_LOINC_100));
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100)))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						String loincNum = items[0];
						String longCommonName = items[25];
						//Do we have this loincNum
						Concept loincConcept = loincNumMap.get(loincNum);
						if (loincConcept != null) {
							report(PRIMARY_REPORT,
									loincNum,
									longCommonName,
									loincConcept, 
									loincConcept.getPreferredSynonym(),
									LoincUtils.getCorrelation(loincConcept),
									loincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP));
						} else {
							report(PRIMARY_REPORT,
									loincNum,
									longCommonName,
									"Not Modelled");
						}
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}
	
	private void determineExistingParts() throws TermServerScriptException {
		try {
			info ("Loading Parts: " + getInputFile(FILE_IDX_LOINC_100_Supplement));
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100_Supplement)))) {
				String line;
				ArrayList<LoincPart> loincParts = new ArrayList<>();
				String lastLoincNum = "";
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						String loincNum = items[0];
						String partNum = items[2];
						String partName = items[3];
						String partTypeName = items[5];
						
						//Have we moved on to a new LOINC Num?  Do model comparison on the last number if so,
						//which is now assumed to be complete.
						if (!lastLoincNum.equals(loincNum)) {
							if (!lastLoincNum.isEmpty()) {
								LoincTemplatedConcept templatedConcept = LoincTemplatedConcept.populateModel(lastLoincNum, loincParts);
								doProposedModelComparison(lastLoincNum, templatedConcept);
							}
							lastLoincNum = loincNum;
							loincParts.clear();
						}
						loincParts.add(new LoincPart(partNum, partTypeName, partName));
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}
	
	private void doProposedModelComparison(String loincNum, LoincTemplatedConcept loincTemplatedConcept) throws TermServerScriptException {
		//Do we have this loincNum
		Concept existingLoincConcept = loincNumMap.get(loincNum);
		Concept proposedLoincConcept = loincTemplatedConcept.getConcept();
		
		String existingSCG = "N/A";
		String modelDiff = "";
		String proposedSCG = proposedLoincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String existingLoincConceptStr = "N/A";
		if (existingLoincConcept != null) {
			existingLoincConceptStr = existingLoincConcept.getId();
			existingSCG = existingLoincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			modelDiff = SnomedUtils.getModelDifferences(existingLoincConcept, proposedLoincConcept, CharacteristicType.STATED_RELATIONSHIP);
		}
		report(QUINARY_REPORT, loincNum, existingLoincConceptStr, loincTemplatedConcept.getClass().getSimpleName(), proposedLoincConcept.getFsn(), existingSCG, proposedSCG, modelDiff);
	}

	private void populateLoincNumMap() throws TermServerScriptException {
		for (Concept c : LoincUtils.getActiveLOINCconcepts(gl)) {
			loincNumMap.put(LoincUtils.getLoincNumFromDescription(c), c);
		}
		info("Populated map of " + loincNumMap.size() + " LOINC concepts");
	}
	
	private void populatePartAttributeMap() throws TermServerScriptException {
		int lineNum = 0;
		try {
			int successfullTypeReplacement = 0;
			int successfullValueReplacement = 0;
			int unsuccessfullTypeReplacement = 0;
			int unsuccessfullValueReplacement = 0;
			info ("Loading Part Attribute Map: " + getInputFile(FILE_IDX_LOINC_100_PARTS_MAP));
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100_PARTS_MAP)))) {
				String line;
				while ((line = br.readLine()) != null) {
					lineNum++;
					if (lineNum > 1) {
						String[] items = line.split("\t");
						//Skip the row if it's not active
						if (items[2].equals("0")) {
							continue;
						}
						String partNum = items[6];
						Concept attributeType = gl.getConcept(items[7]);
						Concept attributeValue = gl.getConcept(items[5]);
						
						if (!attributeType.isActive()) {
							String hardCodedIndicator = " hardcoded";
							Concept replacementType = knownReplacementMap.get(attributeType);
							if (replacementType == null) {
								hardCodedIndicator = "";
								replacementType = getReplacementSafely(TERTIARY_REPORT, partNum, attributeType, false);
							} 
							String replacementMsg = replacementType == null ? " no replacement available." : hardCodedIndicator + " replaced with " + replacementType;
							if (replacementType == null) unsuccessfullTypeReplacement++; 
								else successfullTypeReplacement++;
							report(TERTIARY_REPORT, partNum, "Mapped to" + hardCodedIndicator + " inactive type: " + attributeType + replacementMsg);
							if (replacementType != null) {
								attributeType = replacementType;
							}
						}
						if (!attributeValue.isActive()) {
							String hardCodedIndicator = " hardcoded";
							Concept replacementValue = knownReplacementMap.get(attributeValue);
							if (replacementValue == null) {
								hardCodedIndicator = "";
								replacementValue = getReplacementSafely(TERTIARY_REPORT, partNum, attributeValue, false);
							}
							String replacementMsg = replacementValue == null ? "  no replacement available." : hardCodedIndicator + " replaced with " + replacementValue;
							if (replacementValue == null) unsuccessfullValueReplacement++; 
							else successfullValueReplacement++;
							String prefix = replacementValue == null ? "* " : "";
							LoincPart part = loincParts.get(partNum);
							String partName = part == null ? "Unlisted" : part.getPartName();
							String partStatus = part == null ? "Unlisted" : part.getStatus().name();
							report(TERTIARY_REPORT, partNum, partName, partStatus, prefix + "Mapped to" + hardCodedIndicator + " inactive value: " + attributeValue + replacementMsg);
							if (replacementValue != null) {
								attributeValue = replacementValue;
							}
						}
						
						loincPartAttributeMap.put(partNum, new RelationshipTemplate(attributeType, attributeValue));
					}
				}
			}
			info("Populated map of " + loincPartAttributeMap.size() + " LOINC parts to attributes");
			report(TERTIARY_REPORT, "");
			report(TERTIARY_REPORT, "successfullTypeReplacement",successfullTypeReplacement);
			report(TERTIARY_REPORT, "unsuccessfullTypeReplacement",unsuccessfullTypeReplacement);
			report(TERTIARY_REPORT, "successfullValueReplacement",successfullValueReplacement);
			report(TERTIARY_REPORT, "unsuccessfullValueReplacement",unsuccessfullValueReplacement);

		} catch (Exception e) {
			throw new TermServerScriptException("At line " + lineNum, e);
		}
	}

	
	private void loadLoincParts() throws TermServerScriptException {
		info ("Loading Loinc Parts: " + getInputFile(FILE_IDX_LOINC_PARTS));
		try {
			Reader in = new InputStreamReader(new FileInputStream(getInputFile(FILE_IDX_LOINC_PARTS)));
			//withSkipHeaderRecord() is apparently ignore when using iterator
			Iterator<CSVRecord> iterator = CSVFormat.EXCEL.parse(in).iterator();
			CSVRecord header = iterator.next();
			while (iterator.hasNext()) {
				CSVRecord thisLine = iterator.next();
				LoincPart loincPart = LoincPart.parse(thisLine);
				if (loincParts.containsKey(loincPart.getPartNumber())) {
					throw new TermServerScriptException("Duplicate Part Number Loaded: " + loincPart.getPartNumber());
				}
				loincParts.put(loincPart.getPartNumber(), loincPart);
			}
			info("Loaded " + loincParts.size() + " loinc parts.");
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load " + getInputFile(FILE_IDX_LOINC_PARTS), e);
		}
	}
}
