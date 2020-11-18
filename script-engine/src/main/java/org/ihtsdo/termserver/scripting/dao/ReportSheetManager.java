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
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.util.StringUtils;

public class ReportSheetManager implements RF2Constants, ReportProcessor {

	private static final String DOMAIN = "ihtsdo.org";
	private static final String RAW = "RAW";
	private static final String APPLICATION_NAME = "SI Reporting Engine";
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	private static final String CLIENT_SECRET_DIR = "secure/google-api-secret.json";
	private static final int MAX_REQUEST_RATE = 10;
	private static final int MAX_WRITE_ATTEMPTS = 3;
	
	Map<Integer, Integer> tabRowsCount;
	Map<Integer, Integer> maxTabColumns;
	private static int MAX_CELLS = 5000000 - 5000;
	private static int MAX_ROW_INCREMENT = 10000;
	int currentCellCount = 0;

	Credential credential;
	ReportManager owner;
	Sheets sheetsService;
	Drive driveService;
	Spreadsheet sheet;
	static public String targetFolderId = "1bIRADym0omCgbD7064U-D24XGqAEg3gt";  //Fallback location
	
	Date lastWriteTime;
	List<ValueRange> dataToBeWritten = new ArrayList<>();
	SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
	Map<Integer, Integer> tabLineCount;
	Map<Integer, Integer> linesWrittenPerTab = new HashMap<>();
	int numberOfSheets = 0;

	public ReportSheetManager(ReportManager owner) {
		this.owner = owner;
	}

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
			System.out.print("Looking for client secret file " + secret + "...");
			if (!secret.canRead()) {
				throw new IllegalStateException("Unable to read " + secret);
			}
			//return GoogleCredential.fromStream(new FileInputStream(secret)).createScoped(SCOPES);
			credential = GoogleCredential.fromStream(new FileInputStream(secret)).createScoped(SheetsScopes.all());
			System.out.println ("found.");
		}
		return credential;
	}

	public static void main(String... args) throws Exception {
		ReportManager rm = ReportManager.create(null);
		rm.setTabNames(new String[] {"first tab", "second tab", "third tab"});
		ReportProcessor rsm = new ReportSheetManager(rm);
		rsm.initialiseReportFiles(new String[] { "foo, bar" , "bar, boo", "tim ,tum"});
	}
	
	private void init() throws TermServerScriptException {
		try {
			//Are we re-intialising?  Flush last data if so
			if (sheet != null) {
				flush();
			}
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
			int attempt = 0;
			while (sheet == null) {
				try {
					sheet = request.execute();
				} catch (Exception e) {
					System.out.println("Failed to initialise sheet due to " + e.getMessage());
					if (++attempt < 3 ) {
						System.out.println("Retrying..." + attempt);
						try {
							Thread.sleep(5 * 1000);
						} catch (Exception i) {}
					} else {
						throw e;
					}
				}
			}
			System.out.println("Created: " + sheet.getSpreadsheetUrl());
			
			//And share it with everyone everywhere
			//See https://developers.google.com/drive/api/v2/reference/permissions/insert
			Permission perm = new Permission()
				.setKind("drive#permission")
				.setRole("writer")
				.setType("anyone");
			driveService.permissions()
				.create(sheet.getSpreadsheetId(), perm)
				.setSupportsTeamDrives(true)
				.execute();
			System.out.println("Spreadsheet shared");
		} catch (IOException | GeneralSecurityException e) {
			throw new IllegalStateException("Unable to initialise Google Sheets connection",e);
		}
		
	}

	@Override
	public void initialiseReportFiles(String[] columnHeaders) throws TermServerScriptException {
		tabLineCount = new HashMap<>();
		init();
		try {
			List<Request> requests = new ArrayList<>();
			String titleStr = owner.getScript().getReportName() + " " + df.format(new Date()) + "_" + owner.getEnv();
			if (owner.getScript().getJobRun() != null && owner.getScript().getJobRun().getUser() != null) {
				titleStr += "_" + owner.getScript().getJobRun().getUser().toLowerCase();
			}
			requests.add(new Request()
					.setUpdateSpreadsheetProperties(new UpdateSpreadsheetPropertiesRequest()
							.setProperties(new SpreadsheetProperties()
									.setTitle(titleStr))
									.setFields("title")));
			
			tabRowsCount = new HashMap<>();
			populateColumnsPerTab(columnHeaders);
			int tabIdx = 0;
			for (String header : columnHeaders) {
				Request request = null;
				//Calculate the number of columns from the header, 
				//divide the total max cells by both columns and tab count
				int maxColumns = header.split(COMMA).length;
				int maxRows = MAX_ROW_INCREMENT ;
				tabRowsCount.put(tabIdx, maxRows);
				currentCellCount += maxColumns * maxRows;
				TermServerScript.debug("Tab " + tabIdx + " rows/cols set to " + maxRows + "/" + maxColumns);
				SheetProperties properties = new SheetProperties()
						.setTitle(owner.getTabNames().get(tabIdx))
						.setGridProperties(new GridProperties().setRowCount(maxRows).setColumnCount(maxColumns));
				
				//Sheet 0 already exists, just update - it it's been specified
				if (tabIdx == 0) {
					request = new Request().setUpdateSheetProperties(new UpdateSheetPropertiesRequest().setProperties(properties).setFields("title, gridProperties"));
				} else {
					properties.setSheetId(new Integer(tabIdx));
					request = new Request().setAddSheet(new AddSheetRequest().setProperties(properties));
				}
				requests.add(request);
				writeToReportFile(tabIdx, header, true);
				tabIdx++;
				numberOfSheets++;
			}
			
			executeRequests(requests);

			flush();
			moveFile(sheet.getSpreadsheetId());
		} catch (Exception e) {
			throw new TermServerScriptException ("Unable to initialise Google Sheet headers",e);
		}
	}

	private void executeRequests(List<Request> requests) throws IOException, InterruptedException {
		//Execute creation of tabs
		BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest();
		batch.setRequests(requests);
		int retry = 0;
		boolean createdOK = false;
		while (!createdOK && retry < 3) {
			try {
				BatchUpdateSpreadsheetResponse responses = sheetsService.spreadsheets().batchUpdate(sheet.getSpreadsheetId(), batch).execute();
				createdOK = true;
			} catch (SocketTimeoutException e) {
				if (++retry < 3) {
					System.err.println("Timeout received from Google. Retrying after short nap.");
					Thread.sleep(1000 * 10);
				}
			}
		}
	}

	private int populateColumnsPerTab(String[] columnHeaders) {
		maxTabColumns = new HashMap<>();
		int totalColumns = 0;
		for (int tabIdx = 0; tabIdx < columnHeaders.length; tabIdx++) {
			int tabColumns = columnHeaders[tabIdx].split(COMMA).length;
			maxTabColumns.put(tabIdx, tabColumns);
			totalColumns += tabColumns;
		}
		return totalColumns;
	}

	@Override
	public boolean writeToReportFile(int tabIdx, String line, boolean delayWrite) throws TermServerScriptException {
		if (linesWrittenPerTab.get(tabIdx) != null && linesWrittenPerTab.get(tabIdx).intValue() > tabRowsCount.get(tabIdx)) {
			//We're already hit the max and will have reported failure
			//Do not attempt to add any more data.
			return false;
		}
		
		if (lastWriteTime == null) {
			lastWriteTime = new Date();
		}
		
		addDataToBWritten(tabIdx, line);
		
		//Are we getting close to the limits of what can be written?
		if (dataToBeWritten.size() > 2000) {
			System.err.println("Attempting to write > 2000 rows to sheets, pausing...");
			try { Thread.sleep(MAX_REQUEST_RATE*1000); } catch (Exception e) {}
		}
		
		//Do we have a total count for this tab?  Save 3 to allow for Data truncated message
		int maxRows = tabRowsCount.get(tabIdx);
		linesWrittenPerTab.merge(tabIdx, 1, Integer::sum);
		
		if (linesWrittenPerTab.get(tabIdx).intValue() >= maxRows) {
			//Do we have sufficient cells left to extend this tab further?
			int requestCellIncrement = maxTabColumns.get(tabIdx) * MAX_ROW_INCREMENT;
			if (currentCellCount + requestCellIncrement < MAX_CELLS) {
				currentCellCount += requestCellIncrement;
				tabRowsCount.put(tabIdx, maxRows + MAX_ROW_INCREMENT);
				TermServerScript.debug("Expanding rows in tabIdx " + tabIdx + " to " + tabRowsCount.get(tabIdx));
				expandTabRows(tabIdx);
			} else if (linesWrittenPerTab.get(tabIdx).intValue() == maxRows) {
				addDataToBWritten(tabIdx, "Data truncated at " + maxRows + " rows");
				TermServerScript.warn("Number of rows written to tab idx " + tabIdx + " hit limit of " + maxRows);
			}
			flushWithWait();
			return false;
		}
		
		if (!delayWrite) {
			flushSoft();
		}
		return true;
	}
	
	private void expandTabRows(int tabIdx) throws TermServerScriptException {
		AppendDimensionRequest dimRequest = new AppendDimensionRequest();
		dimRequest.setDimension("ROWS")
			.setSheetId(tabIdx)
			.setLength(MAX_ROW_INCREMENT);
		
		Request request = new Request();
		request.setAppendDimension(dimRequest);
		try {
			executeRequests(Collections.singletonList(request));
		} catch (InterruptedException | IOException e) {
			throw new TermServerScriptException("Unable to expand rows in tabIdx: " + tabIdx, e);
		}
		
	}

	private void addDataToBWritten(int tabIdx, String line) {
		List<Object> data = StringUtils.csvSplitAsObject(line);
		List<List<Object>> cells = Arrays.asList(data);
		//Increment the current row position so we create the correct range
		tabLineCount.merge(tabIdx, 1, Integer::sum);
		int maxColumn = maxTabColumns.get(tabIdx);
		String maxColumnStr = Character.toString((char)('A' + maxColumn));
		String range = "'" + owner.getTabNames().get(tabIdx) + "'!A" + tabLineCount.get(tabIdx) + ":" + maxColumnStr +  tabLineCount.get(tabIdx); 
		dataToBeWritten.add(new ValueRange()
					.setRange(range)
					.setValues(cells));
		
	}

	public void flushWithWait() throws TermServerScriptException {
		flush(false, true);  //Not optional, also wait
	}
	
	public void flush() throws TermServerScriptException {
		flush(false, false);  //Not optional, don't wait
	}

	public void flushSoft() throws TermServerScriptException {
		flush(true, false); //optional, don't wait
	}
	
	private void flush(boolean optional, boolean withWait) throws TermServerScriptException {
		//Are we ready to flush?
		//How long is it since we last wrote to the file?  Write every 5 seconds
		if (lastWriteTime != null) {
			long secondsSinceLastWrite = (new Date().getTime()-lastWriteTime.getTime())/1000;
			if (secondsSinceLastWrite < MAX_REQUEST_RATE) {
				if (withWait) {
					try { Thread.sleep(MAX_REQUEST_RATE - secondsSinceLastWrite); } catch (InterruptedException e) {}
				} else if (optional) {
					return;
				}
			}
		}
		
		//Execute update of data values
		if (sheet != null) {
			BatchUpdateValuesRequest body = new BatchUpdateValuesRequest()
				.setValueInputOption(RAW)
				.setData(dataToBeWritten);
			try {
				TermServerScript.info(new Date() + " flushing to sheets");
				int writeAttempts = 0;
				boolean writeSuccess = false;
				while (!writeSuccess && writeAttempts <= MAX_WRITE_ATTEMPTS) {
					try {
						sheetsService.spreadsheets().values().batchUpdate(sheet.getSpreadsheetId(),body)
						.execute();
						writeSuccess = true;
					}catch(Exception e) {
						//If we're told about an invalid argument, trying again won't improve the situation!
						if (writeAttempts <= MAX_WRITE_ATTEMPTS && !e.getMessage().contains("INVALID_ARGUMENT")) {
							try {
								TermServerScript.warn("Exception from Google Sheets, sleeping then trying again");
								Thread.sleep(30*1000);
							} catch (InterruptedException e1) {}
							TermServerScript.info(e.getMessage() + " trying again...");
						} else {
							throw (e);
						}
					}
					writeAttempts++;
				}
			} catch (IOException e) {
				throw new TermServerScriptException("Unable to update spreadsheet " + sheet.getSpreadsheetUrl(), e);
			} finally {
				lastWriteTime = new Date();
				dataToBeWritten.clear();
			}
		}
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

	public String getUrl() {
		return sheet == null ? null : sheet.getSpreadsheetUrl();
	}

	public boolean formatSpreadSheetColumns() {
		boolean writeSuccess = false;
		BatchUpdateSpreadsheetRequest batch = new BatchUpdateSpreadsheetRequest();
		List<Request> requests = new ArrayList<>();

		for (int tabIdx = 0; tabIdx < numberOfSheets; tabIdx++) {
			int maxColumn = maxTabColumns.get(tabIdx);
			// set the columns to be auto sized
			AutoResizeDimensionsRequest autoResizeDimensionsRequest = new AutoResizeDimensionsRequest();
			DimensionRange dimensionRange = new DimensionRange();
			dimensionRange.setDimension("COLUMNS");
			dimensionRange.setSheetId(tabIdx);
			dimensionRange.setStartIndex(0);
			dimensionRange.setEndIndex(maxColumn);

			autoResizeDimensionsRequest.setDimensions(dimensionRange);
			requests.add(new Request().setAutoResizeDimensions(autoResizeDimensionsRequest));
		}
		batch.setRequests(requests);

		TermServerScript.info("Formatting Goggle SpreadSheet Sheet/s (Columns to auto size).");
		int writeAttempts = 0;
		while (!writeSuccess && writeAttempts <= MAX_WRITE_ATTEMPTS) {
			try {
				sheetsService.spreadsheets().batchUpdate(sheet.getSpreadsheetId(), batch).execute();
				writeSuccess = true;
			} catch (Exception e) {
				if (writeAttempts <= MAX_WRITE_ATTEMPTS) {
					try {
						TermServerScript.warn("Exception from Google Sheets, sleeping then trying again");
						Thread.sleep(30 * 1000);
					} catch (InterruptedException e1) {
					}
					TermServerScript.info(e.getMessage() + " trying again...");
				}
			}
			writeAttempts++;
		}
		return writeSuccess;
	}
}
