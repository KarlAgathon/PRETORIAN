package com.pretorian.tv

/**
 * The Beta build (com.pretorian.tv.beta) lives next to Stable with its own data.
 * It never offers Stable updates, never starts with the box and writes its backups under its own name,
 * so Stable can never pick up a Beta backup by mistake. Beta can read Stable backups (that is how data moves over).
 */
object Beta {
    val on: Boolean get() = BuildConfig.BUILD_TYPE == "beta"
    /** folder under Download/ and on USB sticks for files this build writes */
    val folder: String get() = if (on) "MediaCenter Beta" else "MediaCenter"  // i18n-ignore
    /** backup file name prefix written by this build */
    val backupPrefix: String get() = if (on) "MediaCenterBeta-backup-" else "MediaCenter-backup-"  // i18n-ignore
}
