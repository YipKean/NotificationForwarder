# Namespace rename plan

## Decision

Replace `com.itsazni.notificationforwarder` with `com.notificationforwarder.app` in the Android namespace, application ID and source packages. The user selected a new app identity: existing installations are separate, with no automatic settings, queue or permission migration.

## Implementation (Luna, xhigh)

1. Update `namespace` and `applicationId` in `app/build.gradle.kts`.
2. Move all 18 Kotlin source files from `app/src/main/java/com/itsazni/notificationforwarder/` to `app/src/main/java/com/notificationforwarder/app/`. Replace package declarations, imports and fully qualified references only; preserve file formatting and behavior.
3. Verify relative manifest components resolve to the new classes, including the application, activity, notification listener and boot receiver. Check worker scheduling and notification-access component references for stale names.
4. Update source paths in the security audit and implementation report. In the existing untracked `PROJECT.md`, change only the obsolete source path. Add a short README note explaining the new application ID and fresh setup, including disabling the old app to avoid duplicate forwarding.
5. Preserve license attribution, webhook metadata and historical graphify output. Do not rewrite cached graphs as if they were freshly extracted. Note their stale paths in the README.
6. Search active source/configuration for old dotted and slash-separated names. Run `git diff --check` and attempt `gradlew.bat assembleDebug lintDebug`. Report missing tooling honestly; do not install a toolchain or commit changes.

## Final review (parent agent)

Verify all moved files match their originals after namespace substitution, inspect Gradle and documentation changes, resolve manifest class names against the new source tree, and review validation output. Record material limitations.

## Graphify assessment

The existing 266-node graph helps navigate lifecycle, UI, settings and delivery dependencies. It predates recent security changes and cannot prove rename completeness. Current-source searches and file comparisons are the authority for this mechanical change.

## Final review results

- All 18 moved Kotlin files match their original contents after namespace substitution, with line endings normalized for comparison.
- Gradle changes are limited to the namespace and application ID.
- All four manifest component names resolve to classes in the new source tree.
- No old namespace or source paths remain in active Android source or build configuration.
- Documentation paths and new-install setup guidance are updated; historical graph files remain unchanged.
- `git diff --check` passes.
- Luna attempted `gradlew.bat assembleDebug lintDebug`; execution was blocked because Java is unavailable and `JAVA_HOME` is not configured. Compilation, lint and device behavior remain unverified.
