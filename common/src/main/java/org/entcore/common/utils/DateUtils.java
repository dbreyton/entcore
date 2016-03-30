package org.entcore.common.utils;

import fr.wseduc.mongodb.MongoDb;
import org.vertx.java.core.json.JsonObject;

import javax.xml.bind.DatatypeConverter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

/**
 * Created by dbreyton on 30/03/2016.
 */
public final class DateUtils {
    /** The Constant DEFAULT_DATE_PATTERN. */
    private static final String DEFAULT_DATE_PATTERN = "dd/MM/yyyy";

    /** The Constant DEFAULT_DATE_LOCAL. */
    private static final Locale DEFAULT_DATE_LOCAL = Locale.FRENCH;

    private DateUtils()  {}

    public static Date create(int year, int month, int day, int hours, int minutes,
                             int seconds) {
        return new GregorianCalendar(year, month, day, hours, minutes, seconds).getTime();
    }

    public static Date create(int year, int month, int day) {
        return create(year, month, day, 0, 0, 0);
    }

    public static int getField(Date date, int field) {
        final Calendar cal = new GregorianCalendar();
        cal.setTime(date);
        return cal.get(field);
    }

    public static Date add(Date date, int field, int value) {
        if (date != null) {
            final Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            cal.add(field, value);
            return cal.getTime();
        }

        return null;
    }

    public static int compare(Date date1, Date date2) {
        return compare((date1 != null) ? date1.getTime() : null,
                (date2 != null) ? date2.getTime() : null);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable> int compare(T valeur1, T valeur2) {
        if (valeur1 == null) {
            if (valeur2 == null) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (valeur2 == null) {
               return -1;
            } else {
                return valeur1.compareTo(valeur2);
            }
        }
    }

    /**
     * Returns True if "Date Between" is between "Min Date" and "Max Date".
     */
    public static Boolean isBetween(Date dateBetween, Date dateMin, Date dateMax) {
        return ((compare(dateBetween, dateMin) >= 0) &&
                (compare(dateMax, dateBetween) >= 0));
    }

    public static Boolean lessOrEqualsWithoutTime(Date d1, Date d2) {
        if ((d1 == null) || (d2 == null)) {
            return false;
        }
        final Date d1Arr = untimed(d1);
        final Date d2Arr = untimed(d2);
        return lessOrEquals(d1Arr, d2Arr);
    }

    public static Boolean lessOrEquals(Date d1, Date d2) {
        if ((d1 == null) || (d2 == null)) {
            return false;
        }
        return (d1.before(d2) || d1.equals(d2));
    }

    private static Date untimed(Date date) {
        if (date != null) {
            final Calendar cal = new GregorianCalendar();
            cal.setTime(date);
            cal.set(Calendar.HOUR_OF_DAY, 0);

            return cal.getTime();
        }
        return null;
    }

    public static Date parseIsoDate(JsonObject date) {
        return MongoDb.parseIsoDate(date);
    }

    public static Date parseLongDate(String longStr) {
        return new Date(Long.parseLong(longStr));
    }

    public static Long parseLongDateToLong(String longStr) {
        return new Date(Long.parseLong(longStr)).getTime();
    }


    public static Date parseIsoDate(String date) throws ParseException {
        return MongoDb.parseDate(date);
    }

    public static Date parseDateTime(String date) {
        if (!StringUtils.isEmpty(date)) {
            return DatatypeConverter.parseDateTime(date).getTime();
        }
        return null;
    }

    public static Date parseTimestampWithoutTimezone(String date) throws ParseException {
        final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        return dateFormat.parse(date);
    }

    public static Long parseDateTimeToLong(String date) {
        return parseDateTime(date).getTime();
    }

    public static String format(Date d, String pattern) {
        if (d == null) {
            return null;
        }
        final String myPattern = StringUtils.isEmpty(pattern) ? DEFAULT_DATE_PATTERN : pattern;
        return new SimpleDateFormat(myPattern).format(d);
    }

    public static String format(Date d, Locale local, String pattern) {
        if (d == null) {
            return null;
        }
        final String myPattern = StringUtils.isEmpty(pattern) ? DEFAULT_DATE_PATTERN : pattern;
        final Locale myLocal = (local != null) ? local : DEFAULT_DATE_LOCAL;
        return new SimpleDateFormat(myPattern, myLocal).format(d);
    }

    public static String format(Date d) {
        return format(d, DEFAULT_DATE_PATTERN);
    }

    public static String formatIsoDate(Date date) {
        return MongoDb.formatDate(date);
    }

    public static JsonObject getDateJsonObject(final Date date) {
        if (date != null) {
            return getDateJsonObject(date.getTime());
        }
        final Long l = null;
        return getDateJsonObject(l);
    }

    public static JsonObject getDateJsonObject(final Long date) {
        return new JsonObject().putValue("$date", date);
    }
}
