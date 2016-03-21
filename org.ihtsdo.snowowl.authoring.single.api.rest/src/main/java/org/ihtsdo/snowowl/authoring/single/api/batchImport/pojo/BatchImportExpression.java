package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ihtsdo.otf.rest.exception.ProcessingException;

public class BatchImportExpression {
	
	public static final String FULLY_DEFINED = "===";
	public static final String PRIMITVE = "<<<";
	public static final String PIPE = "|";
	public static final char PIPE_CHAR = '|';
	public static final char SPACE = ' ';
	public static final String REFINEMENT_START = ":";
	public static final String GROUP_START = "\\{";
	public static final char GROUP_START_CHAR = '{';
	public static final String GROUP_END = "}";
	public static final char GROUP_END_CHAR = '}';
	public static final String FOCUS_CONCEPT_SEPARATOR = "\\+";
	public static final String ATTRIBUTE_SEPARATOR = ",";
	public static final String TYPE_SEPARATOR = "=";
	public static char[] termTerminators = new char[] {'|', ':', '+', '{', ',', '}' };

	boolean isFullyDefined = false;
	List<String> focusConcepts;
	List<BatchImportGroup> attributeGroups;

	private BatchImportExpression(){
		
	}
	
	public static BatchImportExpression parse(String expressionStr) throws ProcessingException {
		BatchImportExpression result = new BatchImportExpression();
		StringBuffer expressionBuff = new StringBuffer(expressionStr);
		makeMachineReadable(expressionBuff);
		//After each extract we're left with the remainder of the expression
		result.isFullyDefined = extractDefinitionStatus(expressionBuff);
		result.focusConcepts = extractFocusConcepts(expressionBuff);
		result.attributeGroups = extractGroups(expressionBuff);
		return result;
	}

	static boolean extractDefinitionStatus(StringBuffer expressionBuff) throws ProcessingException {
		Boolean isFullyDefined = null;
		if (expressionBuff.indexOf(FULLY_DEFINED) == 0) {
			isFullyDefined = Boolean.TRUE;
		} else if (expressionBuff.indexOf(PRIMITVE) == 0) {
			isFullyDefined = Boolean.FALSE;
		}
		
		if (isFullyDefined == null) {
			throw new ProcessingException("Unable to determine Definition Status from: " + expressionBuff);
		}
		
		expressionBuff.delete(0, FULLY_DEFINED.length());
		return isFullyDefined;
	}
	
	static List<String> extractFocusConcepts(StringBuffer expressionBuff) {
		// Do we have a refinement, or just the parent(s) defined?
		int focusEnd = expressionBuff.indexOf(REFINEMENT_START);
		if (focusEnd == -1) {
			//Otherwise cut to end
			focusEnd = expressionBuff.length();
		} 
		String focusConceptStr = expressionBuff.substring(0, focusEnd);
		String[] focusConcepts = focusConceptStr.split(FOCUS_CONCEPT_SEPARATOR);
		if (focusEnd < expressionBuff.length()) {
			//Also delete the ":" symbol
			focusEnd++;
		}
		expressionBuff.delete(0, focusEnd);
		return Arrays.asList(focusConcepts);
	}

	static List<BatchImportGroup> extractGroups(StringBuffer expressionBuff) throws ProcessingException {
		List<BatchImportGroup> groups = new ArrayList<BatchImportGroup>();
		//Do we have any groups to parse?
		if (expressionBuff == null || expressionBuff.length() == 0) {
			return groups;
		} else if (expressionBuff.charAt(0) == GROUP_START_CHAR) {
			remove(expressionBuff,GROUP_END_CHAR);
			expressionBuff.deleteCharAt(0);
			String[] arrGroup = expressionBuff.toString().split(GROUP_START);
			int groupNumber = 0;
			for (String thisGroupStr : arrGroup) {
				BatchImportGroup newGroup = BatchImportGroup.parse(++groupNumber, thisGroupStr);
				groups.add(newGroup);
			}
		} else {
			throw new ProcessingException("Unable to parse attributes groups from: " + expressionBuff.toString());
		}
		return groups;
	}
	
	static void makeMachineReadable (StringBuffer hrExp) {
		int pipeIdx =  hrExp.indexOf(PIPE);
		while (pipeIdx != -1) {
			int endIdx = indexOf(hrExp, termTerminators, pipeIdx+1);
			//If we didn't find a terminator, cut to the end.
			if (endIdx == -1) {
				endIdx = hrExp.length();
			} else {
				//If the character found as a terminator is a pipe, then cut that too
				if (hrExp.charAt(endIdx) == PIPE_CHAR) {
					endIdx++;
				}
			}
			hrExp.delete(pipeIdx, endIdx);
			pipeIdx =  hrExp.indexOf(PIPE);
		}
		remove(hrExp, SPACE);
	}
	
	static void remove (StringBuffer haystack, char needle) {
		for (int idx = 0; idx < haystack.length(); idx++) {
			if (haystack.charAt(idx) == needle) {
				haystack.deleteCharAt(idx);
				idx --;
			}
		}
	}
	
	
	static int indexOf (StringBuffer haystack, char[] needles, int startFrom) {
		for (int idx = startFrom; idx < haystack.length(); idx++) {
			for (char thisNeedle : needles) {
				if (haystack.charAt(idx) == thisNeedle) {
					return idx;
				}
			}
		}
		return -1;
	}

	public boolean isFullyDefined() {
		return isFullyDefined;
	}

	public List<String> getFocusConcepts() {
		return focusConcepts;
	}

	public List<BatchImportGroup> getAttributeGroups() {
		return attributeGroups;
	}
}
