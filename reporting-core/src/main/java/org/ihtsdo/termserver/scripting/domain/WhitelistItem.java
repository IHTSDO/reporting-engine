package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class WhitelistItem {
	
	@SerializedName("id")
	@Expose
	private String id;	
	
	@SerializedName("userId")
	@Expose
	private String userId;
	
	@SerializedName("additionalFields")
	@Expose
	private String additionalFields;
	
	@SerializedName("assertionFailureText")
	@Expose
	private String assertionFailureText;
	
	@SerializedName("branch")
	@Expose
	private String branch;
	
	@SerializedName("componentId")
	@Expose
	private String componentId;
	
	@SerializedName("conceptId")
	@Expose
	private String conceptId;
	
	@SerializedName("creationDate")
	@Expose
	private String creationDate;
	
	@SerializedName("creationDateLong")
	@Expose
	private float creationDateLong;
	
	@SerializedName("validationRuleId")
	@Expose
	private String validationRuleId;

	// Getter Methods 
	public String getId() {
		return id;
	}

	public String getUserId() {
		return userId;
	}

	public String getAdditionalFields() {
		return additionalFields;
	}

	public String getAssertionFailureText() {
		return assertionFailureText;
	}

	public String getBranch() {
		return branch;
	}

	public String getComponentId() {
		return componentId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public String getCreationDate() {
		return creationDate;
	}

	public float getCreationDateLong() {
		return creationDateLong;
	}

	public String getValidationRuleId() {
		return validationRuleId;
	}

	 // Setter Methods 

	public void setId(String id) {
		this.id = id;
	}

	public void setUserId(String userId) {
		this.userId = userId;
	}

	public void setAdditionalFields(String additionalFields) {
		this.additionalFields = additionalFields;
	}

	public void setAssertionFailureText(String assertionFailureText) {
		this.assertionFailureText = assertionFailureText;
	}

	public void setBranch(String branch) {
		this.branch = branch;
	}

	public void setComponentId(String componentId) {
		this.componentId = componentId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public void setCreationDate(String creationDate) {
		this.creationDate = creationDate;
	}

	public void setCreationDateLong(float creationDateLong) {
		this.creationDateLong = creationDateLong;
	}

	public void setValidationRuleId(String validationRuleId) {
		this.validationRuleId = validationRuleId;
	}
	
	@Override
	public String toString() {
		return id + " : " + branch + " : " + componentId + " : " + additionalFields;
	}

	public static WhitelistItem createFromComponent(Concept concept, String branchPath, String validationRuleId, Component c) {
		WhitelistItem item = new WhitelistItem();
		item.setValidationRuleId(validationRuleId);
		//Populating this field actually causes a failure to match.  Don't use it!
		//item.setAssertionFailureText(assertionFailureText);
		item.setBranch(branchPath);
		item.setAdditionalFields(c.toWhitelistString());
		item.setConceptId(concept.getId());
		item.setComponentId(c.getId());
		return item;
	}
}
