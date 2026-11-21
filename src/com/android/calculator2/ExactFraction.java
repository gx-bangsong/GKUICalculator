/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;

/** Formatting only; never reconstruct a rational from displayed digits or an approximation. */
final class ExactFraction {
    static final int MAX_BITS=32768;
    static final int MAX_CHARACTERS=8192;
    static final class TooLong extends ArithmeticException {}
    static String format(UnifiedReal value) {
        if(value==null||!value.definitelyRational())return null;
        BoundedRational rational=value.boundedRationalValue();
        if(rational==null)return null;
        return rational.auxiliaryFraction(MAX_BITS,MAX_CHARACTERS);
    }
    private ExactFraction() {}
}
