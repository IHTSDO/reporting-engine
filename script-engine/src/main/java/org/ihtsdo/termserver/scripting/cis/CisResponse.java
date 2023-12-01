package org.ihtsdo.termserver.scripting.cis;

public class CisResponse
{
    String created_at;
    Long id;
    String modified_at;
    String name;
    String request;
    String status;
    String log;

    public CisResponse(String created_at, Long id, String modified_at, String name, String request, String status) {
        this.created_at = created_at;
        this.id = id;
        this.modified_at = modified_at;
        this.name = name;
        this.request = request;
        this.status = status;
    }


    public String getCreated_at() {
        return created_at;
    }

    public Long getId() {
        return id;
    }

    public String getModified_at() {
        return modified_at;
    }

    public String getName() {
        return name;
    }

    public String getRequest() {
        return request;
    }

    public String getStatus() {
        return status;
    }

    public void setCreated_at(String created_at) {
        this.created_at = created_at;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setModified_at(String modified_at) {
        this.modified_at = modified_at;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRequest(String request) {
        this.request = request;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getLog() {
        return log;
    }

    public void setLog(String log) {
        this.log = log;
    }

    @Override
    public String toString() {
        return "CisResponse{" +
                "created_at='" + created_at + '\'' +
                ", id=" + id +
                ", modified_at='" + modified_at + '\'' +
                ", name='" + name + '\'' +
                ", request='" + request + '\'' +
                ", status='" + status + '\'' +
                '}';
    }

}
