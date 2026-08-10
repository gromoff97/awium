package io.github.gromoff97.awium;

import java.util.Arrays;

final class ValueRenderer {

    private ValueRenderer() {
    }

    @SuppressWarnings("removal")
    static String render(Object value) {
        try {
            if (value instanceof boolean[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof byte[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof short[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof int[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof long[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof char[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof float[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof double[] array) {
                return Arrays.toString(array);
            }
            if (value instanceof Object[] array) {
                return Arrays.deepToString(array);
            }
            return String.valueOf(value);
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            return "<value unavailable: toString() threw "
                    + typeName(failure) + ">";
        }
    }

    static String typeName(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? failure.getClass().getName() : simpleName;
    }
}
