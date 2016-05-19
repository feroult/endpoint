package io.yawp.repository.models;

import io.yawp.commons.utils.ReflectionUtils;
import io.yawp.repository.IdRef;
import io.yawp.repository.LazyJson;
import io.yawp.repository.annotations.Id;
import io.yawp.repository.annotations.Index;
import io.yawp.repository.annotations.Json;

import java.beans.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class FieldModel {

    private Field field;

    public FieldModel(Field field) {
        this.field = field;
        field.setAccessible(true);
    }

    public Field getField() {
        return field;
    }

    public String getName() {
        return field.getName();
    }

    public Object getValue(Object object) {
        try {
            return field.get(object);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isId() {
        return field.isAnnotationPresent(Id.class);
    }

    public boolean hasIndex() {
        return field.getAnnotation(Index.class) != null;
    }

    public boolean isIndexNormalizable() {
        if (!hasIndex()) {
            throw new RuntimeException("You must add @Index annotation the the field '" + field.getName()
                    + "' if you want to use it as a index in where statements.");
        }
        return getIndex().normalize() && isString();
    }

    public boolean isEnum(Object value) {
        return value != null && value instanceof Enum<?>;
    }

    public boolean isCollection(Object value) {
        return Collection.class.isInstance(value);
    }

    public boolean isEnum() {
        return Enum.class.isAssignableFrom(field.getType());
    }

    public boolean isIdRef() {
        return IdRef.class.isAssignableFrom(field.getType());
    }

    public boolean isCollection() {
        return Collection.class.isAssignableFrom(field.getType());
    }

    public boolean isSaveAsJson() {
        return field.getAnnotation(Json.class) != null;
    }

    public boolean isSaveAsText() {
        return field.getAnnotation(io.yawp.repository.annotations.Text.class) != null;
    }
    
    public boolean isSaveAsLazyJson() {
		return LazyJson.class.isAssignableFrom(field.getType());
	}

    private Index getIndex() {
        return field.getAnnotation(Index.class);
    }

    public boolean isNumber() {
        if (Number.class.isAssignableFrom(field.getType())) {
            return true;
        }
        String name = field.getType().getName();
        return name.equals("int") || name.equals("long") || name.equals("double");
    }

    public boolean isInt() {
        return Integer.class.isAssignableFrom(field.getType()) || field.getType().getName().equals("int");
    }

    public boolean isLong() {
        return Long.class.isAssignableFrom(field.getType()) || field.getType().getName().equals("long");
    }

    public boolean isDate() {
        return Date.class.isAssignableFrom(field.getType());
    }

    private boolean isString() {
        return String.class.isAssignableFrom(field.getType());
    }

    public boolean isList() {
        return List.class.isAssignableFrom(field.getType());
    }

    public boolean isListOfIds() {
        if (!isList()) {
            return false;
        }

        Class<?> listGenericClazz = ReflectionUtils.getListGenericType(field.getGenericType());
        return IdRef.class.isAssignableFrom(listGenericClazz);
    }

    public boolean isTransient() {
        return Modifier.isTransient(field.getModifiers()) || field.isAnnotationPresent(Transient.class);
    }

}
