package org.ihtsdo.termserver.scripting.fixes.batch_import;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class BatchImportExpression implements ScriptConstants {

	public static final String FULLY_DEFINED = "===";
	public static final String PRIMITVE = "<<<";
	public static final char SPACE = ' ';
	protected static final char[] REFINEMENT_START = new char[] {':', '{'};
	public static final String GROUP_START = "\\{";
	public static final char GROUP_START_CHAR = '{';
	public static final char GROUP_END_CHAR = '}';
	public static final String FOCUS_CONCEPT_SEPARATOR = "\\+";
	public static final String ATTRIBUTE_SEPARATOR = ",";
	public static final String TYPE_SEPARATOR = "=";
	protected static char[] termTerminators = new char[] {'|', ':', '+', '{', ',', '}', '=' };

	private DefinitionStatus definitionStatus;
	private List<String> focusConcepts;
	private List<RelationshipGroup> attributeGroups;

	private BatchImportExpression(){
		
	}
	
	public static BatchImportExpression parse(String expressionStr, String moduleId) throws TermServerScriptException {
		BatchImportExpression result = new BatchImportExpression();
		StringBuilder expressionBuff = new StringBuilder(SnomedUtils.makeMachineReadable(expressionStr));
		//After each extract we're left with the remainder of the expression
		result.definitionStatus = extractDefinitionStatus(expressionBuff);
		result.focusConcepts = extractFocusConcepts(expressionBuff);
		result.attributeGroups = extractGroups(expressionBuff, moduleId);
		return result;
	}

	static DefinitionStatus extractDefinitionStatus(StringBuilder expressionBuff) throws TermServerScriptException {
		Boolean isFullyDefined = null;
		if (expressionBuff.indexOf(FULLY_DEFINED) == 0) {
			isFullyDefined = Boolean.TRUE;
		} else if (expressionBuff.indexOf(PRIMITVE) == 0) {
			isFullyDefined = Boolean.FALSE;
		}
		
		if (isFullyDefined == null) {
			throw new TermServerScriptException("Unable to determine Definition Status from: " + expressionBuff);
		}
		
		expressionBuff.delete(0, FULLY_DEFINED.length());
		return isFullyDefined.booleanValue() ? DefinitionStatus.FULLY_DEFINED : DefinitionStatus.PRIMITIVE;
	}
	
	static List<String> extractFocusConcepts(StringBuilder expressionBuff) {
		// Do we have a refinement, or just the parent(s) defined?
		//Ah, we're sometimes missing the colon.  Allow open curly brace as well.
		int focusEnd = indexOf(expressionBuff, REFINEMENT_START, 0);
		if (focusEnd == -1) {
			//Otherwise cut to end
			focusEnd = expressionBuff.length();
		} 
		String focusConceptStr = expressionBuff.substring(0, focusEnd);
		String[] focusConcepts = focusConceptStr.split(FOCUS_CONCEPT_SEPARATOR);
		if (focusEnd < expressionBuff.length() && expressionBuff.charAt(focusEnd) == ':') {
			//Also delete the ":" symbol
			focusEnd++;
		}
		expressionBuff.delete(0, focusEnd);
		return Arrays.asList(focusConcepts);
	}

	static List<RelationshipGroup> extractGroups(StringBuilder expressionBuff, String moduleId) throws TermServerScriptException {
		List<RelationshipGroup> groups = new ArrayList<>();
		//Do we have any groups to parse?
		if (expressionBuff == null || expressionBuff.isEmpty()) {
			return groups;
		} else if (expressionBuff.charAt(0) == GROUP_START_CHAR) {
			extractGroup(expressionBuff, groups, moduleId);
		} else if (Character.isDigit(expressionBuff.charAt(0))) {
			//Do we have a block of ungrouped attributes to start with?
			parseUngroupedAttributes(expressionBuff, groups, moduleId);
		} else {
			throw new TermServerScriptException("Unable to parse attributes groups from: " + expressionBuff);
		}
		return groups;
	}

	private static void extractGroup(StringBuilder expressionBuff, List<RelationshipGroup> groups, String moduleId) throws TermServerScriptException {
		remove(expressionBuff,GROUP_END_CHAR);
		expressionBuff.deleteCharAt(0);
		String[] arrGroup = expressionBuff.toString().split(GROUP_START);
		int groupNumber = 0;
		for (String thisGroupStr : arrGroup) {
			try {
				RelationshipGroup newGroup = parse(++groupNumber, thisGroupStr, moduleId);
				groups.add(newGroup);
			} catch (TermServerScriptException e) {
				throw new TermServerScriptException("Failed to parse group: " + thisGroupStr, e);
			}
		}
	}

	private static void parseUngroupedAttributes(StringBuilder expressionBuff, List<RelationshipGroup> groups, String moduleId) throws TermServerScriptException {
		//Do we have a block of ungrouped attributes to start with?  parse
		//up to the first open group character, ensuring that it occurs before the
		//next close group character.
		int nextGroupOpen = expressionBuff.indexOf(Character.toString(GROUP_START_CHAR));
		int nextGroupClose = expressionBuff.indexOf(Character.toString(GROUP_END_CHAR));
		//Case no further groups
		if (nextGroupOpen == -1 && nextGroupClose == -1) {
			RelationshipGroup newGroup = parse(0, expressionBuff.toString(), moduleId);
			groups.add(newGroup);
		} else if (nextGroupOpen > -1 && nextGroupClose > nextGroupOpen) {
			RelationshipGroup newGroup = parse(0, expressionBuff.substring(0, nextGroupOpen), moduleId);
			groups.add(newGroup);
			//And now work through the bracketed groups
			StringBuilder remainder = new StringBuilder(expressionBuff.substring(nextGroupOpen, expressionBuff.length()));
			groups.addAll(extractGroups(remainder, moduleId));
		} else {
			throw new TermServerScriptException("Unable to separate grouped from ungrouped attributes in: " + expressionBuff);
		}
	}

	static void remove (StringBuilder haystack, char needle) {
		for (int idx = 0; idx < haystack.length(); idx++) {
			if (haystack.charAt(idx) == needle) {
				haystack.deleteCharAt(idx);
				idx --;
			}
		}
	}

	static int indexOf (StringBuilder haystack, char[] needles, int startFrom) {
		for (int idx = startFrom; idx < haystack.length(); idx++) {
			for (char thisNeedle : needles) {
				if (haystack.charAt(idx) == thisNeedle) {
					return idx;
				}
			}
		}
		return -1;
	}

	public DefinitionStatus getDefinitionStatus() {
		return definitionStatus;
	}

	public List<String> getFocusConcepts() {
		return focusConcepts;
	}

	public List<RelationshipGroup> getAttributeGroups() {
		return attributeGroups;
	}
	
	public static RelationshipGroup parse(int groupNumber, String expression, String moduleId) throws TermServerScriptException {
		RelationshipGroup thisGroup = new RelationshipGroup(groupNumber);
		String[] attributes = expression.split(BatchImportExpression.ATTRIBUTE_SEPARATOR);
		for (String thisAttribute : attributes) {
			Relationship relationship = parseAttribute(groupNumber, thisAttribute, moduleId);
			thisGroup.addRelationship(relationship);
		}
		return thisGroup;
	}

	private static Relationship parseAttribute(int groupNum, String thisAttribute, String moduleId) throws TermServerScriptException {
		//Expected format  type=value so bomb out if we don't end up with two concepts
		String[] attributeParts = thisAttribute.split(BatchImportExpression.TYPE_SEPARATOR);
		if (attributeParts.length != 2) {
			throw new TermServerScriptException("Unable to detect type=value in attribute: '" + thisAttribute + "'");
		}
		//Check we have SCTIDs that pass the Verhoeff check
		SnomedUtils.isValid(attributeParts[0], PartitionIdentifier.CONCEPT);
		SnomedUtils.isValid(attributeParts[1], PartitionIdentifier.CONCEPT);
		Relationship r = new Relationship(null, new Concept(attributeParts[0]), new Concept(attributeParts[1]), groupNum);
		r.setModuleId(moduleId);
		return r;
	}
}
