package com.akonizo.orientdb.tools

import groovy.transform.Canonical
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static java.util.concurrent.TimeUnit.*

@Canonical(includes = ['logger', 'maxRows', 'minRows', 'minTime'])
class QueryRecordCounter {

    Logger logger = LoggerFactory.getLogger(QueryRecordCounter)

    int maxRows = 0
    int minRows = 100
    int minTime = SECONDS.toMillis(10)

    long startTime
    int count = 0

    int lastCount = 0
    long lastTime, nextTime

    long getDuration(Date now = null) {
        def currentTime = now?.time ?: System.currentTimeMillis()
        return currentTime - startTime
    }

    void init(long initTime = 0) {
        startTime = initTime ?: System.currentTimeMillis()
        lastTime = startTime
        nextTime = lastTime + minTime
    }

    void bump(Object identity = '', long ts = 0L) {
        count += 1
        if (count % minRows != 0) {
            return
        }

        long now = ts ?: System.currentTimeMillis()
        if (now >= nextTime) {
            if (logger.infoEnabled) {
                logger.info processingMessage(now, identity.toString())
        }

            lastCount = count
            lastTime = now
            nextTime += minTime
        }
    }

    void done() {
        logger.info String.format('Processed %,d rows in %s', count, prettyTime(duration) )
    }

    String processingMessage(long now, String identity = '') {
        // assert now >= lastTime

        long lapTime = MILLISECONDS.toSeconds(now - lastTime)
        int lapCount = count - lastCount
        int rate = lapTime ? lapCount / lapTime : lapCount

        if (maxRows) {
            long projected = ((now - startTime) / count) * (maxRows - count)
            def remaining = prettyTime(projected)
            return String.format('Processed row %,d ... (%,d rows/sec, %s left) %s', count, rate, remaining, identity)
        }

        return String.format('Processed row %,d ... (%,d rows/sec) %s', count, rate, identity)
    }

    static String prettyTime(long interval) {
        long hours = MILLISECONDS.toHours(interval)
        interval -= HOURS.toMillis(hours)
        long minutes = MILLISECONDS.toMinutes(interval)
        interval -= MINUTES.toMillis(minutes)
        long seconds = MILLISECONDS.toSeconds(interval)

        if (hours > 0) {
            return String.format('%d:%02dm', hours, minutes)
        }
        if (minutes > 0) {
            return String.format('%d:%02ds', minutes, seconds)
        }
        return String.format('%ds', seconds)
    }
}
