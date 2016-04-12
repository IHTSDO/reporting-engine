package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportConcept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import com.b2international.snowowl.dsl.SCGStandaloneSetup;
//import com.b2international.snowowl.dsl.scg.Expression;
import com.google.common.primitives.Ints;

public class BatchImportFormat {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchImportFormat.class);

	public static enum FORMAT { SIRS, ICD11 };
	public static enum FIELD { SCTID, PARENT, FSN, FSN_ROOT, PREF_TERM, NOTES, SEMANTIC_TAG, EXPRESSION, ORIG_REF};
	public static int FIELD_NOT_FOUND = -1;
	public static String RANGE_SEPARATOR = "-";
	public static int FIRST_NOTE = 0;
	public static int LAST_NOTE = 1;
	public static final String NEW_LINE = "\n";
	public static final String SYNONYM = "Synonym";
	public static final String NOTE = "Note";
	public static final String EXPRESSION = "Expression";
	
	private static final String NEW_SCTID = "NEW_SCTID";  //Indicates we'll pass blank to TS
	
	private FORMAT format;
	private Map<FIELD, String> fieldMap;
	private int[] synonymFields;
	private int[] notesFields;
	private boolean definesByExpression = false;
	private boolean constructsFSN = false;
	
	//There are variable numbers of Synonym and Notes fields, so they're optional and we'll work them out at runtime
	public static String[] SIRS_HEADERS = {"Request Id","Topic","Local Code","Local Term","Fully Specified Name","Semantic Tag",
			"Preferred Term","Terminology(1)","Parent Concept Id(1)","UMLS CUI","Definition","Proposed Use","Justification"};
	public static String[] ICD11_HEADERS = {"icd11","sctid","fsn","prefterm","expression"};  //Also note and synonym, but we'll detect those dynamically as there can be more than 1.
	public static String ADDITIONAL_RESULTS_HEADER = "OrigRow,Loaded,Import Result,SCTID Created";
	
	public static Map<FORMAT, String[]> HEADERS_MAP = new HashMap<FORMAT, String[]>();
	static {
		HEADERS_MAP.put(FORMAT.SIRS, SIRS_HEADERS);
		HEADERS_MAP.put(FORMAT.ICD11, ICD11_HEADERS);
	}
	public static Map<FIELD, String>SIRS_MAP = new HashMap<FIELD, String>();
	static {
		//Note that these are 0-based indexes
		SIRS_MAP.put(FIELD.ORIG_REF, "0");
		SIRS_MAP.put(FIELD.SCTID, "2");
		SIRS_MAP.put(FIELD.PARENT, "8");
		SIRS_MAP.put(FIELD.FSN_ROOT, "4");
		SIRS_MAP.put(FIELD.SEMANTIC_TAG, "5");
	}
	
	public static Map<FIELD, String>ICD11_MAP = new HashMap<FIELD, String>();
	static {
		//Note that these are 0-based indexes
		ICD11_MAP.put(FIELD.ORIG_REF, "0");
		ICD11_MAP.put(FIELD.SCTID, "1");
		ICD11_MAP.put(FIELD.FSN, "2");
		ICD11_MAP.put(FIELD.PREF_TERM, "3");		
		ICD11_MAP.put(FIELD.EXPRESSION, "4");		
	}
	
	private static BatchImportFormat create(FORMAT format) throws BusinessServiceException {
		
		if (format == FORMAT.SIRS) {
			return new BatchImportFormat(FORMAT.SIRS, SIRS_MAP, false, true);
		} else if (format == FORMAT.ICD11) {
			return new BatchImportFormat(FORMAT.ICD11, ICD11_MAP, true, false);
		} else {
			throw new BusinessServiceException("Unsupported format: " + format);
		}
	}
	
	private BatchImportFormat (FORMAT format, Map<FIELD, String> fieldMap, boolean definesByExpression, boolean constructsFSN) {
		this.format = format;
		this.fieldMap = fieldMap;
		this.definesByExpression = definesByExpression;
		this.constructsFSN = constructsFSN;
	}
	
	public int getIndex (FIELD field) throws BusinessServiceException {
		if (fieldMap.containsKey(field)) {
			return Integer.parseInt(fieldMap.get(field));
		}
		return FIELD_NOT_FOUND;
	}
	
	public BatchImportConcept createConcept (CSVRecord row) throws BusinessServiceException {
		BatchImportConcept newConcept = null;
		String sctid = row.get(getIndex(FIELD.SCTID)).trim();
		//We need an sctid in order to keep track of the row, so form from row number if null
		if (sctid == null || sctid.trim().length() == 0) {
			sctid = "row_" + row.getRecordNumber();
		}
		
		boolean requiresNewSCTID = false;
		if (sctid.equals(NEW_SCTID) ) {
			requiresNewSCTID = true;
			sctid += "_" + row.getRecordNumber();
		}
		
		if (definesByExpression) {
			String expressionStr = row.get(getIndex(FIELD.EXPRESSION)).trim();
			newConcept = new BatchImportConcept (sctid, row, expressionStr, requiresNewSCTID);
		} else {
			String parent = row.get(getIndex(FIELD.PARENT));
			newConcept = new BatchImportConcept (sctid, parent, row, requiresNewSCTID);			
		}
		return newConcept;
	}

	public List<String> getAllNotes(BatchImportConcept thisConcept) throws BusinessServiceException {
		List<String> notes = new ArrayList<String>();
		for (int i=0 ; i < notesFields.length; i++ ) {
			try {
				String thisNote = thisConcept.getRow().get(notesFields[i]);
				if (thisNote != null && thisNote.trim().length() > 0) {
					notes.add(thisNote);
				}
			} catch (Exception e) {
				LOGGER.error("Failed to recover note at field {} for concept {}", notesFields[i], thisConcept.getSctid(), e);
			}
		}
		return notes;
	}
	
	public List<String> getAllSynonyms(BatchImportConcept thisConcept) throws BusinessServiceException {
		List<String> synList = new ArrayList<String>();
		for (int i=0 ; i < synonymFields.length; i++ ) {
			String thisSyn = thisConcept.getRow().get(synonymFields[i]);
			if (thisSyn != null && thisSyn.trim().length() > 0) {
				synList.add(thisSyn);
			}
		}
		return synList;
	}

	public static BatchImportFormat determineFormat(CSVRecord header) throws BusinessServiceException {
		
		BatchImportFormat thisFormat = null;
		for (Map.Entry<FORMAT, String[]> thisFormatHeaders : HEADERS_MAP.entrySet()) {
			FORMAT checkFormat = thisFormatHeaders.getKey();
			String[] checkHeaders = thisFormatHeaders.getValue();
			boolean mismatchDetected = false;
			List<Integer> notesIndexList = new ArrayList<Integer>();
			List<Integer> synonymIndexList = new ArrayList<Integer>();
			for (int colIdx=0; colIdx < header.size() && !mismatchDetected ;colIdx++) {
				if (colIdx < checkHeaders.length && !header.get(colIdx).equalsIgnoreCase(checkHeaders[colIdx])) {
					LOGGER.info("File is not {} format because header {}:{} is not {}.", checkFormat, colIdx, header.get(colIdx), checkHeaders[colIdx]);
					mismatchDetected = true;
				}
				
				if (header.get(colIdx).equalsIgnoreCase(NOTE)) {
					notesIndexList.add(colIdx);
				}
				
				if (header.get(colIdx).equalsIgnoreCase(SYNONYM)) {
					synonymIndexList.add(colIdx);
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
			throw new BusinessServiceException ("File format could not be determined");
		}
		return thisFormat;
	
	}

	public boolean definesByExpression() {
		return definesByExpression;
	}
	
	public boolean constructsFSN() {
		return constructsFSN;
	}

	public String[] getHeaders() throws BusinessServiceException {
		switch (format) {
			case SIRS : return SIRS_HEADERS;
			case ICD11 : return ICD11_HEADERS;
		}
		throw new BusinessServiceException("Unrecognised format: " + format);
	}
}
