/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.la.database.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.la.commons.constants.LAConstants;
import org.wso2.carbon.la.commons.domain.LogGroup;
import org.wso2.carbon.la.commons.domain.config.LAConfiguration;
import org.wso2.carbon.la.database.DatabaseService;
import org.wso2.carbon.la.database.exceptions.DatabaseHandlerException;
import org.wso2.carbon.la.database.exceptions.LAConfigurationParserException;
import org.wso2.carbon.la.database.internal.constants.SQLQueries;
import org.wso2.carbon.la.database.internal.ds.LocalDatabaseCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LADatabaseService implements DatabaseService {

    private static final Log logger = LogFactory.getLog(LADatabaseService.class);
    private LADataSource dbh;
    private LAConfiguration laConfig;
    private static final String DB_CHECK_SQL = "SELECT * FROM ML_PROJECT";

    public LADatabaseService() {
        
        LAConfigurationParser mlConfigParser = new LAConfigurationParser();
        try {
            laConfig = mlConfigParser.getLAConfiguration(LAConstants.LOG_ANALYZER_XML);
        } catch (LAConfigurationParserException e) {
            String msg = "Failed to parse machine-learner.xml file.";
            logger.error(msg, e);
            throw new RuntimeException(msg, e);
        }
        
        try {
            dbh = new LADataSource(laConfig.getDatasourceName());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new RuntimeException(e.getMessage(), e);
        }

        String value = System.getProperty("setup");
        if (value != null) {
            LocalDatabaseCreator databaseCreator = new LocalDatabaseCreator(dbh.getDataSource());
            try {
                if (!databaseCreator.isDatabaseStructureCreated(DB_CHECK_SQL)) {
                    databaseCreator.createRegistryDatabase();
                } else {
                    logger.info("Machine Learner database already exists. Not creating a new database.");
                }
            } catch (Exception e) {
                String msg = "Error in creating the Machine Learner database";
                throw new RuntimeException(msg, e);
            }
        }
    }
    
    public LAConfiguration getLaConfiguration() {
        return laConfig != null ? laConfig : new LAConfiguration();
    }

    public void shutdown() throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement statement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            statement = connection.prepareStatement("SHUTDOWN");
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseHandlerException("An error has occurred while shutting down the database: "
                    + e.getMessage(), e);
        } finally {
            // Close the database resources.
            LADatabaseUtils.closeDatabaseResources(connection, statement);
        }
    }

    @Override
    public void createLogGroup(LogGroup logGroup) throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement createLogGroupStatement = null;
        int tenantId = logGroup.getTenantId();
        String username = logGroup.getUsername();
        String logGroupName = logGroup.getName();

        if (getLogGroup(logGroup.getName(), tenantId, username) != null) {
            throw new DatabaseHandlerException(String.format("Log Group [name] %s already exists.", logGroupName));
        }
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            createLogGroupStatement = connection.prepareStatement(SQLQueries.CREATE_LOG_GROUP);
            createLogGroupStatement.setString(1, logGroupName);
            createLogGroupStatement.setInt(2, tenantId);
            createLogGroupStatement.setString(3, username);
            createLogGroupStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully created log group: " + logGroupName);
            }
        } catch (SQLException e) {
            LADatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("Error occurred while inserting details of log group: " + logGroupName
                    + " to the database: " + e.getMessage(), e);
        } finally {
            // enable auto commit
            LADatabaseUtils.enableAutoCommit(connection);
            // close the database resources
            LADatabaseUtils.closeDatabaseResources(connection, createLogGroupStatement);
        }
    }

    @Override
    public void deleteLogGroup(String name, int tenantId, String username) throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement preparedStatement = null;
        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            preparedStatement = connection.prepareStatement(SQLQueries.DELETE_LOG_GROUP);
            preparedStatement.setString(1, name);
            preparedStatement.setInt(2, tenantId);
            preparedStatement.setString(3, username);
            preparedStatement.execute();
            connection.commit();
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully deleted the log group: " + name);
            }
        } catch (SQLException e) {
            LADatabaseUtils.rollBack(connection);
            throw new DatabaseHandlerException("Error occurred while deleting the log group: " + name + ": "
                    + e.getMessage(), e);
        } finally {
            // enable auto commit
            LADatabaseUtils.enableAutoCommit(connection);
            // close the database resources
            LADatabaseUtils.closeDatabaseResources(connection, preparedStatement);
        }
    }

    @Override
    public LogGroup getLogGroup(String name, int tenantId, String username) throws DatabaseHandlerException {
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet result;

        try {
            connection = dbh.getDataSource().getConnection();
            connection.setAutoCommit(false);
            statement = connection.prepareStatement(SQLQueries.GET_LOG_GROUP);
            statement.setString(1, name);
            statement.setInt(2, tenantId);
            statement.setString(3, username);
            result = statement.executeQuery();

            if (result.next()) {
                LogGroup logGroup = new LogGroup();
                logGroup.setName(result.getString(1));
                logGroup.setTenantId(result.getInt(2));
                logGroup.setUsername(result.getString(3));
                return logGroup;
            } else {
                return null;
            }
        } catch (SQLException e) {
            throw new DatabaseHandlerException("Error occurred while getting details of log group: " + name
                    + " to the database: " + e.getMessage(), e);
        } finally {
            // close the database resources
            LADatabaseUtils.closeDatabaseResources(connection, statement);
        }
    }

    @Override
    public List<String> getAllLogGroupNames(int tenantId, String username) throws DatabaseHandlerException {
        Connection connection = null;
        ResultSet result = null;
        PreparedStatement statement = null;
        List<String> groupnames = new ArrayList<String>();
        try {
            connection = dbh.getDataSource().getConnection();
            statement = connection.prepareStatement(SQLQueries.GET_ALL_LOG_GROUP_NAMES);
            statement.setInt(1, tenantId);
            statement.setString(2, username);
            result = statement.executeQuery();
            while (result.next()) {
                groupnames.add(result.getString(1));
            }
            return groupnames;
        } catch (SQLException e) {
            throw new DatabaseHandlerException(" An error has occurred while extracting log group names for user : " + username, e);
        } finally {
            // Close the database resources.
            LADatabaseUtils.closeDatabaseResources(connection, statement, result);
        }
    }

    @Override
    public List<LogGroup> getAllLogGroups() throws DatabaseHandlerException {
        return null;
    }

    @Override
    public void createLogStream(String name) throws DatabaseHandlerException {

    }

    @Override
    public void deleteLogStream(String name) throws DatabaseHandlerException {

    }

    @Override
    public List<String> getAllLogStreamNames() throws DatabaseHandlerException {
        return null;
    }
}
