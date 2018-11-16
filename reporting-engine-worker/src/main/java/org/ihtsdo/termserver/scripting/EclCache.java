package org.ihtsdo.termserver.scripting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptCollection;

import com.google.gson.Gson;

import us.monoid.web.JSONResource;

public class EclCache {
	private static Map <String, EclCache> branchCaches;
	private static int PAGING_LIMIT = 500;
	private SnowOwlClient tsClient;
	private Gson gson;
	private GraphLoader gl;
	
	Map <String, List<Concept>> expansionCache = new HashMap<>();
	
	static EclCache getCache(String branch, SnowOwlClient tsClient, Gson gson, GraphLoader gl) {
		if (branchCaches == null) {
			branchCaches  = new HashMap<>();
		}
		
		EclCache branchCache = branchCaches.get(branch);
		if (branchCache == null) {
			branchCache = new EclCache(tsClient, gson);
			branchCaches.put(branch, branchCache);
		}
		branchCache.gl = gl;
		return branchCache;
	}
	
	EclCache (SnowOwlClient tsClient,Gson gson) {
		this.tsClient = tsClient;
		this.gson = gson;
	}
	
	protected List<Concept> findConcepts(String branch, String ecl) throws TermServerScriptException {
		//Have we already recovered this ECL?
		if (expansionCache.containsKey(ecl)) {
			List<Concept> cached = expansionCache.get(ecl);
			TermServerScript.debug ("Recovering cached " + cached.size() + " concepts matching " + ecl);
			return cached;
		}
		
		List<Concept> allConcepts = new ArrayList<>();
		boolean allRecovered = false;
		int offset = 0;
		while (!allRecovered) {
			try {
					JSONResource response = tsClient.getConcepts(ecl, branch, offset, PAGING_LIMIT);
					/*if (response.getHTTPStatus() != 200) {
						throw new TermServerScriptException ("HTTP " + response.getHTTPStatus());
					}*/
					String json = response.toObject().toString();
					ConceptCollection collection = gson.fromJson(json, ConceptCollection.class);
					if (offset == 0) {
						//First time round, report how many we're receiving.
						TermServerScript.debug ("Recovering " + collection.getTotal() + " concepts matching " + ecl);
						if (collection.getTotal() > 2000) {
							TermServerScript.info ("...which seems rather large, don't you think?");
						}
						
						if (collection.getTotal() > 9999) {
							TermServerScript.warn ("too large, returning null");
							//Cache this response too, so that we don't ask again
							expansionCache.put(ecl, new ArrayList<Concept>());
							return expansionCache.get(ecl);
						}
					}
					
					//Recover our locally held copy of these concepts so that we have the full hierarchy populated
					List<Concept> localCopies = collection.getItems().stream()
							.map(c -> gl.getConceptSafely(c.getId()))
							.collect(Collectors.toList());
					allConcepts.addAll(localCopies);
					//Did we get all the concepts that there are?
					if (allConcepts.size() < collection.getTotal()) {
						offset = allConcepts.size();
					} else {
						allRecovered = true;
					}
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to recover concepts using ECL '" + ecl + "' due to " + e.getMessage(),e);
			}
		}
		//Cache this result
		expansionCache.put(ecl, allConcepts);
		return allConcepts;
	}
}
