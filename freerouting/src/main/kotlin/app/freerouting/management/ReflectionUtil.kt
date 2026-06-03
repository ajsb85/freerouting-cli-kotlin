package app.freerouting.management

import app.freerouting.logger.FRLogger
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Array as ReflectArray
import java.lang.reflect.Field
import java.lang.reflect.Modifier

object ReflectionUtil {

    @JvmStatic
    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    fun setFieldValue(obj: Any, propertyName: String, newValue: Any?) {
        val propertyPath = propertyName.split("[.:\\-]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        var currentObject = obj
        var field: Field? = null

        // Navigate to the nested object
        for (i in 0 until propertyPath.size - 1) {
            field = getFieldByNameOrSerializedName(currentObject.javaClass, propertyPath[i])
            field.isAccessible = true
            currentObject = field.get(currentObject)
        }

        // Set the final field value
        field = getFieldByNameOrSerializedName(currentObject.javaClass, propertyPath[propertyPath.size - 1])
        field.isAccessible = true
        val convertedValue = convertValue(field.type, newValue)
        field.set(currentObject, convertedValue)
    }

    private fun getFieldByNameOrSerializedName(clazz: Class<*>, name: String): Field {
        for (field in clazz.declaredFields) {
            if (field.name == name) {
                return field
            }
            val annotation = field.getAnnotation(SerializedName::class.java)
            if (annotation != null && annotation.value == name) {
                return field
            }
        }
        throw NoSuchFieldException("No field found with name or SerializedName: $name")
    }

    private fun convertValue(targetType: Class<*>, value: Any?): Any? {
        if (targetType.isInstance(value)) {
            return value
        }
        if (targetType == Int::class.javaPrimitiveType || targetType == java.lang.Integer::class.java) {
            return value.toString().toInt()
        }
        if (targetType == Long::class.javaPrimitiveType || targetType == java.lang.Long::class.java) {
            return value.toString().toLong()
        }
        if (targetType == Double::class.javaPrimitiveType || targetType == java.lang.Double::class.java) {
            return value.toString().toDouble()
        }
        if (targetType == Boolean::class.javaPrimitiveType || targetType == java.lang.Boolean::class.java) {
            var strValue = value.toString()
            // convert "0" and "1" into their boolean values
            if ("0" == strValue) {
                strValue = "false"
            } else if ("1" == strValue) {
                strValue = "true"
            }
            return strValue.toBoolean()
        }
        if (targetType == Array<String>::class.java) {
            // Parse a comma-separated string into a String array.
            val raw = value.toString().trim()
            if (raw.isEmpty()) {
                return emptyArray<String>()
            }
            val tokens = raw.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (i in tokens.indices) {
                tokens[i] = tokens[i].trim()
            }
            return tokens
        }
        // Add more type conversions as needed
        return value
    }

    /**
     * Copy all non-null, and non-default fields from one object to another
     * recursively
     *
     * @param source The source object
     * @param target The target object
     * @return The number of fields that were copied
     */
    @Suppress("UNCHECKED_CAST")
    @JvmStatic
    fun copyFields(source: Any, target: Any): Int {
        var numberOfFieldsChanged = 0

        for (field in source.javaClass.declaredFields) {
            try {
                // check if the field is static and skip it if it is
                if (Modifier.isStatic(field.modifiers)) {
                    continue
                }

                // check if the field is private and skip it if it is
                if (!Modifier.isPublic(field.modifiers)) {
                    continue
                }

                field.isAccessible = true
                val sourceValue = field.get(source)

                // For nullable wrappers (Boolean/Integer/...), non-null already means "explicitly set".
                // Keep default-value suppression only for primitive fields.
                var shouldCopy = sourceValue != null
                if (shouldCopy && field.type.isPrimitive) {
                    shouldCopy = sourceValue != getDefaultValue(field)
                }

                if (shouldCopy) {
                    // Check if the field is a primitive, wrapper type, or a string
                    if (field.type.isPrimitive
                        || field.type == String::class.java
                        || field.type == java.lang.Integer::class.java
                        || field.type == java.lang.Long::class.java
                        || field.type == java.lang.Float::class.java
                        || field.type == java.lang.Double::class.java
                        || field.type == java.lang.Boolean::class.java
                        || field.type == java.lang.Byte::class.java
                        || field.type == java.lang.Short::class.java
                        || field.type == java.lang.Character::class.java
                    ) {
                        // check if the target field is null or its default value
                        val targetValue = field.get(target)

                        if (targetValue != sourceValue) {
                            field.set(target, sourceValue)
                            numberOfFieldsChanged++
                        }
                    } else if (field.type.isEnum) {
                        val enumType = field.type as Class<out Enum<*>>
                        val enumValue = java.lang.Enum.valueOf(enumType, sourceValue.toString())

                        // Copy the enum value
                        field.set(target, enumValue)
                        numberOfFieldsChanged++
                    } else if (field.type.isArray) {
                        // Is the array of primitive types or strings?
                        if (field.type.componentType.isPrimitive || field.type.componentType == String::class.java) {
                            val targetValue = field.get(target)

                            var targetArrayLength = 0
                            if (targetValue != null && targetValue.javaClass.isArray) {
                                targetArrayLength = ReflectArray.getLength(targetValue)
                            }

                            var sourceArrayLength = 0
                            if (sourceValue != null && sourceValue.javaClass.isArray) {
                                sourceArrayLength = ReflectArray.getLength(sourceValue)
                            }

                            // Check if the target field is null or its length is 0
                            if (targetValue == null || (targetArrayLength == 0 && sourceArrayLength > 0)) {
                                // The field is an array of primitive types or strings, so we can copy it directly
                                field.set(target, sourceValue)
                                numberOfFieldsChanged++
                            }
                        } else {
                            // The field is an array, so we need to copy its elements
                            val sourceArray = sourceValue as Array<Any?>
                            var targetArray = field.get(target) as Array<Any?>?
                            if (targetArray == null) {
                                targetArray = ReflectArray.newInstance(field.type.componentType, sourceArray.size) as Array<Any?>
                                field.set(target, targetArray)
                            }
                            System.arraycopy(sourceArray, 0, targetArray, 0, sourceArray.size)
                            numberOfFieldsChanged += sourceArray.size
                        }
                    } else {
                        // The field is an object, so we need to copy its fields
                        var targetField = field.get(target)
                        if (targetField == null) {
                            targetField = field.type.getDeclaredConstructor().newInstance()
                            field.set(target, targetField)
                        }
                        numberOfFieldsChanged += copyFields(sourceValue, targetField)
                    }
                }
            } catch (e: Exception) {
                FRLogger.error("Error copying fields", e)
            }
        }

        return numberOfFieldsChanged
    }

    private fun getDefaultValue(field: Field): Any? {
        var result: Any? = null

        try {
            result = field.type.getConstructor().newInstance()
        } catch (e: NoSuchMethodException) {
            // The field does not have a default constructor, this can usually the case if the type is a primitive type
            val type = field.type
            if (type == Int::class.javaPrimitiveType || type == java.lang.Integer::class.java) {
                result = 0
            } else if (type == Long::class.javaPrimitiveType || type == java.lang.Long::class.java) {
                result = 0L
            } else if (type == Float::class.javaPrimitiveType || type == java.lang.Float::class.java) {
                result = 0.0f
            } else if (type == Double::class.javaPrimitiveType || type == java.lang.Double::class.java) {
                result = 0.0
            } else if (type == Boolean::class.javaPrimitiveType || type == java.lang.Boolean::class.java) {
                result = false
            } else if (type.isArray) {
                // create an empty array of the original type
                result = ReflectArray.newInstance(type.componentType, 0)
            } else if (type.isEnum) {
                result = type.enumConstants[0]
            } else if (Modifier.isTransient(field.modifiers)) {
                result = null
            } else {
                FRLogger.debug("No default constructor found for field: " + field.name)
            }
        } catch (e: Exception) {
            FRLogger.error("Error getting default value for field: " + field.name, e)
        }

        return result
    }
}
