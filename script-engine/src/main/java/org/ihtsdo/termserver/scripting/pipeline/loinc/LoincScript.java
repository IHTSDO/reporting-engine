package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;
import org.snomed.otf.script.dao.ReportSheetManager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class LoincScript extends ContentPipelineManager implements LoincScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ContentPipelineManager.class);

	protected static Map<String, LoincTerm> loincNumToLoincTermMap = new HashMap<>();
	protected static AttributePartMapManager attributePartMapManager;
	protected Map<String, LoincPart> loincParts = new HashMap<>();
	protected Map<String, String> partMapNotes = new HashMap<>();
	
	protected Map<LoincPart, Set<LoincTerm>> missingPartMappings = new HashMap<>();
	
	//Map of LoincNums to ldtColumnNames to details
	protected static Map<String, Map<String, LoincDetail>> loincDetailMap = new HashMap<>();

	public static final String LOINC_TIME_PART = "LP6969-2";
	
	protected String[] getTabNames() {
		throw new IllegalStateException("Please override getTabNames() in your script");
	}


	public void postInit(String[] tabNames, String[] columnHeadings, boolean csvOutput) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1yF2g_YsNBepOukAu2vO0PICqJMAyURwh";  //LOINC Folder
		tabForFinalWords = SECONDARY_REPORT;
		
		//Just temporarily, we need to create some concepts that aren't visible yet
		gl.registerConcept("10021010000100 |Platelet poor plasma or whole blood specimen (specimen)|"); 
		gl.registerConcept("10051010000107 |Plasma specimen or whole blood specimen (specimen)|");
		gl.registerConcept("10031010000102 |Bromocresol purple dye binding technique (qualifier value)|");
		gl.registerConcept("10041010000105 |Oximetry technique (qualifier value)|");
		gl.registerConcept("10061010000109 |Screening technique (qualifier value)|");
		
		super.postInit(tabNames, columnHeadings, csvOutput);
	}

	@Override
	protected void importExternalContent() throws TermServerScriptException {
		loadLoincDetail();
		loadLoincParts();
	}
	
	protected void loadLoincDetail() {
		LOGGER.info ("Loading Loinc Detail: " + getInputFile(FILE_IDX_LOINC_DETAIL));
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_DETAIL)));
			String line = in.readLine();
			int count = 0;
			while (line != null) {
				if (count > 0) {
					LoincDetail loincDetail = LoincDetail.parse(line.split(TAB));
					//Have we seen anything details for this LoincNum?
					Map<String, LoincDetail> partDetailMap = loincDetailMap.get(loincDetail.getLoincNum());
					if (partDetailMap == null) {
						partDetailMap = new HashMap<>();
						loincDetailMap.put(loincDetail.getLoincNum(), partDetailMap);
					}
					//Now have we seen anything for this loincPart before?
					if (partDetailMap.containsKey(loincDetail.getLDTColumnName())) {
						throw new TermServerScriptException("Duplicate LOINC detail encountered in " + FILE_IDX_LOINC_DETAIL + ": " + loincDetail);
					}
					partDetailMap.put(loincDetail.getLDTColumnName(), loincDetail);
				}
				count++;
				line = in.readLine();
			}
			LOGGER.info("Loaded " + count + " details for " + loincDetailMap.size() + " loincNums");
		} catch (Exception e) {
			throw new RuntimeException("Failed to load " + getInputFile(FILE_IDX_LOINC_DETAIL), e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {}
			}
		}
	}
	
	protected void loadLoincParts() throws TermServerScriptException {
		LOGGER.info ("Loading Loinc Parts: " + getInputFile(FILE_IDX_LOINC_PARTS));
		try {
			Reader in = new InputStreamReader(new FileInputStream(getInputFile(FILE_IDX_LOINC_PARTS)));
			//withSkipHeaderRecord() is apparently ignored when using iterator
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
			LOGGER.info("Loaded " + loincParts.size() + " loinc parts.");
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load " + getInputFile(FILE_IDX_LOINC_PARTS), e);
		}
	}
	
/*	protected void determineExistingConcepts(int tabIdx) throws TermServerScriptException {
		int total  = 0;
		int existingConceptCount = 0;
		try {
			LOGGER.info ("Loading " + getInputFile(FILE_IDX_LOINC_100));
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_LOINC_100)))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						String[] items = line.split("\t");
						LoincTerm loincTerm = LoincTerm.parse(items);
						loincNumToLoincTermMap.put(loincTerm.getLoincNum(), loincTerm);
						boolean exists = checkForExistingModelling(loincTerm, tabIdx);
						if (exists) existingConceptCount++;
					} else isFirstLine = false;
				}
			}
			report(tabIdx,"");
			report(tabIdx,"Summary:");
			report(tabIdx,"Already exists", existingConceptCount);
			report(tabIdx,"Total", total);
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}*/
	
/*	private boolean checkForExistingModelling(LoincTerm loincTerm, int tabIdx) throws TermServerScriptException {
		//Do we have this loincNum
		Concept loincConcept = loincNumToSnomedConceptMap.get(loincTerm.getLoincNum());
		if (loincConcept != null) {
			report(tabIdx,
					loincTerm.getLoincNum(),
					loincTerm.getLongCommonName(),
					loincConcept, 
					LoincUtils.getCorrelation(loincConcept),
					loincConcept.toExpression(CharacteristicType.STATED_RELATIONSHIP),
					loincTerm.getCommonColumns());
			return true;
		} else {
			report(tabIdx,
					loincTerm.getLoincNum(),
					loincTerm.getLongCommonName(),
					"Not Modelled",
					"",
					"",
					loincTerm.getCommonColumns());
			return false;
		}
	}*/

	protected void loadFullLoincFile(int tabIdx) {
		loadFullLoincFile(tabIdx, getInputFile(FILE_IDX_LOINC_FULL));
	}
	protected void loadFullLoincFile(int tabIdx, File fullLoincFile) {
		additionalThreadCount++;
		LOGGER.info ("Loading Full Loinc: " + fullLoincFile);
		loincNumToLoincTermMap = new HashMap<>();
		Set<String> targettedProperties = new HashSet<>(Arrays.asList("PrThr", "MCnc","ACnc", "SCnc","Titr", "Prid"));
		try {
			Reader in = new InputStreamReader(new FileInputStream(fullLoincFile));
			//withSkipHeaderRecord() is apparently ignored when using iterator
			Iterator<CSVRecord> iterator = CSVFormat.EXCEL.parse(in).iterator();
			CSVRecord header = iterator.next();
			int hasTargettedPropertyIn20K = 0;
			int hasTargettedPropertyNotIn20K = 0;
			while (iterator.hasNext()) {
				CSVRecord thisLine = iterator.next();
				LoincTerm loincTerm = LoincTerm.parse(thisLine);
				loincNumToLoincTermMap.put(loincTerm.getLoincNum(), loincTerm);
				//Is this term one of the top 20K?
				String testRank = loincTerm.getCommonTestRank();
				if (StringUtils.isEmpty(testRank) || testRank.equals("0")) {
					if (targettedProperties.contains(loincTerm.getProperty())) {
						hasTargettedPropertyNotIn20K++;
					}
				} else if (tabIdx != NOT_SET){
					if (targettedProperties.contains(loincTerm.getProperty())) {
						hasTargettedPropertyIn20K++;
					}
				}
			}
			if (tabIdx != NOT_SET) {
				report(tabIdx,"");
				report(tabIdx,"Summary:");
				report(tabIdx,"");
				report(tabIdx,"Has Targeted Property in Top 20K", hasTargettedPropertyIn20K);
				report(tabIdx,"Has Targeted Property not in Top 20K", hasTargettedPropertyNotIn20K);
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to load " + fullLoincFile, e);
		} finally {
			additionalThreadCount--;
		}
	}

	protected void addPartMapNote(String partNum, String note) {
		if (partMapNotes.containsKey(partNum)) {
			partMapNotes.put(partNum, partMapNotes.get(partNum) + ", " + note);
		} else {
			partMapNotes.put(partNum, note);
		}
	}

	protected void addPartMapNotes(String partNum , List<String> notes) {
		for (String note : notes) {
			addPartMapNote(partNum, note);
		}
	}

	protected String getPartMapNotes(String partNum) {
		return partMapNotes.containsKey(partNum) ? partMapNotes.get(partNum) : "";
	}
	
	@Override
	protected void reportMissingMappings(int tabIdx) throws TermServerScriptException {
		for (LoincPart loincPart : missingPartMappings.keySet()) {
			Set<LoincTerm> loincTerms = missingPartMappings.get(loincPart);
			//Calculate a total priority based on the sum of the LOINC Term priorities
			int totalPriority = loincTerms.stream()
					.mapToInt(lt -> LoincUtils.getLoincTermPriority(lt))
					.sum();
			String[] highUsageIndicators = getHighUsageIndicators(loincTerms);
			report(tabIdx, 
					loincPart.getPartNumber(),
					loincPart.getPartName(),
					loincPart.getPartTypeName(),
					highUsageIndicators[0],
					highUsageIndicators[1],
					totalPriority,
					loincTerms.size(),
					highUsageIndicators[2],
					highUsageIndicators[3]);
		}
	}

	private String[] getHighUsageIndicators(Set<LoincTerm> loincTerms) {
		//Return an array containing:
		//High Usage (Y/N) if any of these terms are high usage (top 20K)
		//Highest Usage (Y/N) if any of these terms are highest usage (top 2K)
		//Top Priority Usage
		//Highest Rank
		String[] highUsageIndicators = new String[]{"N", "N", "", ""};
		for (LoincTerm loincTerm : loincTerms) {
			if (loincTerm.isHighUsage()) {
				highUsageIndicators[0] = "Y";
			}
			if (loincTerm.isHighestUsage()) {
				highUsageIndicators[1] = "Y";
			}
		}

		highUsageIndicators[2] = loincTerms.stream()
				.sorted()
				.map(lt -> { return "(" + lt.getCommonTestRank() + ") " + lt.getLoincNum() + ": " + lt.getDisplayName(); } )
				.limit(5)
				.collect(Collectors.joining(",\n"));

		highUsageIndicators[3] = loincTerms.stream()
				.sorted()
				.map(LoincTerm::getCommonTestRank)
				.findFirst().orElse("0");
		return highUsageIndicators;
	}

	protected void reportExcludedConcepts(int tabIdx, Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
		Set<String> successfullyModelledLoincNums = successfullyModelled.stream()
				.map(tc -> tc.getExternalIdentifier())
				.collect(Collectors.toSet());

		Map<String, List<LoincTerm>> included = loincNumToLoincTermMap.values().stream()
				.filter(lt -> successfullyModelledLoincNums.contains(lt.getLoincNum()))
				.collect(Collectors.groupingBy(LoincTerm::getProperty));

		//Sort remaining Loinc Terms by Property
		Map<String, List<LoincTerm>> excluded = loincNumToLoincTermMap.values().stream()
				.filter(lt -> !successfullyModelledLoincNums.contains(lt.getLoincNum()))
				.collect(Collectors.groupingBy(LoincTerm::getProperty));

		Map<String, List<LoincTerm>> excludedInTop20K = loincNumToLoincTermMap.values().stream()
				.filter(lt -> !successfullyModelledLoincNums.contains(lt.getLoincNum()))
				.filter(lt -> lt.getCommonTestRank() != null && !lt.getCommonTestRank().equals("0"))
				.collect(Collectors.groupingBy(LoincTerm::getProperty));

		for (Map.Entry<String, List<LoincTerm>> entry : excluded.entrySet()) {
			int includedCount = included.getOrDefault(entry.getKey(), new ArrayList<>()).size();
			int excludedCount = entry.getValue().size();
			int excludedInTop20KCount = excludedInTop20K.getOrDefault(entry.getKey(), new ArrayList<>()).size();
			report(tabIdx, entry.getKey(), includedCount, excludedCount, excludedInTop20KCount);
		}
	}

	public void addMissingMapping(String loincPartNum, String loincNum) {
		LoincPart loincPart = loincParts.get(loincPartNum);
		if (loincPart == null) {
			loincPart = new LoincPart(loincNum, "Unknown Type", "Unknown to Part.csv");
		}
		Set<LoincTerm> loincTerms = missingPartMappings.get(loincPart);
		//Have we seen this one before?
		if (loincTerms == null) {
			loincTerms = new HashSet<>();
			missingPartMappings.put(loincPart, loincTerms);
		}
		loincTerms.add(loincNumToLoincTermMap.get(loincNum));
	}
}
