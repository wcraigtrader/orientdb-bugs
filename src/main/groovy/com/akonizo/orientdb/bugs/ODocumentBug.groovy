package com.akonizo.orientdb.bugs

import groovy.util.logging.Slf4j

import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.record.impl.ODocument
import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.impls.orient.*

@Slf4j
class ODocumentBug {

    static void initializeDatabase( String dbpath ) {

        final Parameter<?, ?> UNIQUE_INDEX = new Parameter<String, String>("type", "UNIQUE_HASH_INDEX")

        OrientGraphFactory factory = new OrientGraphFactory(dbpath, 'admin', 'admin')
        OrientGraphNoTx g = null

        try {
            g = factory.getNoTx()

            OrientVertexType v = g.createVertexType( "foo", "V" )
            v.createProperty("key", OType.STRING)
            v.createProperty("data", OType.STRING )

            g.createKeyIndex("key", Vertex.class, new Parameter<String, String>("class", "foo"), UNIQUE_INDEX)

        } catch (Exception e) {
            log( 'Unable to create database', e)
        } finally {
            g?.shutdown()
            factory?.close()
        }
    }

    static OrientVertex createNode( OrientBaseGraph g, String clazz, String key, String data ) {
        log.info("Creating ${clazz} with key ${key}")
        OrientVertex node = g.addVertex( OrientBaseGraph.CLASS_PREFIX + clazz )
        node.setProperty( "key", key )
        node.setProperty( "data", data )
        return node
    }

    static ODocument getDocument( OrientBaseGraph g, String clazz, String key ) {
        Iterable<Vertex> nodes = g.getVertices( "${clazz}.key", key)
        for (Vertex v : nodes ) {
            log.debug("Retrieved ${key} from ${clazz}")
            OrientVertex ov = (OrientVertex) v
            return ov.getRecord()
        }
        return null
    }

    static void main( String[] args ) {

        def dbpath="memory:test"

        initializeDatabase( dbpath )

        OrientGraphFactory f = new OrientGraphFactory( dbpath ).setupPool(1, 10)
        OrientBaseGraph g = f.getTx()
        g.setAutoStartTx( false )
        OrientVertex ann = createNode( g, "foo", "ann", "someone" )
        g.commit()
        g?.shutdown()

        g = f.getNoTx()
        ODocument doc1 = getDocument( g, "foo", "ann" )
        log.info( "Inside graph:  ${doc1}" )
        g?.shutdown()

        g = f.getNoTx()
        ODocument doc2 = getDocument( g, "foo", "ann" )
        g?.shutdown()
        log.info( "Outside graph: ${doc2}" )

        assert doc1.properties == doc2.properties

        if (dbpath.startsWith("memory:") ) f?.drop()
        f?.close()
    }
}