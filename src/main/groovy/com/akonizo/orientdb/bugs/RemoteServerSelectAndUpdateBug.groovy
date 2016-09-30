package com.akonizo.orientdb.bugs

import com.akonizo.orientdb.tools.Data
import com.akonizo.orientdb.tools.TransactionLogListener
import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.server.OServer
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientElement
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import com.tinkerpop.blueprints.impls.orient.OrientVertex
import groovy.util.logging.Slf4j

import static com.akonizo.orientdb.tools.DatabaseTools.*
import static com.akonizo.orientdb.tools.DateTools.TIME_FORMAT
import static com.akonizo.orientdb.tools.DateTools.TIME_ZONE

@Slf4j
class RemoteServerSelectAndUpdateBug {

    static String ORIENT_VERSION = OrientGraphFactory.class.package.implementationVersion

    static OServer server = null
    static String dbHost = "remote:localhost:4444"

    final static String serverUser = 'root'
    final static String serverPass = 'testing'

    static int quantity = 5000

    static void main(String[] args) {

        switch ( args.length ) {
            case 1: quantity = args[0] as int
        }

        log.info "Orient Version = ${ORIENT_VERSION}"

        log.info ''
        log.info "***** Testing against local memory database *****"
        log.info ''

        testWorkingOrderOfOperations('memory:working', true)

        server = startServer()

        log.info ''
        log.info "***** Testing against remote database *****"
        log.info ''

        def dbPath = makeRemoteDatabase('remote-working')
        testWorkingOrderOfOperations(dbPath)
        dropRemoteDatabase('remote-working')

        // Drop the server
        stopServer(server)

        log.info ''
        log.info "***** Test complete *****"
        System.exit(0)
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

            log.info "===== Add some data ====="
            def data = new Data( 12345678 ) // Always use the same data sequence

            graph.begin()
            for (int i : 1..quantity) {
                graph.addVertex 'class:person', [name: data.randomName, age: data.randomAge]
                if (i % 100) {
                    graph.commit()
                    graph.begin()
                }
            }
            graph.commit()

            def now = System.currentTimeMillis()

            log.info "===== Select and modify the data using getVertices() ====="
            withNodesOfClass( graph, 'person' ) { OrientVertex node ->
                graph.begin()
                log.trace "read ${node.record.toJSON()}"
                node.setProperty( 'created', now )
                graph.commit()
            }

            log.info "===== Select and modify the data with an asynchronous query ====="
            log.warn "The first call to commit() may fail with a remote connection AND enough selected data"
            withGraphQuery( graph, 'SELECT FROM person' ) { OrientElement node, long unused ->
                graph.begin()
                log.trace "read ${node.record.toJSON()}"
                node.setProperty( 'modified', now )
                graph.commit()
            }

            log.info "Test succeeded."


        } finally {
            // Clean up connections
            graph?.rawGraph?.resetListeners()
            graph?.shutdown()
            if (drop) {
                factory?.drop()
            }
            factory?.close()
        }
    }

    /** Configure an empty database */
    public static Object defineSchema(OrientGraphFactory factory) {
        return withGraphDatabase(factory, false) { OrientBaseGraph g ->
            doGraphCommands g, """
                alter database TIMEZONE "${TIME_ZONE}"
                alter database DATETIMEFORMAT "${TIME_FORMAT}"
                alter database custom useLightweightEdges=false
                alter database custom useVertexFieldsForEdgeLabels=true

                create class person extends V
                create property person.name STRING
                create property person.age INTEGER
                create property person.created DATETIME
                create property person.modified DATETIME

                create class car extends V
                create property car.name STRING

                create class knows extends E
                create class drives extends E
            """
            g.rawGraph.reload()
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
        if (ORIENT_VERSION.startsWith('2.1.')) {
            server.startup """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <orient-server>
                    <handlers>
                        <handler class="com.orientechnologies.orient.graph.handler.OGraphServerHandler">
                            <parameters>
                                <parameter value="true" name="enabled"/>
                                <parameter value="50" name="graph.pool.max"/>
                            </parameters>
                        </handler>
                    </handlers>
                    <network>
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
                        <entry name="db.pool.max" value="${MAX_POOL_SIZE}" />
                        <entry name="log.console.level" value="info" />
                        <entry name="log.file.level" value="fine" />
                        <entry name="plugin.dynamic" value="false" />
                        <entry name="profiler.enabled" value="false" />
                    </properties>
                </orient-server>
                """
        } else if (ORIENT_VERSION.startsWith('2.2.')) {
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
                        <entry name="db.pool.max" value="${MAX_POOL_SIZE}"/>
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
