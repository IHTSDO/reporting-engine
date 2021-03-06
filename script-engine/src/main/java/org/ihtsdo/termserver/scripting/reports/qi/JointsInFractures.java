package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
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
public class JointsInFractures extends TermServerReport {
	
	Concept subHierarchy;
	Set<Concept> boneStructures;
	Set<Concept> jointStructures;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		JointsInFractures report = new JointsInFractures();
		try {
			report.additionalReportColumns = "FSN, Joint Structures, Joint Also Bone Structure, Bone Structures, Other things";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runJointsInFracturesReport();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("125605004"); //|Fracture of bone (disorder)|
		boneStructures = gl.getDescendantsCache().getDescendentsOrSelf("272673000"); // |Bone structure (body structure)|
		jointStructures = gl.getDescendantsCache().getDescendentsOrSelf("39352004"); // |Joint structure (body structure)|
	}

	private void runJointsInFracturesReport() throws TermServerScriptException {
		for (Concept c : gl.getDescendantsCache().getDescendents(subHierarchy)) {
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
					report (c, joint, boneStructures.contains(joint)? "Y":"N", bones, other);
					//Most interesting case would be a joint without any sort of bone
					if (bones.isEmpty() && !boneStructures.contains(joint)) {
						warn("Interesting case: " + c + " with joint: " + joint);
					}
				}
			}
		}
	}
	
}
