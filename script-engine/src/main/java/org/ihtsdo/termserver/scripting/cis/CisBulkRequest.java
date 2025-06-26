package org.ihtsdo.termserver.scripting.cis;

import java.util.*;

public class CisBulkRequest {
    String comment;
    Long namespace;
    List<String> sctids;
    String software;

    public CisBulkRequest(String comment, Long namespace, List<String> sctids, String software) {
        this.comment = comment;
        this.namespace = namespace;
        this.sctids = sctids;
        this.software = software;
    }

    public String getComment() {
        return comment;
    }

    public Long getNamespace() {
        return namespace;
    }

    public List<String> getSctids() {
        return sctids;
    }

    public String getSoftware() {
        return software;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public void setNamespace(Long namespace) {
        this.namespace = namespace;
    }

    public void setSctids(List<String> sctids) {
        this.sctids = sctids;
    }

    public void setSoftware(String software) {
        this.software = software;
    }

    @Override
    public String toString() {
        return "CisBulkRequest {" +
                "comment = '" + comment + '\'' +
                ", namespace = " + namespace +
                ", sctids (" + sctids.size() + ") = " + sctids +
                ", software = '" + software + '\'' +
                '}';
    }
}
