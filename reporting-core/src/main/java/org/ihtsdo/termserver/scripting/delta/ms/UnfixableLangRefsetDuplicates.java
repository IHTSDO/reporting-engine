package org.ihtsdo.termserver.scripting.delta.ms;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

public class UnfixableLangRefsetDuplicates extends DeltaGenerator {

	public static final String US_MODULE = "731000124108";
	public static final String NO_MODULE = "51000202101";
	
	boolean includeLegacyIssues = false;
	Set<RefsetMember> mentioned = new HashSet<>();
	String intReleaseBranch="MAIN/2023-06-30";
	Map<String, RefsetMember> intReleaseRefsetMembers = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		UnfixableLangRefsetDuplicates delta = new UnfixableLangRefsetDuplicates();
		delta.sourceModuleIds = Set.of(US_MODULE);
		delta.standardExecution(args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleaseFlag(true);
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("UnfixableLangRefsetDuplicates report cannot be run against MAIN");
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Description, Issue, IntActive, IntEffective, IntRM, ExtActive, ExtEffective, ExtRM, , ",
				"Id, FSN, SemTag, Description, Issue, IntActive, IntEffective, IntRM, ExtActive, ExtEffective, ExtRM, ,"};
		String[] tabNames = new String[] {	
				"UnfixableLangRefsetDuplicates",
				"Historic Duplications"};
		super.postInit(googleFolder, tabNames, columnHeadings);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sortFSN(gl.getAllConcepts())) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				reportUnfixableDuplicates(c, d, new ArrayList<>(d.getLangRefsetEntries()));
			}
			
			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(c, true);  //Will only output dirty fields.
			}
		}
	}

	private void reportUnfixableDuplicates(Concept c, Description d, List<LangRefsetEntry> refsetMembers) throws TermServerScriptException {
		HashSet<RefsetMember> nullETMentioned = new HashSet<>();
		HashMap<String, RefsetMember> reassertedLRs = new HashMap<>();
		
		for (final RefsetMember thisEntry : refsetMembers) {
			// Check against every other entry
			for (final RefsetMember thatEntry : refsetMembers) {
				// If we've already decided we're keeping this entry or deleting this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || 
						mentioned.contains(thisEntry) ||
						mentioned.contains(thatEntry) ) {
					continue;
				}
				
				if (!nullETMentioned.contains(thatEntry) 
						&& (hasModule(INTERNATIONAL_MODULES, true, thatEntry) != null && SnomedUtils.isEmpty(thatEntry.getEffectiveTime()))) {
					report(c, d,"Reasserting International Module LRM with null effectiveTime!", thatEntry.isActive(), thatEntry.getEffectiveTime(), thatEntry.toString());
					nullETMentioned.add(thatEntry);
					LangRefsetEntry reasserted = loadLangRefsetMember(thatEntry.getId(), intReleaseBranch);
					d.addLangRefsetEntry(reasserted);
					reasserted.setDirty();
					c.setModified();
					reassertedLRs.put(thatEntry.getId(), reasserted);
				}
				
				if (thisEntry.getRefsetId().equals(thatEntry.getRefsetId())) {
					checkForModuleJumping(c, d, thisEntry);
					checkForModuleJumping(c, d, thatEntry);
					//Are they both published?  If INT is active and EXT is inactive with no effective time then that's as good as we'll get
					if (thisEntry.isReleased() && thatEntry.isReleased()) {
						RefsetMember intRM = hasModule(INTERNATIONAL_MODULES, true, thisEntry, thatEntry);
						RefsetMember extRM = hasModule(INTERNATIONAL_MODULES, false, thisEntry, thatEntry);
						
						if (intRM != null && extRM != null) {
							int tabIdx = PRIMARY_REPORT;
							if (!SnomedUtils.isEmpty(intRM.getEffectiveTime()) && !SnomedUtils.isEmpty(extRM.getEffectiveTime())) {
								tabIdx = SECONDARY_REPORT;
							}
							
							if (intRM.isActiveSafely() && !extRM.isActiveSafely() && StringUtils.isEmpty(extRM.getEffectiveTime())) {
								report(c, d, "Active Int replaced Inactive Ext", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							} else if (intRM.isActiveSafely() && extRM.isActiveSafely()) {
								report(tabIdx, c, d, "Active Int duplicates Active Ext", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							} else if (!intRM.isActiveSafely() && !extRM.isActiveSafely()) {
								report(tabIdx, c, d, "Inactive Int duplicates Inactive Ext", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							} else if (!intRM.isActiveSafely() && extRM.isActiveSafely() && (StringUtils.isEmpty(extRM.getEffectiveTime()) || StringUtils.isEmpty(intRM.getEffectiveTime()))) {
								report(tabIdx, c, d, "Active Ext duplicates Inactive Int", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
								if (reassertedLRs.containsKey(intRM.getId())) {
									RefsetMember reasserted = reassertedLRs.get(intRM.getId());
									report(tabIdx, c, d, "Inactivating Ext after INT reassertion", reasserted.isActive(), reasserted.getEffectiveTime(), reasserted.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
									extRM.setActive(false);
									extRM.setDirty();
									c.setModified();
								}
							} else if (StringUtils.isEmpty(extRM.getEffectiveTime()) || StringUtils.isEmpty(intRM.getEffectiveTime())){
								report(c, "Other situation", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							}
						} else {
							if (StringUtils.isEmpty(thisEntry.getEffectiveTime()) || StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
								report(c, d, "Same Module Duplication " + thisEntry.getModuleId(), thisEntry.isActive(), thisEntry.getEffectiveTime(), thisEntry.toString(), thatEntry.isActive(), thatEntry.getEffectiveTime(), thatEntry.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							}
						}
					} 
				}
			}
		}
	}
	
	private void checkForModuleJumping(Concept c, Description d, RefsetMember rm) throws TermServerScriptException {
		//Only check recently changed refset members
		//Also skip anything we've already checked
		//Also skip anything already known to be INT
		if (!StringUtils.isEmpty(rm.getEffectiveTime()) 
				|| intReleaseRefsetMembers.containsKey(rm.getId())
				|| SnomedUtils.hasModule(rm, INTERNATIONAL_MODULES)) {
			return;
		}
		RefsetMember loadedRM = loadRefsetMember(rm.getId(), intReleaseBranch);
		intReleaseRefsetMembers.put(rm.getId(), loadedRM);
		if (loadedRM != null) {
			report(c, d, "LR switched to EXT module", rm);
		}
	}

	private RefsetMember hasModule(String[] targetModules, boolean matchLogic, RefsetMember... refsetMembers) {
		for (RefsetMember rm : refsetMembers) {
			if ( (matchLogic && SnomedUtils.hasModule(rm, targetModules)) ||
					(!matchLogic && SnomedUtils.hasNotModule(rm, targetModules))) {
				return rm;
			}
		}
		return null;
	}

}
