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
	
	enum CDOStatement { GET_BRANCH_ID, 
		GET_PROJECT_BRANCH_ID, 
		GET_CONCEPTS_CHANGED_SINCE, 
		GET_CONCEPTS_LAST_MODIFIED, 
		CLEAN_TEMP_TABLE,
		POPULATE_TEMP_TABLE};
	
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
			+ " select c.id as concept_sctid, cdo_created "
			+ " from snomed_concept c use index (SNOMED_CONCEPT_IDX2001),"
			+ " temp_component t "
			+ " where cdo_branch = ? "
			+ " and c.id = t.id "
			+ " and t.transaction_id = ?"
			+ " union "
			+ " select c.id, d.cdo_created "
			+ " from temp_component t LEFT JOIN snomed_concept c use index (SNOMED_CONCEPT_IDX2001) "
			+ " on c.id = t.id, "
			+ " snomed_description d, snomed_concept_descriptions_list cdl "
			+ " where c.cdo_id = cdl.cdo_source "
			+ " and cdl.cdo_value = d.cdo_id "
			+ " and d.cdo_branch = cdl.cdo_branch "
			+ " and cdl.cdo_branch = ? "
			+ " and t.transaction_id = ?"
			+ " and c.cdo_branch = cdl.cdo_branch "
			+ " union "
			+ " select c.id, r.cdo_created "
			+ " from snomed_concept c use index (SNOMED_CONCEPT_IDX2001), "
			+ " snomed_relationship r, snomed_concept_outboundrelationships_list crl, "
			+ " temp_component t "
			+ " where c.cdo_id = crl.cdo_source "
			+ " and crl.cdo_value = r.cdo_id "
			+ " and r.cdo_branch = crl.cdo_branch "
			+ " and crl.cdo_branch = ? "
			+ " and c.id = t.id "
			+ " and t.transaction_id = ?"
			+ " and c.cdo_branch = crl.cdo_branch ) all_components "
			+ " group by concept_sctid ";
	
	private static final String createTempTable = "CREATE TABLE IF NOT EXISTS temp_component (id varchar(19), transaction_id int,"
			+ "INDEX `temp_component_IDX_001` (`id`, `transaction_id`))" ;
	
	private static final String cleanTempTable = "Delete from temp_component where transaction_id = ?";
	
	private static final String populateTempTable = "insert into temp_component values(?, ?)";
	
	public void init() throws SQLException {
	
		getDBConn();
		preparedStatements = new HashMap<>();
		preparedStatements.put(CDOStatement.GET_BRANCH_ID, conn.prepareStatement(getBranchId));
		preparedStatements.put(CDOStatement.GET_PROJECT_BRANCH_ID, conn.prepareStatement(getProjectBranchId));
		preparedStatements.put(CDOStatement.GET_CONCEPTS_CHANGED_SINCE, conn.prepareStatement(getConceptsChangedSince));
		preparedStatements.put(CDOStatement.GET_CONCEPTS_LAST_MODIFIED, conn.prepareStatement(getConceptsLastModified));
		preparedStatements.put(CDOStatement.CLEAN_TEMP_TABLE, conn.prepareStatement(cleanTempTable));
		preparedStatements.put(CDOStatement.POPULATE_TEMP_TABLE, conn.prepareStatement(populateTempTable));
		
		logger.info("Ensuring presence of temptable: temp_component");
		Statement createTempTableStatement = conn.createStatement();
		createTempTableStatement.execute(createTempTable);
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
		
		//Generate a unique identifier for temp table population
		int transactionId = concepts.hashCode() * 1000 + branchId;
		populateTempTable(concepts, transactionId);
		logger.info("Populated temp table");
		
		PreparedStatement stmt = preparedStatements.get(CDOStatement.GET_CONCEPTS_LAST_MODIFIED);
		stmt.setInt(1, branchId);
		stmt.setInt(2, transactionId);
		stmt.setInt(3, branchId);
		stmt.setInt(4, transactionId);
		stmt.setInt(5, branchId);
		stmt.setInt(6, transactionId);
		
		Map<String, Date> lastUpdatedMap = new HashMap<String, Date>();
		ResultSet rs = stmt.executeQuery();
		int rowCount = 0;
		while (rs.next()) {
			lastUpdatedMap.put(rs.getString(1), new Date(rs.getLong(2)));
			rowCount++;
		}
		logger.info("Recovered " + rowCount + " last updated times" );
		cleanTempTable(transactionId);
		stopWatch.stop();
		logger.info("Completed last updated for branch " + branchId + " with " + concepts.size() + " conflicts in " + stopWatch);

		return lastUpdatedMap; 
	}

	private void populateTempTable(List<IComponent> components, int transactionId) throws SQLException {
		conn.setAutoCommit(false);
		PreparedStatement stmt = preparedStatements.get(CDOStatement.POPULATE_TEMP_TABLE);
		
		for(IComponent component : components){
			stmt.setString(1, component.getId());
			stmt.setInt(2, transactionId);
			stmt.execute();
		}
		conn.commit();
		conn.setAutoCommit(true);
	}
	
	private void cleanTempTable(int transactionId) throws SQLException {
		PreparedStatement stmt = preparedStatements.get(CDOStatement.CLEAN_TEMP_TABLE);
		stmt.setInt(1, transactionId);
		stmt.execute();
	}

}
