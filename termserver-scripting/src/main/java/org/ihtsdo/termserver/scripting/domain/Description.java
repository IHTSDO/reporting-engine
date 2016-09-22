
package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Generated;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ACCEPTABILITY;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.DESCRIPTION_TYPE;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.InactivationIndicator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Description {

	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private boolean active;
	@SerializedName("descriptionId")
	@Expose
	private String descriptionId;
	@SerializedName("conceptId")
	@Expose
	private String conceptId;
	@SerializedName("type")
	@Expose
	private DESCRIPTION_TYPE type;
	@SerializedName("lang")
	@Expose
	private String lang;
	@SerializedName("term")
	@Expose
	private String term;
	@SerializedName("caseSignificance")
	@Expose
	private String caseSignificance;
	@SerializedName("acceptabilityMap")
	@Expose
	private Map<String, ACCEPTABILITY> acceptabilityMap;
	@SerializedName("inactivationIndicator")
	@Expose
	private InactivationIndicator inactivationIndicator;
	List<LangRefsetEntry> langRefsetEntries;

	/**
	 * No args constructor for use in serialization
	 * 
	 */
	public Description() {
	}

	/**
	 * 
	 * @param moduleId
	 * @param term
	 * @param conceptId
	 * @param active
	 * @param effectiveTime
	 * @param type
	 * @param descriptionId
	 * @param caseSignificance
	 * @param lang
	 * @param acceptabilityMap
	 */
	public Description(String effectiveTime, String moduleId, boolean active, String descriptionId, String conceptId, DESCRIPTION_TYPE type, String lang, String term, String caseSignificance, Map<String, ACCEPTABILITY> acceptabilityMap) {
		this.effectiveTime = effectiveTime;
		this.moduleId = moduleId;
		this.active = active;
		this.descriptionId = descriptionId;
		this.conceptId = conceptId;
		this.type = type;
		this.lang = lang;
		this.term = term;
		this.caseSignificance = caseSignificance;
		this.acceptabilityMap = acceptabilityMap;
	}

	public Description(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
		//If we inactivate a description, inactivate all of its LangRefsetEntriesAlso
		if (active == false && this.langRefsetEntries != null) {
			for (LangRefsetEntry thisDialect : getLangRefsetEntries()) {
				thisDialect.setEffectiveTime(null);
				thisDialect.setActive(false);
			}
		}
	}

	public String getDescriptionId() {
		return descriptionId;
	}

	public void setDescriptionId(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public DESCRIPTION_TYPE getType() {
		return type;
	}

	public void setType(DESCRIPTION_TYPE type) {
		this.type = type;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		this.term = term;
	}

	public String getCaseSignificance() {
		return caseSignificance;
	}

	public void setCaseSignificance(String caseSignificance) {
		this.caseSignificance = caseSignificance;
	}

	public Map<String, ACCEPTABILITY> getAcceptabilityMap() {
		return acceptabilityMap;
	}

	/**
	 * 
	 * @param acceptabilityMap
	 *	 The acceptabilityMap
	 */
	public void setAcceptabilityMap(Map<String, ACCEPTABILITY> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
	}

	@Override
	public String toString() {
		return (descriptionId==null?"NEW":descriptionId) + "[" + conceptId + "]: " + term;
	}

	@Override
	public int hashCode() {
		return term.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if ((other instanceof Description) == false) {
			return false;
		}
		Description rhs = ((Description) other);
		return this.hashCode() == rhs.hashCode();
	}
	
	@Override
	public Description clone() {
		Description clone = new Description();
		clone.effectiveTime = null; //New description is unpublished.
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.descriptionId = null;  //Creating a new object, so no id for now.
		clone.conceptId = this.conceptId;
		clone.type = this.type;
		clone.lang = this.lang;
		clone.term = this.term;
		clone.caseSignificance = this.caseSignificance;
		clone.acceptabilityMap = new HashMap<String, ACCEPTABILITY>();
		if (this.acceptabilityMap != null) { 
			clone.acceptabilityMap.putAll(this.acceptabilityMap);
		}
		if (langRefsetEntries != null) {
			for (LangRefsetEntry thisDialect : getLangRefsetEntries()) {
				LangRefsetEntry thisDialectClone = thisDialect.clone(); //will create a new UUID and remove EffectiveTime
				clone.getLangRefsetEntries().add(thisDialectClone);
			}
		}
		return clone;
	}

	public InactivationIndicator getInactivationIndicator() {
		return inactivationIndicator;
	}

	public void setInactivationIndicator(InactivationIndicator inactivationIndicator) {
		this.inactivationIndicator = inactivationIndicator;
	}

	public void setAcceptablity(String refsetId, ACCEPTABILITY acceptability) {
		if (acceptabilityMap == null) {
			acceptabilityMap = new HashMap<String, ACCEPTABILITY> ();
		}
		acceptabilityMap.put(refsetId, acceptability);
	}

	public String[] toRF2() throws TermServerScriptException {
		//"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"
		return new String[] {descriptionId, effectiveTime, (active?"1":"0"), moduleId, conceptId, lang,
				SnomedUtils.translateDescType(type), term, caseSignificance};
	}

	public List<LangRefsetEntry> getLangRefsetEntries() {
		if (langRefsetEntries == null) {
			langRefsetEntries = new ArrayList<LangRefsetEntry>();
		}
		return langRefsetEntries;
	}

}
