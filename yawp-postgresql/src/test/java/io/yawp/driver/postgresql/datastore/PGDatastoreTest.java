package io.yawp.driver.postgresql.datastore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import javax.naming.Context;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.postgresql.ds.PGConnectionPoolDataSource;

public class PGDatastoreTest {

	private PGDatastore datastore;

	@BeforeClass
	public static void setUpClass() throws Exception {
		System.setProperty(Context.INITIAL_CONTEXT_FACTORY, InitialContextMock.class.getName());

		PGConnectionPoolDataSource ds = new PGConnectionPoolDataSource();
		ds.setUrl("jdbc:postgresql://localhost/yawp_test");
		// ds.setUser("MY_USER_NAME");
		// ds.setPassword("MY_USER_PASSWORD");

		InitialContextMock.bind("jdbc/yawp_test", ds);
	}

	@Before
	public void before() {
		datastore = new PGDatastore();
	}

	@Test
	public void testCreateRetrieveEntity() {
		// create table people (id bigserial primary key, entity jsonb);

		Entity entity = new Entity("people");
		entity.setProperty("name", "jim");

		datastore.put(entity);

		Entity retrievedEntity = datastore.get(entity.getKey());
		assertEquals("jim", retrievedEntity.getProperty("name"));
	}

	@Test
	public void testCreateUpdateEntity() {
		Entity entity = new Entity("people");
		entity.setProperty("name", "jim");

		Key key = datastore.put(entity);

		entity.setProperty("name", "robert");
		datastore.put(entity);

		Entity retrievedEntity = datastore.get(key);
		assertEquals("robert", retrievedEntity.getProperty("name"));

	}

	@Test
	public void delete() {
		Key key = Key.create("people", "xpto");
		Entity entity = new Entity(key);
		datastore.put(entity);

		datastore.delete(key);

		assertNull(datastore.get(key));
	}

	@Test
	public void testForceName() {
		Key key = Key.create("people", "xpto");

		Entity entity = new Entity(key);
		entity.setProperty("name", "jim");

		datastore.put(entity);

		Entity retrievedEntity = datastore.get(key);
		assertEquals("jim", retrievedEntity.getProperty("name"));
	}

	@Test
	public void testForceId() {
		Key key = Key.create("people", 123l);

		Entity entity = new Entity(key);
		entity.setProperty("name", "jim");

		datastore.put(entity);

		Entity retrievedEntity = datastore.get(key);
		assertEquals("jim", retrievedEntity.getProperty("name"));
	}

	@Test
	public void testParentKey() {
		Key parentKey = Key.create("companies", 1l);
		Key key = Key.create(parentKey, "people", 1l);

		Entity entity = new Entity(key);
		entity.setProperty("name", "jim");

		datastore.put(entity);

		Entity retrievedEntity = datastore.get(key);
		assertEquals("jim", retrievedEntity.getProperty("name"));

		Key anotherParentKey = Key.create("companies", 2l);
		Key anotherKey = Key.create(anotherParentKey, "people", 1l);
		assertNull(datastore.get(anotherKey));
	}

}
