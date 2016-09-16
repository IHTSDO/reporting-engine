package org.ihtsdo.termserver.scripting;

public class IdGenerator {
	static int sequence = 0;
	public String getSCTID(String partition) {
		return ++sequence + partition;
	}
}
