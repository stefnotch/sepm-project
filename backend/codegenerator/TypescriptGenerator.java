package at.ac.tuwien.sepm.groupphase.backend.codegenerator;


import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates Typescript code from reflected Java objects.
 */
public class TypescriptGenerator {
    private final Map<String, TsInterface> definedInterfaces = new HashMap<>();
    private final Map<String, TsType> definedTypes = new HashMap<>();
    private final String codePackage;
    private final List<String> topLevelPackages;

    /**
     * Creates a new {@link TypescriptGenerator}.
     *
     * @param codePackage      the package where the user's code lives in.
     * @param topLevelPackages the package parts which should be excluded from the output.
     */
    public TypescriptGenerator(String codePackage, List<String> topLevelPackages) {
        this.codePackage = codePackage;
        this.topLevelPackages = topLevelPackages;
    }

    /**
     * Turns a camelCase or PascalCase String into a snake-case String.
     *
     * @param name the camelCase or PascalCase String.
     * @return a snake-case String.
     */
    public static String camelCaseToSnakeCase(String name) {
        return name.replaceAll("([a-z])([A-Z])", "$1-$2")
            .toLowerCase(Locale.ROOT);
    }

    /**
     * Removes a suffix from a String. Strings that do not have the suffix are unchanged.
     *
     * @param s      the String that might have a given suffix.
     * @param suffix the suffix.
     * @return the String without the suffix.
     */
    public static String withoutSuffix(String s, String suffix) {
        int suffixIndex = s.lastIndexOf(suffix);
        if (suffixIndex < 0) {
            throw new IllegalArgumentException();
        }

        return s.substring(0, suffixIndex);
    }

    public TsValue addValue(Type typeClass) {
        if (typeClass == Long.class || typeClass == Integer.class || typeClass == Double.class) {
            // Maybe add an "or null"
            return new TsPrimitive("number");
        } else if (typeClass == long.class || typeClass == int.class || typeClass == double.class) {
            return new TsPrimitive("number");
        } else if (typeClass == LocalDate.class) {
            return addType("IsoDateString", "string");
        } else if (typeClass == LocalDateTime.class) {
            return addType("IsoDateTimeString", "string");
        } else if (typeClass == String.class) {
            return new TsPrimitive("string");
        } else if (typeClass == Boolean.class) {
            return new TsPrimitive("boolean | null");
        } else if (typeClass == boolean.class) {
            return new TsPrimitive("boolean");
        } else if (typeClass == void.class) {
            return new TsPrimitive("void");
        } else if (typeClass == UUID.class) {
            return new TsPrimitive("string");
        } else if (typeClass instanceof ParameterizedType paramClass) {
            if (paramClass.getRawType() instanceof Class<?> paramRawClass) {
                if ((List.class).isAssignableFrom(paramRawClass) || (Set.class).isAssignableFrom(paramRawClass)) {
                    return new TsArray(addValue(paramClass.getActualTypeArguments()[0]));
                } else if (isInPackage(paramRawClass)) {
                    var tsInterface = addDto(paramRawClass);

                    var genericTypeArguments = paramClass.getActualTypeArguments();
                    var actualTypes = Arrays.stream(genericTypeArguments)
                        .map(this::addValue)
                        .toList();

                    return new TsInterfaceInstance(tsInterface, actualTypes);
                }
            }
        } else if (typeClass instanceof Class<?> classClass) {
            if (classClass.isEnum()) {
                String enumOptions = Arrays.stream(classClass.getEnumConstants())
                    .map(v -> '"' + v.toString() + '"')
                    .collect(Collectors.joining(" | "));
                // TODO: Generate an array with all the enum values
                // TODO: For that, I gotta change the design to be "file > TsValues"
                return addType(classClass.getSimpleName(), enumOptions);
            } else if (isInPackage(classClass)) {
                return addDto(classClass);
            }
        } else if (typeClass instanceof TypeVariable<?> typeVariable) {
            // Not quite as bulletproof as it could be, it basically trusts that the source code is sane.
            return new TsPrimitive(typeVariable.getName());
        }

        return new TsPrimitive("any");
    }

    public <T> TsInterface addDto(Class<T> typeClass) {
        var key = typeClass.getCanonicalName();
        // TODO: Handle classes that cyclically reference each other. (Or even a class that references itself.)
        if (definedInterfaces.containsKey(key)) {
            return definedInterfaces.get(key);
        } else {
            var tsInterface = TsInterface.from(typeClass, this);
            definedInterfaces.put(key, tsInterface);
            return tsInterface;
        }
    }

    private TsType addType(String name, String type) {
        return definedTypes.computeIfAbsent(name, (v) -> new TsType(name, type));
    }

    public List<TsInterface> getInterfaces() {
        return definedInterfaces.values()
            .stream()
            .sorted(Comparator.comparing(TsInterface::getName))
            .toList();
    }

    public List<TsType> getTypes() {
        return definedTypes.values()
            .stream()
            .sorted(Comparator.comparing(TsType::getName))
            .toList();
    }

    /**
     * Removes a package name from another one.
     * e.g. Removing "at.ac" from "at.ac.example.Example" yields "example.Example"
     */
    public String withoutTopLevelPackages(String packageName) {
        String bestMatch = this.topLevelPackages.stream()
            .filter(packageName::startsWith)
            .max(Comparator.comparingInt(String::length))
            .orElse("");
        String trimmedPackageName = packageName.substring(bestMatch.length());
        // Remove the leading dot
        return trimmedPackageName.substring(trimmedPackageName.startsWith(".") ? 1 : 0);
    }

    public boolean isInPackage(Class<?> typeClass) {
        return typeClass.getPackageName()
            .startsWith(codePackage);
    }

    public abstract static class TsValue {
        /**
         * List of things which need to be imported to use this.
         * Can include itself, for example to use an interface, one needs to import the interface.
         */
        public abstract List<TsValue> getRequiredImports();

        /**
         * List of things which the code needs to import.
         */
        public abstract List<TsValue> getCodeImports();

        /**
         * Gets the 'path' to the file where the code should be written, corresponds to Java packages.
         */
        public abstract List<String> getPath();

        /**
         * Name of the Typescript thing.
         */
        public abstract String getName();

        /**
         * Code that defines the Typescript thing.
         */
        public abstract CodeWriter getCode();
    }

    public static class TsArray extends TsValue {
        private final TsValue value;

        public TsArray(TsValue value) {
            this.value = value;
        }

        @Override
        public List<TsValue> getRequiredImports() {
            return value.getRequiredImports();
        }

        @Override
        public List<TsValue> getCodeImports() {
            return List.of();
        }

        @Override
        public List<String> getPath() {
            return new ArrayList<>();
        }

        @Override
        public String getName() {
            return value.getName() + "[]";
        }

        @Override
        public CodeWriter getCode() {
            return new CodeWriter();
        }
    }

    public static class TsType extends TsValue {
        private final String name;
        private final String type;

        public TsType(String name, String type) {
            this.name = name;
            this.type = type;
        }

        @Override
        public List<TsValue> getRequiredImports() {
            return List.of(this);
        }

        @Override
        public List<TsValue> getCodeImports() {
            return List.of();
        }

        @Override
        public List<String> getPath() {
            return new ArrayList<>();
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public CodeWriter getCode() {
            var codeWriter = new CodeWriter();
            codeWriter.writeLine("type " + name + " = " + type + ";");
            return codeWriter;
        }

    }

    public static class TsPrimitive extends TsValue {
        private final String name;

        public TsPrimitive(String name) {
            this.name = name;
        }

        @Override
        public List<TsValue> getRequiredImports() {
            return List.of();
        }

        @Override
        public List<TsValue> getCodeImports() {
            return List.of();
        }

        @Override
        public List<String> getPath() {
            return new ArrayList<>();
        }

        @Override
        public String getName() {
            return this.name;
        }

        @Override
        public CodeWriter getCode() {
            return new CodeWriter();
        }
    }

    public static class TsInterface extends TsValue {
        private String name;
        private final List<TsProperty> properties = new ArrayList<>();
        private final List<TsGeneric> generics = new ArrayList<>();
        private final List<String> path = new ArrayList<>();

        public static <T> TsInterface from(Class<T> typeClass, TypescriptGenerator gen) {
            var value = new TsInterface();
            value.name = TypescriptGenerator.withoutSuffix(typeClass.getSimpleName(), "Dto");
            String packageName = gen.withoutTopLevelPackages(typeClass.getPackageName());
            value.path.addAll(List.of(StringUtils.split(packageName, ".")));


            var genericTypeParameters = typeClass.getTypeParameters();
            for (TypeVariable<Class<T>> genericTypeParameter : genericTypeParameters) {
                value.generics.add(new TsGeneric(genericTypeParameter.getName()));
            }

            if (typeClass.isRecord()) {
                var fields = typeClass.getRecordComponents();
                for (RecordComponent field : fields) {

                    // Something like this would correctly model Java:
                    // boolean nullable = !field.isAnnotationPresent(NonNull.class); // or if its a primitive..
                    // But null is a mistake, so we assume that everything is NonNull unless it has a @Nullable annotation

                    // TODO: This is a hack, but it works for now. Java Records and Java Spring are a cursed mess.
                    var annotations = field.getAccessor()
                        .getAnnotations();

                    boolean isOptional = Arrays.stream(annotations)
                        .anyMatch(v -> v.annotationType()
                            .getName()
                            .contains("Nullable"));
                    var fieldType = field.getGenericType();
                    if (fieldType instanceof TypeVariable<?>) {
                        // TODO:
                        throw new UnsupportedOperationException("TODO: Implement interfaces with a generic type directly being used");
                        // e.g. interface MyInterface<T> { T value; }
                    }
                    value.properties.add(new TsProperty(field.getName(), gen.addValue(fieldType), isOptional));
                }
            } else {
                var x = typeClass.getDeclaredFields();
                var xx = x[0].getAnnotations();
            }
            return value;
        }

        /**
         * List of generics which the interface has.
         */
        public List<TsGeneric> getGenerics() {
            return this.generics;
        }

        @Override
        public List<TsValue> getRequiredImports() {
            return List.of(this);
        }

        @Override
        public List<TsValue> getCodeImports() {
            return properties.stream()
                .map(TsProperty::getValue)
                .flatMap(v -> v.getRequiredImports()
                    .stream())
                .distinct()
                .toList();
        }

        @Override
        public List<String> getPath() {
            return this.path.stream()
                .toList();
        }

        @Override
        public String getName() {
            return this.name;
        }

        public List<TsProperty> getProperties() {
            return properties;
        }

        @Override
        public CodeWriter getCode() {
            var codeWriter = new CodeWriter();
            String interfaceName = this.name;
            if (!this.generics.isEmpty()) {
                interfaceName += "<";
                interfaceName += this.generics.stream()
                    .map(TsGeneric::getName)
                    .collect(Collectors.joining(","));
                interfaceName += ">";
            }
            codeWriter.writeLine("interface " + interfaceName + " {");
            codeWriter.beginIndent();
            properties.forEach(v -> codeWriter.writeLine(v.toCode() + ";"));
            codeWriter.endIndent();
            codeWriter.writeLine("}");
            return codeWriter;
        }
    }

    public static class TsInterfaceInstance extends TsValue {
        private final TsInterface value;
        private final List<TsValue> actualTypes;

        public TsInterfaceInstance(TsInterface value, List<TsValue> actualTypes) {
            this.value = value;
            this.actualTypes = actualTypes;
            if (this.value.getGenerics()
                .size() != actualTypes.size()) {
                throw new IllegalArgumentException("The number of generics does not match the number of actual types");
            }
        }

        @Override
        public List<TsValue> getRequiredImports() {
            return Stream.concat(value.getRequiredImports()
                    .stream(), actualTypes.stream())
                .distinct()
                .collect(Collectors.toList());
        }

        @Override
        public List<TsValue> getCodeImports() {
            return value.getCodeImports();
        }


        @Override
        public List<String> getPath() {
            return value.getPath();
        }

        @Override
        public String getName() {
            return value.getName() + "<" + this.actualTypes.stream()
                .map(TsValue::getName)
                .collect(Collectors.joining(",")) + ">";
        }

        @Override
        public CodeWriter getCode() {
            return value.getCode();
        }
    }

    public static class TsMethod extends TsValue {

        private final boolean isAsync;

        private final String name;

        private final List<TsProperty> parameters;

        private final TsValue returnType;

        private final CodeWriter body;

        public TsMethod(boolean isAsync, String name, List<TsProperty> parameters, TsValue returnType, CodeWriter body) {
            this.isAsync = isAsync;
            this.name = name;
            this.parameters = parameters;
            this.returnType = returnType;
            this.body = body;
        }

        @Override
        public List<TsValue> getRequiredImports() {
            return List.of();
        }

        @Override
        public List<TsValue> getCodeImports() {
            return List.of();
        }

        @Override
        public List<String> getPath() {
            return new ArrayList<>();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public CodeWriter getCode() {
            var codeWriter = new CodeWriter();
            codeWriter.writeLine(
                isAsync ? "async " : "",
                "function ", name,
                "(",
                parameters.stream()
                    .map(TsProperty::toCode)
                    .collect(Collectors.joining(", ")),
                ")",
                ": " + (isAsync ? "Promise<" : "") + returnType.getName() + (isAsync ? ">" : ""),
                " {");
            codeWriter.beginIndent();
            codeWriter.writeLines(body);
            codeWriter.endIndent();
            codeWriter.writeLine("}");
            return codeWriter;
        }
    }

    public static class TsProperty {
        private final String name;
        private final TsValue value;
        private final boolean isOptional;

        public TsProperty(
            String name,
            TsValue value,
            boolean isOptional) {
            this.name = name;
            this.value = value;
            this.isOptional = isOptional;
        }

        public String toCode() {
            return name + (isOptional ? "?" : "") + ": " + value.getName();
        }

        public TsValue getValue() {
            return value;
        }

        public String getName() {
            return this.name;
        }
    }

    /**
     * Represents a generic type that is not yet known.
     * The name is merely for decoration, and two different TsGenericType instances can have the same name.
     */
    public static class TsGeneric {
        private final String name;

        public TsGeneric(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
