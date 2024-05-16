
package org.ihtsdo.termserver.scripting.domain;

import java.util.List;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;

public class Refset {

    @SerializedName("items")
    @Expose
    private List<RefsetMember> items = null;
    @SerializedName("offset")
    @Expose
    private Long offset;
    @SerializedName("limit")
    @Expose
    private Long limit;
    @SerializedName("total")
    @Expose
    private Long total;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Refset() {
    }

    /**
     * 
     * @param total
     * @param limit
     * @param items
     * @param offset
     */
    public Refset(List<RefsetMember> items, Long offset, Long limit, Long total) {
        super();
        this.items = items;
        this.offset = offset;
        this.limit = limit;
        this.total = total;
    }

    public List<RefsetMember> getItems() {
        return items;
    }

    public void setItems(List<RefsetMember> items) {
        this.items = items;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public Long getLimit() {
        return limit;
    }

    public void setLimit(Long limit) {
        this.limit = limit;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

}
