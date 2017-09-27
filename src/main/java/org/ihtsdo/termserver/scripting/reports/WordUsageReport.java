package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;

/**
 * Reports all terms that contain the specified text
 */
public class WordUsageReport extends TermServerScript{
	
	Map<String, Usage> wordUsage = new LinkedHashMap<String, Usage>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		WordUsageReport report = new WordUsageReport();
		try {
			Description.padTerm = true; //Pad terms with spaces to assist whole word matching.
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.loadWords();
			report.reportWordUsage();
		} catch (Exception e) {
			println("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void loadWords() throws IOException {
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		println ("Loading words of interest from " + inputFile);
		for (String line : lines) {
			wordUsage.put(line, new Usage());
		}
	}

	private void reportWordUsage() throws TermServerScriptException {
		println ("Loading words of interest from " + inputFile);
		Collection<Concept> concepts = GraphLoader.getGraphLoader().getAllConcepts();
		for (Map.Entry<String, Usage> wordUsageEntry : wordUsage.entrySet()) {
			//We'll add a space to the word to ensure we don't have partial matches
			String word = " " + wordUsageEntry.getKey() + " ";
			print("Processing word: " + word);
			for (Concept c : concepts) {
				boolean conceptFeaturesWord = false;
				if (c.isActive()) {
					for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
						if (StringUtils.containsIgnoreCase(d.getTerm(), word)) {
							wordUsageEntry.getValue().instances++;
							conceptFeaturesWord = true;
						}
					}
				}
				if (conceptFeaturesWord) {
					wordUsageEntry.getValue().registerUsage(c);
				}
			}
			println("- " + wordUsageEntry.getValue().instances);
			report (word, wordUsageEntry.getValue());
		}
		addSummaryInformation("Concepts checked", concepts.size());
	}

	protected void report (String word, Usage usage) {
		String line = 	QUOTE + word + QUOTE_COMMA + 
						usage.instances + COMMA_QUOTE + 
						usage.reportTags() + QUOTE_COMMA_QUOTE +
						usage.reportExamples() + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Word, Total Instance, Distribution, Examples");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
	
	class Usage {
		int instances = 0;
		Multiset<String> tags = HashMultiset.create();
		List<String> examples = new ArrayList<String>();

		void registerUsage(Concept c) {
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			tags.add(semTag);
			if (examples.size() < 5) {
				examples.add(c.toString());
			}
		}
		
		String reportTags() {
			String tagsInstances = "";
			for (String tag : tags.elementSet()) {
				tagsInstances += tag + "-" + tags.count(tag) + " " ;
			}
			return tagsInstances;
		}
		
		String reportExamples() {
			String examplesStr = "";
			for (String example : examples) {
				examplesStr += example + ","; 
			}
			return examplesStr;
		}
	}
}
