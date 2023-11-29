package org.ihtsdo.termserver.scripting.reports.nuva;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.github.jsonldjava.shaded.com.google.common.collect.Streams;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NuvaScript extends TermServerScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(NuvaScript.class);

	protected static int FILE_IDX_NUVA_DATA_RDF = 0;
	protected static int FILE_IDX_NUVA_METADATA_RDF = 1;
	protected static final String NUVA_NS= "http://data.esante.gouv.fr/NUVA#";

	public final String TAB_SUMMARY = "Summary";
	public final String TAB_VACCINES = "Vaccines";
	public final String TAB_VALENCES = "Valences";
	public final String TAB_NUVA_DATA = "NUVA Data";
	public final String TAB_NUVA_METADATA = "NUVA MetaData";

	private Model dataModel;
	private Model metaModel;

	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_VACCINES,
			TAB_VALENCES,
			TAB_NUVA_DATA,
			TAB_NUVA_METADATA};

	public enum NuvaUri {
		ABSTRACT("http://data.esante.gouv.fr/NUVA/nuvs#isAbstract"),
		ID("http://www.w3.org/2004/02/skos/core#notation"),
		CODE("http://www.w3.org/2000/01/rdf-schema#label"),
		TYPE("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
		CLASS("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
		COMMENT("http://www.w3.org/2000/01/rdf-schema#comment"),
		CREATED("http://purl.org/dc/terms/created"),
		LABEL("http://www.w3.org/2000/01/rdf-schema#label"),
		HIDDEN_LABEL("http://www.w3.org/2004/02/skos/core#hiddenLabel"),
		MODIFIED("http://purl.org/dc/terms/modified"),
		MATCH("http://www.w3.org/2004/02/skos/core#exactMatch"),
		VALENCE("http://data.esante.gouv.fr/NUVA/nuvs#containsValence"),
		PREVENTS("http://data.esante.gouv.fr/NUVA/nuvs#prevents");
		public final String value;

		NuvaUri(String uri) {
			this.value = uri;
		}
	}

	public enum NuvaClass {
		VACCINE("Vaccine"),
		VALENCE("Valence");
		
		public final String value;

		NuvaClass(String className) {
			this.value = className;
		}
	}

	public boolean isPredicate(Statement stmt, NuvaUri uri) {
		return stmt.getPredicate().hasURI(uri.value);
	}

	public boolean isObject(Statement stmt, Object obj) {
		return getObject(stmt).equalsIgnoreCase(obj.toString());
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		NuvaScript report = new NuvaScript();
		try {
			report.summaryTabIdx = PRIMARY_REPORT;
			report.runStandAlone = false;
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.init(args);
			//report.loadProjectSnapshot(false);
			report.postInit();
			report.runReport();
		} finally {
			report.finish();
		}
	}

	protected String[] getTabNames() {
		return tabNames;
	}

	public int getTab(String tabName) throws TermServerScriptException {
		String[] tabNames = getTabNames();
		for (int i = 0; i < tabNames.length; i++) {
			if (tabNames[i].equals(tabName)) {
				return i;
			}
		}
		throw new TermServerScriptException("Tab '" + tabName + "' not recognised");
	}

	public void postInit() throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "19OR1N_vtMb0kUi2YyNo6jqT3DiFRfbPO";  //NUVA
		String[] columnHeadings = new String[] {
				"Summary Item, Count",
				"Code, Abstract, Translations, Valences, Exact Matches, HiddenLabels, Created",
				"Code, Label, Treat Diseases, Created",
				"Code, Type, Class, Translations, Additional Properties",
				"Code, Type, Class, Translations, Additional Properties"
		};

		super.postInit(tabNames, columnHeadings, false);
	}

	private void runReport() throws TermServerScriptException, InterruptedException {
		dataModel = loadNuva(FILE_IDX_NUVA_DATA_RDF);
		metaModel = loadNuva(FILE_IDX_NUVA_METADATA_RDF);
		exploreModel(dataModel, TAB_NUVA_DATA, "Data-");
		exploreModel(metaModel, TAB_NUVA_METADATA, "Meta-");
	}

	private void exploreModel(Model model, String tabName, String prefix) throws TermServerScriptException {
		//Let's run through the model and report what we find.
		reportSection(getTab(tabName), "NameSpaces");
		NsIterator nsIterator = model.listNameSpaces();
		while (nsIterator.hasNext()) {
			report(getTab(tabName), nsIterator.next());
			incrementSummaryInformation(prefix + "Namespaces");
		}

		reportSection(getTab(tabName), "Subjects");
		ResIterator subIterator = model.listSubjects();
		while (subIterator.hasNext()) {
			Resource subject = subIterator.next();
			//Get the class property and we'll report the Vaccines on their own tab
			Statement classStmt = subject.getProperty(model.getProperty(NuvaUri.CLASS.value));
			if (classStmt != null && isObject(classStmt, NuvaClass.VACCINE)) {
				reportVaccine(subject);
				continue;
			} else if (classStmt != null && isObject(classStmt, NuvaClass.VALENCE)) {
				reportValence(subject);
				continue;
			}
			StmtIterator stmtIterator = subject.listProperties();
			String objClass = "Unknown";
			String type = "Unknown";
			int translationCount = 0;
			while (stmtIterator.hasNext()) {
				Statement stmt = stmtIterator.next();
				if (isPredicate(stmt, NuvaUri.ID) || isPredicate(stmt, NuvaUri.CODE)) {
					//We can skip this, subject already listed
				} else if (isPredicate(stmt, NuvaUri.CLASS)) {
					objClass = getObject(stmt);
				} else if (isPredicate(stmt, NuvaUri.TYPE)) {
					type = getObject(stmt);
					incrementSummaryInformation(prefix + "type - " + type);
				} else if (isPredicate(stmt, NuvaUri.COMMENT)) {
					//TODO Check for @<lang>
					translationCount++;
				}
			}
			String additionalProperties = Streams.stream(subject.listProperties())
					.filter(p -> !hasKnownPredicate(p))
					.map(p -> toString(p))
					.collect(Collectors.joining(",\n"));
			incrementSummaryInformation(prefix + "Subjects");
			report(getTab(tabName), subject.toString().replace(NUVA_NS, ""), type, objClass, translationCount, additionalProperties);
		}

	}

	private void reportVaccine(Resource subject) throws TermServerScriptException {
		StmtIterator stmtIterator = subject.listProperties();
		int translationCount = 0;
		String created = "";
		String isAbstract = "";
		List<String> valences = new ArrayList<>();
		List<String> matches = new ArrayList<>();
		List<String> hiddenLabels = new ArrayList<>();
		while (stmtIterator.hasNext()) {
			Statement stmt = stmtIterator.next();
			if (isPredicate(stmt, NuvaUri.COMMENT)) {
				//TODO Check for @<lang>
				translationCount++;
			} else if (isPredicate(stmt ,NuvaUri.VALENCE)) {
				valences.add(getObject(stmt));
			} else if (isPredicate(stmt ,NuvaUri.MATCH)) {
				matches.add(getObject(stmt));
			} else if (isPredicate(stmt ,NuvaUri.HIDDEN_LABEL)) {
				hiddenLabels.add(getObject(stmt));
			} else if (isPredicate(stmt ,NuvaUri.CREATED)) {
				created = getObject(stmt);
			} else if (isPredicate(stmt ,NuvaUri.ABSTRACT)) {
				isAbstract = getObject(stmt).equals("true")?"Y":"N";
			}
		}
		report(getTab(TAB_VACCINES), subject.toString().replace(NUVA_NS, ""), isAbstract, translationCount, strList(valences), strList(matches), strList(hiddenLabels), created);
	}
	
	private void reportValence(Resource subject) throws TermServerScriptException {
		StmtIterator stmtIterator = subject.listProperties();
		String label = "";
		List<String> prevents = new ArrayList<>();
		String created = "";
		while (stmtIterator.hasNext()) {
			Statement stmt = stmtIterator.next();
			if (isPredicate(stmt ,NuvaUri.LABEL)) {
				label = getObject(stmt);
			} else if (isPredicate(stmt ,NuvaUri.PREVENTS)) {
				prevents.add(getObject(stmt));
			} else if (isPredicate(stmt ,NuvaUri.CREATED)) {
				created = getObject(stmt);
			} 
		}
		report(getTab(TAB_VALENCES), subject.toString().replace(NUVA_NS, ""), label, strList(prevents), created);
	}

	private String strList(List<String> list) {
		return list.stream()
				.collect(Collectors.joining("\n"));
	}

	private boolean hasKnownPredicate(Statement p) {
		for (NuvaUri knownPredicateUri : NuvaUri.values()) {
			if (isPredicate(p, knownPredicateUri)) {
				return true;
			}
		}
		return false;
	}
	
	private String toString(Statement stmt) {
		return getPredicate(stmt) + " --> " + getObject(stmt);
	}

	private String getObject(Statement stmt) {
		if (stmt.getObject().isLiteral()) {
			try {
				return stmt.getObject().asLiteral().getValue().toString();
			} catch (Exception e) {
				LOGGER.warn(e.getMessage() + ": " + stmt);
				String str = stmt.getObject().toString();
				int cut = str.indexOf("^^");
				return str.substring(0,cut);
			}
		} else {
			String str = stmt.getObject().toString();
			int cut = str.indexOf("#") + 1;
			return str.substring(cut);
		}
	}
	
	private String getPredicate(Statement stmt) {
		String str = stmt.getPredicate().toString();
		int cut = str.indexOf("#") + 1;
		return str.substring(cut);
	}

	private void reportSection(int tabIdx, String msg) throws TermServerScriptException {
		report(tabIdx, "");
		report(tabIdx, "*** " + msg + " ***");
		report(tabIdx, "");
	}

	protected Model loadNuva(int fileIdx) {
		File inputFile = getInputFile(fileIdx);
		LOGGER.info ("Loading NUVA file: " + inputFile);
		// create an empty model
		Model model = ModelFactory.createDefaultModel();

		// use the RDFDataMgr to find the input file
		InputStream in = RDFDataMgr.open(inputFile.getAbsolutePath());
		if (in == null) {
			throw new IllegalArgumentException("File: " + inputFile + " not found");
		}

		// read the RDF/XML file
		model.read(in, null);
		return model;
	}
}


