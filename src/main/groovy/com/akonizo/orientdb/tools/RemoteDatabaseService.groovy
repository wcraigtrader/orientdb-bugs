package com.akonizo.orientdb.tools

import com.orientechnologies.orient.client.remote.OServerAdmin
import com.orientechnologies.orient.server.OServer

class RemoteDatabaseService extends DatabaseService {
    static String dbHost
    static OServer server = null
    static List<String> names = []

    final static String serverUser = 'root'
    final static String serverPass = 'testing'

    final static String dbMedia = 'memory' // or 'plocal'

    static String ORIENT_VERSION = OServer.class.package.implementationVersion

    RemoteDatabaseService(String name = null) {
        start(name)
        configure()
    }

    void start(String name = null) {
        dbName = name ?: DatabaseTools.uniqueDatabaseName()

        synchronized (names) {
            makeRemoteServer()
            names.add(dbName)
        }

        def serverAdmin = new OServerAdmin(dbHost).connect(serverUser, serverPass)
        serverAdmin.createDatabase(dbName, 'graph', dbMedia)
        serverAdmin.close()

        dbPath = "${dbHost}/${dbName}"
        factory = DatabaseTools.createOrientFactory(dbPath)
        factory.setAutoStartTx(false)
    }

    @Override
    void stop(boolean destroy = true) {
        factory?.close()
        factory = null

        def serverAdmin = new OServerAdmin(dbPath).connect(serverUser, serverPass)
        serverAdmin.dropDatabase(dbName)
        serverAdmin.close()

        synchronized (names) {
            names.remove(dbName)
            if (names.size() == 0) {
                stopRemoteServer()
            }
        }
    }

    static void makeRemoteServer() {
        if (server) return

        server = new OServer(false)
        if (ORIENT_VERSION.startsWith( '2.1.')) {
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
                        <entry name="db.pool.max" value="${DatabaseTools.MAX_POOL_SIZE}" />
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
                        <entry name="db.pool.max" value="${DatabaseTools.MAX_POOL_SIZE}"/>
                        <entry name="profiler.enabled" value="false"/>
                    </properties>
                </orient-server>
                """
        }
        server.activate()
        dbHost = "remote:localhost:4444"
    }

    static void stopRemoteServer() {
        server?.shutdown()
        server = null
        dbHost = null
    }
}
