
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Metadata {

    @SerializedName("assertionGroupNames")
    @Expose
    private String assertionGroupNames;
    @SerializedName("defaultNamespace")
    @Expose
    private String defaultNamespace;
    @SerializedName("previousRelease")
    @Expose
    private String previousRelease;
    @SerializedName("previousPackage")
    @Expose
    private String previousPackage;
    @SerializedName("dependencyRelease")
    @Expose
    private String dependencyRelease;
    @SerializedName("codeSystemShortName")
    @Expose
    private String codeSystemShortName;
    @SerializedName("languageSearch")
    @Expose
    private String languageSearch;
    @SerializedName("defaultModuleId")
    @Expose
    private String defaultModuleId;
    @SerializedName("shortname")
    @Expose
    private String shortname;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Metadata() {
    }

    /**
     * 
     * @param languageSearch
     * @param assertionGroupNames
     * @param previousRelease
     * @param codeSystemShortName
     * @param defaultModuleId
     * @param defaultNamespace
     * @param shortname
     * @param dependencyRelease
     */
    public Metadata(String assertionGroupNames, String defaultNamespace, String previousRelease, String dependencyRelease, String codeSystemShortName, String languageSearch, String defaultModuleId, String shortname) {
        super();
        this.assertionGroupNames = assertionGroupNames;
        this.defaultNamespace = defaultNamespace;
        this.previousRelease = previousRelease;
        this.dependencyRelease = dependencyRelease;
        this.codeSystemShortName = codeSystemShortName;
        this.languageSearch = languageSearch;
        this.defaultModuleId = defaultModuleId;
        this.shortname = shortname;
    }

    public String getAssertionGroupNames() {
        return assertionGroupNames;
    }

    public void setAssertionGroupNames(String assertionGroupNames) {
        this.assertionGroupNames = assertionGroupNames;
    }

    public String getDefaultNamespace() {
        return defaultNamespace;
    }

    public void setDefaultNamespace(String defaultNamespace) {
        this.defaultNamespace = defaultNamespace;
    }

    public String getPreviousRelease() {
        return previousRelease;
    }

    public void setPreviousRelease(String previousRelease) {
        this.previousRelease = previousRelease;
    }
    
    public String getPreviousPackage() {
        return previousPackage;
    }

    public void setPreviousPackagee(String previousPackage) {
        this.previousPackage = previousPackage;
    }

    public String getDependencyRelease() {
        return dependencyRelease;
    }

    public void setDependencyRelease(String dependencyRelease) {
        this.dependencyRelease = dependencyRelease;
    }

    public String getCodeSystemShortName() {
        return codeSystemShortName;
    }

    public void setCodeSystemShortName(String codeSystemShortName) {
        this.codeSystemShortName = codeSystemShortName;
    }

    public String getLanguageSearch() {
        return languageSearch;
    }

    public void setLanguageSearch(String languageSearch) {
        this.languageSearch = languageSearch;
    }

    public String getDefaultModuleId() {
        return defaultModuleId;
    }

    public void setDefaultModuleId(String defaultModuleId) {
        this.defaultModuleId = defaultModuleId;
    }

    public String getShortname() {
        return shortname;
    }

    public void setShortname(String shortname) {
        this.shortname = shortname;
    }

}
