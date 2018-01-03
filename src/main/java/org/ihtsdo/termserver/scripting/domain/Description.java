
package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Description implements RF2Constants{
	
	public static boolean padTerm = false; //Pads terms front and back with spaces to assist whole word matching.

	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private Boolean active;
	@SerializedName("released")
	@Expose
	private Boolean released;
	@SerializedName("descriptionId")
	@Expose
	private String descriptionId;
	@SerializedName("conceptId")
	@Expose
	private String conceptId;
	@SerializedName("type")
	@Expose
	private DescriptionType type;
	@SerializedName("lang")
	@Expose
	private String lang;
	@SerializedName("term")
	@Expose
	private String term;
	@SerializedName("caseSignificance")
	@Expose
	private CaseSignificance caseSignificance;
	@SerializedName("acceptabilityMap")
	@Expose
	private Map<String, Acceptability> acceptabilityMap = null;
	@SerializedName("inactivationIndicator")
	@Expose
	private InactivationIndicator inactivationIndicator;
	List<LangRefsetEntry> langRefsetEntries;
	private boolean dirty = false;
	private boolean isDeleted = false;
	private String deletionEffectiveTime;
	
	//Note that these values are used when loading from RF2 where multiple entries can exist.
	//When interacting with the TS, only one inactivation indicator is used (see above).
	List<InactivationIndicatorEntry> inactivationIndicatorEntries;
	
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
	 * @param AcceptabilityMap
	 */
	public Description(String effectiveTime, String moduleId, boolean active, String descriptionId, String conceptId, DescriptionType type, String lang, String term, CaseSignificance caseSignificance, Map<String, Acceptability> AcceptabilityMap) {
		this.effectiveTime = effectiveTime;
		this.moduleId = moduleId;
		this.active = active;
		this.descriptionId = descriptionId;
		this.conceptId = conceptId;
		this.type = type;
		this.lang = lang;
		if (padTerm) {
			this.term = " " + term + " ";
		} else {
			this.term = term;
		}
		this.caseSignificance = caseSignificance;
		this.acceptabilityMap = AcceptabilityMap;
	}

	public Description(String descriptionId) {
		this.descriptionId = descriptionId;
	}
	
	public static Description withDefaults (String term, DescriptionType type) {
		Description d = new Description();
		d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
		d.setLang(LANG_EN);
		d.setModuleId(SCTID_CORE_MODULE);
		d.setActive(true);
		d.setTerm(term);
		d.setType(type);
		return d;
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
		if (this.moduleId != null && !this.moduleId.equals(moduleId)) {
			setDirty();
			this.effectiveTime = null;
		}
		this.moduleId = moduleId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean newActiveState) {
		if (this.active != null && !this.active == newActiveState) {
			setDirty();
			//If we inactivate a description, inactivate all of its LangRefsetEntriesAlso
			if (newActiveState == false && this.langRefsetEntries != null) {
				//If we're working with RF2, modify the lang ref set
				for (LangRefsetEntry thisDialect : getLangRefsetEntries()) {
					thisDialect.setActive(false);
				}
				//If we're working with TS Concepts, remove the acceptability Map
				acceptabilityMap = null;
			}
			this.effectiveTime = null;
		}
		this.active = newActiveState;
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

	public DescriptionType getType() {
		return type;
	}

	public void setType(DescriptionType type) {
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
		//Are we changing the term?
		if (this.term != null && !this.term.equalsIgnoreCase(term)) {
			dirty = true;
		}
		
		if (padTerm) {
			this.term = " " + term + " ";
		} else {
			this.term = term;
		}
	}

	public CaseSignificance getCaseSignificance() {
		return caseSignificance;
	}

	public void setCaseSignificance(CaseSignificance caseSignificance) {
		this.caseSignificance = caseSignificance;
	}

	public Map<String, Acceptability> getAcceptabilityMap() {
		return acceptabilityMap;
	}

	/**
	 * 
	 * @param AcceptabilityMap
	 *	 The AcceptabilityMap
	 */
	public void setAcceptabilityMap(Map<String, Acceptability> AcceptabilityMap) {
		this.acceptabilityMap = AcceptabilityMap;
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
		//If both sides have an SCTID, then compare that
		if (this.getDescriptionId() != null && rhs.getDescriptionId() != null) {
			return this.getDescriptionId().equals(rhs.getDescriptionId());
		}
		//Otherwise compare term
		return this.hashCode() == rhs.hashCode();
	}
	
	public Description clone(String newSCTID) {
		Description clone = new Description();
		clone.effectiveTime = null; //New description is unpublished.
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.descriptionId = newSCTID;
		clone.conceptId = this.conceptId;
		clone.type = this.type;
		clone.lang = this.lang;
		clone.term = this.term;
		clone.caseSignificance = this.caseSignificance;
		clone.acceptabilityMap = new HashMap<String, Acceptability>();
		if (this.acceptabilityMap != null) { 
			clone.acceptabilityMap.putAll(this.acceptabilityMap);
		}
		if (langRefsetEntries != null) {
			for (LangRefsetEntry thisDialect : this.getLangRefsetEntries()) {
				//The lang refset entres for the cloned description should also point to it
				LangRefsetEntry thisDialectClone = thisDialect.clone(clone.descriptionId); //will create a new UUID and remove EffectiveTime
				clone.getLangRefsetEntries().add(thisDialectClone);
				thisDialectClone.setActive(true);
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

	public void setAcceptablity(String refsetId, Acceptability Acceptability) {
		if (acceptabilityMap == null) {
			acceptabilityMap = new HashMap<String, Acceptability> ();
		}
		acceptabilityMap.put(refsetId, Acceptability);
	}
	
	public void removeAcceptability(String refsetId) {
		//If we've no acceptability yet, then nothing to do here
		if (acceptabilityMap != null) {
			acceptabilityMap.remove(refsetId);
		}
	}

	public String[] toRF2() throws TermServerScriptException {
		//"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"
		return new String[] {descriptionId, effectiveTime, (active?"1":"0"), moduleId, conceptId, lang,
				SnomedUtils.translateDescType(type), term, SnomedUtils.translateCaseSignificanceToSctId(getCaseSignificance())};
	}
	
	public String[] toRF2Deletion() throws TermServerScriptException {
		//"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"
		return new String[] {descriptionId, effectiveTime, deletionEffectiveTime,
				(active?"1":"0"), "1",
				moduleId, conceptId, lang,
				SnomedUtils.translateDescType(type), term, SnomedUtils.translateCaseSignificanceToSctId(getCaseSignificance())};
	}

	public List<LangRefsetEntry> getLangRefsetEntries() {
		if (langRefsetEntries == null) {
			langRefsetEntries = new ArrayList<LangRefsetEntry>();
		}
		return langRefsetEntries;
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState) {
		if (activeState.equals(ActiveState.BOTH)) {
			return getLangRefsetEntries();
		}
		List<LangRefsetEntry> result = new ArrayList<LangRefsetEntry>();
		for (LangRefsetEntry l : getLangRefsetEntries()) {
			if ((activeState.equals(ActiveState.ACTIVE) && l.isActive()) ||
				(activeState.equals(ActiveState.INACTIVE) && !l.isActive()) ) {
				result.add(l);
			}
		}
		return result;
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState, String langRefsetId) {
		return getLangRefsetEntries (activeState, langRefsetId, null); // Return all modules
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState, String langRefsetId, String moduleId) {
		List<LangRefsetEntry> result = new ArrayList<LangRefsetEntry>();
		for (LangRefsetEntry thisLangRefSetEntry : getLangRefsetEntries(activeState)) {
			if (thisLangRefSetEntry.getRefsetId().equals(langRefsetId)) {
				if (moduleId == null || thisLangRefSetEntry.getModuleId().equals(moduleId)) {
					result.add(thisLangRefSetEntry);
				}
			}
		}
		return result;
	}

	public boolean isDirty() {
		return dirty;
	}
	
	/**
	 * @return true if this description is preferred in any dialect.
	 */
	public boolean isPreferred() {
		//Are we working with the JSON map, or RF2 Lang refset entries?
		if (acceptabilityMap != null) {
			for (Map.Entry<String, Acceptability> entry: acceptabilityMap.entrySet()) {
				if (entry.getValue().equals(Acceptability.PREFERRED)) {
					return true;
				}
			}
			return false;
		}
		
		if (langRefsetEntries != null) {
			for (LangRefsetEntry entry : langRefsetEntries) {
				if (entry.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
					return true;
				}
			}
			return false;
		}
		
		return false;
	}
	
	public void setDirty() {
		dirty = true;
	}
	
	public void inactivateDescription(InactivationIndicator indicator) {
		this.setActive(false);
		this.setInactivationIndicator(indicator);
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void delete(String deletionEffectiveTime) {
		this.isDeleted = true;
		this.deletionEffectiveTime = deletionEffectiveTime;
	}
	
	public static void fillFromRf2(Description d, String[] lineItems) throws TermServerScriptException {
		d.setDescriptionId(lineItems[DES_IDX_ID]);
		d.setActive(lineItems[DES_IDX_ACTIVE].equals("1"));
		//Set effective time after active, since changing activate state resets effectiveTime
		d.setEffectiveTime(lineItems[DES_IDX_EFFECTIVETIME].isEmpty()?null:lineItems[DES_IDX_EFFECTIVETIME]);
		if (d.getEffectiveTime() != null) {
			d.setReleased(true);
		}
		d.setModuleId(lineItems[DES_IDX_MODULID]);
		d.setCaseSignificance(SnomedUtils.translateCaseSignificanceToEnum(lineItems[DES_IDX_CASESIGNIFICANCEID]));
		d.setConceptId(lineItems[DES_IDX_CONCEPTID]);
		d.setLang(lineItems[DES_IDX_LANGUAGECODE]);
		d.setTerm(lineItems[DES_IDX_TERM]);
		d.setType(SnomedUtils.translateDescType(lineItems[DES_IDX_TYPEID]));
	}

	//A langrefset entry is an RF2 representation, where the acceptability map
	//is a text based json representation.   This method allows the former to 
	//be converted to the latter.
	public void addAcceptability(LangRefsetEntry lang) throws TermServerScriptException {
		if (lang.isActive()) {
			Acceptability acceptability = SnomedUtils.translateAcceptability(lang.getAcceptabilityId());
			setAcceptablity(lang.getRefsetId(), acceptability);
		} else {
			removeAcceptability(lang.getRefsetId());
		}
		langRefsetEntries.add(lang);
	}

	public Boolean isReleased() {
		return released;
	}

	public void setReleased(Boolean released) {
		this.released = released;
	}

	public Acceptability getAcceptability(String langRefsetId) throws TermServerScriptException {
		//Are we working with the JSON map, or RF2 Lang refset entries?
		if (acceptabilityMap != null) {
			for (Map.Entry<String, Acceptability> entry: acceptabilityMap.entrySet()) {
				if (entry.getKey().equals(langRefsetId)) {
					return entry.getValue();
				}
			}
		}
		
		if (langRefsetEntries != null) {
			for (LangRefsetEntry entry : langRefsetEntries) {
				if (entry.getRefsetId().equals(langRefsetId) && entry.isActive()) {
					return SnomedUtils.translateAcceptability(entry.getAcceptabilityId());
				}
			}
		}
		return null;
	}
	
	public List<InactivationIndicatorEntry> getInactivationIndicatorEntries() {
		if (inactivationIndicatorEntries == null) {
			inactivationIndicatorEntries = new ArrayList<InactivationIndicatorEntry>();
		}
		return inactivationIndicatorEntries;
	}
	
	public void addInactivationIndicator(InactivationIndicatorEntry i) {
		getInactivationIndicatorEntries().add(i);
		if (i.isActive()) {
			setInactivationIndicator(SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()));
		}
	}
	
	public List<InactivationIndicatorEntry> getInactivationIndicatorEntries(ActiveState activeState) {
		if (activeState.equals(ActiveState.BOTH)) {
			return getInactivationIndicatorEntries();
		} else {
			boolean isActive = activeState.equals(ActiveState.ACTIVE);
			List<InactivationIndicatorEntry> selectedInactivationIndicatortEntries = new ArrayList<InactivationIndicatorEntry>();
			for (InactivationIndicatorEntry i : getInactivationIndicatorEntries()) {
				if (i.isActive() == isActive) {
					selectedInactivationIndicatortEntries.add(i);
				}
			}
			return selectedInactivationIndicatortEntries;
		}
	}
	
	public void setInactivationIndicatorEntries(
			List<InactivationIndicatorEntry> inactivationIndicatorEntries) {
		this.inactivationIndicatorEntries = inactivationIndicatorEntries;
	}

}
