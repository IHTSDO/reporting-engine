package org.ihtsdo.termserver.scripting.pipeline.nuva.domain;

import org.apache.jena.rdf.model.Statement;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.nuva.NuvaConstants;
import org.ihtsdo.termserver.scripting.pipeline.nuva.NuvaOntologyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public abstract class NuvaConcept extends ExternalConcept implements NuvaConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(NuvaConcept.class);

	protected int translationCount = 0;
	protected String created;
	protected String modified;
	protected boolean isAbstract;
	private String enLabel;
	protected final List<String> hiddenLabels = new ArrayList<>();
	protected final Map<String, String> altLabels = new HashMap<>();
	protected List<String> synonyms = new ArrayList<>();

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
	public int getPriority() {
		return 0;
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
		if (StringUtils.isEmpty(enLabel)) {
			LOGGER.debug("Check no display name here");
			return externalIdentifier + " - display name not specified";
		}
		return enLabel;
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
		if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.LABEL)
			|| isPredicate(stmt, NuvaOntologyLoader.NuvaUri.COMMENT)) {
			NuvaLabel translation = getLabel(stmt);
			if (translation.hasNoLanguage()) {
				synonyms.add(translation.getValue());
			} else if (translation.hasLanguage("en")) {
				setEnLabel(translation.getValue());
			}
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.ID)) {
			setExternalIdentifier(getObject(stmt));
			return true;
		} else if (isPredicate(stmt , NuvaOntologyLoader.NuvaUri.ALT_LABEL)) {
			NuvaLabel altLabel = getLabel(stmt);
			altLabels.put(altLabel.getLangCode(), altLabel.getValue());
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
				return NuvaLabel.fromLiteral(stmt.getObject().asLiteral());
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
		return enLabel;
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

	public List<String> getSynonyms() {
		return synonyms;
	}

	public void postImportAdjustment() {
		//Are we missing a label?  See if we can use a label instead
		if (enLabel == null && !synonyms.isEmpty()) {
			setEnLabel("Missing translation: " + synonyms.get(0));
		}
	}

	private void setEnLabel(String enLabel) {
		//If this string contains a new line character, cut at that point
		int cut = enLabel.indexOf("\n");
		if (cut > 0) {
			this.enLabel = enLabel.substring(0, cut);
		} else {
			this.enLabel = enLabel;
		}
	}

	public Map<String, String> getAltLabels() {
		return altLabels;
	}

	public String getAltLabel(String langCode, String fallBack, ContentPipelineManager cpm) throws TermServerScriptException {
		int tabIdx = cpm.getTab(TAB_MODELING_ISSUES);
		if (altLabels.containsKey(langCode)) {
			return altLabels.get(langCode);
		} else if (altLabels.containsKey(fallBack)) {
			//Do we have a French translation we could use?
			if (altLabels.containsKey("fr")) {
				cpm.report(tabIdx, this, RF2Constants.Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Missing English altLabel, using French: " + altLabels.get("fr"));
			}
			return altLabels.get(fallBack);
		}
		cpm.report(tabIdx, this, RF2Constants.Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Missing all AltLabels");
		return null;
	}
}
