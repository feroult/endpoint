package io.yawp.driver.postgresql.datastore;

import io.yawp.commons.utils.Environment;
import io.yawp.driver.postgresql.Person;
import io.yawp.driver.postgresql.configuration.InitialContextSetup;
import io.yawp.driver.postgresql.sql.ConnectionManager;
import io.yawp.driver.postgresql.sql.SqlRunner;
import io.yawp.repository.EndpointScanner;
import io.yawp.repository.Repository;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.sql.Connection;

public class DatastoreTestCase {

    protected ConnectionManager connectionManager;

    protected static Repository yawp;

    @BeforeClass
    public static void setUpClass() throws Exception {
        configureEnvironment();
        createRepository();
        syncTables();
    }

    @AfterClass
    public static void tearDownClass() {
        InitialContextSetup.unregister();
    }

    @Before
    public void setupTestCase() {
        connectionManager = new ConnectionManager();
    }

    private static void configureEnvironment() {
        Environment.set("test");
        InitialContextSetup.configure("configuration/jetty-env-test.xml");
    }

    private static void createRepository() {
        yawp = Repository.r().setFeatures(new EndpointScanner(testPackage()).scan());
    }

    private static void syncTables() {
        SchemaSynchronizer schemaSynchronizer = new SchemaSynchronizer();
        schemaSynchronizer.sync(yawp.getFeatures().getEndpointClazzes());
    }

    @SuppressWarnings("unused")
    private void dropTables() {
        connectionManager.execute("drop schema public cascade; create schema public;");
    }

    protected static String testPackage() {
        return Person.class.getPackage().getName();
    }

}
