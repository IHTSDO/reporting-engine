package org.ihtsdo.termserver.scripting.cis;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CisBulkRegisterRequest {
    String comment;
    Long namespace;
    List<Record> records;
    String software;

    public CisBulkRegisterRequest(String comment, Long namespace, List<String> sctids, String software) {
        this.comment = comment;
        this.namespace = namespace;
        records = sctids.stream()
                .map(s -> new Record(s))
                .collect(Collectors.toList());
        this.software = software;
    }

    public String getComment() {
        return comment;
    }

    public Long getNamespace() {
        return namespace;
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

    public void setSoftware(String software) {
        this.software = software;
    }

    @Override
    public String toString() {
        return "CisBulkRequest{" +
                "comment='" + comment + '\'' +
                ", namespace=" + namespace +
                ", sctids=" + getSCTIDs() +
                ", software='" + software + '\'' +
                '}';
    }

    private String getSCTIDs() {
        return records.stream()
                .map(r -> r.sctid)
                .collect(Collectors.joining(", "));
    }

    class Record {
        String sctid;
        String systemId;

        public Record(String sctid) {
            this.sctid = sctid;
            this.systemId = java.util.UUID.randomUUID().toString();
        }
    }
}
