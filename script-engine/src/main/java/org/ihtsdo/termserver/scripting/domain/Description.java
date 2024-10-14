
package org.ihtsdo.termserver.scripting.domain;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.util.AcceptabilityMode;
import org.ihtsdo.termserver.scripting.util.LanguageHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Description extends Component implements ScriptConstants, Serializable {

	private static boolean padTerm = false;

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

	private transient Set<RefsetMember> otherRefsetMembers;
	
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
	public Description(String effectiveTime, String moduleId, boolean active, String descriptionId, String conceptId, DescriptionType type, String lang, String term, CaseSignificance caseSignificance, Map<String, Acceptability> acceptabilityMap) {
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
		this.acceptabilityMap = acceptabilityMap;
	}

	public Description(String descriptionId) {
		this.descriptionId = descriptionId;
	}
	
	public static Description withDefaults (String term, DescriptionType type, Acceptability acceptability) throws TermServerScriptException {
		Description d = withDefaults(term, type, (Map<String,Acceptability>)null);
		if (acceptability != null) {
			if (acceptability.equals(Acceptability.PREFERRED)) {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_BOTH));
			} else {
				d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
			}

			//And create langrefset entries for both English dialects
			for (String refsetId : ENGLISH_DIALECTS) {
				LangRefsetEntry l = LangRefsetEntry.withDefaults(d, refsetId, SnomedUtils.translateAcceptabilityToSCTID(acceptability));
				d.getLangRefsetEntries().add(l);
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
		d.setDirty();
		d.setAcceptabilityMap(acceptabilityMap);
		return d;
	}

	public static void setPaddingMode(boolean b) {
		padTerm = b;
	}

	@Override
	public void setActive(boolean newActiveState, boolean forceDirty) {
		if (forceDirty || (this.active != null && !this.active == newActiveState)) {
			setDirty();
			//If we inactivate a description, inactivate all of its LangRefsetEntriesAlso
			if (!newActiveState && this.langRefsetEntries != null) {
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

	@Override
	public void setId(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public void setDescriptionId(String descriptionId) {
		setId(descriptionId);
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

	public void setAcceptabilityMap(Map<String, Acceptability> acceptabilityMap) {
		this.acceptabilityMap = acceptabilityMap;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		if (descriptionId == null && (term == null || term.isEmpty())) {
			return "";
		}
		String caseSig = "?";
		try {
			if (caseSignificance != null) {
				caseSig = SnomedUtils.translateCaseSignificanceFromEnum(caseSignificance);
			}
		} catch (Exception e) {
			caseSig = "CSERROR";
		}
		
		sb.append(isActiveSafely()?"":"*")
		.append(descriptionId==null?"NEW":descriptionId)
		.append(" [")
		.append(conceptId)
		.append( "] ");
		
		sb.append(LanguageHelper.toString(acceptabilityMap));

		sb.append(" ")
		.append(term)
		.append(" [")
		.append(caseSig)
		.append("]");

		if (lang != null && !lang.contentEquals("en")) {
			sb.append(" (").append(lang).append(")");
		}
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
		if (!(other instanceof Description)) {
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
		clone.acceptabilityMap = new HashMap<>();
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

	public void setInactivationIndicator(InactivationIndicator ii) {
		this.inactivationIndicator = ii;

		if (ii == null) {
			//Consider we might want to inactivate any inactivation indicator entries here
			return;
		}

		//Do we already have an inactivation indicator to modify, or are we creating a new one?
		String reasonSCTID = SnomedUtils.translateInactivationIndicator(ii);
		//Attempt reuse of existing inactivation indicator as follows:  active and matching reason, inactivate and matching reason
		//active and not matching, inactive and not matching.
		if (reuseInactivationIndicator(reasonSCTID, true, true) ||
				reuseInactivationIndicator(reasonSCTID, false, true) ||
				reuseInactivationIndicator(reasonSCTID, true, false) ||
				reuseInactivationIndicator(reasonSCTID, false, false)) {
			return;
		}
		//If we get here, we need to create a new inactivation indicator entry
		InactivationIndicatorEntry iie = InactivationIndicatorEntry.withDefaults(this, reasonSCTID);
		this.getInactivationIndicatorEntries().add(iie);
	}

	private boolean reuseInactivationIndicator(String reasonSCTID, boolean existingActive, boolean matchingReason) {
		for (InactivationIndicatorEntry iie : getInactivationIndicatorEntries()) {
			if (iie.isActiveSafely() == existingActive &&
					(matchingReason && iie.getInactivationReasonId().equals(reasonSCTID) ||
							!matchingReason && !iie.getInactivationReasonId().equals(reasonSCTID))) {
				//If we're active and we match, we don't actually have to do anything - indicator is already set correctly
				if (existingActive && matchingReason) {
					return true;
				} else {
					iie.setInactivationReasonId(reasonSCTID);
					iie.setActive(true);
					iie.setDirty();
					return true;
				}
			} else {
				//If we're not reusing this SCTID, set it to inactive
				iie.setActive(false);
			}
		}
		return false;
	}

	public void setAcceptability(String refsetId, Acceptability acceptability) throws TermServerScriptException {
		setAcceptability(refsetId, acceptability, false);
	}

	public void setAcceptability(String refsetId, Acceptability acceptability, boolean isReplacement) throws TermServerScriptException {
		if (acceptabilityMap == null) {
			acceptabilityMap = new HashMap<> ();
		}

		acceptabilityMap.put(refsetId, acceptability);
		
		if (!isReplacement) {
			// Also, if we are working with RF2 loaded content, we need to make the same change to the entries.
			boolean refsetEntrySet = false;

			for (LangRefsetEntry l : getLangRefsetEntries(ActiveState.ACTIVE, refsetId)) {
				l.setAcceptabilityId(SnomedUtils.translateAcceptabilityToSCTID(acceptability));
				l.setDirty();
				refsetEntrySet = true;
			}

			//If we've not set it, is there an inactive record we could re-use?
			if (!refsetEntrySet) {
				for (LangRefsetEntry l : getLangRefsetEntries(ActiveState.INACTIVE, refsetId)) {
					l.setActive(true);
					l.setAcceptabilityId(SnomedUtils.translateAcceptabilityToSCTID(acceptability));
					l.setDirty();
					refsetEntrySet = true;
				}

				// If we still have not found a lang refset entry to reuse, then create one.
				if (!refsetEntrySet) {
                    LangRefsetEntry newLangRefsetEntry = LangRefsetEntry.withDefaults(this, refsetId, SnomedUtils.translateAcceptabilityToSCTID(acceptability));
					newLangRefsetEntry.setDirty();
					newLangRefsetEntry.setActive(true);
					this.getLangRefsetEntries().add(newLangRefsetEntry);
				}
			}
		}
	}
	
	public void removeAcceptability(String refsetId, boolean includeLangRefsetEntries) {
		//If we've no acceptability yet, then nothing to do here
		if (acceptabilityMap != null) {
			acceptabilityMap.remove(refsetId);
		}

		if (includeLangRefsetEntries) {
			//And also work through the refset entries, inactivating if released and deleting if not
			List<LangRefsetEntry> lrs = new ArrayList<>(getLangRefsetEntries(ActiveState.BOTH, refsetId));
			for (LangRefsetEntry l : lrs) {
				if (l.isReleasedSafely()) {
					l.setActive(false);
				} else {
					this.langRefsetEntries.remove(l);
				}
			}
		}
	}

	public String[] toRF2() throws TermServerScriptException {
		//"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"
		return new String[] {descriptionId, effectiveTime, (isActiveSafely()?"1":"0"), moduleId, conceptId, lang,
				SnomedUtils.translateDescType(type), term, SnomedUtils.translateCaseSignificanceToSctId(getCaseSignificance())};
	}
	
	public String[] toRF2Deletion() throws TermServerScriptException {
		//"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"
		return new String[] {descriptionId, effectiveTime, deletionEffectiveTime,
				(isActiveSafely()?"1":"0"), "1",
				moduleId, conceptId, lang,
				SnomedUtils.translateDescType(type), term, SnomedUtils.translateCaseSignificanceToSctId(getCaseSignificance())};
	}

	public List<LangRefsetEntry> getLangRefsetEntries() {
		if (langRefsetEntries == null) {
			langRefsetEntries = new ArrayList<>();
		}
		return langRefsetEntries;
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState) {
		if (activeState.equals(ActiveState.BOTH)) {
			return getLangRefsetEntries();
		}
		List<LangRefsetEntry> result = new ArrayList<>();
		for (LangRefsetEntry l : getLangRefsetEntries()) {
			if ((activeState.equals(ActiveState.ACTIVE) && l.isActiveSafely()) ||
				(activeState.equals(ActiveState.INACTIVE) && !l.isActiveSafely()) ) {
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
		List<LangRefsetEntry> result = new ArrayList<>();
		for (LangRefsetEntry thisLangRefSetEntry : getLangRefsetEntries(activeState)) {
			for (String langRefsetId : langRefsetIds) {
				if (thisLangRefSetEntry.getRefsetId().equals(langRefsetId) &&
					(moduleId == null || thisLangRefSetEntry.getModuleId().equals(moduleId))) {
					result.add(thisLangRefSetEntry);
					break;
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
	
	public void setLangRefsetEntries(List<LangRefsetEntry> langrefsetEntries) {
		this.langRefsetEntries = langrefsetEntries;
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
				if (entry.isActiveSafely() && (langRefsetSctId == null || entry.getRefsetId().equals(langRefsetSctId))) {
					langFound = true;
					if (acceptability.equals(Acceptability.BOTH) ||
							entry.getAcceptabilityId().equals(acceptablitySCTID)) {
						return true;
					}

				}
			}
			//Were we in fact looking for there to be no entry here?
			return acceptability.equals(Acceptability.NONE) && !langFound;
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
		boolean isPublished = d.isReleased() != null && d.isReleased();
		d.setModuleId(lineItems[DES_IDX_MODULID], isPublished);
		d.setCaseSignificance(SnomedUtils.translateCaseSignificanceToEnum(lineItems[DES_IDX_CASESIGNIFICANCEID]));
		d.setConceptId(lineItems[DES_IDX_CONCEPTID]);
		d.setLang(lineItems[DES_IDX_LANGUAGECODE]);
		d.setTerm(lineItems[DES_IDX_TERM]);
		d.setType(SnomedUtils.translateDescType(lineItems[DES_IDX_TYPEID]));
	}

	public void setModuleId(String moduleId, boolean isPublished) {
		if (this.moduleId != null && !this.moduleId.equals(moduleId) && !isPublished) {
			setDirty();
			this.effectiveTime = null;
		}
		this.moduleId = moduleId;
	}

	public void addAcceptability(LangRefsetEntry lang) throws TermServerScriptException {
		addLangRefsetEntry(lang);
	}
	//A langrefset entry is an RF2 representation, where the acceptability map
	//is a text based json representation.   This method allows the former to 
	//be converted to the latter.
	public void addLangRefsetEntry(LangRefsetEntry lang) throws TermServerScriptException {
		addLangRefsetEntry(lang, true);
	}
	
	public void addLangRefsetEntry(LangRefsetEntry lang, boolean ensureReuse) throws TermServerScriptException {
		//Keep the JSON view of acceptability in sync.  Don't call setAcceptability, as it is similarly duplicating the data by creating a langrefset entry
		//Ideally we'd consolidate these two functions, probably using this one as we don't know what the lang refsetentry here might contain
		if (lang.isActiveSafely()) {
			Acceptability acceptability = SnomedUtils.translateAcceptability(lang.getAcceptabilityId());
			if (acceptabilityMap == null) {
				acceptabilityMap = new HashMap<>();
			}
			acceptabilityMap.put(lang.getRefsetId(), acceptability);
		} else {
			removeAcceptability(lang.getRefsetId(), false);
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
				throw new IllegalStateException("Check here, don't want two entries for same refset, when ensuring reuse");
			}
		} else {
			if (SnomedUtils.isEmpty(lang.getEffectiveTime()) &&
					langRefsetEntries.stream()
							.filter(Component::isActive)
							.anyMatch(l -> l.getRefsetId().equals(lang.getRefsetId()))) {
				throw new IllegalStateException("Check here, don't want two active entries for same refset, when not ensuring reuse");
			}
		}

		langRefsetEntries.add(lang);
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
				if (entry.getRefsetId().equals(langRefsetId) && entry.isActiveSafely()) {
					return SnomedUtils.translateAcceptability(entry.getAcceptabilityId());
				}
			}
		}
		return null;
	}
	
	public Collection<Acceptability> getAcceptabilities() {
		//Are we working with the JSON map, or RF2 Lang refset entries?
		if (acceptabilityMap != null) {
			return acceptabilityMap.values();
		}
		
		if (langRefsetEntries != null) {
			return langRefsetEntries.stream()
					.filter(Component::isActive)
					.map(rm -> SnomedUtils.translateAcceptabilitySafely(rm.getAcceptabilityId()))
					.collect(Collectors.toSet());
		}
		return new ArrayList<>();
	}
	
	public List<InactivationIndicatorEntry> getInactivationIndicatorEntries() {
		if (inactivationIndicatorEntries == null) {
			inactivationIndicatorEntries = new ArrayList<>();
		}
		return inactivationIndicatorEntries;
	}
	
	public void addInactivationIndicator(InactivationIndicatorEntry i) {
		//Replace any indicators with the same UUID
		getInactivationIndicatorEntries().remove(i);
		getInactivationIndicatorEntries().add(i);
		if (i.isActiveSafely()) {
			setInactivationIndicator(SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()));
		}
	}
	
	public List<InactivationIndicatorEntry> getInactivationIndicatorEntries(ActiveState activeState) {
		if (activeState.equals(ActiveState.BOTH)) {
			return getInactivationIndicatorEntries();
		} else {
			boolean isActive = activeState.equals(ActiveState.ACTIVE);
			List<InactivationIndicatorEntry> selectedInactivationIndicatortEntries = new ArrayList<>();
			for (InactivationIndicatorEntry i : getInactivationIndicatorEntries()) {
				if (i.isActiveSafely() == isActive) {
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
		return getDescriptionId();
	}

	@Override
	public ComponentType getComponentType() {
		return ComponentType.DESCRIPTION;
	}

	@Override
	public String getReportedName() {
		return getTerm();
	}

	@Override
	public String getReportedType() {
		if (type == null) {
			return null;
		}
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
			associationEntries = new ArrayList<>();
		}
		return associationEntries;
	}
	
	public List<AssociationEntry> getAssociationEntries(ActiveState activeState) {
		return getAssociationEntries(activeState, false); //All associations by default
	}

	public List<AssociationEntry> getAssociationEntries(ActiveState activeState, boolean historicalAssociationsOnly) {
		if (activeState.equals(ActiveState.BOTH)) {
			List<AssociationEntry> selectedAssociations = new ArrayList<>();
			for (AssociationEntry h : getAssociationEntries()) {
				//TODO Find a better way of working out if an association is a historical association
				if ((!historicalAssociationsOnly ||	h.getRefsetId().startsWith("9000000"))) {
					selectedAssociations.add(h);
				}
			}
			return selectedAssociations;
		} else {
			boolean isActive = activeState.equals(ActiveState.ACTIVE);
			List<AssociationEntry> selectedAssociations = new ArrayList<>();
			for (AssociationEntry h : getAssociationEntries()) {
				//TODO Find a better way of working out if an association is a historical association
				if (h.isActiveSafely() == isActive && (!historicalAssociationsOnly ||
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
	public String[] getMutableFields() {
		String[] mutableFields = super.getMutableFields();
		int idx = super.getMutableFieldCount();
		mutableFields[idx++] = type.toString();
		mutableFields[idx++] = term;
		mutableFields[idx] = caseSignificance.toString();
		return mutableFields;
	}

	@Override
	public int getMutableFieldCount() {
		return super.getMutableFieldCount() + 3;
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
		for (LangRefsetEntry lre : getLangRefsetEntries(ActiveState.ACTIVE)) {
			Acceptability acceptability = SnomedUtils.translateAcceptability(lre.getAcceptabilityId());
			setAcceptability(lre.getRefsetId(), acceptability);
		}
	}

	public LangRefsetEntry getLangRefsetEntry(ActiveState active, String refsetId) {
		List<LangRefsetEntry> matchingEntries = getLangRefsetEntries(active, refsetId);
		if (matchingEntries == null || matchingEntries.isEmpty()) {
			return null;
		} else if (matchingEntries.size() > 1) {
			throw new IllegalStateException("More than one langrefset entry for refset " + refsetId + " for " + this);
		}
		return matchingEntries.get(0);
	}
	
	@Override
	public String toWhitelistString() {
		try {
			return super.toWhitelistString() + conceptId + "," + lang + "," +
					SnomedUtils.translateDescType(type) + "," +
					term + "," +
					SnomedUtils.translateCaseSignificanceToSctId(caseSignificance);
		} catch (TermServerScriptException e) {
			throw new IllegalArgumentException("Failed to form whitelist string in " + this);
		}
	}

	public Set<RefsetMember> getOtherRefsetMembers() {
		if (otherRefsetMembers == null) {
			otherRefsetMembers = new HashSet<>();
		}
		return otherRefsetMembers;
	}

	public void setOtherRefsetMembers(Set<RefsetMember> otherRefsetMembers) {
		this.otherRefsetMembers = otherRefsetMembers;
	}

	public RefsetMember getOtherRefsetMember(String id) {
		for (RefsetMember m : getOtherRefsetMembers()) {
			if (m.getId().equals(id)) {
				return m;
			}
		}
		return null;
	}

	@Override
	public boolean matchesMutableFields(Component other) {
		Description otherDesc = (Description) other;
		return this.getType().equals(otherDesc.getType())
				&& this.getTerm().equals(otherDesc.getTerm());
	}

	public Description withCaseSignificance(CaseSignificance caseSignificance) {
		setCaseSignificance(caseSignificance);
		return this;
	}

	public void removeLangRefsetEntry(LangRefsetEntry l) {
		langRefsetEntries.remove(l);
	}

	public InactivationIndicatorEntry getFirstActiveInactivationIndicatorEntry() {
		InactivationIndicatorEntry found = null;
		for (InactivationIndicatorEntry i : getInactivationIndicatorEntries()) {
			if (i.isActiveSafely()) {
				if (found != null) {
					throw new IllegalStateException("Multiple active inactivation indicators found for " + this);
				} else {
					found = i;
				}
			}
		}
		return found;
	}

}
