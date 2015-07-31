package io.yawp.commons.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public abstract class FacadeUtils {

	private enum FacadeType {
		SET, GET
	}

	public static <T> void get(T object, Class<? super T> facade) {
		hideProperties(object, notReadable(object.getClass(), facade));
	}

	public static <T> void set(T object, Class<? super T> facade) {
		hideProperties(object, notWriteable(object.getClass(), facade));
	}

	public static <T> void set(T object, T defaults, Class<? super T> facade) {
		assertSameClazz(object, defaults);
		copyProperties(defaults, object, notWriteable(object.getClass(), facade));
	}

	private static void assertSameClazz(Object object, Object defaults) {
		if (!object.getClass().equals(defaults.getClass())) {
			throw new RuntimeException("Objects must have the same class: " + object.getClass().getName() + " != "
					+ defaults.getClass().getName());
		}
	}

	private static void hideProperties(Object object, List<String> properties) {
		Class<?> clazz = object.getClass();
		try {
			for (String property : properties) {
				Field field = ReflectionUtils.getFieldRecursively(clazz, property);
				field.setAccessible(true);
				field.set(object, null);
			}
		} catch (IllegalAccessException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private static void copyProperties(Object from, Object to, List<String> properties) {
		Class<?> clazz = from.getClass();
		try {
			for (String property : properties) {
				Field field = ReflectionUtils.getFieldRecursively(clazz, property);
				field.setAccessible(true);
				field.set(to, field.get(from));
			}
		} catch (IllegalAccessException | SecurityException e) {
			throw new RuntimeException(e);
		}
	}

	private static List<String> notReadable(Class<?> clazz, Class<?> facade) {
		return propetiesNotInFacade(clazz, facade, FacadeType.GET);
	}

	private static List<String> notWriteable(Class<?> clazz, Class<?> facade) {
		return propetiesNotInFacade(clazz, facade, FacadeType.SET);
	}

	private static List<String> facadeProperties(Class<?> facade, FacadeType type) {
		List<String> properties = new ArrayList<String>();

		for (Method attribute : facade.getMethods()) {
			String name = extractNameFromAttribute(facade, attribute, type);
			if (name == null) {
				continue;
			}
			properties.add(name);
		}

		return properties;
	}

	private static List<String> propetiesNotInFacade(Class<?> clazz, Class<?> facade, FacadeType facadeType) {
		List<String> properties = new ArrayList<String>();
		List<Field> fields = ReflectionUtils.getFieldsRecursively(clazz);
		List<String> facadeProperties = facadeProperties(facade, facadeType);
		for (Field field : fields) {
			String name = field.getName();
			if (facadeProperties.contains(name)) {
				continue;
			}
			properties.add(name);
		}
		return properties;
	}

	private static String extractNameFromAttribute(Class<?> facade, Method attribute, FacadeType type) {
		String typePrefix = type.toString().toLowerCase();
		String methodName = attribute.getName();
		if (!methodName.startsWith(typePrefix)) {
			return null;
		}
		String capitalized = methodName.substring(typePrefix.length());
		return Character.toLowerCase(capitalized.charAt(0)) + capitalized.substring(1);
	}

}
