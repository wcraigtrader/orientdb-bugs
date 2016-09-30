package com.akonizo.orientdb.tools

import java.text.ParseException
import java.text.SimpleDateFormat

class DateTools {

    final static String TIME_ZONE = "UTC"
    final static String TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ssXXX"

    /**
     * Format a date in ISO format
     *
     * @param date
     * @return date in ISO format
     */
    static String dateToISO(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT, Locale.US)
        sdf.setTimeZone(TimeZone.getTimeZone(TIME_ZONE))
        return sdf.format(date)
    }

    /**
     * Create a date from an ISO format timestamp
     *
     * @param timestamp in "yyyy-MM-dd'T'HH:mm:ssXXX" format
     * @return date
     * @throws java.text.ParseException
     */
    static Date isoToDate(String timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT)
        try {
            Date date = sdf.parse(timestamp)
            return date
        } catch (ParseException e) {
            throw new RuntimeException(e)
        }
    }

    /**
     * Create a date from scratch using the Gregorian calendar
     *
     * @param year 4-digit year
     * @param month 0-11
     * @param day 1-31
     * @param hour 0-23
     * @param minute 0-60
     * @param second 0-61 (leap seconds)
     * @return date
     */
    static Date utcToDate(int year, int month, int day, int hour, int minute, int second) {
        Calendar cal = new GregorianCalendar(TimeZone.getTimeZone(TIME_FORMAT))
        cal.set(year, month, day, hour, minute, second)
        cal.set(Calendar.MILLISECOND, 0)
        // *grumble* GregorianCalendar provides 507 extra milliseconds compared to SimpleDateFormat.parse()
        return cal.getTime()
    }
}
