package com.akonizo.orientdb.tools

import com.orientechnologies.orient.core.command.OCommandResultListener
import com.orientechnologies.orient.core.db.record.OIdentifiable
import groovy.util.logging.Slf4j

@Slf4j
class AsyncQueryListener implements OCommandResultListener {

    boolean identifiable
    Closure closure

    QueryRecordCounter counter

    AsyncQueryListener(boolean i, Closure c) {
        identifiable = i
        closure = c

        counter = new QueryRecordCounter( log )
        counter.init()
    }

    long getCount() {
        return counter.count
    }

    Object convert(Object record) {
        return record
    }

    // ----- OCommandResultListener interface ---------------------------------

    @Override
    boolean result(Object record) {
        closure.call(convert(record), counter.count)

        def identity = ''
        if (identifiable && record instanceof OIdentifiable) {
            identity = ((OIdentifiable) record).identity
        }
        counter.bump( identity )
        return true
    }

    @Override
    void end() {
        counter.done()
    }

    // This is for Orient 2.2.X only, no idea what it's supposed to do.
    Object getResult() {
        return null
    }
}
