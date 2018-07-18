package org.ihtsdo.termserver.scripting.dao;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.*;

import java.io.*;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

public class ReportSheetManager implements RF2Constants {

	private static final String ANY_RANGE = "A:Z";
	private static final String RAW = "RAW";
	private static final String APPLICATION_NAME = "SI Reporting Engine";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final String CLIENT_SECRET_DIR = "secure/google-api-secret.json";

	Credential credential;
	ReportManager owner;
	Sheets sheetsService;
	Drive driveService;
	Spreadsheet sheet;
	static public String targetFolderId = "1bIRADym0omCgbD7064U-D24XGqAEg3gt";  //Fallback location
	
	Date lastWriteTime;
	List<ValueRange> dataToBeWritten = new ArrayList<>();
	SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
	
	public ReportSheetManager(ReportManager owner) {
		this.owner = owner;	}

	/**
	 * Creates an authorized Credential object.
	 * @param HTTP_TRANSPORT The network HTTP Transport.
	 * @return An authorized Credential object.
	 * @throws IOException If there is no client_secret.
	 */
	private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
		if (credential == null) {
			String dir = System.getProperty("user.dir");
			File secret = new File (dir + File.separator + CLIENT_SECRET_DIR);
			System.out.println("Looking for client secret file " + secret);
			//return GoogleCredential.fromStream(new FileInputStream(secret)).createScoped(SCOPES);
			credential = GoogleCredential.fromStream(new FileInputStream(secret)).createScoped(SheetsScopes.all());
		}
		return credential;
	}

	public static void main(String... args) throws Exception {
		ReportManager rm = new ReportManager();
		rm.setTabNames(new String[] {"first tab", "second tab", "third tab"});
		ReportSheetManager rsm = new ReportSheetManager(rm);
		rsm.initialiseReportFiles(new String[] { "foo, bar" , "bar, boo", "tim ,tum"});
	}
	
	private void init() {
		try {
			final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			if (sheetsService == null) {
				sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
						.setApplicationName(APPLICATION_NAME)
						.build();
			}
			if (driveService == null) {
				driveService = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
						.setApplicationName(APPLICATION_NAME)
						.build();
			}
			Spreadsheet requestBody = new Spreadsheet();
			Sheets.Spreadsheets.Create request = sheetsService.spreadsheets().create(requestBody);
			sheet = request.execute();
			System.out.println("Created: " + sheet.getSpreadsheetUrl());
			
			//And share it with everyone in the company
			Permission perm = new Permission()
				.setKind("drive#permission")
				.setRole("writer")
				.setType("domain")
				.setDomain("ihtsdo.org");
			driveService.permissions()
				.create(sheet.getSpreadsheetId(), perm)
				.setSupportsTeamDrives(true)
				.execute();
			System.out.println("Spreadsheet shared with the domain");
		} catch (IOException | GeneralSecurityException e) {
			throw new IllegalStateException("Unable to initialise Google Sheets connection",e);
		}
		
	}

	public void initialiseReportFiles(String[] columnHeaders) throws TermServerScriptException {
		if (sheet == null) {
			init();
		}
		
		try {
			List<Request> requests = new ArrayList<>();
			requests.add(new Request()
					.setUpdateSpreadsheetProperties(new UpdateSpreadsheetPropertiesRequest()
							.setProperties(new SpreadsheetProperties()
									.setTitle("PGW Test Title " + df.format(new Date())))
									.setFields("title")));
			int tabIdx = 0;
			for (String header : columnHeaders) {
				Request request;
				//Sheet 0 already exists, just update
				if (tabIdx == 0) {
					SheetProperties properties = new SheetProperties().setTitle(owner.getTabNames().get(tabIdx));
					request = new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest().setProperties(properties).setFields("title"));
				} else {
					SheetProperties properties = new SheetProperties().setTitle(owner.getTabNames().get(tabIdx)).setSheetId(new Integer(tabIdx));
					request = new Request().setAddSheet(new AddSheetRequest().setProperties(properties));
				}
				requests.add(request);
				writeToReportFile(tabIdx, header.replace(COMMA, TAB), true);
				tabIdx++;
			}
			
			//Execute creation of tabs
			BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest();
			batch.setRequests(requests);
			BatchUpdateSpreadsheetResponse responses = sheetsService.spreadsheets().batchUpdate(sheet.getSpreadsheetId(), batch).execute();
			for (Response response : responses.getReplies()) {
				System.out.println(response);
			}
			flush();
			moveFile(sheet.getSpreadsheetId());
		} catch (Exception e) {
			throw new TermServerScriptException ("Unable to initialise Google Sheet headers",e);
		}
	}

	public void writeToReportFile(int tabIdx, String line, boolean delayWrite) throws IOException {
		if (lastWriteTime == null) {
			lastWriteTime = new Date();
		}
		List<List<Object>> cells = Arrays.asList(Arrays.asList(line.split(TAB)));
		dataToBeWritten.add(new ValueRange()
					.setRange( "'" + owner.getTabNames().get(tabIdx) + "'!" + ANY_RANGE)
					.setValues(cells));
		
		if (!delayWrite) {
			//How long is it since we last wrote to the file?  Write every 5 seconds
			long secondsSinceLastWrite = (new Date().getTime()-lastWriteTime.getTime())/1000;
			if (secondsSinceLastWrite > 5) {
				flush();
			}
		}
	}
	
	public void flush() throws IOException {
		//Execute update of data values
		BatchUpdateValuesRequest body = new BatchUpdateValuesRequest()
			.setValueInputOption(RAW)
			.setData(dataToBeWritten);
		sheetsService.spreadsheets().values().batchUpdate(sheet.getSpreadsheetId(),body)
		.execute();
		lastWriteTime = new Date();
		dataToBeWritten.clear();
	}
	
	public void moveFile(String fileId) throws IOException {
		// Retrieve the existing parents to remove
		com.google.api.services.drive.model.File file = driveService.files().get(fileId)
				.setFields("parents")
				.setSupportsTeamDrives(true)
				.execute();
		StringBuilder previousParents = new StringBuilder();
		for (String parent : file.getParents()) {
			previousParents.append(parent);
			previousParents.append(',');
		}
		// Move the file to the new folder
		file = driveService.files().update(fileId, null)
			.setAddParents(targetFolderId)
			.setRemoveParents(previousParents.toString())
			.setSupportsTeamDrives(true)
			.setFields("id, parents")
			.execute();
	}
}
