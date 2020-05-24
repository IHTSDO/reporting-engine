package org.ihtsdo.termserver.scripting.reports.release;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * Generates a file of data based on a release so that we can do investigations 
 * between one release and the next using: TBC
 * We want to know a selection of information
 * 1.  Active / Inactive for all concepts
 * 2.  Is the concept an Intermediate Primitive?
 * 3.  Definition Status
 * 4.  Does the concept have SD descendants (inferred)
 * 5.  Does the concept have SD ancestors
 * 
 * See HistoricStatsAnalyzer for analysis.
  * */
public class HistoricStatsGenerator extends TermServerReport implements ReportClass {
	
	private static final String dataDir = "historic-data/";
	
	public HistoricStatsGenerator() {
	}
	
	public HistoricStatsGenerator(TermServerScript ts) {
		project = ts.getProject();
	}

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(HistoricStatsGenerator.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		suppressOutput = true;
		super.init(run);
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Historic Stats Generator")
				.withDescription("Generates a selection of information about all concepts for a given release")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		FileWriter fw = null;
		try {
			//Create the historic-data directory if required
			File dataDirFile = new File(dataDir);
			if (!dataDirFile.exists()) {
				info("Creating directory to store historic data analysis: " + dataDir);
				boolean success = dataDirFile.mkdir();
				if (!success) {
					throw new TermServerScriptException("Failed to create " + dataDirFile.getAbsolutePath());
				}
			}
			
			File f = new File(dataDir + project.getKey() + ".tsv");
			info("Creating dataFile: " + f.getAbsolutePath());
			f.createNewFile();
			fw = new FileWriter(f);
			
			TransitiveClosure tc = gl.generateTransativeClosure();
			
			debug ("Determining all IPs");
			Set<Concept> IPs = identifyIntermediatePrimitives(gl.getAllConcepts(), CharacteristicType.INFERRED_RELATIONSHIP);
		
			debug ("Outputting Data to " + f.getAbsolutePath());
			for (Concept c : gl.getAllConcepts()) {
				String active = c.isActive() ? "Y" : "N";
				String defStatus = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
				String hierarchy = getHierarchy(tc, c);
				String IP = IPs.contains(c) ? "Y" : "N";
				String sdDescendant = hasSdDescendant(tc, c);
				String sdAncestor = hasSdAncestor(tc, c);
				String relIds = getRelIds(c);
				String descIds = getDescIds(c);
				String axiomIds = getAxiomIds(c);
				String langRefSetIds = getLangRefsetIds(c);
				String inactivationIds = getInactivationIds(c);
				String histAssocIds = getHistAssocIds(c);
				ouput(fw, c.getConceptId(), active, defStatus, hierarchy, IP, sdDescendant, sdAncestor, relIds, descIds, axiomIds, langRefSetIds, inactivationIds, histAssocIds);
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		} finally {
			try {
				if (fw != null) {
					fw.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private String getRelIds(Concept c) {
		return c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)
		.stream()
		.map(r -> r.getId())
		.collect(Collectors.joining(","));
	}
	
	private String getDescIds(Concept c) {
		return c.getDescriptions(ActiveState.ACTIVE)
		.stream()
		.map(d -> d.getId())
		.collect(Collectors.joining(","));
	}
	
	private String getAxiomIds(Concept c) {
		return c.getAxiomEntries().stream()
		.filter(a -> a.isActive())
		.map(a -> a.getId())
		.collect(Collectors.joining(","));
	}
	
	private String getLangRefsetIds(Concept c) {
		List<String> langRefsetIds = new ArrayList<>();
		for (Description d : c.getDescriptions()) {
			for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
				langRefsetIds.add(l.getId());
			}
		}
		return String.join(",", langRefsetIds);
	}
	
	private String getInactivationIds(Concept c) {
		return c.getInactivationIndicatorEntries(ActiveState.ACTIVE)
		.stream()
		.map(i -> i.getId())
		.collect(Collectors.joining(","));
	}

	private String getHistAssocIds(Concept c) {
		return c.getAssociations(ActiveState.ACTIVE)
		.stream()
		.map(h -> h.getId())
		.collect(Collectors.joining(","));
	}

	private String getHierarchy(TransitiveClosure tc, Concept c) throws TermServerScriptException {
		if (!c.isActive() || c.equals(ROOT_CONCEPT) || c.getDepth() == NOT_SET) {
			return "";  //Hopefully the previous release will know
		} 
		
		if (c.getDepth() == 1) {
			return c.getConceptId();
		} 
		
		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDepth() == 1) {
				return a.getConceptId();
			}
		}
		throw new TermServerScriptException("Unable to determine hierarchy for " + c);
	}

	private void ouput(FileWriter fw, String... fields) throws IOException {
		StringBuffer sb = new StringBuffer();
		boolean isFirst = true;
		for (String field : fields) {
			if (!isFirst) {
				sb.append(TAB);
			} else {
				isFirst = false;
			}
			sb.append(field);
		}
		sb.append("\n");
		fw.write(sb.toString());
	}

	private String hasSdDescendant(TransitiveClosure tc, Concept c) throws TermServerScriptException {
		for (Long sctId : tc.getDescendants(c)) {
			Concept d = gl.getConcept(sctId);
			if (d.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return "Y";
			}
		}
		return "N";
	}

	private String hasSdAncestor(TransitiveClosure tc, Concept c) throws TermServerScriptException {
		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return "Y";
			}
		}
		return "N";
	}	
}
