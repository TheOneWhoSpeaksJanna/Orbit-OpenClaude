package com.omniclaw

import org.junit.Test
import java.io.File

/**
 * Smoke test that verifies the bundled `packages.default.json` asset (the
 * package registry) is present in the source tree.
 *
 * Historically this test downloaded real packages from the internet at test
 * time, which made it flaky on CI. We now keep the test offline.
 *
 * Note: we cannot parse the JSON in a plain JVM unit test because Android's
 * `org.json` classes are stubbed out (not mocked by default). Real JSON-level
 * validation happens in `androidTest`.
 */
class SystemCheckTest {

    @Test
    fun verifyBundledPackageRegistryExists() {
        val assetFile = File("src/main/assets/packages.default.json")
        check(assetFile.exists()) {
            "Missing bundled package registry at ${assetFile.absolutePath}. " +
                "The app cannot seed its runtime registry without this file."
        }
        check(assetFile.length() > 100L) {
            "packages.default.json is suspiciously small (${assetFile.length()} bytes) — expected at least a few hundred bytes of JSON"
        }

        val text = assetFile.readText()
        // Lightweight structural checks (without parsing JSON).
        check(text.contains("\"packages\"")) {
            "packages.default.json is missing the top-level 'packages' array key"
        }
        check(text.contains("\"name\"") && text.contains("\"url\"")) {
            "packages.default.json is missing required package field keys (name/url)"
        }
        check(text.contains("\"nodejs\"") && text.contains("\"python\"")) {
            "packages.default.json is missing expected default packages (nodejs, python)"
        }
    }
}
