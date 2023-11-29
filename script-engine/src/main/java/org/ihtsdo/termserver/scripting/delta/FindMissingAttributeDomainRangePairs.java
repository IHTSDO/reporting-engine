package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.termserver.scripting.domain.RefsetMember;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class FindMissingAttributeDomainRangePairs extends DeltaGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(FindMissingAttributeDomainRangePairs.class);

    private static final String CONTENT_TYPE_ALL_ID = "723596005";
    private static final String CONTENT_TYPE_ALL_NAME = "All SNOMED CT content";
    private static final String CONTENT_TYPE_PRE_ID = "723594008";
    private static final String CONTENT_TYPE_PRE_NAME = "All precoordinated SNOMED CT content";
    private static final String CONTENT_TYPE_POST_ID = "723595009";
    private static final String CONTENT_TYPE_POST_NAME = "All postcoordinated SNOMED CT content";

    private static final String MRCM_ATTRIBUTE_DOMAIN_REFSET_ID = "723561005";
    private static final String MRCM_ATTRIBUTE_DOMAIN_REFSET_NAME = "MRCM attribute domain international reference set";
    private static final String MRCM_ATTRIBUTE_RANGE_REFSET_ID = "723562003";
    private static final String MRCM_ATTRIBUTE_RANGE_REFSET_NAME = "MRCM attribute range international reference set";

    /**
     * The branch path to query on terminology server.
     */
    private static final String BRANCH = ""; // Give me a value

    public static void main(String[] args) throws Exception {
        FindMissingAttributeDomainRangePairs app = new FindMissingAttributeDomainRangePairs();
        try {
            String now = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            app.getArchiveManager().setPopulateReleasedFlag(true);
            app.newIdsRequired = false;
            app.packageDir = "output" + File.separator + "Delta_" + now + File.separator;
            app.init(args);
            app.postInit();
            app.validateScriptArguments();
            app.process();
            app.flushFiles(false);
            SnomedUtils.createArchive(new File(app.outputDirName));
        } finally {
            app.finish();
        }
    }

    public void postInit() throws TermServerScriptException {
        String[] columnHeadings = new String[]{
                "Concept Id, Content Type, Domain, Range, Pair found",
                "Concept Id, Content Type, Domain, Range, Pair found",
                "Concept Id, Content Type, Domain, Range, Pair found"
        };

        String[] tabNames = new String[]{
                "All",
                "Pre",
                "Post"
        };

        super.postInit(tabNames, columnHeadings, false);
    }

    private void process() throws Exception {
        // Collect AttributeDomains
        List<RefsetMember> membersDomain = fetchReferenceSetMembers(BRANCH, MRCM_ATTRIBUTE_DOMAIN_REFSET_ID, MRCM_ATTRIBUTE_DOMAIN_REFSET_NAME);
        List<RefsetMember> membersDomainFilteredByContentAll = filterReferenceSetMembers(membersDomain, CONTENT_TYPE_ALL_ID, CONTENT_TYPE_ALL_NAME);
        List<RefsetMember> membersDomainFilteredByContentPre = filterReferenceSetMembers(membersDomain, CONTENT_TYPE_PRE_ID, CONTENT_TYPE_PRE_NAME);
        List<RefsetMember> membersDomainFilteredByContentPost = filterReferenceSetMembers(membersDomain, CONTENT_TYPE_POST_ID, CONTENT_TYPE_POST_NAME);

        // Collect AttributeRanges
        List<RefsetMember> membersRange = fetchReferenceSetMembers(BRANCH, MRCM_ATTRIBUTE_RANGE_REFSET_ID, MRCM_ATTRIBUTE_RANGE_REFSET_NAME);
        List<RefsetMember> membersRangeFilteredByContentAll = filterReferenceSetMembers(membersRange, CONTENT_TYPE_ALL_ID, CONTENT_TYPE_ALL_NAME);
        List<RefsetMember> membersRangeFilteredByContentPre = filterReferenceSetMembers(membersRange, CONTENT_TYPE_PRE_ID, CONTENT_TYPE_PRE_NAME);
        List<RefsetMember> membersRangeFilteredByContentPost = filterReferenceSetMembers(membersRange, CONTENT_TYPE_POST_ID, CONTENT_TYPE_POST_NAME);

        // Merge collections into single map. Map ReferenceSetMembers by referencedComponentId
        Map<String, List<RefsetMember>> referenceSetMembersByReferencedComponentId = mapReferenceSetMembersByReferencedComponentId(
                membersDomainFilteredByContentAll,
                membersDomainFilteredByContentPre,
                membersDomainFilteredByContentPost,
                membersRangeFilteredByContentAll,
                membersRangeFilteredByContentPre,
                membersRangeFilteredByContentPost
        );

        // Report on mismatching levels
        reportDifferences(0, referenceSetMembersByReferencedComponentId, CONTENT_TYPE_ALL_ID, CONTENT_TYPE_ALL_NAME);
        reportDifferences(1, referenceSetMembersByReferencedComponentId, CONTENT_TYPE_PRE_ID, CONTENT_TYPE_PRE_NAME);
        reportDifferences(2, referenceSetMembersByReferencedComponentId, CONTENT_TYPE_POST_ID, CONTENT_TYPE_POST_NAME);
    }

    private void validateScriptArguments() throws TermServerScriptException {
        // Verify branches exist
        getBranchOrThrow(BRANCH);
    }

    private Branch getBranchOrThrow(String branchPath) throws TermServerScriptException {
        Branch branch = tsClient.getBranch(branchPath);
        if (branch == null) {
            throw new TermServerScriptException(String.format("Cannot find branch with path '%s'.", branchPath));
        }

        return branch;
    }

    private List<RefsetMember> fetchReferenceSetMembers(String branch, String referenceSetId, String referenceSetName) {
        LOGGER.info("Fetching all ReferenceSetMembers in {} |{}|.", referenceSetId, referenceSetName);

        return tsClient.getMembersByReferenceSet(branch, referenceSetId);
    }

    private List<RefsetMember> filterReferenceSetMembers(List<RefsetMember> refsetMembers, String contentTypeId, String contentTypeName) {
        LOGGER.info("Filtering ReferenceSetMembers that do not have contentTypeId matching {} |{}|.", contentTypeId, contentTypeName);
        List<RefsetMember> refsetMembersFiltered = new ArrayList<>();
        for (RefsetMember refsetMember : refsetMembers) {
            if (contentTypeId.equals(refsetMember.getField("contentTypeId"))) {
                refsetMembersFiltered.add(refsetMember);
            }
        }

        return refsetMembersFiltered;
    }

    private Map<String, List<RefsetMember>> mapReferenceSetMembersByReferencedComponentId(List<RefsetMember>... refsetMembers) {
        Map<String, List<RefsetMember>> map = new HashMap<>();
        for (List<RefsetMember> refsetMemberList : refsetMembers) {
            for (RefsetMember refsetMember : refsetMemberList) {
                List<RefsetMember> value = map.get(refsetMember.getReferencedComponentId());
                if (value == null) {
                    value = new ArrayList<>();
                }

                value.add(refsetMember);
                map.put(refsetMember.getReferencedComponentId(), value);
            }
        }

        return map;
    }

    private void reportDifferences(int reportIndex, Map<String, List<RefsetMember>> membersByReferencedComponentId, String contentTypeId, String contentTypeName) throws TermServerScriptException {
        for (Map.Entry<String, List<RefsetMember>> entrySet : membersByReferencedComponentId.entrySet()) {
            String referencedComponentId = entrySet.getKey();
            List<RefsetMember> referenceSetMembers = entrySet.getValue();

            boolean foundDomain = false;
            boolean foundRange = false;
            for (RefsetMember referenceSetMember : referenceSetMembers) {
                if (MRCM_ATTRIBUTE_DOMAIN_REFSET_ID.equals(referenceSetMember.getRefsetId()) && contentTypeId.equals(referenceSetMember.getField("contentTypeId"))) {
                    foundDomain = true;
                }

                if (MRCM_ATTRIBUTE_RANGE_REFSET_ID.equals(referenceSetMember.getRefsetId()) && contentTypeId.equals(referenceSetMember.getField("contentTypeId"))) {
                    foundRange = true;
                }
            }

            report(reportIndex, referencedComponentId, contentTypeId + " |" + contentTypeName + "|", foundDomain, foundRange, foundDomain & foundRange);
        }
    }
}
