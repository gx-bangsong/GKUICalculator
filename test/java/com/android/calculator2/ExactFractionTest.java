/* SPDX-License-Identifier: Apache-2.0 */
package com.android.calculator2;
import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExactFractionTest {
    private static UnifiedReal rational(long n,long d){return new UnifiedReal(new BoundedRational(n,d));}
    @Test public void exactArithmeticAndReduction() {
        assertEquals("1/3",ExactFraction.format(rational(1,3)));
        assertEquals("1/2",ExactFraction.format(rational(2,4)));
        assertEquals("7/3",ExactFraction.format(rational(7,3)));
        assertEquals("-1/8",ExactFraction.format(rational(-1,8)));
        assertEquals("-1/8",ExactFraction.format(rational(1,-8)));
        assertEquals("3/10",ExactFraction.format(rational(1,10).add(rational(2,10))));
        assertEquals("3333/10000",ExactFraction.format(rational(3333,10000)));
        assertNull(ExactFraction.format(rational(4,2)));
        assertNull(ExactFraction.format(rational(0,3)));
        assertNull(ExactFraction.format(new UnifiedReal(2).sqrt()));
        assertNull(ExactFraction.format(UnifiedReal.PI));
        assertNull(ExactFraction.format(null));
    }
    @Test public void divisionByZeroKeepsOriginalError() {
        try {UnifiedReal.ONE.divide(UnifiedReal.ZERO);fail("division by zero must fail");}
        catch(ArithmeticException expected){}
    }
    @Test public void fullFractionIsNotTruncated() {
        String s=ExactFraction.format(new UnifiedReal(new BoundedRational(BigInteger.ONE,BigInteger.TEN.pow(1000))));
        assertEquals(1003,s.length());assertTrue(s.startsWith("1/1"));assertTrue(s.endsWith("000"));
    }
    @Test public void hugeValuesHaveExplicitBudget() {
        try {ExactFraction.format(new UnifiedReal(new BoundedRational(BigInteger.ONE,BigInteger.ONE.shiftLeft(40000))));fail();}
        catch(ExactFraction.TooLong expected){}
        try {ExactFraction.format(new UnifiedReal(new BoundedRational(BigInteger.ONE,BigInteger.TEN.pow(9000))));fail();}
        catch(ExactFraction.TooLong expected){}
        assertNull(ExactFraction.format(new UnifiedReal(BigInteger.ONE.shiftLeft(40000))));
    }
}
