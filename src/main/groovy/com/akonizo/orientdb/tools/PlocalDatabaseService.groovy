package com.akonizo.orientdb.tools

import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory

class PlocalDatabaseService extends DatabaseService {

    boolean preserve = false

    /**
     * If the database already exists, then use it, but assume that it's already configured.
     * Otherwise create a new database, configure it, and drop it afterwards.
     *
     * @param name
     * @param dir
     */
    PlocalDatabaseService(String name = null, String dir = null) {
        start(name, dir)
        if (!preserve) {
            configure()
        }
    }

    @Override
    void start(String name = null) {
        start(name, null)
    }

    void start(String name, String dir) {
        dbName = name ?: DatabaseTools.uniqueDatabaseName()
        def dbDir = dir ?: System.getProperty('java.io.tmpdir') + '/dbtest'
        def fullPath = new File( dbDir, dbName )
        dbPath = "plocal:${fullPath.absolutePath}"

        preserve = name && dir && fullPath.exists()
        try {
            factory = new OrientGraphFactory(dbPath)
            if (factory.exists() && !preserve) {
                factory.drop()
            }

        } finally {
            if (factory != null) {
                factory.close()
            }
        }

        factory = DatabaseTools.createOrientFactory(dbPath)
        factory.setAutoStartTx(false)
    }

    @Override
    void stop(boolean destroy = true) {
        factory?.close()
        if (destroy && !preserve) {
            factory?.drop()
        }
        factory = null
    }

}
