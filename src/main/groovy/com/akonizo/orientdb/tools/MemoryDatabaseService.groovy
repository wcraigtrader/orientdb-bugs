package com.akonizo.orientdb.tools

class MemoryDatabaseService extends DatabaseService {

    MemoryDatabaseService(String name = null) {
        start(name)
        configure()
    }

    @Override
    void start(String name = null) {
        dbName = name ?: DatabaseTools.uniqueDatabaseName()
        dbPath = "memory:${dbName}"
        factory = DatabaseTools.createOrientFactory(dbPath)
        factory.setAutoStartTx(false)
    }

    @Override
    void stop(boolean destroy = true) {
        factory?.close()
        factory?.drop()
        factory = null
    }

}
