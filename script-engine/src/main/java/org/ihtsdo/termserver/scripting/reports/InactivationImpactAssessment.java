package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.util.concurrent.AtomicLongMap;

/**
 * DEVICES-92
 */
public class InactivationImpactAssessment extends TermServerReport implements ReportClass {
	
	static public String REFSET_ECL = "(< 446609009 |Simple type reference set| OR < 900000000000496009 |Simple map type reference set|) MINUS 900000000000497000 |CTV3 simple map reference set (foundation metadata concept)|";
	private static String CONCEPT_INACTIVATIONS = "Concepts to inactivate";
	private Collection<Concept> referenceSets;
	private List<Concept> emptyReferenceSets;
	private List<Concept> outOfScopeReferenceSets;
	private AtomicLongMap<Concept> refsetSummary = AtomicLongMap.create();
	private static int CHUNK_SIZE = 200;
	
	private List<Concept> inactivatingConcepts = new ArrayList<>();
	private List<String> inactivatingConceptIds = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(CONCEPT_INACTIVATIONS, "467606002,468111004,109145005,469430002,469300009,469431003,470480002,469205002,468962002,470305006,701208004,462649005,463089007,462724002,701542009,705664002,700754003,464608009,733716003,465199002,109148007,465972000,463598003,466469009,109152007,706073002,466745006,467054009,700954006,337485002,711624001,702262002,109154008,704702003,467239009,701271009,468088001,467682003,716751009,718269005,466422004,109156005,700973005,468175001,467509003,467472006,467689007,712479001,705663008,468122000,467235003,467211008,706537000,701302000,764181009,718620003,718268002,706088007,467541009,716689009,701259003,109159003,109164004,468050003,356328002,706063004,706087002,468065002,467759009,467669006,738554003,702013007,702014001,109166002,701721005,467461001,467800006,467470003,467354001,467642005,701008006,701009003,700710009,109168001,467595004,717711001,467549006,700860007,468754002,723699006,468505007,714056006,468218000,469132003,706387008,734941008,468338002,468364008,468620007,706388003,468470001,468533007,468422007,468444002,705665001,468434004,468188003,788130003,707725007,468576009,736296007,706086006,702257003,468430008,705292003,700758000,468707001,468352000,719849002,468391005,726650002,706569009,468209002,468795004,468681008,468186004,468742002,468809005,468260001,714732001,714733006,702198002,705470004,702047005,701268001,468797007,701693005,468802001,713961003,701304004,717110005,701270005,75750007,702311002,470146007,469404001,469977000,469752007,701573008,701907003,701426004,701427008,700759008,706064005,470577000,470184001,701723008,717117008,701030007,700928003,701044001,736112003,705660006,700760003,470402008,701036001,109177008,701066008,462252007,733689006,701929004,109178003,462264004,713780006,704815000,723702004,462337002,462195002,701333003,711630001,356337002,733699001,733701001,733700000,356296007,109179006,469983002,469611007,470450007,470544004,470384007,720867001,462773006,469672008,469859009,711620005,723378002,469873000,702044003,724405006,714064000,701853009,701847009,470066001,469424002,701235008,704816004,469583008,469563007,469523006,469199009,713960002,469202004,701727009,705661005,700761004,470279003,701294005,701198000,700651001,462444007,462398005,463024000,462776003,463054005,462847009,470078002,75187009,718400005,469309005,469991006,469508001,469729008,718267007,469449003,734260005,736870001,706772000,109180009,463725006,701520008,701521007,109181008,701911009,9817005,464453004,701697006,701991005,723700007,464870000,464366003,702149006,705497006,700762006,701747001,701048003,701061003,713969001,705548004,700763001,705667009,109182001,705287000,463739006,463734001,736875006,463570001,463869005,705907009,700799006,700800005,713840009,705919001,705666000,701149009,109183006,704805006,704749004,734984009,704751000,704757001,464737003,464276003,464771007,464559003,464530003,462287007,69670004,719930000,764177009,462399002,462615002,462177000,702213002,701987000,706155003,702187003,711629006,462693005,712472005,462254008,717358007,43001000,718263006,711619004,701424001,701425000,463667009,701694004,463731009,463533005,463462009,109184000,701224003,702093008,704944004,462687002,718266003,465904000,465246004,465605002,109185004,467063006,466332005,466430003,701619008,701043007,718262001,466171001,465693000,466058001,465674002,700648008,738558000,465412002,109186003,465835007,465215003,701961000,465214004,465221004,717109000,713932004,704825005,705220000,706229007,700919004,73276008,109187007,701642001,109188002,466142002,717307005,767709008,738669007,465403004,705659001,465415000,717151007,466011009,465596002,700769002,700735003,466115003,465950002,701956002,108882005,466678008,385387009,464929003,701488000,464945001,465767000,701668008,109189005,465528009,465357007,465288008,706066007,465942006,466379005,702046001,711327000,705528006,712473000,109176004,464804008,704779006,705537006,464387008,464820003,464883004,711478006,713962005,701635001,705662003,465825001,716690000,465222006,466463005,734945004,466906009,109190001,467090002,700932009,466270007,466787008,734925007,734926008,717294009");
		TermServerReport.run(InactivationImpactAssessment.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc reports
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		referenceSets = findConcepts(REFSET_ECL);
		removeEmptyAndNoScopeRefsets();
		info ("Recovered " + referenceSets.size() + " simple reference sets and maps");

		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Issue, Detail, Additional Detail"};
		String[] tabNames = new String[] {	
				"Impact Details"};
		
		for (String inactivatingConceptId : jobRun.getMandatoryParamValue(CONCEPT_INACTIVATIONS).split(",")) {
			inactivatingConceptId = inactivatingConceptId.trim();
			Concept c = gl.getConcept(inactivatingConceptId);
			if (c.isActive()) {
				inactivatingConceptIds.add(inactivatingConceptId);
				inactivatingConcepts.add(c);
			} else {
				report (c, " is already inactive");
			}
		}
		super.postInit(tabNames, columnHeadings, false);
	}

	private void removeEmptyAndNoScopeRefsets() throws TermServerScriptException {
		emptyReferenceSets = new ArrayList<>();
		outOfScopeReferenceSets = new ArrayList<>();
		for (Concept refset : referenceSets) {
			if (!inScope(refset)) {
				outOfScopeReferenceSets.add(refset);
				continue;
			}
			if (getConceptsCount("^" + refset) == 0) {
				emptyReferenceSets.add(refset);
			} else {
				refsetSummary.put(refset, 0);
			}
			try { Thread.sleep(1 * 1000); } catch (Exception e) {}
		}
		referenceSets.removeAll(emptyReferenceSets);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(CONCEPT_INACTIVATIONS).withType(Type.STRING).withMandatory()
					.withDescription("List of concept ids to inactivated, comma separated")
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Inactivation Impact Assessment")
				.withDescription("This report takes in a list of concpts to be inactivated and " +
				"reports if they're current used as attribute values or parents of other concepts " +
				" (not being inactivated) or in refsets or as historical association targets")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		checkHighVolumeUsage();
		checkChildInactivation();
		checkRefsetUsage();
		checkAttributeUsage();
		checkHistoricalAssociations();
	}

	private void checkChildInactivation() throws TermServerScriptException {
		for (Concept c : inactivatingConcepts) {
			for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
				//If we're not also inactivating the child, that could be a problem
				if (!inactivatingConcepts.contains(child)) {
					report (c, "has child not scheduled for inactivation", child);
				}
			}
		}
	}

	private void checkAttributeUsage() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive() && !inactivatingConcepts.contains(c)) {
				for (Relationship r : c.getRelationships()) {
					if (r.isActive() && inactivatingConcepts.contains(r.getTarget())) {
						report (r.getTarget(), "used as attribute target value", c, r);
					}
				}
			}
		}
	}

	private void checkHistoricalAssociations() throws TermServerScriptException {
		for (Concept c : inactivatingConcepts) {
			for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
				report (c, "used as target of historical association", gl.getConcept(assoc.getReferencedComponentId()), assoc);
			}
		}
	}

	private void checkRefsetUsage() throws TermServerScriptException {
		debug ("Checking " + inactivatingConceptIds.size() + " inactivating concepts against " + referenceSets.size() + " refsets");
		for (Concept refset : referenceSets) {
			int conceptsProcessed = 0;
			do {
				String subsetList = "";
				for (int i = 0; i < CHUNK_SIZE && conceptsProcessed < inactivatingConceptIds.size(); i++) {
					if (i > 0) {
						subsetList += " OR ";
					}
					subsetList += inactivatingConceptIds.get(conceptsProcessed);
					conceptsProcessed++;
				}
				String ecl = "^" + refset.getId() + " AND ( " + subsetList + " )"; 
				for (Concept c : findConcepts(ecl)) {
					report (c, "active in refset", refset.getPreferredSynonym());
				}
				try {
					Thread.sleep(1 * 200);
				} catch (Exception e) {}
			} while (conceptsProcessed < inactivatingConceptIds.size());
		}
	}

	private void checkHighVolumeUsage() throws TermServerScriptException {
		debug ("Checking " + inactivatingConceptIds.size() + " inactivating concepts against High Usage SCTIDs");
		String fileName = "resources/HighVolumeSCTIDs.txt";
		debug ("Loading " + fileName );
		try {
			List<String> lines = Files.readLines(new File(fileName), Charsets.UTF_8);
			for (String line : lines) {
				String id = line.split(TAB)[0];
				if (inactivatingConceptIds.contains(id)) {
					report (gl.getConcept(id), "High Volume Usage (UK)");
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
	}

}