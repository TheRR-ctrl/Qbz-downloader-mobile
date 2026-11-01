# QBDLX Mobile — local hardening update

This source update is based on the supplied 0.32.0 ZIP. It does not change Qobuz authentication, request signing, stream URLs, audio bytes, tagging, or the app version.

## Download queue persistence

- Queue writes now use a uniquely named temporary file in the destination directory.
- The temporary file is flushed to storage before it is published.
- The queue is replaced with an atomic move when the filesystem supports it, with a replace move as fallback.
- A failed replacement no longer deletes the previous queue file first.
- Missing parent directories are created safely; a non-directory parent produces a clear I/O error.
- Temporary files are cleaned up after success or failure.
- Added unit tests for nested directory creation and preservation of an existing destination when replacement fails.

## Validation status

Whitespace review completed. Gradle unit tests and APK assembly could not be run in this environment because the Gradle wrapper distribution was not cached and network access to services.gradle.org was unavailable. Run the project documented Gradle test/build commands before installing this build.
