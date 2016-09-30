package com.akonizo.orientdb.tools

import org.slf4j.Logger

import static java.util.concurrent.TimeUnit.*

class QueryRecordCounter {

    Logger logger

    long maxRows = 0
    int minimumRows = 100
    int minimumTime = SECONDS.toMillis(10)

    long count = 0
    int interim = 0
    long start, last, next

    QueryRecordCounter(Logger parentLogger, long max = 0, int minRows = 100, int minTime = 10) {
        logger = parentLogger

        maxRows = max
        minimumRows = minRows
        minimumTime = SECONDS.toMillis(minTime)

        start = System.currentTimeMillis()
        last = start
        next = last + minimumTime
    }

    long getDuration() {
        return System.currentTimeMillis() - start
    }

    void bump(Object identity = '') {
        count += 1
        interim += 1

        if (!logger.infoEnabled || count % minimumRows != 0) {
            return
        }

        def now = System.currentTimeMillis()

        if (now < next) {
            return
        }

        long lapTime = MILLISECONDS.toSeconds(now - last)
        int rate = lapTime ? interim / lapTime : interim

        if (maxRows) {
            long projected = ((now - start) / count) * (maxRows - count)
            def remaining = prettyTime(projected)
            logger.info String.format('Processed row %,d ... (%,d rows/sec, %s left) %s', count, rate, remaining, identity)
        } else {
            logger.info String.format('Processed row %,d ... (%,d rows/sec) %s', count, rate, identity)
        }

        last = now
        next = next + minimumTime
        interim = 0
    }

    void done() {
        logger.info String.format('Processed %,d rows in %s', count, prettyTime(duration) )
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
