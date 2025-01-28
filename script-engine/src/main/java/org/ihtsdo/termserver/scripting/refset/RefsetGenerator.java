package org.ihtsdo.termserver.scripting.refset;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RefsetGenerator extends TermServerScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(RefsetGenerator.class);

	private String outputName;  //Should be controlled by combination of other values eg refsetShape/Name
	protected String edition = "INT";
	protected String refsetShape;
	protected String refsetFileName;
	protected String effectiveDate; 
	protected String moduleId = "900000000000207008";
	protected String[] headers = new String[] {"id","effectiveTime","active","moduleId","refsetId","referencedComponentId"};
	private String[] additionalHeaders;
	protected int additionalColumnsCount = 0;
	protected List<RefsetMember> refsetMembers = new ArrayList<RefsetMember>();

	protected void report(Concept concept, Description d, Severity severity, ReportActionType actionType, String actionDetail) throws TermServerScriptException {
		String line = "";
		
		if (d==null) {
			line = concept.getConceptId() + COMMA + COMMA_QUOTE + 
					concept.getFsn() + QUOTE_COMMA_QUOTE; 
		} else {
			line = concept.getConceptId() + COMMA + 
				d.getDescriptionId() + COMMA_QUOTE + 
				d.getTerm() + QUOTE_COMMA_QUOTE ; 
		}
		line += severity + QUOTE_COMMA_QUOTE + 
				actionType.toString() + QUOTE_COMMA_QUOTE +
				actionDetail + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		getReportManager().initialiseReportFiles( new String[] {"Concept,DescSctId,Term,Severity,Action,Detail"});

		int increment = 0;
		String outputNameBase = "der2_" + refsetShape + "Refset_"+ refsetFileName + "Snapshot_" + edition +"_" + effectiveDate;
		outputName = outputNameBase;
		while (new File (outputName + ".txt").exists()) {
			outputName = outputNameBase + "_" + (++increment) ;
		}
		outputName = outputName + ".txt";
		LOGGER.info("Outputting data to {}", outputName);
		initialiseFileHeaders();
	}
	
	private void initialiseFileHeaders() throws TermServerScriptException {
		Object [] allHeaders = ArrayUtils.addAll(headers, additionalHeaders);
		writeToRF2File(outputName, allHeaders);
	}
	
	protected void process() throws TermServerScriptException{
		generateMembers();
		addSummaryInformation ("Refset members generated", refsetMembers.size());
		outputRefset();
	}
	
	abstract void generateMembers() throws TermServerScriptException;

	protected void outputRefset () throws TermServerScriptException {
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputName, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))
		{
			int columnCount = REF_IDX_REFCOMPID + additionalColumnsCount + 2;  //Count is one higher than max index, plus 1 for line ending.
			for (RefsetMember member : refsetMembers) {
				
				String [] columns = new String[columnCount];
				columns[REF_IDX_ID] = UUID.randomUUID().toString();
				columns[REF_IDX_EFFECTIVETIME] = effectiveDate;
				columns[REF_IDX_ACTIVE] = "1";
				columns[REF_IDX_MODULEID] = moduleId;
				columns[REF_IDX_REFSETID] = member.getRefsetId();
				columns[REF_IDX_REFCOMPID] = member.getReferencedComponentId();
				for (int i = 0; i < additionalColumnsCount ; i++) {
					throw new NotImplementedException("TODO Resolve ordering of additional values columns");
					//columns [REF_IDX_FIRST_ADDITIONAL + i] = member.getAdditionalValues()[i];
				}
				columns[columnCount -1] = LINE_DELIMITER;
				String line = StringUtils.join(columns, TSV_FIELD_DELIMITER);
				out.print(line);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to output refset due to " + e.getClass().getName() + ": " + e.getMessage(), e);
		}
	}

	public String[] getAdditionalHeaders() {
		return additionalHeaders;
	}

	public void setAdditionalHeaders(String[] additionalHeaders) {
		this.additionalHeaders = additionalHeaders;
		additionalColumnsCount = additionalHeaders.length;
	}

}
