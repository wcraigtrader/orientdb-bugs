package com.akonizo.orientdb.bugs

import com.orientechnologies.orient.core.record.impl.ODocument
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import groovy.util.logging.Slf4j

import static com.akonizo.orientdb.tools.DatabaseTools.*

/**
 * If you create a Vertex with an EmbeddedMap property,
 * and that Map is defined as Map<String,ORID>, you can
 * persist an ORecordId in the map, but when you retrieve
 * the Map from Orient, Orient will 'help' you by converting
 * the Map into an OrientElementIterator instead.
 */
@Slf4j
class VertexMapBug {
    public static void main(String[] args) {
        log.info "Orient Version = ${OrientGraphFactory.class.package.implementationVersion}"

        OrientGraphFactory factory = createOrientFactory('memory:test')

        withGraphDatabase(factory, false) { graph ->
            doGraphCommands graph, """
-- Initialize database configuration
alter database TIMEZONE UTC
alter database DATETIMEFORMAT yyyy-MM-dd'T'HH:mm:ssXXX
alter database custom useLightweightEdges=false
alter database custom useVertexFieldsForEdgeLabels=true

-- xnode is a vertex class
create class xnode extends V
create property xnode.key STRING
create property xnode.docs EMBEDDEDMAP

-- xdocument is a document class
create class xdocument
create property xdocument.source LINK
create property xdocument.language STRING
"""
        }

        withGraphDatabase(factory, true) { graph ->
            // Create a new graph node
            graph.begin()
            def node = graph.addVertex('class:xnode', [key: 'foo', docs: [:]])
            graph.commit()

            // Create a new document, with link back to node
            graph.begin()
            def doc = new ODocument('xdocument')
            doc.fromMap([source: node.identity, language: 'fr'])
            graph.rawGraph.save(doc)

            // Update the node with a Map entry that links to the document
            def map = node.getProperty('docs')
            assert map instanceof Map

            map['fr'] = doc.getIdentity()
            node.setProperty('docs', map)
            graph.commit()

            log.info "doc  = ${doc.toJSON()}"
            log.info "node = ${node.record.toJSON()}"

            def xmap = node.getProperty('docs')
            assert xmap instanceof Map
        }

        factory.drop()
        factory.close()
    }
}
