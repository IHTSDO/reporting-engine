package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.exception.ScriptException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Metadata;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.CodeSystem;
import org.ihtsdo.termserver.scripting.domain.CodeSystemVersion;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.module.storage.ModuleMetadata;
import org.snomed.module.storage.ModuleStorageCoordinator;
import org.snomed.module.storage.ModuleStorageCoordinatorException;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.snomed.otf.script.dao.StandAloneResourceConfig;
import org.springframework.core.io.ResourceLoader;

import java.io.*;
import java.util.*;

public class ArchiveCurator extends TermServerReport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveCurator.class);
    private static final List<String> NON_PUBLISHED_PACKAGE_TERMS = List.of("_MEMBER_", "_BETA_", "_PREPROD_", "_PREPRODUCTION_", "_Fake_", "_Identifier_", "f", "x", "MSSP", "ISRS", "RECALL");

    private final ResourceManager resourceManagerSource;
    private final ResourceManager resourceManagerTarget;
    private final ModuleStorageCoordinator moduleStorageCoordinator;

    public ArchiveCurator() throws TermServerScriptException {
        resourceManagerSource = resourceManagerSource(); // Copy packages from this bucket
        resourceManagerTarget = resourceManagerTarget(); // Paste packages to this bucket
        moduleStorageCoordinator = moduleStorageCoordinator();
    }

    public static void main(String[] args) throws ScriptException, IOException, InterruptedException, ModuleStorageCoordinatorException.OperationFailedException, ModuleStorageCoordinatorException.ResourceNotFoundException, ModuleStorageCoordinatorException.InvalidArgumentsException, ModuleStorageCoordinatorException.DuplicateResourceException {
        ArchiveCurator curator = new ArchiveCurator();
        try {
            ReportSheetManager.setTargetFolderId("13XiH3KVll3v0vipVxKwWjjf-wmjzgdDe"); //Technical Specialist
            curator.init(args);
            curator.postInit(
                    new String[]{
                            // Tabs
                            "Code Systems",
                            "Versions",
                            "Ambiguous packages"
                    },
                    new String[]{
                            // Columns
                            "Code System, Status, Comment,",
                            "Code System, Effective Time, Status, Time (seconds), Comment,",
                            "Code System, Effective Time, Package Name"
                    }
            );
            curator.curateArchives();
        } finally {
            curator.finish();
        }
    }

    private Set<String> extractPotentialPackages(String shortName, String effectiveTime, Set<String> potentialPackages) {
        Set<String> potentials = new HashSet<>();

        for (String potentialPackage : potentialPackages) {
            String shorterName = shortName == "INT" ? "International" : shortName;
            boolean containsShortName = potentialPackage.contains(shorterName);
            boolean containsEffectiveTime = potentialPackage.contains(effectiveTime);

            if (containsShortName && containsEffectiveTime) {
                // e.g. xx/rf2.zip => rf2.zip
                potentialPackage = potentialPackage.substring(potentialPackage.lastIndexOf('/') + 1);
                potentials.add(potentialPackage);
            }
        }

        return potentials;
    }

    private void curateArchives() throws ScriptException, ModuleStorageCoordinatorException.OperationFailedException, ModuleStorageCoordinatorException.ResourceNotFoundException, ModuleStorageCoordinatorException.InvalidArgumentsException, ModuleStorageCoordinatorException.DuplicateResourceException, IOException {
        Set<CodeSystemTuple> tuples = getTuples();
        Set<String> potentialPackages = resourceManagerSource.listFilenamesBySuffix(".zip");
        potentialPackages.removeIf(p -> p.contains("published_build_backup")); // Remove backup packages
        int counter = 0;
        int size = tuples.size();
        Map<String, List<ModuleMetadata>> allReleases = moduleStorageCoordinator.getAllReleases();
        nextTuple:
        for (CodeSystemTuple tuple : tuples) {
            sleep(1_000);
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
                String exceptionMessage = null;
                File rf2Package = toFile(inputStream, potential, exceptionMessage);

                if (rf2Package == null) {
                    report(1, codeSystemShortName, effectiveTime, "FAILED", timeTaken(start), exceptionMessage);
                    LOGGER.error("Cannot create local file for {}_{}/{}; {}", codeSystemShortName, moduleId, effectiveTime, exceptionMessage);
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

    private float timeTaken(long start) {
        long end = System.currentTimeMillis();
        return (end - start) / 1000F;
    }

    private File toFile(InputStream inputStream, String fileName, String exceptionMessage) {
        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            File file = new File(tempDir, fileName);
            if (file.createNewFile()) {
                try (OutputStream output = new FileOutputStream(file)) {
                    inputStream.transferTo(output);
                }

                file.deleteOnExit();
                return file;
            } else {
                LOGGER.error("Failed to convert InputStream to File; file already exists.");
                exceptionMessage = "File already exists";
            }
        } catch (IOException e) {
            LOGGER.error("Failed to convert InputStream to File.", e);
        }

        return null;
    }

    private Set<CodeSystemTuple> getTuples() throws TermServerScriptException {
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
                    return o1.getEffectiveTime().compareTo(o2.getEffectiveTime());
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

            report(0, shortName, "PENDING", codeSystemVersions.size() + " versions will be processed.");
            for (CodeSystemVersion codeSystemVersion : codeSystemVersions) {
                String shorterName = Objects.equals(shortName, "SNOMEDCT") ? "INT" : shortName.split("-")[1];
                CodeSystemTuple codeSystemTuple = new CodeSystemTuple(shorterName, defaultModuleId, codeSystemVersion.getEffectiveDate().toString());
                codeSystemTuples.add(codeSystemTuple);
            }
        }

        return codeSystemTuples;
    }

    private ResourceManager resourceManagerSource() throws TermServerScriptException {
        StandAloneResourceConfig versionedContentLoaderConfig = new StandAloneResourceConfig();
        versionedContentLoaderConfig.init("versioned-content-source", false);
        ResourceLoader resourceLoader = getArchiveManager().getS3Manager().getResourceLoader();
        return new ResourceManager(versionedContentLoaderConfig, resourceLoader);
    }

    private ResourceManager resourceManagerTarget() throws TermServerScriptException {
        StandAloneResourceConfig versionedContentLoaderConfig = new StandAloneResourceConfig();
        versionedContentLoaderConfig.init("versioned-content", false);
        ResourceLoader resourceLoader = getArchiveManager().getS3Manager().getResourceLoader();
        return new ResourceManager(versionedContentLoaderConfig, resourceLoader);
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
}
