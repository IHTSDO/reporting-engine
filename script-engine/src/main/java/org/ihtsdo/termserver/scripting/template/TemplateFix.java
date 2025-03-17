package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.ihtsdo.termserver.scripting.EclCache;
import org.snomed.authoringtemplate.domain.ConceptTemplate;
import org.snomed.authoringtemplate.domain.logical.*;

import com.google.common.io.Files;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract public class TemplateFix extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(TemplateFix.class);

	protected Set<Concept> exclusions;
	protected List<String> exclusionWords;
	protected boolean checkAllDescriptionsForExclusions = false;
	protected List<String> inclusionWords;
	protected boolean includeComplexTemplates = true;
	protected boolean includeOrphanet = true;
	protected List<Concept> complexTemplateAttributes;
	protected boolean includeDueTos = false;
	protected boolean excludeSdMultiRG = false;
	protected Set<Concept> explicitExclusions;
	
	String[] templateNames;
	List<Template> templates = new ArrayList<>();
	TemplateServiceClient tsc = null; //Don't try and initialise this before we know the server and cookie details
	
	Map<Concept, Template> conceptToTemplateMap = new HashMap<>();

	protected TemplateFix(BatchFix clone) {
		super(clone);
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		if (args != null) {
			super.init(args);
		}

		tsc = new TemplateServiceClient(getServerUrl(), getAuthenticatedCookie());
		
		AttributeGroup.useDefaultValues = true;
		//We'll check these now so we know if there's some parsing error
		char id = 'A';
		if (templateNames != null) {
			for (int x = 0; x < templateNames.length; x++, id++) {
				Template t = loadLocalTemplate(id, templateNames[x]);
				subsetECL = t.getDomain();
				validateTemplate(t);
				LOGGER.info("Validated template: {}", templateNames[x]);
			}
		}
		//We're going to scrub the ECL cache at this point, because we've now cached a bunch of concepts just as the SCTIDs, which will fail
		//when we recover that same ECL later and try to use them.
		EclCache.reset();
	}

	public void postInit() throws TermServerScriptException {
		initTemplatesAndExclusions();
		super.postInit();
		LOGGER.info("Post initialisation complete");
	}
	
	public void postInit(String[] tabNames, String[] columnHeadings) throws TermServerScriptException {
		initTemplatesAndExclusions();
		postInit(tabNames, columnHeadings,false);
		LOGGER.info("Post initialisation complete, with multiple tabs");
	}

	private void initTemplatesAndExclusions() throws TermServerScriptException {
		if (subHierarchyStr != null) {
			subHierarchy = gl.getConcept(subHierarchyStr);
		}
		
		//Only load templates now if we've not already done so
		if (templates.isEmpty()) {
			char id = 'A';
			for (int x = 0; x < templateNames.length; x++, id++) {
				Template t = loadLocalTemplate(id, templateNames[x]);
				validateTemplate(t);
				templates.add(t);
				LOGGER.info("Loaded template: {}", t);
				
				if (StringUtils.isEmpty(subsetECL)) {
					subsetECL = t.getDomain();
					LOGGER.info("Subset ECL set to {}", subsetECL);
				}
			}
			LOGGER.info("{} Templates loaded successfully", templates.size());
		}
		
		if (exclusions == null) {
			exclusions = new HashSet<>();
		}

		for (Concept thisExclude : excludeHierarchies) {
			LOGGER.info("Setting exclusion of {} subHierarchy.", thisExclude);
			exclusions.addAll(thisExclude.getDescendants(NOT_SET));
		}
		
		//Note add words as lower case as we do all lower case matching
		if (exclusionWords == null) {
			exclusionWords = new ArrayList<>();
		}
		
		if (hasInputFile(0)) {
			importExplicitExclusions();
		}
		
		if (!includeComplexTemplates) {
			if (!includeDueTos) {
				exclusionWords.add("due to");
			}
			exclusionWords.add("co-occurrent");
			exclusionWords.add("on examination");
			exclusionWords.add("complex");
			exclusionWords.add("associated");
			exclusionWords.add("after");
			exclusionWords.add("complication");
			exclusionWords.add("with");
			exclusionWords.add("without");
		} else {
			LOGGER.warn ("Including complex templates");
		}
		
		complexTemplateAttributes = new ArrayList<>();
		if (!includeDueTos) {
			complexTemplateAttributes.add(DUE_TO);
		}
		complexTemplateAttributes.add(AFTER);
		complexTemplateAttributes.add(gl.getConcept("726633004")); //|Temporally related to (attribute)|
		complexTemplateAttributes.add(gl.getConcept("288556008")); //|Before (attribute)|
		complexTemplateAttributes.add(gl.getConcept("371881003")); //|During (attribute)|
		complexTemplateAttributes.add(gl.getConcept("363713009")); //|Has interpretation (attribute)|
		complexTemplateAttributes.add(gl.getConcept("363714003")); //|Interprets (attribute)|
		complexTemplateAttributes.add(gl.getConcept("47429007"));  //|Associated with (attribute)
		
	}

	private void importExplicitExclusions() throws TermServerScriptException {
		explicitExclusions = new HashSet<>();
		LOGGER.info("Loading Explicit Exclusions {}", getInputFile());
		if (!getInputFile().canRead()) {
			throw new TermServerScriptException("Cannot read: " + getInputFile());
		}
		List<String> lines;
		try {
			lines = Files.readLines(getInputFile(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Failure while reading: " + getInputFile(), e);
		}
		LOGGER.debug("Processing Explicit Exclusions File");
		for (String line : lines) {
			String sctId = line.split(TAB)[0];
			Concept excluded = gl.getConcept(sctId, false, true);  //Validate concept exists
			explicitExclusions.add(excluded);
		}
		addSummaryInformation("Explicitly excluded concepts specified", explicitExclusions.size());
	}

	private void validateTemplate(Template t) throws TermServerScriptException {
		//Is the Domain specified by the template valid?  No point running if it selects no rows
		boolean useLocalStoreIfSimple = false;
		String ecl = t.getDomain();
		if (!getArchiveManager().isAllowStaleData() && findConcepts(ecl, false, useLocalStoreIfSimple).isEmpty()) {
			throw new TermServerScriptException("Template domain: " + ecl + " returned 0 rows");
		}
		
		//Ensure that any repeated instances of identically named slots have the same value
		Map<String, String> namedSlots = new HashMap<>();
		for (AttributeGroup g : t.getAttributeGroups()) {
			for (Attribute a : g.getAttributes()) {
				//Does this attribute have a named slot?
				if (!StringUtils.isEmpty(a.getValueSlotName())) {
					String attributeClause = a.toString().replace("  ", " ");
					String attributeClauseValue = attributeClause.substring(attributeClause.indexOf("=") + 1).trim();
					if (namedSlots.containsKey(a.getValueSlotName())) {
						//TODO This comparison should be made without FSNs involved
						if (!attributeClauseValue.equals(namedSlots.get(a.getValueSlotName()))) {
							String first = attributeClauseValue;
							String second = namedSlots.get(a.getValueSlotName());
							String diff = StringUtils.difference(first, second);
							String detail = a.getValueSlotName() + " -> " + first + " vs " + second + " difference is '" + diff + "'";
							throw new IllegalArgumentException("Named slots sharing the same name must have identical slot definition: " + detail);
						}
					} else {
						namedSlots.put(a.getValueSlotName(), attributeClauseValue);
					}
				}
			}
		}
	}

	protected Template loadLocalTemplate (char id, String fileName) throws TermServerScriptException {
		try {
			LOGGER.info("Loading local template {}: {}", id, fileName );
			ConceptTemplate ct = tsc.loadLocalConceptTemplate(fileName);
			LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
			Template t = new Template(id, lt, fileName);
			t.setDomain(ct.getDomain());
			t.setDocumentation(ct.getDocumentation());
			return t;
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to load template " + fileName, e);
		}
	}
	
	protected Template loadTemplate (char id, String templateName) throws TermServerScriptException {
		try {
			LOGGER.info("Loading remote template {}: '{}' from {}", id, templateName, tsc.getServerUrl() );
			ConceptTemplate ct = tsc.loadLogicalTemplate(templateName);
			LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
			Template t = new Template(id, lt, templateName);
			t.setDomain(ct.getDomain());
			t.setDocumentation(ct.getDocumentation());
			return t;
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to load template " + templateName, e);
		}
	}
	
	protected Set<Concept> findTemplateMatches(Template t, Collection<Concept> concepts, Set<Concept> misalignedConcepts, Integer exclusionReport, CharacteristicType charType) throws TermServerScriptException {
		Set<Concept> matches = new HashSet<Concept>();
		LOGGER.info("Examining " + concepts.size() + " concepts against template " + t);
		int conceptsExamined = 0;
		for (Concept c : concepts) {
			if (!c.isActive()) {
				LOGGER.warn ("Ignoring inactive concept returned by ECL: " + c);
				continue;
			}
			if (!isExcluded(c, exclusionReport)) {
				if (TemplateUtils.matchesTemplate(c, t, this, charType)) {
					//Do we already have a template for this concept?  
					//Assign the most specific template if so (TODO Don't assume order indicates complexity!)
					if (conceptToTemplateMap.containsKey(c)) {
						Template existing = conceptToTemplateMap.get(c);
						Template moreSpecific = t.getId() > existing.getId() ? t : existing; 
						LOGGER.warn( c + "matches two templates: " + t.getId() + " & " + existing.getId() + " using most specific " + moreSpecific.getId());
						conceptToTemplateMap.put(c, moreSpecific);
					} else {
						conceptToTemplateMap.put(c, t);
					}
					matches.add(c);
				} else {
					if (misalignedConcepts != null) {
						misalignedConcepts.add(c);
					}
				}
			} else {
				//Only count exclusions for the first pass
				if (t.getId() == 'A') {
					incrementSummaryInformation("Concepts excluded");
				}
			}
			if (++conceptsExamined % 1000 == 0) {
				print(".");
			}
		}
		println("");
		addSummaryInformation("Concepts in \"" + t.getDomain() + "\" matching template " + t.getId(), matches.size());
		return matches;
	}
	
	protected boolean isExcluded(Concept c, Integer exclusionReport) throws TermServerScriptException {
		
		//These hierarchies have been excluded
		if (exclusions.contains(c)) {
			if (exclusionReport != null) {
				incrementSummaryInformation("Concepts excluded due to hierarchial exclusion");
				report(exclusionReport, c, "Hierarchial exclusion", c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			}
			return true;
		}
		
		//Are we excluding sufficiently defined concepts with more than one substantial role group?
		if (excludeSdMultiRG && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			boolean firstSubstantialRGDetected = false;
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (g.size() > 1) {
					if (firstSubstantialRGDetected) {
						//So we're now on our 2nd one
						if (exclusionReport != null) {
							incrementSummaryInformation("Concepts excluded due to SD with multiple substantial role groups");
							report(exclusionReport, c, "Multi-RG exclusion", c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
						}
						return true;
					} else {
						firstSubstantialRGDetected = true;
					}
				}
			}
		}
		
		if (!includeOrphanet && gl.isOrphanetConcept(c)) {
			if (exclusionReport != null) {
				incrementSummaryInformation("Orphanet concepts excluded");
				report(exclusionReport, c, "Orphanet exclusion", c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			}
			return true;
		}
		
		if (StringUtils.isEmpty(c.getFsn())) {
			if (exclusionReport != null) {
				LOGGER.warn("Skipping concept with no FSN: " + c.getConceptId());
				report(exclusionReport, c, "No FSN", c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			}
			return true;
		}
		
		//We could ignore on the basis of a word, or SCTID
		String fsn = " " + c.getFsn().toLowerCase();
		String pt = " " + c.getPreferredSynonym().toLowerCase();
		for (String word : exclusionWords) {
			if (fsn.contains(word) || pt.contains(word) || (checkAllDescriptionsForExclusions && descriptionsContainWord(c, word))) {
				if (exclusionReport != null) {
					incrementSummaryInformation("Concepts excluded due to lexical match ");
					incrementSummaryInformation("Concepts excluded due to lexical match (" + word + ")");
					report(exclusionReport, c, "Lexical exclusion", word, c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				}
				return true;
			}
		}
		
		if (inclusionWords.size() > 0 && !containsInclusionWord(c)) {
			incrementSummaryInformation("Concepts excluded due to lexical match failure");
			report(exclusionReport, c, "Lexical inclusion failure", c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			return true;
		}
		
		//We're excluding complex templates that have a due to, or "after" attribute
		if (!includeComplexTemplates && isComplex(c)) {
			if (exclusionReport != null) {
				incrementSummaryInformation("Concepts excluded due to complexity");
				report(exclusionReport, c, "Complex templates excluded", c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
			}
			return true;
		}
		return false;
	}
	
	private boolean descriptionsContainWord(Concept c, String word) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
				.map(d -> " " + d.getTerm().toLowerCase() + " ")
				.anyMatch(s -> s.contains(word));
	}

	protected boolean isComplex(Concept c) {
		for (Concept excludedType : complexTemplateAttributes) {
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getType().equals(excludedType)) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void report(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
		Concept c = (Concept)component;
		char relevantTemplate = ' ';
		if (conceptToTemplateMap != null && conceptToTemplateMap.containsKey(c)) {
			relevantTemplate = conceptToTemplateMap.get(c).getId();
		}
		super.report(task, component, severity, actionType, SnomedUtils.translateDefnStatus(c.getDefinitionStatus()), relevantTemplate, details);
	}
	
	
	
	protected boolean containsInclusionWord(Concept c) {
		String fsn = c.getFsn().toLowerCase();
		String pt = c.getPreferredSynonym().toLowerCase();
		for (String word : inclusionWords) {
			if (fsn.contains(word) || pt.contains(word)) {
				return true;
			}
		}
		return false;
	}
	
	protected void outputMetaData() throws TermServerScriptException {
		LOGGER.info("Outputting metadata tab");
		String user = jobRun == null ? "System" : jobRun.getUser();
		writeToReportFile (SECONDARY_REPORT, "Requested by: " + user);
		writeToReportFile (SECONDARY_REPORT, QUOTE + "Run against: " + subsetECL + QUOTE);
		writeToReportFile (SECONDARY_REPORT, "Project: " + project);
		if (!StringUtils.isEmpty(subsetECL)) {
			writeToReportFile (SECONDARY_REPORT, "Concepts considered: " + findConcepts(subsetECL).size());
		}
		writeToReportFile (SECONDARY_REPORT, "Templates: " );
		
		for (Template t : templates) {
			writeToReportFile (SECONDARY_REPORT,TAB + "Name: " + t.getName());
			writeToReportFile (SECONDARY_REPORT,QUOTE + TAB  + "Domain: " + t.getDomain() + QUOTE);
			writeToReportFile (SECONDARY_REPORT,TAB + "Documentation: " + t.getDocumentation());
			String stl = t.getLogicalTemplate().toString();
			stl = SnomedUtils.populateFSNs(stl);
			writeToReportFile (SECONDARY_REPORT,QUOTE + TAB + "STL: " +  stl + QUOTE);
			if (!StringUtils.isEmpty(t.getDomain())) {
				writeToReportFile (SECONDARY_REPORT, TAB + "Concepts considered: " + findConcepts(t.getDomain()).size());
			}
			writeToReportFile (SECONDARY_REPORT,TAB);
		}
	}
}

