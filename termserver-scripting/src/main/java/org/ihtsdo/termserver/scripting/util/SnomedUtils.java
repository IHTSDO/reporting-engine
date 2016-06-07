package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class SnomedUtils implements RF2Constants{

	public static String[] deconstructFSN(String fsn) {
		String[] elements = new String[2];
		int cutPoint = fsn.lastIndexOf(SEMANTIC_TAG_START);
		elements[0] = fsn.substring(0, cutPoint).trim();
		elements[1] = fsn.substring(cutPoint);
		return elements;
	}

}
