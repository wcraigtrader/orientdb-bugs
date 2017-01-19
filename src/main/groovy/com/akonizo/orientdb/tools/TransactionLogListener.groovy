package com.akonizo.orientdb.tools

import com.orientechnologies.orient.core.command.OCommandExecutor
import com.orientechnologies.orient.core.command.OCommandRequestText
import com.orientechnologies.orient.core.db.ODatabase
import com.orientechnologies.orient.core.db.ODatabaseDocumentInternal
import com.orientechnologies.orient.core.db.ODatabaseListener
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import com.orientechnologies.orient.core.db.document.ODatabaseDocument
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.record.ORecordOperation
import com.orientechnologies.orient.core.id.ORecordId
import com.orientechnologies.orient.core.record.impl.ODocument
import groovy.util.logging.Slf4j
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap

@Slf4j
class TransactionLogListener implements ODatabaseListener {

    private static final Logger TRANSACTION = LoggerFactory.getLogger('TRANSACTION')

    protected static final def pendingOps = new ConcurrentHashMap<ODatabaseDocument, List<ORecordOperation>>()

    // ----- Transaction Logging ----------------------------------------------

    static void addOperation(ORecordOperation op) {
        log.trace "addOperation( ${op.type} : ${op?.record?.identity} )"

        ODatabaseDocumentInternal db = ODatabaseRecordThreadLocal.INSTANCE.get()
        if (db.getTransaction() == null || !db.getTransaction().isActive()) {
            logOperation(op)
        } else {
            synchronized (pendingOps) {
                def list = pendingOps[db]
                if (list == null) {
                    list = new ArrayList<ORecordOperation>()
                    pendingOps[db] = list
                }
                list << op
            }
        }
    }

    static void logPendingOperations(ODatabase db) {
        log.trace "logPendingOperations()"

        List<ORecordOperation> list
        synchronized (pendingOps) {
            list = pendingOps.remove(db)
        }

        if (list != null) {
            for (ORecordOperation item : list) {
                logOperation(item)
            }
        }
    }

    static void clearPendingOperations(ODatabase db) {
        log.trace "clearPendingOperations()"

        synchronized (pendingOps) {
            pendingOps.remove(db)
        }
    }

    static void logOperation(ORecordOperation op) {
        log.trace "logOperation( ${op.type} : ${op?.record?.identity} )"

        String key
        def rid = op.record.identity

        def classname = op.record.field('@class', String.class)
        key = op.record.field('key', String.class)
        if (key == null) {
            def oRID = op.record.field('in', ORecordId.class)
            def iRID = op.record.field('out', ORecordId.class)
            if (oRID != null && iRID != null) {
                key = "${oRID}-(${classname})->${iRID}"
            }
        }

        switch (op.type) {
            case ORecordOperation.CREATED:
                TRANSACTION.warn "CREATED|${classname}|${rid}|${key}|${op.record.toJSON()}"
                break
            case ORecordOperation.UPDATED:
                TRANSACTION.warn "UPDATED|${classname}|${rid}|${key}|${op.record.toJSON()}"
                break
            case ORecordOperation.DELETED:
                TRANSACTION.warn "DELETED|${classname}|${rid}|${key}|${op.record.toJSON()}"
                break
            default:
                log.warn "Unrecognized Op: ${op}"
        }

    }

    static void logCommand(String command) {
        TRANSACTION.warn "COMMAND|${command}"
    }

    // ----- Log Listener -----------------------------------------------------

    @Override
    void onCreate(ODatabase db) {
        log.trace "onCreate()"
    }

    @Override
    void onDelete(ODatabase db) {
        log.trace "onDelete()"
        clearPendingOperations(db)
    }

    @Override
    void onOpen(ODatabase db) {
        log.trace "onOpen()"
    }

    @Override
    void onBeforeTxBegin(ODatabase db) {
        log.trace "onBeforeTxBegin()"
    }

    @Override
    void onBeforeTxCommit(ODatabase db) {
        log.trace "onBeforeTxCommit()"

        if (db instanceof ODatabaseDocumentTx) {
            final def dbtx = db as ODatabaseDocumentTx
            final def transaction = dbtx.getTransaction()
            final Iterable<? extends ORecordOperation> entries = transaction.currentRecordEntries
            for (ORecordOperation op : entries) {
                if (op.record instanceof ODocument) {
                    addOperation(op)
                }
            }
        }
    }

    @Override
    void onAfterTxCommit(ODatabase db) {
        log.trace "onAfterTxCommit()"
        logPendingOperations(db)
    }

    @Override
    void onBeforeTxRollback(ODatabase db) {
        log.trace "onBeforeTxRollback()"
    }

    @Override
    void onAfterTxRollback(ODatabase db) {
        log.trace "onAfterTxRollback()"
        clearPendingOperations(db)
    }

    @Override
    void onClose(ODatabase db) {
        log.trace "onClose()"
        clearPendingOperations(db)
    }

    // @Override
    void onBeforeCommand(OCommandRequestText iCommand, OCommandExecutor executor) {
        log.trace "onBeforeCommand()"
    }

    // @Override
    void onAfterCommand(OCommandRequestText iCommand, OCommandExecutor executor, Object result) {
        log.trace "onAfterCommand()"
        logCommand(iCommand.text)
    }

    @Override
    boolean onCorruptionRepairDatabase(ODatabase db, String iReason, String iWhatWillbeFixed) {
        log.trace "onCorruptionRepairDatabase()"
        return false
    }
}
