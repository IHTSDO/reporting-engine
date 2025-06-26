package org.ihtsdo.termserver.scripting.cis;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

public class CisRecord {

	static DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").withZone(DateTimeZone.UTC);
	
	private Long sctid;
	//private String sequence
	//private String namespace
	//private String partitionId
	//private String checkDigit
	//private String systemId
	private String status;
	//private String author
	private String software;
	//private String expirationDate
	//private String comment
	private Integer jobId;
	private DateTime createdAt;
	private DateTime modifiedAt;
	
	public Long getSctid() {
		return sctid;
	}
	public void setSctid(Long sctid) {
		this.sctid = sctid;
	}

	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getSoftware() {
		return software;
	}
	public void setSoftware(String software) {
		this.software = software;
	}
	public Integer getJobId() {
		return jobId;
	}
	public void setJobId(Integer jobId) {
		this.jobId = jobId;
	}
	public DateTime getCreatedAt() {
		return createdAt;
	}
	public void setCreatedAt(DateTime createdAt) {
		this.createdAt = createdAt;
	}
	public DateTime getModifiedAt() {
		return modifiedAt;
	}
	public void setModifiedAt(DateTime modifiedAt) {
		this.modifiedAt = modifiedAt;
	}
	
	public String toString() {
		return sctid + " is " + status + ", software:'" + getOrNA(software, true)  +
				"', jobId: " + getOrNA(jobId, false) + ", modified: " + getOrNA(modifiedAt, false);
	}

	private String getOrNA(Object s, boolean quote) {
		if (s == null) {
			return "N/A";
		}
		return quote ? "'" + s + "'" : s.toString();
	}
	
	public static CisRecord create(String[] items) {
		CisRecord cr = new CisRecord();
		cr.sctid = Long.parseLong(items[0]);
		//cr.sequence = items[1]
		//cr.namespace = items[2]
		//cr.partitionId = items[3]
		//cr.checkDigit = items[4]
		//cr.systemId = items[5]
		cr.status = items[6];
		//cr.author = items[7]
		cr.software = items[8].equals("\\N")? null : items[8];
		//cr.expirationDate = items[9]
		//cr.comment = items[10]
		cr.jobId = items[11].equals("\\N")? null : Integer.parseInt(items[11]);
		cr.createdAt = items[12].equals("\\N")? null:DateTime.parse(items[12], formatter);
		cr.modifiedAt = items[13].equals("\\N")? null:DateTime.parse(items[13], formatter);
		return cr;
	}
	
	public Object[] toArray() {
		return new Object[] {sctid, status, software, jobId, createdAt, modifiedAt};
	}
}
