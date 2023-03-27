
package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.RF2Constants.CaseSignificance;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.util.AcceptabilityMode;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Description extends Component implements ScriptConstants {
	
	public static boolean padTerm = false; //Pads terms front and back with spaces to assist whole word matching.

	@SerializedName(value = "descriptionId", alternate = {"id"})
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
	
	@SerializedName("associationTargets")
	@Expose
	private AssociationTargets associationTargets;
	
	private transient List<LangRefsetEntry> langRefsetEntries;
	private transient boolean isDeleted = false;
	private transient String deletionEffectiveTime;
	
	//Note that these values are used when loading from RF2 where multiple entries can exist.
	//When interacting with the TS, only one inactivation indicator is used (see above).
	private transient List<InactivationIndicatorEntry> inactivationIndicatorEntries;
	
	private transient List<AssociationEntry> associationEntries;
	
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
	
	public static Description withDefaults (String term, DescriptionType type, Acceptability acceptability) {
		Description d = withDefaults(term, type, (Map<String,Acceptability>)null);
		if (acceptability != null) {
			if (acceptability.equals(Acceptability.PREFERRED)) {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_BOTH));
			} else {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
			}
		}
		return d;
	}
	
	public static Description withDefaults (String term, DescriptionType type, Map<String,Acceptability> acceptabilityMap) {
		Description d = new Description();
		if (!StringUtils.isEmpty(term)) {
			d.setCaseSignificance(StringUtils.calculateCaseSignificance(term));
		} else {
			d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
		}
		d.setLang(LANG_EN);
		d.setModuleId(SCTID_CORE_MODULE);
		d.setActive(true);
		d.setTerm(term);
		d.setType(type);
		d.setReleased(false);
		d.setAcceptabilityMap(acceptabilityMap);
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
		setModuleId(moduleId, false);
	}
	public void setModuleId(String moduleId, boolean isPublished) {
		if (this.moduleId != null && !this.moduleId.equals(moduleId) && !isPublished) {
			setDirty();
			this.effectiveTime = null;
		}
		this.moduleId = moduleId;
	}

	public void setActive(boolean newActiveState, boolean forceDirty) {
		if (forceDirty || (this.active != null && !this.active == newActiveState)) {
			setDirty();
			//If we inactivate a description, inactivate all of its LangRefsetEntriesAlso
			if (newActiveState == false && this.langRefsetEntries != null) {
				//If we're working with RF2, modify the lang ref set
				for (LangRefsetEntry thisDialect : getLangRefsetEntries()) {
					thisDialect.setActive(false, forceDirty);
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
	
	public void setId(String descriptionId) {
		this.descriptionId = descriptionId;
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
			isDirty = true;
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
		if (acceptabilityMap == null) {
			acceptabilityMap = new HashMap<>();
		}
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
		StringBuffer sb = new StringBuffer();
		if (descriptionId == null && (term == null || term.isEmpty())) {
			return "";
		}
		String caseSig = "?";
		try {
			if (caseSignificance != null) {
				caseSig = SnomedUtils.translateCaseSignificanceFromEnum(caseSignificance);
			}
		} catch (Exception e) {}
		
		sb.append((isActive() == null || isActive())?"":"*")
		.append(descriptionId==null?"NEW":descriptionId)
		.append(" [")
		.append(conceptId)
		.append( "] ");
		
		if (lang != null) {
			if (lang.contentEquals("en")) {
				sb.append(SnomedUtils.toString(acceptabilityMap));
			} else {
				sb.append(lang);
			}
		}
		
		sb.append(": ")
		.append(term)
		.append(" [")
		.append(caseSig)
		.append("]");
		return sb.toString();
	}

	@Override
	public int hashCode() {
		return term == null? NOT_SET : term.hashCode();
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
		
		//If one side has an SCTID and the other doesn't, then don't compare - one is likely "new"
		if ((this.getDescriptionId() == null && rhs.getDescriptionId() != null) || (this.getDescriptionId() != null && rhs.getDescriptionId() == null)) {
			return false;
		}
		//Otherwise if both sides have an SCTID, then compare that
		if (this.getDescriptionId() != null && rhs.getDescriptionId() != null) {
			return this.getDescriptionId().equals(rhs.getDescriptionId());
		}
		//Otherwise compare term
		return this.hashCode() == rhs.hashCode();
	}
	
	public Description clone(String newSCTID) {
		return clone(newSCTID, false);
	}
	
	public Description clone(String newSCTID, boolean keepIds) {
		Description clone = new Description();
		//If we're issuing a new id then description is unpublished.
		clone.effectiveTime = keepIds ? this.effectiveTime : null; 
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.descriptionId = keepIds? this.descriptionId : newSCTID;
		clone.conceptId = this.conceptId;
		clone.type = this.type;
		clone.lang = this.lang;
		clone.term = this.term;
		clone.caseSignificance = this.caseSignificance;
		if (this.released != null) {
			clone.setReleased(keepIds ? this.released : false);
		}
		clone.acceptabilityMap = new HashMap<String, Acceptability>();
		if (this.acceptabilityMap != null) { 
			clone.acceptabilityMap.putAll(this.acceptabilityMap);
		}
		if (langRefsetEntries != null) {
			for (LangRefsetEntry thisDialect : this.getLangRefsetEntries()) {
				//The lang refset entres for the cloned description should also point to it
				LangRefsetEntry thisDialectClone = thisDialect.clone(clone.descriptionId, keepIds);
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
	
	public void setAcceptability(String refsetId, Acceptability acceptability) throws TermServerScriptException {
		setAcceptability(refsetId, acceptability, false);
	}

	public void setAcceptability(String refsetId, Acceptability acceptability, boolean isReplacement) throws TermServerScriptException {
		if (acceptabilityMap == null) {
			acceptabilityMap = new HashMap<String, Acceptability> ();
		}
		acceptabilityMap.put(refsetId, acceptability);
		
		if (!isReplacement) {
			//Also if we're working with RF2 loaded content we need to make the same change to the entries
			boolean refsetEntrySet = false;
			for (LangRefsetEntry l : getLangRefsetEntries(ActiveState.ACTIVE, refsetId)) {
				l.setAcceptabilityId(SnomedUtils.translateAcceptabilityToSCTID(acceptability));
				refsetEntrySet = true;
				l.setDirty();
			}
			//If we've not set it, is there an inactive record we could re-use?
			if (!refsetEntrySet) {
				for (LangRefsetEntry l : getLangRefsetEntries(ActiveState.INACTIVE, refsetId)) {
					l.setActive(true);
					l.setAcceptabilityId(SnomedUtils.translateAcceptabilityToSCTID(acceptability));
					l.setDirty();
				}
			}
		}
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
		return getLangRefsetEntries (activeState, new String[] {langRefsetId}, null); // Return all modules
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState, String[] langRefsetIds) {
		return getLangRefsetEntries (activeState, langRefsetIds, null); // Return all modules
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState, String[] langRefsetIds, String moduleId) {
		List<LangRefsetEntry> result = new ArrayList<LangRefsetEntry>();
		for (LangRefsetEntry thisLangRefSetEntry : getLangRefsetEntries(activeState)) {
			for (String langRefsetId : langRefsetIds) {
				if (thisLangRefSetEntry.getRefsetId().equals(langRefsetId)) {
					if (moduleId == null || thisLangRefSetEntry.getModuleId().equals(moduleId)) {
						result.add(thisLangRefSetEntry);
						break;
					}
				}
			}
		}
		return result;
	}

	public LangRefsetEntry getLangRefsetEntry(String memberId) {
		for (LangRefsetEntry l : getLangRefsetEntries()) {
			if (l.getId().equals(memberId)) {
				return l;
			}
		}
		return null;
	}

	/**
	 * @return true if this description is preferred in any dialect.
	 */
	public boolean isPreferred() {
		return isPreferred(null);
	}
	
	public boolean isPreferred(String langRefsetSctId) {
		return hasAcceptability(Acceptability.PREFERRED, langRefsetSctId);
	}
	
	public boolean isAcceptable(String langRefsetSctId) {
		return hasAcceptability(Acceptability.ACCEPTABLE, langRefsetSctId);
	}
	
	public boolean hasAcceptability(Acceptability acceptability, String langRefsetSctId) {
		//Are we working with the JSON map, or RF2 Lang refset entries?
		if (acceptabilityMap != null) {
			//First, are we looking for not-acceptable?
			if (acceptability.equals(Acceptability.NONE) && !acceptabilityMap.containsKey(langRefsetSctId)) {
				return true;
			}
			
			//Otherwise, find that acceptability in the map
			for (Map.Entry<String, Acceptability> entry: acceptabilityMap.entrySet()) {
				if ((langRefsetSctId == null || entry.getKey().equals(langRefsetSctId)) && 
						(acceptability.equals(Acceptability.BOTH) || entry.getValue().equals(acceptability))) {
					return true;
				}
			}
			return false;
		}
		String acceptablitySCTID = acceptability == Acceptability.PREFERRED ? SCTID_PREFERRED_TERM : SCTID_ACCEPTABLE_TERM;
		boolean langFound = false;
		if (langRefsetEntries != null) {
			for (LangRefsetEntry entry : langRefsetEntries) {
				if (entry.isActive()) {
					if ((langRefsetSctId == null || entry.getRefsetId().equals(langRefsetSctId))) {
						langFound = true;
						if (acceptability.equals(Acceptability.BOTH) || 
								entry.getAcceptabilityId().equals(acceptablitySCTID)) {
							return true;
						}
					}
				}
			}
			//Were we in fact looking for there to be no entry here?
			if (acceptability.equals(Acceptability.NONE) && !langFound) {
				return true;
			}
			return false;
		}
		
		return false;
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
		//d.setActive(lineItems[DES_IDX_ACTIVE].equals("1"));  //Set this directly when loading RF2
		//Otherwise we'll inactivate the language refset entries prematurely
		d.active=lineItems[DES_IDX_ACTIVE].equals("1");
		//Set effective time after active, since changing activate state resets effectiveTime
		d.setEffectiveTime(lineItems[DES_IDX_EFFECTIVETIME].isEmpty()?null:lineItems[DES_IDX_EFFECTIVETIME]);
		if (d.getEffectiveTime() != null) {
			d.setReleased(true);
		}
		boolean isPublished = d.isReleased() == null ? false :  d.isReleased();
		d.setModuleId(lineItems[DES_IDX_MODULID], isPublished);
		d.setCaseSignificance(SnomedUtils.translateCaseSignificanceToEnum(lineItems[DES_IDX_CASESIGNIFICANCEID]));
		d.setConceptId(lineItems[DES_IDX_CONCEPTID]);
		d.setLang(lineItems[DES_IDX_LANGUAGECODE]);
		d.setTerm(lineItems[DES_IDX_TERM]);
		d.setType(SnomedUtils.translateDescType(lineItems[DES_IDX_TYPEID]));
	}

	public void addAcceptability(LangRefsetEntry lang) throws TermServerScriptException {
		addLangRefsetEntry(lang);
	}
	//A langrefset entry is an RF2 representation, where the acceptability map
	//is a text based json representation.   This method allows the former to 
	//be converted to the latter.
	public void addLangRefsetEntry(LangRefsetEntry lang) throws TermServerScriptException {
		addLangRefsetEntry(lang, true, false);
	}
	
	public void addLangRefsetEntry(LangRefsetEntry lang, boolean ensureReuse, boolean isReplacement) throws TermServerScriptException {
		if (lang.isActive()) {
			Acceptability acceptability = SnomedUtils.translateAcceptability(lang.getAcceptabilityId());
			setAcceptability(lang.getRefsetId(), acceptability, isReplacement);
		} else {
			removeAcceptability(lang.getRefsetId());
		}
		//We only need one refset entry per description for a given refsetId
		//Remove any entries with the same id first
		if (langRefsetEntries != null) {
			langRefsetEntries.remove(lang);
		} else {
			langRefsetEntries = new ArrayList<>();
		}
		
		if (ensureReuse) {
			if (SnomedUtils.isEmpty(lang.getEffectiveTime()) &&
				langRefsetEntries.stream()
					.anyMatch(l -> l.getRefsetId().equals(lang.getRefsetId()))) {
				throw new IllegalStateException("Check here, don't want two entries for same refset");
			}
		} else {
			if (SnomedUtils.isEmpty(lang.getEffectiveTime()) &&
					langRefsetEntries.stream()
						.filter(l -> l.isActive())
						.anyMatch(l -> l.getRefsetId().equals(lang.getRefsetId()))) {
					throw new IllegalStateException("Check here, don't want two active entries for same refset");
				}
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
	
	public Collection<Acceptability> getAcceptabilities() throws TermServerScriptException {
		//Are we working with the JSON map, or RF2 Lang refset entries?
		if (acceptabilityMap != null) {
			return acceptabilityMap.values();
		}
		
		if (langRefsetEntries != null) {
			return langRefsetEntries.stream()
					.filter(rm -> rm.isActive())
					.map(rm -> SnomedUtils.translateAcceptabilitySafely(rm.getAcceptabilityId()))
					.collect(Collectors.toSet());
		}
		return new ArrayList<>();
	}
	
	public List<InactivationIndicatorEntry> getInactivationIndicatorEntries() {
		if (inactivationIndicatorEntries == null) {
			inactivationIndicatorEntries = new ArrayList<InactivationIndicatorEntry>();
		}
		return inactivationIndicatorEntries;
	}
	
	public void addInactivationIndicator(InactivationIndicatorEntry i) {
		//Replace any indicators with the same UUID
		getInactivationIndicatorEntries().remove(i);
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

	@Override
	public String getId() {
		return descriptionId;
	}

	@Override
	public ComponentType getComponentType() {
		return ComponentType.DESCRIPTION;
	}

	@Override
	public String getReportedName() {
		return term;
	}

	@Override
	public String getReportedType() {
		return type.toString();
	}

	public String getEffectiveTimeSafely() {
		return effectiveTime == null ? "" : effectiveTime;
	}

	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) {
		Description otherD = (Description)other;
		List<String> differences = new ArrayList<>();
		String name = this.getClass().getSimpleName(); 
		commonFieldComparison(other, differences, ignoreEffectiveTime);
		
		if (!this.getConceptId().equals(otherD.getConceptId())) {
			differences.add("Concept Id is different in " + name + ": " + this.getConceptId() + " vs " + otherD.getConceptId());
		}
		
		if (!this.getType().equals(otherD.getType())) {
			differences.add("Type is different in " + name + ": " + this.getType() + " vs " + otherD.getType());
		}
		
		if (!this.getTerm().equals(otherD.getTerm())) {
			differences.add("Term is different in " + name + ": " + this.getTerm() + " vs " + otherD.getTerm());
		}
		
		if (!this.getCaseSignificance().equals(otherD.getCaseSignificance())) {
			differences.add("CaseSig is different in " + name + ": " + this.getCaseSignificance() + " vs " + otherD.getCaseSignificance());
		}
		return differences;
	}
	
	public List<AssociationEntry> getAssociationEntries() {
		if (associationEntries == null) {
			associationEntries = new ArrayList<AssociationEntry>();
		}
		return associationEntries;
	}
	
	public List<AssociationEntry> getAssociationEntries(ActiveState activeState) {
		return getAssociationEntries(activeState, false); //All associations by default
	}

	public List<AssociationEntry> getAssociationEntries(ActiveState activeState, boolean historicalAssociationsOnly) {
		if (activeState.equals(ActiveState.BOTH)) {
			List<AssociationEntry> selectedAssociations = new ArrayList<AssociationEntry>();
			for (AssociationEntry h : getAssociationEntries()) {
				//TODO Find a better way of working out if an association is a historical association
				if ((!historicalAssociationsOnly ||	h.getRefsetId().startsWith("9000000"))) {
					selectedAssociations.add(h);
				}
			}
			return selectedAssociations;
		} else {
			boolean isActive = activeState.equals(ActiveState.ACTIVE);
			List<AssociationEntry> selectedAssociations = new ArrayList<AssociationEntry>();
			for (AssociationEntry h : getAssociationEntries()) {
				//TODO Find a better way of working out if an association is a historical association
				if (h.isActive() == isActive && (!historicalAssociationsOnly ||
					(h.getRefsetId().startsWith("9000000")))) {
					selectedAssociations.add(h);
				}
			}
			return selectedAssociations;
		}
	}

	public InactivationIndicatorEntry getInactivationIndicatorEntry(String indicatorId) {
		for (InactivationIndicatorEntry i : getInactivationIndicatorEntries()) {
			if (i.getId().equals(indicatorId)) {
				return i;
			}
		}
		return null;
	}

	public AssociationEntry getAssociationEntry(String assocId) {
		if (associationEntries == null) {
			return null;
		}
		
		for (AssociationEntry i : associationEntries) {
			if (i.getId().equals(assocId)) {
				return i;
			}
		}
		return null;
	}

	public void setDirty(String[] refsetIds) {
		for (String refsetId : refsetIds) {
			for (LangRefsetEntry l : getLangRefsetEntries(ActiveState.ACTIVE, refsetId)) {
				l.setDirty();
				l.setEffectiveTime(null);
			}
		}
	}
	
	public void setTypeId (String typeId) throws TermServerScriptException {
		setType(SnomedUtils.translateDescType(typeId));
	}

	@Override
	public String getMutableFields() {
		return super.getMutableFields() + this.type + "," 
				+ this.term + "," + this.caseSignificance;
	}
	
	public AssociationTargets getAssociationTargets() {
		if (associationTargets == null) {
			associationTargets = new AssociationTargets();
		}
		return associationTargets;
	}

	public void setAssociationTargets(AssociationTargets associationTargets) {
		this.associationTargets = associationTargets;
	}

	public void calculateAcceptabilityMap() throws TermServerScriptException {
		if (langRefsetEntries == null) {
			throw new IllegalStateException("Must have langrefset entries loaded from RF2 to calculate acceptability map");
		}
		acceptabilityMap = new HashMap<>();
		for (LangRefsetEntry lang : getLangRefsetEntries(ActiveState.ACTIVE)) {
			Acceptability acceptability = SnomedUtils.translateAcceptability(lang.getAcceptabilityId());
			setAcceptability(lang.getRefsetId(), acceptability);
		}
	}

	public LangRefsetEntry getLangRefsetEntry(ActiveState active, String refsetId) {
		List<LangRefsetEntry> langRefsetEntries = getLangRefsetEntries(active, refsetId);
		if (langRefsetEntries == null || langRefsetEntries.size() == 0) {
			return null;
		} else if (langRefsetEntries.size() > 1) {
			throw new IllegalStateException("More than one langrefset entry for refset " + refsetId + " for " + this);
		}
		return langRefsetEntries.get(0);
	}

}
