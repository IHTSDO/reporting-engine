package org.ihtsdo.termserver.scripting.dao;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class ReportConfigurationTest {

    @Test
    public void testValidCreation() {

        ReportConfiguration reportConfiguration =
                new ReportConfiguration("S3|GOOGLE",
                        "CSV");

        Set<ReportConfiguration.ReportOutputType> expectedReportOutputTypes = new HashSet<>();
        expectedReportOutputTypes.add(ReportConfiguration.ReportOutputType.S3);
        expectedReportOutputTypes.add(ReportConfiguration.ReportOutputType.GOOGLE);

        assertEquals(reportConfiguration.getReportOutputTypes(), expectedReportOutputTypes);
        assertTrue(reportConfiguration.isValid());

        Set<ReportConfiguration.ReportFormatType> expectedReportFormatTypes = new HashSet<>();
        expectedReportFormatTypes.add(ReportConfiguration.ReportFormatType.CSV);
        assertTrue(reportConfiguration.isValid());

        assertEquals(reportConfiguration.getReportFormatTypes(), expectedReportFormatTypes);

        assertTrue(reportConfiguration.isValid());
    }

    @Test
    public void testEmptyCreation() {
        ReportConfiguration reportConfiguration =
                new ReportConfiguration(null, "CSV");
        assertEquals(reportConfiguration.getReportOutputTypes(), null);
        assertFalse(reportConfiguration.isValid());

        reportConfiguration =
                new ReportConfiguration("", "CSV");
        assertEquals(reportConfiguration.getReportOutputTypes(), null);
        assertFalse(reportConfiguration.isValid());


        reportConfiguration =
                new ReportConfiguration("S3|GOOGLE", null);
        assertEquals(reportConfiguration.getReportFormatTypes(), null);
        assertFalse(reportConfiguration.isValid());

        reportConfiguration =
                new ReportConfiguration("S3|GOOGLE", "");
        assertEquals(reportConfiguration.getReportFormatTypes(), null);
        assertFalse(reportConfiguration.isValid());

        reportConfiguration =
                new ReportConfiguration("S3|GOOGLE", "");
        assertEquals(reportConfiguration.getReportFormatTypes(), null);
        assertFalse(reportConfiguration.isValid());
    }

    @Test
    public void testInvalidEnumValue() {
        ReportConfiguration reportConfiguration =
                new ReportConfiguration("S3|GOOGLE", "CSV_INVALID");

        Set<ReportConfiguration.ReportFormatType> expectedReportFormatTypes = new HashSet<>();
        expectedReportFormatTypes.add(ReportConfiguration.ReportFormatType.CSV);
        assertFalse(reportConfiguration.isValid());
    }

    @Test
    public void testConstructionWithEnumsDirectly() {
        ReportConfiguration reportConfiguration = new ReportConfiguration(
                ReportConfiguration.ReportOutputType.GOOGLE,
                ReportConfiguration.ReportFormatType.CSV);

        assertTrue(reportConfiguration.isValid());
    }
}
