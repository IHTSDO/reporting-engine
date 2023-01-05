package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * LOINC-382 List Primitive LOINC concepts
 */
public class ExistingLoincNums extends TermServerScript {
	
	private static Map<String, Concept> loincNumMap = new HashMap<>();
	private static Map<String, RelationshipTemplate> loincPartMap = new HashMap<>();
	
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
				"LoincNum, LoincPartNum, Advice, LoincPartName, LongCommonName, Concept, PT, Correlation, Expression, , , ,",
				"LoincPartNum, Advice, Detail, Detail"
		};
		String[] tabNames = new String[] {
				"LoincNums to Model",
				"Live Modeling",
				"Part Mapping Notes"
		};
		super.postInit(tabNames, columnHeadings, false);
	}

	private void runReport() throws TermServerScriptException {
		populateLoincNumMap();
		populatePartMap();
		determineExistingConcepts();
		determineExistingParts();
	}
	
	private void determineExistingConcepts() throws TermServerScriptException {
		try {
			info ("Loading " + inputFile);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(inputFile))) {
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
			info ("Loading Parts " + inputFile3);
			boolean isFirstLine = true;
			Set<String> partNumsMapped = new HashSet<>();
			Set<String> partNumsUnmapped = new HashSet<>();
			int skipped = 0;
			int mapped = 0;
			int unmapped = 0;
			try (BufferedReader br = new BufferedReader(new FileReader(inputFile3))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						String loincNum = items[0];
						String partNum = items[2];
						String partName = items[3];
						String partTypeName = items[5];
						
						//Do we have this partNum?
						RelationshipTemplate partAttribute = loincPartMap.get(partNum);
						if (partTypeName.equals("CLASS")) {
							skipped++;
							continue;
						}
						
						if (partAttribute != null) {
							mapped++;
							report(SECONDARY_REPORT,
									loincNum,
									partNum,
									"Mapped OK",
									partName,
									partAttribute);
							partNumsMapped.add(partNum);
						} else {
							unmapped++;
							report(SECONDARY_REPORT,
									loincNum,
									partNum,
									"Not Mapped - " + partTypeName + " | " + partName,
									partName);
							partNumsUnmapped.add(partNum);
						}
					} else isFirstLine = false;
				}
			}
			report (SECONDARY_REPORT, "");
			report (SECONDARY_REPORT, "Parts mapped", mapped);
			report (SECONDARY_REPORT, "Parts unmapped", unmapped);
			report (SECONDARY_REPORT, "Parts skipped", skipped);
			report (SECONDARY_REPORT, "Unique PartNums mapped", partNumsMapped.size());
			report (SECONDARY_REPORT, "Unique PartNums unmapped", partNumsUnmapped.size());
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}
	
	private void populateLoincNumMap() throws TermServerScriptException {
		for (Concept c : LoincUtils.getActiveLOINCconcepts(gl)) {
			loincNumMap.put(LoincUtils.getLoincNumFromDescription(c), c);
		}
		info("Populated map of " + loincNumMap.size() + " LOINC concepts");
	}
	
	private void populatePartMap() throws TermServerScriptException {
		try {
			int successfullTypeReplacement = 0;
			int successfullValueReplacement = 0;
			int unsuccessfullTypeReplacement = 0;
			int unsuccessfullValueReplacement = 0;
			info ("Loading Part Map" + inputFile2);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(inputFile2))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						String partNum = items[6];
						Concept attributeType = gl.getConcept(items[7]);
						Concept attributeValue = gl.getConcept(items[5]);
						
						if (!attributeType.isActive()) {
							Concept replacementType = getReplacementSafely(SECONDARY_REPORT, partNum, attributeType, false);
							String replacementMsg = replacementType == null ? " no replacement available." : " replaced with " + replacementType;
							if (replacementType == null) unsuccessfullTypeReplacement++; 
								else successfullTypeReplacement++;
							report(TERTIARY_REPORT, partNum, "Mapped to inactive type: " + attributeType + replacementMsg);
							if (replacementType != null) {
								attributeType = replacementType;
							}
						}
						if (!attributeValue.isActive()) {
							Concept replacementValue = getReplacementSafely(SECONDARY_REPORT, partNum, attributeValue, false);
							String replacementMsg = replacementValue == null ? "  no replacement available." : " replaced with " + replacementValue;
							if (replacementValue == null) unsuccessfullValueReplacement++; 
							else successfullValueReplacement++;
							String prefix = replacementValue == null ? "* " : "";
							report(TERTIARY_REPORT, partNum, prefix + "Mapped to inactive value: " + attributeValue + replacementMsg);
							if (replacementValue != null) {
								attributeValue = replacementValue;
							}
						}
						
						loincPartMap.put(partNum, new RelationshipTemplate(attributeType, attributeValue));
					} else isFirstLine = false;
				}
			}
			info("Populated map of " + loincPartMap.size() + " LOINC parts");
			report(TERTIARY_REPORT, "");
			report(TERTIARY_REPORT, "successfullTypeReplacement",successfullTypeReplacement);
			report(TERTIARY_REPORT, "unsuccessfullTypeReplacement",unsuccessfullTypeReplacement);
			report(TERTIARY_REPORT, "successfullValueReplacement",successfullValueReplacement);
			report(TERTIARY_REPORT, "unsuccessfullValueReplacement",unsuccessfullValueReplacement);

		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

}
