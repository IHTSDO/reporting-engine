package org.ihtsdo.termserver.scripting.reports.gmdn;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;

import nu.xom.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.DateUtils;
import org.ihtsdo.otf.utils.ZipFileUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.reports.gmdn.utils.GmdnFields;
import org.ihtsdo.termserver.scripting.reports.gmdn.utils.GmdnException;
import org.ihtsdo.termserver.scripting.reports.gmdn.utils.GmdnSFTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ihtsdo.termserver.scripting.reports.gmdn.utils.GmdnFields.*;

/**
 * GmdnMonthlyDeltaReport is a report class that generates deltas for the GMDN data for this month.
 * It extends the TermServerReport class and implements the ReportClass interface.
 */
public class GmdnMonthlyDeltaReport extends TermServerReport implements ReportClass {
    private static final Logger LOGGER = LoggerFactory.getLogger(GmdnMonthlyDeltaReport.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MMMM dd yyyy HH:mm:ss");
    private static final String REPORT_NAME = "GMDN Monthly Delta";
    private static final String REPORT_DESCRIPTION = "This report generates deltas for the GMDN (Global Medical Device Nomenclature) data for this month";
    private static final String REPORT_CATEGORY = JobCategory.ADHOC_QUERIES;
    private static final String REPORT_FOLDER_ID = "1Ox_3dZue1JSZiXDh-bBSEV2PBLQR1SXx";
    private static final String LOCAL_WORKING_DIRECTORY = "/tmp/";

    public static final String OBSOLETE = "Obsolete";
    public static final String ACTIVE = "Active";
    public static final String NON_IVD = "Non-IVD";
    public static final String TERM = "term";
    private static final int TAB_STATUS = 0;
    private static final int TAB_NEW_ACTIVE = 1;
    private static final int TAB_MODIFIED = 2;
    private static final int TAB_OBSOLETE = 3;
    public static final String CELL_ERROR = "ERROR";
    public static final String CELL_OK = "OK";

    private String currentMonthName;
    private String currentMonthsZipFileName;
    private String lastMonthsZipFileName;
    private String currentMonthsXmlFileName;
    private String lastMonthsXmlFileName;

    GmdnSFTPClient gmdnSFTPClient;

    private record GmdnAnalysis(List<Element> newActiveNodes, List<Element> obsoleteNodes, List<Element> modifiedNodes,
                                List<String> oldTerms) {
    }

    public static void main(String[] args) throws Exception {
        LOGGER.info("Starting command line run of {}", REPORT_NAME);
        Map<String, String> params = new HashMap<>();
        TermServerScript.run(GmdnMonthlyDeltaReport.class, args, params);
    }

    @Override
    public void init(JobRun run) throws TermServerScriptException {
        LOGGER.info("Initialising {}", REPORT_NAME);
        ReportSheetManager.setTargetFolderId(REPORT_FOLDER_ID);
        currentMonthName = DateUtils.getCurrentMonthName();

        //Are we running in a Spring context?
        ApplicationContext appContext = this.getApplicationContext();

        if (appContext == null) {
            //No, we're running standalone, create a new instance
            LOGGER.info("No Spring context available, creating new GmdnSFTPClient");
            gmdnSFTPClient = new GmdnSFTPClient();
        } else {
            LOGGER.info("Running as Spring, creating new GmdnSFTPClient");
            gmdnSFTPClient = appContext.getBean(GmdnSFTPClient.class);
        }

        LocalDate now = LocalDate.now();
        LocalDate lastMonth = now.minusMonths(1);
        int currentMonthNumber = now.getMonthValue();
        int currentYearNumber = now.getYear();
        int lastMonthNumber = lastMonth.getMonthValue();
        int lastYearNumber = lastMonth.getYear();
        lastMonthsZipFileName = String.format("gmdnData%02d_%d.zip", lastYearNumber - 2000, lastMonthNumber);
        currentMonthsZipFileName = String.format("gmdnData%02d_%d.zip", currentYearNumber - 2000, currentMonthNumber);
        lastMonthsXmlFileName = String.format("gmdnData%02d_%d.xml", lastYearNumber - 2000, lastMonthNumber);
        currentMonthsXmlFileName = String.format("gmdnData%02d_%d.xml", currentYearNumber - 2000, currentMonthNumber);

        super.init(run);
    }

    @Override
    protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
        LOGGER.info("Skipping Snapshot load - not required for this report {}", REPORT_NAME);
    }

    @Override
    public void postInit() throws TermServerScriptException {
        LOGGER.info("Setting up spreadsheet: {}", REPORT_NAME);
        LOGGER.info("Generating GMDN report for: {}", currentMonthName);

        String[] spreadsheetTabNames = new String[]{
                "Status Log",
                currentMonthName + " New Active",
                currentMonthName + " Modified",
                currentMonthName + " Obsolete"
        };

        String[] spreadsheetColumnHeadings = new String[]{
                "Time, Event, Status",
                "Term Code, Term Name, Term Definition, Term Status, Created Date, Modified Date",
                "Term Code, Previous Term Name, Current Term Name, Term Status, Created Date, Modified Date",
                "Term Code, Term Name, Term Definition, Term Status, Created Date, Modified Date"
        };

        super.postInit(spreadsheetTabNames, spreadsheetColumnHeadings);
    }

    @Override
    public Job getJob() {
        return new Job()
                .withCategory(new JobCategory(JobType.REPORT, REPORT_CATEGORY))
                .withName(REPORT_NAME)
                .withDescription(REPORT_DESCRIPTION)
                .withProductionStatus(ProductionStatus.PROD_READY)
                .withTag(INT)
                .withTag(MS)
                .withParameters(new JobParameters())
                .build();
    }

    @Override
    public void runJob() throws TermServerScriptException {
        LOGGER.info("Running: {}", REPORT_NAME);
        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Downloading files for " + currentMonthName, CELL_OK);
        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Downloading " + currentMonthsZipFileName, CELL_OK);
        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Downloading " + lastMonthsZipFileName, CELL_OK);

        try {
            downloadZipFiles();
            unzipFilesToXml();
            generateDeltaReport(LOCAL_WORKING_DIRECTORY + lastMonthsXmlFileName, LOCAL_WORKING_DIRECTORY + currentMonthsXmlFileName);
        } catch (GmdnException e) {
            report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "GMDN Processing failed: " + e.getMessage(), CELL_ERROR);
            throw new TermServerScriptException(e);
        }

        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Completed", CELL_OK);
        LOGGER.info("Completed: {}", REPORT_NAME);
    }

    private void downloadZipFiles() throws GmdnException, TermServerScriptException {
        LOGGER.info("Downloading - expecting last months ZIP file: {}", lastMonthsZipFileName);
        LOGGER.info("and this months ZIP file: {}", currentMonthsZipFileName);

        if (Files.exists(Paths.get(LOCAL_WORKING_DIRECTORY + lastMonthsZipFileName)) && Files.exists(Paths.get(LOCAL_WORKING_DIRECTORY + currentMonthsZipFileName))) {
            LOGGER.info("Zip files already exist, not downloading again.");
            report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Zip files already exist, not downloading again.", CELL_OK);
        } else {
            try {
                gmdnSFTPClient.downloadGmdnFiles(lastMonthsZipFileName, currentMonthsZipFileName);
            } catch (GmdnException e) {
                report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Download of " + lastMonthsZipFileName + " Failed", CELL_ERROR);
                report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), e.getMessage(), CELL_ERROR);
                throw new GmdnException("Zip download error", e);
            }
        }

        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), lastMonthsZipFileName, CELL_OK);
        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), currentMonthsZipFileName, CELL_OK);
    }

    private void unzipFilesToXml() throws GmdnException, TermServerScriptException {
        LOGGER.info("Unzipping - expecting last months XML file: {}", lastMonthsXmlFileName);
        LOGGER.info("and this months XML file: {}", currentMonthsXmlFileName);

        if (Files.exists(Paths.get(LOCAL_WORKING_DIRECTORY + lastMonthsXmlFileName)) && Files.exists(Paths.get(LOCAL_WORKING_DIRECTORY + currentMonthsXmlFileName))) {
            LOGGER.info("Xml files already exist, not unzipping again.");
            report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Xml files already exist, not unzipping again.", CELL_OK);
        } else {
            try {
                unzip(lastMonthsZipFileName, currentMonthsZipFileName);
            } catch (GmdnException e) {
                report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), "Unzipping failed: " + e.getMessage(), CELL_ERROR);
                throw new GmdnException("Unzip error", e);
            }
        }

        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), lastMonthsXmlFileName, CELL_OK);
        report(TAB_STATUS, LocalDateTime.now().format(TIME_FORMATTER), currentMonthsXmlFileName, CELL_OK);
    }

    private void unzip(String... filesToUnzip) throws GmdnException {
        for (String file : filesToUnzip) {
            try {
                ZipFileUtils.extractZipFile(new File(LOCAL_WORKING_DIRECTORY + file), LOCAL_WORKING_DIRECTORY);
            } catch (IOException e) {
                throw new GmdnException("Failed to unzip file " + file, e);
            }
        }
    }

    public void generateDeltaReport(String previousDataFileName, String currentDataFileName) throws GmdnException {
        Builder builder = new Builder();
        Elements previousTerms = getElementsFromXmlDocument(builder, previousDataFileName, "Previous total terms: {}");
        Map<String, Element> previousGmdnTermsMap = new HashMap<>();

        for (int i = 0; i < previousTerms.size(); i++) {
            Element element = previousTerms.get(i);

            if (NON_IVD.equals(element.getFirstChildElement(TERM_IS_IVD.getName()).getValue())) {
                previousGmdnTermsMap.put(element.getFirstChildElement(TERM_CODE.getName()).getValue().trim(), element);
            }
        }

        LOGGER.info("Previous NON-IVD terms total: {}", previousGmdnTermsMap.keySet().size());
        Elements currentTerms = getElementsFromXmlDocument(builder, currentDataFileName, "Current total terms: {}");

        GmdnAnalysis analysisResult = analyzeAllTerms(currentTerms, previousGmdnTermsMap);
        ArrayList<GmdnFields> outputFields = getGmdnFields();

        outputElementsToSpreadsheet(analysisResult.newActiveNodes(), TAB_NEW_ACTIVE, outputFields);
        outputElementsToSpreadsheet(analysisResult.obsoleteNodes(), TAB_OBSOLETE, outputFields);
        outputElementsWithOldTermsToSpreadsheet(analysisResult.modifiedNodes(), TAB_MODIFIED, analysisResult.oldTerms());
    }

    private static GmdnAnalysis analyzeAllTerms(Elements currentTerms, Map<String, Element> previousGmdnTermsMap) {
        List<Element> newActiveNodes = new ArrayList<>();
        List<Element> obsoleteNodes = new ArrayList<>();
        List<Element> modifiedNodes = new ArrayList<>();
        List<String> oldTerms = new ArrayList<>();

        for (int i = 0; i < currentTerms.size(); i++) {
            analyzeTerm(previousGmdnTermsMap, currentTerms.get(i), newActiveNodes, obsoleteNodes, modifiedNodes, oldTerms);
        }

        return new GmdnAnalysis(newActiveNodes, obsoleteNodes, modifiedNodes, oldTerms);
    }

    private static void analyzeTerm(Map<String, Element> previousGmdnTermsMap, Element element, List<Element> newActiveNodes, List<Element> obsoleteNodes, List<Element> modifiedNodes, List<String> oldTerms) {
        if (!NON_IVD.equals(element.getFirstChildElement(TERM_IS_IVD.getName()).getValue())) {
            return;
        }

        String termCode = element.getFirstChildElement(TERM_CODE.getName().trim()).getValue();
        Element previousNode = previousGmdnTermsMap.get(termCode);

        if (previousNode == null) {
            newActiveNodes.add(element);
            return;
        }

        //checking changes from last release
        if (OBSOLETE.equals(element.getFirstChildElement(TERM_STATUS.getName()).getValue()) && ACTIVE.equals(previousNode.getFirstChildElement(TERM_STATUS.getName()).getValue())) {
            obsoleteNodes.add(element);
        } else {
            if (!(previousNode.getFirstChildElement(TERM_NAME.getName()).getValue().trim().equals(element.getFirstChildElement(TERM_NAME.getName()).getValue().trim()))) {
                //term changed
                modifiedNodes.add(element);
                oldTerms.add(previousNode.getFirstChildElement(TERM_NAME.getName()).getValue());
            }
        }
    }

    private static ArrayList<GmdnFields> getGmdnFields() {
        ArrayList<GmdnFields> outputFields = new ArrayList<>();

        for (GmdnFields field : values()) {
            if (!field.equals(TERM_IS_IVD)) {
                outputFields.add(field);
            }
        }
        return outputFields;
    }

    private static Elements getElementsFromXmlDocument(Builder builder, String fileName, String logString) throws GmdnException {
        Document document;

        try {
            document = builder.build(fileName);
        } catch (ParsingException e) {
            throw new GmdnException("Parsing Exception: " + fileName, e);
        } catch (IOException e) {
            throw new GmdnException("IO Exception: " + fileName, e);
        }

        Elements childElements = document.getRootElement().getChildElements(TERM);
        LOGGER.info(logString, childElements.size());

        return childElements;
    }

    public void outputElementsToSpreadsheet(List<Element> nodes, int tab, List<GmdnFields> fields) throws GmdnException {
        for (Element element : nodes) {
            List<String> cells = new ArrayList<>();

            for (GmdnFields field : fields) {
                Element elementField = element.getFirstChildElement(field.getName());
                String fieldValue = elementField == null ? "N/A" : elementField.getValue();
                cells.add(fieldValue);
            }

            try {
                report(tab, cells.toArray(new Object[0]));
            } catch (TermServerScriptException e) {
                throw new GmdnException("Unable to write row", e);
            }
        }
    }

    public void outputElementsWithOldTermsToSpreadsheet(List<Element> termChanged, int tab, List<String> oldTerms) throws GmdnException {
        int i = 0;

        for (Element element : termChanged) {
            List<String> cells = new ArrayList<>();
            cells.add(element.getFirstChildElement(TERM_CODE.getName()).getValue());
            cells.add(oldTerms.get(i++));
            cells.add(element.getFirstChildElement(TERM_NAME.getName()).getValue());
            cells.add(element.getFirstChildElement(TERM_STATUS.getName()).getValue());
            cells.add(element.getFirstChildElement(CREATED_DATE.getName()).getValue());
            cells.add(getModifiedDate(element));

            try {
                report(tab, cells.toArray(new Object[0]));
            } catch (TermServerScriptException e) {
                throw new GmdnException("Unable to write row", e);
            }
        }
    }

    private String getModifiedDate(Element element) {
        //From 2018 June release this field is not set when the no modification made since the created date.
        Element modifiedDateElement = element.getFirstChildElement(MODIFIED_DATE.getName());
        return modifiedDateElement != null ? modifiedDateElement.getValue() : "N/A";
    }
}
