package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportConcept;

import com.google.common.primitives.Ints;

public class BatchImportFormat {

	public static enum FORMAT { SIRS };
	public static enum FIELD { SCTID, PARENT, FSN_ROOT, NOTES, SEMANTIC_TAG};
	public static int FIELD_NOT_FOUND = -1;
	public static String RANGE_SEPARATOR = "-";
	public static int FIRST_NOTE = 0;
	public static int LAST_NOTE = 1;
	public static final String NEW_LINE = "\n";
	public static final String SYNONYM = "Synonym";
	public static final String NOTE = "Note";
	
	private FORMAT format;
	private Map<FIELD, String> fieldMap;
	private int[] synonymFields;
	private int[] notesFields;
	
	//There are variable numbers of Synonym and Notes fields, so they're optional and we'll work them out at runtime
	public static String[] SIRS_HEADERS = {"Request Id","Topic","Local Code","Local Term","Fully Specified Name","Semantic Tag",
			"Preferred Term","Terminology(1)","Parent Concept Id(1)","UMLS CUI","Definition","Proposed Use","Justification"};

	public static String ADDITIONAL_RESULTS_HEADER = "OrigRow,Loaded,Import Result";
	
	public static Map<FIELD, String>SIRS_MAP = new HashMap<FIELD, String>();
	static {
		//Note that these are 0-based indexes
		SIRS_MAP.put(FIELD.SCTID, "2");
		SIRS_MAP.put(FIELD.PARENT, "8");
		SIRS_MAP.put(FIELD.FSN_ROOT, "4");
		SIRS_MAP.put(FIELD.SEMANTIC_TAG, "5");
	}
	
	private static BatchImportFormat create(FORMAT format) throws BusinessServiceException {
		
		if (format == FORMAT.SIRS) {
			return new BatchImportFormat(FORMAT.SIRS, SIRS_MAP);
		} else {
			throw new BusinessServiceException("Unsupported format: " + format);
		}
	}
	
	private BatchImportFormat (FORMAT format, Map<FIELD, String> fieldMap) {
		this.format = format;
		this.fieldMap = fieldMap;
	}
	
/*	public static FORMAT determineFormat(Map<String, Integer> headers) throws BusinessServiceException {
		
		//Is it SIRS?  Throw exception if not because it's the only format we support
		for (Map.Entry<String, Integer> thisHeaderEntry : headers.entrySet()) {
			String header = thisHeaderEntry.getKey();
			int index = thisHeaderEntry.getValue().intValue();
			if (!header.equals(SIRS_HEADERS[index])) {
				throw new BusinessServiceException("File is unrecognised format because header " + index + ":" + header + " is not " + SIRS_HEADERS[index] + " as expected." );
			}
		}
		return FORMAT.SIRS;
	}*/
	
	public int getIndex (FIELD field) throws BusinessServiceException {
		if (fieldMap.containsKey(field)) {
			return Integer.parseInt(fieldMap.get(field));
		}
		return FIELD_NOT_FOUND;
	}
	
	public BatchImportConcept createConcept (CSVRecord row) throws BusinessServiceException {
		String sctid = row.get(getIndex(FIELD.SCTID));
		//We need an sctid in order to keep track of the row, so form from row number if null
		if (sctid == null || sctid.trim().length() == 0) {
			sctid = "row_" + row.getRecordNumber();
		}
		String parent = row.get(getIndex(FIELD.PARENT));
		return new BatchImportConcept (sctid, parent, row);
	}

	public List<String> getAllNotes(BatchImportConcept thisConcept) throws BusinessServiceException {
		List<String> notes = new ArrayList<String>();
		for (int i=0 ; i < notesFields.length; i++ ) {
			String thisNote = thisConcept.getRow().get(notesFields[i]);
			if (thisNote != null && thisNote.trim().length() > 0) {
				notes.add(thisNote);
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
		
		//Is it SIRS?  Throw exception if not because it's the only format we support
		List<Integer> notesIndexList = new ArrayList<Integer>();
		List<Integer> synonymIndexList = new ArrayList<Integer>();
		for (int colIdx=0; colIdx < header.size() ;colIdx++) {
			if (colIdx < SIRS_HEADERS.length && !header.get(colIdx).equals(SIRS_HEADERS[colIdx])) {
				throw new BusinessServiceException("File is unrecognised format because header " + colIdx + ":" + header.get(colIdx) + " is not " + SIRS_HEADERS[colIdx] + " as expected." );
			}
			
			if (header.get(colIdx).equalsIgnoreCase(NOTE)) {
				notesIndexList.add(colIdx);
			}
			
			if (header.get(colIdx).equalsIgnoreCase(SYNONYM)) {
				synonymIndexList.add(colIdx);
			}
		}
		BatchImportFormat thisFormat = create(FORMAT.SIRS);
		thisFormat.notesFields = Ints.toArray(notesIndexList);
		thisFormat.synonymFields = Ints.toArray(synonymIndexList);
		return thisFormat;
	}

	public String[] getHeaders() throws BusinessServiceException {
		switch (format) {
			case SIRS : return SIRS_HEADERS;
		}
		throw new BusinessServiceException("Unrecognised format: " + format);
	}
}
