package com.akonizo.orientdb.bugs

import groovy.util.logging.Slf4j

import com.orientechnologies.orient.core.metadata.schema.OType
import com.tinkerpop.blueprints.Parameter
import com.tinkerpop.blueprints.Vertex
import com.tinkerpop.blueprints.impls.orient.*

@Slf4j
public class TBIndexing {
    public final static String url = "memory:test" // "plocal:/Users/ctrader/db/tb" //

    public static void main(String[] args) {
        OrientGraphFactory factory = new OrientGraphFactory(url, "admin", "admin")
        // ... create database ...
        OrientGraphNoTx graphNoTx = factory.getNoTx()
        OrientVertexType person = graphNoTx.createVertexType("Person")
        person.createProperty("name", OType.STRING)
        person.createProperty("age", OType.INTEGER)

        //        person.createIndex("person.nameIdx", OClass.INDEX_TYPE.UNIQUE, "name")

        final Parameter<?, ?> UNIQUE_INDEX = new Parameter<String, String>("type", "UNIQUE_HASH_INDEX")
        graphNoTx.createKeyIndex("name", Vertex.class, new Parameter<String, String>("class", "Person"), UNIQUE_INDEX)

        //        OCommandSQL cmd = new OCommandSQL()
        //        cmd.setText("create index Person.name on Person (name) unique" )
        //        graphNoTx.command(cmd).execute()

        graphNoTx.shutdown()

        // ... populate data ...

        long t = System.currentTimeMillis()
        OrientGraph graph = factory.getTx()
        // graph.setAutoStartTx( false )
        for (int i=0;i<100;i++) {
            try {
                Map<String,Object> props = new HashMap<String,Object>()
                props.put("name", "Jill")
                props.put("age", 33)

                OrientVertex v1 = graph.addVertex("class:Person" )
                v1.setProperties( props )
                v1.save()
                graph.commit()
            } catch (Exception e) {
                log.error("${e.message}")
                graph.rollback()
            }
        }
        System.out.println("Time: "+(System.currentTimeMillis()-t))
        Iterator<Vertex> it = graph.getVerticesOfClass("Person").iterator()
        System.out.println(it.next().getProperty("name"))
        System.out.println(it.next().getProperty("name"))
        graph.shutdown()

    }
}