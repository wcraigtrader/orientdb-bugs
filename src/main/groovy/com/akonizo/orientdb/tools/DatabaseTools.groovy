package com.akonizo.orientdb.tools

import com.orientechnologies.orient.core.command.OCommandExecutorNotFoundException
import com.orientechnologies.orient.core.command.OCommandOutputListener
import com.orientechnologies.orient.core.command.OCommandResultListener
import com.orientechnologies.orient.core.db.ODatabaseListener
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.OIdentifiable
import com.orientechnologies.orient.core.db.record.ridbag.ORidBag
import com.orientechnologies.orient.core.exception.OCommandExecutionException
import com.orientechnologies.orient.core.exception.OSchemaException
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

    /** Open a database using the Document API */
    static def withDocumentDatabase(String dbPath, String dbUser, String dbPass, Closure<?> closure) {
        ODatabaseDocumentTx db = null

        assert dbPath
        assert dbUser
        assert dbPass

        try {
            DatabaseTools.log.debug "Connecting to ${dbUser}@${dbPath} ..."
            db = new ODatabaseDocumentTx(dbPath).open(dbUser, dbPass)
            return withDatabaseConnection(db, closure)

        } finally {
            try {
                db?.close()
            } catch (Exception e) {
                DatabaseTools.log.error "DB close exception", e
            }
        }

    }

    /** Open a database using the Graph API */
    static def withGraphDatabase(String dbPath, String dbUser, String dbPass, boolean transactional = true, Closure<?> closure) {
        OrientGraphFactory factory = null
        OrientBaseGraph graph = null

        try {
            DatabaseTools.log.debug "Connecting to ${dbUser}@${dbPath} ..."
            factory = new OrientGraphFactory(dbPath, dbUser, dbPass).setupPool(1, 1)
            graph = transactional ? factory.tx : factory.noTx
            return withDatabaseConnection(graph, closure)

        } finally {
            try {
                graph?.shutdown(true, transactional)
                factory?.close()
            } catch (Exception e) {
                DatabaseTools.log.error "DB close exception", e
            }
        }
    }

    /** Common error handling for database connections */
    static def withDatabaseConnection(Object connection, Closure closure) {
        def result = null
        try {
            result = closure.call(connection)

        } catch (InterruptedException e) {
            DatabaseTools.log.error "Interrupted: ${e.message}"

        } catch (Exception e) {
            DatabaseTools.log.error "DB action exception", e

        }
        return result
    }

    static def createGraphDatabase(String dbPath, String dbAdminPass = 'admin', String dbReaderPass = 'reader', String dbWriterPass = 'writer') {
        OrientGraphFactory factory = null
        OrientBaseGraph graph

        try {
            factory = new OrientGraphFactory(dbPath)
            if (factory.exists()) {
                factory.drop()
            }

        } finally {
            factory?.close()
        }

        try {
            factory = new OrientGraphFactory(dbPath, 'admin', 'admin')
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

    /** Backup a database using the Document API */
    static def backupDatabase(ODatabaseDocumentTx db, File file) {
        try {
            DatabaseTools.log.info "Backing up ${db.URL} to ${file.path}"
            def listener = new LoggingListener()
            def backup = new FileOutputStream(file)
            db.backup(backup, null, null, listener, BACKUP_COMPRESSION_LEVEL, BACKUP_BUFFER_SIZE)
            DatabaseTools.log.info "Backup complete."
            return true

        } catch (IOException e) {
            DatabaseTools.log.error "Unable to backup database: ${e}"
        }
        return false
    }

    /** Restore a database using the Document API */
    static def restoreDatabase(ODatabaseDocumentTx db, File file) {
        try {
            DatabaseTools.log.info "Restoring ${db.URL} from ${file.path}"
            def listener = new LoggingListener()
            def backup = new FileInputStream(file)
            db.restore(backup, null, null, listener)
            DatabaseTools.log.info "Restore complete."
            return true

        } catch (IOException e) {
            DatabaseTools.log.error "Unable to restore database: ${e}"
        }
        return false
    }

    static def removeDatabaseListeners(OrientBaseGraph graph) {
        removeDatabaseListeners(graph.rawGraph)
    }

    static def removeDatabaseListeners(ODatabaseDocumentTx database) {
        for (ODatabaseListener l : database.browseListeners()) {
            DatabaseTools.log.info "Unregister database listener: ${l}"
            database.unregisterListener(l)
        }
    }

    /** Remove all pertinent data from the graph */
    static def cleanGraphData(OrientBaseGraph graph) {
        doGraphCommands graph, """
            DELETE VERTEX V
            DELETE EDGE E
        """
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
                    DatabaseTools.log.debug "Command: ${sqlcmd}"
                    cmd.setText(sqlcmd)
                    db.command(cmd).execute()

                } catch (OSchemaException e) {
                    def error = "SQL command (${sqlcmd}) failed with schema error: ${e.message}"
                    DatabaseTools.log.warn error
                    // errors << error

                } catch (OCommandSQLParsingException e) {
                    def error = "SQL command (${sqlcmd}) did not parse: ${e.message}"
                    DatabaseTools.log.error error
                    errors << error

                } catch (OCommandExecutorNotFoundException e) {
                    def error = "SQL command (${sqlcmd}) did not parse: ${e.message}"
                    DatabaseTools.log.error error
                    errors << error

                } catch (OCommandExecutionException e) {
                    def error = "Erroneous command: ${sqlcmd}: ${e.message}"
                    DatabaseTools.log.error error
                    errors << error
                }
            }
        }

        return errors
    }

    static int withDocumentQuery(ODatabaseDocumentTx db, String sql, Closure closure) {
        def listener = new AsyncQueryListener(sql.toUpperCase().startsWith('SELECT FROM'), closure)

        DatabaseTools.log.debug "Starting async query: ${sql}"
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

        DatabaseTools.log.debug "Starting async query: ${sql}"
        graph.command(new OSQLAsynchQuery<OrientElement>(sql, -1, listener)).execute()

        return listener.count
    }

    static List<ODocument> doGraphQuery(OrientBaseGraph graph, String sql) {
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
