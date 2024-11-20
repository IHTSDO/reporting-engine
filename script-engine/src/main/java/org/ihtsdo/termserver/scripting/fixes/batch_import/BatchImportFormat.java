package org.ihtsdo.termserver.scripting.fixes.batch_import;

import java.util.*;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;

public class BatchImportFormat implements ScriptConstants {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchImportFormat.class);

	public enum FORMAT { SIRS, ICD11, LOINC, PHAST }

	public enum FIELD { SCTID, PARENT_1, PARENT_2, FSN, CAPS, FSN_ROOT, CAPSFSN, PREF_TERM, NOTES, SEMANTIC_TAG, EXPRESSION, ORIG_REF}

	public static final int FIELD_NOT_FOUND = -1;
	public static final String RANGE_SEPARATOR = "-";
	public static final int FIRST_NOTE = 0;
	public static final int LAST_NOTE = 1;
	public static final String NEW_LINE = "\n";
	public static final String SYNONYM = "Synonym";
	public static final String NOTE = "Note";
	private static final String NEW_SCTID = "NEW_SCTID";  //Indicates we'll pass blank to TS
	private static final String NULL_STR = "NULL";
	public static final String TERM1 = "TERM1";
	public static final String TERM2 = "TERM2";
	
	private final FORMAT format;
	private final Map<FIELD, Integer> fieldMap;
	private int[] documentationFields = new int[0];
	private int[] notesFields = new int[0];
	private boolean definesByExpression = false;
	private boolean constructsFSN = false;
	private boolean multipleTerms = false;

	//There are variable numbers of Synonym and Notes fields, so they're optional and we'll work them out at runtime
	protected static final String[] SIRS_HEADERS = {"Request Id","Topic","Local Code","Local Term","Fully Specified Name","Semantic Tag",
			"Preferred Term","Terminology(1)","Parent Concept Id(1)","UMLS CUI","Definition","Proposed Use","Justification"};
	protected static final String[] ICD11_HEADERS = {"icd11","sctid","fsn","TERM1","US1","GB1","TERM2","US2","GB2","TERM3","US3","GB3","TERM4","US4","GB4","expression"};  //Also note and synonym, but we'll detect those dynamically as there can be more than 1.
	protected static final String[] LOINC_HEADERS = {"SCTID","Parent_1","Parent_2","FSN","CAPSFSN","TERM1","US1","GB1","CAPS1","TERM2","US2","GB2","CAPS2","TERM3","US3","GB3","CAPS3","TERM4","US4","GB4","CAPS4","TERM5","US5","GB5","CAPS5","Associated LOINC Part(s)","Reference link(s)",NOTES};

	public static final String ADDITIONAL_RESULTS_HEADER = "OrigRow,Loaded,Import Result,SCTID Created";
	
	protected static final Map<FORMAT, String[]> HEADERS_MAP = new HashMap<>();
	static {
		HEADERS_MAP.put(FORMAT.SIRS, SIRS_HEADERS);
		HEADERS_MAP.put(FORMAT.ICD11, ICD11_HEADERS);
		HEADERS_MAP.put(FORMAT.LOINC, LOINC_HEADERS);
		HEADERS_MAP.put(FORMAT.PHAST, PHAST_HEADERS);
	}
	protected static final Map<FIELD, String>SIRS_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		SIRS_MAP.put(FIELD.ORIG_REF, "0");
		SIRS_MAP.put(FIELD.SCTID, "2");
		SIRS_MAP.put(FIELD.PARENT_1, "8");
		SIRS_MAP.put(FIELD.FSN_ROOT, "4");
		SIRS_MAP.put(FIELD.SEMANTIC_TAG, "5");
	}
	
	protected static final Map<FIELD, String>ICD11_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		ICD11_MAP.put(FIELD.ORIG_REF, "0");
		ICD11_MAP.put(FIELD.SCTID, "1");
		ICD11_MAP.put(FIELD.FSN, "2");
		ICD11_MAP.put(FIELD.EXPRESSION, "15");
	}
	
	protected static final Map<FIELD, String>LOINC_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		LOINC_MAP.put(FIELD.SCTID, "0");
		LOINC_MAP.put(FIELD.PARENT_1, "1");
		LOINC_MAP.put(FIELD.PARENT_2, "2");
		LOINC_MAP.put(FIELD.FSN, "3");
		LOINC_MAP.put(FIELD.CAPSFSN, "4");
	}

	protected static final Map<FIELD, Integer> ICD11_MAP = new EnumMap<>(FIELD.class);
	static {
		//Note that these are 0-based indexes
		ICD11_MAP.put(FIELD.ORIG_REF, 0);
		ICD11_MAP.put(FIELD.SCTID, 1);
		ICD11_MAP.put(FIELD.FSN, 2);
		ICD11_MAP.put(FIELD.EXPRESSION, 15);
	}

	protected static final Map<FIELD, Integer> LOINC_MAP = new EnumMap<>(FIELD.class);
	static {
		//Note that these are 0-based indexes
		LOINC_MAP.put(FIELD.SCTID, 0);
		LOINC_MAP.put(FIELD.PARENT_1, 1);
		LOINC_MAP.put(FIELD.PARENT_2, 2);
		LOINC_MAP.put(FIELD.FSN, 3);
		LOINC_MAP.put(FIELD.CAPSFSN, 4);
	}

	protected static final Map<FIELD, Integer> PHAST_MAP = new EnumMap<>(FIELD.class);
	static {
		//Note that these are 0-based indexes
		PHAST_MAP.put(FIELD.SCTID, 0);
		PHAST_MAP.put(FIELD.FSN, 1);
		PHAST_MAP.put(FIELD.EXPRESSION, 5);
	}
	
	protected static final int[] LOINC_Documentation = new int[] {25,26,27};

	public boolean isFormat(FORMAT format) {
		return this.format == format;
	}

	private static BatchImportFormat create(FORMAT format) throws TermServerScriptException {
		//Booleans are:  defines by expression, constructs FSN, multipleTerms
		if (format == FORMAT.SIRS) {
			return new BatchImportFormat(FORMAT.SIRS, SIRS_MAP, null, false, true, false);
		} else if (format == FORMAT.ICD11) {
			return new BatchImportFormat(FORMAT.ICD11, ICD11_MAP, null, true, false, true);
		} else if (format == FORMAT.LOINC) {
			return new BatchImportFormat(FORMAT.LOINC, LOINC_MAP, LOINC_DOCUMENTATION, false, false, true);
		} else if (format == FORMAT.PHAST) {
			return new BatchImportFormat(FORMAT.PHAST, PHAST_MAP, null, true, false, true);
		} else {
			throw new TermServerScriptException("Unsupported format: " + format);
		}
	}
	
	private BatchImportFormat(FORMAT format, Map<FIELD, Integer> fieldMap, int[] documentationFields, boolean definesByExpression, boolean constructsFSN, boolean multipleTerms) {
		this.format = format;
		this.fieldMap = fieldMap;
		this.definesByExpression = definesByExpression;
		this.constructsFSN = constructsFSN;
		this.multipleTerms  = multipleTerms;
		if (documentationFields != null) {
			this.documentationFields = documentationFields;
		}
	}
	
	public int getIndex(FIELD field) {
		if (fieldMap.containsKey(field)) {
			return fieldMap.get(field);
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
		
		newConcept = createConceptByParentsOrExpression(row, sctid, moduleId, requiresNewSCTID);
		
		populateDescriptions(newConcept, row);
		
		return newConcept;
	}
	

	private BatchImportConcept createConceptByParentsOrExpression(CSVRecord row, String sctid, String moduleId, boolean requiresNewSCTID) throws TermServerScriptException {
		BatchImportConcept newConcept;
		if (definesByExpression) {
			newConcept = new BatchImportConcept(sctid, row, requiresNewSCTID, moduleId);
			populateExpression(newConcept, row.get(getIndex(FIELD.EXPRESSION)).trim(), moduleId);
		} else {
			ArrayList<String> parents = new ArrayList<>();
			parents.add(row.get(getIndex(FIELD.PARENT_1)));
			int parent2Idx = getIndex(FIELD.PARENT_2);
			if (parent2Idx != FIELD_NOT_FOUND && !row.get(parent2Idx).isEmpty()) {
				parents.add(row.get(parent2Idx));
			}
			newConcept = new BatchImportConcept(sctid, parents, row, requiresNewSCTID);
		}
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
	
	private void populateDescriptions(BatchImportConcept newConcept, CSVRecord row) throws TermServerScriptException {
		if (multipleTerms) {
			addMultipleDescriptions(newConcept, row);
		}
		
		List<Description> descriptions = new ArrayList<>();

		
		String prefTerm = calculateFSNAndPref(newConcept, descriptions);
		
		if (hasMultipleTerms()) {
			descriptions.addAll(newConcept.getDescriptions());
		} else {
			Description pref = Description.withDefaults(prefTerm, DescriptionType.SYNONYM, Acceptability.PREFERRED);
			descriptions.add(pref);
		}
		
		//Check the case significance of all descriptions
		for (Description d : descriptions) {
			//Trim any leading spaces and double spaces
			d.setTerm(d.getTerm().trim().replace("  ", " "));
			if (StringUtils.isCaseSensitive(d.getTerm())) {
				d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
			}
		}
		newConcept.setDescriptions(descriptions);
		
	}

	private String calculateFSNAndPref(BatchImportConcept newConcept, List<Description> descriptions) throws TermServerScriptException {
		String prefTerm;
		String fsnTerm;
		if (constructsFSN()) {
			prefTerm = newConcept.get(getIndex(BatchImportFormat.FIELD.FSN_ROOT));
			fsnTerm = prefTerm + " (" + newConcept.get(getIndex(BatchImportFormat.FIELD.SEMANTIC_TAG)) +")";
		} else {
			fsnTerm = newConcept.get(getIndex(BatchImportFormat.FIELD.FSN));
			prefTerm = SnomedUtilsBase.deconstructFSN(fsnTerm)[0];
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
		return prefTerm;
	}

	private void addMultipleDescriptions(BatchImportConcept newConcept, CSVRecord row) throws TermServerScriptException {
		String[] headers = getHeaders();
		for (int i = 0; i < headers.length; i++) {
			if (headers[i].toLowerCase().startsWith("term")) {
				String termStr = row.get(i);
				if (!termStr.isEmpty() && !termStr.equalsIgnoreCase(NULL_STR)) {
					Description desc = createDescriptionFromColumns(termStr, row, i);
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

	private Description createDescriptionFromColumns(String termStr, CSVRecord row, int i) throws TermServerScriptException {
		char usAccept = row.get(i+1).isEmpty()? '\0' : row.get(i+1).charAt(0);
		char gbAccept = row.get(i+2).isEmpty()? '\0' : row.get(i+2).charAt(0);
		Map<String, Acceptability> acceptabilityMap = new HashMap<>();
		acceptabilityMap.put(GB_ENG_LANG_REFSET, SnomedUtils.translateAcceptabilityFromChar(gbAccept));
		acceptabilityMap.put(US_ENG_LANG_REFSET, SnomedUtils.translateAcceptabilityFromChar(usAccept));
		return Description.withDefaults(termStr, DescriptionType.SYNONYM, acceptabilityMap);
	}

	void convertExpressionToRelationships(Concept source,
			BatchImportExpression expression, String moduleId) throws TermServerScriptException {
		
		if (expression.getFocusConcepts() == null || expression.getFocusConcepts().isEmpty()) {
			throw new TermServerScriptException("Unable to determine a parent for concept from expression");
		} 
		GraphLoader gl = GraphLoader.getGraphLoader();
		Set<Relationship> relationships = new HashSet<>();
		addFocusConcepts(source, expression, relationships, gl);
		
		for (RelationshipGroup group : expression.getAttributeGroups()) {
			relationships.addAll(group.getRelationships());
		}

		addRelationshipsToConcept(source, relationships, moduleId, gl);
	}

	private void addRelationshipsToConcept(Concept source, Set<Relationship> relationships, String moduleId, GraphLoader gl) throws TermServerScriptException {
		for (Relationship r : relationships) {
			r.setModuleId(moduleId);
			r.setActive(true);
			//Let's get the actual type / target so we can see the FSNs
			Concept type = gl.getConcept(r.getType().getConceptId(), false, false);
			if (type != null) {
				r.setType(type);
			}

			//Are we working with a Concept or Concrete Value?
			String targetStr = r.getTarget().getConceptId();
			if (targetStr.startsWith("#")) {
				//Is this a type that normally takes an integer?
				ConcreteValue cv = new ConcreteValue(targetStr);
				r.setConcreteValue(cv);
			} else {
				Concept target = gl.getConcept(r.getTarget().getConceptId(), false, false);
				if (target != null) {
					r.setTarget(target);
				}
			}
		}
		source.setRelationships(relationships);
	}

	private void addFocusConcepts(Concept source, BatchImportExpression expression, Set<Relationship> relationships, GraphLoader gl) throws TermServerScriptException {
		for (String parentId : expression.getFocusConcepts()) {
			//Do not create parent, validate that it exists
			Concept parent = gl.getConcept(parentId, false, true);

			//Parents must be active concepts of course!
			if (!parent.isActiveSafely()) {
				throw new TermServerScriptException("Specified parent " + parent + " is inactive");
			}
			source.addParent(CharacteristicType.STATED_RELATIONSHIP, parent);
			Relationship isA = new Relationship(source, IS_A, parent, UNGROUPED);
			relationships.add(isA);
		}
	}

	public List<String> getAllNotes(BatchImportConcept thisConcept) {
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
	
	public List<String> getAllSynonyms(BatchImportConcept thisConcept) {
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
				//The first field might have some non-ASCII encoding in it, so we'll trim it
				String colStr = header.get(colIdx).replaceAll("[^a-zA-Z0-9]", "");
				if (colIdx < checkHeaders.length && !colStr.equalsIgnoreCase(checkHeaders[colIdx])) {
					LOGGER.info("File is not {} format because header {}:'{}' is not {}.", checkFormat, colIdx, colStr, checkHeaders[colIdx]);
					mismatchDetected = true;
					continue nextFormat;
				} else {
					if (colStr.equalsIgnoreCase(NOTE)) {
						notesIndexList.add(colIdx);
					}
					
					if (colStr.equalsIgnoreCase(SYNONYM)) {
						synonymIndexList.add(colIdx);
					}
				}
			}

			if (!mismatchDetected) {
				LOGGER.info("File Batch Import file format determined to be {}.", checkFormat);
				thisFormat = create(checkFormat);
				thisFormat.notesFields = Ints.toArray(notesIndexList);
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
			case PHAST : return PHAST_HEADERS;
			default : throw new TermServerScriptException("Unrecognised format: " + format);
		}
	}
	
	public int[] getDocumentationFields() {
		return documentationFields;
	}

	public boolean hasMultipleTerms() {
		return multipleTerms;
	}

	
}
