package org.ihtsdo.termserver.scripting.pipeline.nuva.domain;

import org.apache.jena.rdf.model.Statement;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.nuva.NuvaConstants;
import org.ihtsdo.termserver.scripting.pipeline.nuva.NuvaOntologyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.ihtsdo.termserver.scripting.util.UnacceptableCharacters.NBSPSTR;

public abstract class NuvaConcept extends ExternalConcept implements NuvaConstants, RF2Constants {

	private static final Logger LOGGER = LoggerFactory.getLogger(NuvaConcept.class);
	private static ContentPipelineManager cpm;

	protected int translationCount = 0;
	protected String created;
	protected String modified;
	protected boolean isAbstract;
	private final Map<String, String> labels = new HashMap<>();
	private final Map<String, String> comments = new HashMap<>();
	protected final List<String> hiddenLabels = new ArrayList<>();
	protected final Map<String, String> altLabels = new HashMap<>();
	protected List<String> untranslatedLabels = new ArrayList<>();

	public static void init(ContentPipelineManager cpm) {
		NuvaConcept.cpm = cpm;
	}

	protected NuvaConcept(String externalIdentifier) {
		super(externalIdentifier);
	}

	@Override
	public boolean isHighUsage() {
		return false;
	}

	@Override
	public boolean isHighestUsage() {
		return false;
	}

	@Override
	public String[] getCommonColumns() {
		return new String[0];
	}

	@Override
	public String toString() {
		return externalIdentifier;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof NuvaConcept other) {
			return other.externalIdentifier.equals(this.externalIdentifier);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return externalIdentifier.hashCode();
	}

	@Override
	public String getLongDisplayName() {
		try {
			return getComment("en", "fr");
		} catch (TermServerScriptException e) {
			throw new IllegalStateException("Failed to get long display name (ie OWL comment) for " + getExternalIdentifier(), e);
		}
	}

	@Override
	public String getShortDisplayName() {
		return getLongDisplayName();
	}

	protected static boolean isPredicate(Statement stmt, NuvaOntologyLoader.NuvaUri uri) {
		return stmt.getPredicate().hasURI(uri.value);
	}

	protected static boolean isObject(Statement stmt, Object obj) {
		return getObject(stmt).equalsIgnoreCase(obj.toString());
	}

	protected boolean isCommonPredicate(Statement stmt) {
		if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.LABEL)) {
			NuvaLabel label = getLabel(stmt);
			if (label.hasNoLanguage()) {
				untranslatedLabels.add(label.getValue());
			} else {
				labels.put(label.getLangCode(), label.getValue());
			}
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.ID)) {
			setExternalIdentifier(getObject(stmt));
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.ALT_LABEL)) {
			NuvaLabel altLabel = getLabel(stmt);
			altLabels.put(altLabel.getLangCode(), altLabel.getValue());
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.COMMENT)) {
			NuvaLabel comment = getLabel(stmt);
			comments.put(comment.getLangCode(), comment.getValue());
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.ABSTRACT)) {
			String abstractStr = getObject(stmt);
			isAbstract = abstractStr.contains("true");
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.TYPE)) {
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.CREATED)) {
			created = getObject(stmt);
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.MODIFIED)) {
			modified = getObject(stmt);
			return true;
		}
		return false;
	}

	protected static NuvaLabel getLabel(Statement stmt) {
		if (stmt.getObject().isLiteral()) {
			try {
				NuvaLabel label = NuvaLabel.fromLiteral(stmt.getObject().asLiteral());
				normaliseLabel(label);
				return label;
			} catch (Exception e) {
				LOGGER.warn("{}: {}", e.getMessage(), stmt);
				String str = stmt.getObject().toString();
				int cut = str.indexOf("^^");
				return new NuvaLabel(str.substring(0,cut), null);
			}
		} else {
			throw new IllegalArgumentException("Not a label: " + stmt);
		}
	}

	private static void normaliseLabel(NuvaLabel label) {
		String text = label.getValue();
		String normalizedText = text.replaceAll("[\r\n\t]", "")
				.replace("  ", " ")
				.replace(NBSPSTR, " ")
				.trim();

		if (!text.equals(normalizedText) && label.getLangCode().equals("en")) {
			LOGGER.warn("Removed illegal or double whitespace from label: {}", label.getValue());
		}
		label.setValue(normalizedText);
	}

	public static String getObject(Statement stmt) {
		if (stmt.getObject().isLiteral()) {
			try {
				return stmt.getObject().asLiteral().toString();
			} catch (Exception e) {
				LOGGER.warn("{}: {}", e.getMessage(), stmt);
				String str = stmt.getObject().toString();
				int cut = str.indexOf("^^");
				return str.substring(0,cut);
			}
		} else {
			String str = stmt.getObject().toString();
			if (str.startsWith(NUVA_NS)){
				return str.substring(NUVA_NS.length());
			}
			int cut = str.indexOf("#") + 1;
			return str.substring(cut);
		}
	}

	public int getTranslationCount() {
		return translationCount;
	}

	public String getEnLabel() {
		return labels.get("en");
	}

	public String getCreated() {
		return created;
	}

	public List<String> getHiddenLabels() {
		return hiddenLabels;
	}

	public boolean isAbstract() {
		return isAbstract;
	}

	public List<String> getUntranslatedLabels() {
		return untranslatedLabels;
	}

	public void postImportAdjustment(ContentPipelineManager cpm) throws TermServerScriptException {
		//Are we missing a label?  See if we can use a label instead
		if (getEnLabel() == null) {
			int tabIdx = cpm.getTab(TAB_MODELING_ISSUES);
			//Do we have a French translation we could use?
			if (labels.containsKey("fr")) {
				cpm.report(tabIdx, this, "", RF2Constants.Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Missing English label, using French: " + labels.get("fr"));
				labels.put("en", labels.get("fr"));
			} else if  (!untranslatedLabels.isEmpty()) {
				cpm.report(tabIdx, this, "", RF2Constants.Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Missing English label, using synonym: " + untranslatedLabels.get(0));
				labels.put("en", "Missing translation: " + untranslatedLabels.get(0));
			}
		}
	}

	public Map<String, String> getAltLabels() {
		return altLabels;
	}

	public String getAltLabel(String langCode, String fallBack) throws TermServerScriptException {
		return getText("altLabel", altLabels, langCode, fallBack);
	}

	public String getLabel(String langCode, String fallBack) throws TermServerScriptException {
		return getText("Labels", labels, langCode, fallBack);
	}

	public String getComment(String langCode, String fallBack) throws TermServerScriptException {
		return getText("Comments", comments, langCode, fallBack);
	}

	public String getText(String mapName, Map<String, String> textMap, String langCode, String fallBack) throws TermServerScriptException {
		int tabIdx = cpm.getTab(TAB_MODELING_ISSUES);
		if (textMap.containsKey(langCode)) {
			return textMap.get(langCode);
		} else if (textMap.containsKey(fallBack)) {
			//Do we have a French translation we could use?
			if (textMap.containsKey(fallBack)) {
				cpm.report(tabIdx, this, "", RF2Constants.Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Missing English " + mapName + ", using " + fallBack + " : " + textMap.get("fr"));
			}
			return textMap.get(fallBack);
		}
		cpm.report(tabIdx, this, "", RF2Constants.Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Missing all text in " + mapName);
		return null;
	}
}
