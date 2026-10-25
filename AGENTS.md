# Development instructions

- Build a native Android app using Kotlin and Jetpack Compose. Do not replace the native UI with a WebView wrapper.
- No project-operated runtime backend. Third-party requests originate on the device. GitHub hosting/build tooling is distinct from runtime services.
- Keep route calculations independently testable. Compare distances, segments, arrival times, wind components, weather classification and route coloring with the existing website using public or synthetic fixtures.
- Preserve source track segments and available elevation; do not invent missing source fields. Coordinate systems must remain explicit, and unverified source-coordinate assumptions must be documented.
- Never commit personal map/weather credentials, signing private keys, local machine paths, private routes or raw service logs. Exclude user credentials from logs and backups by default.
- Read signing fingerprints from the installed app's actual signing certificate. Do not hard-code a debug fingerprint as the release identity.
- Validate the selected Tianditu service's current key type, authentication and runtime key configuration. Never bypass access controls or assume browser keys are Android keys.
- Public-route import must stop on authentication/authorization failures and offer local GPX import. Retain timeouts, size limits and decompression limits.
- Inspect installed tools before installing replacements. Use the Gradle wrapper and pin compatible toolchain/dependency versions; never claim a build or device test passed without executing it.
- Begin with a map/key and public-route import feasibility check before implementing the entire app.
