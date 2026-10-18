package com.sukisu.ultra.ui.util.ghostlock.data.profile

import com.sukisu.ultra.ui.util.ghostlock.data.CredTemplate
import com.sukisu.ultra.ui.util.ghostlock.data.KernelOffsetTable
import com.sukisu.ultra.ui.util.ghostlock.data.TaskStructOffsets
import com.sukisu.ultra.ui.util.ghostlock.data.route.RouteKind

/** A sparse layer of execution values, keyed by their path under `execution`. */
data class SparseExecutionValues(val values: Map<String, ULong>)

/** Resolved device/kernel core, mirrored one-to-one by the native wire slots. */
data class CoreProfile(
    val release: String,
    val schemaVersion: Int,
    val kernelMajor: UInt,
    val taskStruct: TaskStructOffsets,
    val cred: CredTemplate,
    val offsets: KernelOffsetTable,
    val kernelPhysLoad: ULong,
    val route: RouteKind?,
    val fallback: RouteKind?,
    val kernelsnitchCollisions: UInt,
    val mmStructSz: UInt,
    val recommendations: SparseExecutionValues,
)

/** Typed, path-carrying configuration error. */
data class ConfigError(val fieldPath: String, val message: String) {
    override fun toString(): String = "$fieldPath: $message"
}
