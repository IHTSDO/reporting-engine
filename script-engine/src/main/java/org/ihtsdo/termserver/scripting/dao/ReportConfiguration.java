package org.ihtsdo.termserver.scripting.dao;

import com.google.common.collect.Sets;
import org.ihtsdo.termserver.scripting.util.StringUtils;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang.StringUtils.split;

public class ReportConfiguration {

    public static String CONFIGURATION_SEPARATOR = "|";

    public enum ReportOutputType { LOCAL_FILE, GOOGLE, S3 }
    public enum ReportFormatType { CSV, JSON }

    private Set<ReportOutputType> reportOutputTypes;
    private Set<ReportFormatType> reportFormatTypes;

    public ReportConfiguration(Set<ReportOutputType> reportOutputTypes,
                               Set<ReportFormatType> reportFormatTypes) {
        try {
            this.reportOutputTypes = reportOutputTypes;
            this.reportFormatTypes = reportFormatTypes;
        } catch (Exception e) {}
    }

    public ReportConfiguration(ReportOutputType reportOutputType,
                               ReportFormatType reportFormatType) {
        this(Sets.newHashSet(reportOutputType), Sets.newHashSet(reportFormatType));
    }

    public ReportConfiguration(String reportOutputTypes,
                               String reportFormatTypes) {
        try {
            this.reportOutputTypes = getEnumSet(reportOutputTypes, ReportOutputType.class);
            this.reportFormatTypes = getEnumSet(reportFormatTypes, ReportFormatType.class);
        } catch (Exception e) {}
    }

    protected <T extends Enum<T>> Set<T> getEnumSet(String configuration, Class<T> enumClass) {
        if (!StringUtils.isEmpty(configuration)) {
            return Arrays.stream(
                    split(configuration, CONFIGURATION_SEPARATOR))
                    .map(string -> Enum.valueOf(enumClass, string))
                    .collect(Collectors.toSet());
        }
        return null;
    }

    public Set<ReportOutputType> getReportOutputTypes() {
        return reportOutputTypes;
    }

    public Set<ReportFormatType> getReportFormatTypes() {
        return reportFormatTypes;
    }

    public boolean isValid() {
        if (reportOutputTypes == null || reportOutputTypes.size() == 0
                || reportFormatTypes == null || reportFormatTypes.size() == 0) {
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReportConfiguration)) return false;
        ReportConfiguration that = (ReportConfiguration) o;
        return Objects.equals(reportOutputTypes, that.reportOutputTypes) &&
                Objects.equals(reportFormatTypes, that.reportFormatTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reportOutputTypes, reportFormatTypes);
    }

    @Override
    public String toString() {
        return "ReportConfiguration{" +
                "reportOutputTypes=" + reportOutputTypes +
                ", reportFormatTypes=" + reportFormatTypes +
                '}';
    }
}
