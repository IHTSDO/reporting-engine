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
	public static final String NULL = "NULL";
	public static final int IDX_ENTIRE = 0;
	public static final int IDX_PART = 1;
	public static final int MAX_VALUE_COUNT = 2;
	public static String[] entirePart = new String[] { ENTIRE, PART };

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		SEPRefsetGenerator sep = new SEPRefsetGenerator();
		try {
			sep.setAdditionalHeaders(new String[] {"targetComponentId1", "targetComponentId2"});
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
		refsetShape = "cc";
		refsetName = "DoubleMap";
		refsetId = "1000005";
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
			if (fsnBase.contains(STRUCTURE)) {
				String[] entireAndPart = getEntireAndPart(thisConcept);
				RefsetMember rm = new RefsetMember(thisConcept, entireAndPart);
				refsetMembers.add(rm);
			}
		}
	}
	
	private String[] getEntireAndPart(Concept thisConcept) throws TermServerScriptException {
		//Find immediate active children of thisConcept, identify concepts with 'Entire' or 'Part' in FSN and warn if 
		//there is more than one Entire or Part
		String[] entireAndPart = new String[] { NULL, NULL };
		Set<Concept> immediateChildren = thisConcept.getDescendents(IMMEDIATE_CHILD,CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Concept thisChild : immediateChildren) {
			for (int i=0; i<MAX_VALUE_COUNT; i++) {
				if (thisChild.getFsn().contains(entirePart[i])) {
					if (!entireAndPart[i].equals(NULL)) {
						report (thisConcept, null, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Concept contained more than one " + entirePart[i]);
					} else {
						entireAndPart[i] = thisChild.getConceptId();
					}
				}
			}
		}
		if (entireAndPart[IDX_ENTIRE].equals(NULL)) {
			incrementSummaryInformation("Structure without Entire", 1);
			report (thisConcept, null, SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Structure concept has no 'Entire' counterpart");
		}
		return entireAndPart;
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
