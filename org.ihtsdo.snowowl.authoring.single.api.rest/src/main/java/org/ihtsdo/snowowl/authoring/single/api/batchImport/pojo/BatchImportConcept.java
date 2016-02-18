package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;

public class BatchImportConcept {
	
	CSVRecord row;
	
	String sctid;
	
	String parent;
	
	List<BatchImportConcept> children = new ArrayList<BatchImportConcept>();
	
	public BatchImportConcept(String sctid, String parent, CSVRecord row) {
		this.sctid = sctid;
		this.parent = parent;
		this.row = row;
	}
	
	private BatchImportConcept() {
		//Only the "root" concept (which is not loaded) has null data
	}
	
	public static BatchImportConcept createRootConcept() {
		return new BatchImportConcept();
	}
	
	public void addChild(BatchImportConcept child) {
		children.add(child);
	}
	
	public void removeChild(BatchImportConcept child) throws BusinessServiceException {
		//We only expect to remove children from the root concept
		if (!this.isRootConcept()) {
			throw new BusinessServiceException("Attempted to remove "  + child + " from non-root Concept");
		}
		
		this.children.remove(child);
	}

	public boolean isRootConcept() {
		return row == null;
	}

	public CSVRecord getRow() {
		return row;
	}
	
	public String get(int index) {
		return row.get(index);
	}

	public String getSctid() {
		return sctid;
	}

	public String getParent() {
		return parent;
	}

	public final List<BatchImportConcept> getChildren() {
		return children;
	}

	public int childrenCount() {
		int childCount = children.size();
		//And add the count of the children's children
		for (BatchImportConcept thisChild : children) {
			childCount += thisChild.childrenCount();
		}
		return childCount;
	}

	public void addDescendants(List<BatchImportConcept> thisBatch) {
		for (BatchImportConcept thisChild : children) {
			thisBatch.add(thisChild);
			thisChild.addDescendants(thisBatch);
		}
	}

}
