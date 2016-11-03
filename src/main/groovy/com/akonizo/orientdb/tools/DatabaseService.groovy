package com.akonizo.orientdb.tools

import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import groovy.util.logging.Slf4j

@Slf4j
abstract class DatabaseService {

    OrientGraphFactory factory = null

    String dbPath
    String dbName

    abstract void start(String name = null)
    abstract void stop(boolean destroy = true)

    void configure() {
    }

    static String getResourceText(String scriptPath) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader()
            final InputStream script = cl.getResourceAsStream(scriptPath)
            if (script) {
                return script?.text
            }

        } catch (IOException e) {
            log.error "While loading resource ${scriptPath}: ${e.message}"
        }

        return ''
    }
}


