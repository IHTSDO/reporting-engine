package org.ihtsdo.termserver.scripting.reports.gmdn.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.LocalProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * GmdnSFTPClient is a component class that handles the downloading of files from an SFTP server.
 * It uses FTPSClient from the Apache Commons Net library to establish a secure FTP connection.
 */
@Lazy
@Service
public class GmdnSFTPClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GmdnSFTPClient.class);
    public static final String FTP_PROTECTION_LEVEL = "P"; // P=Private
    public static final String DOWNLOAD_DIRECTORY = "/tmp/";
    public static final String REMOTE_DIRECTORY = "/";

    private boolean isInitialised = false;

    @Value("${gmdn.report.ftp.host}")
    private String ftpHostName;

    @Value("${gmdn.report.ftp.port:21}")
    private Integer ftpPort = 21;

    @Value("${gmdn.report.ftp.username}")
    private String ftpUserName;

    @Value("${gmdn.report.ftp.password}")
    private String ftpPassword;

    FTPSClient ftpClient;

    public static void main(String[] args) {
        GmdnSFTPClient gmdnSFTPClient = new GmdnSFTPClient();
        try {
            gmdnSFTPClient.downloadGmdnFiles("gmdnData24_3.zip", "gmdnData24_4.zip");
        } catch (GmdnException e) {
            LOGGER.error("FTP Error", e);
        }
    }

    @Lazy
    public GmdnSFTPClient() {
    }

    /**
     * Downloads the specified files from the FTP server.
     *
     * @param file1 the name of the first file to download
     * @param file2 the name of the second file to download
     * @throws GmdnException if there is an error during the download process
     */
    public void downloadGmdnFiles(String file1, String file2) throws GmdnException {
        if (!isInitialised) {
            if (StringUtils.isEmpty(ftpHostName)) {
                loadProperties();
            }
            isInitialised = true;
        }

        if (StringUtils.isEmpty(ftpHostName)) {
            throw new GmdnException("Configuration error, GMDN Hostname has not been supplied");
        }
        
        try {
            ftpLogin();
            ftpDownloadAFile(file1);
            ftpDownloadAFile(file2);
        } finally {
            try {
                // If we failed to log in, then we'll also fail to log out, so just report that
                // but don't throw another exception or that's all we'll report to the user
                ftpLogout();
            } catch (GmdnException e) {
                LOGGER.error("Error in logging out", e);
            }
        }
    }

    private void loadProperties() {
        try {
            LocalProperties properties = new LocalProperties(null);
            this.ftpHostName = properties.getProperty("gmdn.report.ftp.host");
            this.ftpPort = properties.getIntegerProperty("gmdn.report.ftp.port", 21);
            this.ftpUserName = properties.getProperty("gmdn.report.ftp.username");
            this.ftpPassword = properties.getProperty("gmdn.report.ftp.password");
        } catch (Exception e) {
            LOGGER.error("Failed to load local properties file", e);
        }
    }

    private void ftpLogin() throws GmdnException {
        LOGGER.info("Logging in to FTP server: {} as {}", ftpHostName, ftpUserName);
        try {
            ftpClient = new FTPSClient();
            ftpClient.connect(ftpHostName, ftpPort);

            if (!ftpClient.login(ftpUserName, ftpPassword)) {
                throw new GmdnException("Could not login to the server: {} client failed without offering a reason", ftpHostName);
            }

            ftpClient.enterLocalPassiveMode();
            ftpClient.execPROT(FTP_PROTECTION_LEVEL);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        } catch (IOException e) {
            throw new GmdnException("Unable to login to {} as {} ", ftpHostName, ftpUserName, e);
        }
        LOGGER.info("FTP Server Login successful");
    }

    private void ftpDownloadAFile(String fileName) throws GmdnException {
        try (OutputStream outputStream = new FileOutputStream(DOWNLOAD_DIRECTORY + fileName)) {
            if (ftpClient.retrieveFile(REMOTE_DIRECTORY + fileName, outputStream)) {
                LOGGER.info("File has been downloaded successfully: {}", fileName);
            } else {
                throw new GmdnException("Could not download {} due to client reporting error {} {}", fileName, ftpClient.getReplyCode(), ftpClient.getReplyString());
            }
        } catch (IOException e) {
            throw new GmdnException("Unable to download GMDN file: {} due to IOException", fileName, e);
        }
    }

    private void ftpLogout() throws GmdnException {
        try {
            ftpClient.logout();
            ftpClient.disconnect();
        } catch (IOException e) {
            throw new GmdnException("Unable to logout", e);
        }
    }
}
