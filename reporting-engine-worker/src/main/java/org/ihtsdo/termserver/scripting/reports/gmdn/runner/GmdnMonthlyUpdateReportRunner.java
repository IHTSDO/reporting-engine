package org.ihtsdo.termserver.scripting.reports.gmdn.runner;

import org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnMonthlyUpdateReportParser;

public class GmdnMonthlyUpdateReportRunner {

	public static void main(String[] args) {
		if (args == null || args.length < 1 ) {
			throw new RuntimeException("No GMDN monthly update xml file specified.");
		} 
		
		String monthlyUpdateFile= args[0];
		if ( !args[0].endsWith(".xml")) {
			throw new RuntimeException("Specified file:" + monthlyUpdateFile + " is not a XML file!");
		}

		GmdnMonthlyUpdateReportParser parser = new GmdnMonthlyUpdateReportParser();
		parser.parseGmdnUpdateReport(args[0]);
	}
}
