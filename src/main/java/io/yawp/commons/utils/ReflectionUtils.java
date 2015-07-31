package io.yawp.commons.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ReflectionUtils {

	private ReflectionUtils() {
		throw new RuntimeException("Should not be instanciated");
	}

	public static List<Class<?>> getAllInterfaces(Class<?> clazz) {
		List<Class<?>> interfaces = new ArrayList<>();

		while (clazz != null) {
			interfaces.addAll(Arrays.asList(clazz.getInterfaces()));
			clazz = clazz.getSuperclass();
		}

		return interfaces;
	}

	public static boolean isInnerClass(Class<?> clazz) {
		return clazz.getEnclosingClass() != null && !Modifier.isStatic(clazz.getModifiers());
	}

	public static List<Field> getImmediateFields(Class<?> clazz) {
		List<Field> finalFields = new ArrayList<>();
		for (Field field : clazz.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
				finalFields.add(field);
			}
		}
		return finalFields;
	}

	public static Object getFieldValue(Object object, String fieldName) {
		try {
			Class<?> clazz = object.getClass();
			Field field = getFieldRecursively(clazz, fieldName);
			boolean accessible = field.isAccessible();
			field.setAccessible(true);
			Object value = field.get(object);
			field.setAccessible(accessible);
			return value;
		} catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Field getFieldRecursively(Class<?> clazz, String fieldName) {
		Class<?> baseClazz = clazz;
		while (clazz != null) {
			try {
				return clazz.getDeclaredField(fieldName);
			} catch (NoSuchFieldException ex) {
				clazz = clazz.getSuperclass();
			}
		}
		throw new RuntimeException("Field '" + fieldName + "'not found in entity " + baseClazz, new NoSuchFieldException(fieldName));
	}

	public static List<Field> getFieldsRecursively(Class<?> clazz) {
		List<Field> fields = new ArrayList<>();
		while (!isJavaClass(clazz)) {
			fields.addAll(ReflectionUtils.getImmediateFields(clazz));
			clazz = clazz.getSuperclass();
		}
		return fields;
	}

	public static boolean isBaseClass(Class<?> clazz) {
		return Object.class.equals(clazz) || clazz.isPrimitive() || clazz.isEnum() || clazz.isArray();
	}

	public static boolean isJavaClass(Class<?> clazz) {
		return isBaseClass(clazz) || clazz.getPackage().getName().startsWith("java.") || clazz.getPackage().getName().startsWith("javax.");
	}

	public static Class<?> getGenericParameter(Class<?> clazz) {
		Class<?>[] parameters = getGenericParameters(clazz);
		if (parameters.length == 0) {
			return null;
		}
		return parameters[0];
	}

	public static Class<?>[] getGenericParameters(Class<?> clazz) {
		Type genericFieldType = clazz.getGenericSuperclass();
		if (genericFieldType instanceof ParameterizedType) {
			ParameterizedType aType = (ParameterizedType) genericFieldType;
			Type[] fieldArgTypes = aType.getActualTypeArguments();
			Class<?>[] clazzes = new Class<?>[fieldArgTypes.length];
			for (int i = 0; i < clazzes.length; i++) {
				clazzes[i] = (Class<?>) fieldArgTypes[i];
			}
			return clazzes;
		}
		return new Class<?>[] {};
	}

}