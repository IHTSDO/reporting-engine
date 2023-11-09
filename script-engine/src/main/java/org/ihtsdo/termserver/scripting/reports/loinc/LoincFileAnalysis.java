package org.ihtsdo.termserver.scripting.reports.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class LoincFileAnalysis extends LoincScript {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoincFileAnalysis.class);

    private static final int FILE_IDX_LOINC_FULL = 0;
    private static final int FILE_IDX_ALT_ID = 1;
    private static final int FILE_IDX_20K = 2;

    Set<String> loincNumsInPreview = new HashSet<>();
    Map<String, Integer> propertiesInPreview = new HashMap<>();
    Map<String, Integer> propertiesIn20k = new HashMap<>();

    public static void main(String[] args) throws Exception {
        LoincFileAnalysis report = new LoincFileAnalysis();
        report.init(args);
        report.postInit();
        //report.reportFileAnalysis();
        report.loinc20kAnalysis();
        report.finish();
    }

    public void postInit() throws TermServerScriptException {
        ReportSheetManager.targetFolderId = "1yF2g_YsNBepOukAu2vO0PICqJMAyURwh";  //LOINC Folder
        super.postInit(new String[]{"Analysis"}, new String[]{"Property, Count, In Preview"}, false);
    }

    public void reportFileAnalysis() throws Exception {
        loadFullLoincFile(NOT_SET, getInputFile(FILE_IDX_LOINC_FULL));
        loadAltIdFile(getInputFile(FILE_IDX_ALT_ID));
        int unknownLoincNums = 0;
        try {
            LOGGER.info ("Analysing " + getInputFile(FILE_IDX_20K));
            boolean isFirstLine = true;
            try (BufferedReader br = new BufferedReader(new FileReader(getInputFile(FILE_IDX_20K)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!isFirstLine) {
                        String[] lineItems = line.split(TAB);
                        String loincNum = lineItems[0];
                        LoincTerm loincTerm = loincNumToLoincTermMap.get(loincNum);
                        if (loincTerm == null) {
                            unknownLoincNums++;
                            continue;
                        }

                        String property = loincTerm.getProperty();
                        propertiesIn20k.merge(property, 1, Integer::sum);
                        if (loincNumsInPreview.contains(loincNum)) {
                            propertiesInPreview.merge(property, 1, Integer::sum);
                        }
                    } else isFirstLine = false;
                }
            }

            for (String property : propertiesIn20k.keySet()) {
                int previewCount = propertiesInPreview.getOrDefault(property, 0);
                report(PRIMARY_REPORT, property, propertiesIn20k.get(property), previewCount);
            }
            report(PRIMARY_REPORT, "Unknown LoincNums", unknownLoincNums);
        } catch (Exception e) {
            throw new TermServerScriptException(e);
        }
    }

    public void loinc20kAnalysis() throws Exception {
        loadFullLoincFile(NOT_SET, getInputFile(FILE_IDX_LOINC_FULL));
        loadAltIdFile(getInputFile(FILE_IDX_ALT_ID));
        try {
            for (String loincNum : loincNumToLoincTermMap.keySet()) {
                LoincTerm loincTerm = loincNumToLoincTermMap.get(loincNum);
                if (loincTerm.getCommonTestRank().equals("0")) {
                    //Skip any LOINC terms that aren't in their top 20K
                    continue;
                }

                String property = loincTerm.getProperty();
                propertiesIn20k.merge(property, 1, Integer::sum);
                if (loincNumsInPreview.contains(loincNum)) {
                    propertiesInPreview.merge(property, 1, Integer::sum);
                }
            }

            for (String property : propertiesIn20k.keySet()) {
                int previewCount = propertiesInPreview.getOrDefault(property, 0);
                report(PRIMARY_REPORT, property, propertiesIn20k.get(property), previewCount);
            }
        } catch (Exception e) {
            throw new TermServerScriptException(e);
        }
    }

    private void loadAltIdFile(File altFile) throws IOException {
            LOGGER.info ("Loading " + altFile);
            boolean isFirstLine = true;
            try (BufferedReader br = new BufferedReader(new FileReader(altFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!isFirstLine) {
                        String[] lineItems = line.split(TAB);
                        String loincNum = lineItems[0];
                        loincNumsInPreview.add(loincNum);
                    } else isFirstLine = false;
                }
            }
    }

    @Override
    public String getReportName() {
        return "LOINC File Analysis";
    }
}
