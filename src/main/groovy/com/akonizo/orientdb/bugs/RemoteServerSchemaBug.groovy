package com.akonizo.orientdb.bugs

import com.akonizo.orientdb.tools.DatabaseTools
import com.akonizo.orientdb.tools.DateTools
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.core.exception.OQueryParsingException
import com.orientechnologies.orient.server.OServer
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import groovy.util.logging.Slf4j

import static com.akonizo.orientdb.tools.DateTools.TIME_FORMAT
import static com.akonizo.orientdb.tools.DateTools.TIME_ZONE

@Slf4j
class RemoteServerSchemaBug {

    static String VERSION = OrientGraphFactory.class.package.implementationVersion

    static OServer server = null
    static String dbHost = "remote:localhost:4444"

    final static String serverUser = 'root'
    final static String serverPass = 'testing'

    public static void main(String[] args) {
        String dbPath

        log.info "Orient Version = ${VERSION}"

        log.info ''
        log.info "***** Testing against local memory database *****"
        log.info ''

        testWorkingOrderOfOperations('memory:working', true)
        testFailingOrderOfOperations('memory:failing', true)
        testAnotherWayToFail('memory:another', true)
        testThirdWayToFail('memory:third', true)
//        testIdentityStates('memory:identity', true)

        server = startServer()

        log.info ''
        log.info "***** Testing against remote database *****"
        log.info ''

        dbPath = makeRemoteDatabase('remote-working')
        testWorkingOrderOfOperations(dbPath)
        dropRemoteDatabase('remote-working')

        dbPath = makeRemoteDatabase('remote-failing')
        testFailingOrderOfOperations(dbPath)
        dropRemoteDatabase('remote-failing')

        dbPath = makeRemoteDatabase('remote-another')
        testAnotherWayToFail(dbPath)
        dropRemoteDatabase('remote-another')

        dbPath = makeRemoteDatabase('remote-third')
        testThirdWayToFail(dbPath)
        dropRemoteDatabase('remote-third')

//        dbPath = makeRemoteDatabase('remote-identity')
//        testIdentityStates(dbPath)
//        dropRemoteDatabase('remote-identity')

        // Drop the server
        stopServer(server)

        log.info "Test complete"
        System.exit(0)
    }

    /** Test failing order of operations */
    static void testFailingOrderOfOperations(String dbPath, boolean drop = false) {
        OrientGraphFactory factory = null
        OrientBaseGraph graph = null

        log.info "Testing failing order against ${dbPath}"
        try {
            // Create a factory for the database
            factory = new OrientGraphFactory(dbPath, 'admin', 'admin').setupPool(1, 5)
            factory.setAutoStartTx(false)

            // Get Graph First
            graph = factory.tx

            // Define a schema for the database using a newly created non-transactional graph instance
            defineSchema(factory)

            graph.rawGraph.reload()
            log.info """DATETIMEFORMAT = \"${graph.rawGraph.storage.configuration.dateTimeFormat}\""""
            testUsingDatabase(graph)

        } finally {
            // Clean up connections
            graph?.shutdown(true)
            graph = null
            if (drop) {
                factory?.drop()
            }
            factory?.close()
        }
    }

    /** Test working order of operations */
    static void testAnotherWayToFail(String dbPath, boolean drop = false) {
        OrientGraphFactory factory = null
        OrientBaseGraph graph = null

        log.info "Testing another way to fail against ${dbPath}"
        try {
            // Create a factory for the database
            factory = new OrientGraphFactory(dbPath, 'admin', 'admin').setupPool(1, 5)
            factory.setAutoStartTx(false)

            // Create but don't use a transactional graph instance
            graph = factory.tx
            graph.shutdown(true)
            graph = null

            // Define a schema for the database using a newly created non-transactional graph instance
            defineSchema(factory)

            // Get Graph afterwards
            graph = factory.tx

            graph.rawGraph.reload()
            log.info """DATETIMEFORMAT = \"${graph.rawGraph.storage.configuration.dateTimeFormat}\""""
            testUsingDatabase(graph)

        } finally {
            // Clean up connections
            graph?.shutdown(true)
            graph = null
            if (drop) {
                factory?.drop()
            }
            factory?.close()
        }
    }

    /** Test working order of operations */
    static void testThirdWayToFail(String dbPath, boolean drop = false) {
        OrientGraphFactory factory = null
        OrientBaseGraph graph = null, graph2 = null

        log.info "Testing another way to fail against ${dbPath}"
        try {
            // Create a factory for the database
            factory = new OrientGraphFactory(dbPath, 'admin', 'admin').setupPool(1, 5)
            factory.setAutoStartTx(false)

            // Create but don't use a transactional graph instance
            graph = factory.tx

            // Define a schema for the database using a newly created non-transactional graph instance
            defineSchema(factory)

            // Get Graph afterwards
            graph2 = factory.tx

            graph2.rawGraph.reload()
            log.info """DATETIMEFORMAT = \"${graph2.rawGraph.storage.configuration.dateTimeFormat}\""""
            testUsingDatabase(graph2)

        } finally {
            // Clean up connections
            graph2?.shutdown(true)
            graph?.shutdown(true)
            if (drop) {
                factory?.drop()
            }
            factory?.close()
        }
    }

    /** Test working order of operations */
    static void testWorkingOrderOfOperations(String dbPath, boolean drop = false) {
        OrientGraphFactory factory = null
        OrientBaseGraph graph = null

        log.info "Testing working order against ${dbPath}"
        try {
            // Create a factory for the database
            factory = new OrientGraphFactory(dbPath, 'admin', 'admin').setupPool(1, 5)
            factory.setAutoStartTx(false)

            // Define a schema for the database using a newly created non-transactional graph instance
            defineSchema(factory)

            // Get Graph afterwards
            graph = factory.tx
            log.info """DATETIMEFORMAT = \"${graph.rawGraph.storage.configuration.dateTimeFormat}\""""

            testUsingDatabase(graph)

        } finally {
            // Clean up connections
            graph?.shutdown()
            if (drop) {
                factory?.drop()
            }
            factory?.close()
        }
    }

    /** Test identity states */
    static void testIdentityStates(String dbPath, boolean drop = false) {
        OrientGraphFactory factory = null
        OrientBaseGraph graph = null

        log.info "Testing identity states against ${dbPath}"
        try {
            // Create a factory for the database
            factory = new OrientGraphFactory(dbPath, 'admin', 'admin').setupPool(1, 5)
            factory.setAutoStartTx(false)

            // Define a schema for the database using a newly created non-transactional graph instance
            defineSchema(factory)

            // Get Graph afterwards
            graph = factory.tx

            graph.begin()
            def node = graph.addVertex 'class:person', [name: 'Luca', age: 42]
            log.info "${node} => new(${node.identity.new}), temporary(${node.identity.temporary}), persistent(${node.identity.persistent})"

            assert node.identity.new
            assert !node.identity.persistent
            assert node.identity.temporary

            graph.commit()

        } catch (AssertionError e) {
            log.error "***** Node Should be Temporary *****"
            System.err.println e.message
            graph.rollback()

        } finally {
            // Clean up connections
            graph?.shutdown()
            if (drop) {
                factory?.drop()
            }
            factory?.close()
        }
    }

    /** Configure an empty database */
    public static Object defineSchema(OrientGraphFactory factory) {
        return DatabaseTools.withGraphDatabase(factory, false) { OrientBaseGraph g ->
            DatabaseTools.doGraphCommands g, """
                alter database TIMEZONE "${TIME_ZONE}"
                alter database DATETIMEFORMAT "${TIME_FORMAT}"
                alter database custom useLightweightEdges=false
                alter database custom useVertexFieldsForEdgeLabels=true

                create class person extends V
                create property person.created DATETIME
                create property person.name STRING
                create property person.age INTEGER

                create class car extends V
                create property car.name STRING

                create class knows extends E
                create class drives extends E
            """
            g.rawGraph.reload()
        }
    }

    /** Test the capabilities of the database */
    public static void testUsingDatabase(OrientGraph graph) {
        try {
            def date = DateTools.utcToDate(2000, 0, 1, 0, 0, 0)
            def iso = DateTools.dateToISO(date)

            graph.begin()
            def node = graph.addVertex 'class:person', [name: 'Luca', age: 42, created: iso]
            graph.commit()
            def json = node.record.toJSON('')

            assert node.getProperty('created') == date
            assert json.contains("""\"created\":\"${iso}\"""")

            log.info "Test succeeded."

        } catch (OQueryParsingException e) {
            log.error "***** DATETIMEFORMAT NOT DEFINED *****"
            log.error """DATETIMEFORMAT = \"${graph.rawGraph.storage.configuration.dateTimeFormat}\""""
            log.error e.message
        }
    }

    /** Create a new database in the server */
    public static String makeRemoteDatabase(String dbName) {
        def serverAdmin = new OServerAdmin(dbHost).connect(serverUser, serverPass)
        serverAdmin.createDatabase(dbName, 'graph', 'memory')
        serverAdmin.close()
        return "${dbHost}/${dbName}"
    }

    /** Drop the database */
    public static void dropRemoteDatabase(String dbName) {
        def serverAdmin = new OServerAdmin("${dbHost}/${dbName}").connect(serverUser, serverPass)
        serverAdmin.dropDatabase(dbName)
        serverAdmin.close()
    }

    /** Create a server */
    public static void startServer() {
        server = new OServer()
        if (VERSION.startsWith( '2.1.') ) {
            server.startup """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <orient-server>
                    <network>
                        <handlers>
                            <handler class="com.orientechnologies.orient.graph.handler.OGraphServerHandler">
                                <parameters>
                                    <parameter value="true" name="enabled"/>
                                    <parameter value="50" name="graph.pool.max"/>
                                </parameters>
                            </handler>
                        </handlers>
                        <protocols>
                            <protocol implementation="com.orientechnologies.orient.server.network.protocol.binary.ONetworkProtocolBinary" name="binary"/>
                        </protocols>
                        <listeners>
                            <listener protocol="binary" socket="default" port-range="4444-4444" ip-address="127.0.0.1"/>
                        </listeners>
                    </network>
                    <storages/>
                    <users>
                        <user name="${serverUser}" password="${serverPass}" resources="*" />
                    </users>
                    <properties>
                        <entry name="db.pool.min" value="1" />
                        <entry name="db.pool.max" value="5" />
                        <entry name="log.console.level" value="finest" />
                        <entry name="log.file.level" value="fine" />
                        <entry name="plugin.dynamic" value="false" />
                        <entry name="profiler.enabled" value="false" />
                    </properties>
                </orient-server>
                """
        } else if (VERSION.startsWith('2.2.') ) {
            server.startup """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <orient-server>
                <handlers>
                    <handler class="com.orientechnologies.orient.graph.handler.OGraphServerHandler">
                        <parameters>
                            <parameter name="enabled" value="true"/>
                            <parameter name="graph.pool.max" value="50"/>
                        </parameters>
                    </handler>
                </handlers>
                <network>
                    <!--
                    <sockets>
                        <socket implementation="com.orientechnologies.orient.server.network.OServerTLSSocketFactory" name="ssl">
                            <parameters>
                                <parameter value="false" name="network.ssl.clientAuth"/>
                                <parameter value="config/cert/orientdb.ks" name="network.ssl.keyStore"/>
                                <parameter value="password" name="network.ssl.keyStorePassword"/>
                                <parameter value="config/cert/orientdb.ks" name="network.ssl.trustStore"/>
                                <parameter value="password" name="network.ssl.trustStorePassword"/>
                            </parameters>
                        </socket>
                        <socket implementation="com.orientechnologies.orient.server.network.OServerTLSSocketFactory" name="https">
                            <parameters>
                                <parameter value="false" name="network.ssl.clientAuth"/>
                                <parameter value="config/cert/orientdb.ks" name="network.ssl.keyStore"/>
                                <parameter value="password" name="network.ssl.keyStorePassword"/>
                                <parameter value="config/cert/orientdb.ks" name="network.ssl.trustStore"/>
                                <parameter value="password" name="network.ssl.trustStorePassword"/>
                            </parameters>
                        </socket>
                    </sockets>
                    -->
                    <sockets/>
                    <protocols>
                        <protocol name="binary" implementation="com.orientechnologies.orient.server.network.protocol.binary.ONetworkProtocolBinary"/>
                    </protocols>
                    <listeners>
                        <listener protocol="binary" ip-address="0.0.0.0" port-range="4444-4444" socket="default"/>
                    </listeners>
                </network>
                <storages/>
                <users>
                    <user name="${serverUser}" password="${serverPass}" resources="*" />
                </users>
                <properties>
                    <entry name="db.pool.min" value="1"/>
                    <entry name="db.pool.max" value="50"/>
                    <entry name="profiler.enabled" value="false"/>
                </properties>
            </orient-server>
            """
        }
        server.activate()
    }

    /** Stop the server */
    public static void stopServer(OServer server) {
        server?.shutdown()
        server = null
    }

}
