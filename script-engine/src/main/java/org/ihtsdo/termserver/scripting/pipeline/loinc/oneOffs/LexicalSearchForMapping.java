package org.ihtsdo.termserver.scripting.pipeline.loinc.oneOffs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.loinc.LoincScript;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class LexicalSearchForMapping extends LoincScript {

	Map<String, UnmappedPart> unmappedPartMap = new HashMap<>();
	List<LexicalConcept> lexicalConcepts = new ArrayList<>();

	public static void main(String[] args) throws TermServerScriptException, IOException {
		new LexicalSearchForMapping().run(args);
	}

	public void run(String[] args) throws TermServerScriptException, IOException {
		try {
			getGraphLoader().setExcludedModules(new HashSet<>());
			getArchiveManager().setLoadOtherReferenceSets(true);
			getArchiveManager().setRunIntegrityChecks(false);
			getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);  //Needed for working out if we're deleteing or inactivating
			init(args);
			loadProjectSnapshot(false);
			postInit();
			loadSupportingInformation();
			loadUnmappedParts("/Users/peter/GDrive/018_Loinc/2025/map_work/unmapped_parts.txt");
			prepareLexicalConcepts(SUBSTANCE);
			prepareLexicalConcepts(ORGANISM);
			searchForLexicalMatches();
		} finally {
			finish();
		}
	}

	private void prepareLexicalConcepts(Concept hierarchy) throws TermServerScriptException {
		for (Concept c : hierarchy.getDescendants(NOT_SET)) {
			LexicalConcept lc = new LexicalConcept();
			lc.concept = c;
			lc.terms = new ArrayList<>();
			lc.tokenizedTerms = new HashMap<>();
			lc.allTokens = new HashSet<>();

			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				String term = d.getTerm();
				if (d.getType().equals(DescriptionType.FSN)) {
					term = SnomedUtilsBase.deconstructFSN(term)[0];
				}
				term = normalizeTerm(term);
				lc.terms.add(term);
				String[] tokens = term.split(" ");
				for (String token : tokens) {
					lc.allTokens.add(token);
					lc.tokenizedTerms.computeIfAbsent(d, k -> new HashSet<>()).add(token);
				}
			}
			lexicalConcepts.add(lc);
		}
	}

	private void searchForLexicalMatches() throws TermServerScriptException {
		for (UnmappedPart unmappedPart : unmappedPartMap.values()) {
			//We'll work through a set of successively more permissive matches
			if (!mapUsingExactMatch(unmappedPart)) {
				if (!mapUsingPartialMatch(unmappedPart, true)) {
					if (!mapUsingPartialMatch(unmappedPart, false)) {
						report(SECONDARY_REPORT, unmappedPart.asArray());
					}
				}
			}
		}
	}

	private boolean mapUsingExactMatch(UnmappedPart unmappedPart) throws TermServerScriptException  {
		for (LexicalConcept lc : lexicalConcepts) {
			for (String term : lc.terms) {
				if (term.equals(unmappedPart.getPartName())) {
					// We have a match
					report(PRIMARY_REPORT, unmappedPart.asArray(), "Exact Match", display(lc.concept));
					return true;
				}
			}
		}
		return false;
	}

	private String display(Concept concept) {
		return concept.toString() + "\n"+
				SnomedUtils.getDescriptionsToString(concept);
	}

	private boolean mapUsingPartialMatch(UnmappedPart unmappedPart, boolean b) {
		return false;
	}


	public void postInit() throws TermServerScriptException {
		String[] tabNames = {
				"Unmapped Part Suggestions",
				"Hopeless Cases"
		};

		String[] columnHeadings = {
				"Part Number, Part Name, Part Type, High Priority, Method Used, Matches",
				"Part Number, Part Name, Part Type, High Priority"
		};
		super.postInit(GFOLDER_LOINC, tabNames, columnHeadings, false);
	}

	protected void loadUnmappedParts(String filePath) throws TermServerScriptException, IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			while ((line = br.readLine()) != null) {
				// Skip empty lines
				if (line.trim().isEmpty()) continue;

				String[] tokens = line.split("\t");
				if (tokens.length != 4) {
					System.err.println("Skipping malformed line: " + line);
					continue;
				}

				String partNum = tokens[0];
				String partName = tokens[1];
				String partType = tokens[2];
				boolean highPriority = tokens[3].equalsIgnoreCase("Y");

				UnmappedPart unmappedPart = new UnmappedPart(partNum, partName, partType, highPriority);
				unmappedPartMap.put(partNum, unmappedPart);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void importPartMap() throws TermServerScriptException {
		throw new UnsupportedOperationException();
	}

	@Override
	public TemplatedConcept getAppropriateTemplate(ExternalConcept externalConcept) throws TermServerScriptException {
		return null;
	}

	public class UnmappedPart {
		private String partNum;
		private String partName;
		private String partType;
		private boolean highPriority;

		public UnmappedPart(String partNum, String partName, String partType, boolean highPriority) {
			this.partNum = partNum;
			this.partName = normalizeTerm(partName);
			this.partType = partType;
			this.highPriority = highPriority;
		}

		// Getters and toString() for debugging
		public String getPartNum() { return partNum; }
		public String getPartName() { return partName; }
		public String getPartType() { return partType; }
		public boolean isHighPriority() { return highPriority; }

		@Override
		public String toString() {
			return partNum + " | " + partName + " | " + partType + " | High Priority: " + highPriority;
		}

		public String[] asArray() {
			return new String[] {
				partNum,
				partName,
				partType,
				highPriority ? "Y" : "N"
			};
		}
	}

	private String normalizeTerm(String text) {
		return text.replaceAll("[^a-zA-Z0-9 ]", " ").toLowerCase();
	}

	class LexicalConcept {
		Concept concept;
		List<String> terms;
		Map<Description, Set<String>> tokenizedTerms;
		Set<String> allTokens;

	}
}
