package org.ihtsdo.termserver.scripting.pipeline.loinc;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;
import org.jetbrains.annotations.NotNull;

public class LoincTerm extends ExternalConcept implements Comparable<LoincTerm> {

	private String component;
	private String property;
	private String timeAspct;
	private String system;
	private String scaleType;
	private String methodType;
	private String loincClass;
	private String versionLastChanged;
	private String chngType;
	private String definitionDescription;
	private String status;
	private String consumerName;
	private String classType;
	private String formula;
	private String exmplAnswers;
	private String surveyQuestText;
	private String surveyQuestSrc;
	private String unitsRequired;
	private String relatedNames2;
	private String shortName;
	private String orderObs;
	private String hl7FieldSubfieldId;
	private String externalCopyrightNotice;
	private String exampleUnits;
	private String longCommonName;
	private String exampleUCUMUnits;
	private String statusReason;
	private String statusText;
	private String changeReasonPublic;
	private String commonTestRank;
	private String commonOrderRank;
	private String commonSItestRank;
	private String hl7AttachmentStructure;
	private String externalCopyrightLink;
	private String panelType;
	private String askatOrderEntry;
	private String associatedObservations;
	private String versionFirstReleased;
	private String validHL7AttachmentRequest;
	private String displayName;
	
	private LoincTerm() {
	}
	
	@Override
	public int hashCode() {
		return externalIdentifier.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if (!(other instanceof LoincTerm)) {
			return false;
		}
		return ((LoincTerm)other).getLoincNum().equals(this.getLoincNum());
	}
	
	public String getLoincNum() {
		return externalIdentifier;
	}
	public void setLoincNum(String loincNum) {
		this.externalIdentifier = loincNum;
	}
	public String getComponent() {
		return component;
	}
	public void setComponent(String component) {
		this.component = component;
	}
	public String getProperty() {
		return property;
	}
	public void setProperty(String property) {
		this.property = property;
	}
	public String getTimeAspct() {
		return timeAspct;
	}
	public void setTimeAspct(String timeAspct) {
		this.timeAspct = timeAspct;
	}
	public String getSystem() {
		return system;
	}
	public void setSystem(String system) {
		this.system = system;
	}
	public String getScaleType() {
		return scaleType;
	}
	public void setScaleType(String scaleType) {
		this.scaleType = scaleType;
	}
	public String getMethodType() {
		return methodType;
	}
	public void setMethodType(String methodType) {
		this.methodType = methodType;
	}
	public String getLoincClass() {
		return loincClass;
	}
	public void setLoincClass(String loincClass) {
		this.loincClass = loincClass;
	}
	public String getVersionLastChanged() {
		return versionLastChanged;
	}
	public void setVersionLastChanged(String versionLastChanged) {
		this.versionLastChanged = versionLastChanged;
	}
	public String getChngType() {
		return chngType;
	}
	public void setChngType(String chngType) {
		this.chngType = chngType;
	}
	public String getDefinitionDescription() {
		return definitionDescription;
	}
	public void setDefinitionDescription(String definitionDescription) {
		this.definitionDescription = definitionDescription;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getConsumerName() {
		return consumerName;
	}
	public void setConsumerName(String consumerName) {
		this.consumerName = consumerName;
	}
	public String getClassType() {
		return classType;
	}
	public void setClassType(String classType) {
		this.classType = classType;
	}
	public String getFormula() {
		return formula;
	}
	public void setFormula(String formula) {
		this.formula = formula;
	}
	public String getExmplAnswers() {
		return exmplAnswers;
	}
	public void setExmplAnswers(String exmplAnswers) {
		this.exmplAnswers = exmplAnswers;
	}
	public String getSurveyQuestText() {
		return surveyQuestText;
	}
	public void setSurveyQuestText(String surveyQuestText) {
		this.surveyQuestText = surveyQuestText;
	}
	public String getSurveyQuestSrc() {
		return surveyQuestSrc;
	}
	public void setSurveyQuestSrc(String surveyQuestSrc) {
		this.surveyQuestSrc = surveyQuestSrc;
	}
	public String getUnitsRequired() {
		return unitsRequired;
	}
	public void setUnitsRequired(String unitsRequired) {
		this.unitsRequired = unitsRequired;
	}
	public String getRelatedNames2() {
		return relatedNames2;
	}
	public void setRelatedNames2(String relatedNames2) {
		this.relatedNames2 = relatedNames2;
	}
	public String getShortName() {
		return shortName;
	}
	public void setShortName(String shortName) {
		this.shortName = shortName;
	}
	public String getOrderObs() {
		return orderObs;
	}
	public void setOrderObs(String orderObs) {
		this.orderObs = orderObs;
	}
	public String getHl7FieldSubfieldId() {
		return hl7FieldSubfieldId;
	}
	public void setHl7FieldSubfieldId(String hl7FieldSubfieldId) {
		this.hl7FieldSubfieldId = hl7FieldSubfieldId;
	}
	public String getExternalCopyrightNotice() {
		return externalCopyrightNotice;
	}
	public void setExternalCopyrightNotice(String externalCopyrightNotice) {
		this.externalCopyrightNotice = externalCopyrightNotice;
	}
	public String getExampleUnits() {
		return exampleUnits;
	}
	public void setExampleUnits(String exampleUnits) {
		this.exampleUnits = exampleUnits;
	}
	public String getLongCommonName() {
		return longCommonName;
	}
	public void setLongCommonName(String longCommonName) {
		this.longCommonName = longCommonName;
	}
	public String getExampleUCUMUnits() {
		return exampleUCUMUnits;
	}
	public void setExampleUCUMUnits(String exampleUCUMUnits) {
		this.exampleUCUMUnits = exampleUCUMUnits;
	}
	public String getStatusReason() {
		return statusReason;
	}
	public void setStatusReason(String statusReason) {
		this.statusReason = statusReason;
	}
	public String getStatusText() {
		return statusText;
	}
	public void setStatusText(String statusText) {
		this.statusText = statusText;
	}
	public String getChangeReasonPublic() {
		return changeReasonPublic;
	}
	public void setChangeReasonPublic(String changeReasonPublic) {
		this.changeReasonPublic = changeReasonPublic;
	}
	public String getCommonTestRank() {
		return commonTestRank;
	}
	public void setCommonTestRank(String commonTestRank) {
		this.commonTestRank = commonTestRank;
	}
	public String getCommonOrderRank() {
		return commonOrderRank;
	}
	public void setCommonOrderRank(String commonOrderRank) {
		this.commonOrderRank = commonOrderRank;
	}
	public String getCommonSItestRank() {
		return commonSItestRank;
	}
	public void setCommonSItestRank(String commonSItestRank) {
		this.commonSItestRank = commonSItestRank;
	}
	public String getHl7AttachmentStructure() {
		return hl7AttachmentStructure;
	}
	public void setHl7AttachmentStructure(String hl7AttachmentStructure) {
		this.hl7AttachmentStructure = hl7AttachmentStructure;
	}
	public String getExternalCopyrightLink() {
		return externalCopyrightLink;
	}
	public void setExternalCopyrightLink(String externalCopyrightLink) {
		this.externalCopyrightLink = externalCopyrightLink;
	}
	public String getPanelType() {
		return panelType;
	}
	public void setPanelType(String panelType) {
		this.panelType = panelType;
	}
	public String getAskatOrderEntry() {
		return askatOrderEntry;
	}
	public void setAskatOrderEntry(String askatOrderEntry) {
		this.askatOrderEntry = askatOrderEntry;
	}
	public String getAssociatedObservations() {
		return associatedObservations;
	}
	public void setAssociatedObservations(String associatedObservations) {
		this.associatedObservations = associatedObservations;
	}
	public String getVersionFirstReleased() {
		return versionFirstReleased;
	}
	public void setVersionFirstReleased(String versionFirstReleased) {
		this.versionFirstReleased = versionFirstReleased;
	}
	public String getValidHL7AttachmentRequest() {
		return validHL7AttachmentRequest;
	}
	public void setValidHL7AttachmentRequest(String validHL7AttachmentRequest) {
		this.validHL7AttachmentRequest = validHL7AttachmentRequest;
	}
	public String getDisplayName() {
		return displayName;
	}
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	public static LoincTerm parse(String[] items) {
		LoincTerm loincTerm = new LoincTerm();
		loincTerm.setLoincNum(items[0]);
		loincTerm.setComponent(items[1]);
		loincTerm.setProperty(items[2]);
		loincTerm.setTimeAspct(items[3]);
		loincTerm.setSystem(items[4]);
		loincTerm.setScaleType(items[5]);
		loincTerm.setMethodType(items[6]);
		loincTerm.setLoincClass(items[7]);
		loincTerm.setVersionLastChanged(items[8]);
		loincTerm.setChngType(items[9]);
		//loincTerm.setDefinitionDescription(items[10]);
		loincTerm.setStatus(items[11]);
		//loincTerm.setConsumerName(items[12]);
		loincTerm.setClassType(items[13]);
		//loincTerm.setFormula(items[14]);
		//loincTerm.setExmplAnswers(items[15]);
		//loincTerm.setSurveyQuestText(items[16]);
		//loincTerm.setSurveyQuestSrc(items[17]);
		//loincTerm.setUnitsRequired(items[18]);
		//loincTerm.setRelatedNames2(items[19]);
		//loincTerm.setShortName(items[20]);
		loincTerm.setOrderObs(items[21]);
		//loincTerm.setHl7FieldSubfieldId(items[22]);
		//loincTerm.setExternalCopyrightNotice(items[23]);
		//loincTerm.setExampleUnits(items[24]);
		loincTerm.setLongCommonName(items[25]);
		//loincTerm.setExampleUCUMUnits(items[26]);
		loincTerm.setStatusReason(items[27]);
		loincTerm.setStatusText(items[28]);
		//loincTerm.setChangeReasonPublic(items[29]);
		loincTerm.setCommonTestRank(items[30]);
		loincTerm.setCommonOrderRank(items[31]);
		loincTerm.setCommonSItestRank(items[32]);
		//loincTerm.setHl7AttachmentStructure(items[33]);
		//loincTerm.setExternalCopyrightLink(items[34]);
		loincTerm.setPanelType(items[35]);
		//loincTerm.setAskatOrderEntry(items[36]);
		//loincTerm.setAssociatedObservations(items[37]);
		//loincTerm.setVersionFirstReleased(items[38]);
		//loincTerm.setValidHL7AttachmentRequest(items[39]);
		loincTerm.setDisplayName(items[40]);
		return loincTerm;
	}
	
	public static LoincTerm parse(CSVRecord csv) {
		LoincTerm loincTerm = new LoincTerm();
		loincTerm.setLoincNum(csv.get(0));
		loincTerm.setComponent(csv.get(1));
		loincTerm.setProperty(csv.get(2));
		loincTerm.setTimeAspct(csv.get(3));
		loincTerm.setSystem(csv.get(4));
		loincTerm.setScaleType(csv.get(5));
		loincTerm.setMethodType(csv.get(6));
		loincTerm.setLoincClass(csv.get(7));
		loincTerm.setVersionLastChanged(csv.get(8));
		loincTerm.setChngType(csv.get(9));
		//loincTerm.setDefinitionDescription(csv.get(10));
		loincTerm.setStatus(csv.get(11));
		//loincTerm.setConsumerName(csv.get(12));
		loincTerm.setClassType(csv.get(13));
		//loincTerm.setFormula(csv.get(14));
		//loincTerm.setExmplAnswers(csv.get(15));
		//loincTerm.setSurveyQuestText(csv.get(16));
		//loincTerm.setSurveyQuestSrc(csv.get(17));
		//loincTerm.setUnitsRequired(csv.get(18));
		//loincTerm.setRelatedNames2(csv.get(19));
		//loincTerm.setShortName(csv.get(20));
		loincTerm.setOrderObs(csv.get(21));
		//loincTerm.setHl7FieldSubfieldId(csv.get(22));
		//loincTerm.setExternalCopyrightNotice(csv.get(23));
		//loincTerm.setExampleUnits(csv.get(24));
		loincTerm.setLongCommonName(csv.get(25));
		//loincTerm.setExampleUCUMUnits(csv.get(26));
		loincTerm.setStatusReason(csv.get(27));
		loincTerm.setStatusText(csv.get(28));
		//loincTerm.setChangeReasonPublic(csv.get(29));
		loincTerm.setCommonTestRank(csv.get(30));
		loincTerm.setCommonOrderRank(csv.get(31));
		
		//Does not exist from LOINC 2.77
		//loincTerm.setCommonSItestRank(csv.get(32));
		//loincTerm.setHl7AttachmentStructure(csv.get(33));
		//loincTerm.setExternalCopyrightLink(csv.get(34));
		//loincTerm.setPanelType(csv.get(35));
		//loincTerm.setAskatOrderEntry(csv.get(36));
		//loincTerm.setAssociatedObservations(csv.get(37));
		//loincTerm.setVersionFirstReleased(csv.get(38));
		//loincTerm.setValidHL7AttachmentRequest(csv.get(39));
		//loincTerm.setDisplayName(csv.get(40));
		
		
		//loincTerm.setHl7AttachmentStructure(csv.get(32));
		//loincTerm.setExternalCopyrightLink(csv.get(33));
		//loincTerm.setPanelType(csv.get(34));
		//loincTerm.setAskatOrderEntry(csv.get(35));
		//loincTerm.setAssociatedObservations(csv.get(36));
		//loincTerm.setVersionFirstReleased(csv.get(37));
		//loincTerm.setValidHL7AttachmentRequest(csv.get(38));
		loincTerm.setDisplayName(csv.get(39));
		return loincTerm;
	}
	
	public String[] getCommonColumns() {
		return new String[] {
				getComponent(),
				getProperty(),
				getTimeAspct(),
				getSystem(),
				getScaleType(),
				getMethodType(),
				getLoincClass(),
				getClassType(),
				getVersionLastChanged(),
				getChngType(),
				getStatus(),
				getStatusReason(),
				getStatusText(),
				getOrderObs(),
				getLongCommonName(),
				getCommonTestRank(),
				getCommonOrderRank(),
				getCommonSItestRank(),
				getPanelType()
		};
	}
	public String getColonizedTerm() {
		String term = getComponent() + ":" + getProperty() + ":" +
				getTimeAspct() + ":" + getSystem() + ":" + getScaleType() + ":" +
				getMethodType();
		term = term.replaceAll("::", ":");
		if (term.endsWith(":")) {
			term = term.substring(0, term.length() - 1);
		}
		return term;
	}

	public boolean isHighUsage() {
		return !getCommonTestRank().equals("0");
	}

	public boolean isHighestUsage() {
		return !getCommonTestRank().equals("0") && Integer.parseInt(getCommonTestRank()) <= 2000;
	}

	public Integer getCommonTestRankNormalized() {
		//Because 0 is unranked, we actually want to rank it as the highest
		if (getCommonTestRank().equals("0")) {
			return Integer.MAX_VALUE;
		}
		return Integer.parseInt(getCommonTestRank());

	}

	@Override
	public int compareTo(@NotNull LoincTerm l) {
		return getCommonTestRankNormalized().compareTo(l.getCommonTestRankNormalized());
	}
}
