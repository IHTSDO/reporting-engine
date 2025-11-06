package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-10
 * Reports all joints appearing as finding sites in fracture of bone
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JointsInFractures extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(JointsInFractures.class);

	Concept subHierarchy;
	Set<Concept> boneStructures;
	Set<Concept> jointStructures;
	
	public static void main(String[] args) throws TermServerScriptException {
		JointsInFractures report = new JointsInFractures();
		try {
			report.additionalReportColumns = "FSN, Joint Structures, Joint Also Bone Structure, Bone Structures, Other things";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runJointsInFracturesReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("125605004"); //|Fracture of bone (disorder)|
		boneStructures = gl.getDescendantsCache().getDescendantsOrSelf("272673000"); // |Bone structure (body structure)|
		jointStructures = gl.getDescendantsCache().getDescendantsOrSelf("39352004"); // |Joint structure (body structure)|
	}

	private void runJointsInFracturesReport() throws TermServerScriptException {
		for (Concept c : gl.getDescendantsCache().getDescendants(subHierarchy)) {
			//For each active concept, list all the things that a types of joint, 
			//types of bone, and other things separately
			Set<Concept> joints = new HashSet<>();
			String bones = "", other ="";
			if (c.isActive()) {
				for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, FINDING_SITE, ActiveState.ACTIVE)) {
					if (jointStructures.contains(r.getTarget())) {
						joints.add(r.getTarget());
						incrementSummaryInformation("Joints Found");
					} else if (boneStructures.contains(r.getTarget())) {
						bones += bones.isEmpty()? r.getTarget() : "\n" + r.getTarget();
						incrementSummaryInformation("Bones Found");
					} else {
						other += other.isEmpty()? r.getTarget() : "\n" + r.getTarget();
						incrementSummaryInformation("Other things Found");
					}
				}
				
				for (Concept joint : joints) {
					//If the joint is also a bone structure, add an indicator
					report(c, joint, boneStructures.contains(joint)? "Y":"N", bones, other);
					//Most interesting case would be a joint without any sort of bone
					if (bones.isEmpty() && !boneStructures.contains(joint)) {
						LOGGER.warn("Interesting case: " + c + " with joint: " + joint);
					}
				}
			}
		}
	}
	
}
