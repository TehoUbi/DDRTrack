package com.tehoubi.ddrtracker.server

/** Settings from the environment, with defaults for a local Postgres. */
data class ServerConfig(
    val port: Int = 8080,
    val databaseUrl: String = "jdbc:postgresql://localhost:5432/ddrtracker",
    val databaseUser: String = "ddrtracker",
    val databasePassword: String = "ddrtracker",
    val maxPoolSize: Int = 10,
    /** Registrations allowed per client address per hour. */
    val registrationsPerHour: Int = 10,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()) = ServerConfig(
            port = env["PORT"]?.toInt() ?: 8080,
            databaseUrl = env["DATABASE_URL"] ?: ServerConfig().databaseUrl,
            databaseUser = env["DATABASE_USER"] ?: ServerConfig().databaseUser,
            databasePassword = env["DATABASE_PASSWORD"] ?: ServerConfig().databasePassword,
            maxPoolSize = env["DATABASE_POOL_SIZE"]?.toInt() ?: 10,
            registrationsPerHour = env["REGISTRATIONS_PER_HOUR"]?.toInt() ?: 10,
        )
    }
}
