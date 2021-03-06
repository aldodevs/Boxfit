package com.manuege.boxfit.transformers;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;

/**
 * A transformer to convert a Date to / from a String.
 * Subclasses will need to provide a `DateFormat`
 */
public abstract class StringToDateTransformer implements Transformer<Date, String> {

    @Override
    public Date transform(String object) {
        try {
            return getFormat().parse(object);
        } catch (ParseException e) {
            return null;
        }
    }

    @Override
    public String inverseTransform(Date object) {
        if (object == null) {
            return null;
        }
        return getFormat().format(object);
    }

    protected abstract DateFormat getFormat();

    protected Locale getLocale() {
        return Locale.getDefault();
    }

}
