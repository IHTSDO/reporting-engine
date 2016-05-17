package org.ihtsdo.termserver.scripting.fixes;

import java.util.Scanner;

import org.ihtsdo.termserver.scripting.client.SCAClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient;

import us.monoid.web.Resty;

public abstract class TermServerFix {
	
	static boolean debug = true;
	static boolean dryRun = true;
	protected String url = environments[0];
	protected SnowOwlClient tsClient;
	protected SCAClient scaClient;
	protected String authenticatedCookie;

	public abstract String getFixName();
	
	public String getAuthenticatedCookie() {
		return authenticatedCookie;
	}

	public void setAuthenticatedCookie(String authenticatedCookie) {
		this.authenticatedCookie = authenticatedCookie;
	}

	protected Resty resty = new Resty();
	protected String project;
	
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
		}
	}
	
	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}

}
