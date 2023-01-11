package org.ihtsdo.termserver.scripting.reports.gmdn.generator;

import static org.ihtsdo.termserver.scripting.reports.gmdn.generator.GMDNConstants.*;
import static org.ihtsdo.termserver.scripting.reports.gmdn.generator.GmdnFields.*;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.reports.gmdn.utils.GMDNUtils;

import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;

public class GmdnMonthlyUpdateReportParser {

	public static final String AMEND = "amend";
	public static final String NEW = "new";
	public static final String OBSOLETION = "obsoletion";

	public void parseGmdnUpdateReport(String monthlyUpdateFile) {
		if (monthlyUpdateFile == null) {
			throw new RuntimeException("No GMDN monthly update xml file specified.");
		}
		Builder builder = new Builder();
		try {
			Document updateFile = builder.build(monthlyUpdateFile);
			Elements terms = updateFile.getRootElement().getChildElements(TERM);
			System.out.println("Monthly updated total terms:" + terms.size() );
			List<Element> activeNodes = new ArrayList<>();
			List<Element> obsoleteNodes = new ArrayList<>();
			List<Element> termChanged = new ArrayList<>();
			int totalNonIvd = 0;
			for (int i=0; i < terms.size(); i++) {
				Element element = terms.get(i);
				if (NON_IVD.equals(element.getFirstChildElement(TERM_IS_IVD.getName()).getValue())) {
					totalNonIvd++;
					String changeType = element.getFirstChildElement("changeType").getValue();
					if (AMEND.equals(changeType)) {
						termChanged.add(element);
					} else if (NEW.equals(changeType)) {
						activeNodes.add(element);
					} else if (OBSOLETION.equals(changeType)) {
						obsoleteNodes.add(element);
					} else {
						System.out.println("Unknown change type:" + changeType);
					}
				}
			}
			System.out.println("Total non-IVD monthly update:" + totalNonIvd);
			System.out.println("Total active:" + activeNodes.size());
			System.out.println("Total obsolete:" + obsoleteNodes.size());
			System.out.println("Total amend:" + termChanged.size());
			
			List<GmdnFields> outputFields = Arrays.stream(GmdnFields.values())
					.filter(field -> !field.equals(GmdnFields.TERM_IS_IVD)).collect(Collectors.toList());

			String outputDir = Paths.get(monthlyUpdateFile).getParent().toString() + File.separator;
			String fileExtension = GMDNConstants.CSV;
			GMDNUtils.outputElements(activeNodes, outputDir + "ActiveTermsDeltaReport" + fileExtension, outputFields);

			System.out.println("Total current obsolete NON-IVD terms:" + obsoleteNodes.size());
			GMDNUtils.outputElements(obsoleteNodes, outputDir + "ObsoleteTermsDeltaReport" + fileExtension, outputFields);
			
			System.out.println("Total current NON-IVD terms modified since last release:" + termChanged.size());
			GMDNUtils.outputElements(termChanged, outputDir + "ModifiedTermsDeltaReport" + fileExtension, outputFields);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
