
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;

public class Branch {

	@SerializedName("metadata")
	@Expose
	private Metadata metadata;
	@SerializedName("name")
	@Expose
	private String name;
	@SerializedName("baseTimestamp")
	@Expose
	private Long baseTimestamp;
	@SerializedName("headTimestamp")
	@Expose
	private Long headTimestamp;
	@SerializedName("deleted")
	@Expose
	private Boolean deleted;
	@SerializedName("path")
	@Expose
	private String path;
	@SerializedName("state")
	@Expose
	private String state;

	/**
	 * No args constructor for use in serialization
	 * 
	 */
	public Branch() {
	}

	/**
	 * 
	 * @param name
	 * @param state
	 * @param path
	 * @param baseTimestamp
	 * @param deleted
	 * @param headTimestamp
	 * @param metadata
	 */
	public Branch(Metadata metadata, String name, Long baseTimestamp, Long headTimestamp, Boolean deleted, String path, String state) {
		super();
		this.metadata = metadata;
		this.name = name;
		this.baseTimestamp = baseTimestamp;
		this.headTimestamp = headTimestamp;
		this.deleted = deleted;
		this.path = path;
		this.state = state;
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public void setMetadata(Metadata metadata) {
		this.metadata = metadata;
	}

	public Branch withMetadata(Metadata metadata) {
		this.metadata = metadata;
		return this;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Branch withName(String name) {
		this.name = name;
		return this;
	}

	public Long getBaseTimestamp() {
		return baseTimestamp;
	}

	public void setBaseTimestamp(Long baseTimestamp) {
		this.baseTimestamp = baseTimestamp;
	}

	public Branch withBaseTimestamp(Long baseTimestamp) {
		this.baseTimestamp = baseTimestamp;
		return this;
	}

	public Long getHeadTimestamp() {
		return headTimestamp;
	}

	public void setHeadTimestamp(Long headTimestamp) {
		this.headTimestamp = headTimestamp;
	}

	public Branch withHeadTimestamp(Long headTimestamp) {
		this.headTimestamp = headTimestamp;
		return this;
	}

	public Boolean getDeleted() {
		return deleted;
	}

	public void setDeleted(Boolean deleted) {
		this.deleted = deleted;
	}

	public Branch withDeleted(Boolean deleted) {
		this.deleted = deleted;
		return this;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public Branch withPath(String path) {
		this.path = path;
		return this;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public Branch withState(String state) {
		this.state = state;
		return this;
	}

	@Override
	public String toString() {
		return new ToStringBuilder(this).append("metadata", metadata).append("name", name).append("baseTimestamp", baseTimestamp).append("headTimestamp", headTimestamp).append("deleted", deleted).append("path", path).append("state", state).toString();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(name).append(state).append(path).append(baseTimestamp).append(deleted).append(headTimestamp).append(metadata).toHashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if ((other instanceof Branch) == false) {
			return false;
		}
		Branch rhs = ((Branch) other);
		return new EqualsBuilder().append(name, rhs.name).append(state, rhs.state).append(path, rhs.path).append(baseTimestamp, rhs.baseTimestamp).append(deleted, rhs.deleted).append(headTimestamp, rhs.headTimestamp).append(metadata, rhs.metadata).isEquals();
	}

}
