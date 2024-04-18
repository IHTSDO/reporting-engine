package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.ScriptException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.termserver.job.ApplicationProperties;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.CodeSystem;
import org.ihtsdo.termserver.scripting.domain.CodeSystemVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.module.storage.ModuleMetadata;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.snomed.module.storage.ModuleStorageCoordinatorException;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class ArchiveCurator extends TermServerReport implements ReportClass {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveCurator.class);
    private static final List<String> NON_PUBLISHED_PACKAGE_TERMS = List.of("_MEMBER_", "_BETA_", "_PREPROD_", "_PREPRODUCTION_", "_Fake_", "_Identifier_", "f", "x", "MSSP", "ISRS", "RECALL");
    private static final String PARAM_NUMBER_OF_VERSIONS = "Number of versions";

    private ResourceManager resourceManagerSource; // Copy packages from this bucket
    private ResourceManager resourceManagerTarget; // Paste packages to this bucket
    private ModuleStorageCoordinator moduleStorageCoordinator; // Copy & paste using MSC
    private ApplicationProperties applicationPropertiesSource;
    private ApplicationProperties applicationPropertiesTarget;

    public static void main(String[] args) throws TermServerScriptException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put(PARAM_NUMBER_OF_VERSIONS, "-1"); // Special character: all versions
        TermServerReport.run(ArchiveCurator.class, args, params);
    }

    @Override
    public void postInit() throws TermServerScriptException {
        String[] spreadsheetTabNames = new String[]{
                "Code Systems",
                "Versions",
                "Ambiguous packages"
        };

        String[] spreadsheetColumnHeadings = new String[]{
                "Code System, Status, Comment",
                "Code System, Effective Time, Status, Time, Comment",
                "Code System, Effective Time, Package Name"
        };

        super.postInit(spreadsheetTabNames, spreadsheetColumnHeadings, false);
    }

    @Override
    public void init(JobRun run) throws TermServerScriptException {
        ReportSheetManager.targetFolderId = "13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"; //Technical Specialist

        super.init(run);
    }

    @Override
    public Job getJob() {
        JobParameters params = new JobParameters()
                .add(PARAM_NUMBER_OF_VERSIONS).withType(JobParameter.Type.STRING).withDefaultValue("1")
                .build();

        return new Job()
                .withCategory(new JobCategory(JobType.REPORT, JobCategory.DEVOPS))
                .withName("Archive Curator")
                .withDescription("This report allows published packages to be selected from dropdown menus. Note: this report only uploads for Dev environments.")
                .withProductionStatus(Job.ProductionStatus.TESTING)
                .withParameters(params)
                .withTag(INT)
                .build();
    }

    @Override
    public void runJob() throws TermServerScriptException {
        try {
            initProperties();
            curateArchives();
        } catch (Exception e) {
            LOGGER.error("ArchiveCurator failed", e);
            throw new TermServerScriptException(e.getMessage());
        }
    }

    private void initProperties() throws TermServerScriptException {
        boolean springContext = this.appContext != null;
        LOGGER.info("springContext: {}", springContext);
        if (springContext) {
            applicationPropertiesSource = ApplicationProperties.from(this.appContext.getBean(ApplicationProperties.class));
            applicationPropertiesTarget = ApplicationProperties.from(this.appContext.getBean(ApplicationProperties.class));

            applicationPropertiesSource.initStandAloneResourceConfig(
                    applicationPropertiesSource.getVersionedContentSourceReadOnly(),
                    applicationPropertiesSource.getVersionedContentSourceUseCloud(),
                    applicationPropertiesSource.getVersionedContentSourceLocalPath(),
                    applicationPropertiesSource.getVersionedContentSourceCloudBucketName(),
                    applicationPropertiesSource.getVersionedContentSourceCloudPath()
            );

            applicationPropertiesTarget.initStandAloneResourceConfig(
                    applicationPropertiesSource.getVersionedContentTargetReadOnly(),
                    applicationPropertiesSource.getVersionedContentTargetUseCloud(),
                    applicationPropertiesSource.getVersionedContentTargetLocalPath(),
                    applicationPropertiesSource.getVersionedContentTargetCloudBucketName(),
                    ""
            );
        } else {
            applicationPropertiesSource = new ApplicationProperties();
            applicationPropertiesTarget = new ApplicationProperties();

            // At this point, only ResourceConfiguration's properties will be set.
            applicationPropertiesSource.init("versioned-content-source", false);
            applicationPropertiesTarget.init("versioned-content", false);
        }

        resourceManagerSource = resourceManagerSource();
        resourceManagerTarget = resourceManagerTarget();
        moduleStorageCoordinator = moduleStorageCoordinator();
    }

    private void curateArchives() throws ScriptException, ModuleStorageCoordinatorException.OperationFailedException, ModuleStorageCoordinatorException.ResourceNotFoundException, ModuleStorageCoordinatorException.InvalidArgumentsException, ModuleStorageCoordinatorException.DuplicateResourceException, IOException {
        LOGGER.info("Attempting to read from target bucket.");
        Set<String> strings = resourceManagerTarget.listFilenamesBySuffix(".zip");
        LOGGER.info("targetBucket size: {}", strings.size());
        Set<CodeSystemTuple> tuples = getTuples();
        LOGGER.info("Found {} tuples.", tuples.size());
        Optional<String> bucketNamePath = resourceManagerSource.getBucketNamePath();
        bucketNamePath.ifPresent(s -> LOGGER.info("source bucket name path: {}", s));

        Set<String> potentialPackages = resourceManagerSource.listFilenamesBySuffix(".zip");
        potentialPackages.removeIf(p -> p.contains("published_build_backup")); // Remove backup packages
        LOGGER.info("Found {} potential packages.", potentialPackages.size());
        int counter = 0;
        int size = tuples.size();
        Map<String, List<ModuleMetadata>> allReleases = moduleStorageCoordinator.getAllReleases();
        LOGGER.info("Found {} releases.", allReleases.size());
        if (allReleases.isEmpty()) {
            LOGGER.info("It's strange no releases have been found: confirm permissions to read from appropriate bucket.");
        }

        nextTuple:
        for (CodeSystemTuple tuple : tuples) {
            sleep(5_000);
            flushFiles(false);
            long start = System.currentTimeMillis();
            counter = counter + 1;
            String codeSystemShortName = tuple.getCodeSystemShortName();
            String moduleId = tuple.getModuleId();
            String effectiveTime = tuple.getEffectiveTime();

            LOGGER.info("Processing {}_{}/{} ({}/{})", codeSystemShortName, moduleId, effectiveTime, counter, size);
            Set<String> potentials = extractPotentialPackages(codeSystemShortName, effectiveTime, potentialPackages);
            if (potentials.isEmpty()) {
                LOGGER.info("No published RF2 package found for {}_{}/{}; skipping.", codeSystemShortName, moduleId, effectiveTime);
                report(1, codeSystemShortName, effectiveTime, "SKIPPED", timeTaken(start), "No RF2 package found");
                continue;
            }

            if (potentials.size() > 1) {
                Set<String> potentialsCopy = new HashSet<>(potentials);
                for (String nonPublishedPackageTerm : NON_PUBLISHED_PACKAGE_TERMS) {
                    potentials.removeIf(p -> p.contains(nonPublishedPackageTerm));
                    potentials.removeIf(p -> p.startsWith(nonPublishedPackageTerm));
                }

                if (potentials.size() != 1) {
                    LOGGER.info("Too many published RF2 packages found for {}_{}/{}; skipping.", codeSystemShortName, moduleId, effectiveTime);
                    report(1, codeSystemShortName, effectiveTime, "SKIPPED", timeTaken(start), "Too many RF2 packages found: " + String.join(",", potentialsCopy));
                    for (String potenitalCopy : potentialsCopy) {
                        report(2, codeSystemShortName, effectiveTime, potenitalCopy);
                    }

                    continue;
                }
            }

            List<ModuleMetadata> moduleMetadata = allReleases.get(codeSystemShortName);
            if (moduleMetadata != null && !moduleMetadata.isEmpty()) {
                for (ModuleMetadata moduleMetadatum : moduleMetadata) {
                    if (moduleMetadatum.getEffectiveTime().toString().equals(effectiveTime)) {
                        LOGGER.info("Entry already exists for {}_{}/{}; skipping.", codeSystemShortName, moduleId, effectiveTime);
                        report(1, codeSystemShortName, effectiveTime, "SKIPPED", timeTaken(start), "Already exists");
                        continue nextTuple;
                    }
                }
            }

            String potential = potentials.iterator().next();
            try (InputStream inputStream = resourceManagerSource.readResourceStream(potential)) {
                LOGGER.info("Uploading new entry to {}_{}/{}...", codeSystemShortName, moduleId, effectiveTime);
                File rf2Package = toFile(inputStream, potential);

                if (rf2Package == null) {
                    report(1, codeSystemShortName, effectiveTime, "FAILED", timeTaken(start));
                    LOGGER.error("Cannot create local file for {}_{}/{}", codeSystemShortName, moduleId, effectiveTime);
                    continue nextTuple;
                }

                moduleStorageCoordinator.upload(codeSystemShortName, moduleId, effectiveTime, rf2Package);
                report(1, codeSystemShortName, effectiveTime, "UPLOADED", timeTaken(start), "");
                LOGGER.info("Successfully uploaded to {}_{}/{}", codeSystemShortName, moduleId, effectiveTime);
            } catch (Exception e) {
                report(1, codeSystemShortName, effectiveTime, "FAILED", timeTaken(start), e.getMessage());
                LOGGER.error("Cannot upload to {}_{}/{}; moving onto next.", codeSystemShortName, moduleId, effectiveTime, e);
            }
        }
    }

    private Set<String> extractPotentialPackages(String shortName, String effectiveTime, Set<String> potentialPackages) {
        Set<String> potentials = new HashSet<>();

        for (String potentialPackage : potentialPackages) {
            String shorterName = shortName == "INT" ? "International" : shortName;
            boolean containsShortName = potentialPackage.contains(shorterName);
            boolean containsEffectiveTime = potentialPackage.contains(effectiveTime);

            if (containsShortName && containsEffectiveTime) {
                potentials.add(potentialPackage);
            }
        }

        return potentials;
    }

    private float timeTaken(long start) {
        long end = System.currentTimeMillis();
        return (end - start) / 1000F;
    }

    private File toFile(InputStream inputStream, String fileName) {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            // e.g. xx/rf2.zip => rf2.zip
            fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
            File file = new File(tempDir, fileName);
            if (file.createNewFile()) {
                try (OutputStream output = new FileOutputStream(file)) {
                    inputStream.transferTo(output);
                }

                file.deleteOnExit();
                return file;
            } else {
                LOGGER.error("Failed to convert InputStream to File; file already exists.");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to convert InputStream to File.", e);
        }

        return null;
    }

    private String getJobRunParam() {
        if (jobRun == null) {
            return "-1";
        }

        return jobRun.getParamValue(PARAM_NUMBER_OF_VERSIONS);
    }

    private Set<CodeSystemTuple> getTuples() throws TermServerScriptException {
        Integer numberOfVersions = asIntegerOrDefault(getJobRunParam(), 1);
        List<CodeSystem> codeSystems = tsClient.getCodeSystems();
        Set<CodeSystemTuple> codeSystemTuples = new TreeSet<>((o1, o2) -> {
            boolean o1isInt = Objects.equals(o1.getCodeSystemShortName(), "INT");
            boolean o2isInt = Objects.equals(o2.getCodeSystemShortName(), "INT");

            if (o1isInt && !o2isInt) {
                return -1;
            } else if (!o1isInt && o2isInt) {
                return 1;
            } else {
                if (o1.getCodeSystemShortName().equals(o2.getCodeSystemShortName())) {
                    return o2.getEffectiveTime().compareTo(o1.getEffectiveTime());
                } else {
                    return o1.getCodeSystemShortName().compareTo(o2.getCodeSystemShortName());
                }
            }
        });

        for (CodeSystem codeSystem : codeSystems) {
            String shortName = codeSystem.getShortName();
            if (shortName.endsWith("FIX") || shortName.endsWith("UPD")) {
                report(0, shortName, "SKIPPED", "Temporary fix project.");
                continue;
            }

            if (shortName.endsWith("AU")) {
                report(0, shortName, "SKIPPED", "Whitelisted");
                continue;
            }

            String branchPath = codeSystem.getBranchPath();
            Branch branch = tsClient.getBranch(branchPath);
            if (branch == null) {
                report(0, shortName, "SKIPPED", "Cannot find corresponding branch on term server.");
                continue;
            }

            Metadata metadata = branch.getMetadata();
            if (metadata == null) {
                report(0, shortName, "SKIPPED", "Cannot find corresponding metadata.");
                continue;
            }

            String defaultModuleId = metadata.getDefaultModuleId();
            if (defaultModuleId == null && shortName.equals("SNOMEDCT")) {
                if (Objects.equals("MAIN", branchPath)) {
                    defaultModuleId = SCTID_CORE_MODULE;
                }
            }

            if (defaultModuleId == null) {
                report(0, shortName, "SKIPPED", "Cannot find default module id.");
                continue;
            }

            LOGGER.info("Finding versions for CodeSystem {}", shortName);
            List<CodeSystemVersion> codeSystemVersions = tsClient.getCodeSystemVersions(shortName);
            if (codeSystemVersions.isEmpty()) {
                report(0, shortName, "SKIPPED", "No versions available.");
                continue;
            }

            Collections.reverse(codeSystemVersions);
            report(0, shortName, "PENDING", numberOfVersions == -1 ? codeSystemVersions.size() + " versions will be processed." : numberOfVersions + " versions will be processed.");
            int versionsAdded = 0;
            for (CodeSystemVersion codeSystemVersion : codeSystemVersions) {
                if (numberOfVersions != -1 && versionsAdded >= numberOfVersions) {
                    continue;
                }

                versionsAdded = versionsAdded + 1;
                String shorterName = Objects.equals(shortName, "SNOMEDCT") ? "INT" : shortName.split("-")[1];
                CodeSystemTuple codeSystemTuple = new CodeSystemTuple(shorterName, defaultModuleId, codeSystemVersion.getEffectiveDate().toString());
                codeSystemTuples.add(codeSystemTuple);
            }
        }

        return codeSystemTuples;
    }

    private ResourceManager resourceManagerSource() throws TermServerScriptException {
        ResourceLoader resourceLoader = getArchiveManager().getS3Manager().getResourceLoader();
        return new ResourceManager(applicationPropertiesSource, resourceLoader);
    }

    private ResourceManager resourceManagerTarget() throws TermServerScriptException {
        ResourceLoader resourceLoader = getArchiveManager().getS3Manager().getResourceLoader();
        return new ResourceManager(applicationPropertiesTarget, resourceLoader);
    }

    private ModuleStorageCoordinator moduleStorageCoordinator() {
        return ModuleStorageCoordinator.initDev(resourceManagerTarget);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private static class CodeSystemTuple {
        private final String codeSystemShortName;
        private final String moduleId;
        private final String effectiveTime;

        public CodeSystemTuple(String codeSystemShortName, String moduleId, String effectiveTime) {
            this.codeSystemShortName = codeSystemShortName;
            this.moduleId = moduleId;
            this.effectiveTime = effectiveTime;
        }

        public String getCodeSystemShortName() {
            return codeSystemShortName;
        }

        public String getModuleId() {
            return moduleId;
        }

        public String getEffectiveTime() {
            return effectiveTime;
        }
    }

    private Integer asIntegerOrDefault(String input, Integer fallback) {
        try {
            int output = Integer.parseInt(input);
            if (output == 0 || output < -1) {
                return fallback;
            }

            return output;
        } catch (Exception e) {
            LOGGER.info("Using fallback as count as {} cannot be converted to integer.", input);
            return fallback;
        }
    }
}
