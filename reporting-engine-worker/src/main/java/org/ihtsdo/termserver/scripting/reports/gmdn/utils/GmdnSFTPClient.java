package org.ihtsdo.termserver.scripting.reports.gmdn.utils;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.LocalProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * GmdnSFTPClient is a component class that handles the downloading of files from an SFTP server.
 * It uses FTPSClient from the Apache Commons Net library to establish a secure FTP connection.
 */
@Service
public class GmdnSFTPClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GmdnSFTPClient.class);
    public static final String FTP_PROTECTION_LEVEL = "P"; // P=Private
    public static final String DOWNLOAD_DIRECTORY = "/tmp/";
    public static final String REMOTE_DIRECTORY = "/";

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

    public GmdnSFTPClient() {
		if (StringUtils.isEmpty(ftpHostName)) {
            loadProperties();
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
            LOGGER.error("Failed to load properties file", e);
        }
    }

    /**
     * Downloads the specified files from the FTP server.
     *
     * @param file1 the name of the first file to download
     * @param file2 the name of the second file to download
     * @throws GmdnException if there is an error during the download process
     */
    public void downloadGmdnFiles(String file1, String file2) throws GmdnException {
        try {
            ftpLogin();
            ftpDownloadAFile(file1);
            ftpDownloadAFile(file2);
        } finally {
            ftpLogout();
        }
    }

    private void ftpLogin() throws GmdnException {
        try {
            ftpClient = new FTPSClient();
            ftpClient.connect(ftpHostName, ftpPort);

            if (!ftpClient.login(ftpUserName, ftpPassword)) {
                throw new GmdnException("Could not login to the server: {}", ftpHostName);
            }

            ftpClient.enterLocalPassiveMode();
            ftpClient.execPROT(FTP_PROTECTION_LEVEL);
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        } catch (IOException e) {
            throw new GmdnException("Unable to login", e);
        }
    }

    private void ftpDownloadAFile(String fileName) throws GmdnException {
        try (OutputStream outputStream = new FileOutputStream(DOWNLOAD_DIRECTORY + fileName)) {
            if (ftpClient.retrieveFile(REMOTE_DIRECTORY + fileName, outputStream)) {
                LOGGER.info("File has been downloaded successfully: {}", fileName);
            } else {
                LOGGER.error("Error in downloading file: {}", fileName);
                LOGGER.error("Reply code: {}", ftpClient.getReplyCode());
                LOGGER.error("Reply string: {}", ftpClient.getReplyString());
                throw new GmdnException("Could not download: {}", fileName);
            }
        } catch (IOException e) {
            throw new GmdnException("Unable to logout", e);
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
