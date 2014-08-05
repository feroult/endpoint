package endpoint;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Query;

import endpoint.actions.RepositoryActions;
import endpoint.hooks.RepositoryHooks;
import endpoint.response.HttpResponse;
import endpoint.utils.EntityUtils;

public class Repository {

	@SuppressWarnings("unused")
	private static Logger logger = Logger.getLogger(Repository.class.getSimpleName());

	private Namespace namespace;

	public static Repository r() {
		return new Repository();
	}

	public static Repository r(String ns) {
		return new Repository(ns);
	}

	private Repository() {
		this.namespace = new Namespace();
	}

	private Repository(String ns) {
		this.namespace = new Namespace(ns);
	}

	public Repository namespace(String ns) {
		namespace.setNs(ns);
		return this;
	}

	public Namespace namespace() {
		return namespace;
	}

	public String currentNamespace() {
		return namespace.getNs();
	}

	public void save(Object object) {
		namespace.set(object.getClass());
		try {
			RepositoryHooks.beforeSave(this, object);

			Entity entity = createEntity(object);
			EntityUtils.toEntity(object, entity);
			saveEntity(object, entity, null);
			saveLists(object, entity);

			RepositoryHooks.afterSave(this, object);
		} finally {
			namespace.reset();
		}
	}

	public HttpResponse action(Class<?> clazz, String method, String action, Long id, Map<String, String> params) {
		namespace.set(clazz);
		try {
			return RepositoryActions.execute(this, clazz, method, action, id, params);
		} finally {
			namespace.reset();
		}
	}

	public <T> List<T> all(Class<T> clazz) {
		return query(clazz).list();
	}

	public <T> DatastoreQuery<T> query(Class<T> clazz) {
		DatastoreQuery<T> q = DatastoreQuery.q(clazz, this);
		RepositoryHooks.beforeQuery(this, q, clazz);
		return q;
	}

	public void delete(Object object) {
		delete(EntityUtils.getId(object), object.getClass());
	}

	public void delete(Long id, Class<?> clazz) {
		namespace.set(clazz);
		try {
			DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();

			Key key = EntityUtils.createKey(id, clazz);
			datastoreService.delete(key);
			deleteLists(key, clazz);

		} finally {
			namespace.reset();
		}

	}

	private void deleteLists(Key key, Class<?> clazz) {
		Field[] fields = EntityUtils.getFields(clazz);
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			if (!EntityUtils.isSaveAsList(field)) {
				continue;
			}

			field.setAccessible(true);
			deleteChilds(key, EntityUtils.getParametrizedType(field));
		}
	}

	private void saveEntity(Object object, Entity entity, String action) {
		DatastoreService datastoreService = DatastoreServiceFactory.getDatastoreService();
		Key key = datastoreService.put(entity);
		EntityUtils.setKey(object, key);
	}

	@SuppressWarnings("unchecked")
	private void saveLists(Object object, Entity entity) {
		Field[] fields = EntityUtils.getFields(object.getClass());
		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			if (!EntityUtils.isSaveAsList(field)) {
				continue;
			}

			field.setAccessible(true);

			try {
				saveList(EntityUtils.getParametrizedType(field), (List<Object>) field.get(object), object);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void saveList(Class<?> childClazz, List<Object> childs, Object parentObject) {

		deleteChilds(EntityUtils.getKey(parentObject), childClazz);

		if (childs == null) {
			return;
		}

		for (Object child : childs) {
			Entity entity = createEntityForChild(child, parentObject);
			EntityUtils.toEntity(child, entity);
			saveEntity(child, entity, null);
		}
	}

	private void deleteChilds(Key parentKey, Class<?> childClazz) {
		DatastoreService service = DatastoreServiceFactory.getDatastoreService();

		Query query = new Query(EntityUtils.getKind(childClazz));

		query.setAncestor(parentKey);
		query.setKeysOnly();

		Iterable<Entity> childs = service.prepare(query).asIterable();

		for (Entity child : childs) {
			service.delete(child.getKey());
		}

	}

	private Entity createEntity(Object object) {
		Entity entity = null;

		Key currentKey = EntityUtils.getKey(object);

		if (currentKey == null) {
			entity = new Entity(EntityUtils.getKind(object.getClass()));
		} else {
			Key key = KeyFactory.createKey(currentKey.getKind(), currentKey.getId());
			entity = new Entity(key);
		}
		return entity;
	}

	private Entity createEntityForChild(Object object, Object parent) {
		Entity entity = null;

		Key currentKey = EntityUtils.getKey(object);
		if (currentKey == null) {
			entity = new Entity(EntityUtils.getKind(object.getClass()), EntityUtils.getKey(parent));
		} else {
			Key key = KeyFactory.createKey(EntityUtils.getKey(parent), currentKey.getKind(), currentKey.getId());
			entity = new Entity(key);
		}
		return entity;
	}
}
