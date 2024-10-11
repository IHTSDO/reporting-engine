package org.ihtsdo.termserver.scripting.reports;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ConceptsDefinedUsingConcepts extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptsDefinedUsingConcepts.class);
	public static final String DEFINING_CONCEPTS = "Defining Concepts";

	private List<String> definingConcepts = new ArrayList<>();

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, Object> params = new HashMap<>();
		params.put(DEFINING_CONCEPTS, "418920007,259105007,419343004,419665005,418215000,64896002" +
				"372633005,373265006,373266007,372794006,387424000,373249005" +
				"372482001,372813008,372862008,419927001,255632006,372720008" +
				"372892004,372641005,386982002,373267003,372785000,425586005" +
				"372688009,372582004,372723005,372783007,373218000,373293005" +
				"418416001,2537003,372791003,373239007,373282008,373209002" +
				"117499009,418791006,373289004,372618002,372615004,373745000" +
				"372616003,373696007,420100005,419485001,372872006,255640000" +
				"438441000124102,426607001,373301002,438571000124109,372580007,418497006" +
				"418540006,438581000124107,373530005,33435008,79522001,372617007" +
				"372619005,373263004,373247007,24570008,255755001,418407000" +
				"9680003,372752008,372620004,373335004,372740001,372583009" +
				"438591000124105,372635003,105925009,438601000124102,373260001,373269000" +
				"373286006,373290008,373278006,372855004,372693007,373746004" +
				"438611000124104,385420005,373564009,363719008,311758007,289122001" +
				"373230006,373307003,73106004,8987006,373686003,373526007" +
				"372599003,417901007,373250005,264299001,419148000,373782009" +
				"418377002,372684006,311942001,418516003,373222005,372695000" +
				"410942007,37758008,61789006,372845003,373380003,418299007" +
				"421299002,346415002,767417004,372849009,438621000124107,438451000124100" +
				"386127005,19209005,373258003,373775005,373288007,373504006" +
				"418974001,373469002,229006007,26070000,425924000,259699009" +
				"373708006,346641004,372681003,71818000,87897005,439255000" +
				"419517004,372585002,439304005,372586001,372861001,373244000" +
				"372823004,373213009,33278000,427324005,426722004,438631000124105" +
				"438641000124100,406429003,418596004,372800002,425718001,373473004" +
				"823054000,373477003,372691009,16923002,419909004,438651000124103" +
				"264316006,373751005,427162002,109010000,373460003,419478000" +
				"372822009,438661000124101,372607002,412250002,438681000124106,373295003" +
				"438461000124103,372700007,372665008,372790002,373453009,819018009" +
				"372792005,410937004,438471000124105,372668005,417888000,59545008" +
				"418804003,116121000,372600000,372753003,418149003,387459000" +
				"418183005,395849005,419151007,373698008,419963001,417926003" +
				"46921009,28069006,373545003,418760000,373272007,438481000124108" +
				"372660003,372654003,372614000,373565005,372666009,372894003" +
				"438671000124108,61010005,373333006,24686008,14443002,372802005" +
				"419241000,373219008,373287002,419278002,372857007,713540004" +
				"372701006,373236000,372627001,406440006,372631007,372558009" +
				"372480009,373270004,372722000,39552000,372788003,1155735007" +
				"373206009,372632000,43585000,117177007,439011000124102,373498003" +
				"303530004,372884008,372576002,438491000124106,372747003,372742009" +
				"49067007,373520001,789708003,45604009,419370006,372611008" +
				"373253007,372590004,372758007,372787008,870406003,418681006" +
				"438501000124103,438511000124100,438521000124108,438531000124106,438541000124101,438551000124104" +
				"438561000124102,438431000124107,35079003,255677008,419098001,106181007");
		TermServerReport.run(ConceptsDefinedUsingConcepts.class, params, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, Def Status, Type, Target";
		super.init(run);

		if (StringUtils.isEmpty(run.getParamValue(DEFINING_CONCEPTS))) {
			throw new TermServerScriptException("No defining concepts specified");
		}

		Arrays.stream(run.getParamValue(DEFINING_CONCEPTS).split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.forEach(s -> definingConcepts.add(s));
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(DEFINING_CONCEPTS).withType(JobParameter.Type.STRING).withMandatory()
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Concepts Defined Using Concepts")
				.withDescription("This report lists concepts that use any of the specified list of concepts in their stated model attributes (ie IS-A relationships are ignored).  Specify concepts comma delimited.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		List<Concept> conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = new ArrayList<>(findConcepts(subsetECL));
		}
		conceptsOfInterest.sort(Comparator.comparing(Concept::getFsnSafely));
		
		for (Concept c : conceptsOfInterest) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getType().equals(IS_A)) {
					continue;
				}

				if (definingConcepts.contains(r.getType().getConceptId())
					|| (!r.isConcrete() && definingConcepts.contains(r.getTarget().getConceptId()))) {
					report (c, SnomedUtils.translateDefnStatus(c.getDefinitionStatus()), r.getType(), r.getTarget());
					countIssue(c);
				}
			}
		}
	}

}
