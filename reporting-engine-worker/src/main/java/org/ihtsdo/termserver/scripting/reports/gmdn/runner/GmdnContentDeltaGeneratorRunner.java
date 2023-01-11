package org.ihtsdo.termserver.scripting.reports.gmdn.runner;

/*
 * To generate GMDN delta files using previous and current release terms xml files.
 * Delta files generated contain following:
 * 1. New active terms
 * 2. Obsolete terms 
 * 3. Modified terms
 * 
 *
 */

import org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnContentDeltaGenerator;

/**
<termID>117298</termID> is removed from 16_7 release

<term>
<termCode>10003</termCode>
<termIsIVD>Non-IVD</termIsIVD>
<termName>Abdominal binder, single-use</termName>
<termDefinition></termDefinition>
<termStatus>Active</termStatus>
<modifiedDate>2011-11-07</modifiedDate>
<createdDate>2004-05-17</createdDate>
</term>
 */
public class GmdnContentDeltaGeneratorRunner {

private static final String XML = ".xml";

public static void main(String[] args) throws Exception {
		
		if (args == null || args.length < 2 ) {
			throw new RuntimeException("No GMDN terms xml files specified.");
		} 
		
		String previousFile = args[0];
		if ( !args[0].endsWith(XML) || !args[1].endsWith(XML)) {
			throw new RuntimeException("Specified file:" + previousFile + " is not a XML file!");
		}
		GmdnContentDeltaGenerator deltaGenerator = new GmdnContentDeltaGenerator();
		deltaGenerator.generateDeltaReport(args[0], args[1]);
	}
}
