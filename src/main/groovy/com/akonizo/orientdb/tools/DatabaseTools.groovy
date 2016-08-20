package com.akonizo.orientdb.tools

import com.orientechnologies.common.exception.OException
import com.orientechnologies.orient.core.command.OCommandExecutorNotFoundException
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.command.OCommandResultListener
import com.orientechnologies.orient.core.db.ODatabaseListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.db.record.ridbag.ORidBag
import com.orientechnologies.orient.core.exception.OCommandExecutionException
import com.orientechnologies.orient.core.exception.OConcurrentModificationException
import com.orientechnologies.orient.core.exception.OSchemaException
import com.orientechnologies.orient.core.id.ORID
import com.orientechnologies.orient.core.record.ORecord
import com.orientechnologies.orient.core.record.impl.ODocument
import com.orientechnologies.orient.core.sql.OCommandSQL
import com.orientechnologies.orient.core.sql.OCommandSQLParsingException
import com.orientechnologies.orient.core.sql.query.OSQLAsynchQuery
import com.orientechnologies.orient.core.sql.query.OSQLQuery
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph
import com.tinkerpop.blueprints.impls.orient.OrientElement
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory
import com.tinkerpop.blueprints.impls.orient.OrientVertex
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

/**
 * This class contains methods for working with OrientDB's document and graph APIs.
 *
 * The methods are all static so that they can be used in a multi-threaded environment.
 */
@Slf4j
class DatabaseTools {

    public static final int BACKUP_COMPRESSION_LEVEL = 1
    public static final int BACKUP_BUFFER_SIZE = 1024 * 1024
    public static final int MAX_POOL_SIZE = 5

    /** Create a Graph Factory, with auto-start transactions disabled */
    static OrientGraphFactory createOrientFactory(
            String dbPath, String dbUser = OrientBaseGraph.ADMIN, String dbPass = 'admin',
            int minPool = 1, int maxPool = MAX_POOL_SIZE) {
        def factory = new OrientGraphFactory(dbPath, dbUser, dbPass).setupPool(minPool, maxPool);
        factory.setAutoStartTx(false);
        return factory;
    }

    /** Open a database using the Document API */
    static def withDocumentDatabase(String dbPath, String dbUser, String dbPass, Closure<?> closure) {
        ODatabaseDocumentTx db = null

        assert dbPath
        assert dbUser
        assert dbPass

        try {
            log.debug "Connecting to ${dbUser}@${dbPath} ..."
            db = new ODatabaseDocumentTx(dbPath).open(dbUser, dbPass)
            return withDatabaseConnection(db, closure)

        } finally {
            try {
                db?.close()
            } catch (Exception e) {
                log.error "DB close exception", e
            }
        }

    }

    /** Open a connection using the Document API */
    static def withDocumentDatabase(OrientGraphFactory factory, boolean transactional = true, Closure<?> closure) {
        OrientBaseGraph graph = null

        assert factory

        try {
            log.debug "Connecting to ${factory} ..."
            graph = transactional ? factory.tx : factory.noTx
            return withDatabaseConnection(graph.rawGraph, closure)

        } finally {
            try {
                graph?.shutdown(true, transactional)
            } catch (Exception e) {
                log.error "DB close exception", e
            }
        }
    }

    /** Open a database using the Graph API */
    static def withGraphDatabase(String dbPath, String dbUser, String dbPass, boolean transactional = true, Closure<?> closure) {
        OrientGraphFactory factory = null
        OrientBaseGraph graph = null

        assert dbPath
        assert dbUser
        assert dbPass

        try {
            log.debug "Connecting to ${dbUser}@${dbPath} ..."
            factory = createOrientFactory(dbPath, dbUser, dbPass, 1, 1)
            graph = transactional ? factory.tx : factory.noTx
            return withDatabaseConnection(graph, closure)

        } finally {
            try {
                graph?.shutdown(true, transactional)
                factory?.close()
            } catch (Exception e) {
                log.error "DB close exception", e
            }
        }
    }

    /** Open a connection using the Graph API */
    static def withGraphDatabase(OrientGraphFactory factory, boolean transactional = true, Closure<?> closure) {
        OrientBaseGraph graph = null

        assert factory

        try {
            log.debug "Connecting to ${factory} ..."
            graph = transactional ? factory.tx : factory.noTx
            return withDatabaseConnection(graph, closure)

        } finally {
            try {
                graph?.shutdown(true, transactional)
            } catch (Exception e) {
                log.error "DB close exception", e
            }
        }
    }

    /** Common error handling for database connections */
    static private def withDatabaseConnection(Object connection, Closure closure) {
        def result = null
        try {
            result = closure.call(connection)

        } catch (InterruptedException e) {
            log.error "Interrupted: ${e.message}"

        } catch (Exception e) {
            log.error "DB action exception", e

        }
        return result
    }

    /** Create an empty Graph database, overriding default passwords */
    static def createGraphDatabase(String dbPath, String dbAdminPass = 'admin', String dbReaderPass = 'reader', String dbWriterPass = 'writer') {
        OrientGraphFactory factory = null
        OrientBaseGraph graph

        try {
            factory = createOrientFactory(dbPath)
            if (factory.exists()) {
                factory.drop()
            }

        } finally {
            factory?.close()
        }

        try {
            factory = createOrientFactory(dbPath, 'admin', 'admin')
            graph = factory.noTx

            doGraphCommands graph, """
                update OUser set password = '$dbAdminPass'  where name = 'admin'
                update OUser set password = '$dbReaderPass' where name = 'reader'
                update OUser set password = '$dbWriterPass' where name = 'writer'
            """

        } finally {
            graph?.shutdown()
            factory?.close()
        }
    }

    /** Create a name for a unique database (for testing) */
    static String uniqueDatabaseName() {
        return "test-${UUID.randomUUID()}"
    }

    static String uniqueMemoryDatabaseName() {
        return "memory:${uniqueDatabaseName()}"
    }

    /** Backup a database using the Document API */
    static def backupDatabase(ODatabaseDocumentTx db, File file) {
        try {
            def listener = new LoggingListener()
            def backup = new FileOutputStream(file)
            //TODO: FIX ME Commenting out until we find out backup over remote connection
            if (!db.storage.remote) {
                log.info "Backing up ${db.URL} to ${file.path}"
                db.backup(backup, null, null, listener, BACKUP_COMPRESSION_LEVEL, BACKUP_BUFFER_SIZE)
                log.info "Backup complete."
            }
            return true

        } catch (IOException e) {
            log.error "Unable to backup database: ${e}"
        }
        return false
    }

    /** Restore a database using the Document API */
    static def restoreDatabase(ODatabaseDocumentTx db, File file) {
        try {
            log.info "Restoring ${db.URL} from ${file.path}"
            def listener = new LoggingListener()
            def backup = new FileInputStream(file)
            db.restore(backup, null, null, listener)
            log.info "Restore complete."
            return true

        } catch (IOException e) {
            log.error "Unable to restore database: ${e}"
        }
        return false
    }

    static def removeDatabaseListeners(OrientBaseGraph graph) {
        removeDatabaseListeners(graph.rawGraph)
    }

    static def removeDatabaseListeners(ODatabaseDocumentTx database) {
        for (ODatabaseListener l : database.browseListeners()) {
            log.info "Unregister database listener: ${l}"
            database.unregisterListener(l)
        }
    }

    /** Remove all pertinent data from the graph */
    static def cleanGraphData(OrientBaseGraph graph) {
        assert graph != null
        log.debug "Cleaning existing graph data: ${graph}"
        def cmd = new OCommandSQL()
        try {
            graph.begin()
            if (graph.rawGraph.existsCluster('V')) {
                cmd.setText('DELETE VERTEX V')
                graph.command(cmd).execute()
            }
            if (graph.rawGraph.existsCluster('E')) {
                cmd.setText('DELETE EDGE E')
                graph.command(cmd).execute()
            }
            graph.commit()

        } catch (OException e) {
            log.error "Unable to clean graph data: ${e.message}"
        }
    }

    /** Attempt to commit a database transaction up to N times */
    static boolean commitWithRetries(ODatabaseDocumentTx db, int attempts, String message = null, Closure closure) {
        def result = false, retrying = true
        while (retrying && attempts > 0) {
            try {
                db.begin()
                result = closure.call()
                if (result) {
                    db.commit()
                } else {
                    db.rollback()
                }
                retrying = false

            } catch (OConcurrentModificationException e) {
                if (message) {
                    log.debug "Retrying: ${message}"
                }
                attempts -= 1
            }
        }
        return attempts > 0 ? result : false
    }

    /** Read SQL commands from a file and execute them for a database */
    static def doGraphCommands(OrientBaseGraph graph, File source) {
        return doDocumentCommands(graph.rawGraph, source.getText())
    }

    /** Read SQL commands from a string and execute them for a database */
    static def doGraphCommands(OrientBaseGraph graph, String lines) {
        return doDocumentCommands(graph.rawGraph, lines)
    }

    /** Read SQL commands from a file and execute them for a database */
    static def doDocumentCommands(ODatabaseDocumentTx db, File source) {
        return doDocumentCommands(db, source.getText())
    }

    /** Read SQL commands from a string and execute them for a database */
    static def doDocumentCommands(ODatabaseDocumentTx db, String lines) {
        String sqlcmd
        def errors = []
        def cmd = new OCommandSQL()

        // One line at a time ...
        for (String line : lines.split(System.lineSeparator())) {

            // One command at a time ...
            for (String sql : line.split(';')) {
                sqlcmd = sql.trim()
                if (sqlcmd == '' || sqlcmd.startsWith('--')) {
                    continue // skip blank lines or comments
                }

                try {
                    log.debug "Command: ${sqlcmd}"
                    cmd.setText(sqlcmd)
                    db.command(cmd).execute()

                } catch (OSchemaException e) {
                    def error = "SQL command (${sqlcmd}) failed with schema error: ${e.message}"
                    log.warn error
                    // errors << error

                } catch (OCommandSQLParsingException e) {
                    def error = "SQL command (${sqlcmd}) did not parse: ${e.message}"
                    log.error error
                    errors << error

                } catch (OCommandExecutorNotFoundException e) {
                    def error = "SQL command (${sqlcmd}) did not parse: ${e.message}"
                    log.error error
                    errors << error

                } catch (OCommandExecutionException e) {
                    def error = "Erroneous command: ${sqlcmd}: ${e.message}"
                    log.error error
                    errors << error
                }
            }
        }

        return errors
    }

    static int withDocumentQuery(ODatabaseDocumentTx db, String sql, Closure closure) {
        def listener = new AsyncQueryListener(sql.toUpperCase().startsWith('SELECT FROM'), closure)

        log.debug "Starting async query: ${sql}"
        db.query(new OSQLAsynchQuery<ODocument>(sql, -1, listener))

        return listener.count
    }

    static int withGraphQuery(OrientBaseGraph graph, String sql, Closure closure) {
        def listener = new AsyncQueryListener(sql.toUpperCase().startsWith('SELECT FROM'), closure) {
            @Override
            Object convert(Object record) {
                return graph.getElement(record)
            }
        }

        log.debug "Starting async query: ${sql}"
        graph.command(new OSQLAsynchQuery<OrientElement>(sql, -1, listener)).execute()

        return listener.count
    }

    static List<ODocument> doDocumentQuery(OrientBaseGraph graph, String sql) {
        return doDocumentQuery(graph.rawGraph, sql)
    }

    static List<ODocument> doDocumentQuery(ODatabaseDocumentTx db, String sql) {
        OSQLQuery<?> query = new OSQLSynchQuery<ODocument>(sql)
        query.limit = -1
        return db.command(query).execute()
    }

    static Object getResultField(List<ODocument> results, int row, String fieldname) {
        return results.get(row).field(fieldname)
    }

    static int countAttachedEdges(OrientVertex node) {
        int count = 0
        def record = node.record
        record.fieldNames().findAll { it.startsWith('in_') || it.startsWith('out_') }.each { String name ->
            ORidBag bag = (ORidBag) record.field(name)
            count += bag?.size()
        }
        return count
    }

    static Map<String, Integer> countEdgeTypes(OrientVertex node) {
        return countEdgeTypes(node.record)
    }

    static Map<String, Integer> countEdgeTypes(ODocument document) {
        def counts = [:]
        document.fieldNames().findAll { it.startsWith('in_') || it.startsWith('out_') }.each { String bagName ->
            def name = bagName.split('_')[1..-1].join('_')
            ORidBag bag = (ORidBag) document.field(bagName)
            def size = bag?.size()
            counts[name] = counts.containsKey(name) ? counts[name] + size : size
        }
        return counts
    }

    static Map<String, Integer> countConnectedNodeTypes(OrientVertex node) {
        return countConnectedNodeTypes(node.record)
    }

    static Map<String, Integer> countConnectedNodeTypes(ODocument document) {
        def db = document.database
        def counts = [:]
        document.fieldNames().findAll { it.startsWith('in_') }.each { String bagName ->
            ORidBag bag = (ORidBag) document.field(bagName)
            for (ORecord edge : bag) {
                ORID node = edge.field('out', ORID.class)
                def name = db.getClusterNameById(node.clusterId)
                counts[name] = counts.containsKey(name) ? counts[name] + 1 : 1
            }
        }
        document.fieldNames().findAll { it.startsWith('out_') }.each { String bagName ->
            ORidBag bag = (ORidBag) document.field(bagName)
            for (ORecord edge : bag) {
                ORID node = edge.field('in', ORID.class)
                def name = db.getClusterNameById(node.clusterId)
                counts[name] = counts.containsKey(name) ? counts[name] + 1 : 1
            }
        }
        return counts
    }
}

@Slf4j
class AsyncQueryListener implements OCommandResultListener {

    boolean identifiable
    Closure closure

    int minimumRows = 100
    int minimumTime = 10

    int count = 0
    int interim = 0

    long start, last, next

    AsyncQueryListener(boolean i, Closure c) {
        identifiable = i
        closure = c

        start = System.currentTimeMillis()
        last = start
        next = last + TimeUnit.SECONDS.toMillis(minimumTime)
    }

    Object convert(Object record) {
        return record
    }

    @Override
    boolean result(Object record) {
        closure.call(convert(record), count)
        count += 1

        interim += 1
        if (count % minimumRows == 0) {
            if (log.debugEnabled) {
                def now = System.currentTimeMillis()
                if (now >= next) {
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(now - last)
                    int rate = seconds ? interim / seconds : interim

                    def identity = ''
                    if (identifiable && record instanceof OIdentifiable) {
                        identity = ((OIdentifiable) record).identity
                    }

                    log.debug String.format('Processed row %,d ... (%,d rows/sec) %s', count, rate, identity)
                    last = now
                    next = next + TimeUnit.SECONDS.toMillis(minimumTime)
                    interim = 0
                }
            }
        }
        return true
    }

    @Override
    void end() {
        def now = System.currentTimeMillis()
        log.debug String.format('Processed %,d rows in %,d seconds', count, TimeUnit.MILLISECONDS.toSeconds(now - start))
    }

    Object getResult() {
        return null
    }
}

@Slf4j
class LoggingListener implements OCommandOutputListener {
    def buffer = new StringBuilder()

    @Override
    void onMessage(String message) {
        if (message.startsWith('\n- ')) {
            buffer.append(message.substring(3))
        } else {
            buffer.append(message)
            log.debug buffer.toString()
            buffer.setLength(0)
        }
    }
}
