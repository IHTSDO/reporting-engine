package org.ihtsdo.termserver.scripting.pipeline.nuva;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.collect.Streams;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.ihtsdo.termserver.scripting.pipeline.nuva.NuvaConcept.*;

public class NuvaOntologyLoader extends TermServerScript implements NuvaConstants {

	static {
		ReportSheetManager.targetFolderId = "19OR1N_vtMb0kUi2YyNo6jqT3DiFRfbPO";  //NUVA
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(NuvaOntologyLoader.class);

	protected static final int FILE_IDX_NUVA_DATA_RDF = 0;
	protected static final int FILE_IDX_NUVA_METADATA_RDF = 1;

	public static final String TAB_SUMMARY = "Summary";
	public static final String TAB_VACCINES = "Vaccines";
	public static final String TAB_VALENCES = "Valences";
	public static final String TAB_DISEASES = "Diseases";
	public static final String TAB_NUVA_DATA = "NUVA Data";
	public static final String TAB_NUVA_METADATA = "NUVA MetaData";

	private Model dataModel;

	protected String[] tabNames = new String[] {
			TAB_SUMMARY,
			TAB_VACCINES,
			TAB_VALENCES,
			TAB_DISEASES,
			TAB_NUVA_DATA,
			TAB_NUVA_METADATA};

	private Property subClassProperty;

	public enum NuvaUri {
		ABSTRACT("http://data.esante.gouv.fr/NUVA/nuvs#isAbstract"),
		ID("http://www.w3.org/2004/02/skos/core#notation"),
		CODE("http://www.w3.org/2000/01/rdf-schema#label"),
		TYPE("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
		SUBCLASSOF("http://www.w3.org/2000/01/rdf-schema#subClassOf"),
		COMMENT("http://www.w3.org/2000/01/rdf-schema#comment"),
		CREATED("http://purl.org/dc/terms/created"),
		DISEASE("http://data.esante.gouv.fr/NUVA#Disease"),
		LABEL("http://www.w3.org/2000/01/rdf-schema#label"),
		ALT_LABEL("http://www.w3.org/2004/02/skos/core#altLabel"),
		HIDDEN_LABEL("http://www.w3.org/2004/02/skos/core#hiddenLabel"),
		MODIFIED("http://purl.org/dc/terms/modified"),
		MATCH("http://www.w3.org/2004/02/skos/core#exactMatch"),
		VALENCE("http://data.esante.gouv.fr/NUVA/nuvs#containsValence"),
		CONTAINED_IN_VACCINE("http://data.esante.gouv.fr/NUVA/nuvs#containedInVaccine"),
		PREVENTS("http://data.esante.gouv.fr/NUVA/nuvs#prevents");
		public final String value;

		NuvaUri(String uri) {
			this.value = uri;
		}
	}

	public enum NuvaClass {
		VACCINE("Vaccine"),
		VALENCE("Valence"),
		DISEASE("Disease");

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

	public static void main(String[] args) throws TermServerScriptException {
		NuvaScript report = new NuvaScript();
		try {
			report.summaryTabIdx = PRIMARY_REPORT;
			report.runStandAlone = false;
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.init(args);
			report.loadProjectSnapshot();
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
		for (int i = 0; i <  getTabNames().length; i++) {
			if (getTabNames()[i].equals(tabName)) {
				return i;
			}
		}
		throw new TermServerScriptException("Tab '" + tabName + "' not recognised");
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("19OR1N_vtMb0kUi2YyNo6jqT3DiFRfbPO");  //NUVA
		String[] columnHeadings = new String[] {
				"Summary Item, Count",
				"Code, Is Abstract, Translation Count, Valences, Equivalent SCT, HiddenLabels, Created, Other Exact Matches",
				"Code, Label, Treats Diseases, Created",
				"Code, Translations, Created",
				"Code, Type, Class, Translations, Additional Properties",
				"Code, Type, Class, Translations, Additional Properties"
		};

		super.postInit(tabNames, columnHeadings, false);
	}

	private void runReport() throws TermServerScriptException {
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
			outputSubjectToAppropriateTab(model, subject, tabName, prefix);
		}
	}

	private void outputSubjectToAppropriateTab(Model model, Resource subject, String tabName, String prefix) throws TermServerScriptException {
		//Get the subclass property and we'll report the Vaccines on their own tab
		//Note Valences can be both a subclass of Valence and also of some specific Valence eg VAL114
		//So iterate through the subclasses.
		StmtIterator subclassIter = subject.listProperties(model.getProperty(NuvaUri.SUBCLASSOF.value));
		while (subclassIter.hasNext()) {
			Statement subclassStmt = subclassIter.next();
			if (isObject(subclassStmt, NuvaClass.VACCINE)) {
				reportVaccine(subject);
				return;
			} else if (isObject(subclassStmt, NuvaClass.VALENCE)) {
				reportValence(subject);
				return;
			} else if (isObject(subclassStmt, NuvaClass.DISEASE)) {
				reportDisease(subject);
				return;
			} else {
				outputUnknownObject(subject, tabName, prefix);
			}
		}

	}

	private void outputUnknownObject(Resource subject, String tabName, String prefix) throws TermServerScriptException {
		StmtIterator stmtIterator = subject.listProperties();
		String objClass = "Unknown";
		String type = "Unknown";
		int translationCount = 0;
		while (stmtIterator.hasNext()) {
			Statement stmt = stmtIterator.next();
			if (isPredicate(stmt, NuvaUri.ID) || isPredicate(stmt, NuvaUri.CODE)) {
				//We can skip this, subject already listed
			} else if (isPredicate(stmt, NuvaUri.SUBCLASSOF)) {
				objClass = getObject(stmt);
			} else if (isPredicate(stmt, NuvaUri.TYPE)) {
				type = getObject(stmt);
				incrementSummaryInformation(prefix + "type - " + type);
			} else if (isPredicate(stmt, NuvaUri.COMMENT)) {
				//TO DO Check for @<lang>
				translationCount++;
			}
		}
		String additionalProperties = Streams.stream(subject.listProperties())
				.filter(p -> !hasKnownPredicate(p))
				.map(this::toString)
				.collect(Collectors.joining(",\n"));
		incrementSummaryInformation(prefix + "Subjects");
		report(getTab(tabName), subject.toString().replace(NUVA_NS, ""), type, objClass, translationCount, additionalProperties);
	}

	private void reportVaccine(Resource subject) throws TermServerScriptException {
		String nuvaId = subject.toString().replace(NUVA_NS, "");
		NuvaVaccine vaccine = NuvaVaccine.fromResource(nuvaId, subject.listProperties());
		report(getTab(TAB_VACCINES), vaccine.getExternalIdentifier(), vaccine.getAbstractStr(), vaccine.getTranslationCount() + "\n" + vaccine.getEnLabel(), strList(vaccine.getValenceRefs()), strSctList(vaccine.getSnomedCodes()), strList(vaccine.getHiddenLabels()), vaccine.getCreated(), strList(vaccine.getMatches()));
	}
	
	private void reportValence(Resource subject) throws TermServerScriptException {
		String nuvaId = subject.toString().replace(NUVA_NS, "");
		NuvaValence valence = NuvaValence.fromResource(nuvaId, subject.listProperties());
		report(getTab(TAB_VALENCES), valence.getExternalIdentifier(), valence.getEnLabel(), strList(valence.getPrevents()), valence.getCreated());
	}

	private void reportDisease(Resource subject) throws TermServerScriptException {
		String nuvaId = subject.toString().replace(NUVA_NS, "");
		NuvaDisease disease = NuvaDisease.fromResource(nuvaId, subject.listProperties());
		report(getTab(TAB_DISEASES), disease.getExternalIdentifier(), disease.getTranslationCount() + "\n" + disease.getEnLabel(), disease.getCreated());
	}

	private String strList(List<String> list) {
		return list.stream()
				.collect(Collectors.joining("\n"));
	}

	private String strSctList(List<String> list) {
		return list.stream()
				.map(this::getSctItem)
				.collect(Collectors.joining("\n"));
	}

	private String getSctItem(String sctId) {
		try {
			Concept c = gl.getConcept(sctId, false, false);
			return c == null ? sctId : c.toString();
		} catch (Exception e) {
			return sctId + ": " + e.getMessage();
		}
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
		LOGGER.info("Loading NUVA file: " + inputFile);
		// create an empty model
		Model model = ModelFactory.createDefaultModel();

		// use the RDFDataMgr to find the input file
		InputStream in = RDFDataMgr.open(inputFile.getAbsolutePath());

		// read the RDF/XML file
		model.read(in, null);
		return model;
	}

	public List<NuvaVaccine> asVaccines(File file) {
		dataModel = loadNuva(file);
		Map<String, NuvaVaccine> vaccineMap = new HashMap<>();
		Map<String, NuvaValence> valenceMap = new HashMap<>();
		Map<String, NuvaDisease> diseaseMap = new HashMap<>();
		subClassProperty = dataModel.getProperty(NuvaUri.SUBCLASSOF.value);
		ResIterator subIterator = dataModel.listSubjects();
		while (subIterator.hasNext()) {
			Resource subject = subIterator.next();
			convertToNuvaObjects(subject, vaccineMap, valenceMap, diseaseMap);
		}
		formHierarchy(vaccineMap, valenceMap, diseaseMap);
		return new ArrayList<>(vaccineMap.values());
	}

	private void convertToNuvaObjects(Resource subject, Map<String, NuvaVaccine> vaccineMap, Map<String, NuvaValence> valenceMap, Map<String, NuvaDisease> diseaseMap) {
		String nuvaId = subject.toString().replace(NUVA_NS, "");
		StmtIterator subclassIter = subject.listProperties(subClassProperty);
		while (subclassIter.hasNext()) {
			Statement subclassStmt = subclassIter.next();
			if (isObject(subclassStmt, NuvaClass.VACCINE)) {
				NuvaVaccine vaccine = NuvaVaccine.fromResource(nuvaId, subject.listProperties());
				vaccineMap.put(vaccine.getExternalIdentifier(), vaccine);
				return;
			} else if (isObject(subclassStmt, NuvaClass.VALENCE)) {
				NuvaValence valence = NuvaValence.fromResource(nuvaId, subject.listProperties());
				valenceMap.put(valence.getExternalIdentifier(), valence);
				return;
			} else if (isObject(subclassStmt, NuvaClass.DISEASE)) {
				NuvaDisease disease = NuvaDisease.fromResource(nuvaId, subject.listProperties());
				diseaseMap.put(disease.getExternalIdentifier(), disease);
				return;
			}
		}
	}


	private void formHierarchy(Map<String, NuvaVaccine> vaccineMap, Map<String, NuvaValence> valenceMap, Map<String, NuvaDisease> diseaseMap) {
		formVaccineHerarchy(vaccineMap, valenceMap);
		formValenceHierarchy(valenceMap, diseaseMap);
	}

	private void formValenceHierarchy(Map<String, NuvaValence> valenceMap, Map<String, NuvaDisease> diseaseMap) {
		//Now loop through the valences to see what disease they prevent, and link that object
		//Also  if the valences have parent valences, link them up
		for (NuvaValence valence : valenceMap.values()) {
			for (String parentValenceId : valence.getParentValenceIds()) {
				NuvaValence parentValence = valenceMap.get(parentValenceId);
				if (parentValence != null) {
					valence.addParentValence(parentValence);
				}
			}

			for (String diseaseId : valence.getPrevents()) {
				NuvaDisease disease = diseaseMap.get(diseaseId);
				if (disease != null) {
					valence.setDisease(disease);
				}
			}
		}
	}

	private void formVaccineHerarchy(Map<String, NuvaVaccine> vaccineMap, Map<String, NuvaValence> valenceMap) {
		//Work through all the vaccines and populate them with the valences that they currently have string references to
		for (NuvaVaccine vaccine : vaccineMap.values()) {
			for (String valenceId : vaccine.getValenceRefs()) {
				NuvaValence valence = valenceMap.get(valenceId);
				if (valence != null) {
					vaccine.addValence(valence);
				}
			}
		}
	}

}


