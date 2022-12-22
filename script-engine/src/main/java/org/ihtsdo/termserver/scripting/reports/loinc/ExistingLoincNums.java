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
				"LoincNum, LoincPartNum, LongCommonName, Concept, PT, Correlation, Expression, , , ,"
		};
		String[] tabNames = new String[] {
				"LoincNums to Model",
				"Live Modeling"
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
			int hit = 0;
			int miss = 0;
			boolean isFirstLine = true;
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
						
						if (partAttribute != null) {
							hit++;
							report(SECONDARY_REPORT,
									loincNum,
									partNum,
									partAttribute);
						} else {
							miss++;
							report(SECONDARY_REPORT,
									loincNum,
									partNum,
									"Not Part Mapped - " + partTypeName + " | " + partName);
						}
					} else isFirstLine = false;
				}
			}
			info("Parts mapped = " + hit + "/" + (hit + miss));
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
							report(SECONDARY_REPORT, "", partNum, "Mapped to inactive type: " + attributeType + replacementMsg);
							if (replacementType != null) {
								attributeType = replacementType;
							}
						}
						if (!attributeValue.isActive()) {
							Concept replacementValue = getReplacementSafely(SECONDARY_REPORT, partNum, attributeValue, false);
							String replacementMsg = replacementValue == null ? "  no replacement available." : " replaced with " + replacementValue;
							report(SECONDARY_REPORT, "", partNum, "Mapped to inactive value: " + attributeValue + replacementMsg);
							if (replacementValue != null) {
								attributeValue = replacementValue;
							}
						}
						
						loincPartMap.put(partNum, new RelationshipTemplate(attributeType, attributeValue));
					} else isFirstLine = false;
				}
			}
			info("Populated map of " + loincPartMap.size() + " LOINC parts");
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

}
