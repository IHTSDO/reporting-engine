package org.ihtsdo.termserver.scripting.delta.ms;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * MSSP-1837 Descriptions should have their en-gb langrefset entries copied to nz langrefset
 * MSSP-1838 Descriptions should have their en-gb langrefset entries copied to ie langrefset
 */
public class AlignLangRefsetEntriesToEN_GB extends DeltaGenerator implements ScriptConstants {

	String langRefsetId = "271000210107";  //New Zealand English language reference set (foundation metadata concept)
	String previousRelease = "MAIN/2021-01-31";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		AlignLangRefsetEntriesToEN_GB delta = new AlignLangRefsetEntriesToEN_GB();
		try {
			ReportSheetManager.targetFolderId = "1KGVf5QpzlohZsa0Cn9QDxs-fpaEtYaZC"; //Delta Generation / Managed Service
			delta.targetModuleId = "21000210109";  //NZ
			delta.runStandAlone = true;
			delta.newIdsRequired = false; 
			delta.additionalReportColumns = "FSN,SemTag,Severity,Action,LangRefset, Detail,";
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.getRF2Manager().flushFiles(true);  //Flush and Close
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				delta.descIdGenerator.finish();
			}
		}
	}
	
	public void process() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			boolean changeMade = false;
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				//Skip this description if it has an MS langrefentry
				if (d.getLangRefsetEntries(ActiveState.BOTH, langRefsetId).size() > 0) {
					continue;
				}
				//Recover the en-gb langrefset entry
				LangRefsetEntry enGb = d.getLangRefsetEntry(ActiveState.ACTIVE, GB_ENG_LANG_REFSET);
				
				//If it doesn't exist (probably because it's US spelling), then skip.
				if (enGb == null) {
					continue;
				}
				
				if (d.getEffectiveTime().compareTo("20210731") < 1) {
					report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Description dates back to " + d.getEffectiveTime());
				}
				
				//Clone enGb into target langrefset
				LangRefsetEntry l = enGb.clone(d.getDescriptionId(), false);
				
				//If this is a PT Synonym, does the country already have one?  
				//If the MS PT was previously also the GB PT, then we can downgrade
				//the MS PT to keep it aligned with GB.  Otherwise downgrade the GB one.
				if (d.isPreferred(GB_ENG_LANG_REFSET) && d.getType().equals(DescriptionType.SYNONYM)) {
					Description enMSPT = c.getPreferredSynonym(langRefsetId);
					if (enMSPT != null && !enMSPT.equals(d)) {
						List<LangRefsetEntry> previousGbLRSs = enMSPT.getLangRefsetEntries(ActiveState.BOTH, GB_ENG_LANG_REFSET);
						if (previousGbLRSs.size() > 0) {
							if(previousGbLRSs.size() > 1) {
								report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "More than one inactive gb pt langrefset member", d);
							}
							LangRefsetEntry previousGbLRS = previousGbLRSs.get(0);
							//Was this entry modified at the same time the new was created?
							if (!previousGbLRS.getEffectiveTime().equals(enGb.getEffectiveTime())) {
								report(c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Check effective times on new and previous GB PT entries", enGb + ",\n" + previousGbLRS, d);
							}
							//Pull this LRS from an old branch to see if it used to be PT
							RefsetMember previousGbLRSOnOldBranch = loadRefsetMember(previousGbLRS.getId(), previousRelease);
							if (previousGbLRSOnOldBranch == null || !previousGbLRSOnOldBranch.getField(LangRefsetEntry.ACCEPTABILITY_ID).equals(SCTID_PREFERRED_TERM)) {
								report(c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Check prior state of previous GB LRS", enGb + ",\n" + previousGbLRS, d);
							}
							report(c, Severity.LOW, ReportActionType.INFO, "Downgrading existing MS PT to acceptable, due to new GP PT, previously aligned", enMSPT);
							enMSPT.setAcceptability(langRefsetId, Acceptability.ACCEPTABLE);
							incrementSummaryInformation("LangRefset entries downgraded");
						} else {
							report(c, Severity.MEDIUM, ReportActionType.INFO, "Downgrading description to acceptable, due to existing MS PT", d);
							l.setAcceptabilityId(SCTID_ACCEPTABLE_TERM);
						}
					}
				}

				l.setModuleId(targetModuleId);
				l.setRefsetId(langRefsetId);
				d.addLangRefsetEntry(l);
				l.setDirty();
				c.setModified();
				changeMade = true;
				incrementSummaryInformation("LangRefset entries cloned");
				report(c, Severity.LOW, ReportActionType.LANG_REFSET_CLONED, l, d);
			}
			
			String changeAdvice = changeMade ? " Changes Made" : "";
			
			//Check that the engb PT is the same as the MS one.
			Description enGbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
			if (enGbPT == null) {
				//This is fine if this is an NZ concept.  But if it's not...
				if (!c.getModuleId().equals(targetModuleId)) {
					report(c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "Int concept missing GB PT?");
				}
			} else {
				Description enMSPT = c.getPreferredSynonym(langRefsetId);
				//If we don't have en enMSPT (eg 279026008 |Structure of surface region of upper abdomen (body structure)|), then
				//promote whatever description is the GB PT
				if (enMSPT == null) {
					enGbPT.setAcceptability(langRefsetId, Acceptability.PREFERRED);
					incrementSummaryInformation("LangRefset entries upgraded");
					report(c, Severity.HIGH, ReportActionType.LANG_REFSET_MODIFIED, "Concept missing PT.  Brought into alignement with GB.");
				} else if (!enGbPT.equals(enMSPT)) {
					report(c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Preferred Terms are not aligned." + changeAdvice, "GB: " + enGbPT + ",\nMS: " + enMSPT);
				}
			}
			
			//Have we left ourselves with 2 x MS PTs?
			List<Description> msPts = c.getDescriptions(langRefsetId, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
			if (msPts.size() > 1) {
				String descStr = msPts.stream()
						.map(d -> d.toString())
						.collect(Collectors.joining(",\n"));
				report(c, Severity.CRITICAL, ReportActionType.VALIDATION_CHECK, "2 x MS PTs now exist." + changeAdvice, descStr);
			}
			
			if (c.isModified()) {
				incrementSummaryInformation("Concepts modified");
				outputRF2(c, true);  //Will only output dirty fields.
			}
			
			getRF2Manager().flushFiles(false);
		}
	}

}
