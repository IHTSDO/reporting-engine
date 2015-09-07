package org.ihtsdo.snowowl.authoring.single.api.service.dao;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.apache.commons.dbcp2.*;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;

import com.b2international.snowowl.core.ApplicationContext;
import com.b2international.snowowl.core.config.SnowOwlConfiguration;
import com.b2international.snowowl.datastore.config.RepositoryConfiguration;

import java.sql.Connection;
import java.util.Map;
import java.util.Properties;

/**
 * @author Andres.Cespedes
 * @see http://examples.javacodegeeks.com/core-java/apache/commons/org-apache-commons-dbcp2-poolableconnection-example/
 * @version 1.0 Date: 14/01/2015
 * @since 1.7
 *
 */
public class PoolConnectionFactory {
	public static final String MYSQL_DRIVER = "com.mysql.jdbc.Driver";
	public static final String ORACLE_DRIVER = "oracle.jdbc.OracleDriver";
	public static final String DBCP_DRIVER = "org.apache.commons.dbcp2.PoolingDriver";
	public static Class driverClass;
	private static PoolingDriver driver;

	private static final String DB_NAME = "snomedStore";
	private static final String DB_ADDITIONAL_PROPERTIES = "&autoReconnect=true&poolPreparedStatements=true&maxOpenPreparedStatements=20";
	private static String CONNECTION_POOL_NAME = "IHTSDO_TS_DBCP";
	private static String CONNECTION_STRING = "jdbc:apache:commons:dbcp:" + CONNECTION_POOL_NAME;

	/**
	 * 
	 * @param driver
	 */
	public static void registerJDBCDriver(String driver) {
		try {
			driverClass = Class.forName(driver);
		} catch (ClassNotFoundException e) {
			System.err.println("There was not able to find the driver class");
		}
	}

	/**
	 * 
	 * @return the DBCP Driver
	 */
	public static PoolingDriver getDBCPDriver() {
		try {
			Class.forName(DBCP_DRIVER);
		} catch (ClassNotFoundException e) {
			System.err.println("There was not able to find the driver class");
		}
		try {
			driver = (PoolingDriver) DriverManager
					.getDriver("jdbc:apache:commons:dbcp:");
		} catch (SQLException e) {
			System.err.println("There was an error: " + e.getMessage());
		}
		return driver;
	}

	/**
	 * Registry a Pool in the DBCP Driver
	 * 
	 * @param poolName
	 * @param pool
	 */
	public static void registerPool(String poolName, ObjectPool pool) {
		driver.registerPool(poolName, pool);
	}
	
	public void init() {
		
		//Create a new database configuration based on the current repository configuration
		RepositoryConfiguration config = ApplicationContext.getInstance().getServiceChecked(SnowOwlConfiguration.class).getModuleConfig(RepositoryConfiguration.class);
		Map<Object, Object> propertyMap = config.getDatasourceProperties(DB_NAME);
		Properties dbProperties = new Properties();
		dbProperties.putAll(propertyMap);
		String dbUrl = propertyMap.get("uRL").toString() + DB_ADDITIONAL_PROPERTIES;
		
		// 1. Register the Driver to the jbdc.driver java property
		PoolConnectionFactory.registerJDBCDriver(PoolConnectionFactory.MYSQL_DRIVER);

		// 2. Create the Connection Factory (DriverManagerConnectionFactory)
		ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(dbUrl, dbProperties);

		// 3. Instantiate the Factory of Pooled Objects
		PoolableConnectionFactory poolfactory = new PoolableConnectionFactory(
				connectionFactory, null);
		poolfactory.setMaxOpenPrepatedStatements(20);
		poolfactory.setPoolStatements(true);

		// 4. Create the Pool with the PoolableConnection objects
		ObjectPool connectionPool = new GenericObjectPool(poolfactory);

		// 5. Set the objectPool to enforces the association (prevent bugs)
		poolfactory.setPool(connectionPool);

		// 6. Get the Driver of the pool and register them
		PoolingDriver dbcpDriver = PoolConnectionFactory.getDBCPDriver();
		dbcpDriver.registerPool(CONNECTION_POOL_NAME, connectionPool);
	}

	public Connection getConn() throws SQLException {
		return DriverManager.getConnection(CONNECTION_STRING);
	}

}
