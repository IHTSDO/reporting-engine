package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphLoader implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(GraphLoader.class);

	private static GraphLoader singleton = null;
	private Map<String, Concept> concepts = new HashMap<String, Concept>();
	private Map<String, Description> descriptions = new HashMap<String, Description>();
	private Map<String, Component> allComponents = null;
	private Map<Component, Concept> componentOwnerMap = null;
	private Map<String, Concept> fsnMap = null;
	private Map<String, Concept> usptMap = null;
	private Map<String, Concept> gbptMap = null;
	private Set<String> excludedModules;
	public static int MAX_DEPTH = 1000;
	private Set<String> orphanetConceptIds;
	private AxiomRelationshipConversionService axiomService;
	
	private DescendantsCache descendantsCache = DescendantsCache.getDescendantsCache();
	private DescendantsCache statedDescendantsCache = DescendantsCache.getStatedDescendantsCache();
	private AncestorsCache ancestorsCache = AncestorsCache.getAncestorsCache();
	private AncestorsCache statedAncestorsCache = AncestorsCache.getStatedAncestorsCache();
	
	//Watch that this map is of the TARGET of the association, ie all concepts used in a historical association
	private Map<Concept, List<AssociationEntry>> historicalAssociations =  new HashMap<Concept, List<AssociationEntry>>();
	private Map<Concept, Set<DuplicatePair>> duplicateLangRefsetEntriesMap;
	private Set<LangRefsetEntry> duplicateLangRefsetIdsReported = new HashSet<>();

	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapPreCoord = new HashMap<>();
	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapPostCoord = new HashMap<>();
	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapAll = new HashMap<>();
	private Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMapNewPreCoord = new HashMap<>();
	
	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapPreCoord = new HashMap<>();
	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapPostCoord = new HashMap<>();
	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapAll = new HashMap<>();
	private Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMapNewPreCoord = new HashMap<>();
	private Map<Concept, MRCMDomain> mrcmDomainMap = new HashMap<>();

	private boolean detectNoChangeDelta = false;
	private boolean runIntegrityChecks = true;
	private boolean checkForExcludedModules = false;
	private boolean recordPreviousState = false;
	private boolean allowIllegalSCTIDs = false;
	
	protected boolean populateOriginalModuleMap = false;
	protected Map<Component, String> originalModuleMap = null;
	
	public StringBuffer log = new StringBuffer();
	
	private TransitiveClosure transitiveClosure;
	private TransitiveClosure previousTransitiveClosure;

	private List<String> integrityWarnings = new ArrayList<>();
	
	public static GraphLoader getGraphLoader() {
		if (singleton == null) {
			singleton = new GraphLoader();
			singleton.axiomService = new AxiomRelationshipConversionService (null);
			singleton.excludedModules = new HashSet<>();
			singleton.excludedModules.add(SCTID_LOINC_PROJECT_MODULE);
			populateKnownConcepts();
		}
		return singleton;
	}
	
	private GraphLoader() {
		//Prevents instantiation by other than getGraphLoader()
	}
	
	private static void populateKnownConcepts() {
		//Pre populate known concepts to ensure we only ever refer to one object
		//Reset concept each time, to avoid contamination from previous runs
		List<Concept> conceptsToReset = List.of(
				ROOT_CONCEPT, IS_A, PHARM_BIO_PRODUCT, MEDICINAL_PRODUCT, PHARM_DOSE_FORM,
				SUBSTANCE, CLINICAL_FINDING, BODY_STRUCTURE, PROCEDURE, SITN_WITH_EXP_CONTXT,
				SPECIMEN, OBSERVABLE_ENTITY, EVENT, DISEASE, DEVICE, ORGANISM,
				LEFT, RIGHT, BILATERAL);
		for (Concept c : conceptsToReset) {
			c.reset();
			singleton.concepts.put(c.getConceptId(), c);
		}
	}

	public Collection <Concept> getAllConcepts() {
		return concepts.values();
	}
	
	public void reset() {
		LOGGER.info("Resetting Graph Loader");
		concepts = new HashMap<String, Concept>();
		descriptions = new HashMap<String, Description>();
		allComponents = null;
		componentOwnerMap = null;
		fsnMap = null;
		orphanetConceptIds = null;
		descendantsCache.reset();
		statedDescendantsCache.reset();
		ancestorsCache.reset();
		statedAncestorsCache.reset();
		historicalAssociations =  new HashMap<Concept, List<AssociationEntry>>();
		duplicateLangRefsetEntriesMap = new HashMap<>();
		duplicateLangRefsetIdsReported = new HashSet<>();
		integrityWarnings = new ArrayList<>();
		
		//We'll reset the ECL cache during TS Init
		populateKnownConcepts();
		previousTransitiveClosure = null;
		transitiveClosure = null;
		
		fsnMap = null;
		usptMap = null;
		gbptMap = null;
		historicalAssociations =  new HashMap<Concept, List<AssociationEntry>>();
		duplicateLangRefsetEntriesMap= null;
		duplicateLangRefsetIdsReported = new HashSet<>();
		mrcmStagingAttributeRangeMapPreCoord = new HashMap<>();
		mrcmStagingAttributeRangeMapPostCoord = new HashMap<>();
		mrcmStagingAttributeRangeMapAll = new HashMap<>();
		mrcmStagingAttributeRangeMapNewPreCoord = new HashMap<>();
		mrcmAttributeRangeMapPreCoord = new HashMap<>();
		mrcmAttributeRangeMapPostCoord = new HashMap<>();
		mrcmAttributeRangeMapAll = new HashMap<>();
		mrcmAttributeRangeMapNewPreCoord = new HashMap<>();
		mrcmDomainMap = new HashMap<>();
		
		System.gc();
		outputMemoryUsage();
	}
	
	private void outputMemoryUsage() {
		Runtime runtime = Runtime.getRuntime();
		NumberFormat format = NumberFormat.getInstance();

		//long maxMemory = runtime.maxMemory();
		//long allocatedMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();

		LOGGER.info("free memory now: " + format.format(freeMemory / 1024));
		//LOGGER.info("allocated memory: " + format.format(allocatedMemory / 1024) );
		//LOGGER.info("max memory: " + format.format(maxMemory / 1024));
		//LOGGER.info("total free memory: " + format.format((freeMemory + (maxMemory - allocatedMemory)) / 1024));
	}

	public Set<Concept> loadRelationships(CharacteristicType characteristicType, InputStream relStream, boolean addRelationshipsToConcepts, boolean isDelta, Boolean isReleased) 
			throws IOException, TermServerScriptException {
		Set<Concept> concepts = new HashSet<Concept>();
		BufferedReader br = new BufferedReader(new InputStreamReader(relStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		long relationshipsLoaded = 0;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				
				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				
				/*if (lineItems[REL_IDX_ID].equals("243605023")) {
					TermServerScript.debug("here");
				}*/
				
				String msg = SnomedUtils.isValid(lineItems[IDX_ID], PartitionIdentifier.RELATIONSHIP);
				if (msg != null) {
					TermServerScript.warn(msg);
				}
				
				//Might need to modify the characteristic type for Additional Relationships
				characteristicType = SnomedUtils.translateCharacteristicType(lineItems[REL_IDX_CHARACTERISTICTYPEID]);
				
				if (!isConcept(lineItems[REL_IDX_SOURCEID])) {
					TermServerScript.debug (characteristicType + " relationship " + lineItems[REL_IDX_ID] + " referenced a non concept identifier: " + lineItems[REL_IDX_SOURCEID]);
				}
				//Dutch extension has phantom concept referenced in an inactive stated relationship
				if (lineItems[REL_IDX_DESTINATIONID].equals("39451000146106")) {
					log.append("Skipping reference to phantom concept - 39451000146106");
					continue;
				}
				Concept thisConcept = getConcept(lineItems[REL_IDX_SOURCEID]);
				
				/*if ( lineItems[REL_IDX_CHARACTERISTICTYPEID].equals(SCTID_INFERRED_RELATIONSHIP) 
						&& thisConcept.getId().equals("422435005") 
						&& lineItems[REL_IDX_TYPEID].equals("116680003")) {
					TermServerScript.debug("here");
				}*/
				
				/*if (lineItems[REL_IDX_ACTIVE].equals("1") && lineItems[REL_IDX_CHARACTERISTICTYPEID].equals(SCTID_STATED_RELATIONSHIP)) {
					TermServerScript.warn("Didn't expected to see any more of these! " + String.join(" ", lineItems));
				}*/
				
				//If we've already received a newer version of this component, say
				//by loading published INT first and a previously published MS 2nd, then skip
				Relationship existing = thisConcept.getRelationship(lineItems[IDX_ID]);
				
				String previousState = null;
				if (isRecordPreviousState() && existing != null) {
					previousState = existing.getMutableFields();
				}
				
				if (existing != null &&
						!StringUtils.isEmpty(existing.getEffectiveTime()) 
						&& (isReleased != null && isReleased)
						&& (existing.getEffectiveTime().compareTo(lineItems[IDX_EFFECTIVETIME]) >= 0)) {
					//If we get a subsequent import with the SAME date, then we'll keep the active one
					//TODO IF an extension and the international edition end up making a chance to a component on the same 
					//date, then we should take the international state to be the final state because the extension cannot
					//have known about the International changes at the time it was published ie it was based on an earlier 
					//edition of International. We would then upgrade that extension with our new component version.
					if (existing.getEffectiveTime().compareTo(lineItems[IDX_EFFECTIVETIME]) == 0) {
						if (existing.isActive() && lineItems[IDX_ACTIVE].equals("0")) {
							System.out.println("Skipping incoming published relationship row with same date as cuurently held, but inactive.");
							continue;
						}
					} else {
						continue;
					}
				}
				
				if (addRelationshipsToConcepts) {
					addRelationshipToConcept(characteristicType, lineItems, isDelta, isReleased, previousState);
				}
				concepts.add(thisConcept);
				relationshipsLoaded++;
			} else {
				isHeaderLine = false;
			}
		}
		log.append("\tLoaded " + relationshipsLoaded + " relationships of type " + characteristicType + " which were " + (addRelationshipsToConcepts?"":"not ") + "added to concepts\n");
		return concepts;
	}
	
	private boolean isExcluded(String moduleId) {
		return excludedModules.contains(moduleId);
	}

	public void loadAxioms(InputStream axiomStream, boolean isDelta, Boolean isReleased) 
			throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(axiomStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		int axiomsLoaded = 0;
		int ignoredAxioms = 0;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				
				//Only load OWL Expressions
				if (!lineItems[REF_IDX_REFSETID].equals(SCTID_OWL_AXIOM_REFSET)) {
					continue;
				}
				
				if (!isConcept(lineItems[REF_IDX_REFCOMPID])) {
					TermServerScript.debug("Axiom " + lineItems[REL_IDX_ID] + " referenced a non concept identifier: " + lineItems[REF_IDX_REFCOMPID]);
				}
				
				Long conceptId = Long.parseLong(lineItems[REF_IDX_REFCOMPID]);
				Concept c = getConcept(conceptId);
				
				/*if (conceptId == 10504007L) {
					TermServerScript.debug("Here");
				}
				
				if (lineItems[IDX_ID].equals("1e684afa-9319-4b24-9489-40caef554e13")) {
					TermServerScript.debug ("here");
				}*/
				
				try {
					//Also save data in RF2 form so we can build Snapshot
					AxiomEntry axiomEntry = AxiomEntry.fromRf2(lineItems);
					
					//Are we overwriting an existing axiom?  We also want to capture what stated relationships
					//were previously present so we can work out the individual published states.
					Set<Relationship> previouslyPublishedStatedRels = null;
					if (c.getAxiomEntries().contains(axiomEntry)) {
						AxiomEntry replacedAxiomEntry = c.getAxiom(axiomEntry.getId());
						previouslyPublishedStatedRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
						axiomEntry.setReleased(replacedAxiomEntry.isReleased());
						if (isRecordPreviousState()) {
							String previousState = replacedAxiomEntry.getMutableFields();
							axiomEntry.addIssue(previousState);
						}
						//It might be that depending on the point in the release cycle,
						//we might try to load an extension on top of a more recent dependency
						//if the core has recently been released.  Don't allow an overwrite in this case.
						if (!StringUtils.isEmpty(axiomEntry.getEffectiveTime())
								&& replacedAxiomEntry.getEffectiveTime().compareTo(axiomEntry.getEffectiveTime()) > 0) {
							ignoredAxioms++;
							if (ignoredAxioms < 5) {
								TermServerScript.warn("Ignoring " + axiomEntry.getEffectiveTime() + " since " + replacedAxiomEntry.getEffectiveTime() + " " + replacedAxiomEntry.getId() + " already held");
							} 
							continue;
						}
						if (detectNoChangeDelta && !isReleased) {
							detectNoChangeDelta(c, replacedAxiomEntry, lineItems);
						}
						c.getAxiomEntries().remove(axiomEntry);
						//We'll inactivate all these relationships and allow them to be replaced
						AxiomRepresentation replacedAxiom = axiomService.convertAxiomToRelationships(replacedAxiomEntry.getOwlExpression());
						//Filter out any additional statements such as TransitiveObjectProperty(:123005000)]
						if (replacedAxiom != null) {
							Set<Relationship> replacedRelationships = AxiomUtils.getRHSRelationships(c, replacedAxiom);
							alignAxiomRelationships(c, replacedRelationships, replacedAxiomEntry, false);
							for (Relationship r : replacedRelationships) {
								addRelationshipToConcept(CharacteristicType.STATED_RELATIONSHIP, r, isDelta);
							}
						}
					}
					c.getAxiomEntries().add(axiomEntry);
					
					//Only set the released flag if it's not set already
					if (axiomEntry.isReleased() == null) {
						axiomEntry.setReleased(isReleased);
					}
				
					AxiomRepresentation axiom = axiomService.convertAxiomToRelationships(lineItems[REF_IDX_AXIOM_STR]);
					//Filter out any additional statements such as TransitiveObjectProperty(:123005000)]
					if (axiom != null) {
						Long LHS = axiom.getLeftHandSideNamedConcept();
						if (LHS == null) {
							//Is this a GCI?
							Long RHS = axiom.getRightHandSideNamedConcept();
							if (!conceptId.equals(RHS)) {
								throw new IllegalArgumentException("GCI Axiom RHS != RefCompId: " + line);
							}
							//This will replace any existing axiom with the same UUID
							c.addGciAxiom(AxiomUtils.toAxiom(c, axiomEntry, axiom));
							axiomEntry.setGCI(true);
						} else if (!conceptId.equals(LHS)) {
							//Have we got these weird NL axioms that exist on a different concept?
							log.append("Encountered " + (axiomEntry.isActive()?"active":"inactive") + " axiom on different concept to LHS argument");
							continue;
							//TODO
							//throw new IllegalArgumentException("Axiom LHS != RefCompId: " + line);
						}
						
						Set<Relationship> relationships = AxiomUtils.getRHSRelationships(c, axiom);
						if (relationships.size() == 0) {
							log.append("Check here - zero RHS relationships");
						}
						
						//If we already have relationships loaded from this axiom then it may be that 
						//a subsequent version does not feature them, and we'll have to remove them.
						removeRelsNoLongerFeaturedInAxiom(c, axiomEntry.getId(), relationships);
						
						//Now we might need to adjust the active flag if the axiom is being inactivated
						//Or juggle the groupId, since individual axioms don't know about each other's existence
						alignAxiomRelationships(c, relationships, axiomEntry, axiomEntry.isActive());
						
						//Although axiom may have been published, relationships that were not previously
						//present should not have that published state given to them
						if (isReleased != null && !isReleased && previouslyPublishedStatedRels != null && StringUtils.isEmpty(axiomEntry.getEffectiveTime())) {
							for (Relationship r : relationships) {
								if (!previouslyPublishedStatedRels.contains(r)) {
									r.setReleased(false);
								}
							}
						}
						
						for (Relationship r : relationships) {
							addRelationshipToConcept(CharacteristicType.STATED_RELATIONSHIP, r, isDelta);
						}
					} else {
						//Are we looking at a special axiom: Transitive, Reflexive or RoleChain?
						if (lineItems[IDX_ACTIVE].equals("1")) {
							c.mergeObjectPropertyAxiomRepresentation(axiomService.asObjectPropertyAxiom(lineItems[REF_IDX_AXIOM_STR]));
						}
					}
				} catch (ConversionException e) {
					throw new TermServerScriptException("Failed to load axiom: " + line, e);
				}
			} else {
				isHeaderLine = false;
			}
		}
		log.append("\tLoaded " + axiomsLoaded + " axioms");
		if (ignoredAxioms > 0) {
			System.err.println("Ignored " + ignoredAxioms + " already held with later effective time");
		}
	}
	
	private void removeRelsNoLongerFeaturedInAxiom(Concept c, String axiomId, Set<Relationship> currentAxiomRels) {
		List<Relationship> origSameAxiomRels = c.getRelationshipsFromAxiom(axiomId, ActiveState.ACTIVE);
		//Remove those still part of the axiom to leave the ones that have been removed
		origSameAxiomRels.removeAll(currentAxiomRels);
		for (Relationship removeMe : origSameAxiomRels) {
			c.removeRelationship(removeMe, true);  //Force removal.  Deletion of published component doesn't apply to axiom changes.
		}
	}

	private void alignAxiomRelationships(Concept c, Set<Relationship> relationships, AxiomEntry axiomEntry, boolean active) {
		//Do the groups already exist in the concept?  Give them that groupId if so
		int nextFreeGroup = 1;
		for (RelationshipGroup g : SnomedUtils.toGroups(relationships)) {
			if (g.isGrouped()) {
				RelationshipGroup match = SnomedUtils.findMatchingGroup(c, g, CharacteristicType.STATED_RELATIONSHIP);
				if (match == null) {
					//Do we need a new relationship group id for this group?
					while (!SnomedUtils.isFreeGroup(CharacteristicType.STATED_RELATIONSHIP, c, nextFreeGroup)) {
						nextFreeGroup++;
					}
					g.setGroupId(nextFreeGroup);
					nextFreeGroup++;
				} else {
					g.setGroupId(match.getGroupId());
					if (match.getGroupId() >= nextFreeGroup) {
						nextFreeGroup++;
					}
				}
			}
			g.setActive(active);
			g.setAxiom(axiomEntry);
			g.setModule(axiomEntry.getModuleId());
			g.setReleased(axiomEntry.isReleased());
			
			//Set the effectiveTime last as changing the other attributes will blank it
			g.setEffectiveTime(axiomEntry.getEffectiveTime());
		}
	}

	private boolean isConcept(String sctId) {
		return sctId.charAt(sctId.length()-2) == '0';
	}
	
	private boolean isDescription(String sctId) {
		return sctId.charAt(sctId.length()-2) == '1';
	}
	
	public Concept findConcept (String fsn) {
		//Populate the fsn map if required
		if (fsnMap == null) {
			fsnMap = new HashMap<>();
			for (Concept c : concepts.values()) {
				fsnMap.put(c.getFsn(), c);
			}
		}
		fsn = fsn.trim().replaceAll(ESCAPED_PIPE, "");
		return fsnMap.get(fsn);
	}
	
	public Concept findConceptByUSPT (String pt) throws TermServerScriptException {
		//Populate the pt map if required
		if (usptMap == null) {
			usptMap = new HashMap<>();
			for (Concept c : concepts.values()) {
				Description thisPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
				if (thisPT != null) {
					usptMap.put(thisPT.getTerm().toLowerCase(), c);
				}
			}
		}
		return usptMap.get(pt.toLowerCase());
	}
	
	public Concept findConceptByGBPT (String pt) throws TermServerScriptException {
		//Populate the pt map if required
		if (gbptMap == null) {
			gbptMap = new HashMap<>();
			for (Concept c : concepts.values()) {
				Description thisPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
				if (thisPT != null) {
					usptMap.put(thisPT.getTerm().toLowerCase(), c);
				}
			}
		}
		return gbptMap.get(pt.toLowerCase());
	}
	
	private Relationship createRelationshipFromRF2(CharacteristicType charType, String[] lineItems) throws TermServerScriptException {
		String sourceId = lineItems[REL_IDX_SOURCEID];
		Concept source = getConcept(sourceId);
		String typeId = lineItems[REL_IDX_TYPEID];
		
		if (sourceId.length() < 4 || typeId.length() < 4 ) {
			TermServerScript.debug("*** Invalid SCTID encountered in relationship " + lineItems[REL_IDX_ID] + ": s" + sourceId + " t" + typeId);
		}
		Concept type = getConcept(typeId);
		int groupNum = Integer.parseInt(lineItems[REL_IDX_RELATIONSHIPGROUP]);
		
		Relationship r;
		if (lineItems[REL_IDX_VALUE].startsWith("#")) {
			//Trim leading hash symbol. Leave as string to preserve DPs
			String value = lineItems[REL_IDX_VALUE].substring(1);
			r = new Relationship(source, type, value, groupNum, ConcreteValue.ConcreteValueType.DECIMAL);
		} else if (lineItems[REL_IDX_VALUE].startsWith("\"")) {
			//Trim of start and ending quote
			String value = lineItems[REL_IDX_VALUE].substring(1, lineItems[REL_IDX_VALUE].length()-1);
			r = new Relationship(source, type, value, groupNum, ConcreteValue.ConcreteValueType.STRING);
		} else {
			String destId = lineItems[REL_IDX_DESTINATIONID];
			if (destId.length() < 4 ) {
				TermServerScript.debug("*** Invalid SCTID encountered in relationship " + lineItems[REL_IDX_ID] + ": d" + destId );
			}
			Concept destination = getConcept(lineItems[REL_IDX_DESTINATIONID]);
			r = new Relationship(source, type, destination, groupNum);
		}
		r.setRelationshipId(lineItems[REL_IDX_ID].isEmpty()?null:lineItems[REL_IDX_ID]);
		r.setCharacteristicType(charType);
		r.setActive(lineItems[REL_IDX_ACTIVE].equals("1"));
		r.setModifier(SnomedUtils.translateModifier(lineItems[REL_IDX_MODIFIERID]));
		r.setModuleId(lineItems[REL_IDX_MODULEID]);
		
		//Set the effectiveTime last because changing the other fields from defaults causes it to null out
		r.setEffectiveTime(lineItems[REL_IDX_EFFECTIVETIME].isEmpty()?null:lineItems[REL_IDX_EFFECTIVETIME]);
		
		//Changing those values after the defaults were set in the constructor will incorrectly mark dirty
		r.setClean();
		return r;
	}
	
	public void addRelationshipToConcept(CharacteristicType charType, String[] lineItems, boolean isDelta, Boolean isReleased) throws TermServerScriptException {
		addRelationshipToConcept(charType, lineItems,isDelta, isReleased, null);
	}

	/**
	 * @param isReleased 
	 * @throws TermServerScriptException
	 */
	public void addRelationshipToConcept(CharacteristicType charType, String[] lineItems, boolean isDelta, Boolean isReleased, String issue) throws TermServerScriptException {
		Relationship r = createRelationshipFromRF2(charType, lineItems);
		r.setReleased(isReleased);
		
		if (issue != null) {
			r.addIssue(issue);
		}
		
		String revertEffectiveTime = null;
		if (detectNoChangeDelta && isReleased != null && !isReleased && r.getId() != null) {
			//Do we already have this relationship from the snapshot?  
			//Only interested if we have an id as axioms handled separately
			Relationship existing = r.getSource().getRelationship(r.getId());
			revertEffectiveTime = detectNoChangeDelta(r.getSource(), existing, lineItems);
		}
		
		if (revertEffectiveTime != null) {
			r.setEffectiveTime(revertEffectiveTime);
		}
/*		if (r.getType().equals(IS_A) && r.getSourceId().equals("555621000005106")) {
			TermServerScript.debug("here");
		}*/
		
		addRelationshipToConcept(charType, r, isDelta);
	}
	
	public void addRelationshipToConcept(CharacteristicType charType, Relationship r, boolean isDelta) throws TermServerScriptException {
		r.getSource().addRelationship(r);
		
		//Consider adding or removing parents if the relationship is ISA
		//But only remove items if we're processing a delta and there aren't any remaining
		//Don't modify our loaded hierarchy if we're loading a single concept from the TS
		if (r.getType().equals(IS_A) && r.getTarget() != null) {
			Concept source = r.getSource();
			Concept target = r.getTarget();
			
			/*if (r.getCharacteristicType().equals(CharacteristicType.INFERRED_RELATIONSHIP) && 
					r.getSourceId().contentEquals("21731004") && !r.isActive()) {
				TermServerScript.debug("here");
			}*/
			
			if (r.isActive()) {
				source.addParent(r.getCharacteristicType(),r.getTarget());
				target.addChild(r.getCharacteristicType(),r.getSource());
			} else {
				//Ah this gets tricky.  We only remove the parent child relationship if
				//the source concept has no other relationships with the same triple
				//because the relationship might exist in another axiom
				if (source.getRelationships(r.getCharacteristicType(), r).size() == 0) {
					source.removeParent(r.getCharacteristicType(),r.getTarget());
					target.removeChild(r.getCharacteristicType(),r.getSource());
				} else {
					//TermServerScript.warn("Not removing parent/child relationship as exists in other axiom / alternative relationship: " + r);
				}
			}
		} 
	}
	
	public Concept getConcept(String sctid) throws TermServerScriptException {
		return getConcept(sctid.trim(), true, true);
	}
	
	public Concept getConceptSafely (String identifier) {
		try {
			return getConcept(identifier, true, true);
		} catch (TermServerScriptException e) {
			return null;
		}
	}
	
	public Concept getConcept(Long sctId) throws TermServerScriptException {
		return getConcept(sctId.toString(), true, true);
	}
	
	public boolean conceptKnown(String sctId) {
		return concepts.containsKey(sctId);
	}
	
	public Concept getConcept(String identifier, boolean createIfRequired, boolean validateExists) throws TermServerScriptException {
		//Have we been passed a full identifier for the concept eg SCTID |FSN| ?
		if (StringUtils.isEmpty(identifier)) {
			if (validateExists) {
				throw new IllegalArgumentException("Empty SCTID encountered");
			}
			return null;
		}
		if (identifier.contains(PIPE)) {
			identifier = identifier.split(ESCAPED_PIPE)[0].trim();
		}
		String sctId = identifier.trim();
		
		//Make sure we're actually being asked for a concept
		if (sctId.length() < 6 || !isConcept(sctId)) {
			if (!createIfRequired && !validateExists && sctId.length() == 0) {
				return null;
			} else {
				if (allowIllegalSCTIDs) {
					LOGGER.warn("Allowing illegal SCTID to exist: {}", sctId);
				} else {
					throw new IllegalArgumentException("Request made for non concept sctid: '" + sctId + "'");
				}
			}
		}
		
		//Seeing a concept appear from somewhere that fails Verhoeff.  Blow up if this happens, we
		//need to know what file it's in and deal with it as a P1
		if (isRunIntegrityChecks()) {
			SnomedUtils.isValid(sctId, PartitionIdentifier.CONCEPT, true);
		} else {
			String msg = SnomedUtils.isValid(sctId, PartitionIdentifier.CONCEPT);
			if (msg != null) {
				LOGGER.warn(msg);
			}
		}
		
		Concept c = concepts.get(sctId);
		if (c == null) {
			if (createIfRequired) {
				c = new Concept(sctId);
				concepts.put(sctId, c);
			} else if (validateExists) {
				throw new TermServerScriptException("Expected Concept '" + sctId + "' has not been loaded from archive");
			}
		}
		return c;
	}
	
	public Description getDescription(String sctId) throws TermServerScriptException {
		return getDescription(sctId, true, true);
	}
	
	public Description getDescription(Long sctId) throws TermServerScriptException {
		return getDescription(sctId.toString(), true, true);
	}
	
	public Description getDescription(String sctId, boolean createIfRequired, boolean validateExists) throws TermServerScriptException {
		Description d = descriptions.get(sctId);
		if (d == null) {
			if (createIfRequired) {
				d = new Description(sctId);
				descriptions.put(sctId, d);
			} else if (validateExists) {
				throw new TermServerScriptException("Expected Description " + sctId + " has not been loaded from archive");
			}
		}
		return d;
	}
	
	public void loadConceptFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				
				/*if (lineItems[IDX_ID].equals("394576009")) {
					TermServerScript.debug("here");
				}*/

				//We might already have received some details about this concept
				Concept c = getConcept(lineItems[IDX_ID]);
				
				//If moduleId is null, then this concept has no prior state
				if (isRecordPreviousState()) {
					if (isReleased == null) {
						throw new IllegalStateException("Unable to record previous state from existing archive");
					}
					
					if (!isReleased  && c.getModuleId() != null) {
						String previousState = c.getMutableFields();
						c.addIssue(previousState);
					}
				}
				
				//If the concept's module isn't known, then it wasn't loaded in the snapshot
				String revertEffectiveTime = null;
				if (detectNoChangeDelta && !isReleased && c.getModuleId() != null) {
					revertEffectiveTime = detectNoChangeDelta(c, c, lineItems);
				}
				
				//If we've already received a newer version of this component, say
				//by loading INT first and a published MS 2nd, then skip
				if (!StringUtils.isEmpty(c.getEffectiveTime()) 
						&& (isReleased != null && isReleased)
						&& (c.getEffectiveTime().compareTo(lineItems[IDX_EFFECTIVETIME]) >= 1)) {
					//System.out.println("Skipping incoming published concept row, older than that held");
					continue;
				}
				
				Concept.fillFromRf2(c, lineItems);
				//Now we might have changed the moduleId if the delta is in another module, but this 
				//doesn't make the RF2 "dirty" because that change hasn't been made by THIS process
				c.setClean();
				
				if (revertEffectiveTime != null) {
					c.setEffectiveTime(revertEffectiveTime);
				}
				
				//Only set the released flag if it's not set already
				if (c.isReleased() == null) {
					c.setReleased(isReleased);
				}
				
				if (c.getDefinitionStatus() == null) {
					throw new TermServerScriptException("Concept " + c + " did not define definition status");
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	public int loadDescriptionFile(InputStream descStream, boolean fsnOnly, Boolean isReleased) throws IOException, TermServerScriptException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(descStream, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		int count = 0;
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				
				/*if (lineItems[DES_IDX_ID].equals("63241000195117")) {
					TermServerScript.debug("Debug Here");
				}*/
				
				Concept c = getConcept(lineItems[DES_IDX_CONCEPTID]);
				
				if (isRunIntegrityChecks()) {
					SnomedUtils.isValid(lineItems[IDX_ID], PartitionIdentifier.DESCRIPTION, true);
				} else {
					String msg = SnomedUtils.isValid(lineItems[IDX_ID], PartitionIdentifier.DESCRIPTION);
					if (msg != null) {
						TermServerScript.warn(msg);
					}
				}
				
				if (!fsnOnly || lineItems[DES_IDX_TYPEID].equals(FSN)) {
					//We might already have information about this description, eg langrefset entries
					Description d = getDescription(lineItems[DES_IDX_ID]);
					
					//If the term is null, then this is the first we've seen of this description, so no
					//need to record its previous state.
					if (isRecordPreviousState() && !isReleased && d.getTerm() != null) {
						String previousState = d.getMutableFields();
						d.addIssue(previousState);
					}
					
					//If we've already received a newer version of this component, say
					//by loading INT first and a published MS 2nd, then skip
					if (!StringUtils.isEmpty(d.getEffectiveTime()) 
							&& isReleased
							&& (d.getEffectiveTime().compareTo(lineItems[IDX_EFFECTIVETIME]) >= 1)) {
						//System.out.println("Skipping incoming published description row, older than that held");
						continue;
					}
					
					//But if the module is not known, it's new
					String revertEffectiveTime = null;
					if (detectNoChangeDelta && !isReleased && d.getModuleId() != null) {
						revertEffectiveTime = detectNoChangeDelta(c, d, lineItems);
					}
					Description.fillFromRf2(d,lineItems);
					//Now we might have changed the moduleId if the delta is in another module, but this 
					//doesn't make the RF2 "dirty" because that change hasn't been made by THIS process
					d.setClean();
					
					if (revertEffectiveTime != null) {
						d.setEffectiveTime(revertEffectiveTime);
					}
					
					//Only set the released flag if it's not set already
					if (d.isReleased() == null) {
						d.setReleased(isReleased);
					}
					
					c.addDescription(d);
					count++;
				}
			} else {
				isHeader = false;
			}
		}
		return count;
	}

	public Set<Concept> loadRelationshipDelta(CharacteristicType characteristicType, InputStream relStream) throws IOException, TermServerScriptException {
		return loadRelationships(characteristicType, relStream, true, true, false);
	}

	public Set<Concept> getModifiedConcepts(
			CharacteristicType characteristicType, ZipInputStream relStream) throws IOException, TermServerScriptException {
		return loadRelationships(characteristicType, relStream, false, false, false);
	}

	public void loadLanguageFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		int attemptPublishedRemovals = 0;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				Description d = getDescription(lineItems[LANG_IDX_REFCOMPID]);
				LangRefsetEntry langRefsetEntry = LangRefsetEntry.fromRf2(lineItems);
				
				/*if (langRefsetEntry.getId().equals("85a10b1f-a8ad-4c73-92bd-d6a0ef67cfa8")) {
					TermServerScript.debug("here");
				}
				
				if (langRefsetEntry.getId().equals("e66a48eb-9824-4a62-99ff-ee7058b878ca")) {
					TermServerScript.debug("here");
				}*/
				/*if (langRefsetEntry.getReferencedComponentId().equals("2643877015") || langRefsetEntry.getReferencedComponentId().equals("2643878013")) {
					TermServerScript.debug("here");
				}*/
				//Are we adding or replacing this entry?
				if (d.getLangRefsetEntries().contains(langRefsetEntry)) {
					LangRefsetEntry original = d.getLangRefsetEntry(langRefsetEntry.getId());
					
					if (isRecordPreviousState() && original != null && !isReleased) {
						String previousState = original.getMutableFields();
						langRefsetEntry.addIssue(previousState);
					}
					
					//If we've already received a newer version of this component, say
					//by loading INT first and a published MS 2nd, then skip
					if (!StringUtils.isEmpty(original.getEffectiveTime()) 
							&& (isReleased != null && isReleased)
							&& (original.getEffectiveTime().compareTo(lineItems[IDX_EFFECTIVETIME]) >= 1)) {
						//System.out.println("Skipping incoming published langrefset row, older than that held");
						continue;
					}
					
					//Set Released Flag if our existing entry has it
					if (original.isReleased()) {
						langRefsetEntry.setReleased(true);
					}
					//If we're working with not-released data and we already have a not-released entry
					//then there's two copies of this langrefset entry in a delta
					//We don't have to worry about this when loading a pre-created snapshot as the duplicates
					//will already have been removed.
					if (isReleased != null && !isReleased && StringUtils.isEmpty(original.getEffectiveTime())) {
						//Have we already reported this duplicate?
						if (duplicateLangRefsetIdsReported.contains(original)) {
							TermServerScript.warn("Seeing additional duplication for " + original.getId());
						} else {
							TermServerScript.warn("Seeing duplicate langrefset entry in a delta: \n" + original.toString(true) + "\n" + langRefsetEntry.toString(true));
							duplicateLangRefsetIdsReported.add(original);
						}
					}
					d.getLangRefsetEntries().remove(original);
				}
				
				if (langRefsetEntry.isReleased() == null) {
					langRefsetEntry.setReleased(isReleased);
				}
				
				//Complexity here that we've historically had language refset entries
				//for the same description which attempt to cancel each other out using
				//different UUIDs.  Therefore if we get a later entry inactivating a given
				//dialect, then allow that to overwrte an earlier value with a different UUUID
				
				//Do we have an existing entry for this description & dialect that is later and inactive?
				boolean clearToAdd = true;
				List<LangRefsetEntry> allExisting = d.getLangRefsetEntries(ActiveState.BOTH, langRefsetEntry.getRefsetId());
				for (LangRefsetEntry existing : allExisting) {
					//If we have two active for the same description, and neither has an effectiveTime delete the one that hasn't been published
					//Only if we're loading a delta, otherwise it's published
					if (isReleased != null && !isReleased) {
						checkForActiveDuplication(d, existing, langRefsetEntry);
					}
					
					if (existing.getEffectiveTime().compareTo(langRefsetEntry.getEffectiveTime()) <= 1) {
						clearToAdd = false;
					} else if (existing.getEffectiveTime().equals(langRefsetEntry.getEffectiveTime())) {
						//As long as they have different UUIDs, it's OK to have the same effective time
						//But we'll ignore the inactivation
						if (!langRefsetEntry.isActive()) {
							clearToAdd = false;
						}
					} else {
						//New entry is later or same effective time as one we already know about
						if (!SnomedUtils.isEmpty(existing.getEffectiveTime()) && !existing.getId().equals(langRefsetEntry.getId())) {
							attemptPublishedRemovals++;
							if (attemptPublishedRemovals < 5) {
								System.err.println ("Attempt to remove published entry: " + existing.toStringWithModule() + " by " + langRefsetEntry.toStringWithModule());
							}
						} else {
							d.getLangRefsetEntries().remove(existing);
						}
					}
				}
				
				//INFRA-5274 We're going to add the entry in all cases so we can detect duplicates,
				//but we'll only set the acceptability on the description if the above code decided it was safe
				d.getLangRefsetEntries().add(langRefsetEntry);
				
				if (clearToAdd) {
					if (lineItems[LANG_IDX_ACTIVE].equals("1")) {
						Acceptability a = SnomedUtils.translateAcceptability(lineItems[LANG_IDX_ACCEPTABILITY_ID]);
						d.setAcceptability(lineItems[LANG_IDX_REFSETID], a);
					} else {
						d.removeAcceptability(lineItems[LANG_IDX_REFSETID]);
					}
				}
			} else {
				isHeaderLine = false;
			}
		}
		if (attemptPublishedRemovals > 0)  {
			System.err.println ("Attempted to remove " + attemptPublishedRemovals + " published entries in total");
		}
	}

	
	private void checkForActiveDuplication(Description d, LangRefsetEntry l1, LangRefsetEntry l2) throws TermServerScriptException {
		if (l1.isActive() && l2.isActive()) {
			Set<DuplicatePair> duplicates = getLangRefsetDuplicates(d);
			duplicates.add(new DuplicatePair(l1, l2));  //Keep the first, with the intention to inactivate (or delete) the second
			System.err.println("Noting langrefset as duplicate with " + l1.getId() + " : " + l2.getId() + " - " + l2);
		}
		
		if (l1.isActive() != l2.isActive()) {
			Set<DuplicatePair> duplicates = getLangRefsetDuplicates(d);
			DuplicatePair pair = new DuplicatePair(l1, l2);
			if (duplicates.contains(pair)) {
				duplicates.add(pair);  //Keep the first, with the intention to inactivate (or delete) the second
				System.err.println("Replacing langrefset duplicate (now inactive) " + l1.getId() + " : " + l2.getId() + " - " + l1);
			}
			//Or did these two meet in the opposite order last time?s
			DuplicatePair pair2 = new DuplicatePair(l2, l1);
			if (duplicates.contains(pair2)) {
				duplicates.remove(pair2);  //Not quite identical, first one is active
				duplicates.add(pair2);  //Keep the first, with the intention to inactivate (or delete) the second
				System.err.println("Replacing langrefset duplicate (now inactive) " + l2.getId() + " : " + l1.getId() + " - " + l1);
			}
		}
	}
	
	private Set<DuplicatePair> getLangRefsetDuplicates(Description d) throws TermServerScriptException {
		Concept c = getConcept(d.getConceptId());
		if (duplicateLangRefsetEntriesMap == null) {
			duplicateLangRefsetEntriesMap = new HashMap<>();
		}
		Set<DuplicatePair> duplicates = duplicateLangRefsetEntriesMap.get(c);
		if (duplicates == null) {
			duplicates = new HashSet<>();
			duplicateLangRefsetEntriesMap.put(c,  duplicates);
		}
		return duplicates;
	}

	/**
	 * Recurse hierarchy and set shortest path depth for all concepts
	 * @throws TermServerScriptException 
	 */
	public void populateHierarchyDepth(Concept startingPoint, int currentDepth) throws TermServerScriptException {
		startingPoint.setDepth(currentDepth);
		/*if (startingPoint.getConceptId().equals("210431006")) {
			//TermServerScript.debug ("Checkpoint");
		}*/
		
		for (Concept child : startingPoint.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (currentDepth >= MAX_DEPTH) {
				throw new TermServerScriptException("Maximum depth exceeded from " + startingPoint + " and inferred child " + child);
			}
			try {
				populateHierarchyDepth(child, currentDepth + 1);
			} catch (TermServerScriptException e) {
				TermServerScript.debug ("Exception path: " + startingPoint + " -> " + child);
				throw (e);
			}
		}
	}

	public void loadInactivationIndicatorFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				String id = lineItems[IDX_ID];
				
				/*if (id.equals("f0046c6b-287b-545f-aae5-f0a4b9946a0a")) {
					TermServerScript.debug("here");
				}*/
				
				String revertEffectiveTime = null;
				if (detectNoChangeDelta && isReleased != null && !isReleased) {
					//Recover this entry for the component - concept or description
					InactivationIndicatorEntry i = getInactivationIndicatorEntry(lineItems[REF_IDX_REFCOMPID], lineItems[IDX_ID]);
					if (i != null) {
						Component c = SnomedUtils.getParentComponent(i, this);
						revertEffectiveTime = detectNoChangeDelta(c, i, lineItems);
					}
				}

				InactivationIndicatorEntry inactivation = InactivationIndicatorEntry.fromRf2(lineItems);
				
				//Only set the released flag if it's not set already
				if (inactivation.isReleased() == null) {
					inactivation.setReleased(isReleased);
				}
				
				if (revertEffectiveTime != null) {
					inactivation.setEffectiveTime(revertEffectiveTime);
				}
				
				if (inactivation.getRefsetId().equals(SCTID_CON_INACT_IND_REFSET)) {
					Concept c = getConcept(lineItems[INACT_IDX_REFCOMPID]);
					/*if (c.getConceptId().equals("198308002")) {
						TermServerScript.debug("Check Here");
					}*/
					//Do we already have this indicator?  Copy the released flag if so
					InactivationIndicatorEntry existing = c.getInactivationIndicatorEntry(id);
					if (existing != null) {
						inactivation.setReleased(existing.getReleased());
						if (isRecordPreviousState() && !isReleased) {
							String previousState = existing.getMutableFields();
							inactivation.addIssue(previousState);
						}
					}
					
					c.addInactivationIndicator(inactivation);
				} else if (inactivation.getRefsetId().equals(SCTID_DESC_INACT_IND_REFSET)) {
					Description d = getDescription(lineItems[INACT_IDX_REFCOMPID]);
					/*if (d.getDescriptionId().equals("1221136011")) {
						TermServerScript.debug("Check here");
					}*/
					//Do we already have this indicator?  Copy the released flag if so
					InactivationIndicatorEntry existing = d.getInactivationIndicatorEntry(id);
					if (existing != null) {
						inactivation.setReleased(existing.getReleased());
						if (isRecordPreviousState() && !isReleased) {
							String previousState = existing.getMutableFields();
							inactivation.addIssue(previousState);
						}
					}
					
					d.addInactivationIndicator(inactivation);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	private InactivationIndicatorEntry getInactivationIndicatorEntry(String componentId, String indicatorId) throws TermServerScriptException {
		if (SnomedUtils.isConceptSctid(componentId)) {
			Concept c = getConcept(componentId, false, false);
			if (c != null) {
				return c.getInactivationIndicatorEntry(indicatorId);
			}
		} else {
			Description d = getDescription(componentId);
			if (d != null) {
				return d.getInactivationIndicatorEntry(indicatorId);
			}
		}
		return null;
	}

	public void loadHistoricalAssociationFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				String id = lineItems[IDX_ID];
				String referencedComponent = lineItems[INACT_IDX_REFCOMPID];
				if (isConcept(referencedComponent)) {
					Concept c = getConcept(referencedComponent);
					
					/*if (c.getId().equals("140506004")) {
						System.out.println("here");
					}*/
					
					String revertEffectiveTime = null;
					if (detectNoChangeDelta && isReleased != null && !isReleased) {
						//Recover this entry for the component - concept or description
						AssociationEntry a = getAssociationEntry(lineItems[REF_IDX_REFCOMPID], lineItems[IDX_ID]);
						if (a != null) {
							Component comp = SnomedUtils.getParentComponent(a, this);
							revertEffectiveTime = detectNoChangeDelta(comp, a, lineItems);
						}
					}

					AssociationEntry association = AssociationEntry.fromRf2(lineItems);
					
					//Only set the released flag if it's not set already
					if (association.isReleased() == null) {
						association.setReleased(isReleased);
					}
					
					if (revertEffectiveTime != null) {
						association.setEffectiveTime(revertEffectiveTime);
					}
					
					//Do we already have this association?  Copy the released flag if so
					AssociationEntry existing = c.getAssociationEntry(id);
					if (existing != null) {
						association.setReleased(existing.getReleased());
						if (isRecordPreviousState() && !isReleased) {
							String previousState = existing.getMutableFields();
							association.addIssue(previousState);
						}
					}
					
					//Remove first in case we're replacing
					c.getAssociationEntries().remove(association);
					c.getAssociationEntries().add(association);
					if (association.isActive()) {
						SnomedUtils.addHistoricalAssociationInTsForm(c, association);
						recordHistoricalAssociation(association);
					}
				} else if (isDescription(referencedComponent)) {
					Description d = getDescription(referencedComponent);
					AssociationEntry association = AssociationEntry.fromRf2(lineItems);
					
					//Only set the released flag if it's not set already
					if (association.isReleased() == null) {
						association.setReleased(isReleased);
					}
					
					//Do we already have this association?  Copy the released flag if so
					AssociationEntry existing = d.getAssociationEntry(id);
					if (existing != null) {
						association.setReleased(existing.getReleased());
						if (isRecordPreviousState() && !isReleased) {
							String previousState = existing.getMutableFields();
							association.addIssue(previousState);
						}
					}
					
					//Remove first in case we're replacing
					d.getAssociationEntries().remove(association);
					d.getAssociationEntries().add(association);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	
	private AssociationEntry getAssociationEntry(String componentId, String assocId) throws TermServerScriptException {
		if (SnomedUtils.isConceptSctid(componentId)) {
			Concept c = getConcept(componentId, false, false);
			if (c != null) {
				return c.getAssociationEntry(assocId);
			}
		} else {
			Description d = getDescription(componentId, false, false);
			if (d != null) {
				return d.getAssociationEntry(assocId);
			}
		}
		return null;
	}
	
	private void recordHistoricalAssociation(AssociationEntry h) throws TermServerScriptException {
		//Remember we're using the target of the association as the map key here
		Concept target = getConcept(h.getTargetComponentId());
		//Have we seen this concept before?
		List<AssociationEntry> associations;
		if (historicalAssociations.containsKey(target)) {
			associations = historicalAssociations.get(target);
		} else {
			associations = new ArrayList<AssociationEntry>();
			historicalAssociations.put(target, associations);
		}
		associations.add(h);
	}
	
	public List<AssociationEntry> usedAsHistoricalAssociationTarget (Concept c) {
		if (historicalAssociations.containsKey(c)) {
			return historicalAssociations.get(c);
		}
		return new ArrayList<AssociationEntry>();
	}
	
	public boolean isUsedAsHistoricalAssociationTarget (Concept c) {
		return historicalAssociations.containsKey(c);
	}
	
	public void loadMRCMAttributeRangeFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				//Allow trailing empty fields
				String[] lineItems = line.split(FIELD_DELIMITER, -1);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				MRCMAttributeRange ar = MRCMAttributeRange.fromRf2(lineItems);
				
				//Only set the released flag if it's not set already
				if (ar.isReleased() == null) {
					ar.setReleased(isReleased);
				}
				Concept refComp = getConcept(ar.getReferencedComponentId());
				String contentTypeId = lineItems[MRCM_ATTRIB_CONTENT_TYPE];
				
				switch(contentTypeId) {
					case SCTID_PRE_COORDINATED_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapPreCoord, refComp, ar);
					break;
					case SCTID_POST_COORDINATED_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapPostCoord, refComp, ar);
					break;
					case SCTID_ALL_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapAll, refComp, ar);
					break;
					case SCTID_NEW_PRE_COORDINATED_CONTENT : addToMRCMAttributeMap(mrcmStagingAttributeRangeMapNewPreCoord, refComp, ar);
					break;
					default : throw new TermServerScriptException("Unrecognised content type in MRCM Attribute Range File: " + contentTypeId);
				}
			} else {
				isHeaderLine = false;
			}
		}
	}
	
	private void addToMRCMAttributeMap(Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttribMap, Concept refComp, MRCMAttributeRange ar) throws TermServerScriptException {
		//Do we already have an entry for this referencedCompoment id?
		/*if (mrcmAttribMap.containsKey(refComp)) {
			MRCMAttributeRange existing = mrcmAttribMap.get(refComp);
			//If this one is inactive and there is an existing one active with a different id, then 
			//we'll just keep the existing one.
			if (!existing.getId().equals(ar.getId()) && existing.isActive() && !ar.isActive()) {
				return;
			}
			//If it's the same NOT the same id, we have a problem if the existing one is also active
			//We'll collect all conflicts up and report back on all of them in the calling function
			if (!existing.getId().equals(ar.getId()) && existing.isActive() && ar.isActive()) {
				String contentType = translateContentType(ar.getContentTypeId());
				conflictingAttributes.add(contentType + ": " + refComp);
			}
		}
		mrcmAttribMap.put(refComp, ar);*/
		//We'll add all entries to a staging structure initially, to allow conflicts to be resolved
		//in a delta.
		Map<String, MRCMAttributeRange> attribRanges = mrcmStagingAttribMap.get(refComp);
		if (attribRanges == null) {
			attribRanges = new HashMap<>();
			mrcmStagingAttribMap.put(refComp, attribRanges);
		}
		//This will overwrite any existing MRCM row with the same UUID
		//And allow multiple rows for exist for a given referenced component id
		attribRanges.put(ar.getId(), ar);
	}

	private String translateContentType(String contentTypeId) throws TermServerScriptException {
		switch (contentTypeId) {
			case SCTID_PRE_COORDINATED_CONTENT : return " Pre-coordinated content ";
			case SCTID_POST_COORDINATED_CONTENT : return " Post-coordinated content ";
			case SCTID_ALL_CONTENT : return " All SNOMED content ";
			case SCTID_NEW_PRE_COORDINATED_CONTENT : return " New Pre-coordinated content ";
			default : throw new TermServerScriptException ("Unrecognised MRCM content type encountered: " + contentTypeId);
		}
	}

	public void loadMRCMDomainFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				//Allow trailing empty fields
				String[] lineItems = line.split(FIELD_DELIMITER, -1);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				MRCMDomain d = MRCMDomain.fromRf2(lineItems);
				
				//Only set the released flag if it's not set already
				if (d.isReleased() == null) {
					d.setReleased(isReleased);
				}
				Concept refComp = getConcept(d.getReferencedComponentId());
				mrcmDomainMap.put(refComp, d);
			} else {
				isHeaderLine = false;
			}
		}
	}

	public Component getComponent(String id) {
		if (allComponents == null) {
			populateAllComponents();
		}
		return allComponents.get(id);
	}
	
	public final Map<String,Component> getComponentMap() {
		if (allComponents == null) {
			populateAllComponents();
		}
		//Don't create a new hashmap.  Caller can do this if they think
		//it's going to change.
		return allComponents;
	}
	
	public Concept getComponentOwner(String id) {
		Component component = getComponent(id);
		return componentOwnerMap.get(component);
	}

	private void populateAllComponents() {
		System.out.println("Populating maps of all components...");
		allComponents = new HashMap<String, Component>();
		componentOwnerMap = new HashMap<Component, Concept>();
		
		for (Concept c : getAllConcepts()) {
			
			/*if (c.getId().equals("128559007")) {
				TermServerScript.debug("here");
			}*/
			allComponents.put(c.getId(), c);
			componentOwnerMap.put(c,  c);
			for (Description d : c.getDescriptions()) {
				/*if (d.getId().equals("5169695010")) {
					TermServerScript.debug("Debug here\n");
				}*/
				populateDescriptionComponents(c, d);
			}
			
			for (Relationship r : c.getRelationships()) {
				//A relationship with a null ID will have come from an axiom.
				//We'll let the axiomEntry cover that.
				if (r.fromAxiom()) {
					continue;
				}
				
				if (r.getRelationshipId() == null) {
					throw new IllegalArgumentException ("Rel ID not expected to be null");
				}
				//Have we historically swapped ID from stated to inferred
				if (allComponents.containsKey(r.getRelationshipId())) {
					if (r.isActive()) {
						System.out.println("All Components Map replacing '" + r.getRelationshipId() + "' " + allComponents.get(r.getRelationshipId()) + " with active " + r);
						allComponents.put(r.getRelationshipId(), r);
					} else if (allComponents.get(r.getRelationshipId()).isActive()) {
						System.out.println("Ignoring inactive '" + r.getRelationshipId() + "' " + r + " due to already having " + allComponents.get(r.getRelationshipId()));
					} else {
						System.out.println("Two inactive components share the same id of " + r.getId() + ": " + r + " and " + allComponents.get(r.getId()));
					}
				} else {
					allComponents.put(r.getRelationshipId(), r);
				}
				
				componentOwnerMap.put(r,  c);
			}

			for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
				allComponents.put(i.getId(), i);
				componentOwnerMap.put(i,  c);
			}
			
			for (AssociationEntry h : c.getAssociationEntries()) {
				allComponents.put(h.getId(), h);
				componentOwnerMap.put(h,  c);
			}
			
			for (AxiomEntry a : c.getAxiomEntries()) {
				allComponents.put(a.getId(), a);
				componentOwnerMap.put(a, c);
			}
		}
		System.out.print("Component owner map complete with " + componentOwnerMap.size() + " entries.\n");
	}

	private void populateDescriptionComponents(Concept c, Description d) {
		allComponents.put(d.getDescriptionId(), d);
		componentOwnerMap.put(d, c);
		
		for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
			allComponents.put(i.getId(), i);
			componentOwnerMap.put(i, c);
		}
		
		for (LangRefsetEntry l : d.getLangRefsetEntries()) {
			allComponents.put(l.getId(), l);
			componentOwnerMap.put(l, c);
		}
	}

	public String listAssociationParticipation(Concept c) {
		return historicalAssociations.get(c).stream()
				.map(assoc -> assoc.toVerboseString())
				.collect (Collectors.joining(", "));
	}

	public void registerConcept(Concept concept) {
		concepts.put(concept.getConceptId(), concept);
	}
	
	public Concept registerConcept(String sctIdFSN) {
		Concept concept = Concept.withDefaultsFromSctIdFsn(sctIdFSN);
		concepts.put(concept.getConceptId(), concept);
		return concept;
	}

	public DescendantsCache getDescendantsCache() {
		return descendantsCache;
	}
	
	public DescendantsCache getStatedDescendantsCache() {
		return statedDescendantsCache;
	}

	public AncestorsCache getAncestorsCache() {
		return ancestorsCache;
	}
	
	public AncestorsCache getStatedAncestorsCache() {
		return statedAncestorsCache;
	}

	public Concept getComponentOwner(ComponentType componentType, Component c) throws TermServerScriptException {
		if (c==null) {
			return null;
		}
		//Work out the owning component given the known type of this component
		switch (componentType) {
			case CONCEPT : //Concepts own themselves
							return (Concept)c;
			case DESCRIPTION :
			case TEXT_DEFINITION : return getConcept(((Description)c).getConceptId());
			case INFERRED_RELATIONSHIP:
			case STATED_RELATIONSHIP : return getConcept(((Relationship)c).getSourceId());
			case HISTORICAL_ASSOCIATION : String referencedComponentId = ((AssociationEntry)c).getReferencedComponentId();
											ComponentType referencedType = SnomedUtils.getComponentType(referencedComponentId);
											return getComponentOwner(referencedType, getComponent(referencedComponentId));
			case ATTRIBUTE_VALUE : referencedComponentId = ((InactivationIndicatorEntry)c).getReferencedComponentId();
											referencedType = SnomedUtils.getComponentType(referencedComponentId);
											return getComponentOwner(referencedType, getComponent(referencedComponentId));
			case LANGREFSET : referencedComponentId = ((LangRefsetEntry)c).getReferencedComponentId();
											referencedType = SnomedUtils.getComponentType(referencedComponentId);
											return getComponentOwner(referencedType, getComponent(referencedComponentId));
			default: throw new TermServerScriptException("Unknown component Type: " + componentType);
		}
	}
	
	public Component createComponent(ComponentType componentType, String[] lineItems) throws TermServerScriptException {
		//Work out the owning component given the known type of this component
		switch (componentType) {
			case CONCEPT :	Concept c = getConcept(lineItems[IDX_ID]);
							Concept.fillFromRf2(c, lineItems);
							return c;
			case DESCRIPTION :
			case TEXT_DEFINITION :	Description d = getDescription (lineItems[DES_IDX_ID]);
									Description.fillFromRf2(d,lineItems);
									return d;
			case INFERRED_RELATIONSHIP: return createRelationshipFromRF2(CharacteristicType.INFERRED_RELATIONSHIP, lineItems);
			case STATED_RELATIONSHIP : return createRelationshipFromRF2(CharacteristicType.STATED_RELATIONSHIP, lineItems);
			case HISTORICAL_ASSOCIATION : return AssociationEntry.fromRf2(lineItems);
			case ATTRIBUTE_VALUE : return  InactivationIndicatorEntry.fromRf2(lineItems);
			case LANGREFSET : return LangRefsetEntry.fromRf2(lineItems);
		default: throw new TermServerScriptException("Unknown component Type: " + componentType);
		}
	}
	
	public synchronized Collection<String> getOrphanetConceptIds() {
		if (orphanetConceptIds == null) {
			TermServerScript.print("Loading list of Orphanet Concepts...");
			try {
				InputStream is = GraphLoader.class.getResourceAsStream("/data/orphanet_concepts.txt");
				if (is == null) {
					throw new RuntimeException ("Failed to load Orphanet data file - not found.");
				}
				orphanetConceptIds = IOUtils.readLines(is, "UTF-8").stream()
						.map(s -> s.trim())
						.collect(Collectors.toSet());
			} catch (Exception e) {
				throw new RuntimeException ("Failed to load list of Orphanet Concepts",e);
			}
			TermServerScript.println("complete.");
		}
		return Collections.unmodifiableCollection(orphanetConceptIds);
	}

	public boolean isOrphanetConcept (Concept c) {
		return getOrphanetConceptIds().contains(c.getId());
	}

	public void makeReady() {
		for (Concept c : concepts.values()) {
			c.setIssue(null);
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
				g.setIssues(null);
				g.resetIndicators();
			}
		}
	}
	
	public AxiomRelationshipConversionService getAxiomService() {
		return axiomService;
	}

	public void populatePreviousTransativeClosure() throws TermServerScriptException {
		LOGGER.info("Populating PREVIOUS transitive closure");
		previousTransitiveClosure = generateTransativeClosure();
		LOGGER.info("PREVIOUS transitive closure complete");
	}
	
	public TransitiveClosure getTransitiveClosure() throws TermServerScriptException {
		if (transitiveClosure == null) {
			transitiveClosure = generateTransativeClosure();
		}
		return transitiveClosure;
	}
	
	public TransitiveClosure generateTransativeClosure() throws TermServerScriptException {
		LOGGER.info ("Calculating transative closure...");
		TransitiveClosure tc = new TransitiveClosure();
		//For all active concepts, populate their ancestors into the TC
		getAllConcepts().parallelStream().forEach(c->{
			try {
				tc.addConcept(c);
			} catch (TermServerScriptException e) {
				e.printStackTrace();
			} 
		});
		LOGGER.info ("Completed transative closure: " + tc.size() + " relationships mapped");
		return tc;
	}

	public TransitiveClosure getPreviousTC() {
		return previousTransitiveClosure;
	}

	public void setExcludedModules(HashSet<String> excludedModules) {
		this.excludedModules = excludedModules;
	}
	
	public Map<Concept, Set<DuplicatePair>> getDuplicateLangRefsetEntriesMap() {
		return duplicateLangRefsetEntriesMap;
	}

	public void setDetectNoChangeDelta(boolean detectNoChangeDelta) {
		this.detectNoChangeDelta = detectNoChangeDelta;
	}
	
	/**
	 * @return the effective time to revert back to if a no change delta is detected
	 * @throws TermServerScriptException
	 */
	private String detectNoChangeDelta(Component parent, Component c, String[] lineItems) throws TermServerScriptException {
		try {
			//If the parent or the component itself was not previously known, 
			//then it's brand new, so not a no-change delta
			if (parent == null || c == null) {
				return null;
			}
			//Is the component in it's original state any different to the new rows?
			if (!differsOtherThanEffectiveTime(c.toRF2(), lineItems)) {
				TermServerScript.warn("No change delta detected for " + c + " reverting effective time");
				c.setDirty();
				return c.getEffectiveTime();
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to check delta change", e);
		}
		return null;
	}
	
	public static boolean differsOtherThanEffectiveTime(String[] a, String[] b) {
		//Return true if a field other than effectiveTime is different
		for (int i=0; i<a.length; i++) {
			if (i!=IDX_EFFECTIVETIME && !a[i].equals(b[i])) {
				return true;
			}
		}
		return false;
	}
	
	public String convertRelationshipsToOwlExpression(Concept c, Set<Relationship> relationships) throws TermServerScriptException {
		try {
			AxiomRepresentation axiom = new AxiomRepresentation();
			//Assuming a normal RHS definition
			axiom.setLeftHandSideNamedConcept(Long.parseLong(c.getId()));
			axiom.setRightHandSideRelationships(AxiomUtils.convertRelationshipsToMap(relationships));
			return axiomService.convertRelationshipsToAxiom(axiom);
		} catch (ConversionException e) {
			throw new TermServerScriptException(e);
		}
	}

	public void loadReferenceSets(InputStream is, String fileName, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String headerLine = br.readLine();

		if (!headerLine.startsWith("id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId")) {
			LOGGER.warn("Ignoring RefSet file, wrong header: {}", headerLine);
			return;
		}

		//The columns after the referenced component id give us our additional field names
		String[] fieldNames = headerLine.split(TAB);
		String[] additionalFieldNames = Arrays.copyOfRange(fieldNames, REF_IDX_FIRST_ADDITIONAL, fieldNames.length);

		LOGGER.info("Loading reference set file {}", fileName);
		for (String line = br.readLine(); line != null; line = br.readLine()) {
			String[] lineItems = line.split(TAB);
			RefsetMember member = new RefsetMember();
			RefsetMember.populatefromRf2(member, lineItems, additionalFieldNames);
			if (isReleased != null) {
				member.setReleased(isReleased);
			}
			/*if (member.getId().equals("acc12fa8-74db-5e6e-9408-5ea60cd650e6")) {
				LOGGER.info("Check here");
			}*/
			//Do we already have this member?  Copy the released flag if so
			if (SnomedUtils.isConceptSctid(member.getReferencedComponentId())) {
				Concept c = getConcept(member.getReferencedComponentId());
				RefsetMember existing = c.getOtherRefsetMember(member.getId());
				if (existing != null) {
					member.setReleased(existing.getReleased());
				}
				c.addOtherRefsetMember(member);
			} else {
				Description d = getDescription(member.getReferencedComponentId());
				RefsetMember existing = d.getOtherRefsetMember(member.getId());
				if (existing != null) {
					member.setReleased(existing.getReleased());
				}
				d.getOtherRefsetMembers().add(member);
			}
		}
	}

    public void removeConcept(Concept c) {
		concepts.remove(c.getId());
		if (allComponents != null) {
			allComponents.remove(c.getId());
		}
    }

    public class DuplicatePair {
		private Component keep;
		private Component inactivate;
		
		DuplicatePair (Component keep, Component inactivate) {
			this.keep = keep;
			this.inactivate = inactivate;
		}
		
		public Component getKeep() {
			return keep;
		}
		public Component getInactivate() {
			return inactivate;
		}
		
		@Override
		public int hashCode() {
			return (keep.getId() + "_" +  inactivate.getId()).hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof DuplicatePair)) {
				return false;
			}
			DuplicatePair d2 = (DuplicatePair) obj;
			if (keep.getId().equals(d2.keep.getId()) &&
					inactivate.getId().equals(d2.inactivate.getId())) {
				return true;
			}
			return false;
		}
	}

	public String getCurrentEffectiveTime() {
		//Loop through all components to fine the latest effective time
		String maxEffectiveTime = "0";
		
		//Populating componentMap proved too expensive.  Loop through manually.
		for (Concept c : getAllConcepts()) {
			if (c.getEffectiveTime() != null && c.getEffectiveTime().compareTo(maxEffectiveTime) > 0)  {
				maxEffectiveTime = c.getEffectiveTime();
			}
			for (Description d : c.getDescriptions()) {
				if (d.getEffectiveTime() != null && d.getEffectiveTime().compareTo(maxEffectiveTime) > 0)  {
					maxEffectiveTime = d.getEffectiveTime();
				}
			}
			for (Relationship r : c.getRelationships()) {
				if (r.getEffectiveTime() != null && r.getEffectiveTime().compareTo(maxEffectiveTime) > 0)  {
					maxEffectiveTime = r.getEffectiveTime();
				}
			}
		}
		return maxEffectiveTime;
	}

	//Note: No need to return the staging variants of these, as they're only used
	//temporarily by the GraphLoader
	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapPreCoord() {
		return mrcmAttributeRangeMapPreCoord;
	}
	
	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapPostCoord() {
		return mrcmAttributeRangeMapPostCoord;
	}
	
	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapAll() {
		return mrcmAttributeRangeMapAll;
	}
	
	public Map<Concept, MRCMAttributeRange> getMrcmAttributeRangeMapNewPreCoord() {
		return mrcmAttributeRangeMapNewPreCoord;
	}

	public Map<Concept, MRCMDomain> getMrcmDomainMap() {
		return mrcmDomainMap;
	}

	public boolean isRunIntegrityChecks() {
		return runIntegrityChecks;
	}

	public void setRunIntegrityChecks(boolean runIntegrityChecks) {
		this.runIntegrityChecks = runIntegrityChecks;
	}

	public boolean isRecordPreviousState() {
		return recordPreviousState;
	}

	public void setRecordPreviousState(boolean recordPreviousState) {
		this.recordPreviousState = recordPreviousState;
	}

	public void setAllComponentsClean() {
		for (Concept concept : getAllConcepts()) {
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				c.setClean();
			}
		}
	}
	
	public boolean isPopulateOriginalModuleMap() {
		return populateOriginalModuleMap;
	}

	public void setPopulateOriginalModuleMap(boolean populateOriginalModuleMap) {
		this.populateOriginalModuleMap = populateOriginalModuleMap;
	}
	
	public Map<Component, String> getOriginalModuleMap() {
		return originalModuleMap;
	}
	
	public void populateOriginalModuleMap() {
		LOGGER.info("Populating Original Module Map");
		originalModuleMap = new HashMap<>();
		for (Concept c : getAllConcepts()) {
			for (Component comp : SnomedUtils.getAllComponents(c)) {
				if (StringUtils.isEmpty(comp.getEffectiveTime())) {
					throw new IllegalStateException("Can't populate original module on unreleased components like " + comp);
				}
				if (originalModuleMap.containsKey(comp)) {
					throw new IllegalStateException("Original module map already contains: " + comp);
				}
				originalModuleMap.put(comp, comp.getModuleId());
			}
		}
	}

	public void setAllowIllegalSCTIDs(boolean setting) {
		allowIllegalSCTIDs = setting;
	}

	public void finalizeGraph() throws TermServerScriptException {
		//Only current work required here is to verify that the Delta import has resolved
		//any apparent conflicts in the MRCM.  Convert the staging collection (which will hold duplicates)
		//into the non-staging collection only if all duplications have been resolved.
		LOGGER.info("Finalising MRCM from Staging Area");
		List<String> conflictingAttributes = new ArrayList<>();
		boolean preCoordAcceptable= finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapPreCoord, mrcmAttributeRangeMapPreCoord, conflictingAttributes);
		boolean postCoordAcceptable = finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapPostCoord, mrcmAttributeRangeMapPostCoord, conflictingAttributes);
		boolean allCoordAcceptable = finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapAll, mrcmAttributeRangeMapAll, conflictingAttributes);
		boolean newCoordAcceptable = finaliseMRCMAttributeRange(mrcmStagingAttributeRangeMapNewPreCoord, mrcmAttributeRangeMapNewPreCoord, conflictingAttributes);
		boolean allContentAcceptable = preCoordAcceptable && postCoordAcceptable && allCoordAcceptable && newCoordAcceptable;

		if (conflictingAttributes.size() > 0) {
			String msg = "MRCM Attribute Range File conflicts: \n";
			msg += conflictingAttributes.stream().collect(Collectors.joining(",\n"));
			if (allContentAcceptable) {
				integrityWarnings.add(msg);
			} else {
				throw new TermServerScriptException(msg);
			}
		}
	}

	private boolean finaliseMRCMAttributeRange(
			Map<Concept, Map<String, MRCMAttributeRange>> mrcmStagingAttributeRangeMap,
			Map<Concept, MRCMAttributeRange> mrcmAttributeRangeMap, List<String> conflictingAttributes) throws TermServerScriptException {
		boolean acceptablyFinalised = true;
		mrcmAttributeRangeMap.clear();
		for (Concept refComp : mrcmStagingAttributeRangeMap.keySet()) {
			Map<String, MRCMAttributeRange> conflictingRanges = mrcmStagingAttributeRangeMap.get(refComp);
			if (conflictingRanges.size() == 1) {
				//No Conflict in this case
				mrcmAttributeRangeMap.put(refComp, conflictingRanges.values().iterator().next());
			} else {
				//Assuming the conflict is between an International Row and an Extension Row for the same concept
				//but with distinct UUIDs, we'll let the extension row win.
				MRCMAttributeRange winningAR = pickWinningMRCMAttributeRange(refComp, conflictingRanges, conflictingAttributes);
				if (winningAR == null) {
					//We're OK as long as only 1 of the ranges is active.  Store that one.   Otherwise, add to conflicts list to report
					for (MRCMAttributeRange ar : conflictingRanges.values()) {
						MRCMAttributeRange existing = mrcmAttributeRangeMap.get(refComp);
						//We have a problem if the existing one is also active
						//We'll collect all conflicts up and report back on all of them in the calling function
						if (existing != null && existing.isActive() && ar.isActive()) {
							String contentType = translateContentType(ar.getContentTypeId());
							String detail = " (" + ar.getId() + " in module " + ar.getModuleId() + " vs " + existing.getId() + " in module " + existing.getModuleId() + ")";
							conflictingAttributes.add(contentType + ": " + refComp + detail);
							acceptablyFinalised = false;
						} else if (existing == null || ar.isActive()) {
							mrcmAttributeRangeMap.put(refComp, ar);
						}
					}
				} else {
					mrcmAttributeRangeMap.put(refComp, winningAR);
				}
			}
		}
		return acceptablyFinalised;
	}

	private MRCMAttributeRange pickWinningMRCMAttributeRange(Concept refComp, Map<String, MRCMAttributeRange> conflictingRanges, List<String> conflictingAttributes) throws TermServerScriptException {
		//Return 1 active row for each of INT and EXT, or null if there's no active row, or multiple active rows
		MRCMAttributeRange intAR = pickActiveMRCMAttributeRange(conflictingRanges, true);
		MRCMAttributeRange extAR = pickActiveMRCMAttributeRange(conflictingRanges, false);
		if (intAR != null && extAR != null) {
			String contentType = translateContentType(intAR.getContentTypeId());
			String detail = " (" + intAR.getId() + " in module " + intAR.getModuleId() + " vs " + extAR.getId() + " in module " + extAR.getModuleId() + ")";
			conflictingAttributes.add(contentType + ": " + refComp + detail);
			return extAR;
		}
		return null;
	}

	private MRCMAttributeRange pickActiveMRCMAttributeRange(Map<String, MRCMAttributeRange> conflictingRanges, boolean isInternational) {
		List<MRCMAttributeRange> activeRanges = conflictingRanges.values().stream()
				.filter(ar -> ar.isActive())
				.filter(ar -> isInternational == SnomedUtils.isInternational(ar))
				.collect(Collectors.toList());
		if (activeRanges.size() == 1) {
			return activeRanges.get(0);
		}
		return null;
	}

	public List<String> getIntegrityWarnings() {
		return integrityWarnings;
	}
}
