package com.akonizo.orientdb.bugs

import com.akonizo.orientdb.tools.DatabaseService
import com.akonizo.orientdb.tools.RemoteDatabaseService
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import com.tinkerpop.blueprints.impls.orient.OrientVertex
import groovy.util.logging.Slf4j

import static com.akonizo.orientdb.tools.DatabaseTools.*

@Slf4j
class CaseInsensitiveIndexing {
    static DatabaseService service
    static OrientGraphFactory factory

    static void main(String[] args) {
        try {
            service = new RemoteDatabaseService('bug5')
            factory = service.factory

            withGraphDatabase(factory, false) { OrientBaseGraph graph ->
                doGraphCommands graph, """
                    alter database custom useLightweightEdges=false
                    alter database custom useVertexFieldsForEdgeLabels=true

                    create class foo extends V
                    create property foo.key STRING
                    create property foo.uuid STRING

                    create index foo.key on foo (key COLLATE CI) unique
                    create index foo.uuid on foo (uuid) unique
                """
                graph.rawGraph.reload()
            }

            test_null_keys()
            test_duplicate_keys()

            withGraphDatabase(factory, true) { OrientBaseGraph graph ->
                log.info "----- All foo records -----"
                withNodesOfClass(graph, 'foo') { OrientVertex node ->
                    log.info "${node.identity} <= ${node.record.toJSON()}"
                }

                log.info "----- All foo.key records -----"
                withDocumentQuery(graph.rawGraph, "SELECT FROM index:foo.key") { ODocument node, int i ->
                    log.info "${node.toJSON()}"
                }

                log.info "----- All foo.uuid records -----"
                withDocumentQuery(graph.rawGraph, "SELECT FROM index:foo.uuid") { ODocument node, int i ->
                    log.info "${node.toJSON()}"
                }
            }

        } catch (Throwable t) {
            log.error 'Unexpected exception', t

        } finally {
            factory?.close()
            service?.stop()
        }
    }

    static void test_null_keys() {
        OrientVertex v1 = null, v2 = null, v3 = null, v4 = null

        withGraphDatabase(factory, true) { OrientBaseGraph graph ->
            graph.begin()
            v1 = graph.addVertex 'class:foo', [uuid: 'bar1']
            graph.commit()
            log.info "Inserted ${v1}"

            try {
                graph.begin()
                v2 = graph.addVertex 'class:foo', [uuid: 'bar2']
                graph.commit() // this should fail, but doesn't

                graph.begin()
                v3 = graph.addVertex 'class:foo', [uuid: 'bar3']
                graph.commit()

                graph.begin()
                v4 = graph.addVertex 'class:foo', [uuid: 'bar4']
                graph.commit()

                log.error "***** Allowed inserting duplicate (case-insensitive) keys (each null)"

            } catch (ORecordDuplicatedException e) {
                log.info "Unable to add duplicate key, as desired ($e.message)"
            }
        }
    }

    static void test_duplicate_keys() {
        OrientVertex v1 = null, v2 = null, v3 = null, v4 = null

        withGraphDatabase(factory, true) { OrientBaseGraph graph ->
            graph.begin()
            v1 = graph.addVertex 'class:foo', [key: 'BAR', uuid: 'bar5']
            graph.commit()

            try {
                graph.begin() // this should fail
                v2 = graph.addVertex 'class:foo', [key: 'bar', uuid: 'bar6']
                graph.commit()

                log.error "***** Allowed inserting a duplicate (case-insensitive) key (${v2.getProperty('key')})"

            } catch (ORecordDuplicatedException e) {
                log.info "Unable to add duplicate key, as desired ($e.message)"
            }
        }
    }
}
