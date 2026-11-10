package app.template.patches.example

import app.morphe.patcher.patch.patch
import app.template.patches.shared.Constants.COMPATIBILITY_HELAKURU

/** Temporary patch used to build a Morphe output without applying the blur modifications. */
@Suppress("unused")
val diagnosticNoOpPatch = patch(
    name = "Diagnostic No-op (no app changes)",
    description = "Creates a comparison build without applying the live blur modifications.",
    default = true
) {
    compatibleWith(COMPATIBILITY_HELAKURU)

    execute {
        // Intentionally empty: selecting this lets Morphe run its packaging/signing pipeline.
    }
}
