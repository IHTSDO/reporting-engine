package org.ihtsdo.termserver.scripting.fixes.rf2Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;


/*
 * Splits an RF2 Archive of changes into tasks by destination of 
 * "Has Disposition" attribute.
 */
public class DispositionsArchive extends Rf2Player implements RF2Constants{
	
	//Disposition could be compound eg X+Y
	Multimap<String, Concept> dispositionBuckets = HashMultimap.create(); 
	Map<Concept, String> conceptToDispositionMap = new HashMap<Concept, String>();
	Set<Concept> hasNoDisposition = new HashSet<Concept>();	
	Set<Concept> inactivations = new HashSet<Concept>();
	
	Concept hasDisposition;
	
	protected DispositionsArchive(DispositionsArchive clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new DispositionsArchive(null).playRf2Archive(args);
	}


	protected Batch formIntoBatch() throws TermServerScriptException  {
		Batch batch = new Batch(getScriptName());
		hasDisposition = gl.getConcept(726542003L); //|Has disposition (attribute)|
		groupByHasDisposition();
		int conceptsModified = 0;

		for (String bucketId : dispositionBuckets.keySet()) {
			Task task = batch.addNewTask(author_reviewer);
			for (Concept concept : dispositionBuckets.get(bucketId)) {
				task.add(concept);
				conceptsModified++;
			}
			String bucketName = bucketId.contains("+")?bucketId : gl.getConcept(bucketId).toString();
			println(bucketName + ": " + task.size());
		}
		
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, conceptsModified);
		addSummaryInformation("CONCEPTS_INACTIVATED", inactivations.size());
		addSummaryInformation("CONCEPTS_NO_DISPOSITION", hasNoDisposition.size());
		return batch;
	}


	private void groupByHasDisposition() {
		for (Concept c : changingConcepts.values()) {
			//Firstly, is this an inactivation?
			if (!c.isActive()) {
				inactivations.add(c);
			} else {
				List<String> dispositions = getDispositions(c);
				if (dispositions.size() == 0) {
					hasNoDisposition.add(c);
				} else {
					String dispositionKey = StringUtils.join(dispositions, "+");
					dispositionBuckets.put(dispositionKey, c);
				}
			}
		}
	}

	private List<String> getDispositions(Concept c) {
		List<Relationship> dispositions = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, hasDisposition, ActiveState.ACTIVE);
		List<String> dispositionIds = new ArrayList<String>();
		for (Relationship r : dispositions) {
			dispositionIds.add(r.getTarget().getConceptId());
		}
		return dispositionIds;
	}

	@Override
	protected Batch formIntoBatch(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerScriptException {
		throw new NotImplementedException();
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
