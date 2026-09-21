package dev.bee.kanjianki.data.sql

data class SchemaTransition(
    val fromVersion: Int,
    val toVersion: Int,
    val kind: SchemaTransitionKind,
)

enum class SchemaTransitionKind {
    CREATED,
    UPGRADED,
    DOWNGRADED,
    UNCHANGED,
}

class SchemaManager(
    private val context: MigrationContext,
) {
    suspend fun initialize(database: SqlDatabase): SchemaTransition =
        database.write {
            val oldVersion = pragmas.readLong(SqlPragma.USER_VERSION).toInt()
            when {
                oldVersion == 0 -> {
                    CanonicalSchema.creationStatements.forEach(::execute)
                    pragmas.writeLong(SqlPragma.USER_VERSION, CanonicalSchema.VERSION.toLong())
                    SchemaTransition(
                        fromVersion = oldVersion,
                        toVersion = CanonicalSchema.VERSION,
                        kind = SchemaTransitionKind.CREATED,
                    )
                }

                oldVersion < CanonicalSchema.VERSION -> {
                    SchemaMigrations.upgrade(
                        session = this,
                        oldVersion = oldVersion,
                        targetVersion = CanonicalSchema.VERSION,
                        context = context,
                    )
                    pragmas.writeLong(SqlPragma.USER_VERSION, CanonicalSchema.VERSION.toLong())
                    SchemaTransition(
                        fromVersion = oldVersion,
                        toVersion = CanonicalSchema.VERSION,
                        kind = SchemaTransitionKind.UPGRADED,
                    )
                }

                oldVersion > CanonicalSchema.VERSION -> {
                    SchemaMigrations.recordDowngrade(
                        session = this,
                        oldVersion = oldVersion,
                        context = context,
                    )
                    pragmas.writeLong(SqlPragma.USER_VERSION, CanonicalSchema.VERSION.toLong())
                    SchemaTransition(
                        fromVersion = oldVersion,
                        toVersion = CanonicalSchema.VERSION,
                        kind = SchemaTransitionKind.DOWNGRADED,
                    )
                }

                else -> SchemaTransition(
                    fromVersion = oldVersion,
                    toVersion = oldVersion,
                    kind = SchemaTransitionKind.UNCHANGED,
                )
            }
        }

    companion object {
        const val DATABASE_NAME: String = CanonicalSchema.DATABASE_NAME
        const val DATABASE_VERSION: Int = CanonicalSchema.VERSION
    }
}
