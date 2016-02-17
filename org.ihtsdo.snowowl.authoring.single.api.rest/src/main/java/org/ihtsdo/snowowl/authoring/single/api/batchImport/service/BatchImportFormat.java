package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportConcept;

public class BatchImportFormat {

	public static enum FORMAT { SIRS };
	public static enum FIELD { SCTID, PARENT, FSN_ROOT, NOTES, SEMANTIC_TAG};
	public static int FIELD_NOT_FOUND = -1;
	public static String RANGE_SEPARATOR = "-";
	
	//private FORMAT format;
	private Map<FIELD, String> fieldMap;
	
	public static String[] SIRS_HEADERS = {"Request Id","Topic","Local Code","Local Term","Fully Specified Name","Semantic Tag",
			"Preferred Term","Terminology(1)","Parent Concept Id(1)","UMLS CUI","Definition","Proposed Use",
			"Justification","Synonym","Synonym","Synonym","Synonym","Synonym","Note","Note","Note","Note","Note","Note","Note","Note","Note"};

	public static Map<FIELD, String>SIRS_MAP = new HashMap<FIELD, String>();
	static {
		SIRS_MAP.put(FIELD.SCTID, "3");
		SIRS_MAP.put(FIELD.PARENT, "9");
		SIRS_MAP.put(FIELD.FSN_ROOT, "5");
	}
	
	public static BatchImportFormat create(FORMAT format) throws BusinessServiceException {
		
		if (format == FORMAT.SIRS) {
			return new BatchImportFormat(SIRS_MAP);
		} else {
			throw new BusinessServiceException("Unsupported format: " + format);
		}
	}
	
	private BatchImportFormat (Map<FIELD, String> fieldMap) {
		this.fieldMap = fieldMap;
	}
	
	public static FORMAT determineFormat(Map<String, Integer> headers) throws BusinessServiceException {
		
		//Is it SIRS?  Throw exception if not because it's the only format we support
		for (Map.Entry<String, Integer> thisHeaderEntry : headers.entrySet()) {
			String header = thisHeaderEntry.getKey();
			int index = thisHeaderEntry.getValue().intValue();
			if (!header.equals(SIRS_HEADERS[index])) {
				throw new BusinessServiceException("File is unrecognised format because header " + index + ":" + header + " is not " + SIRS_HEADERS[index] + " as expected." );
			}
		}
		return FORMAT.SIRS;
	}
	
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
	
	public BatchImportConcept createConcept (CSVRecord row) throws BusinessServiceException {
		String sctid = row.get(getIndex(FIELD.SCTID));
		//We need an sctid in order to keep track of the row, so form from row number if null
		if (sctid == null || sctid.trim().length() == 0) {
			sctid = "row_" + row.getRecordNumber();
		}
		String parent = row.get(getIndex(FIELD.PARENT));
		return new BatchImportConcept (sctid, parent, row);
	}
}
