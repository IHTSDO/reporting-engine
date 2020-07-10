package org.ihtsdo.termserver.scripting.fixes.batchImport;

import java.util.*;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



//import com.b2international.snowowl.dsl.SCGStandaloneSetup;
//import com.b2international.snowowl.dsl.scg.Expression;
import com.google.common.primitives.Ints;

public class BatchImportFormat implements RF2Constants {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchImportFormat.class);

	public enum FORMAT { SIRS, ICD11, LOINC }

	public enum FIELD { SCTID, PARENT_1, PARENT_2, FSN, CAPS, FSN_ROOT, CAPSFSN, PREF_TERM, NOTES, SEMANTIC_TAG, EXPRESSION, ORIG_REF}

	public static int FIELD_NOT_FOUND = -1;
	public static String RANGE_SEPARATOR = "-";
	public static int FIRST_NOTE = 0;
	public static int LAST_NOTE = 1;
	public static final String NEW_LINE = "\n";
	public static final String SYNONYM = "Synonym";
	public static final String NOTE = "Note";
	public static final String NOTES = "Notes";
	public static final String EXPRESSION = "Expression";
	private static final String NEW_SCTID = "NEW_SCTID";  //Indicates we'll pass blank to TS
	private static final String NULL_STR = "NULL";
	
	private FORMAT format;
	private Map<FIELD, String> fieldMap;
	private int[] documentationFields = new int[0];
	private int[] synonymFields = new int[0];
	private int[] notesFields = new int[0];
	private boolean definesByExpression = false;
	private boolean constructsFSN = false;
	private boolean multipleTerms = false;
	
	//There are variable numbers of Synonym and Notes fields, so they're optional and we'll work them out at runtime
	public static String[] SIRS_HEADERS = {"Request Id","Topic","Local Code","Local Term","Fully Specified Name","Semantic Tag",
			"Preferred Term","Terminology(1)","Parent Concept Id(1)","UMLS CUI","Definition","Proposed Use","Justification"};
	public static String[] ICD11_HEADERS = {"icd11","sctid","fsn","TERM1","US1","GB1","TERM2","US2","GB2","TERM3","US3","GB3","TERM4","US4","GB4","expression"};  //Also note and synonym, but we'll detect those dynamically as there can be more than 1.
	public static String[] LOINC_HEADERS = {"SCTID","Parent_1","Parent_2","FSN","CAPSFSN","TERM1","US1","GB1","CAPS1","TERM2","US2","GB2","CAPS2","TERM3","US3","GB3","CAPS3","TERM4","US4","GB4","CAPS4","TERM5","US5","GB5","CAPS5","Associated LOINC Part(s)","Reference link(s)","Notes"};

	public static String ADDITIONAL_RESULTS_HEADER = "OrigRow,Loaded,Import Result,SCTID Created";
	
	public static Map<FORMAT, String[]> HEADERS_MAP = new HashMap<>();
	static {
		HEADERS_MAP.put(FORMAT.SIRS, SIRS_HEADERS);
		HEADERS_MAP.put(FORMAT.ICD11, ICD11_HEADERS);
		HEADERS_MAP.put(FORMAT.LOINC, LOINC_HEADERS);
	}
	public static Map<FIELD, String>SIRS_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		SIRS_MAP.put(FIELD.ORIG_REF, "0");
		SIRS_MAP.put(FIELD.SCTID, "2");
		SIRS_MAP.put(FIELD.PARENT_1, "8");
		SIRS_MAP.put(FIELD.FSN_ROOT, "4");
		SIRS_MAP.put(FIELD.SEMANTIC_TAG, "5");
	}
	
	public static Map<FIELD, String>ICD11_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		ICD11_MAP.put(FIELD.ORIG_REF, "0");
		ICD11_MAP.put(FIELD.SCTID, "1");
		ICD11_MAP.put(FIELD.FSN, "2");
		ICD11_MAP.put(FIELD.EXPRESSION, "15");
	}
	
	public static Map<FIELD, String>LOINC_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		LOINC_MAP.put(FIELD.SCTID, "0");
		LOINC_MAP.put(FIELD.PARENT_1, "1");
		LOINC_MAP.put(FIELD.PARENT_2, "2");
		LOINC_MAP.put(FIELD.FSN, "3");
		LOINC_MAP.put(FIELD.CAPSFSN, "4");
	}
	
	public static int[] LOINC_Documentation = new int[] {25,26,27};

	private static BatchImportFormat create(FORMAT format) throws TermServerScriptException {
		//Booleans are:  defines by expression, constructs FSN, multipleTerms
		if (format == FORMAT.SIRS) {
			return new BatchImportFormat(FORMAT.SIRS, SIRS_MAP, null, false, true, false);
		} else if (format == FORMAT.ICD11) {
			return new BatchImportFormat(FORMAT.ICD11, ICD11_MAP, null, true, false, true);
		} else if (format == FORMAT.LOINC) {
			
			return new BatchImportFormat(FORMAT.LOINC, LOINC_MAP, LOINC_Documentation, false, false, true);
		} else {
			throw new TermServerScriptException("Unsupported format: " + format);
		}
	}
	
	private BatchImportFormat(FORMAT format, Map<FIELD, String> fieldMap, int[] documentationFields, boolean definesByExpression, boolean constructsFSN, boolean multipleTerms) {
		this.format = format;
		this.fieldMap = fieldMap;
		this.definesByExpression = definesByExpression;
		this.constructsFSN = constructsFSN;
		this.multipleTerms  = multipleTerms;
		if (documentationFields != null) {
			this.documentationFields = documentationFields;
		}
	}
	
	public int getIndex(FIELD field) throws TermServerScriptException {
		if (fieldMap.containsKey(field)) {
			return Integer.parseInt(fieldMap.get(field));
		}
		return FIELD_NOT_FOUND;
	}
	
	public BatchImportConcept createConcept(CSVRecord row, String moduleId) throws TermServerScriptException {
		BatchImportConcept newConcept;
		String sctid = row.get(getIndex(FIELD.SCTID)).trim();
		
		//We need an sctid in order to keep track of the row, so form from row number if null
		if (sctid.isEmpty()) {
			sctid = "row_" + row.getRecordNumber();
		}
		
		boolean requiresNewSCTID = false;
		if (sctid.equals(NEW_SCTID) ) {
			requiresNewSCTID = true;
			sctid += "_" + row.getRecordNumber();
		}
		
		if (definesByExpression) {
			newConcept = new BatchImportConcept (sctid, row, requiresNewSCTID, moduleId);
			populateExpression(newConcept, row.get(getIndex(FIELD.EXPRESSION)).trim(), moduleId);
		} else {
			ArrayList<String> parents = new ArrayList<>();
			parents.add(row.get(getIndex(FIELD.PARENT_1)));
			int parent2Idx = getIndex(FIELD.PARENT_2);
			if (parent2Idx != FIELD_NOT_FOUND && !row.get(parent2Idx).isEmpty()) {
				parents.add(row.get(parent2Idx));
			}
			newConcept = new BatchImportConcept (sctid, parents, row, requiresNewSCTID);
		}
		
		if (multipleTerms) {
			String[] headers = getHeaders();
			for (int i = 0; i < headers.length; i++) {
				if (headers[i].toLowerCase().startsWith("term")) {
					String termStr = row.get(i);
					if (!termStr.isEmpty() && !termStr.toUpperCase().equals(NULL_STR)) {
						char usAccept = row.get(i+1).isEmpty()? null : row.get(i+1).charAt(0);
						char gbAccept =  row.get(i+2).isEmpty()? null : row.get(i+2).charAt(0);
						Map<String, Acceptability> acceptabilityMap = new HashMap<>();
						acceptabilityMap.put(GB_ENG_LANG_REFSET, SnomedUtils.translateAcceptabilityFromChar(gbAccept));
						acceptabilityMap.put(US_ENG_LANG_REFSET, SnomedUtils.translateAcceptabilityFromChar(usAccept));
						Description desc = Description.withDefaults(termStr, DescriptionType.SYNONYM, acceptabilityMap);
						i += 3;
						//Do we have a CAPS indicator here?
						if (headers[i].toLowerCase().startsWith("caps")) {
							desc.setCaseSignificance(SnomedUtils.translateCaseSignificanceFromString(row.get(i)));
						} else {
							i--;  //Move back one because loop will take us on to the next term.
						}
						newConcept.addDescription(desc);
					}
				}
			}
		}
		
		List<Description> descriptions = new ArrayList<>();
		String prefTerm, fsnTerm;
		
		if (constructsFSN()) {
			prefTerm = newConcept.get(getIndex(BatchImportFormat.FIELD.FSN_ROOT));
			fsnTerm = prefTerm + " (" + newConcept.get(getIndex(BatchImportFormat.FIELD.SEMANTIC_TAG)) +")";
		} else {
			fsnTerm = newConcept.get(getIndex(BatchImportFormat.FIELD.FSN));
			prefTerm = SnomedUtils.deconstructFSN(fsnTerm)[0];
			if (!hasMultipleTerms()) {
				prefTerm = newConcept.get(getIndex(BatchImportFormat.FIELD.PREF_TERM));
			}
		}
		
		//Set the FSN
		CaseSignificance fsnCase = CaseSignificance.CASE_INSENSITIVE;  //default
		if (getIndex(BatchImportFormat.FIELD.CAPSFSN) != BatchImportFormat.FIELD_NOT_FOUND) {
			fsnCase = SnomedUtils.translateCaseSignificanceFromString(newConcept.get(getIndex(BatchImportFormat.FIELD.CAPSFSN)));
		}
		
		newConcept.setFsn(fsnTerm);
		Description fsn = Description.withDefaults(fsnTerm, DescriptionType.FSN, Acceptability.PREFERRED);
		fsn.setCaseSignificance(fsnCase);
		descriptions.add(fsn);
		
		if (hasMultipleTerms()) {
			descriptions.addAll(newConcept.getDescriptions());
		} else {
			Description pref = Description.withDefaults(prefTerm, DescriptionType.SYNONYM, Acceptability.PREFERRED);
			descriptions.add(pref);
		}
		
		//Check the case significance of all descriptions
		for (Description d : descriptions) {
			//Trim any leading spaces and double spaces
			d.setTerm(d.getTerm().trim().replaceAll("  ", " "));
			if (StringUtils.isCaseSensitive(d.getTerm())) {
				d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
			}
		}
		newConcept.setDescriptions(descriptions);
		
		return newConcept;
	}
	
	private void populateExpression(BatchImportConcept newConcept, String expressionStr, String moduleId) throws TermServerScriptException {
		BatchImportExpression exp = BatchImportExpression.parse(expressionStr, moduleId);
		newConcept.setExpression(exp);
		convertExpressionToRelationships(newConcept, exp, moduleId);
		newConcept.setDefinitionStatus(newConcept.getExpression().getDefinitionStatus());
		newConcept.setModuleId(moduleId);
		newConcept.recalculateGroups();
	}

	void convertExpressionToRelationships(Concept source,
			BatchImportExpression expression, String moduleId) throws TermServerScriptException {
		
		if (expression.getFocusConcepts() == null || expression.getFocusConcepts().size() < 1) {
			throw new TermServerScriptException("Unable to determine a parent for concept from expression");
		} 
		GraphLoader gl = GraphLoader.getGraphLoader();
		Set<Relationship> relationships = new HashSet<>();
		for (String parentId : expression.getFocusConcepts()) {
			//SnomedUtils.isValid(parentId, PartitionIdentifier.CONCEPT, true);
			//Concept parent = new Concept(parentId);
			//Do not create parent, validate that it exists
			Concept parent = gl.getConcept(parentId, false, true);
			source.addParent(CharacteristicType.STATED_RELATIONSHIP, parent);
			Relationship isA = new Relationship(source, IS_A, parent, UNGROUPED);
			relationships.add(isA);
		}
		
		for (RelationshipGroup group : expression.getAttributeGroups()) {
			relationships.addAll(group.getRelationships());
		}
		
		for (Relationship r : relationships) {
			r.setModuleId(moduleId);
			r.setActive(true);
			//Lets get the actual type / target so we can see the FSNs
			Concept type = gl.getConcept(r.getType().getConceptId(), false, false);
			if (type != null) {
				r.setType(type);
			}
			
			Concept target = gl.getConcept(r.getTarget().getConceptId(), false, false);
			if (target != null) {
				r.setTarget(target);
			}
		}
		source.setRelationships(relationships);
	}

	public List<String> getAllNotes(BatchImportConcept thisConcept) throws TermServerScriptException {
		List<String> notes = new ArrayList<>();
		for (int notesField : notesFields) {
			try {
				String thisNote = thisConcept.getRow().get(notesField);
				if (thisNote != null && thisNote.trim().length() > 0) {
					notes.add(thisNote);
				}
			} catch (Exception e) {
				LOGGER.error("Failed to recover note at field {} for concept {}", notesField, thisConcept.getId(), e);
			}
		}
		return notes;
	}
	
	public List<String> getAllSynonyms(BatchImportConcept thisConcept) throws TermServerScriptException {
		List<String> synList = new ArrayList<>();
		for (int synonymField : synonymFields) {
			String thisSyn = thisConcept.getRow().get(synonymField);
			if (thisSyn != null && thisSyn.trim().length() > 0) {
				synList.add(thisSyn);
			}
		}
		return synList;
	}

	public static BatchImportFormat determineFormat(CSVRecord header) throws TermServerScriptException {
		
		BatchImportFormat thisFormat = null;
		nextFormat:
		for (Map.Entry<FORMAT, String[]> thisFormatHeaders : HEADERS_MAP.entrySet()) {
			FORMAT checkFormat = thisFormatHeaders.getKey();
			String[] checkHeaders = thisFormatHeaders.getValue();
			boolean mismatchDetected = false;
			List<Integer> notesIndexList = new ArrayList<>();
			List<Integer> synonymIndexList = new ArrayList<>();
			for (int colIdx=0; colIdx < header.size() && !mismatchDetected ;colIdx++) {
				if (colIdx < checkHeaders.length && !header.get(colIdx).trim().equalsIgnoreCase(checkHeaders[colIdx])) {
					LOGGER.info("File is not {} format because header {}:'{}' is not {}.", checkFormat, colIdx, header.get(colIdx), checkHeaders[colIdx]);
					mismatchDetected = true;
					continue nextFormat;
				} else {
					if (header.get(colIdx).equalsIgnoreCase(NOTE)) {
						notesIndexList.add(colIdx);
					}
					
					if (header.get(colIdx).equalsIgnoreCase(SYNONYM)) {
						synonymIndexList.add(colIdx);
					}
				}
			}
			if (!mismatchDetected) {
				LOGGER.info("File Batch Import file format determined to be {}.", checkFormat);
				thisFormat = create(checkFormat);
				thisFormat.notesFields = Ints.toArray(notesIndexList);
				thisFormat.synonymFields = Ints.toArray(synonymIndexList);
				break;
			}
		}
		if (thisFormat == null) {
			throw new TermServerScriptException("File format could not be determined");
		}
		return thisFormat;
	
	}

	public boolean definesByExpression() {
		return definesByExpression;
	}
	
	public boolean constructsFSN() {
		return constructsFSN;
	}

	public String[] getHeaders() throws TermServerScriptException {
		switch (format) {
			case SIRS : return SIRS_HEADERS;
			case ICD11 : return ICD11_HEADERS;
			case LOINC : return LOINC_HEADERS;
		}
		throw new TermServerScriptException("Unrecognised format: " + format);
	}
	
	public int[] getDocumentationFields() {
		return documentationFields;
	}

	public boolean hasMultipleTerms() {
		return multipleTerms;
	}

	
}
