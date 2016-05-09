package org.ihtsdo.termserver.scripting.fixes;

import java.util.Scanner;

import org.ihtsdo.termserver.scripting.client.SnowOwlClient;

import us.monoid.web.Resty;

public abstract class TermServerFix {
	
	static boolean debug = true;
	static boolean dryRun = true;
	protected String url = environments[0];
	protected SnowOwlClient client;
	protected Resty resty = new Resty();
	protected String project;
	
	private static String[] environments = new String[] {	"http://localhost:8080/",
															"https://dev-term.ihtsdotools.org",
															"https://uat-term.ihtsdotools.org",
															"https://term.ihtsdotools.org",
	};
	
	public static void print (String msg) {
		System.out.println (msg);
	}
	
	public static void debug (String msg) {
		System.out.println (msg);
	}
	
	public void init() {
		print ("Select an environment: ");
		for (int i=0; i < environments.length; i++) {
			print ("  " + i + ": " + environments[i]);
		}
		try (Scanner in = new Scanner(System.in)) {
			String choice = in.nextLine().trim();
			url = environments[Integer.parseInt(choice)];
		
			client = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");
			print ("Please enter your authenticated cookie for connection to " + url);
			String cookie = in.nextLine().trim();
			resty.withHeader("Cookie", cookie);
			
			print ("Specify Project: " + project==null?"":"[" + project + "]");
			String response = in.nextLine().trim();
			if (!response.isEmpty()) {
				project = response;
			}
		}
	}

}
