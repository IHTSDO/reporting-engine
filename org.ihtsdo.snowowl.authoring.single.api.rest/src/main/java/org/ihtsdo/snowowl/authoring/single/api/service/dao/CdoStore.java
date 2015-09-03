package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.b2international.snowowl.api.domain.IComponent;
import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.datastore.config.RepositoryConfiguration;

import java.sql.*;
import java.util.Date;
import java.util.*;

import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CdoStore {
	
	private Map <CDOStatement, PreparedStatement> preparedStatements;
	
	private static final String DB_NAME = "snomedStore";
	private static final String DB_ADDITIONAL_PROPERTIES = "&autoReconnect=true";
	private static final int BATCH_SIZE = 500;
	public static final String MAIN = "MAIN";
	
	enum CDOStatement { GET_BRANCH_ID, GET_PROJECT_BRANCH_ID, GET_CONCEPTS_CHANGED_SINCE, GET_CONCEPTS_LAST_MODIFIED };
	
	Connection conn;
	
	private Logger logger = LoggerFactory.getLogger(getClass());
	
	private static final String getBranchId = "select childBranch.id "
		+ " from cdo_branches childBranch "
		+ " where childBranch.base_id =  "
		+ " ( select max(parent.id) from cdo_branches parent where name = ? ) "
		+ " and childBranch.name = ? ";
	
	private static final String getProjectBranchId = " select max(childBranch.id) "
			+ " from cdo_branches childBranch "
			+ " where childBranch.base_id = 0 "
			+ " and childBranch.name = ? "; 

	//Union updates from concept, description and relationship tables to get last update times
	private static final String getConceptsChangedSince = 
		" select concept_sctid, max(cdo_created) from ( "
		+ " select id as concept_sctid, cdo_created "
		+ " from snomed_concept "
		+ " where cdo_branch = ? "
		+ " union "
		+ " select c.id,d.cdo_created "
		+ " from snomed_concept c, snomed_description d, snomed_concept_descriptions_list cdl "
		+ " where c.cdo_id = cdl.cdo_source "
		+ " and cdl.cdo_value = d.cdo_id "
		+ " and d.cdo_branch = cdl.cdo_branch "
		+ " and cdl.cdo_branch = ? "
		+ " and c.cdo_branch = cdl.cdo_branch "
		+ " union "
		+ " select c.id, r.cdo_created "
		+ " from snomed_concept c, snomed_relationship r, snomed_concept_outboundrelationships_list crl "
		+ " where c.cdo_id = crl.cdo_source "
		+ " and crl.cdo_value = r.cdo_id "
		+ " and r.cdo_branch = crl.cdo_branch "
		+ " and crl.cdo_branch = ? "
		+ " and c.cdo_branch = crl.cdo_branch) all_components " 
		+ " where cdo_created >= ?  "
		+ " group by concept_sctid";
	
	private static final String getConceptsLastModified = 
			" select concept_sctid, max(cdo_created) from ( "
			+ " select id as concept_sctid, cdo_created "
			+ " from snomed_concept use index (SNOMED_CONCEPT_IDX2001)"
			+ " where cdo_branch = ? "
			+ " and id in (?) "
			+ " union "
			+ " select c.id, d.cdo_created "
			+ " from snomed_concept c use index (SNOMED_CONCEPT_IDX2001), "
			+ " snomed_description d, snomed_concept_descriptions_list cdl "
			+ " where c.cdo_id = cdl.cdo_source "
			+ " and cdl.cdo_value = d.cdo_id "
			+ " and d.cdo_branch = cdl.cdo_branch "
			+ " and cdl.cdo_branch = ? "
			+ " and c.id in (?) "
			+ " and c.cdo_branch = cdl.cdo_branch "
			+ " union "
			+ " select c.id, r.cdo_created "
			+ " from snomed_concept c use index (SNOMED_CONCEPT_IDX2001), "
			+ " snomed_relationship r, snomed_concept_outboundrelationships_list crl "
			+ " where c.cdo_id = crl.cdo_source "
			+ " and crl.cdo_value = r.cdo_id "
			+ " and r.cdo_branch = crl.cdo_branch "
			+ " and crl.cdo_branch = ? "
			+ " and c.id in (?) "
			+ " and c.cdo_branch = crl.cdo_branch ) all_components "
			+ " group by concept_sctid ";
	
	public void init() throws SQLException {
	
		getDBConn();
		preparedStatements = new HashMap<>();
		preparedStatements.put(CDOStatement.GET_BRANCH_ID, conn.prepareStatement(getBranchId));
		preparedStatements.put(CDOStatement.GET_PROJECT_BRANCH_ID, conn.prepareStatement(getProjectBranchId));
		preparedStatements.put(CDOStatement.GET_CONCEPTS_CHANGED_SINCE, conn.prepareStatement(getConceptsChangedSince));
		preparedStatements.put(CDOStatement.GET_CONCEPTS_LAST_MODIFIED, conn.prepareStatement(getConceptsLastModified));
	}
	
	private void getDBConn() throws SQLException {
		//Create a new database configuration based on the current repository configuration
		RepositoryConfiguration config = ApplicationContext.getInstance().getServiceChecked(SnowOwlConfiguration.class).getModuleConfig(RepositoryConfiguration.class);
		Map<Object, Object> propertyMap = config.getDatasourceProperties(DB_NAME);
		Properties dbProperties = new Properties();
		dbProperties.putAll(propertyMap);
		String dbUrl = propertyMap.get("uRL").toString() + DB_ADDITIONAL_PROPERTIES;
		conn = DriverManager.getConnection(dbUrl, dbProperties);
	}
	
	public Integer getBranchId(String parentBranchName, String childBranchName) throws SQLException {
		
		PreparedStatement stmt;
		if (childBranchName.equals(MAIN)) {
			//MAIN is always branch 0
			return new Integer(0);
		} else if (parentBranchName.equals(MAIN)) {
			//If we're working with a direct child of MAIN, we need a slightly different SQL
			stmt = preparedStatements.get(CDOStatement.GET_PROJECT_BRANCH_ID);
			stmt.setString(1, childBranchName);
		} else {
			stmt = preparedStatements.get(CDOStatement.GET_BRANCH_ID);
			stmt.setString(1, parentBranchName);
			stmt.setString(2, childBranchName);
		}
		ResultSet rs = stmt.executeQuery();
		return rs.next() ? new Integer (rs.getInt(1)) : null; 
	}
	
	/**
	 * Returns a list of all concepts modified (including descriptions and relationships)
	 * since a given date
	 * @param branchId
	 * @param fromDate
	 * @return
	 * @throws SQLException
	 */
	public Set<String> getConceptChanges(Integer branchId, Date fromDate) throws SQLException {
		
		PreparedStatement stmt = preparedStatements.get(CDOStatement.GET_CONCEPTS_CHANGED_SINCE);
		stmt.setInt(1, branchId);
		stmt.setInt(2, branchId);
		stmt.setInt(3, branchId);
		stmt.setLong(4, fromDate.getTime());
		ResultSet rs = stmt.executeQuery();
		Set<String> conceptsChanged = new HashSet<String>();
		while (rs.next()) {
			conceptsChanged.add(rs.getString(1));
		}
		return conceptsChanged; 
	}
	
	/**
	 * Returns a list of all matching concepts and their last updated time
	 * @param branchId
	 * @param fromDate
	 * @return
	 * @throws SQLException
	 */
	public Map<String, Date> getLastUpdated(Integer branchId, List<IComponent> concepts) throws SQLException {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		logger.info("Starting last updated query for branch " + branchId );
		
		PreparedStatement stmt = preparedStatements.get(CDOStatement.GET_CONCEPTS_LAST_MODIFIED);
		stmt.setInt(1, branchId);
		stmt.setInt(3, branchId);
		stmt.setInt(5, branchId);
		
		Map<String, Date> lastUpdatedMap = new HashMap<String, Date>();
		
		//Loop around in batches getting last updated dates of matching concepts
		int idx = 0;
		while (idx < concepts.size()) {
			StringBuilder inClause = new StringBuilder();
			boolean isFirstInBatch = true;
			for (int i= 0;i < BATCH_SIZE && idx < concepts.size(); i++, idx++) {
				if (!isFirstInBatch) {
					inClause.append(", ");
				} else {
					isFirstInBatch = false;
				}
				inClause.append(concepts.get(idx).getId());
			}
			logger.info("Selecting concepts: " + inClause.toString() );
			stmt.setString(2, inClause.toString());
			stmt.setString(4, inClause.toString());
			stmt.setString(6, inClause.toString());
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				lastUpdatedMap.put(rs.getString(1), new Date(rs.getLong(2)));
			}
		}
		stopWatch.stop();
		logger.info("Completed last updated for branch " + branchId + " with " + concepts.size() + " conflicts in " + stopWatch);

		return lastUpdatedMap; 
	}

}
