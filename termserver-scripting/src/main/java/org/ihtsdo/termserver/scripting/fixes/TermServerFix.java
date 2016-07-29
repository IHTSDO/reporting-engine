package org.ihtsdo.termserver.scripting.fixes;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.ihtsdo.termserver.scripting.client.SCAClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

import us.monoid.web.Resty;

public abstract class TermServerFix implements RF2Constants {
	
	static boolean debug = true;
	static boolean dryRun = true;
	static int dryRunCounter = 0;
	static int taskThrottle = 30;
	static int conceptThrottle = 20;
	protected String env;
	protected String url = environments[0];
	protected SnowOwlClient tsClient;
	protected SCAClient scaClient;
	protected String authenticatedCookie;
	protected Resty resty = new Resty();
	protected String project;
	public static final int maxFailures = 5;
	protected int restartPosition = NOT_SET;
	private static Date startTime;
	private static Map<String, Object> summaryDetails = new HashMap<String, Object>();
	private static String summaryText = "";
	File reportFile;
	File outputDir;
	
	Scanner STDIN = new Scanner(System.in);
	
	public static String CONCEPTS_IN_FILE = "Concepts in file";
	public static String CONCEPTS_PROCESSED = "Concepts processed";
	public static String REPORTED_NOT_PROCESSED = "Reported not processed";
	public static String CRITICAL_ISSUE = "CRITICAL ISSUE";

	public abstract String getFixName();
	
	public String getAuthenticatedCookie() {
		return authenticatedCookie;
	}
	
	public static int getNextDryRunNum() {
		return ++dryRunCounter;
	}

	public void setAuthenticatedCookie(String authenticatedCookie) {
		this.authenticatedCookie = authenticatedCookie;
	}
	
	private static String[] envKeys = new String[] {"local","dev","uat", "prod"};

	private static String[] environments = new String[] {	"http://localhost:8080/",
															"https://dev-term.ihtsdotools.org/",
															"https://uat-term.ihtsdotools.org/",
															"https://term.ihtsdotools.org/",
	};
	
	public static void println (String msg) {
		System.out.println (msg);
	}
	
	public static void print (String msg) {
		System.out.print (msg);
	}
	
	public static void debug (String msg) {
		System.out.println (msg);
	}
	
	public static void warn (String msg) {
		System.out.println ("*** " + msg);
	}
	
	public static String getMessage (Exception e) {
		String msg = e.getMessage();
		Throwable cause = e.getCause();
		if (cause != null) {
			msg += " caused by " + cause.getMessage();
		}
		return msg;
	}
	
	protected void init(String[] args) throws TermServerFixException, IOException {
		
		if (args.length < 3) {
			println("Usage: java <FixClass> [-a author] [-b <batchSize>] [-r <restart lineNum>] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] <batch file Location>");
			println(" d - dry run");
			System.exit(-1);
		}
		boolean isProjectName = false;
		boolean isCookie = false;
		boolean isDryRun = false;
		boolean isRestart = false;
		boolean isTaskThrottle = false;
		boolean isConceptThrottle = false;
		boolean isOutputDir = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-p")) {
				isProjectName = true;
			} else if (thisArg.equals("-c")) {
				isCookie = true;
			} else if (thisArg.equals("-d")) {
				isDryRun = true;
			} else if (thisArg.equals("-o")) {
				isOutputDir = true;
			} else if (thisArg.equals("-r")) {
				isRestart = true;
			} else if (thisArg.equals("-t") || thisArg.equals("-t1")) {
				isTaskThrottle = true;
			} else if (thisArg.equals("-t2")) {
				isConceptThrottle = true;
			} else if (isProjectName) {
				project = thisArg;
				isProjectName = false;
			} else if (isDryRun) {
				dryRun = thisArg.toUpperCase().equals("Y");
				isDryRun = false;
			} else if (isRestart) {
				restartPosition = Integer.parseInt(thisArg);
				isRestart = false;
			} else if (isTaskThrottle) {
				taskThrottle = Integer.parseInt(thisArg);
				isTaskThrottle = false;
			} else if (isConceptThrottle) {
				conceptThrottle = Integer.parseInt(thisArg);
				isConceptThrottle = false;
			} else if (isCookie) {
				authenticatedCookie = thisArg;
				isCookie = false;
			} else if (isOutputDir) {
				File possibleDir = new File(thisArg);
				if (possibleDir.exists() && possibleDir.isDirectory() && possibleDir.canRead()) {
					outputDir = possibleDir;
				} else {
					println ("Unable to use directory " + possibleDir.getAbsolutePath() + " for output.");
				}
				isOutputDir = false;
			} 
		}		
		
		println ("Select an environment ");
		for (int i=0; i < environments.length; i++) {
			println ("  " + i + ": " + environments[i]);
		}
		
		print ("Choice: ");
		String choice = STDIN.nextLine().trim();
		int envChoice = Integer.parseInt(choice);
		url = environments[envChoice];
		env = envKeys[envChoice];
	
		initialiseSnowOwlClient();
		if (authenticatedCookie == null) {
			print ("Please enter your authenticated cookie for connection to " + url + " : ");
			authenticatedCookie = STDIN.nextLine().trim();
		}
		//TODO Make calls through client objects rather than resty direct and remove this member 
		resty.withHeader("Cookie", authenticatedCookie);  
		scaClient = new SCAClient(url, authenticatedCookie);
		
		print ("Specify Project " + (project==null?": ":"[" + project + "]: "));
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			project = response;
		}
		
		if (restartPosition != NOT_SET) {
			print ("Restarting from line [" +restartPosition + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				restartPosition = Integer.parseInt(response);
			}
		}
		
		if (taskThrottle > 0) {
			print ("Time delay between tasks (throttle) seconds [" +taskThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				taskThrottle = Integer.parseInt(response);
			}
		}
		
		if (conceptThrottle > 0) {
			print ("Time delay between concepts (throttle) seconds [" +conceptThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				conceptThrottle = Integer.parseInt(response);
			}
		}

		if (restartPosition == 0) {
			println ("Restart position given as 0 but line numbering starts from 1.  Starting at line 1.");
			restartPosition = 1;
		}
	}
	
	protected void initialiseSnowOwlClient() {
		tsClient = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");
	}

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}
	
	public void startTimer() {
		startTime = new Date();
	}
	
	public void addSummaryInformation(String item, Object detail) {
		summaryDetails.put(item, detail);
	}
	
	public void incrementSummaryInformation(String key, int incrementAmount) {
		if (!summaryDetails.containsKey(key)) {
			summaryDetails.put (key, new Integer(0));
		}
		int newValue = ((Integer)summaryDetails.get(key)).intValue() + incrementAmount;
		summaryDetails.put(key, newValue);
	}
	
	public void storeRemainder(String start, String remove1, String remove2, String storeAs) {
		Collection<?> differences = new ArrayList((Collection<?>)summaryDetails.get(start));
		Collection<?> removeList = (Collection<?>)summaryDetails.get(remove1);
		differences.removeAll(removeList);
		if (remove2 != null && !remove2.isEmpty()) {
			removeList = (Collection<?>)summaryDetails.get(remove2);
			differences.removeAll(removeList);
		}
		summaryDetails.put(storeAs, differences.toString());
	}
	
	public void finish() {
		println ("===========================================");
		Date endTime = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		//I've not had to adjust for timezones when creating a date before?
		if (startTime != null) {
			Date diff = new Date(endTime.getTime() - startTime.getTime() + (endTime.getTimezoneOffset() * 60 * 1000));
			recordSummaryText ("Completed processing in " + sdf.format(diff));
			recordSummaryText ("Started at: " + startTime);
		}
		recordSummaryText ("Finished at: " + endTime);
		List<String> criticalIssues = new ArrayList<String>();
		
		for (Map.Entry<String, Object> summaryDetail : summaryDetails.entrySet()) {
			String key = summaryDetail.getKey();
			Object value = summaryDetail.getValue();
			String display = "";
			if (value instanceof Collection) {
				display += ((Collection<?>)value).size();
			} else if (key.startsWith(CRITICAL_ISSUE)) {
				criticalIssues.add(key + ": " + value.toString());
				continue;
			} else {
				display = value.toString();
			}
			recordSummaryText (key + ": " + display);
		}
		if (summaryDetails.containsKey("Tasks created") && summaryDetails.containsKey(CONCEPTS_PROCESSED) ) {
			double c = (double)((Collection<?>)summaryDetails.get("Concepts processed")).size();
			double t = (double)((Integer)summaryDetails.get("Tasks created")).intValue();
			double avg = Math.round((c/t) * 10) / 10.0;
			recordSummaryText ("Concepts per task: " + avg);
		}
		
		if (criticalIssues.size() > 0) {
			recordSummaryText ("\nCritical Issues Encountered\n========================");
			for (String thisCriticalIssue : criticalIssues) {
				recordSummaryText(thisCriticalIssue);
			}
		}
		
	}
	
	private synchronized void recordSummaryText(String msg) {
		println (msg);
		msg = msg.replace("\n", "\n</br>");
		summaryText += msg + "\n<br/>";
	}
	
	public static String getSummaryText() {
		return summaryText;
	}
	
	
	protected void writeToFile(String line) {
		try(FileWriter fw = new FileWriter(reportFile, true);
				BufferedWriter bw = new BufferedWriter(fw);
				PrintWriter out = new PrintWriter(bw))
		{
			out.println(line);
		} catch (Exception e) {
			print ("Unable to output report line: " + line + " due to " + e.getMessage());
		}
	}

}
