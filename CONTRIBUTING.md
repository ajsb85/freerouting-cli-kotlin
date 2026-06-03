# Contribution Guidelines

Thank you for contributing to the Freerouting Kotlin CLI Modernization project! To maintain a high-quality, stable codebase with a clear history, we follow strict standards for branching and commits.

---

## 🏔️ Git Workflow: Trunk-Based Development (TBD)

We use the **Trunk-Based Development** model. All active development is merged directly into our single core branch (`main`):

1. **Short-Lived Branches**: Features or fixes should be developed on temporary, short-lived branches (lasting less than 1-2 days).
2. **`tbdflow` CLI Tool**: We manage our Git workflow using the `tbdflow` tool to automate rebase synchronization, intent tracking, and commits.
   - Run `tbdflow sync` to stay up-to-date with `main` before starting work.
   - Use `tbdflow note "..."` to record incremental intent while coding.
   - Run `tbdflow commit` to package changes, run automated validations, and push them to the trunk.

---

## 📝 Commit Messages: Conventional Commits

We enforce the **Conventional Commits** specification to ensure clear and readable project history and to allow automatic changelog generation.

### Commit Format

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Specifications and Rules

1. **Subject Line**:
   - Must be **under 50 characters** long.
   - Use the **imperative, present tense** (e.g., "add", "fix", "change" instead of "added", "fixed", "changes").
   - Do not capitalize the first letter of the subject.
   - Do not end the subject line with a period.

2. **Commit Types (`<type>`)**:
   - `feat`: A new feature or capability.
   - `fix`: A bug fix or technical issue resolution.
   - `docs`: Documentation modifications (e.g., updates to README).
   - `style`: Changes that do not affect the meaning of the code (formatting, white-space, etc.).
   - `refactor`: A code change that neither fixes a bug nor adds a feature.
   - `test`: Adding missing tests or correcting existing tests.
   - `chore`: Build tasks, Gradle configurations, or dependency upgrades.

3. **Body and Footer**:
   - Wrap the body and footer text at **72 characters**.
   - Explain the *what* and *why* of the change (rather than the *how*).

### Example Commit Message

```
feat(cli): add System.exit(0) upon completion

Force the JVM process to terminate immediately after writing the
Specctra session file. This prevents the JVM from hanging on active
daemon threads owned by the background router scheduler.
```
