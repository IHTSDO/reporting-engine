package org.ihtsdo.termserver.scripting.fixes.batch_import;

import java.util.*;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;

public class BatchImportConcept extends Concept {

	private CSVRecord row;
	private BatchImportExpression expression;
	private boolean requiresNewSCTID = false;
	
	public BatchImportConcept(String sctid, List<String> parents, CSVRecord row, boolean requiresNewSCTID) {
		super(sctid);
		for (String parent : parents) {
			this.addParent(CharacteristicType.STATED_RELATIONSHIP, new Concept(parent));
		}
		this.row = row;
		this.requiresNewSCTID = requiresNewSCTID;
	}
	
	public BatchImportConcept(String sctid, CSVRecord row, boolean requiresNewSCTID, String moduleId) throws TermServerScriptException {
		super(sctid);
		this.row = row;
		this.requiresNewSCTID = requiresNewSCTID;
		this.setModuleId(moduleId);
		this.active = true;
	}
	
	private BatchImportConcept() {
		//Only the "root" concept (which is not loaded) has null data
		super(NULL_CONCEPT);
	}
	
	public static BatchImportConcept createRootConcept() {
		return new BatchImportConcept();
	}
	
	public boolean requiresNewSCTID() {
		return this.requiresNewSCTID;
	}

	public CSVRecord getRow() {
		return row;
	}
	
	public String get(int index) {
		return row.get(index);
	}

	public void addDescendants(Task task) {
		for (Concept thisChild : getChildren(CharacteristicType.STATED_RELATIONSHIP)) {
			BatchImportConcept child = (BatchImportConcept) thisChild;
			task.add(child);
			child.addDescendants(task);
		}
	}
	
	public BatchImportExpression getExpression() {
		return expression;
	}

	public void setExpression(BatchImportExpression expression) {
		this.expression = expression;
	}
}
