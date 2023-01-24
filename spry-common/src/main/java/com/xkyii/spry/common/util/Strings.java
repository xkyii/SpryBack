package com.xkyii.spry.common.util;


import java.util.HashMap;
import java.util.Map;

/**
 * from: https://github.com/qos-ch/slf4j/blob/bae56f544b0c30cedb265729f3c6cce72fa79f10/slf4j-api/src/main/java/org/slf4j/helpers/MessageFormatter.java
 *
 * TODO: 移入core
 */
public class Strings {

    static final char DELIM_START = '{';
    static final char DELIM_STOP = '}';
    static final String DELIM_STR = "{}";
    private static final char ESCAPE_CHAR = '\\';

    /**
     * Performs single argument substitution for the 'messagePattern' passed as
     * parameter.
     * <p>
     * For example,
     *
     * <pre>
     * MessageFormatter.format(&quot;Hi {}.&quot;, &quot;there&quot;);
     * </pre>
     *
     * will return the string "Hi there.".
     * <p>
     *
     * @param messagePattern
     *          The message pattern which will be parsed and formatted
     * @param arg
     *          The argument to be substituted in place of the formatting anchor
     * @return The formatted message
     */
    final public static String format(String messagePattern, Object arg) {
        return arrayFormat(messagePattern, new Object[] { arg });
    }

    /**
     *
     * Performs a two argument substitution for the 'messagePattern' passed as
     * parameter.
     * <p>
     * For example,
     *
     * <pre>
     * MessageFormatter.format(&quot;Hi {}. My name is {}.&quot;, &quot;Alice&quot;, &quot;Bob&quot;);
     * </pre>
     *
     * will return the string "Hi Alice. My name is Bob.".
     *
     * @param messagePattern
     *          The message pattern which will be parsed and formatted
     * @param arg1
     *          The argument to be substituted in place of the first formatting
     *          anchor
     * @param arg2
     *          The argument to be substituted in place of the second formatting
     *          anchor
     * @return The formatted message
     */
    final public static String format(final String messagePattern, Object arg1, Object arg2) {
        return arrayFormat(messagePattern, new Object[] { arg1, arg2 });
    }

    final public static String arrayFormat(final String messagePattern, final Object[] argArray) {
        Throwable throwableCandidate = getThrowableCandidate(argArray);
        Object[] args = argArray;
        if (throwableCandidate != null) {
            args = trimmedCopy(argArray);
        }
        return arrayFormat(messagePattern, args, throwableCandidate).getMessage();
    }

    /**
     * Assumes that argArray only contains arguments with no throwable as last element.
     *
     * @param messagePattern
     * @param argArray
     */
    final public static String basicArrayFormat(final String messagePattern, final Object[] argArray) {
        FormattingTuple ft = arrayFormat(messagePattern, argArray, null);
        return ft.getMessage();
    }

    public static String basicArrayFormat(NormalizedParameters np) {
        return basicArrayFormat(np.getMessage(), np.getArguments());
    }

    final public static FormattingTuple arrayFormat(final String messagePattern, final Object[] argArray, Throwable throwable) {

        if (messagePattern == null) {
            return new FormattingTuple(null, argArray, throwable);
        }

        if (argArray == null) {
            return new FormattingTuple(messagePattern);
        }

        int i = 0;
        int j;
        // use string builder for better multicore performance
        StringBuilder sbuf = new StringBuilder(messagePattern.length() + 50);

        int L;
        for (L = 0; L < argArray.length; L++) {

            j = messagePattern.indexOf(DELIM_STR, i);

            if (j == -1) {
                // no more variables
                if (i == 0) { // this is a simple string
                    return new FormattingTuple(messagePattern, argArray, throwable);
                } else { // add the tail string which contains no variables and return
                    // the result.
                    sbuf.append(messagePattern, i, messagePattern.length());
                    return new FormattingTuple(sbuf.toString(), argArray, throwable);
                }
            } else {
                if (isEscapedDelimeter(messagePattern, j)) {
                    if (!isDoubleEscaped(messagePattern, j)) {
                        L--; // DELIM_START was escaped, thus should not be incremented
                        sbuf.append(messagePattern, i, j - 1);
                        sbuf.append(DELIM_START);
                        i = j + 1;
                    } else {
                        // The escape character preceding the delimiter start is
                        // itself escaped: "abc x:\\{}"
                        // we have to consume one backward slash
                        sbuf.append(messagePattern, i, j - 1);
                        deeplyAppendParameter(sbuf, argArray[L], new HashMap<>());
                        i = j + 2;
                    }
                } else {
                    // normal case
                    sbuf.append(messagePattern, i, j);
                    deeplyAppendParameter(sbuf, argArray[L], new HashMap<>());
                    i = j + 2;
                }
            }
        }
        // append the characters following the last {} pair.
        sbuf.append(messagePattern, i, messagePattern.length());
        return new FormattingTuple(sbuf.toString(), argArray, throwable);
    }

    final static boolean isEscapedDelimeter(String messagePattern, int delimeterStartIndex) {

        if (delimeterStartIndex == 0) {
            return false;
        }
        char potentialEscape = messagePattern.charAt(delimeterStartIndex - 1);
        if (potentialEscape == ESCAPE_CHAR) {
            return true;
        } else {
            return false;
        }
    }

    final static boolean isDoubleEscaped(String messagePattern, int delimeterStartIndex) {
        if (delimeterStartIndex >= 2 && messagePattern.charAt(delimeterStartIndex - 2) == ESCAPE_CHAR) {
            return true;
        } else {
            return false;
        }
    }

    // special treatment of array values was suggested by 'lizongbo'
    private static void deeplyAppendParameter(StringBuilder sbuf, Object o, Map<Object[], Object> seenMap) {
        if (o == null) {
            sbuf.append("null");
            return;
        }
        if (!o.getClass().isArray()) {
            safeObjectAppend(sbuf, o);
        } else {
            // check for primitive array types because they
            // unfortunately cannot be cast to Object[]
            if (o instanceof boolean[]) {
                booleanArrayAppend(sbuf, (boolean[]) o);
            } else if (o instanceof byte[]) {
                byteArrayAppend(sbuf, (byte[]) o);
            } else if (o instanceof char[]) {
                charArrayAppend(sbuf, (char[]) o);
            } else if (o instanceof short[]) {
                shortArrayAppend(sbuf, (short[]) o);
            } else if (o instanceof int[]) {
                intArrayAppend(sbuf, (int[]) o);
            } else if (o instanceof long[]) {
                longArrayAppend(sbuf, (long[]) o);
            } else if (o instanceof float[]) {
                floatArrayAppend(sbuf, (float[]) o);
            } else if (o instanceof double[]) {
                doubleArrayAppend(sbuf, (double[]) o);
            } else {
                objectArrayAppend(sbuf, (Object[]) o, seenMap);
            }
        }
    }

    private static void safeObjectAppend(StringBuilder sbuf, Object o) {
        try {
            String oAsString = o.toString();
            sbuf.append(oAsString);
        } catch (Throwable t) {
//            Util.report("SLF4J: Failed toString() invocation on an object of type [" + o.getClass().getName() + "]", t);
            sbuf.append("[FAILED toString()]");
        }

    }

    private static void objectArrayAppend(StringBuilder sbuf, Object[] a, Map<Object[], Object> seenMap) {
        sbuf.append('[');
        if (!seenMap.containsKey(a)) {
            seenMap.put(a, null);
            final int len = a.length;
            for (int i = 0; i < len; i++) {
                deeplyAppendParameter(sbuf, a[i], seenMap);
                if (i != len - 1)
                    sbuf.append(", ");
            }
            // allow repeats in siblings
            seenMap.remove(a);
        } else {
            sbuf.append("...");
        }
        sbuf.append(']');
    }

    private static void booleanArrayAppend(StringBuilder sbuf, boolean[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    private static void byteArrayAppend(StringBuilder sbuf, byte[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    private static void charArrayAppend(StringBuilder sbuf, char[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    private static void shortArrayAppend(StringBuilder sbuf, short[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    private static void intArrayAppend(StringBuilder sbuf, int[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    private static void longArrayAppend(StringBuilder sbuf, long[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    private static void floatArrayAppend(StringBuilder sbuf, float[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    private static void doubleArrayAppend(StringBuilder sbuf, double[] a) {
        sbuf.append('[');
        final int len = a.length;
        for (int i = 0; i < len; i++) {
            sbuf.append(a[i]);
            if (i != len - 1)
                sbuf.append(", ");
        }
        sbuf.append(']');
    }

    /**
     * Helper method to determine if an {@link Object} array contains a {@link Throwable} as last element
     *
     * @param argArray
     *          The arguments off which we want to know if it contains a {@link Throwable} as last element
     * @return if the last {@link Object} in argArray is a {@link Throwable} this method will return it,
     *          otherwise it returns null
     */
    public static Throwable getThrowableCandidate(final Object[] argArray) {
        return NormalizedParameters.getThrowableCandidate(argArray);
    }

    /**
     * Helper method to get all but the last element of an array
     *
     * @param argArray
     *          The arguments from which we want to remove the last element
     *
     * @return a copy of the array without the last element
     */
    public static Object[] trimmedCopy(final Object[] argArray) {
        return NormalizedParameters.trimmedCopy(argArray);
    }

    private static class FormattingTuple {

        static public FormattingTuple NULL = new FormattingTuple(null);

        private final String message;
        private final Throwable throwable;
        private final Object[] argArray;

        public FormattingTuple(String message) {
            this(message, null, null);
        }

        public FormattingTuple(String message, Object[] argArray, Throwable throwable) {
            this.message = message;
            this.throwable = throwable;
            this.argArray = argArray;
        }

        public String getMessage() {
            return message;
        }

        public Object[] getArgArray() {
            return argArray;
        }

        public Throwable getThrowable() {
            return throwable;
        }

    }

    private static class NormalizedParameters {

        final String message;
        final Object[] arguments;
        final Throwable throwable;

        public NormalizedParameters(String message, Object[] arguments, Throwable throwable) {
            this.message = message;
            this.arguments = arguments;
            this.throwable = throwable;
        }

        public NormalizedParameters(String message, Object[] arguments) {
            this(message, arguments, null);
        }

        public String getMessage() {
            return message;
        }

        public Object[] getArguments() {
            return arguments;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        /**
         * Helper method to determine if an {@link Object} array contains a
         * {@link Throwable} as last element
         *
         * @param argArray The arguments off which we want to know if it contains a
         *                 {@link Throwable} as last element
         * @return if the last {@link Object} in argArray is a {@link Throwable} this
         *         method will return it, otherwise it returns null
         */
        public static Throwable getThrowableCandidate(final Object[] argArray) {
            if (argArray == null || argArray.length == 0) {
                return null;
            }

            final Object lastEntry = argArray[argArray.length - 1];
            if (lastEntry instanceof Throwable) {
                return (Throwable) lastEntry;
            }

            return null;
        }

        /**
         * Helper method to get all but the last element of an array
         *
         * @param argArray The arguments from which we want to remove the last element
         *
         * @return a copy of the array without the last element
         */
        public static Object[] trimmedCopy(final Object[] argArray) {
            if (argArray == null || argArray.length == 0) {
                throw new IllegalStateException("non-sensical empty or null argument array");
            }

            final int trimmedLen = argArray.length - 1;

            Object[] trimmed = new Object[trimmedLen];

            if (trimmedLen > 0) {
                System.arraycopy(argArray, 0, trimmed, 0, trimmedLen);
            }

            return trimmed;
        }

        /**
         * This method serves to normalize logging call invocation parameters.
         *
         * More specifically, if a throwable argument is not supplied directly, it
         * attempts to extract it from the argument array.
         */
        public static NormalizedParameters normalize(String msg, Object[] arguments, Throwable t) {

            if (t != null) {
                return new NormalizedParameters(msg, arguments, t);
            }

            if (arguments == null || arguments.length == 0) {
                return new NormalizedParameters(msg, arguments, t);
            }

            Throwable throwableCandidate = NormalizedParameters.getThrowableCandidate(arguments);
            if (throwableCandidate != null) {
                Object[] trimmedArguments = Strings.trimmedCopy(arguments);
                return new NormalizedParameters(msg, trimmedArguments, throwableCandidate);
            } else {
                return new NormalizedParameters(msg, arguments);
            }

        }
//        public static NormalizedParameters normalize(LoggingEvent event) {
//            return normalize(event.getMessage(), event.getArgumentArray(), event.getThrowable());
//        }
    }
}
