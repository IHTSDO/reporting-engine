package org.ihtsdo.termserver.scripting;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.collection.CompositeCollection;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptCollection;
import org.springframework.util.StringUtils;

import com.google.gson.Gson;

public class EclCache {
	private static Map <String, EclCache> branchCaches;
	private static int PAGING_LIMIT = 1000;
	private static int MAX_RESULTS = 9999;
	private TermServerClient tsClient;
	private GraphLoader gl;
	boolean safetyProtocolEngaged = true;
	boolean quiet = false;
	
	Map <String, Collection<Concept>> expansionCache = new HashMap<>();
	
	static EclCache getCache(String branch, TermServerClient tsClient, Gson gson, GraphLoader gl, boolean quiet) {
		if (branchCaches == null) {
			branchCaches  = new HashMap<>();
		}
		
		EclCache branchCache = branchCaches.get(branch);
		if (branchCache == null) {
			branchCache = new EclCache(tsClient, gson);
			branchCaches.put(branch, branchCache);
		}
		branchCache.gl = gl;
		branchCache.quiet = quiet;
		return branchCache;
	}
	
	EclCache (TermServerClient tsClient,Gson gson) {
		this.tsClient = tsClient;
	}
	
	public static void reset() {
		branchCaches = new HashMap<>();
	}
	
	protected Collection<Concept> findConcepts(String branch, String ecl) throws TermServerScriptException {
		return findConcepts(branch, ecl, !safetyProtocolEngaged, true);
	}
	
	protected boolean isCached(String ecl) {
		return expansionCache.containsKey(ecl);
	}
	
	protected Collection<Concept> findConcepts(String branch, String ecl, boolean expectLargeResults, boolean useLocalStoreIfSimple) throws TermServerScriptException {
		ecl = ecl.trim();
		String machineEcl = org.ihtsdo.termserver.scripting.util.StringUtils.makeMachineReadable(ecl);
		Collection<Concept> allConcepts;
		
		//Have we already recovered this ECL?
		if (expansionCache.containsKey(ecl)) {
			Collection<Concept> cached = expansionCache.get(ecl);
			//Have we reset the GL? Recover full local cached objects if so
			if (cached.size() > 0 && StringUtils.isEmpty(cached.iterator().next().getFsn())) {
				Set<Concept> localCopies = cached.stream()
						.map(c -> gl.getConceptSafely(c.getId()))
						.collect(Collectors.toSet());
				cached = localCopies;
				expansionCache.put(ecl, cached);
			}
			if (!quiet) {
				TermServerScript.debug ("Recovering cached " + cached.size() + " concepts matching '" + ecl +"'");
			}
			return cached;
		} else if (ecl.contains(" OR ") && !machineEcl.contains("(") && !machineEcl.contains(" AND ")) {
			//Can this ecl be broken down into cheaper, requestable chunks?  
			//TODO Create class that holds these collections and can 
			//iterate through them without copying the objects
			Collection<Concept> combinedSet = new HashSet<>();
			for (String eclFragment : ecl.split(" OR ")) {
				TermServerScript.debug("Combining request for: " + eclFragment);
				combinedSet.addAll(findConcepts(branch, eclFragment, expectLargeResults, useLocalStoreIfSimple));
			}
			allConcepts = combinedSet;
		} else {
			if (useLocalStoreIfSimple && ecl.equals("*")) {
				allConcepts = gl.getAllConcepts();
			} else if (useLocalStoreIfSimple && isSimple(ecl)){
				//We might want to modify these sets, so request mutable copies
				if (ecl.startsWith("<<")) {
					Concept subhierarchy = gl.getConcept(ecl.substring(2).trim());
					allConcepts = gl.getDescendantsCache().getDescendentsOrSelf(subhierarchy, true);
				} else if (ecl.startsWith("<")) {
					Concept subhierarchy = gl.getConcept(ecl.substring(1).trim());
					allConcepts = gl.getDescendantsCache().getDescendents(subhierarchy, true);
				} else {
					throw new IllegalStateException("ECL is not simple: " + ecl);
				}
				TermServerScript.debug("Recovered " + allConcepts.size() + " concepts for simple ecl from local memory: " + ecl);
			} else {
				allConcepts = recoverConceptsFromTS(branch, ecl, expectLargeResults);
			}
		}
		
		if (allConcepts.size() == 0) {
			TermServerScript.warn ("ECL " + ecl + " recovered 0 concepts.  Check?");
			expansionCache.remove(ecl);
		} else {
			//Seeing a transient issue where we're getting 0 concepts back on the first call, and the concepts back on a 
			//subsequent call.  So for now, don't cache a null response and we'll sleep / retry
			//Cache this result
			expansionCache.put(ecl, allConcepts);
		}
		return allConcepts;
	}
	
	private boolean isSimple(String ecl) {
		//Any braces, commas, more than two pipes, hats, colons mark this ecl as not being simple
		boolean isSimple = true;
		if (StringUtils.countOccurrencesOf(ecl, "|") > 2) {
			isSimple = false;
		} else {
			//Need to strip out all FSNs that might contain odd characters
			ecl = org.ihtsdo.termserver.scripting.util.StringUtils.makeMachineReadable(ecl);
			if (ecl.contains("{") || ecl.contains(",") || ecl.contains("^") || ecl.contains("(") ||
					ecl.contains(" AND ") || ecl.contains(":")) {
				isSimple = false;
			}
		}
		return isSimple;
	}

	public void engageSafetyProtocol(boolean engaged) {
		boolean changed = (safetyProtocolEngaged != engaged);
		safetyProtocolEngaged = engaged;
		if (!engaged && changed) {
			TermServerScript.warn ("ECL cache safety protocols have been disengaged. There's no limit");
		}
	}
	
	private Set<Concept> recoverConceptsFromTS(String branch, String ecl, boolean expectLargeResults) throws TermServerScriptException {
		Set<Concept> allConcepts = new HashSet<>();
		boolean allRecovered = false;
		//int offset = 0;
		String searchAfter = null;
		while (!allRecovered) {
			try {
					ConceptCollection collection = tsClient.getConcepts(ecl, branch, searchAfter, PAGING_LIMIT);
					if (searchAfter == null) {
						//First time round, report how many we're receiving.
						TermServerScript.debug ("Recovering " + collection.getTotal() + " concepts matching '" + ecl +"'");
						if (collection.getTotal() > 2000 && !expectLargeResults) {
							TermServerScript.info ("...which seems rather large, don't you think?");
						}
						
						if (!expectLargeResults && collection.getTotal() > MAX_RESULTS) {
							throw new TermServerScriptException("ECL returned " + collection.getTotal() + " concepts, exceeding limit of " + MAX_RESULTS);
						}
					}
					
					//Recover our locally held copy of these concepts so that we have the full hierarchy populated
					List<Concept> localCopies = collection.getItems().stream()
							.map(c -> gl.getConceptSafely(c.getId()))
							.collect(Collectors.toList());
					allConcepts.addAll(localCopies);
					
					//Debug - check for duplicates
					/*Concept duplicate = gl.getConcept("1162864000");
					if (localCopies.contains(duplicate)) {
						TermServerScript.warn ("Duplicate found");
					}*/
					
					//Did we get all the concepts that there are?
					if (allConcepts.size() < collection.getTotal()) {
						//offset = allConcepts.size();
						searchAfter = collection.getSearchAfter();
					} else {
						allRecovered = true;
					}
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to recover concepts using ECL '" + ecl + "' due to " + e.getMessage(),e);
			}
		}
		return allConcepts;
	}
}
