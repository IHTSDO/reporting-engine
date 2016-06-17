package org.ihtsdo.termserver.scripting.fixes;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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
	static int throttle = 0;
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
	
	public void init() {
		println ("Select an environment ");
		for (int i=0; i < environments.length; i++) {
			println ("  " + i + ": " + environments[i]);
		}
		try (Scanner in = new Scanner(System.in)) {
			print ("Choice: ");
			String choice = in.nextLine().trim();
			url = environments[Integer.parseInt(choice)];
		
			tsClient = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");
			if (authenticatedCookie == null) {
				print ("Please enter your authenticated cookie for connection to " + url + " : ");
				authenticatedCookie = in.nextLine().trim();
			}
			//TODO Make calls through client objects rather than resty direct and remove this member 
			resty.withHeader("Cookie", authenticatedCookie);  
			scaClient = new SCAClient(url, authenticatedCookie);
			
			print ("Specify Project " + (project==null?": ":"[" + project + "]: "));
			String response = in.nextLine().trim();
			if (!response.isEmpty()) {
				project = response;
			}
			
			if (restartPosition != NOT_SET) {
				print ("Restarting from line [" +restartPosition + "]: ");
				response = in.nextLine().trim();
				if (!response.isEmpty()) {
					restartPosition = Integer.parseInt(response);
				}
			}
			
			if (throttle > 0) {
				print ("Time delay between tasks (throttle) seconds [" +throttle + "]: ");
				response = in.nextLine().trim();
				if (!response.isEmpty()) {
					throttle = Integer.parseInt(response);
				}
			}

		}
		if (restartPosition == 0) {
			println ("Restart position given as 0 but line numbering starts from 1.  Starting at line 1.");
			restartPosition = 1;
		}
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
	
	public void finish() {
		println ("===========================================");
		Date endTime = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		//I've not had to adjust for timezones when creating a date before?
		Date diff = new Date(endTime.getTime() - startTime.getTime() + (endTime.getTimezoneOffset() * 60 * 1000));
		println ("Completed processing in " + sdf.format(diff));
		println ("Started at: " + startTime);
		println ("Finished at: " + endTime);
		
		for (Map.Entry<String, Object> summaryDetail : summaryDetails.entrySet()) {
			println (summaryDetail.getKey() + ": " + summaryDetail.getValue().toString());
		}
		if (summaryDetails.containsKey("Tasks created") && summaryDetails.containsKey("Concepts processed") ) {
			double c = (double)((Integer)summaryDetails.get("Concepts processed")).intValue();
			double t = (double)((Integer)summaryDetails.get("Tasks created")).intValue();
			double avg = Math.round((c/t) * 10) / 10.0;
			println ("Concepts per task: " + avg);
		}
		
	}

}
