package org.ihtsdo.termserver.scripting.reports.release;

import com.google.gson.annotations.Expose;

public class ReleaseSummaryDetail implements Comparable<ReleaseSummaryDetail> {

	@Expose
	private String effectiveTime;
	
	@Expose
	private String previousEffectiveTime;
	
	@Expose
	private String[] data;

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public String getPreviousEffectiveTime() {
		return previousEffectiveTime;
	}

	public void setPreviousEffectiveTime(String previousEffectiveTime) {
		this.previousEffectiveTime = previousEffectiveTime;
	}

	public String[] getData() {
		return data;
	}

	public void setData(String[] data) {
		this.data = data;
	}
	
	@Override
	public boolean equals(Object o) {
		//Release details take their effective time as a primary key
		if (o instanceof ReleaseSummaryDetail) {
			ReleaseSummaryDetail other = (ReleaseSummaryDetail)o;
			if (this.getEffectiveTime() == null || other.getEffectiveTime() == null) {
				throw new IllegalStateException("ReleaseSummaryDetail encountered with null EffectiveTime");
			}
			return this.getEffectiveTime().equals(((ReleaseSummaryDetail)o).getEffectiveTime());
		}
		return false;
	}
	
	@Override
	public int hashCode() {
		if (this.getEffectiveTime() == null) {
			throw new IllegalStateException("ReleaseSummaryDetail encountered with null EffectiveTime");
		}
		return this.getEffectiveTime().hashCode();
	}
	
	@Override
	public int compareTo(ReleaseSummaryDetail other) {
		if (this.getEffectiveTime() == null || other.getEffectiveTime() == null) {
			throw new IllegalStateException("ReleaseSummaryDetail encountered with null EffectiveTime");
		}
		return other.getEffectiveTime().compareTo(this.getEffectiveTime());
	}
}
