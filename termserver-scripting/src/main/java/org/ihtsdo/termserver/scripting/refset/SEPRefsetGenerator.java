package org.ihtsdo.termserver.scripting.refset;

import java.io.IOException;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class SEPRefsetGenerator extends RefsetGenerator{
	
	String subHierchyStr = "91723000";  // |Anatomical structure (body structure)|
	public static final String STRUCTURE = "structure";  //Currently a lot of X structure as well as Structure Of X so compare lower case
	public static final String ENTIRE = "Entire ";
	public static final String PART = "Part ";
	public static final int IDX_ENTIRE = 0;
	public static final int IDX_PART = 1;
	public static final int MAX_VALUE_COUNT = 2;
	public static String[] targetTermKeywords = new String[] {	ENTIRE.toLowerCase(),
																PART.toLowerCase(),
																STRUCTURE.trim().toLowerCase() };
	public static String[] entirePart = new String[] { ENTIRE, PART };
	
	public static String ENTIRE_REFSET_SCTID = "1000005";
	public static String PART_REFSET_SCTID = "2000005";
	public static String [] refsetSCTIDs = new String[] { ENTIRE_REFSET_SCTID, PART_REFSET_SCTID };

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		SEPRefsetGenerator sep = new SEPRefsetGenerator();
		try {
			sep.setAdditionalHeaders(new String[] {"targetComponentId"});
			sep.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			sep.loadProjectSnapshot(true);  //Just FSN
			//We won't incude the project export in our timings
			sep.startTimer();
			sep.process();
		} finally {
			sep.finish();
		}
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		effectiveDate = "20170731";
		refsetShape = "c";
		refsetFileName = "AssociationReference";
		super.init(args);
	}

	@Override
	void generateMembers() throws TermServerScriptException {
		//Work through the subHierarchy, identifying concepts containing "Structure" in their FSN
		//Then find immediate child Entire and Part to add to that row in the SEP refset
		GraphLoader gl = GraphLoader.getGraphLoader();
		Concept subHierarchy = gl.getConcept(subHierchyStr);
		Set<Concept> allConcepts = subHierarchy.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Concept thisConcept : allConcepts) {
			String fsnBase = SnomedUtils.deconstructFSN(thisConcept.getFsn())[0].toLowerCase();
			//Check we don't have more than one of our target keywords. THAT would be confusing!
			if (hasMultipleKeywords(fsnBase)) {
				report (thisConcept, null, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Concept contained multiple keywords");
			} else if (fsnBase.contains(STRUCTURE)) {
				String[] entireAndPart = getEntireAndPart(thisConcept);
				for (int i=0; i< entireAndPart.length; i++){
					if (entireAndPart[i] != null) {
						//Only need a single value for a simple map
						String[] additionalValues = new String[] { entireAndPart[i] };
						RefsetMember rm = new RefsetMember(refsetSCTIDs[i], thisConcept, additionalValues);
						refsetMembers.add(rm);
					}
				}
			}
		}
	}
	
	private boolean hasMultipleKeywords(String fsnBase) {
		int keywordsFound = 0;
		String searchIn = fsnBase.toLowerCase();
		for (String keyword : targetTermKeywords ) {
			if (searchIn.contains(keyword)) {
				keywordsFound++;
			}
		}
		return (keywordsFound > 1);
	}

	private String[] getEntireAndPart(Concept thisConcept) throws TermServerScriptException {
		//Find immediate active children of thisConcept, identify concepts with 'Entire' or 'Part' in FSN and warn if 
		//there is more than one Entire or Part
		String[] entireAndPart = new String[] { null, null };
		Set<Concept> immediateChildren = thisConcept.getDescendents(IMMEDIATE_CHILD,CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Concept thisChild : immediateChildren) {
			for (int i=0; i<MAX_VALUE_COUNT; i++) {
				if (thisChild.getFsn().contains(entirePart[i])) {
					if (!(entireAndPart[i] == null)) {
						report (thisConcept, null, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Concept contained more than one " + entirePart[i]);
					} else {
						entireAndPart[i] = thisChild.getConceptId();
					}
				}
			}
		}
		if (entireAndPart[IDX_ENTIRE] == null) {
			incrementSummaryInformation("Structure without Entire", 1);
			report (thisConcept, null, SEVERITY.MEDIUM, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Structure concept has no 'Entire' counterpart");
		}
		return entireAndPart;
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
