package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import com.b2international.snowowl.api.domain.IComponent;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.Utils;
import org.apache.commons.lang.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.*;
import java.util.Date;
import java.util.*;

public class CdoStore {

	@Autowired
	private BasicDataSource dataSource;

	private Logger logger = LoggerFactory.getLogger(getClass());

	private static final String MAIN = "MAIN";

	private static final String GET_BRANCH_ID = "select childBranch.id "
		+ " from cdo_branches childBranch "
		+ " where childBranch.base_id =  "
		+ " ( select max(parent.id) from cdo_branches parent where name = ? ) "
		+ " and childBranch.name = ? ";

	private static final String GET_PROJECT_BRANCH_ID = " select max(childBranch.id) "
			+ " from cdo_branches childBranch "
			+ " where childBranch.base_id = 0 "
			+ " and childBranch.name = ? "; 

	//Union updates from concept, description and relationship tables to get last update times
	private static final String GET_CONCEPTS_CHANGED_SINCE =
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
	
	private static final String GET_CONCEPTS_LAST_MODIFIED = 
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
	
	private static final String CREATE_TEMP_TABLE = "CREATE TABLE IF NOT EXISTS temp_component (id varchar(19), transaction_id int,"
			+ "INDEX `temp_component_IDX_001` (`id`, `transaction_id`))" ;
	
	private static final String CLEAN_TEMP_TABLE = "Delete from temp_component where transaction_id = ?";
	
	private static final String POPULATE_TEMP_TABLE = "insert into temp_component values(?, ?)";
	
	private static final int QUERY_TIMEOUT = 30;
	
	public void init() throws SQLException {
		logger.info("Ensuring presence of temptable: temp_component");
		try (Connection conn = dataSource.getConnection()) {
			Statement createTempTableStatement = conn.createStatement();
			createTempTableStatement.execute(CREATE_TEMP_TABLE);
		}
	}

	public Integer getBranchId(String parentBranchName, String childBranchName) throws SQLException {
		try (Connection conn = dataSource.getConnection()) {
			PreparedStatement stmt;
			if (childBranchName.equals(MAIN)) {
				//MAIN is always branch 0
				return 0;
			} else if (parentBranchName.equals(MAIN)) {
				//If we're working with a direct child of MAIN, we need a slightly different SQL
				stmt = conn.prepareStatement(GET_PROJECT_BRANCH_ID);
				stmt.setString(1, childBranchName);
			} else {
				stmt = conn.prepareStatement(GET_BRANCH_ID);
				stmt.setString(1, parentBranchName);
				stmt.setString(2, childBranchName);
			}
			ResultSet rs = stmt.executeQuery();
			Integer result = rs.next() ? new Integer(rs.getInt(1)) : null;
			logger.info("Determined branch id for {}/{}: {}", parentBranchName, childBranchName, result);
			return result;
		}
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
		logger.info("Starting concepts changed query for branch " + branchId );
		try (Connection conn = dataSource.getConnection()) {
			PreparedStatement stmt = conn.prepareStatement(GET_CONCEPTS_CHANGED_SINCE);
			stmt.setInt(1, branchId);
			stmt.setInt(2, branchId);
			stmt.setInt(3, branchId);
			stmt.setLong(4, fromDate.getTime());
			ResultSet rs = stmt.executeQuery();
			Set<String> conceptsChanged = new HashSet<String>();
			while (rs.next()) {
				conceptsChanged.add(rs.getString(1));
			}
			logger.info("Completed concepts changed for branch " + branchId + " with " + conceptsChanged.size() + " concepts changed.");

			return conceptsChanged;
		}
	}
	
	/**
	 * Returns a list of all matching concepts and their last updated time
	 * @param branchId
	 * @param fromDate
	 * @return
	 * @throws SQLException
	 */
	public Map<String, Date> getLastUpdated(Integer branchId, List<IComponent> concepts, int transactionId, boolean populateTempTable) throws SQLException {
		StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		logger.info("Starting last updated query for branch " + branchId );

		try (Connection conn = dataSource.getConnection()) {
			if (populateTempTable) {
				populateTempTable(concepts, transactionId, conn);
				logger.info("Populated temp table for branchId {}", branchId);
			}

			PreparedStatement stmt = conn.prepareStatement(GET_CONCEPTS_LAST_MODIFIED);
			stmt.setInt(1, branchId);
			stmt.setInt(2, transactionId);
			stmt.setInt(3, branchId);
			stmt.setInt(4, transactionId);
			stmt.setInt(5, branchId);
			stmt.setInt(6, transactionId);

			Map<String, Date> lastUpdatedMap = new HashMap<String, Date>();
			stmt.setQueryTimeout(QUERY_TIMEOUT);
			ResultSet rs = stmt.executeQuery();
			int rowCount = 0;
			while (rs.next()) {
				lastUpdatedMap.put(rs.getString(1), new Date(rs.getLong(2)));
				rowCount++;
			}
			logger.info("Recovered " + rowCount + " last updated times for branch {}", branchId);

			//If we didn't populate the temp table, then it's our turn to clean it up
			if (!populateTempTable) {
				cleanTempTable(transactionId, conn);
			}
			stopWatch.stop();
			logger.info("Completed last updated for branch " + branchId + " with " + concepts.size() + " conflicts in " + stopWatch);

			return lastUpdatedMap;
		}
	}

	private void populateTempTable(List<IComponent> components, int transactionId, Connection conn) throws SQLException {
		conn.setAutoCommit(false);
		PreparedStatement stmt = conn.prepareStatement(POPULATE_TEMP_TABLE);
		
		for(IComponent component : components){
			stmt.setString(1, component.getId());
			stmt.setInt(2, transactionId);
			stmt.execute();
		}
		conn.commit();
		conn.setAutoCommit(true);
	}
	
	private void cleanTempTable(int transactionId, Connection conn) throws SQLException {
		PreparedStatement stmt = conn.prepareStatement(CLEAN_TEMP_TABLE);
		stmt.setInt(1, transactionId);
		stmt.execute();
	}

}
