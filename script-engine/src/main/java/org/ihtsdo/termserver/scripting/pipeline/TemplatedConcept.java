package org.ihtsdo.termserver.scripting.pipeline;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants.ProcessingFlag;

import java.util.*;
import java.util.stream.Collectors;

public abstract class TemplatedConcept implements ScriptConstants, ConceptWrapper {

	protected TemplatedConcept(ExternalConcept externalConcept) {
		this.externalConcept = externalConcept;
	}

	public enum IterationIndicator { NEW, REMOVED, RESURRECTED, MODIFIED, UNCHANGED }

	protected static ContentPipelineManager cpm;
	protected static GraphLoader gl;

	protected static Set<String> partNumsMapped = new HashSet<>();
	protected static Set<String> partNumsUnmapped = new HashSet<>();
	
	protected static int mapped = 0;
	protected static int unmapped = 0;
	protected static int skipped = 0;
	protected static int conceptsModelled = 0;

	protected Concept existingConcept = null;
	protected boolean existingConceptHasInactivations = false;

	protected List<String> differencesFromExistingConcept = new ArrayList<>();

	protected IterationIndicator iterationIndicator;

	protected ExternalConcept externalConcept;
	protected Concept concept;
	
	private Set<ProcessingFlag> processingFlags = new HashSet<>();
	
	protected Map<String, Concept> typeMap = new HashMap<>();
	private String preferredTermTemplate;
	
	protected Map<String, String> slotTermMap = new HashMap<>();
	protected Map<String, String> slotTermAppendMap = new HashMap<>();

	public static void reportStats(int tabIdx) throws TermServerScriptException {
		cpm.report(tabIdx, "");
		cpm.report(tabIdx, "Parts mapped", mapped);
		cpm.report(tabIdx, "Parts unmapped", unmapped);
		cpm.report(tabIdx, "Parts skipped", skipped);
		cpm.report(tabIdx, "Unique PartNums mapped", partNumsMapped.size());
		cpm.report(tabIdx, "Unique PartNums unmapped", partNumsUnmapped.size());
	}

	public ExternalConcept getExternalConcept() {
		return externalConcept;
	}

	public String getExternalIdentifier() {
		return externalConcept.getExternalIdentifier();
	}

	public boolean isHighUsage() {
		return externalConcept.isHighUsage();
	}

	public boolean isHighestUsage() {
		return externalConcept.isHighestUsage();
	}
	
	public IterationIndicator getIterationIndicator() {
		return iterationIndicator;
	}
	
	protected abstract String getSemTag();

	public void setIterationIndicator(IterationIndicator iterationIndicator) {
		this.iterationIndicator = iterationIndicator;
	}

	public Concept getExistingConcept() {
		return existingConcept;
	}

	public void setExistingConcept(Concept existingConcept) {
		this.existingConcept = existingConcept;
	}

	public String getDifferencesFromExistingConcept() {
		return differencesFromExistingConcept.stream().collect(Collectors.joining(",\n"));
	}

	public void addDifferenceFromExistingConcept(String differenceFromExistingConcept) {
		this.differencesFromExistingConcept.add(differenceFromExistingConcept);
	}

	public boolean existingConceptHasInactivations() {
		return existingConceptHasInactivations;
	}

	public void setExistingConceptHasInactivations(boolean existingConceptHasInactivations) {
		this.existingConceptHasInactivations = existingConceptHasInactivations;
	}

	protected String bracket(String str) {
		return "[" + str + "]";
	}

	@Override
	public String getWrappedId() {
		return getConcept().getId();
	}

	@Override
	public Concept getConcept() {
		return concept;
	}
	
	@Override
	public void setConcept(Concept concept) {
		this.concept = concept;
	}
	
	public void addProcessingFlag(ProcessingFlag flag) {
		processingFlags.add(flag);
	}
	
	public boolean hasProcessingFlag(ProcessingFlag flag) {
		return processingFlags.contains(flag);
	}

	public void populateTemplate() throws TermServerScriptException {
		if (isHighestUsage()) {
			cpm.incrementSummaryCount(ContentPipelineManager.HIGHEST_USAGE_COUNTS,"Highest Usage In Scope");
		}
		populateParts();
		populateTerms();
		if (detailsIndicatePrimitiveConcept() ||
				hasProcessingFlag(ProcessingFlag.MARK_AS_PRIMITIVE)) {
			getConcept().setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		}

		if (hasProcessingFlag(ProcessingFlag.SPLIT_TO_GROUP_PER_COMPONENT)) {
			splitComponentsIntoDistinctGroups();
		}
		getConcept().addAlternateIdentifier(getExternalIdentifier(), getCodeSystemSctId());
	}

	protected abstract String getCodeSystemSctId();

	protected void populateTerms() throws TermServerScriptException {
		//Start with the template PT and swap out as many parts as we come across
		String ptTemplateStr = getPreferredTermTemplate();
		
		ptTemplateStr = populateTermTemplateFromSlots(ptTemplateStr);
		ptTemplateStr = tidyUpTerm(ptTemplateStr);
		ptTemplateStr = StringUtils.capitalizeFirstLetter(ptTemplateStr);

		Description pt = Description.withDefaults(ptTemplateStr, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		applyTemplateSpecificTermingRules(pt);

		Description fsn = Description.withDefaults(ptTemplateStr + getSemTag(), DescriptionType.FSN, Acceptability.PREFERRED);
		applyTemplateSpecificTermingRules(fsn);

		concept.addDescription(pt);
		concept.addDescription(fsn);

		if (cpm.includeLongNameDescription) {
			//Also add the Long Common Name as a Synonym
			String lcnStr = getExternalConcept().getDisplayName();
			Description lcn = Description.withDefaults(lcnStr, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
			//Override the case significance for these
			lcn.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			concept.addDescription(lcn);
		}
	}
	
	protected abstract void applyTemplateSpecificTermingRules(Description pt);

	protected String populateTermTemplateFromSlots(String ptTemplateStr) {
		//Do we have any slots left to fill?  Find their attribute types via the slot map
		String [] templateItems = org.apache.commons.lang3.StringUtils.substringsBetween(ptTemplateStr,"[", "]");
		if (templateItems == null) {
			return ptTemplateStr;
		}

		for (String templateItem : templateItems) {
			ptTemplateStr = populateTemplateItem(templateItem, ptTemplateStr);
		}
		return ptTemplateStr;
	}

	protected String populateTemplateItem(String templateItem, String ptTemplateStr) {
		String regex = "\\[" + templateItem + "\\]";
		if (slotTermMap.containsKey(templateItem)) {
			String itemStr = StringUtils.decapitalizeFirstLetter(slotTermMap.get(templateItem));
			ptTemplateStr = ptTemplateStr.replaceAll(regex, itemStr);
		}
		return ptTemplateStr;
	}

	protected abstract void populateParts() throws TermServerScriptException;

	protected abstract boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException;

	private void splitComponentsIntoDistinctGroups() {
		//Split out the components from the other attributes, then copy all the non-component values
		//into their own groups
		List<Relationship> componentAttributes = new ArrayList<>();
		List<Relationship> otherAttributes = new ArrayList<>();
		Set<Relationship> initialRelationships = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Relationship r : initialRelationships) {
			if (r.getType().equals(COMPONENT)
					|| r.getType().equals(INHERES_IN)) {
				componentAttributes.add(r);
				//And remove, we'll add back in later
				concept.removeRelationship(r);
			} else {
				otherAttributes.add(r);
			}
		}

		int groupNum = 0;
		for (Relationship componentAttribute : componentAttributes) {
			groupNum++;
			RelationshipGroup g = concept.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, groupNum);
			if (g == null) {
				g = new RelationshipGroup(groupNum);
			}
			//Add the component attribute if required
			if (!g.containsTypeValue(componentAttribute)) {
				concept.addRelationship(componentAttribute.getType(), componentAttribute.getTarget(), groupNum);
			}
			//Add all the other attributes
			for (Relationship otherAttribute : otherAttributes) {
				if (!g.containsTypeValue(otherAttribute)) {
					concept.addRelationship(otherAttribute.getType(), otherAttribute.getTarget(), groupNum);
				}
			}
		}
	}

	public String getPreferredTermTemplate() {
		return preferredTermTemplate;
	}

	public void setPreferredTermTemplate(String preferredTermTemplate) {
		this.preferredTermTemplate = preferredTermTemplate;
	}

	protected String tidyUpTerm(String term) {
		term = removeUnpopulatedTermSlot(term, " at [TIME]");
		term = removeUnpopulatedTermSlot(term, " by [METHOD]");
		term = removeUnpopulatedTermSlot(term, " in [SYSTEM]");
		term = removeUnpopulatedTermSlot(term, " using [DEVICE]");
		term = removeUnpopulatedTermSlot(term, " [CHALLENGE]");
		term = term.replaceAll(" {2}", " ");
		return term;
	}

	private String removeUnpopulatedTermSlot(String term, String str) {
		if (term.contains(str)) {
			//Need to make string regex safe
			str = str.replaceAll("\\[","\\\\\\[").replaceAll("\\]","\\\\\\]");
			term = term.replaceAll(str, "");
		}
		return term;
	}
}
