This project is used for demonstrating *features* of OrientDB.

# Updating data selected from an asynchronous query MAY cause OrientDB to hang if the query returns enough data

[Test class](src/main/groovy/com/akonizo/orientdb/bugs/RemoteServerSelectAndUpdateBug.groovy)

In OrientDB 2.1.22, when selecting data using an asynchronous query, if you're also updating 
the records returned by the query, the transaction may hang when `commit()` is called. 
This appears to be dependent upon the number of records returned by the query.
 
To reproduce this, use Gradle to run the application:
```
$ ./gradlew run [ -Psize=5000 ] [ -POV=2.1.22 ]
```

* You can vary the OrientDB version, by changing `OV`.
* You can vary the number of records written and read by changing `size`.
 
With OrientDB 2.1.22, 5000 records seems to be enough; with Orient 2.2.7, 6000 records seems to be enough. 
Below that, the application will complete; above that, the first commit() inside of a SELECT query will hang.

# Server Databases graph connections must not overlap

[Test class](src/main/groovy/com/akonizo/orientdb/bugs/RemoteServerSchemaBug.groovy)

In OrientDB 2.1.20, when connecting to a remote database with a OrientGraphFactory with autoStartTx disabled, you cannot have overlapped graph connections within a single thread. This works with local memory databases, and local physical databases (plocal:). 

In the example code, I create a remote database, then:
 1. Populate it's schema using a non-transactional graph, and 
 1. Test that Orient is respecting that schema using a transactional graph.

In the case where those graph instances are created out of sequence, it still succeeds with a memory/plocal connection, but fails on a server:
 * def g1 = factory.tx
 * def g2 = factory.noTx
 * ... use g2
 * g2.shutdown()
 * ... use g1
 * g1.shutdown()

Another simpler way to fail:
 * def g1 = factory.tx
 * g1.shutdown()
 * def g2 = factory.noTx
 * ... use g2
 * g2.shutdown()
 * def g3 = factory.tx
 * ... use g3
 * g3.shutdown()

Observed quirk, possibly related: When using a remote database with a OrientGraphFactory with autoStartTx disabled, commands executed against a non-transactional connection (as you must use to manipulate the schema) will cause this message to appear in the logs, even when the connections are not overlapped:

```
The command 'Committing the active transaction to create the new type 'person' as subclass of 'V'. The transaction will be reopen right after that. To avoid this behavior create the classes outside the transaction' must be executed outside an active transaction: the transaction will be committed and reopen right after it. To avoid this behavior execute it outside a transaction (db=remote-working)
```

Example run:

```
2016-08-20 14:58:55 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Orient Version = 2.1.20
2016-08-20 14:58:55 INFO  main                      c.a.o.b.RemoteServerSchemaBug -
2016-08-20 14:58:55 INFO  main                      c.a.o.b.RemoteServerSchemaBug - ***** Testing against local memory database *****
2016-08-20 14:58:55 INFO  main                      c.a.o.b.RemoteServerSchemaBug -
2016-08-20 14:58:55 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Testing working order against memory:working
2016-08-20 14:58:56 INFO  main                      com.orientechnologies - OrientDB auto-config DISKCACHE=10,695MB (heap=3,641MB os=16,384MB disk=27,344MB)
2016-08-20 14:58:56 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Test succeeded.
2016-08-20 14:58:56 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Testing failing order against memory:failing
2016-08-20 14:58:56 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Test succeeded.
2016-08-20 14:58:56 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Testing another way to fail against memory:another
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Test succeeded.
2016-08-20 14:58:57 INFO  main                      c.o.o.s.c.OServerConfigurationLoaderXml - Loading configuration from input stream
2016-08-20 14:58:57 INFO  main                      c.o.o.server.OServer - OrientDB Server v2.1.20 is starting up...
2016-08-20 14:58:57 INFO  main                      c.o.o.server.OServer - Databases directory: /Users/ctrader/Workspace/orientbugs/./databases
2016-08-20 14:58:57 INFO  main                      c.o.o.s.n.OServerNetworkListener - Listening binary connections on 127.0.0.1:4444 (protocol v.32, socket=default)
2016-08-20 14:58:57 INFO  main                      c.o.o.server.OServer - OrientDB Server v2.1.20 is active.
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug -
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug - ***** Testing against remote database *****
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug -
2016-08-20 14:58:57 INFO  OrientDB <- BinaryClient (/127.0.0.1:62454) c.o.o.s.n.p.b.ONetworkProtocolBinary - {db=remote-working} Created database 'remote-working' of type 'memory'
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Testing working order against remote:localhost:4444/remote-working
2016-08-20 14:58:57 WARN  main                      c.t.b.i.o.OrientGraph - The command 'Committing the active transaction to create the new type 'person' as subclass of 'V'. The transaction will be reopen right after that. To avoid this behavior create the classes outside the transaction' must be executed outside an active transaction: the transaction will be committed and reopen right after it. To avoid this behavior execute it outside a transaction (db=remote-working)
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Test succeeded.
2016-08-20 14:58:57 INFO  OrientDB <- BinaryClient (/127.0.0.1:62456) c.o.o.s.n.p.b.ONetworkProtocolBinary - {db=remote-working} Dropped database 'remote-working'
2016-08-20 14:58:57 INFO  OrientDB <- BinaryClient (/127.0.0.1:62457) c.o.o.s.n.p.b.ONetworkProtocolBinary - {db=remote-failing} Created database 'remote-failing' of type 'memory'
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Testing failing order against remote:localhost:4444/remote-failing
2016-08-20 14:58:57 WARN  main                      c.t.b.i.o.OrientGraph - The command 'Committing the active transaction to create the new type 'person' as subclass of 'V'. The transaction will be reopen right after that. To avoid this behavior create the classes outside the transaction' must be executed outside an active transaction: the transaction will be committed and reopen right after it. To avoid this behavior execute it outside a transaction (db=remote-failing)
2016-08-20 14:58:57 ERROR main                      c.a.o.b.RemoteServerSchemaBug - ***** DATETIMEFORMAT NOT DEFINED *****
2016-08-20 14:58:57 ERROR main                      c.a.o.b.RemoteServerSchemaBug - Error on parsing command at position #0: Error on conversion of date '2000-01-01T00:00:00Z' using the format: yyyy-MM-dd HH:mm:ss
2016-08-20 14:58:57 INFO  OrientDB <- BinaryClient (/127.0.0.1:62459) c.o.o.s.n.p.b.ONetworkProtocolBinary - {db=remote-failing} Dropped database 'remote-failing'
2016-08-20 14:58:57 INFO  OrientDB <- BinaryClient (/127.0.0.1:62460) c.o.o.s.n.p.b.ONetworkProtocolBinary - {db=remote-another} Created database 'remote-another' of type 'memory'
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Testing another way to fail against remote:localhost:4444/remote-another
2016-08-20 14:58:57 WARN  main                      c.t.b.i.o.OrientGraph - The command 'Committing the active transaction to create the new type 'person' as subclass of 'V'. The transaction will be reopen right after that. To avoid this behavior create the classes outside the transaction' must be executed outside an active transaction: the transaction will be committed and reopen right after it. To avoid this behavior execute it outside a transaction (db=remote-another)
2016-08-20 14:58:57 ERROR main                      c.a.o.b.RemoteServerSchemaBug - ***** DATETIMEFORMAT NOT DEFINED *****
2016-08-20 14:58:57 ERROR main                      c.a.o.b.RemoteServerSchemaBug - Error on parsing command at position #0: Error on conversion of date '2000-01-01T00:00:00Z' using the format: yyyy-MM-dd HH:mm:ss
2016-08-20 14:58:57 INFO  OrientDB <- BinaryClient (/127.0.0.1:62462) c.o.o.s.n.p.b.ONetworkProtocolBinary - {db=remote-another} Dropped database 'remote-another'
2016-08-20 14:58:57 INFO  main                      c.a.o.b.RemoteServerSchemaBug - Test complete
```

# Embedded Maps with ORecordID values fail

[Test class](src/main/groovy/com/akonizo/orientdb/bugs/VertexMapBug.groovy)

In OrientDB < 2.1.20, if you create a Vertex with an EmbeddedMap property, and that Map is defined as Map<String,ORID>, you can persist an ORecordId in the map, but when you retrieve the Map from Orient, Orient will 'help' you by converting the Map into an OrientElementIterator instead.

# Graph / ODocument interaction

[Test class](src/main/groovy/com/akonizo/orientdb/bugs/ODocumentBug.groovy)

In OrientDB 1.7.X you can retrieve a document from the graph, and then shutdown the graph, and still access the document.  In Orient 2.0.X, this behavior is not supported.

```
$ ./gradlew -q -POV=1.7.8 run
May 08, 2015 10:46:05 PM com.orientechnologies.common.log.OLogManager log
WARNING: Current implementation of storage does not support sbtree collections
May 08, 2015 10:46:06 PM com.orientechnologies.common.log.OLogManager log
WARNING: Current implementation of storage does not support sbtree collections
May 08, 2015 10:46:06 PM com.orientechnologies.common.log.OLogManager log
WARNING: Current implementation of storage does not support sbtree collections
2015-05-08 22:46:06 INFO  c.a.o.b.ODocumentBug - Creating foo with key ann
2015-05-08 22:46:06 INFO  c.a.o.b.ODocumentBug - Inside graph:  foo#11:0{key:ann,data:someone} v0
2015-05-08 22:46:06 INFO  c.a.o.b.ODocumentBug - Outside graph: foo#11:0{key:ann,data:someone} v0

$ ./gradlew -q -POV=2.0.8 run
May 08, 2015 10:46:13 PM com.orientechnologies.common.log.OLogManager log
INFO: OrientDB auto-config DISKCACHE=10,695MB (heap=3,641MB os=16,384MB disk=156,266MB)
2015-05-08 22:46:14 INFO  c.a.o.b.ODocumentBug - Creating foo with key ann
2015-05-08 22:46:14 INFO  c.a.o.b.ODocumentBug - Inside graph:  foo#11:0{key:ann,data:someone} v1
2015-05-08 22:46:14 INFO  c.a.o.b.ODocumentBug - Outside graph: foo#11:0 v1
Exception in thread "main" Assertion failed:

assert doc1.properties == doc2.properties
       |    |          |  |    |
       |    |          |  |    [contentChanged:false, trackingChanges:true, lazyLoad:true, allowChainedAccess:true, ordered:true, size:28, empty:true, owner:null, 
       |    |          |  |     class:class com.orientechnologies.orient.core.record.impl.ODocument, className:foo, databaseIfDefined:null, version:1, 
       |    |          |  |     internalStatus:LOADED, owners:[], recordVersion:1, dirtyFields:[], recordType:100, databaseIfDefinedInternal:null, 
       |    |          |  |     record:foo#11:0 v1, identity:#11:0, immutableSchemaClass:foo, schemaClass:null, dirty:false, embedded:false]
       |    |          |  foo#11:0 v1
       |    |          false
       |    [contentChanged:false, trackingChanges:true, lazyLoad:true, allowChainedAccess:true, ordered:true, size:28, empty:false, owner:null, 
       |     class:class com.orientechnologies.orient.core.record.impl.ODocument, className:foo, databaseIfDefined:null, version:1, 
       |     internalStatus:LOADED, owners:[], recordVersion:1, dirtyFields:[], recordType:100, databaseIfDefinedInternal:null, 
       |     record:foo#11:0{key:ann,data:someone} v1, identity:#11:0, immutableSchemaClass:foo, schemaClass:null, dirty:false, embedded:false]
       foo#11:0{key:ann,data:someone} v1

    at org.codehaus.groovy.runtime.InvokerHelper.assertFailed(InvokerHelper.java:399)
    at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.assertFailed(ScriptBytecodeAdapter.java:648)
    at com.akonizo.orientdb.bugs.ODocumentBug.main(ODocumentBug.groovy:78)
```

As you can see, when run with OrientDB 2.0.8, the ODocument record will be returned in both cases, but the actual data will not be available if you don't access it before you shutdown the graph.
