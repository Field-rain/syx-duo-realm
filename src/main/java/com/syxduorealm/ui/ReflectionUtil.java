package com.syxduorealm.ui;

import java.lang.reflect.Field;
import java.util.Optional;

final class ReflectionUtil {

    private ReflectionUtil() {
    }

    static Optional<Field> findField(String fieldName, Object instance) {
        if (instance == null) {
            return Optional.empty();
        }

        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    static <T> Optional<T> getFieldValue(String fieldName, Object instance) {
        return findField(fieldName, instance).flatMap(field -> {
            try {
                return Optional.ofNullable((T) field.get(instance));
            } catch (IllegalAccessException e) {
                return Optional.empty();
            }
        });
    }
}
