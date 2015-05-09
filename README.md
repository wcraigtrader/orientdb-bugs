This project is used for demonstrating *features* of OrientDB.

# Graph / ODocument interaction

In OrientDB 1.7.X you can retrieve a document from the graph, and then shutdown the graph, and still access the document.  In Orient 2.0.X, this behavior is not supported.


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

As you can see, when run with OrientDB 2.0.8, the ODocument record will be returned in both cases, but the actual data will not be available if you don't access it before you shutdown the graph.