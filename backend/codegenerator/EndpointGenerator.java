package at.ac.tuwien.sepm.groupphase.backend.codegenerator;

import at.ac.tuwien.sepm.groupphase.backend.endpoint.ArtistEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.CartEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.EventEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.EventShowEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.ImageMediaEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.InvoiceEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.LocationEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.MerchandiseProductEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.MerchandisePurchaseEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.NewsEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.OrderEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.PasswordResetEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.SeatingPlanEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.TicketEndpoint;
import at.ac.tuwien.sepm.groupphase.backend.endpoint.UserEndpoint;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Takes a list of REST endpoints and generates Typescript code from them.
 * Is designed to be mostly a standalone tool, with as few Spring dependencies as possible.
 *
 * <p>This includes generating one service per endpoint and all the DTO interfaces.</p>
 */
public class EndpointGenerator {

    /**
     * Output base path, basically all outputted files will end up in this directory or one of
     * its subdirectories.
     */
    public static final Path basePath = Path.of("../frontend/src");

    public static void main(String[] args) throws IOException {
        if (!Files.isDirectory(basePath)) {
            throw new FileNotFoundException("Expected basePath to exist: " + basePath.toAbsolutePath()
                .normalize());
        }

        var generator = new TypescriptGenerator("at.ac.tuwien", List.of("at.ac.tuwien.sepm.groupphase.backend.endpoint.dto"));
        var outputFiles = new ArrayList<OutputFile>();
        outputFiles.addAll(generateForEndpoint(ArtistEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(EventEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(EventShowEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(InvoiceEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(LocationEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(OrderEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(SeatingPlanEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(TicketEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(UserEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(ImageMediaEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(CartEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(MerchandiseProductEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(MerchandisePurchaseEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(NewsEndpoint.class, generator));
        outputFiles.addAll(generateForEndpoint(PasswordResetEndpoint.class, generator));

        outputFiles.addAll(
            Stream.concat(
                    generator.getInterfaces()
                        .stream(),
                    generator.getTypes()
                        .stream()
                )
                .map(v -> new OutputFile(
                        basePath.resolve("./dtos/" + toTsFilePath(v.getPath()) + toTsFileName(v.getName(), true)),
                        autogeneratedHeader() + "\n"
                            + v.getCodeImports()
                            .stream()
                            .map(ref -> "import type { " + ref.getName() + " } from \"@/dtos/" + toTsFilePath(ref.getPath()) + toTsFileName(ref.getName(), false) + "\"\n")
                            .collect(Collectors.joining(""))
                            + "\n"
                            + "export " + v.getCode()
                            .toCode(0)
                    )
                )
                .toList()
        );

        System.out.println("About to write " + outputFiles.size() + " files");
        var fileUpdater = new InteractiveFileUpdater(basePath, ".ts");
        fileUpdater.findFilesToUpdate();
        for (OutputFile outputFile : outputFiles) {
            // TODO: One IOException in the loop shouldn't stop everything
            fileUpdater.interactiveUpdateFile(outputFile.getPath(), outputFile.getContents());
        }
        fileUpdater.cleanupRemainingFiles();
        System.out.println("Done!");
        // TODO: Mapping validation stuff to Vuelidate or zod
        // TODO: Validation group => Generate a Pick<DTO, some fields> type. Might as well generate idiomatic Typescript code for it.
        // TODO: Nullable strings (parse the @NonNull annotation)
    }

    /**
     * Takes an endpoint and generates the matching Typescript code.
     *
     * @param endpointClass REST endpoint class.
     * @param gen           a utility class to simplify generating Typescript code from Java code.
     * @return the Typescript service file, note that imported interface files are not returned.
     */
    public static <T> List<OutputFile> generateForEndpoint(Class<T> endpointClass, TypescriptGenerator gen) {
        if (endpointClass.getAnnotation(RestController.class) == null) {
            throw new IllegalArgumentException("Expected endpointClass to have a @RestController annotation");
        }

        var importedNames = new HashSet<String>();
        var imports = new CodeWriter();
        imports.writeLine("import { useService } from './service';");
        Function<TypescriptGenerator.TsValue, TypescriptGenerator.TsValue> addImport = (TypescriptGenerator.TsValue v) -> {
            for (TypescriptGenerator.TsValue valueToImport : v.getRequiredImports()) {
                if (!importedNames.contains(valueToImport.getName())) {
                    importedNames.add(valueToImport.getName());
                    imports.writeLine("import type { " + valueToImport.getName() + " } from \"@/dtos/" + toTsFilePath(valueToImport.getPath()) + toTsFileName(valueToImport.getName(), false) + "\"");
                }
            }
            return v;
        };

        var output = new CodeWriter();
        String name = TypescriptGenerator.withoutSuffix(endpointClass.getSimpleName(), "Endpoint") + "Service";
        output.writeLine("export function use", name, "() {");
        output.beginIndent();
        var route = endpointClass.getAnnotation(RequestMapping.class)
            .value()[0];
        output.writeLine("const basePath = `", route.replaceAll("^/", ""), "`;");
        output.writeLine("const { api, filterSearchParams } = useService(basePath);");
        output.writeLine();

        // Spring has some really neat utilities
        var parameterNamesGetter = new DefaultParameterNameDiscoverer();

        var methods = new ArrayList<TypescriptGenerator.TsMethod>();

        for (Method declaredMethod : Arrays.stream(endpointClass.getDeclaredMethods())
            .sorted(Comparator.comparing(Method::getName))
            .toList()) {
            var mappingType = getMappingType(declaredMethod);
            if (mappingType == null) {
                continue;
            }

            var parameters = declaredMethod.getParameters();
            var parameterNames = parameterNamesGetter.getParameterNames(declaredMethod);
            if (parameterNames == null || parameterNames.length < parameters.length) {
                parameterNames = Arrays.stream(parameters)
                    .map(Parameter::getName)
                    .toList()
                    .toArray(new String[0]);
            }

            /*
             * There are 3 different supported ways of passing parameters to a REST endpoint:
             * 1. Path parameters: @PathVariable, /user/{id}
             * 2. Query parameters: @RequestParam, /?param=value
             * 3. Body parameters: @RequestBody, { json: value }
             */


            var parametersList = new ArrayList<TypescriptGenerator.TsProperty>();

            var requestOptions = new HashMap<String, String>();
            var requestOptionsSearchParams = new HashMap<String, String>();

            for (int i = 0; i < parameters.length; i++) {
                var parameter = parameters[i];
                String parameterName = parameterNames[i];
                var tsProperty = new TypescriptGenerator.TsProperty(
                    parameterName,
                    addImport.apply(gen.addValue(parameter.getParameterizedType())),
                    false);
                parametersList.add(tsProperty);

                if (parameter.isAnnotationPresent(RequestBody.class)) {
                    requestOptions.put("json", parameterName);
                } else if (parameter.isAnnotationPresent(PathVariable.class)) {
                    // Handled further down
                } else {
                    // Query parameters
                    if (tsProperty.getValue() instanceof TypescriptGenerator.TsInterface tsInterfaceProperty) {
                        // We also have to handle the object case. This makes the logic here a bit more fun, because we have to "destructure" the object.
                        // TODO: How are super-nested objects handled?
                        tsInterfaceProperty.getProperties()
                            .forEach(property -> {
                                requestOptionsSearchParams.put("'" + property.getName() + "'", parameterName + "." + property.getName());
                            });
                    } else {
                        requestOptionsSearchParams.put("'" + parameterName + "'", parameterName);
                    }
                }
            }

            if (!requestOptionsSearchParams.isEmpty()) {
                requestOptions.put("searchParams", "filterSearchParams(" + toJsonArray(requestOptionsSearchParams) + ")");
            }

            // / The path variable syntax basically matches the Javascript string interpolation syntax...so we're using that
            String path = mappingType.getPath()
                .isBlank() ? "" : (mappingType.getPath()
                .replaceAll("^/", "")
                .replaceAll("\\{", "\\${"));

            var body = new CodeWriter();

            var returnType = addImport.apply(gen.addValue(declaredMethod.getGenericReturnType()));
            boolean returnsVoid = returnType instanceof TypescriptGenerator.TsPrimitive tsPrimitive && tsPrimitive.getName()
                .equals("void");

            body.writeLine(
                (returnsVoid ? "await " : "return ") + "api." + mappingType.getType()
                    .name()
                    .toLowerCase(Locale.ROOT),
                "(`",
                path,
                "`",
                requestOptions.size() > 0 ? ", " + toJsonObject(requestOptions) : "",
                ")" + (returnsVoid ? "" : ".json();")
            );

            methods.add(new TypescriptGenerator.TsMethod(true,
                declaredMethod.getName(),
                parametersList,
                returnType,
                body));
        }

        methods.forEach(v -> output.writeLines(v.getCode()));

        output.writeLine("return {");
        output.beginIndent();
        methods.forEach(v -> output.writeLine(v.getName() + ","));
        output.endIndent();
        output.writeLine("};");

        output.endIndent();
        output.writeLine("}");

        var outputFile = new OutputFile(
            basePath.resolve("./services/" + toTsFileName(name, true)),
            autogeneratedHeader() + "\n"
                + imports.toCode(0) + "\n"
                + output.toCode(0)
        );

        return List.of(outputFile);
    }

    private static String toJsonArray(Map<String, String> obj) {
        return "[" +
            obj.entrySet()
                .stream()
                .map(entry -> "[" + entry.getKey() + ", " + entry.getValue() + "]")
                .collect(Collectors.joining(", ")) +
            "]";
    }

    private static String toJsonObject(Map<String, String> obj) {
        return "{" +
            (obj.size() > 0 ? " " : "") +
            obj.entrySet()
                .stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(Collectors.joining(", ")) +
            (obj.size() > 0 ? " " : "") +
            "}";
    }

    private static String autogeneratedHeader() {
        var header = new CodeWriter();
        header.writeLine("/** Autogenerated Code - Do Not Touch */");
        header.writeLine("/* eslint-disable */");

        return header.toCode(0);
    }

    /**
     * Takes a camelCase or PascalCase Java file name and outputs a corresponding snake-case
     * Typescript file name.
     *
     * <p>Regarding the ".ts" extension, sadly Typescript doesn't support imports with a
     * proper file extension. Hence, this function has an option to exclude the file extension.
     * </p>
     *
     * @param name             the Java file name.
     * @param includeExtension whether the ".ts" extension should be included or not.
     * @return the Typescript file name.
     */
    private static String toTsFileName(String name, boolean includeExtension) {
        return TypescriptGenerator.camelCaseToSnakeCase(name) + (includeExtension ? ".ts" : "");
    }


    /**
     * Takes a path and joins it with "/"s.
     */
    private static String toTsFilePath(List<String> path) {
        return path.stream()
            .map(v -> v + "/")
            .collect(Collectors.joining());
    }

    private static RequestMappingType getMappingType(Method endpointMethod) {
        {
            var annotation = endpointMethod.getAnnotation(GetMapping.class);
            if (annotation != null) {
                return new RequestMappingType(RequestMappingType.MappingType.GET, String.join("/", annotation.value()));
            }
        }
        {
            var annotation = endpointMethod.getAnnotation(PostMapping.class);
            if (annotation != null) {
                return new RequestMappingType(RequestMappingType.MappingType.POST, String.join("/", annotation.value()));
            }
        }
        {
            var annotation = endpointMethod.getAnnotation(PutMapping.class);
            if (annotation != null) {
                return new RequestMappingType(RequestMappingType.MappingType.PUT, String.join("/", annotation.value()));
            }
        }
        {
            var annotation = endpointMethod.getAnnotation(PatchMapping.class);
            if (annotation != null) {
                return new RequestMappingType(RequestMappingType.MappingType.PATCH, String.join("/", annotation.value()));
            }
        }
        {
            var annotation = endpointMethod.getAnnotation(DeleteMapping.class);
            if (annotation != null) {
                return new RequestMappingType(RequestMappingType.MappingType.DELETE, String.join("/", annotation.value()));
            }
        }
        return null;
    }

    private static class RequestMappingType {
        enum MappingType {
            GET, PUT, POST, PATCH, DELETE
        }

        private final MappingType type;
        private final String path;

        private RequestMappingType(MappingType type, String path) {
            this.type = type;
            this.path = path;
        }

        public MappingType getType() {
            return type;
        }

        public String getPath() {
            return path;
        }
    }

    private static class OutputFile {
        private final Path path;
        private final String contents;

        private OutputFile(Path path, String contents) {
            this.path = path;
            this.contents = contents;
        }

        public Path getPath() {
            return path;
        }

        public String getContents() {
            return contents;
        }
    }
}