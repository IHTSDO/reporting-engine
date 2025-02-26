package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.*;
import java.util.*;

import org.ihtsdo.otf.RF2Constants;
/**
 * ISRS-1256 Requirement to update an existing archive with a Delta
 */
public class DetectSwitchedRefsets implements RF2Constants {
	
	Map<String, String> memberToRefsetMap = new HashMap<>();
	Set<String> reported = new HashSet<>();
	
	public static void main(String[] args) throws Exception {
		DetectSwitchedRefsets app = new DetectSwitchedRefsets();
		try {
			for (String arg : args) {
				System.out.println("Processing " + arg);
				app.processFile(arg);
			}
		} finally {
			System.out.println("Finished");
			System.out.println("Count: " + app.reported.size());
		}
	}

	private void processFile(String filename) throws Exception {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new FileReader(filename));
		String line;
		boolean isHeader = true;
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String id = lineItems[IDX_ID];
				String refsetId = lineItems[LANG_IDX_REFSETID];
				
				//Have we seen this id in another refset?
				if (memberToRefsetMap.containsKey(id)) {
					String existingRefsetId = memberToRefsetMap.get(id);
					if (!refsetId.equals(existingRefsetId) && !reported.contains(id)) {
						System.out.println(id);
						reported.add(id);
					}
				} else {
					memberToRefsetMap.put(id, refsetId);
				}
			} else {
				isHeader = false;
			}
		}
		br.close();
	}
	
}
