package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI-2
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * */
public class IntermediatePrimitivesFromSubHierarchy extends TermServerReport{
	
	Concept subHierarchy;
	public Set<Concept> intermediatePrimitives = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		IntermediatePrimitivesFromSubHierarchy report = new IntermediatePrimitivesFromSubHierarchy();
		try {
			report.additionalReportColumns = "ProximalPrimitiveParent, isIntermediate, StatedAttributes, StatedRoleGroups, InferredRoleGroups, StatedParents";
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.postInit();
			report.reportIntermediatePrimitives();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}
	
	private void postInit() throws TermServerScriptException {
		//setSubHierarchy(gl.getConcept("46866001")); // |Fracture of lower limb (disorder)|
		setSubHierarchy(gl.getConcept("125605004")); // QI-2 |Fracture of bone (disorder)|
		//setSubHierarchy(gl.getConcept("128294001")); // QI-7 |Chronic inflammatory disorder (disorder)|
	}
	
	public void setSubHierarchy(Concept subHierarchy) {
		this.subHierarchy = subHierarchy;
		intermediatePrimitives = new HashSet<>();
	}

	public void reportIntermediatePrimitives() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			//We're only interested in fully defined concepts
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				List<Concept> proxPrimParents = determineProximalPrimitiveParents(c);
				//Do those parents themselves have sufficiently defined ancestors ie making them intermediate primitives
				for (Concept thisPPP : proxPrimParents) {
					boolean isIntermediate = false;
					if (containsFdConcept(ancestorsCache.getAncestors(thisPPP))) {
						isIntermediate = true;
						incrementSummaryInformation("Intermediate Primitives reported");
						incrementSummaryInformation(thisPPP.toString());
						intermediatePrimitives.add(thisPPP);
					} else {
						incrementSummaryInformation("Safely modelled count");
					}
					
					if (!quiet) {
						report (c, thisPPP.toString(), 
								isIntermediate?"Yes":"No", 
								Integer.toString(countAttributes(c, CharacteristicType.STATED_RELATIONSHIP)),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false).size()),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false).size()),
								getParentsWithDefnStatus(c)
								);
					}
				}
				incrementSummaryInformation("FD Concepts checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	}

	private String getParentsWithDefnStatus(Concept c) {
		StringBuffer sb = new StringBuffer();
		boolean isFirst = true;
		for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
			if (!isFirst) {
				sb.append(", ");
			} else { 
				isFirst = false;
			}
			sb.append("[")
			.append(SnomedUtils.translateDefnStatus(p.getDefinitionStatus()))
			.append("] ")
			.append(p.toString());
		}
		return sb.toString();
	}

	private int countAttributes(Concept c, CharacteristicType charType) {
		int attributes = 0;
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributes++;
			}
		}
		return attributes;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}
	
}
