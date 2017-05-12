package com.akonizo.orientdb.bugs

import com.orientechnologies.orient.core.index.OCompositeKey
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import com.tinkerpop.blueprints.impls.orient.OrientVertex
import groovy.util.logging.Slf4j

import static com.akonizo.orientdb.tools.DatabaseTools.*

/**
 * Created by ctrader on 1/19/17.
 */
@Slf4j
class OCompositeKeyBug {
    static void main(String[] args) {
        log.info "Orient Version = ${OrientGraphFactory.class.package.implementationVersion}"

        OrientGraphFactory factory = createOrientFactory('memory:test')

        withGraphDatabase(factory, false) { OrientBaseGraph graph ->
            doGraphCommands graph, """
-- Initialize database configuration
alter database TIMEZONE UTC
alter database DATETIMEFORMAT yyyy-MM-dd'T'HH:mm:ssXXX
alter database custom useLightweightEdges=false
alter database custom useVertexFieldsForEdgeLabels=true
alter database custom sqlStrict=false

-- Add link properties for proper edge indexing
create property E.in LINK
create property E.out LINK

-- account is a vertex class
create class account extends V
create property account.description STRING
create property account.namespace STRING
create property account.name STRING
create property account.first INTEGER
create property account.second INTEGER

create class knows extends E

create index account.composite on account (name, namespace) unique
create index knows.unique on knows (out,in) unique
"""
        }

        withGraphDatabase(factory, true) { OrientBaseGraph graph ->
            // Create a new graph node
            graph.begin()
            def v1 = graph.addVertex('class:account', [name: 'foo', namespace: 'bar', description: 'foobar', first: 111, second: 222])
            def v2 = graph.addVertex('class:account', [name: 'foo', namespace: 'baz', description: 'foobaz', first: 333, second: 444])
            def e1 = v1.addEdge(class: knows, v2)
            graph.commit()

            log.info "Retrive a node via the index, using SQL"
            withGraphQuery(graph, "select from index:account.composite where key = [ 'foo', 'bar' ]") { OrientVertex node, i ->
                log.info "row ${i} => ${node.record.toJSON()}"
            }

            try {
                log.info "Retrieve a node via the Graph API"
                def node = graph.getVertices('account.composite', new OCompositeKey('foo', 'bug'))
            } catch (Exception e) {
                log.error "Failed to select node: ${e.message}"
                log.error "Stacktrace", e
            }

            log.info "Retrieve an edge via the Graph API"
        }

        factory.drop()
        factory.close()
    }
}
