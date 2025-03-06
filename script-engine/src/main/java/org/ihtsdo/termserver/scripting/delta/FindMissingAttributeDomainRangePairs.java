package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class FindMissingAttributeDomainRangePairs extends DeltaGenerator {
    private static final String CONTENT_TYPE_ALL_ID = "723596005";
    private static final String CONTENT_TYPE_ALL_NAME = "All SNOMED CT content";
    private static final String CONTENT_TYPE_PRE_ID = "723594008";
    private static final String CONTENT_TYPE_PRE_NAME = "All precoordinated SNOMED CT content";
    private static final String CONTENT_TYPE_PRE_NEW_ID = "723593002";
    private static final String CONTENT_TYPE_PRE_NEW_ID_NAME = "All new precoordinated SNOMED CT content";
    private static final String CONTENT_TYPE_POST_ID = "723595009";
    private static final String CONTENT_TYPE_POST_NAME = " All postcoordinated SNOMED CT content";
    private static final String MRCM_ATTRIBUTE_DOMAIN_REFSET_ID = "723561005";
    private static final String MRCM_ATTRIBUTE_RANGE_REFSET_ID = "723562003";
    
    private static final String CONTENT_TYPE_ID = "contentTypeId";

    private List<RefsetMember> rangesActive = new ArrayList<>();
    private List<RefsetMember> rangesInactive = new ArrayList<>();
    private List<RefsetMember> rangesWithoutAttributes = new ArrayList<>();

    private List<RefsetMember> attributesActive = new ArrayList<>();
    private List<RefsetMember> attributesInactive = new ArrayList<>();

    private List<RefsetMember> ranges;
    private List<RefsetMember> attributes;

    private int rangesGreaterThanAttributes = 0;
    private int attributesWithoutRanges = 0;
    private int suggestions = 0;

    /**
     * The branch path to query on terminology server.
     */
    private static final String BRANCH = ""; // Give me a value

    public static void main(String[] args) throws Exception {
        FindMissingAttributeDomainRangePairs app = new FindMissingAttributeDomainRangePairs();
        try {
            String now = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            app.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
            app.newIdsRequired = false;
            app.packageDir = "output" + File.separator + "Delta_" + now + File.separator;
            app.init(args);
            app.postInit(GFOLDER_ADHOC_UPDATES);
            app.validateScriptArguments();
            app.process();
            app.flushFiles(false);
            SnomedUtils.createArchive(new File(app.outputDirName));
        } finally {
            app.finish();
        }
    }

    @Override
    public void postInit(String googleFolder) throws TermServerScriptException {
        String[] columnHeadings = new String[]{
                "Item, Count, Note",
                "Range Id, Range Content Type, Attribute Id, Attribute Content Type, Solution",
                "Attribute Id, Attribute Content Type, Solution"
        };

        String[] tabNames = new String[]{
                "Overview",
                "Ranges greater than Attributes",
                "Attributes without Ranges"
        };

        super.postInit(googleFolder, tabNames, columnHeadings);
    }

    @Override
    protected void process() throws TermServerScriptException {

        populateRangesAndAttributes();

        for (RefsetMember range : rangesActive) {
           boolean attributeFound = false;

           attributeFound |= processRange(range);

	        if (!attributeFound) {
	            rangesWithoutAttributes.add(range);
	        }
        }

        processAttributes();

        outputReports();
    }

    private boolean processRange(RefsetMember range) throws TermServerScriptException {
    	 boolean attributeFound = false;
    	 for (RefsetMember attribute : attributesActive) {
             boolean matchReferencedComponentId = Objects.equals(attribute.getReferencedComponentId(), range.getReferencedComponentId());
             boolean matchContentTypeId = Objects.equals(attribute.getField(CONTENT_TYPE_ID), range.getField(CONTENT_TYPE_ID));

             // Match
             if (matchReferencedComponentId && matchContentTypeId) {
                 attributeFound = true;
                 continue;
             }

             // Match, but requires scope change
             if (matchReferencedComponentId && !matchContentTypeId) {
                 attributeFound = true;

                 int attributeScore = getScore(attribute.getField(CONTENT_TYPE_ID));
                 int rangeScore = getScore(range.getField(CONTENT_TYPE_ID));
                 if (rangeScore > attributeScore) {
                     suggestions = suggestions + 1;
                     rangesGreaterThanAttributes = rangesGreaterThanAttributes + 1;
                     report(1, range.getMemberId(), asContentType(range.getField(CONTENT_TYPE_ID)), attribute.getMemberId(), asContentType(attribute.getField(CONTENT_TYPE_ID)), "Change contentTypeId of Range to match Attribute");
                 }
             }
         }
    	 return attributeFound;
	}

	private void processAttributes() throws TermServerScriptException {
    	for (RefsetMember attribute : attributes) {
            boolean rangeFound = false;
            for (RefsetMember range : ranges) {
                boolean matchReferencedComponentId = Objects.equals(attribute.getReferencedComponentId(), range.getReferencedComponentId());
                if (matchReferencedComponentId) {
                    rangeFound = true;
                    break;
                }
            }

            if (!rangeFound) {
                suggestions = suggestions + 1;
                attributesWithoutRanges = attributesWithoutRanges + 1;
                report(2, attribute.getMemberId(), asContentType(attribute.getField(CONTENT_TYPE_ID)), "Create Attribute Range for content type " + asContentType(attribute.getField(CONTENT_TYPE_ID)));
            }
        }
		
	}

	private void outputReports() throws TermServerScriptException {
        report(0, "Attribute Ranges", ranges.size());
        report(0, "Attribute Ranges (active)", rangesActive.size());
        report(0, "Attribute Ranges (inactive)", rangesInactive.size());
        report(0, "");

        report(0, "Attribute Domains", attributes.size());
        report(0, "Attribute Domains (active)", attributesActive.size());
        report(0, "Attribute Domains (inactive)", attributesInactive.size());
        report(0, "");

        report(0, "Ranges greater than Attributes", rangesGreaterThanAttributes, "The Attribute Range has a greater scope (i.e. contentTypeId) than its corresponding Attribute Domain.");
        report(0, "Ranges without Attributes", rangesWithoutAttributes.size(), "The Attribute Range does not have any corresponding Attribute Domains.");
        report(0, "Attributes without Ranges", attributesWithoutRanges, "The Attribute Domain does not have any corresponding Attribute Ranges.");
        report(0, "Suggested fixes", suggestions, "Suggestions on how to fix content.");
        report(0, "");
    }

    private void populateRangesAndAttributes() {
        ranges = fetchReferenceSetMembers(BRANCH, MRCM_ATTRIBUTE_RANGE_REFSET_ID);
        for (RefsetMember range : ranges) {
            if (range.isActiveSafely()) {
                rangesActive.add(range);
            } else {
                rangesInactive.add(range);
            }
        }

        attributes = fetchReferenceSetMembers(BRANCH, MRCM_ATTRIBUTE_DOMAIN_REFSET_ID);
        for (RefsetMember attribute : attributes) {
            if (attribute.isActiveSafely()) {
                attributesActive.add(attribute);
            } else {
                attributesInactive.add(attribute);
            }
        }
    }

    private void validateScriptArguments() throws TermServerScriptException {
        // Verify branches exist
        if (BRANCH.isBlank()) {
            throw new TermServerScriptException("No branch path given.");
        }

        Branch branch = tsClient.getBranch(BRANCH);
        if (branch == null) {
            throw new TermServerScriptException(String.format("Cannot find branch with path '%s'.", BRANCH));
        }
    }

    private List<RefsetMember> fetchReferenceSetMembers(String branch, String referenceSetId) {
        return tsClient.getMembersByReferenceSet(branch, referenceSetId);
    }

    // Arbitrary ranking to show whether a range has a greater scope than it's corresponding attribute
    private int getScore(String contentTypeId) {
        return switch (contentTypeId) {
            case CONTENT_TYPE_ALL_ID -> 3;
            case CONTENT_TYPE_POST_ID -> 2;
            case CONTENT_TYPE_PRE_ID -> 1;
            case CONTENT_TYPE_PRE_NEW_ID -> 0;
            default -> throw new IllegalArgumentException("ContentTypeId " + contentTypeId + " not recognised.");
        };
    }

    private String asContentType(String contentTypeId) {
        return switch (contentTypeId) {
            case CONTENT_TYPE_ALL_ID -> CONTENT_TYPE_ALL_NAME;
            case CONTENT_TYPE_POST_ID -> CONTENT_TYPE_POST_NAME;
            case CONTENT_TYPE_PRE_ID -> CONTENT_TYPE_PRE_NAME;
            case CONTENT_TYPE_PRE_NEW_ID -> CONTENT_TYPE_PRE_NEW_ID_NAME;
            default -> throw new IllegalArgumentException("ContentTypeId " + contentTypeId + " not recognised.");
        };
    }
}
