package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;

public abstract class TermServerReport extends TermServerScript {
	
	SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
	String currentTimeStamp = df.format(new Date());
	
	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		try {
			super.init(args);
			
			String reportFilename = getScriptName() + "_" + project.getKey() + "_" + currentTimeStamp + "_" + env  + ".csv";
			reportFile = new File(outputDir, reportFilename);
			reportFile.createNewFile();
			println ("Outputting Report to " + reportFile.getAbsolutePath());
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to initialise output report",e);
		}
	}
	

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
	}
}
