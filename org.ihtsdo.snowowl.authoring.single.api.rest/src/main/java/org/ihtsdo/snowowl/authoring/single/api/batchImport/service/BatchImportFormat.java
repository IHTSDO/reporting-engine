package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportConcept;

public class BatchImportFormat {

	public static enum FORMAT { SIRS };
	public static enum FIELD { SCTID, PARENT, FSN_ROOT, NOTES, SEMANTIC_TAG};
	public static int FIELD_NOT_FOUND = -1;
	public static String RANGE_SEPARATOR = "-";
	public static int FIRST_NOTE = 0;
	public static int LAST_NOTE = 1;
	public static final String NEW_LINE = "\n";
	
	private FORMAT format;
	private Map<FIELD, String> fieldMap;
	
	public static String[] SIRS_HEADERS = {"Request Id","Topic","Local Code","Local Term","Fully Specified Name","Semantic Tag",
			"Preferred Term","Terminology(1)","Parent Concept Id(1)","UMLS CUI","Definition","Proposed Use",
			"Justification","Synonym","Synonym","Synonym","Synonym","Synonym","Note","Note","Note","Note","Note","Note","Note","Note","Note"};

	public static String ADDITIONAL_RESULTS_HEADER = "OrigRow,Loaded,Import Result";
	
	public static Map<FIELD, String>SIRS_MAP = new HashMap<FIELD, String>();
	static {
		//Note that these are 0-based indexes
		SIRS_MAP.put(FIELD.SCTID, "2");
		SIRS_MAP.put(FIELD.PARENT, "8");
		SIRS_MAP.put(FIELD.FSN_ROOT, "4");
		SIRS_MAP.put(FIELD.SEMANTIC_TAG, "5");
		SIRS_MAP.put(FIELD.NOTES, "18-26");
	}
	
	public static BatchImportFormat create(FORMAT format) throws BusinessServiceException {
		
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
		if (fieldMap.containsKey(field) && !isRange(field)) {
			return Integer.parseInt(fieldMap.get(field));
		}
		return FIELD_NOT_FOUND;
	}
	
	public boolean isRange(FIELD field) throws BusinessServiceException {
		if (fieldMap.containsKey(field) && fieldMap.get(field).contains(RANGE_SEPARATOR)) {
			return true;
		}
		return false;
	}
	
	public int[] getRange(FIELD field) throws BusinessServiceException {
		if (!isRange(field)) {
			throw new BusinessServiceException(field + " expected to contain a range but instead is: " + fieldMap.get(field));
		}
		String[] startEnd = fieldMap.get(field).split(RANGE_SEPARATOR);
		return new int[] { Integer.parseInt(startEnd[FIRST_NOTE]), Integer.parseInt(startEnd[LAST_NOTE])};
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
		int[] noteIdexes = getRange(FIELD.NOTES);
		int maxNotes = thisConcept.getRow().size();
		for (int i=noteIdexes[FIRST_NOTE] ; i <= noteIdexes[LAST_NOTE] && i < maxNotes; i++ ) {
			String thisNote = thisConcept.getRow().get(i);
			if (thisNote != null && thisNote.trim().length() > 0) {
				notes.add(thisNote);
			}
		}
		return notes;
	}

	public static FORMAT determineFormat(CSVRecord header) throws BusinessServiceException {
		//Is it SIRS?  Throw exception if not because it's the only format we support
		for (int colIdx=0; colIdx < header.size() && colIdx < SIRS_HEADERS.length;colIdx++) {
			if (!header.get(colIdx).equals(SIRS_HEADERS[colIdx])) {
				throw new BusinessServiceException("File is unrecognised format because header " + colIdx + ":" + header.get(colIdx) + " is not " + SIRS_HEADERS[colIdx] + " as expected." );
			}
		}
		return FORMAT.SIRS;
	}

	public String[] getHeaders() throws BusinessServiceException {
		switch (format) {
			case SIRS : return SIRS_HEADERS;
		}
		throw new BusinessServiceException("Unrecognised format: " + format);
	}
}
