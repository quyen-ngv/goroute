# Backend working agreement

Before changing this backend, read [ARCHITECTURE.md](ARCHITECTURE.md). It is the canonical guide for the current architecture, MyBatis conventions, database changes, and verification rules.

Keep changes backward compatible unless the task explicitly requests an API or data migration. Preserve the existing request/response envelope and business flow. Prefer small, testable changes and run the Maven verification command described in the architecture guide.

## Maven verification on Windows

This backend requires Java 21. Before running Maven, make sure the current PowerShell process uses a JDK 21 installation. On this workstation, use:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
rtk java -version
```

Run Maven from the `goroute` directory through the Maven wrapper. All shell commands must keep the workspace-wide `rtk` prefix:

```powershell
rtk .\mvnw.cmd -q -DskipTests compile
rtk .\mvnw.cmd -q test
```

Use a command timeout of at least 10 minutes for either Maven command. Quiet mode may produce no output for several minutes; do not treat missing output as a failure while the process is still running. Never use the system Maven installation when the wrapper is available. If the documented JDK path changes, select another installed JDK 21 and update `JAVA_HOME` before verification.
