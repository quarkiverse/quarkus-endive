package io.quarkiverse.endive.deployment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.enterprise.context.ApplicationScoped;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import io.quarkiverse.endive.deployment.items.GeneratedWasmCodeBuildItem;
import io.quarkiverse.endive.deployment.items.WasmContextRegistrationCompleted;
import io.quarkiverse.endive.runtime.WasmQuarkusConfig;
import io.quarkiverse.endive.runtime.WasmQuarkusUtils;
import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContext;
import io.quarkiverse.endive.runtime.wasm.WasmQuarkusContextRecorder;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Produce;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.GeneratedClassBuildItem;
import io.quarkus.deployment.builditem.GeneratedResourceBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourcePatternsBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import run.endive.build.time.compiler.Config;
import run.endive.build.time.compiler.Generator;

/**
 * The Quarkus Endive deployment processor provides the following features:
 * <ul>
 * <li>Produce a collection of injectable named beans, each representing a configured Wasm module</li>
 * <li>Replace the Endive Maven plugin functionality to generate bytecode, Wasm meta files and raw Java sources</li>
 * <li>Watch statically configured Wasm module files to trigger a rebuild in <i>dev mode</i></li>
 * </ul>
 * <p>
 * The first build step creates a collection of application scoped named beans, each representing a statically
 * configured Wasm module, which can be injected into client applications.
 * <br>
 * An additional build step leverages the Endive {@link Generator} to generate the bytecode, the meta Wasm files and
 * the raw Java sources for each configured Wasm module, and subsequent build steps process the output to provide
 * Quarkus the related classes and resources that will be built as part of the application, thus replacing the Endive
 * Maven plugin functionality.
 * <br>
 * Finally, a build step is responsible for adding all the statically configured Wasm files to the collection of watched
 * resources.
 * </p>
 */
class QuarkusWasmProcessor {

    private static final String FEATURE = "endive";
    private static final Logger LOG = Logger.getLogger(QuarkusWasmProcessor.class);

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Creates a collection of {@link WasmQuarkusContext} application scoped named beans, for each statically
     * configured Wasm module.
     *
     * @param syntheticBeans The {@link BuildProducer} instance that creates the synthetic beans
     * @param recorder The {@link WasmQuarkusContextRecorder} instance that provides the logic to create the runtime
     *        instances of the required beans
     * @param config The application configuration, storing all the configured modules.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Produce(WasmContextRegistrationCompleted.class)
    void registerWasmContextBeans(BuildProducer<SyntheticBeanBuildItem> syntheticBeans, WasmQuarkusContextRecorder recorder,
            WasmQuarkusConfig config, OutputTargetBuildItem outputTarget) {
        // Produce synthetic WasmQuarkusContext beans for related Wasm modules
        for (Map.Entry<String, WasmQuarkusConfig.ModuleConfig> moduleConfigEntry : config.modules().entrySet()) {
            final String key = moduleConfigEntry.getKey();
            final Optional<String> packageType = ConfigProvider.getConfig().getOptionalValue("quarkus.package.type",
                    String.class);
            final boolean isNativePackageType = packageType.isPresent() && packageType.get().equals("native");
            syntheticBeans.produce(
                    SyntheticBeanBuildItem.configure(WasmQuarkusContext.class)
                            .scope(ApplicationScoped.class)
                            .runtimeValue(recorder.createContext(key, config, isNativePackageType,
                                    outputTarget.getOutputDirectory().getParent().toString()))
                            .setRuntimeInit()
                            .named(key)
                            .done());
        }
    }

    /**
     * Use the Endive build time compiler {@link Generator} to generate bytecode from configured {@code Wasm} modules.
     *
     * @param config The application configuration, storing all the configured modules.
     * @param outputTarget The build output target providing the directory where generated files will be written.
     * @param nativeImageResourcePatternsBuildItemBuildProducer The producer for registering native image resource patterns.
     * @return A collection of {@link GeneratedWasmCodeBuildItem} items, each of them storing the name of the
     *         generated Wasm module, a list of paths referencing the generated {@code .class} files,
     *         a reference to the generated {@code .meta} Wasm file, and a reference to the generated {@code .java}
     *         source file.
     * @throws IOException If the generation fails.
     */
    @BuildStep
    @Consume(WasmContextRegistrationCompleted.class)
    public List<GeneratedWasmCodeBuildItem> generate(WasmQuarkusConfig config,
            OutputTargetBuildItem outputTarget,
            BuildProducer<NativeImageResourcePatternsBuildItem> nativeImageResourcePatternsBuildItemBuildProducer)
            throws IOException {

        final List<GeneratedWasmCodeBuildItem> result = new ArrayList<>();

        final Path targetDirectory = outputTarget.getOutputDirectory();
        final Path classesDir = targetDirectory.resolve("classes");
        final Path generatedSourcesDir = targetDirectory.resolve("generated-sources");

        for (Map.Entry<String, WasmQuarkusConfig.ModuleConfig> entry : config.modules().entrySet()) {
            final String key = entry.getKey();
            final WasmQuarkusConfig.ModuleConfig moduleConfig = entry.getValue();
            final String name = moduleConfig.name();
            Path wasmFile = null;
            if (moduleConfig.wasmFile().isPresent()) {
                wasmFile = moduleConfig.wasmFileAbsolutePath(targetDirectory.getParent());
            } else if (moduleConfig.wasmResource().isPresent()) {
                wasmFile = WasmQuarkusUtils.getWasmPathFromResource(moduleConfig.wasmResource().get());
            } else {
                LOG.info(
                        "Neither a resource name nor a file path is defined. Skipping code generation for Wasm module " + key);
            }
            // generate when a Wasm file exists
            if (wasmFile != null) {
                final Optional<List<Integer>> interpretedFunctionsConfig = moduleConfig.compiler().interpretedFunctions();

                LOG.info("Generating bytecode and resources into " + classesDir.toFile().getAbsolutePath() + " for "
                        + key + " from "
                        + wasmFile);

                // Wait for file to be stable before parsing to avoid race conditions during hot reload
                waitForFileStability(wasmFile, key);

                final Config generatorConfig = Config.builder()
                        .withWasmFile(wasmFile)
                        .withName(name)
                        .withTargetClassFolder(classesDir)
                        .withTargetWasmFolder(classesDir)
                        .withTargetSourceFolder(generatedSourcesDir)
                        .withInterpreterFallback(moduleConfig.compiler().interpreterFallback())
                        .withInterpretedFunctions(
                                interpretedFunctionsConfig.isPresent() ? new HashSet<>(interpretedFunctionsConfig.get())
                                        : Set.of())
                        .build();
                final Generator generator = new Generator(generatorConfig);
                final Set<Integer> finalInterpretedFunctions = generator.generateResources();
                generator.generateMetaWasm(finalInterpretedFunctions);
                generator.generateSources();

                // Track the generated *.class and .meta Wasm files
                final List<Path> generatedClasses = new ArrayList<>();
                Path generatedMetaWasm = null;
                Path generatedJava = null;
                // N .class files
                LOG.debug("Tracking the generated .class files in " + classesDir.toAbsolutePath());
                try (Stream<Path> pathStream = Files.walk(classesDir.toAbsolutePath())) {
                    ArrayList<Path> files = pathStream
                            .filter(p -> p.toString().contains("/" + WasmQuarkusUtils.getWasmModuleClassName(name))
                                    && p.toString().endsWith(".class"))
                            .collect(Collectors.toCollection(ArrayList::new));
                    for (Path file : files) {
                        LOG.debug("Tracking the generated .class file: " + file);
                        generatedClasses.add(file);
                    }
                }
                // 1 .meta Wasm file
                LOG.debug("Tracking the generated .meta files in " + classesDir.toFile().getAbsolutePath());
                try (Stream<Path> pathStream = Files.walk(classesDir.toAbsolutePath())) {
                    generatedMetaWasm = pathStream
                            .filter(p -> p.toString().endsWith(".meta"))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(".meta Wasm file not found"));
                    LOG.debug("Tracking the generated .meta file: " + generatedMetaWasm);
                }
                // 1 .java source file
                LOG.debug("Tracking the generated .java files in " + generatedSourcesDir.toFile().getAbsolutePath());
                try (Stream<Path> pathStream = Files.walk(generatedSourcesDir.toAbsolutePath())) {
                    generatedJava = pathStream
                            .filter(p -> p.toString().endsWith(".java"))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(".java Wasm file not found"));
                    LOG.debug("Tracking the generated .java file: " + generatedJava);
                }
                result.add(new GeneratedWasmCodeBuildItem(name, generatedClasses, generatedMetaWasm, generatedJava));
            }
        }
        // register *.wasm files for Native mode (although they are not be used...)
        if (config.modules().entrySet().stream().anyMatch(m -> m.getValue().wasmFile().isPresent())) {
            nativeImageResourcePatternsBuildItemBuildProducer.produce(NativeImageResourcePatternsBuildItem.builder()
                    .includeGlobs("**/*.wasm")
                    .build());
        }
        return result;
    }

    /**
     * A build step that consumes the build items generated by
     * {@link #generate(WasmQuarkusConfig, OutputTargetBuildItem, BuildProducer)}
     * to collect a list of {@link GeneratedClassBuildItem} referencing the generated {@code .class} files.
     *
     * @param generatedWasmCodeBuildItems The list of {@link GeneratedWasmCodeBuildItem} items that will be used
     *        to collect all the generated {@code .class} files
     *
     * @param generatedClassBuildItemBuildProducer The producer that produces instances of
     *        {@link GeneratedClassBuildItem} items, referencing the generated {@code .class} files
     * @param reflectiveClassBuildItemBuildProducer The producer that produces instances of
     *        {@link ReflectiveClassBuildItem} items, referencing the generated {@code .class} files
     */
    @BuildStep
    public void collectGeneratedClasses(List<GeneratedWasmCodeBuildItem> generatedWasmCodeBuildItems,
            BuildProducer<GeneratedClassBuildItem> generatedClassBuildItemBuildProducer,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClassBuildItemBuildProducer)
            throws IOException {

        for (GeneratedWasmCodeBuildItem buildItem : generatedWasmCodeBuildItems) {
            final String name = buildItem.getName();
            LOG.info("Collecting generated .class files for " + name);
            final String classPackage = name.substring(0, name.lastIndexOf('.'));
            for (Path file : buildItem.getClasses()) {
                final String className = classPackage + "." + file.getFileName().toString().replace(".class", "");
                LOG.debug("Adding .class file: " + className);
                // register as an application class
                generatedClassBuildItemBuildProducer.produce(
                        new GeneratedClassBuildItem(true, className, Files.readAllBytes(file)));
                // register for reflection
                reflectiveClassBuildItemBuildProducer.produce(
                        ReflectiveClassBuildItem.builder(className)
                                .constructors()
                                .methods()
                                .fields()
                                .build());
            }
        }
    }

    /**
     * A build step that consumes the build items generated by
     * {@link #generate(WasmQuarkusConfig, OutputTargetBuildItem, BuildProducer)}
     * to collect a list of {@link GeneratedResourceBuildItem} referencing the generated {@code .meta} files.
     *
     * @param generatedWasmCodeBuildItems The list of {@link GeneratedWasmCodeBuildItem} items that will be used
     *        to collect all the generated {@code .meta} files
     * @param generatedResourceBuildItemBuildProducer The producer that will generate {@link GeneratedResourceBuildItem}
     *        instances, referencing the generated {@code .meta} files.
     */
    @BuildStep
    public void collectGeneratedMetaWasm(
            List<GeneratedWasmCodeBuildItem> generatedWasmCodeBuildItems,
            BuildProducer<GeneratedResourceBuildItem> generatedResourceBuildItemBuildProducer,
            BuildProducer<NativeImageResourceBuildItem> nativeImageResourceBuildItemBuildProducer)
            throws IOException {

        for (GeneratedWasmCodeBuildItem buildItem : generatedWasmCodeBuildItems) {
            final String name = buildItem.getName();
            final Path metaWasm = buildItem.getMetaWasm();
            final String resourcePath = WasmQuarkusUtils.getWasmModuleClassPath(name);
            final String resource = resourcePath + "/" + metaWasm.getFileName();
            LOG.info("Collecting the generated .meta file: " + metaWasm + " for " + buildItem.getName() + ", as a resource "
                    + resource);
            generatedResourceBuildItemBuildProducer.produce(new GeneratedResourceBuildItem(resource,
                    Files.readAllBytes(metaWasm)));
            // register meta Wasm for Native mode
            nativeImageResourceBuildItemBuildProducer.produce(new NativeImageResourceBuildItem(resource));
        }
    }

    /**
     * A build step that consumes the build items generated by
     * {@link #generate(WasmQuarkusConfig, OutputTargetBuildItem, BuildProducer)}
     * to collect a list of {@link GeneratedResourceBuildItem} referencing the generated {@code .java} files.
     *
     * @param generatedWasmCodeBuildItems The list of {@link GeneratedWasmCodeBuildItem} items that will be used
     *        to collect all the generated {@code .java} files
     *
     * @return A collection of {@link GeneratedResourceBuildItem} items, referencing the generated {@code .java} files.
     */
    @BuildStep
    public List<GeneratedResourceBuildItem> collectGeneratedSources(
            List<GeneratedWasmCodeBuildItem> generatedWasmCodeBuildItems)
            throws IOException {

        final List<GeneratedResourceBuildItem> generatedJavaSources = new ArrayList<>();

        for (GeneratedWasmCodeBuildItem buildItem : generatedWasmCodeBuildItems) {
            final Path javaSources = buildItem.getJavaSources();
            LOG.info("Collecting the generated .java file: " + javaSources + " for " + buildItem.getName());
            generatedJavaSources.add(new GeneratedResourceBuildItem(javaSources.getFileName().toString(),
                    Files.readAllBytes(javaSources)));
        }
        return generatedJavaSources;
    }

    /**
     * Only in dev mode, the configured Wasm modules that define a filesystem path are added to the watched resources.
     *
     * @param wasmQuarkusConfig The application configuration, storing all the configured modules.
     * @param outputTarget The build output target providing the base directory for resolving file paths.
     * @return A list of {@link HotDeploymentWatchedFileBuildItem}, representing the collection of
     *         Wasm module files that will be watched in dev mode.
     */
    @BuildStep(onlyIf = IsDevelopment.class)
    List<HotDeploymentWatchedFileBuildItem> addWatchedResources(WasmQuarkusConfig wasmQuarkusConfig,
            OutputTargetBuildItem outputTarget) {

        List<HotDeploymentWatchedFileBuildItem> result = new ArrayList<>();

        for (Map.Entry<String, WasmQuarkusConfig.ModuleConfig> entry : wasmQuarkusConfig.modules().entrySet()) {
            final WasmQuarkusConfig.ModuleConfig moduleConfig = entry.getValue();
            final Path wasmFile;
            if (moduleConfig.wasmFile().isPresent()) {
                wasmFile = moduleConfig.wasmFileAbsolutePath(outputTarget.getOutputDirectory().getParent());
                LOG.info("Adding " + wasmFile + " to the collection of watched resources (live reload)");
                result.add(new HotDeploymentWatchedFileBuildItem(wasmFile.toAbsolutePath().toString()));
            }
        }
        return result;
    }

    /**
     * Waits for a WASM file to become stable before processing.
     * This prevents race conditions when the file is being written by an external process
     * (e.g., Docker build script) and the file watcher triggers a rebuild before the write operation completes.
     *
     * @param wasmFile The path to the WASM file to monitor
     * @param moduleName The name of the module (for logging)
     * @throws IOException If file operations fail
     */
    private void waitForFileStability(Path wasmFile, String moduleName) throws IOException {
        final long stabilityCheckIntervalMs = 200;
        final long maxWaitMs = 3000;
        final int requiredStableChecks = 3; // File must be stable for 3 consecutive checks

        if (!Files.exists(wasmFile)) {
            LOG.warn("WASM file does not exist yet: " + wasmFile + " for module " + moduleName);
            return;
        }

        long lastSize = Files.size(wasmFile);
        int stableChecks = 0;
        final long startTime = System.currentTimeMillis();

        LOG.debug("Checking file stability for " + wasmFile + " (module: " + moduleName + ")");

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            try {
                Thread.sleep(stabilityCheckIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for file stability", e);
            }

            long currentSize = Files.size(wasmFile);

            if (currentSize == lastSize && currentSize > 0) {
                stableChecks++;
                if (stableChecks >= requiredStableChecks) {
                    LOG.debug("File is stable: " + wasmFile + " (size: " + currentSize + " bytes)");
                    return;
                }
            } else {
                // File size changed, reset counter
                stableChecks = 0;
                lastSize = currentSize;
                LOG.debug("File still changing: " + wasmFile + " (size: " + currentSize + " bytes)");
            }
        }

        // File is stable or we've waited long enough
        LOG.debug("File stability check completed for " + wasmFile + " after " +
                (System.currentTimeMillis() - startTime) + "ms");
    }
}
