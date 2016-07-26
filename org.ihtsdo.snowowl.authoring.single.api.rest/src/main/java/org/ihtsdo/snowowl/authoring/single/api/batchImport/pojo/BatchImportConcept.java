package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;

public class BatchImportConcept {
	
	CSVRecord row;
	String sctid;
	List<String> parents = new ArrayList<String>();
	String expressionStr;
	String fsn;
	BatchImportExpression expression;
	BatchImportDocumentation documentation = new BatchImportDocumentation();
	ArrayList <BatchImportTerm> terms = new ArrayList<BatchImportTerm>();

	public BatchImportDocumentation getDocumentation() {
		return documentation;
	}

	boolean requiresNewSCTID = false;
	
	List<BatchImportConcept> children = new ArrayList<BatchImportConcept>();
	
	public BatchImportConcept(String sctid, List<String> parents, CSVRecord row, boolean requiresNewSCTID) {
		this.sctid = sctid;
		this.parents = parents;
		this.row = row;
		this.requiresNewSCTID = requiresNewSCTID;
	}
	
	public BatchImportConcept(String sctid, CSVRecord row, String expressionStr, boolean requiresNewSCTID) {
		this.sctid = sctid;
		this.expressionStr = expressionStr;
		this.row = row;
		this.requiresNewSCTID = requiresNewSCTID;
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
	
	public boolean requiresNewSCTID() {
		return this.requiresNewSCTID;
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

	public List<String> getParents() {
		return parents;
	}
	
	public String getParent(int ref) {
		return parents.get(ref);
	}
	
	public void addParent(String parentSCTID) {
		parents.add(parentSCTID);
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
	
	public BatchImportExpression getExpression() {
		return expression;
	}

	public void setExpression(BatchImportExpression expression) {
		this.expression = expression;
	}

	public String getExpressionStr() {
		return this.expressionStr;
	}

	public String getFsn() {
		return fsn;
	}

	public void setFsn(String fsn) {
		this.fsn = fsn;
	}
	
	public void addTerm (BatchImportTerm t) {
		terms.add(t);
	}

	public ArrayList<BatchImportTerm> getTerms() {
		return terms;
	}
}
