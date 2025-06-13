package org.ihtsdo.termserver.scripting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.otf.exception.TermServerRuntimeException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.ConcreteValue;
import org.ihtsdo.termserver.scripting.domain.mrcm.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.module.storage.ModuleDependencyReferenceSet;
import org.snomed.otf.owltoolkit.conversion.AxiomRelationshipConversionService;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.Script;

public class GraphLoader implements ScriptConstants, ComponentStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(GraphLoader.class);

	private static GraphLoader singleton = null;
	private Map<String, Concept> concepts = new HashMap<>();
	private ModuleDependencyReferenceSet mdrs = null;
	private Map<String, Description> descriptions = new HashMap<>();
	private Map<String, Component> allComponents = null;
	private Map<Component, Concept> componentOwnerMap = null;
	private Map<String, Concept> fsnMap = null;
	private Map<String, Concept> usptMap = null;
	private Map<String, Concept> gbptMap = null;
	private Map<Concept, Map<String, String>> alternateIdentifierMap = new HashMap<>();
	private Set<String> excludedModules;
	public static final int MAX_DEPTH = 1000;
	private Set<String> orphanetConceptIds;
	private AxiomRelationshipConversionService axiomService;
	
	private DescendantsCache descendantsCache = DescendantsCache.getDescendantsCache();
	private DescendantsCache statedDescendantsCache = DescendantsCache.getStatedDescendantsCache();
	private AncestorsCache ancestorsCache = AncestorsCache.getAncestorsCache();
	private AncestorsCache statedAncestorsCache = AncestorsCache.getStatedAncestorsCache();
	
	//Watch that this map is of the TARGET of the association, ie all concepts used in a historical association
	private Map<Concept, List<AssociationEntry>> historicalAssociations =  new HashMap<>();
	private Map<Concept, Set<DuplicatePair>> duplicateLangRefsetEntriesMap;
	private Set<LangRefsetEntry> duplicateLangRefsetIdsReported = new HashSet<>();

	MRCMAttributeRangeManager mrcmAttributeRangeManager = new MRCMAttributeRangeManager(this);
	MRCMAttributeDomainManager mrcmAttributeDomainManager = new MRCMAttributeDomainManager(this);
	MRCMDomainManager mrcmDomainManager = new MRCMDomainManager(this);
	MRCMModuleScopeManager mrcmModuleScopeManager = new MRCMModuleScopeManager(this);

	private boolean detectNoChangeDelta = false;
	private boolean runIntegrityChecks = true;
	private boolean checkForExcludedModules = false;
	private boolean recordPreviousState = false;
	private boolean allowIllegalSCTIDs = false;
	private boolean firstPreviousStateRelationshipWarning = false;
	
	protected boolean populateOriginalModuleMap = false;
	protected Map<Component, String> originalModuleMap = null;
	
	public StringBuilder log = new StringBuilder();
	
	private TransitiveClosure transitiveClosure;
	private TransitiveClosure previousTransitiveClosure;

	private List<String> integrityWarnings = new ArrayList<>();
	
	public static GraphLoader getGraphLoader() {
		if (singleton == null) {
			singleton = new GraphLoader();
			singleton.axiomService = new AxiomRelationshipConversionService(NEVER_GROUPED_ATTRIBUTES);
			singleton.excludedModules = new HashSet<>();
			singleton.excludedModules.add(SCTID_LOINC_PROJECT_MODULE);
			populateKnownConcepts();
		}
		return singleton;
	}
	
	private GraphLoader() {
		//Prevents instantiation by other than getGraphLoader()
	}

	public ModuleDependencyReferenceSet getMdrs() throws TermServerScriptException {
		if (mdrs == null) {
			throw new TermServerScriptException("MDRS requested, but not loaded");
		}
		return mdrs;
	}
	
	private static void populateKnownConcepts() {
		//Pre-populate known concepts to ensure we only ever refer to one object
		//Reset concept each time, to avoid contamination from previous runs
		List<Concept> conceptsToReset = List.of(
				ROOT_CONCEPT, IS_A, PHARM_BIO_PRODUCT, MEDICINAL_PRODUCT, PHARM_DOSE_FORM,
				SUBSTANCE, CLINICAL_FINDING, BODY_STRUCTURE, PROCEDURE, SITN_WITH_EXP_CONTXT,
				SPECIMEN, OBSERVABLE_ENTITY, EVENT, DISEASE, DEVICE, ORGANISM, METHOD,
				USING_DEVICE, PROCEDURE_SITE, FINDING_SITE, MORPHOLOGIC_ABNORMALITY,
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
		LOGGER.info("Resetting Graph Loader - configuration reset");
		setRecordPreviousState(false);

		LOGGER.info("Resetting Graph Loader - memory wipe");
		concepts = new HashMap<>();
		mdrs = null;
		descriptions = new HashMap<>();
		allComponents = null;
		componentOwnerMap = null;
		fsnMap = null;
		orphanetConceptIds = null;
		descendantsCache.reset();
		statedDescendantsCache.reset();
		ancestorsCache.reset();
		statedAncestorsCache.reset();
		historicalAssociations =  new HashMap<>();
		duplicateLangRefsetEntriesMap = new HashMap<>();
		duplicateLangRefsetIdsReported = new HashSet<>();
		integrityWarnings = new ArrayList<>();
		alternateIdentifierMap = new HashMap<>();
		
		//We'll reset the ECL cache during TS Init
		populateKnownConcepts();
		previousTransitiveClosure = null;
		transitiveClosure = null;
		
		fsnMap = null;
		usptMap = null;
		gbptMap = null;
		historicalAssociations =  new HashMap<>();
		duplicateLangRefsetEntriesMap= null;
		duplicateLangRefsetIdsReported = new HashSet<>();

		mrcmAttributeDomainManager.reset();
		mrcmAttributeRangeManager.reset();
		mrcmDomainManager.reset();
		mrcmModuleScopeManager.reset();
		
		System.gc();
		outputMemoryUsage();
	}
	
	private void outputMemoryUsage() {
		Runtime runtime = Runtime.getRuntime();
		NumberFormat format = NumberFormat.getInstance();
		String freeMemoryStr = format.format(runtime.freeMemory() / 1024);
		LOGGER.info("free memory now: {}", freeMemoryStr);
	}

	public void loadRelationships(CharacteristicType characteristicType, InputStream relStream, boolean addRelationshipsToConcepts, Boolean isReleased)
			throws IOException, TermServerScriptException {
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

				String msg = SnomedUtils.isValid(lineItems[IDX_ID], PartitionIdentifier.RELATIONSHIP);
				if (msg != null) {
					LOGGER.warn(msg);
				}
				
				//Might need to modify the characteristic type for Additional Relationships
				characteristicType = SnomedUtils.translateCharacteristicType(lineItems[REL_IDX_CHARACTERISTICTYPEID]);
				
				if (!isConcept(lineItems[REL_IDX_SOURCEID])) {
					LOGGER.debug("{} relationship {} referenced a non concept identifier: {}", characteristicType, lineItems[REL_IDX_ID], lineItems[REL_IDX_SOURCEID]);
				}
				//Dutch extension has phantom concept referenced in an inactive stated relationship
				if (lineItems[REL_IDX_DESTINATIONID].equals("39451000146106")) {
					log.append("Skipping reference to phantom concept - 39451000146106");
					continue;
				}
				Concept thisConcept = getConcept(lineItems[REL_IDX_SOURCEID]);

				//If we've already received a newer version of this component, say
				//by loading published INT first and a previously published MS 2nd, then skip
				Relationship existing = thisConcept.getRelationship(lineItems[IDX_ID]);
				
				String previousState = null;
				if (isRecordPreviousState() && existing != null && !firstPreviousStateRelationshipWarning) {
					LOGGER.warn("Not recording previous state of relationships for memory reasons");
					firstPreviousStateRelationshipWarning = true;
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
							//Skipping incoming published relationship row with same date as curently held, but inactive.
							continue;
						}
					} else {
						continue;
					}
				}

				//This file may not be a released one, but if the existing relationship is marked as released, then the relationship remains released
				Boolean thisRelIsReleased = isReleased;
				if (existing != null && existing.isReleased() != null && existing.isReleasedSafely()) {
					thisRelIsReleased = true;
				}
				
				if (addRelationshipsToConcepts) {
					addRelationshipToConcept(characteristicType, lineItems, thisRelIsReleased, previousState);
				}
				relationshipsLoaded++;
			} else {
				isHeaderLine = false;
			}
		}
		log.append("\tLoaded {}" + relationshipsLoaded + " relationships of type " + characteristicType + " which were " + (addRelationshipsToConcepts?"":"not ") + "added to concepts\n");
	}
	
	public boolean isExcluded(String moduleId) {
		return excludedModules.contains(moduleId);
	}

	public void loadAxioms(InputStream axiomStream, Boolean isReleased)
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
					LOGGER.debug("Axiom {} referenced a non concept identifier: {}", lineItems[IDX_ID], lineItems[REF_IDX_REFCOMPID]);
				}
				
				Long conceptId = Long.parseLong(lineItems[REF_IDX_REFCOMPID]);
				Concept c = getConcept(conceptId);

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
							axiomEntry.setPreviousState(replacedAxiomEntry.getMutableFields());
						}
						//It might be that depending on the point in the release cycle,
						//we might try to load an extension on top of a more recent dependency
						//if the core has recently been released.  Don't allow an overwrite in this case.
						if (!StringUtils.isEmpty(axiomEntry.getEffectiveTime())
								&& replacedAxiomEntry.getEffectiveTime().compareTo(axiomEntry.getEffectiveTime()) > 0) {
							ignoredAxioms++;
							if (ignoredAxioms < 5) {
								LOGGER.warn("Ignoring {} since {} {} already held", axiomEntry.getEffectiveTime(), replacedAxiomEntry.getEffectiveTime(), replacedAxiomEntry.getId());
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
							modifyExistingStatedRelationshipsOnConcept(c, replacedAxiom, axiomEntry, replacedAxiomEntry, lineItems);
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
						Long lhs = axiom.getLeftHandSideNamedConcept();
						if (lhs == null) {
							//Is this a GCI?
							Long rhs = axiom.getRightHandSideNamedConcept();
							if (!conceptId.equals(rhs)) {
								throw new IllegalArgumentException("GCI Axiom rhs != RefCompId: " + line);
							}
							//This will replace any existing axiom with the same UUID
							c.addGciAxiom(AxiomUtils.toAxiom(c, axiomEntry, axiom));
							axiomEntry.setGCI(true);
						} else if (!conceptId.equals(lhs)) {
							//Have we got these weird NL axioms that exist on a different concept?
							log.append("Encountered " + (axiomEntry.isActiveSafely()?"active":"inactive") + " axiom on different concept to lhs argument");
							continue;
						}
						
						Set<Relationship> relationships = AxiomUtils.getRHSRelationships(c, axiom);
						if (relationships.isEmpty()) {
							log.append("Check here - zero RHS relationships");
						}

						//If we already have relationships loaded from this axiom then it may be that 
						//a subsequent version does not feature them, and we'll have to remove them.
						removeRelsNoLongerFeaturedInAxiom(c, axiomEntry.getId(), relationships);
						
						//Now we might need to adjust the active flag if the axiom is being inactivated
						//Or juggle the groupId, since individual axioms don't know about each other's existence
						//Ensure the relationships know what their axiom is, so they match with those on the concept
						relationships.forEach(r -> r.setAxiomEntry(axiomEntry));
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
							if (r.isActive()) {
								addRelationshipToConcept(r);
							} else {
								//Don't leave inactive stated relationships in the concept as they have no substance
								//and cause problems with Sets because they don't have IDs and can't be distinguished
								//from the same relationship inserted back in a 2nd time as active
								//But do we have any relationships for this axiom, or has it been inactive for a while?
								if (c.getRelationships(axiomEntry).size() > 0) {
									c.removeRelationship(r, true);
								}
							}
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
			LOGGER.error("Ignored {} already held with later effective time", ignoredAxioms);
		}

	}

	private void modifyExistingStatedRelationshipsOnConcept(Concept c, AxiomRepresentation replacedAxiom, AxiomEntry axiomEntry, AxiomEntry replacedAxiomEntry, String[] lineItems) throws TermServerScriptException {
		try {
			Set<Relationship> replacedRelationships = AxiomUtils.getRHSRelationships(c, replacedAxiom);

			//Set the axiom on the relationships, otherwise they won't match existing ones
			replacedRelationships.forEach(r -> r.setAxiomEntry(axiomEntry));
			alignAxiomRelationships(c, replacedRelationships, replacedAxiomEntry, false);
		} catch (IllegalArgumentException e) {
			throw new TermServerScriptException("Failure while processing axiom refset member " + lineItems[IDX_ID], e);
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
			LOGGER.debug("*** Invalid SCTID encountered in relationship " + lineItems[REL_IDX_ID] + ": s" + sourceId + " t" + typeId);
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
				LOGGER.warn("*** Invalid SCTID encountered in relationship {}: d{}", lineItems[REL_IDX_ID], destId );
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
	
	public void addRelationshipToConcept(CharacteristicType charType, String[] lineItems, Boolean isReleased) throws TermServerScriptException {
		addRelationshipToConcept(charType, lineItems, isReleased, null);
	}

	/**
	 * @param isReleased
	 * @throws TermServerScriptException
	 */
	public void addRelationshipToConcept(CharacteristicType charType, String[] lineItems, Boolean isReleased, String issue) throws TermServerScriptException {
		Relationship r = createRelationshipFromRF2(charType, lineItems);
		//Only set the released flag if it's not set already
		if (r.isReleased() == null) {
			r.setReleased(isReleased);
		}
		
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

		addRelationshipToConcept(r);
	}
	
	public void addRelationshipToConcept(Relationship r) {
		r.getSource().addRelationship(r);
		
		//Consider adding or removing parents if the relationship is ISA
		//But only remove items if we're processing a delta and there aren't any remaining
		//Don't modify our loaded hierarchy if we're loading a single concept from the TS
		if (r.getType().equals(IS_A) && r.getTarget() != null) {
			Concept source = r.getSource();
			Concept target = r.getTarget();

			if (r.isActiveSafely()) {
				source.addParent(r.getCharacteristicType(),r.getTarget());
				target.addChild(r.getCharacteristicType(),r.getSource());
			} else {
				//Ah this gets tricky.  We only remove the parent child relationship if
				//the source concept has no other relationships with the same triple
				//because the relationship might exist in another axiom
				if (source.getRelationships(r.getCharacteristicType(), r).isEmpty()) {
					source.removeParent(r.getCharacteristicType(),r.getTarget());
					target.removeChild(r.getCharacteristicType(),r.getSource());
				} else {
					//Not removing parent/child relationship as exists in other axiom / alternative relationship
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

	public Concept getConceptSafely (String identifier, boolean createIfRequired, boolean validateExists) {
		try {
			return getConcept(identifier, createIfRequired, validateExists);
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
		if (!allowIllegalSCTIDs && isRunIntegrityChecks()) {
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
				throw new TermServerScriptException("Requested concept '" + identifier + "' is not known to currently loaded snapshot.");
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

				//We might already have received some details about this concept
				Concept c = getConcept(lineItems[IDX_ID]);
				
				//If moduleId is null, then this concept has no prior state
				if (isRecordPreviousState()) {
					if (isReleased == null) {
						throw new IllegalStateException("Unable to record previous state from existing archive");
					}
					
					if (!isReleased  && c.getModuleId() != null) {
						c.setPreviousState(c.getMutableFields());
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
					//Skipping incoming published concept row, older than that held
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
	
	public void loadAlternateIdentifierFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				//Allow trailing empty fields
				String[] lineItems = line.split(FIELD_DELIMITER, -1);

				//Can we also populate an alternate identifier onto the concept?
				Concept c = getConcept(lineItems[REF_IDX_REFCOMPID]);
				AlternateIdentifier altId = new AlternateIdentifier();
				AlternateIdentifier.populatefromRf2(altId, lineItems);

				//Only set the released flag if it's not set already
				if (altId.isReleased() == null) {
					altId.setReleased(isReleased);
				}
				c.addAlternateIdentifier(altId);

				if (lineItems[IDX_ACTIVE].equals("1")) {
					Concept scheme = getConcept(altId.getIdentifierSchemeId());
					Map<String, String> schemeMap = alternateIdentifierMap.computeIfAbsent(scheme, k -> new HashMap<>());
					schemeMap.put(altId.getAlternateIdentifier(), c.getId());
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
				Concept c = getConcept(lineItems[DES_IDX_CONCEPTID]);
				
				if (!allowIllegalSCTIDs && isRunIntegrityChecks()) {
					SnomedUtils.isValid(lineItems[IDX_ID], PartitionIdentifier.DESCRIPTION, true);
				} else {
					String msg = SnomedUtils.isValid(lineItems[IDX_ID], PartitionIdentifier.DESCRIPTION);
					if (msg != null) {
						LOGGER.warn(msg);
					}
				}
				
				if (!fsnOnly || lineItems[DES_IDX_TYPEID].equals(SCTID_FSN)) {
					//We might already have information about this description, eg langrefset entries
					Description d = getDescription(lineItems[DES_IDX_ID]);
					
					//If the term is null, then this is the first we've seen of this description, so no
					//need to record its previous state.
					if (isRecordPreviousState() && !isReleased && d.getTerm() != null) {
						d.setPreviousState(d.getMutableFields());
					}
					
					//If we've already received a newer version of this component, say
					//by loading INT first and a published MS 2nd, then skip

					//If we're loading a cached snapshot, then we don't know if the record is released or not
					//so in that case, we don't expected to see any previous verison, so don't skip in that case
					if (!StringUtils.isEmpty(d.getEffectiveTime()) 
							&& (isReleased != null && isReleased)
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

				//Are we adding or replacing this entry?
				if (d.getLangRefsetEntries().contains(langRefsetEntry)) {
					LangRefsetEntry original = d.getLangRefsetEntry(langRefsetEntry.getId());

					if (isRecordPreviousState() && original != null && !isReleased) {
						langRefsetEntry.setPreviousState(original.getMutableFields());
					}

					//If we've already received a newer version of this component, say
					//by loading INT first and a published MS 2nd, then skip
					if (original != null && !StringUtils.isEmpty(original.getEffectiveTime())
							&& (isReleased != null && isReleased)
							&& (original.getEffectiveTime().compareTo(lineItems[IDX_EFFECTIVETIME]) >= 1)) {
						//Skipping incoming published langrefset row, older than that held
						continue;
					}

					//Set Released Flag if our existing entry has it
					if (original.isReleasedSafely()) {
						langRefsetEntry.setReleased(true);
					}
					//If we're working with not-released data and we already have a not-released entry
					//then there's two copies of this langrefset entry in a delta
					//We don't have to worry about this when loading a pre-created snapshot as the duplicates
					//will already have been removed.
					if (isReleased != null && !isReleased && StringUtils.isEmpty(original.getEffectiveTime())) {
						//Have we already reported this duplicate?
						if (duplicateLangRefsetIdsReported.contains(original)) {
							LOGGER.warn("Seeing additional duplication for {}", original.getId());
						} else {
							LOGGER.warn("Seeing duplicate langrefset entry in a delta: \n" + original.toString(true) + "\n" + langRefsetEntry.toString(true));
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
				//different UUIDs.  Therefore, if we get a later entry inactivating a given
				//dialect, then allow that to overwrite an earlier value with a different UUID

				//Do we have an existing entry for this description & dialect that is later and inactive?
				boolean clearToAdd = true;
				List<LangRefsetEntry> allExisting = d.getLangRefsetEntries(ActiveState.BOTH, langRefsetEntry.getRefsetId());
				for (LangRefsetEntry existing : allExisting) {
					//If we have two active for the same description, and neither has an effectiveTime delete the one that hasn't been published
					//Only if we're loading a delta, otherwise it's published
					if (isReleased != null && !isReleased) {
						checkForActiveDuplication(d, existing, langRefsetEntry);
					}

					//If the existing langrefset is later than this new row, don't add the new row
					if (existing.getEffectiveTime().compareTo(langRefsetEntry.getEffectiveTime()) < 0) {
						clearToAdd = false;
					} else if (existing.getEffectiveTime().equals(langRefsetEntry.getEffectiveTime())) {
						//As long as they have different UUIDs, it's OK to have the same effective time
						//But we'll ignore the inactivation, since there's still an active row
						if (!langRefsetEntry.isActiveSafely()) {
							clearToAdd = false;
						}
					} else {
						//New entry is later or same effective time as one we already know about
						if (!SnomedUtils.isEmpty(existing.getEffectiveTime()) && !existing.getId().equals(langRefsetEntry.getId())) {
							attemptPublishedRemovals++;
							if (attemptPublishedRemovals < 5) {
								String existingStr = existing.toStringWithModule();
								String newStr = langRefsetEntry.toStringWithModule();
								LOGGER.error("Attempt to remove published entry: {} by {}", existingStr, newStr);
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
						d.removeAcceptability(lineItems[LANG_IDX_REFSETID], false);
					}
				}
			} else {
				isHeaderLine = false;
			}
		}
		if (attemptPublishedRemovals > 0)  {
			LOGGER.error("Attempted to remove {} published entries in total", attemptPublishedRemovals);
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
		return duplicateLangRefsetEntriesMap.computeIfAbsent(c, k -> new HashSet<>());
	}

	/**
	 * Recurse hierarchy and set the shortest path depth for all concepts
	 */
	public void populateHierarchyDepth(Concept startingPoint, int currentDepth) throws TermServerScriptException {
		startingPoint.setDepth(currentDepth);

		for (Concept child : startingPoint.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (currentDepth >= MAX_DEPTH) {
				throw new TermServerScriptException("Maximum depth exceeded from " + startingPoint + " and inferred child " + child);
			}
			try {
				populateHierarchyDepth(child, currentDepth + 1);
			} catch (TermServerScriptException e) {
				LOGGER.debug("Exception path: {} -> {}",startingPoint, child);
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
					//Do we already have this indicator?  Copy the released flag if so
					InactivationIndicatorEntry existing = c.getInactivationIndicatorEntry(id);
					if (existing != null) {
						inactivation.setReleased(existing.getReleased());
						if (isRecordPreviousState() && !isReleased) {
							inactivation.setPreviousState(existing.getMutableFields());
						}
					}
					
					c.addInactivationIndicator(inactivation);
				} else if (inactivation.getRefsetId().equals(SCTID_DESC_INACT_IND_REFSET)) {
					Description d = getDescription(lineItems[INACT_IDX_REFCOMPID]);
					//Do we already have this indicator?  Copy the released flag if so
					InactivationIndicatorEntry existing = d.getInactivationIndicatorEntry(id);
					if (existing != null) {
						inactivation.setReleased(existing.getReleased());
						if (isRecordPreviousState() && !isReleased) {
							inactivation.setPreviousState(existing.getMutableFields());
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

	public void loadComponentAnnotationFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER, -1);

				if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
					continue;
				}
				String id = lineItems[IDX_ID];

				String revertEffectiveTime = null;
				if (detectNoChangeDelta && isReleased != null && !isReleased) {
					//Recover this entry for the component - concept or description
					ComponentAnnotationEntry cae = getComponentAnnotationEntry(lineItems[REF_IDX_REFCOMPID], lineItems[IDX_ID]);
					if (cae != null) {
						Component c = SnomedUtils.getParentComponent(cae, this);
						revertEffectiveTime = detectNoChangeDelta(c, cae, lineItems);
					}
				}

				ComponentAnnotationEntry componentAnnotationEntry = ComponentAnnotationEntry.fromRf2(lineItems);

				//Only set the released flag if it's not set already
				if (componentAnnotationEntry.isReleased() == null) {
					componentAnnotationEntry.setReleased(isReleased);
				}

				if (revertEffectiveTime != null) {
					componentAnnotationEntry.setEffectiveTime(revertEffectiveTime);
				}

				Concept c = getConcept(lineItems[COMP_ANNOT_IDX_REFCOMPID]);
				//Do we already have this cae?  Copy the released flag if so
				ComponentAnnotationEntry existing = c.getComponentAnnotationEntry(id);
				if (existing != null) {
					componentAnnotationEntry.setReleased(existing.getReleased());
					if (isRecordPreviousState() && !isReleased) {
						componentAnnotationEntry.setPreviousState(existing.getPreviousState());
					}
				}

				c.addComponentAnnotationEntry(componentAnnotationEntry);
			} else {
				isHeaderLine = false;
			}
		}
	}

	private ComponentAnnotationEntry getComponentAnnotationEntry(String componentId, String annotationId) throws TermServerScriptException {
		if (SnomedUtils.isConceptSctid(componentId)) {
			Concept c = getConcept(componentId, false, false);
			if (c != null) {
				return c.getComponentAnnotationEntry(annotationId);
			}
		} else {
			throw new TermServerScriptException("Component Annotation Entry for other component types not yet supported");
		}
		return null;
	}

	public void loadHistoricalAssociationFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER, -1);

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
							association.setPreviousState(existing.getMutableFields());
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
							association.setPreviousState(existing.getMutableFields());
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
			associations = new ArrayList<>();
			historicalAssociations.put(target, associations);
		}
		associations.add(h);
	}
	
	public List<AssociationEntry> usedAsHistoricalAssociationTarget (Concept c) {
		if (historicalAssociations.containsKey(c)) {
			return historicalAssociations.get(c);
		}
		return new ArrayList<>();
	}
	
	public boolean isUsedAsHistoricalAssociationTarget (Concept c) {
		return historicalAssociations.containsKey(c);
	}

	public void loadModuleDependencyFile(InputStream is, Boolean isReleased) throws IOException {
		if (mdrs == null) {
			mdrs = new ModuleDependencyReferenceSet();
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		boolean isHeaderLine = true;
		String line;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				loadModuleDependencyRow(line.split(FIELD_DELIMITER, -1), isReleased);
			} else {
				isHeaderLine = false;
			}
		}
	}

	private void loadModuleDependencyRow(String[] lineItems, Boolean isReleased) {
		if (checkForExcludedModules && isExcluded(lineItems[IDX_MODULEID])) {
			return;
		}
		String id = lineItems[IDX_ID];

		MdrsEntry mdrsEntry = MdrsEntry.fromRf2(lineItems);

		//Only set the released flag if it's not set already
		if (mdrsEntry.isReleased() == null) {
			mdrsEntry.setReleased(isReleased);
		}

		//Do we already have this mdrs entry?  Copy the released flag if so
		MdrsEntry existing = mdrs.getMdrsRow(id);
		if (existing != null) {
			mdrsEntry.setReleased(existing.getReleased());
			if (isRecordPreviousState() && !isReleased) {
				mdrsEntry.setPreviousState(existing.getMutableFields());
			}
		}

		if (mdrsEntry.isActiveSafely()) {
			mdrs.addMdrsRow(mdrsEntry);
		}
	}

	public void loadMRCMAttributeRangeFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		mrcmAttributeRangeManager.loadFile(is, isReleased);
	}

	public void loadMRCMAttributeDomainFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		mrcmAttributeDomainManager.loadFile(is, isReleased);
	}

	public void loadMRCMDomainFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		mrcmDomainManager.loadFile(is, isReleased);
	}

	public void loadMRCMModuleScopeFile(InputStream is, Boolean isReleased) throws IOException, TermServerScriptException {
		mrcmModuleScopeManager.loadFile(is, isReleased);
	}

	public Component getComponent(String id) {
		if (allComponents == null) {
			populateAllComponents();
		}
		return allComponents.get(id);
	}

	@Override
	public boolean isComponentId(String id) {
		return getComponent(id) != null;
	}

	public Collection<Component> getAllComponents() {
		if (allComponents == null) {
			populateAllComponents();
		}
		return allComponents.values();
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
		Script.print("Populating maps of all components");
		int tenPercent = getAllConcepts().size()/10;

		int conceptsProcessed = 0;
		for (Concept c : getAllConcepts()) {
			if (++conceptsProcessed % tenPercent == 0) {
				Script.print(".");
			}
			populateComponentMapForConcept(c);
		}
		Script.print("\n");
		LOGGER.info("Component owner map complete with {} entries.", componentOwnerMap.size());
	}

	public void populateComponentMapForConcept(Concept c) {
		if (allComponents == null) {
			allComponents = new HashMap<>();
			componentOwnerMap = new HashMap<>();
		}
		allComponents.put(c.getId(), c);
		componentOwnerMap.put(c,  c);
		for (Description d : c.getDescriptions()) {
			populateDescriptionComponents(c, d);
		}

		for (Relationship r : c.getRelationships()) {
			populateComponentMapForRelationship(c, r);
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

		for (ComponentAnnotationEntry ae : c.getComponentAnnotationEntries()) {
			allComponents.put(ae.getId(), ae);
			componentOwnerMap.put(ae, c);
		}

		for (AlternateIdentifier altId : c.getAlternateIdentifiers()) {
			allComponents.put(altId.getId(), altId);
			componentOwnerMap.put(altId, c);
		}
	}

	private void populateComponentMapForRelationship(Concept c, Relationship r) {
		//A relationship with a null ID will have come from an axiom.
		//We'll let the axiomEntry cover that.
		if (r.fromAxiom()) {
			return;
		}

		if (r.getRelationshipId() == null) {
			throw new IllegalArgumentException ("Rel ID not expected to be null");
		}
		//Have we historically swapped ID from a stated to an inferred relationship?
		if (allComponents.containsKey(r.getRelationshipId())) {
			if (r.isActiveSafely()) {
				Script.print("\nAll Components Map replacing '" + r.getRelationshipId() + "' " + allComponents.get(r.getRelationshipId()) + " with active " + r);
				allComponents.put(r.getRelationshipId(), r);
			} else if (allComponents.get(r.getRelationshipId()).isActiveSafely()) {
				Script.print("\nIgnoring inactive '" + r.getRelationshipId() + "' " + r + " due to already having " + allComponents.get(r.getRelationshipId()));
			} else {
				Script.print("\nTwo inactive components share the same id of " + r.getId() + ": " + r + " and " + allComponents.get(r.getId()));
			}
		} else {
			allComponents.put(r.getRelationshipId(), r);
		}
		componentOwnerMap.put(r,  c);
	}

	private void populateDescriptionComponents(Concept c, Description d) {
		allComponents.put(d.getDescriptionId(), d);
		componentOwnerMap.put(d, c);
		
		for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
			allComponents.put(i.getId(), i);
			componentOwnerMap.put(i, c);
		}

		for (AssociationEntry a : d.getAssociationEntries()) {
			allComponents.put(a.getId(), a);
			componentOwnerMap.put(a, c);
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
		populateComponentMapForConcept(concept);
	}
	
	public Concept registerConcept(String sctIdFSN) throws TermServerScriptException {
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
			case DESCRIPTION, TEXT_DEFINITION : return getConcept(((Description)c).getConceptId());
			case INFERRED_RELATIONSHIP, STATED_RELATIONSHIP : return getConcept(((Relationship)c).getSourceId());
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
			LOGGER.info("Loading list of Orphanet Concepts...");
			try {
				InputStream is = GraphLoader.class.getResourceAsStream("/data/orphanet_concepts.txt");
				if (is == null) {
					throw new TermServerRuntimeException("Failed to load Orphanet data file - not found.");
				}
				orphanetConceptIds = IOUtils.readLines(is, StandardCharsets.UTF_8).stream()
						.map(String::trim)
						.collect(Collectors.toSet());
			} catch (Exception e) {
				throw new RuntimeException ("Failed to load list of Orphanet Concepts",e);
			}
			LOGGER.info("Orphanet import complete.");
		}
		return Collections.unmodifiableCollection(orphanetConceptIds);
	}

	public boolean isOrphanetConcept (Concept c) {
		return getOrphanetConceptIds().contains(c.getId());
	}

	public void makeReady() {
		for (Concept c : concepts.values()) {
			c.clearIssues();
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
				g.setIssues(null);
				g.resetIndicators();
			}
		}
	}
	
	public AxiomRelationshipConversionService getAxiomService() {
		return axiomService;
	}

	public void populatePreviousTransitiveClosure() {
		LOGGER.info("Populating PREVIOUS transitive closure");
		previousTransitiveClosure = generateTransitiveClosure();
		LOGGER.info("PREVIOUS transitive closure complete");
	}
	
	public TransitiveClosure getTransitiveClosure() {
		if (transitiveClosure == null) {
			transitiveClosure = generateTransitiveClosure();
		}
		return transitiveClosure;
	}
	
	public TransitiveClosure generateTransitiveClosure() {
		LOGGER.info("Calculating transitive closure...");
		TransitiveClosure tc = new TransitiveClosure();
		//For all active concepts, populate their ancestors into the TC
		getAllConcepts().parallelStream().forEach(c->{
			try {
				tc.addConcept(c);
			} catch (TermServerScriptException e) {
				LOGGER.error("Exception encountered",e);
			} 
		});
		LOGGER.info("Completed transitive closure: {} relationships mapped", tc.size());
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
				LOGGER.warn("No change delta detected for {}, reverting effective time", c);
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
		String[] fieldNames = headerLine.split(TAB, -1);
		String[] additionalFieldNames = Arrays.copyOfRange(fieldNames, REF_IDX_FIRST_ADDITIONAL, fieldNames.length);

		LOGGER.info("Loading reference set file {}", fileName);
		int lineNum = 0;
		String line = "No line read yet";
		try {
			for (line = br.readLine(); line != null; line = br.readLine()) {
				lineNum++;
				loadReferenceSetLine(line, additionalFieldNames, isReleased);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to load reference set file " + fileName + " at line " + lineNum + ": " + line, e);
		}
	}

	private void loadReferenceSetLine(String line, String[] additionalFieldNames, Boolean isReleased) throws TermServerScriptException {
		String[] lineItems = line.split(TAB, -1);
		RefsetMember member = new RefsetMember();
		RefsetMember.populatefromRf2(member, lineItems, additionalFieldNames);
		if (isReleased != null) {
			member.setReleased(isReleased);
		}
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

	public void removeConcept(Concept c) {
		concepts.remove(c.getId());
		if (allComponents != null) {
			allComponents.remove(c.getId());
		}
    }

	public boolean doCheckForExcludedModules() {
		return checkForExcludedModules;
	}

	public MRCMAttributeRangeManager getMRCMAttributeRangeManager() {
		return mrcmAttributeRangeManager;
	}

	public MRCMAttributeDomainManager getMRCMAttributeDomainManager() {
		return mrcmAttributeDomainManager;
	}

	public MRCMDomainManager getMRCMDomainManager() {
		return mrcmDomainManager;
	}

	public MRCMModuleScopeManager getMRCMModuleScopeManager() {
		return mrcmModuleScopeManager;
	}

	public void finalizeMRCM() throws TermServerScriptException {
		mrcmAttributeRangeManager.finaliseFromStagingArea(integrityWarnings);
		mrcmAttributeDomainManager.finaliseFromStagingArea(integrityWarnings);
		mrcmDomainManager.finaliseFromStagingArea(integrityWarnings);
		mrcmModuleScopeManager.finaliseFromStagingArea(integrityWarnings);
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
		LOGGER.info("Setting record previous state to: {}", recordPreviousState);
		this.recordPreviousState = recordPreviousState;
	}

	public void setAllComponentsClean() {
		for (Component c : getAllComponents()) {
			c.setClean();
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

	public List<String> getIntegrityWarnings() {
		return integrityWarnings;
	}

	public Map<String, String> getSchemaMap(Concept scheme) {
		return alternateIdentifierMap.computeIfAbsent(scheme, k -> new HashMap<>());
	}
	
	public Map<Concept, Map<String, String>> getAlternateIdentifierMap() {
		return alternateIdentifierMap;
	}
}
