package endpoint.utils;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Text;

import endpoint.Id;
import endpoint.IdRef;
import endpoint.Index;
import endpoint.Json;
import endpoint.Repository;

// TODO make it not static and repository aware
public class EntityUtils {

	private static final String NORMALIZED_FIELD_PREFIX = "__";

	public static String getKind(Class<?> clazz) {
		return clazz.getSimpleName();
	}

	public static void toEntity(Object object, Entity entity) {
		Field[] fields = getFields(object.getClass());

		for (int i = 0; i < fields.length; i++) {
			Field field = fields[i];
			if (isControl(field)) {
				continue;
			}

			if (isSaveAsList(field)) {
				continue;
			}

			setEntityProperty(object, entity, field);
		}
	}

	public static <T> T toObject(Repository r, Entity entity, Class<T> clazz) {
		try {
			T object = clazz.newInstance();

			setKey(r, object, entity.getKey());

			Field[] fields = getFields(clazz);

			for (int i = 0; i < fields.length; i++) {
				Field field = fields[i];
				if (isControl(field)) {
					continue;
				}

				if (isSaveAsList(field)) {
					continue;
				}

				setObjectProperty(r, object, entity, field);
			}

			return object;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static <T> void setKey(Repository r, T object, Key key) {
		try {
			Field field = getIdField(object.getClass());
			field.setAccessible(true);

			if (!isIdRef(field)) {
				field.set(object, key.getId());
			} else {
				field.set(object, IdRef.create(r, getIdFieldRefClazz(object.getClass()), key.getId()));
			}

		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static Key getKey(Object object) {
		try {
			Field field = getIdField(object.getClass());

			field.setAccessible(true);
			if (field.get(object) == null) {
				return null;
			}

			return createKeyFromIdField(object, field);

		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	private static Key createKeyFromIdField(Object object, Field field) throws IllegalAccessException {
		Long id = null;

		if (!isIdRef(field)) {
			id = (Long) field.get(object);
		} else {
			id = ((IdRef<?>) field.get(object)).asLong();
		}

		return createKey(id, object.getClass());
	}

	public static String getIdFieldName(Class<?> clazz) {
		return getIdField(clazz).getName();
	}

	public static Class<?> getIdFieldRefClazz(Class<?> clazz) {
		Field idField = getIdField(clazz);
		return (Class<?>) getParametrizedTypes(idField)[0];
	}

	private static Field getIdField(Class<?> clazz) {
		Field field = getAnnotatedIdFromClass(clazz);

		if (field == null) {
			field = getKeyFieldFromClass(clazz);
			if (field == null) {
				throw new RuntimeException("No @Id annotated field found in class " + clazz.getSimpleName());
			}
		}
		return field;
	}

	private static Field getAnnotatedIdFromClass(Class<?> clazz) {
		for (Field field : ReflectionUtils.getFieldsRecursively(clazz)) {
			if (field.isAnnotationPresent(Id.class)) {
				return field;
			}
		}
		return null;
	}

	private static Field getKeyFieldFromClass(Class<?> clazz) {
		for (Field field : ReflectionUtils.getFieldsRecursively(clazz)) {
			if (Key.class.isAssignableFrom(field.getType())) {
				return field;
			}
		}
		return null;
	}

	public static Long getId(Object object) {
		return getKey(object).getId();
	}

	public static <T> Field[] getFields(Class<T> clazz) {
		Field[] allFields = ArrayUtils.addAll(Object.class.getDeclaredFields(), clazz.getDeclaredFields());

		List<Field> fields = new ArrayList<Field>();

		for (int i = 0; i < allFields.length; i++) {
			Field field = allFields[i];
			if (Modifier.isStatic(field.getModifiers())) {
				continue;
			}

			fields.add(field);
		}

		return fields.toArray(new Field[fields.size()]);
	}

	public static Class<?> getListType(Field field) {
		return (Class<?>) getParametrizedTypes(field)[0];
	}

	private static Type[] getParametrizedTypes(Field field) {
		Type genericFieldType = field.getGenericType();
		if (genericFieldType instanceof ParameterizedType) {
			ParameterizedType aType = (ParameterizedType) genericFieldType;
			Type[] fieldArgTypes = aType.getActualTypeArguments();
			return fieldArgTypes;
		}

		throw new RuntimeException("can't get generic type");
	}

	private static Field getFieldFromAnyParent(Class<?> clazz, String fieldName) {
		while (clazz != null) {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException ex) {
				clazz = clazz.getSuperclass();
			}
		}

		throw new RuntimeException("Field '" + fieldName + "'not found in entity " + clazz, new NoSuchFieldException(fieldName));
	}

	public static <T> String getActualFieldName(String fieldName, Class<T> clazz) {
		Field field = getFieldFromAnyParent(clazz, fieldName);

		if (isKey(field)) {
			return Entity.KEY_RESERVED_PROPERTY;
		}

		if (isIndexNormalizable(field)) {
			return NORMALIZED_FIELD_PREFIX + fieldName;
		}

		return fieldName;
	}

	private static boolean isKey(Field field) {
		return field.getAnnotation(Id.class) != null || field.getType().equals(Key.class);
	}

	private static Index getIndex(Field field) {
		Index index = field.getAnnotation(Index.class);
		if (index == null) {
			throw new RuntimeException("You must add @Index annotation the the field '" + field.getName()
					+ "' if you want to use it as a index in where statements.");
		}
		return index;
	}

	public static <T> Object getActualFieldValue(String fieldName, Class<T> clazz, Object value) {
		Field field = getFieldFromAnyParent(clazz, fieldName);

		if (isCollection(value)) {
			return getActualListFieldValue(fieldName, clazz, (Collection<?>) value);
		}

		if (isKey(field)) {
			return getActualKeyFieldValue(clazz, value);
		}

		if (isEnum(value)) {
			return value.toString();
		}

		if (isIndexNormalizable(field)) {
			return normalizeValue(value);
		}

		if (value instanceof IdRef) {
			return ((IdRef<?>) value).asLong();
		}

		if (isDate(field) && value instanceof String) {
			return DateUtils.toTimestamp((String) value);
		}

		return value;
	}

	private static boolean isCollection(Object value) {
		return Collection.class.isInstance(value);
	}

	private static <T> Object getActualListFieldValue(String fieldName, Class<T> clazz, Collection<?> value) {
		Collection<?> objects = (Collection<?>) value;
		List<Object> values = new ArrayList<Object>();
		for (Object obj : objects) {
			values.add(getActualFieldValue(fieldName, clazz, obj));
		}
		return values;
	}

	private static <T> Object getActualKeyFieldValue(Class<T> clazz, Object value) {
		if (value instanceof Key) {
			return value;
		}

		Long id;
		if (value instanceof Long) {
			id = (Long) value;
		} else if (value instanceof IdRef) {
			id = ((IdRef<?>) value).asLong();
		} else {
			id = Long.parseLong(value.toString());
		}

		return createKey(id, clazz);
	}

	public static Key createKey(Long id, Class<?> clazz) {
		return KeyFactory.createKey(getKind(clazz), id);
	}

	private static void setEntityProperty(Object object, Entity entity, Field field) {
		Object value = getFieldValue(field, object);

		if (!hasIndex(field)) {
			entity.setUnindexedProperty(field.getName(), value);
			return;
		}

		if (isIndexNormalizable(field)) {
			entity.setProperty(NORMALIZED_FIELD_PREFIX + field.getName(), normalizeValue(value));
			entity.setUnindexedProperty(field.getName(), value);
			return;
		}

		entity.setProperty(field.getName(), value);
	}

	private static Object normalizeValue(Object o) {
		if (o == null) {
			return null;
		}

		if (!o.getClass().equals(String.class)) {
			return o;
		}

		return StringUtils.stripAccents((String) o).toLowerCase();
	}

	private static Object getFieldValue(Field field, Object object) {
		try {
			field.setAccessible(true);
			Object value = field.get(object);

			if (value == null) {
				return null;
			}

			if (isEnum(value)) {
				return value.toString();
			}

			if (isSaveAsJson(field)) {
				return new Text(JsonUtils.to(value));
			}

			if (isIdRef(field)) {
				IdRef<?> idRef = (IdRef<?>) value;
				return idRef.asLong();
			}

			return value;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static Object getter(Object o, String property) {
		try {
			if (Map.class.isInstance(o)) {
				return ((Map<?, ?>) o).get(property);
			}

			return new PropertyDescriptor(property, o.getClass()).getReadMethod().invoke(o);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | IntrospectionException e) {
			throw new RuntimeException(e);
		}
	}

	private static <T> void setObjectProperty(Repository r, T object, Entity entity, Field field) throws IllegalAccessException {
		field.setAccessible(true);

		Object value = entity.getProperty(field.getName());

		if (value == null) {
			field.set(object, null);
			return;
		}

		if (isEnum(field)) {
			setEnumProperty(object, field, value);
			return;
		}

		if (isSaveAsJson(field)) {
			setJsonProperty(r, object, field, value);
			return;
		}

		if (isInt(field)) {
			setIntProperty(object, field, value);
			return;
		}

		if (isIdRef(field)) {
			setIdRefProperty(r, object, field, value);
			return;
		}

		field.set(object, value);
	}

	private static <T> void setIdRefProperty(Repository r, T object, Field field, Object value) throws IllegalAccessException {
		IdRef<?> idRef = IdRef.create(r, getListType(field), (Long) value);
		field.set(object, idRef);
	}

	private static <T> void setIntProperty(T object, Field field, Object value) throws IllegalAccessException {
		field.set(object, ((Long) value).intValue());
	}

	private static <T> void setJsonProperty(Repository r, T object, Field field, Object value) throws IllegalAccessException {
		if (value == null) {
			return;
		}

		String json = ((Text) value).getValue();
		field.set(object, JsonUtils.from(r, json, field.getGenericType()));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <T> void setEnumProperty(T object, Field field, Object value) throws IllegalAccessException {
		if (value == null) {
			return;
		}

		field.set(object, Enum.valueOf((Class) field.getType(), value.toString()));
	}

	private static boolean hasIndex(Field field) {
		return field.getAnnotation(Index.class) != null;
	}

	private static boolean isIndexNormalizable(Field field) {
		return getIndex(field).normalize() && isString(field);
	}

	private static boolean isControl(Field field) {
		return Key.class.equals(field.getType()) || field.isAnnotationPresent(Id.class) || field.isSynthetic();
	}

	private static boolean isIdRef(Field field) {
		return IdRef.class.isAssignableFrom(field.getType());
	}

	private static boolean isSaveAsJson(Field field) {
		return field.getAnnotation(Json.class) != null;
	}

	public static boolean isSaveAsList(Field field) {
		return isList(field) && !isSaveAsJson(field);
	}

	private static boolean isList(Field field) {
		return List.class.isAssignableFrom(field.getType());
	}

	private static boolean isString(Field field) {
		return String.class.isAssignableFrom(field.getType());
	}

	private static boolean isDate(Field field) {
		return Date.class.isAssignableFrom(field.getType());
	}

	private static boolean isEnum(Object value) {
		return value != null && value.getClass().isEnum();
	}

	private static boolean isEnum(Field field) {
		return field.getType().isEnum();
	}

	private static boolean isInt(Field field) {
		return Integer.class.isAssignableFrom(field.getType()) || field.getType().getName().equals("int");
	}
}
