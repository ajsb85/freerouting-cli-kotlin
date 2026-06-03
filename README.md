# Freerouting Kotlin CLI Modernization

[![Kotlin](https://img.shields.io/badge/kotlin-1.9.22-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![Gradle](https://img.shields.io/badge/gradle-9.5-green.svg?logo=gradle)](https://gradle.org)
[![License](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](LICENSE)

Welcome to the **Freerouting Kotlin CLI Modernization** project. This repository streamlines and simplifies the classic **Freerouting** printed circuit board (PCB) auto-router by migrating its command-line interface (CLI) launcher to **Kotlin**, removing heavy/unnecessary components (such as Swing GUIs, REST API servers, and MCP servers) to deliver a highly optimized, headless PCB routing engine.

---

## 🚀 Key Features

- **Kotlin-Based CLI Launcher**: Fully migrated CLI orchestration (`FreeroutingCli.kt`) managing input parsing, session setup, job orchestration, and background scheduling.
- **Minimalistic & Lightweight**: Intentionally disables Swing-based GUIs and local API/MCP servers to minimize memory footprint and binary sizes.
- **SPECCTRA Compatibility**: Full byte-for-byte output parity with the original Freerouting compiler, routing `.dsn` (Design) files and exporting `.ses` (Session) files.
- **JVM 25 Ready**: Targeted and toolchained for modern Java releases, falling back gracefully to standard runtimes.

---

## 📁 Repository Structure

This repository uses a multi-project Gradle workspace:

```
freerouting-kt-root/
├── freerouting/              # The core Java-based Freerouting engine
│   └── src/                  # Routing algorithms, DRC logic, and board solvers
├── freerouting-cli-kotlin/   # The new migrated Kotlin CLI subproject
│   ├── src/main/kotlin/      # app.freerouting.FreeroutingCli launcher
│   └── build.gradle          # Kotlin-specific JVM Gradle build configuration
├── settings.gradle           # Multi-project workspace configuration
└── gradle.properties         # JVM targets and compiler validations
```

---

## 🛠️ Getting Started

### Prerequisites

- **Java Development Kit (JDK)**: Version 21 or higher (JVM 25 toolchain recommended).
- **Gradle**: Managed via the included Wrapper (`./gradlew`).

### Building the Executable JAR

Generate a standalone, runnable fat JAR containing all dependencies and the core routing engine:

```bash
./gradlew :freerouting-cli-kotlin:executableJar
```

The compiled executable JAR will be located at:
`freerouting-cli-kotlin/build/libs/freerouting-cli-kotlin-current-executable.jar`

### Running the Router

To route a board layout headlessly, run the executable JAR with the input and output arguments:

```bash
java -jar freerouting-cli-kotlin/build/libs/freerouting-cli-kotlin-current-executable.jar \
  -de path/to/input.dsn \
  -do path/to/output.ses
```

---

## 🤝 Contributing

We adopt the **Trunk-Based Development** Git workflow enforced via `tbdflow` and expect all commits to conform to **Conventional Commits** best practices.

Please read our [Contribution Guidelines](CONTRIBUTING.md) for more details.
