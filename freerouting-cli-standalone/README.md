# Freerouting Kotlin Standalone CLI

This directory contains the fully standalone distribution of the **Freerouting Kotlin CLI**. 

The Kotlin CLI launcher, session management, job scheduler, logger, and configuration parsing have been completely migrated to Kotlin.

---

## 📂 Distribution Structure

```
freerouting-cli-standalone/
├── README.md               # This documentation
├── bin/
│   └── freerouting-cli.jar # Pre-compiled standalone Kotlin CLI fat JAR
└── examples/
    ├── input/
    │   └── tutorial_board.dsn # Sample input board (tutorial board)
    └── output/             # Output directory for routed Specctra Sessions (.ses)
```

---

## 🚀 How to Run Standalone

To run the CLI tool, you only need a Java Runtime Environment (JRE 21 or later) installed on your system. **No source code (Java or Kotlin) is required.**

Run the following command from this directory:

```bash
java -jar bin/freerouting-cli.jar \
  -de examples/input/tutorial_board.dsn \
  -do examples/output/tutorial_board.ses \
  --gui.enabled=false
```

Upon completion, you will find the fully routed board at:
`examples/output/tutorial_board.ses`

---

## 🛠️ Note on Source Compiling vs. Running

To run this application as a standalone executable, **no Java code or project source files are needed** because the pre-compiled JAR (`bin/freerouting-cli.jar`) bundles all required classes (both migrated Kotlin components and core routing algorithm dependencies).

However, if you wish to **compile** the project from source, please note that while the CLI launcher and its immediate controller layers are 100% Kotlin, they still depend on the underlying geometric routing engine, board libraries, and trace optimization algorithms of the `:freerouting` core subproject, which remain in Java.
