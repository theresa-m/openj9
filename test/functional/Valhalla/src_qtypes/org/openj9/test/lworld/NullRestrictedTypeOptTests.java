/*
 * Copyright IBM Corp. and others 2023
 *
 * This program and the accompanying materials are made available under
 * the terms of the Eclipse Public License 2.0 which accompanies this
 * distribution and is available at https://www.eclipse.org/legal/epl-2.0/
 * or the Apache License, Version 2.0 which accompanies this distribution and
 * is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * This Source Code may also be made available under the following
 * Secondary Licenses when the conditions for such availability set
 * forth in the Eclipse Public License, v. 2.0 are satisfied: GNU
 * General Public License, version 2 with the GNU Classpath
 * Exception [1] and GNU General Public License, version 2 with the
 * OpenJDK Assembly Exception [2].
 *
 * [1] https://www.gnu.org/software/classpath/license.html
 * [2] https://openjdk.org/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0-only WITH Classpath-exception-2.0 OR GPL-2.0-only WITH OpenJDK-assembly-exception-1.0
 */
package org.openj9.test.lworld;

import org.testng.Assert;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;

import jdk.internal.vm.annotation.ImplicitlyConstructible;
import jdk.internal.vm.annotation.NullRestricted;


@Test(groups = { "level.sanity" })
public class NullRestrictedTypeOptTests {

	// A primitive (null-restricted) value class
	@ImplicitlyConstructible
	public value static class PrimPair {
		public final int x, y;

		public PrimPair(int x, int y) {
			this.x = x; this.y = y;
		}

		public PrimPair(PrimPair p) {
			this.x = p.x; this.y = p.y;
		}
	}

	public static class EscapeException extends RuntimeException {
		public Object escapingObject;

		public EscapeException(Object o) {
			escapingObject = o;
		}
	}

	public static int result = 0;
	@NullRestricted
	public static PrimPair[] parr = new PrimPair[] { new PrimPair(3, 4), new PrimPair(0, 0), new PrimPair(0, 0) };

	@Test(priority=1)
	static public void testEAOpportunity1() throws Throwable {
		for (int i = 0; i < 10000; i++) {
			result = 0;
			for (int j = 0; j < 100; j++) {
				evalTestEA1a(j);
			}
			assertEquals(result, 500);

			result = 0;
			evalTestEA1b();
			assertEquals(result, 500);
		}
	}

	@NullRestricted
	static PrimPair evalTestEA1a_p1;
	@NullRestricted
	static PrimPair evalTestEA1a_p2;
	@NullRestricted
	static PrimPair evalTestEA1a_p3;
	@NullRestricted
	static PrimPair evalTestEA1a_p4;

	static private void evalTestEA1a(int iter) {
		// Test situation where EA could apply to value p1,
		// but might have to allocate contiguously
		evalTestEA1a_p1 = new PrimPair(1, 2);
		evalTestEA1a_p2 = parr[0];

		if (iter % 2 == 0) {
			evalTestEA1a_p3 = evalTestEA1a_p2;
			evalTestEA1a_p4 = evalTestEA1a_p1;
		} else {
			evalTestEA1a_p3 = evalTestEA1a_p1;
			evalTestEA1a_p4 = evalTestEA1a_p2;
		}
		result += evalTestEA1a_p3.x + evalTestEA1a_p4.y;
	}

	@NullRestricted
	static PrimPair evalTestEA1b_p1;
	@NullRestricted
	static PrimPair evalTestEA1b_p2;

	@NullRestricted
	static PrimPair evalTestEA1b_p3;
	@NullRestricted
	static PrimPair evalTestEA1b_p4;

	static private void evalTestEA1b() {
		for (int j = 0; j < 100; j++) {
			// Test situation where EA could apply to value p1,
			// but might have to allocate contiguously
			evalTestEA1b_p1 = new PrimPair(1, 2);
			evalTestEA1b_p2 = parr[0];

			if (j % 2 == 0) {
				evalTestEA1b_p3 = evalTestEA1b_p2;
				evalTestEA1b_p4 = evalTestEA1b_p1;
			} else {
				evalTestEA1b_p3 = evalTestEA1b_p1;
				evalTestEA1b_p4 = evalTestEA1b_p2;
			}
			result += evalTestEA1b_p3.x + evalTestEA1b_p4.y;
		}
	}

	@NullRestricted
	public static PrimPair testEA2Field = new PrimPair(0,0);

	@Test(priority=1)
	static public void testEAOpportunity2() throws Throwable {
		for (int i = 0; i < 10000; i++) {
			result = 0;
			evalTestEA2(-1);  // No escape
			assertEquals(result, 200);
			assertEquals(testEA2Field, new PrimPair(0,0));
		}
		evalTestEA2(99);  // Escape for index 99
		assertEquals(testEA2Field, new PrimPair(2,1));
		evalTestEA2(98);  // Escape for index 98
		assertEquals(testEA2Field, new PrimPair(1,2));
	}

	@NullRestricted
	static PrimPair evalTestEA2_p1;

	static private void evalTestEA2(int escapePoint) {
		int x = 1; int y = 2;
		int[] nextVal = {0, 2, 1};

		for (int i = 0; i < 100; i++) {
			evalTestEA2_p1 = new PrimPair(x, y);
			int updatex = nextVal[x];
			int updatey = nextVal[y];
			x = updatex;
			y = updatey;
			if (evalTestEA2_p1.x*evalTestEA2_p1.y != 2 || escapePoint == i) testEA2Field = evalTestEA2_p1;  // Value might escape

			result += x*y;
		}
	}

	@Test(priority=1)
	static public void testEAOpportunity3() throws Throwable {
		for (int i = 0; i < 1000; i++) {
			result = 0;
			evalTestEA3();
			assertEquals(result, 1000);
		}
	}

	@NullRestricted
	static PrimPair[] evalTestEA3_arr;
	@NullRestricted
	static PrimPair evalTestEA3_val;

	static private void evalTestEA3() {
		for (int i = 0; i < 100; i++) {
			// Test potential stack allocation of array of value type
			evalTestEA3_arr = new PrimPair[] {new PrimPair(1, 2), new PrimPair(3, 4)};
			for (int j = 0; j < evalTestEA3_arr.length; j++) {
				evalTestEA3_val = evalTestEA3_arr[j%evalTestEA3_arr.length];
				result += evalTestEA3_val.x + evalTestEA3_val.y;
			}
		}
	}

	// A primitive (null-restricted) value class with primitive value class fields
	@ImplicitlyConstructible
	public value static class NestedPrimPair {
		@NullRestricted
		public final PrimPair p1;
		@NullRestricted
		public final PrimPair p2;

		public NestedPrimPair(int i, int j, int m, int n) {
			this.p1 = new PrimPair(i, j);
			this.p2 = new PrimPair(m, n);
		}

		public NestedPrimPair(PrimPair p1, PrimPair p2) {
			this.p1 = new PrimPair(p1);
			this.p2 = new PrimPair(p2);
		}
	}

	// An array whose component type is a primitive (null-restricted) value class
	@NullRestricted
	public static NestedPrimPair[] nestedprimarr = new NestedPrimPair[] { new NestedPrimPair(11, 12, 13, 14) };

	@Test(priority=1)
	static public void testEAOpportunity4() throws Throwable {
		for (int i = 0; i < 10000; i++) {
			result = 0;
			for (int j = 0; j < 100; j++) {
				evalTestEA4a(j);
			}
			assertEquals(result, 1500);

			result = 0;
			evalTestEA4b();
			assertEquals(result, 1500);
		}
	}

	@NullRestricted
	static NestedPrimPair evalTestEA4a_p1;
	@NullRestricted
	static NestedPrimPair evalTestEA4a_p2;

	@NullRestricted
	static NestedPrimPair evalTestEA4a_p3;
	@NullRestricted
	static NestedPrimPair evalTestEA4a_p4;

	static private void evalTestEA4a(int iter) {
		// Test situation where EA could apply to value p1,
		// but might have to allocate contiguously
		evalTestEA4a_p1 = new NestedPrimPair(1, 2, 3, 4);
		evalTestEA4a_p2 = nestedprimarr[0];

		if (iter % 2 == 0) {
			evalTestEA4a_p3 = evalTestEA4a_p2;
			evalTestEA4a_p4 = evalTestEA4a_p1;
		} else {
			evalTestEA4a_p3 = evalTestEA4a_p1;
			evalTestEA4a_p4 = evalTestEA4a_p2;
		}
		result += evalTestEA4a_p3.p1.x + evalTestEA4a_p4.p2.y;
	}

	@NullRestricted
	static NestedPrimPair evalTestEA4b_p1;
	@NullRestricted
	static NestedPrimPair evalTestEA4b_p2;

	@NullRestricted
	static NestedPrimPair evalTestEA4b_p3;
	@NullRestricted
	static NestedPrimPair evalTestEA4b_p4;

	static private void evalTestEA4b() {
		for (int j = 0; j < 100; j++) {
			// Test situation where EA could apply to value p1,
			// but might have to allocate contiguously.  Also,
			// extra challenges for stack allocation in a loop
			evalTestEA4b_p1 = new NestedPrimPair(1, 2, 3, 4);
			evalTestEA4b_p2 = nestedprimarr[0];

			if (j % 2 == 0) {
				evalTestEA4b_p3 = evalTestEA4b_p1;
				evalTestEA4b_p4 = evalTestEA4b_p2;
			} else {
				evalTestEA4b_p3 = evalTestEA4b_p2;
				evalTestEA4b_p4 = evalTestEA4b_p1;
			}

			result += evalTestEA4b_p3.p1.x + evalTestEA4b_p4.p2.y;
		}
	}

	@NullRestricted
	public static NestedPrimPair testEA5Field = new NestedPrimPair(0,0,0,0);

	@Test(priority=1)
	static public void testEAOpportunity5() throws Throwable {
		for (int i = 0; i < 10000; i++) {
			result = 0;
			evalTestEA5();
			assertEquals(result, 2400);
			assertEquals(testEA5Field, new NestedPrimPair(0,0,0,0));
		}
	}

	@NullRestricted
	static NestedPrimPair evalTestEA5_p;

	static private void evalTestEA5() {
		int x = 1; int y = 2; int z = 3; int w = 4;

		for (int i = 0; i < 100; i++) {
			evalTestEA5_p = new NestedPrimPair(x, y, z, w);
			int updatex = (x-y)*(x-y);
			int updatey = y*(y-x);
			int updatez = (z-w)*(z-w)*z;
			int updatew = w*(w-z);
			x = updatex;
			y = updatey;
			z = updatez;
			w = updatew;
			if (evalTestEA5_p.p1.x*evalTestEA5_p.p1.y+evalTestEA5_p.p2.x*evalTestEA5_p.p2.y != 14) {
				testEA5Field = evalTestEA5_p;  // Looks like value might escape (but never actually does)
			}

			result += x*y*z*w;
		}
	}

	@Test(priority=1)
	static public void testEAOpportunity6() throws Throwable {
		for (int i = 0; i < 1000; i++) {
			result = 0;
			evalTestEA6();
			assertEquals(result, 1800);
		}
	}

	@NullRestricted
	static NestedPrimPair[] evalTestEA6_arr;
	@NullRestricted
	static NestedPrimPair evalTestEA6_val;
			
	static private void evalTestEA6() {
		for (int i = 0; i < 100; i++) {
			// Test potential stack allocation of array of value type
			evalTestEA6_arr = new NestedPrimPair[] {new NestedPrimPair(1, 2, 3, 4), new NestedPrimPair(5, 6, 7, 8)};
			for (int j = 0; j < evalTestEA6_arr.length; j++) {
				evalTestEA6_val = evalTestEA6_arr[j%evalTestEA6_arr.length];
				result += evalTestEA6_val.p1.x + evalTestEA6_val.p2.y;
			}
		}
	}

	public static int sval7 = 0;
	@NullRestricted
	public static PrimPair escape7 = new PrimPair(0,0);

	@Test(priority=1)
	static public void testEAOpportunity7() throws Throwable {
		result = 0;
		for (int i = 0; i < 100000; i++) {
			evalTestEA7(i*sval7);
		}
		assertEquals(result, 500000);
		assertEquals(escape7, new PrimPair(0,0));

		evalTestEA7(sval7+99);
		assertEquals(result, 500000);
		assertEquals(escape7, new PrimPair(1,2));
	}

	@NullRestricted
	static PrimPair evalTestEA7_p;

	static private void evalTestEA7(int i) {
		// Test cold block escape for nested value object
		evalTestEA7_p = new PrimPair(1, 2);

		try {
			result += evalTestEA7_p.x + parr[i].y;
		} catch (ArrayIndexOutOfBoundsException aioobe) {
			escape7 = evalTestEA7_p;
		}
	}

	public static int sval8 = 0;
	@NullRestricted
	public static NestedPrimPair escape8 = new NestedPrimPair(0,0,0,0);

	@Test(priority=1)
	static public void testEAOpportunity8() throws Throwable {
		result = 0;
		for (int i = 0; i < 100000; i++) {
			evalTestEA8(i*sval8);
		}
		assertEquals(result, 1500000);
		assertEquals(escape8, new NestedPrimPair(0,0,0,0));

		evalTestEA8(sval8+99);
		assertEquals(result, 1500000);
		assertEquals(escape8, new NestedPrimPair(1,2,3,4));
	}

	@NullRestricted
	static NestedPrimPair evalTestEA8_p;

	static private void evalTestEA8(int i) {
		// Test cold block escape for nested primitive value objects
		evalTestEA8_p = new NestedPrimPair(1, 2, 3, 4);

		try {
			result += evalTestEA8_p.p1.x + nestedprimarr[i].p2.y;
		} catch (ArrayIndexOutOfBoundsException aioobe) {
			escape8 = evalTestEA8_p;
		}
	}

	public static class TestStoreToNullRestrictedField {
		@NullRestricted
		public PrimPair nullRestrictedInstanceField;
		@NullRestricted
		public static PrimPair nullRestrictedStaticField;

		public void replaceInstanceField(Object val) {
			nullRestrictedInstanceField = (PrimPair) val;
		}

		public static void replaceStaticField(Object val) {
			nullRestrictedStaticField = (PrimPair) val;
		}
	}

	@ImplicitlyConstructible
	public static value class TestWithFieldStoreToNullRestrictedField {
		@NullRestricted
		public PrimPair nullRestrictedInstanceField;

		public TestWithFieldStoreToNullRestrictedField(Object val) {
			this.nullRestrictedInstanceField = (PrimPair) val;
		}
	}

	@Test
	static public void testStoreNullValueToNullRestrictedInstanceField() throws Throwable {
		TestStoreToNullRestrictedField storeFieldObj = new TestStoreToNullRestrictedField();
		storeFieldObj.replaceInstanceField(new PrimPair(1, 2));

		try {
			storeFieldObj.replaceInstanceField(null);
		} catch (NullPointerException npe) {
			return; /* pass */
		}

		Assert.fail("Expect a NullPointerException. No exception or wrong kind of exception thrown");
	}

	@Test
	static public void testStoreNullValueToNullRestrictedStaticField() throws Throwable {
		TestStoreToNullRestrictedField.replaceStaticField(new PrimPair(1, 2));

		try {
			TestStoreToNullRestrictedField.replaceStaticField(null);
		} catch (NullPointerException npe) {
			return; /* pass */
		}

		Assert.fail("Expect a NullPointerException. No exception or wrong kind of exception thrown");
	}

	@Test
	static public void testWithFieldStoreToNullRestrictedField() throws Throwable {
		TestWithFieldStoreToNullRestrictedField withFieldObj = new TestWithFieldStoreToNullRestrictedField(new PrimPair(1, 2));

		try {
			withFieldObj = new TestWithFieldStoreToNullRestrictedField(null);
		} catch (NullPointerException npe) {
			return; /* pass */
		}

		Assert.fail("Expect a NullPointerException. No exception or wrong kind of exception thrown");
	}
}
