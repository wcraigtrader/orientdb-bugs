package com.akonizo.orientdb.bugs

import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import groovy.util.logging.Slf4j

import static com.akonizo.orientdb.tools.DatabaseTools.*

@Slf4j
class OFunctionIndexingBug {

    static File dbFile
    static String dbPath

    static void main(String[] args) {
        def version = args[0]
        def majMin = version.split('[.]')[0..1].join('.')

        switch (majMin) {
            case '2.0':
                assert version >= '2.0.10'
                log.info "Create initial database and add a function"
                setDatabase('20')
                dbFile.deleteDir()
                createGraphDatabase(dbPath)
                withGraphFactory(dbPath) { OrientGraphFactory factory ->
                    withGraphDatabase(factory, false) { OrientBaseGraph graph ->
                        doGraphCommands graph, """
                           CREATE FUNCTION fun20 "return list?.countBy { String s -> s }" PARAMETERS [ list ] IDEMPOTENT true LANGUAGE groovy
                        """
                    }
                    backupDatabase(factory, new File("backup-20.zip"))
                }
                log.info "Now re-run with -POV=2.1.22"
                break

            case '2.1':
                log.info "Test upgrading older database and adding a function"
                setDatabase('21')
                dbFile.deleteDir()
                def bkup = new File('backup-20.zip')
                createGraphDatabase(dbPath)
                if (bkup.exists()) {
                    withGraphFactory(dbPath) { OrientGraphFactory factory ->
                        restoreDatabase(factory, bkup)
                    }
                }
                withGraphFactory(dbPath) { OrientGraphFactory factory ->
                    withGraphDatabase(factory, false) { OrientBaseGraph graph ->
                        doGraphCommands graph, """
                           CREATE FUNCTION fun21 "return list?.countBy { String s -> s }" PARAMETERS [ list ] IDEMPOTENT true LANGUAGE groovy
                        """
                    }
                    backupDatabase(factory, new File("backup-21.zip"))
                }

                log.info "Now re-run with -POV=2.2.19"
                break

            case '2.2':
                log.info "Test upgrading older database again and adding another function"
                setDatabase('22')
                dbFile.deleteDir()
                def bkup = new File('backup-21.zip')
                createGraphDatabase(dbPath)
                if (bkup.exists()) {
                    withGraphFactory(dbPath) { OrientGraphFactory factory ->
                        restoreDatabase(factory, bkup)
                    }
                }
                withGraphFactory(dbPath) { OrientGraphFactory factory ->
                    withGraphDatabase(factory, false) { OrientBaseGraph graph ->
                        doGraphCommands graph, """
                           CREATE FUNCTION fun22 "return list?.countBy { String s -> s }" PARAMETERS [ list ] IDEMPOTENT true LANGUAGE groovy
                        """
                    }
                    backupDatabase(factory, new File("backup-22.zip"))
                }
                break

            default:
                log.error "Did not recognize OrientDB ${version}"
        }
    }

    static void setDatabase(String version) {
        dbFile = new File("function-database-${version}")
        dbPath = "plocal:${dbFile.absolutePath}" as String
    }
}
