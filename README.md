# Quarkus Endive

[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.endive/quarkus-endive?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.endive/quarkus-endive-parent)

## Overview

Quarkus Endive integrates the [Endive](https://github.com/bytecodealliance/endive) WebAssembly runtime with Quarkus, enabling 
seamless execution of WebAssembly modules within Java applications.

The extension provides environment-aware WebAssembly module management through CDI injection, with automatic 
optimization for development, production, and native image deployments.

<img src="docs/modules/ROOT/assets/images/qc.gif" width="800" alt="A QR code generation quick demo!">

## Key Features

- **Injectable WebAssembly Context** - Access WASM context via CDI with `@Inject` and `@Named`
- **Environment-Optimized Execution** - Automatic selection of interpreter, runtime compiler, or build-time compilation based 
on execution context
- **Multi-Module Support** - Configure and manage multiple independent WASM modules
- **Build-Time Code Generation** - Build-time compilation of WASM to JVM bytecode for optimal performance
- **Live Reload** - Automatic module recompilation in development mode when WASM files change
- **WASI Support** - Integrates Endive WebAssembly System Interface implementation
- **Native Image Compatible** - First-class support for GraalVM native images

## Quick Start

Add the extension to your project:

```xml
<dependency>
    <groupId>io.quarkiverse.endive</groupId>
    <artifactId>quarkus-endive</artifactId>
    <version>${quarkus-endive.version}</version>
</dependency>
```

Configure a WASM module in `application.properties`:

```properties
quarkus.endive.modules.my-module.wasm-file=src/main/resources/my-module.wasm
```

Inject and use in your application:

```java
@Inject
@Named("my-module")
WasmQuarkusContext wasmContext;

@PostConstruct
void init() {
    Instance instance = Instance.builder(wasmContext.getWasmModule())
        .withMachineFactory(wasmContext.getMachineFactory())
        .build();

    ExportFunction add = instance.export("add");
    long[] result = add.apply(40, 2);
}
```

## Documentation

For comprehensive documentation, configuration options, and advanced usage examples, see the [full documentation](./docs/modules/ROOT/pages/index.adoc).

## Contributing

Contributions are welcome! Please refer to the [Quarkiverse contribution guidelines](https://github.com/quarkiverse/quarkiverse/wiki) for more information.

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.
