package org.ihtsdo.termserver.scripting.reports.gmdn.utils;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.Selectors;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.apache.commons.vfs2.provider.sftp.SftpFileSystemConfigBuilder;


public class GmdnSFTPClient {
	private Properties configs = null;
	public GmdnSFTPClient (Properties configs) {
		this.configs = configs;
	}
	public boolean downloadFile(String ... filesToDownload) throws FileNotFoundException, IOException {
		String serverAddress = configs.getProperty("host").trim();
		String port = configs.getProperty("port").trim();
		String userId = configs.getProperty("username").trim();
		String password = configs.getProperty("password").trim();
		String remoteDirectory = configs.getProperty("remoteDirectory").trim();
		String localDirectory = configs.getProperty("localDirectory").trim();
		StandardFileSystemManager manager = new StandardFileSystemManager();
		try {

			// Initializes the file manager
			manager.init();
			// Setup our SFTP configuration
			FileSystemOptions opts = new FileSystemOptions();
			SftpFileSystemConfigBuilder.getInstance().setStrictHostKeyChecking(opts, "no");
			SftpFileSystemConfigBuilder.getInstance().setUserDirIsRoot(opts, true);
			SftpFileSystemConfigBuilder.getInstance().setTimeout(opts, 10000);

			for (String fileName : filesToDownload) {
				// Create the SFTP URI using the host name, userid, password,  remote path and file name
				String sftpUri = "sftp://" + userId + ":" + password +  "@" + serverAddress + ":" + port + "/" + 
						remoteDirectory + "/" + fileName;

				// Create local file object
				String filepath = localDirectory + "/" +  fileName;
				File file = new File(filepath);
				FileObject localFile = manager.resolveFile(file.getAbsolutePath());

				// Create remote file object
				FileObject remoteFile = manager.resolveFile(sftpUri, opts);

				// Copy local file to sftp server
				localFile.copyFrom(remoteFile, Selectors.SELECT_SELF);
				System.out.println("File download successful:" + fileName);
			}
			return true;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error encountered when downloading files", e);
		}
		finally {
			manager.close();
		}
	}
}
