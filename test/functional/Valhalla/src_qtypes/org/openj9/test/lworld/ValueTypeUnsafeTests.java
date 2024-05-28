/*
 * Copyright IBM Corp. and others 2021
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

import jdk.internal.misc.Unsafe;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;
import jdk.internal.vm.annotation.ImplicitlyConstructible;
import jdk.internal.vm.annotation.NullRestricted;
import static org.testng.Assert.*;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import static org.openj9.test.lworld.ValueTypeTestClasses.*;

@Test(groups = { "level.sanity" })
public class ValueTypeUnsafeTests {
	static Unsafe myUnsafe = Unsafe.getUnsafe();
	static boolean isCompressedRefsEnabled = false;
	static boolean isDualHeaderShapeDisabled = false;
	static boolean isGcPolicyGencon = false;
	static boolean isFlatteningEnabled = false;
	static boolean isArrayFlatteningEnabled = false;

	@NullRestricted
	static ValueTypePoint2D vtPoint;
	@NullRestricted
	static ValueTypePoint2D[] vtPointAry;
	@NullRestricted
	static ValueTypeInt[] vtIntAry;
	static long vtPointOffsetX;
	static long vtPointOffsetY;
	static long vtLongPointOffsetX;
	static long vtLongPointOffsetY;
	static long vtPointAryOffset0;
	static long vtPointAryOffset1;
	static long intWrapperOffsetVti;
	static long vtIntAryOffset0;
	static long vtIntAryOffset1;

	@BeforeClass
	static public void testSetUp() throws Throwable {
		List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
		isFlatteningEnabled = arguments.contains("-XX:ValueTypeFlatteningThreshold=99999");
		isArrayFlatteningEnabled = arguments.contains("-XX:+EnableArrayFlattening");
		isCompressedRefsEnabled = arguments.contains("-Xcompressedrefs");
		// If dual header shape is disabled array header will have dataAddr field, 8 bytes in size, regardless of GC policy
		isDualHeaderShapeDisabled = arguments.contains("-XXgc:disableIndexableDualHeaderShape");
		// Dual header shape affects Gencon GC only
		isGcPolicyGencon = arguments.contains("-Xgcpolicy:gencon")
			|| (!arguments.contains("-Xgcpolicy:optthruput")
				&& !arguments.contains("-Xgcpolicy:balanced")
				&& !arguments.contains("-Xgcpolicy:metronome")
				&& !arguments.contains("-Xgcpolicy:optavgpause"));

		vtPointOffsetX = myUnsafe.objectFieldOffset(ValueTypePoint2D.class.getDeclaredField("x"));
		vtPointOffsetY = myUnsafe.objectFieldOffset(ValueTypePoint2D.class.getDeclaredField("y"));
		vtLongPointOffsetX = myUnsafe.objectFieldOffset(ValueTypeLongPoint2D.class.getDeclaredField("x"));
		vtLongPointOffsetY = myUnsafe.objectFieldOffset(ValueTypeLongPoint2D.class.getDeclaredField("y"));
		vtPointAryOffset0 = myUnsafe.arrayBaseOffset(ValueTypePoint2D[].class);
		intWrapperOffsetVti = myUnsafe.objectFieldOffset(IntWrapper.class.getDeclaredField("vti"));
		vtIntAryOffset0 = myUnsafe.arrayBaseOffset(ValueTypeInt[].class);
	}

	// array must not be empty
	static private <T> long arrayElementSize(T[] array) {
		// don't simply use getObjectSize(array[0]) - valueHeaderSize(array[0].getClass()) because the amount of memory used to store an object may be different depending on if it is inside an array or not (for instance, when flattening is disabled)
		return (myUnsafe.getObjectSize(array) - myUnsafe.arrayBaseOffset(array.getClass())) / array.length;
	}

	@BeforeMethod
	static public void setUp() {
		vtPoint = new ValueTypePoint2D(new ValueTypeInt(7), new ValueTypeInt(8));
		vtPointAry = new ValueTypePoint2D[] {
			new ValueTypePoint2D(new ValueTypeInt(5), new ValueTypeInt(10)),
			new ValueTypePoint2D(new ValueTypeInt(10), new ValueTypeInt(20))
		};
		vtPointAryOffset1 = vtPointAryOffset0 + arrayElementSize(vtPointAry);
		vtIntAry = new ValueTypeInt[] { new ValueTypeInt(1), new ValueTypeInt(2) };
		vtIntAryOffset1 = vtIntAryOffset0 + arrayElementSize(vtIntAry);
	}

	public static class CompareAndDoSomethingFunction {
		private Method method;

		public CompareAndDoSomethingFunction(String methodName) throws NoSuchMethodException {
			this.method = myUnsafe.getClass().getMethod(
				methodName,
				new Class[]{Object.class, long.class, Class.class, Object.class, Object.class}
			);
		}

		public boolean execute(Object obj, long offset, Class<?> clz, Object v1, Object v2) throws Throwable {
			Object returned = null;
			try {
				returned = method.invoke(myUnsafe, obj, offset, clz, v1, v2);
			} catch (InvocationTargetException exception) {
				throw exception.getCause();
			}
			boolean result = false;
			if (returned instanceof Boolean) {
				result = (Boolean)returned;
			} else {
				result = (returned == v1);
			}
			return result;
		}

		public String toString() {
			return "CompareAndDoSomethingFunction(" + method.getName() + ")";
		}
	}

	@DataProvider(name = "compareAndDoSomethingFuncs")
	static public Object[][] compareAndDoSomethingFuncs() throws NoSuchMethodException {
		return new Object[][] {
			{new CompareAndDoSomethingFunction("compareAndSetValue")},
			{new CompareAndDoSomethingFunction("compareAndExchangeValue")},
		};
	}

	@Test
	static public void testFlattenedFieldIsFlattened() throws Throwable {
		boolean isFlattened = myUnsafe.isFlatField(vtPoint.getClass().getDeclaredField("x"));
		assertEquals(isFlattened, isFlatteningEnabled);
	}

	@Test
	static public void testFlattenedArrayIsFlattened() throws Throwable {
		boolean isArrayFlattened = myUnsafe.isFlatArray(vtPointAry.getClass());
		assertEquals(isArrayFlattened, isArrayFlatteningEnabled);
	}

	@Test
	static public void testRegularArrayIsNotFlattened() throws Throwable {
		Point2D[] ary = {};
		assertTrue(ary.getClass().isArray());
		boolean isArrayFlattened = myUnsafe.isFlatArray(ary.getClass());
		assertFalse(isArrayFlattened);
	}

	@Test
	static public void testRegularFieldIsNotFlattened() throws Throwable {
		Point2D p = new Point2D(1, 1);
		boolean isFlattened = myUnsafe.isFlatField(p.getClass().getDeclaredField("x"));
		assertFalse(isFlattened);
	}

	@Test
	static public void testUnflattenableFieldInVTIsNotFlattened() throws Throwable {
		ValueClassPoint2D p = new ValueClassPoint2D(new ValueClassInt(1), new ValueClassInt(2));
		boolean isFlattened = myUnsafe.isFlatField(p.getClass().getDeclaredField("x"));
		assertFalse(isFlattened);
	}

	@Test
	static public void testFlattenedObjectIsNotFlattenedArray() throws Throwable {
		boolean isArrayFlattened = myUnsafe.isFlatArray(vtPoint.getClass());
		assertFalse(isArrayFlattened);
	}

	@Test
	static public void testNullIsNotFlattenedArray() throws Throwable {
		boolean isArrayFlattened = myUnsafe.isFlatArray(null);
		assertFalse(isArrayFlattened);
	}

	@Test
	static public void testPassingNullToIsFlattenedThrowsException() throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			myUnsafe.isFlatField(null);
		});
	}

	@Test
	static public void testValueHeaderSizeOfValueType() throws Throwable {
		long headerSize = myUnsafe.valueHeaderSize(ValueTypePoint2D.class);
		if (isCompressedRefsEnabled) {
			assertEquals(headerSize, 4);
		} else {
			assertEquals(headerSize, 8);
		}
	}

	@Test
	static public void testValueHeaderSizeOfClassWithLongField() throws Throwable {
		long headerSize = myUnsafe.valueHeaderSize(ValueTypeWithLongField.class);
		if (isCompressedRefsEnabled && !isFlatteningEnabled) {
			assertEquals(headerSize, 4);
		} else {
			// In compressed refs mode, when the class has 8 byte field(s) only, 4 bytes of padding are added to the class. These padding bytes are included in the headerSize.
			assertEquals(headerSize, 8);
		}
	}

	@Test
	static public void testValueHeaderSizeIsDifferenceOfGetObjectSizeAndDataSize() {
		long dataSize = isCompressedRefsEnabled ? 8 : 16; // when compressed refs are enabled, references and flattened ints will take up 4 bytes of space each. When compressed refs are not enabled they will each be 8 bytes long.
		long headerSize = isCompressedRefsEnabled ? 4 : 8; // just like data size, the header size will also depend on whether compressed refs are enabled
		assertEquals(myUnsafe.valueHeaderSize(vtPoint.getClass()), headerSize);
		assertEquals(myUnsafe.valueHeaderSize(vtPoint.getClass()), myUnsafe.getObjectSize(vtPoint) - dataSize);
	}

	@Test
	static public void testValueHeaderSizeOfNonValueType() throws Throwable {
		assertEquals(myUnsafe.valueHeaderSize(Point2D.class), 0);
	}

	@Test
	static public void testValueHeaderSizeOfNull() throws Throwable {
		assertEquals(myUnsafe.valueHeaderSize(null), 0);
	}

	@Test
	static public void testValueHeaderSizeOfVTArray() throws Throwable {
		long size = myUnsafe.valueHeaderSize(vtIntAry.getClass());
		assertEquals(size, 0);
	}

	@Test
	static public void testValueHeaderSizeOfArray() throws Throwable {
		Point2D[] ary = {};
		long size = myUnsafe.valueHeaderSize(ary.getClass());
		assertEquals(size, 0);
	}

	@NullRestricted
	static ValueTypePoint2D testUninitializedDefaultValueOfValueType_newPoint;
	@NullRestricted
	static ValueTypePoint2D testUninitializedDefaultValueOfValueType_p;

	@Test
	static public void testUninitializedDefaultValueOfValueType() throws Throwable {
		// create a new ValueTypePoint2D to ensure that the class has been initialized befoere myUnsafe.uninitializedDefaultValue is called
		testUninitializedDefaultValueOfValueType_newPoint = new ValueTypePoint2D(new ValueTypeInt(1), new ValueTypeInt(1));
		testUninitializedDefaultValueOfValueType_p = myUnsafe.uninitializedDefaultValue(testUninitializedDefaultValueOfValueType_newPoint.getClass());
		assertEquals(testUninitializedDefaultValueOfValueType_p.x.i, 0);
		assertEquals(testUninitializedDefaultValueOfValueType_p.y.i, 0);
	}

	@Test
	static public void testUninitializedDefaultValueOfNonValueType() throws Throwable {
		Point2D p = myUnsafe.uninitializedDefaultValue(Point2D.class);
		assertNull(p);
	}

	@Test
	static public void testUninitializedDefaultValueOfNull() throws Throwable {
		Object o = myUnsafe.uninitializedDefaultValue(null);
		assertNull(o);
	}

	@Test
	static public void testUninitializedDefaultValueOfVTArray() throws Throwable {
		Object o = myUnsafe.uninitializedDefaultValue(vtIntAry.getClass());
		assertNull(o);
	}

	@Test
	static public void testUninitializedDefaultValueOfArray() throws Throwable {
		Point2D[] ary = {};
		Object o = myUnsafe.uninitializedDefaultValue(ary.getClass());
		assertNull(o);
	}

	@Test
	static public void testuninitializedVTClassHasNullDefaultValue() {
		@ImplicitlyConstructible
		value class NeverInitialized {
			@NullRestricted
			final ValueTypeInt i;
			NeverInitialized(ValueTypeInt i) { this.i = i; }
		}
		assertNull(myUnsafe.uninitializedDefaultValue(NeverInitialized.class));
	}

	@Test
	static public void testNullObjGetValue() throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			myUnsafe.getValue(null, 0, ValueTypeInt.class);
		});
	}

	@Test
	static public void testNullClzGetValue() throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			myUnsafe.getValue(vtPoint, vtPointOffsetX, null);
		});
	}

	@Test
	static public void testNonVTClzGetValue() throws Throwable {
		assertNull(myUnsafe.getValue(vtPoint, intWrapperOffsetVti, IntWrapper.class));
	}

	@NullRestricted
	static ValueTypePoint2D testGetValuesOfArray_p;

	@Test
	static public void testGetValuesOfArray() throws Throwable {
		if (isFlatteningEnabled) {
			testGetValuesOfArray_p = myUnsafe.getValue(vtPointAry, vtPointAryOffset0, ValueTypePoint2D.class);
			assertEquals(testGetValuesOfArray_p.x.i, vtPointAry[0].x.i);
			assertEquals(testGetValuesOfArray_p.y.i, vtPointAry[0].y.i);
			testGetValuesOfArray_p = myUnsafe.getValue(vtPointAry, vtPointAryOffset1, ValueTypePoint2D.class);
			assertEquals(testGetValuesOfArray_p.x.i, vtPointAry[1].x.i);
			assertEquals(testGetValuesOfArray_p.y.i, vtPointAry[1].y.i);
		}
	}

	@NullRestricted
	static ZeroSizeValueType[] testGetValueOfZeroSizeVTArrayDoesNotCauseError_zsvtAry;

	@Test
	static public void testGetValueOfZeroSizeVTArrayDoesNotCauseError() throws Throwable {
		testGetValueOfZeroSizeVTArrayDoesNotCauseError_zsvtAry = new ZeroSizeValueType[] {
			new ZeroSizeValueType(),
			new ZeroSizeValueType()
		};
		long zsvtAryOffset0 = myUnsafe.arrayBaseOffset(testGetValueOfZeroSizeVTArrayDoesNotCauseError_zsvtAry.getClass());
		assertNotNull(myUnsafe.getValue(testGetValueOfZeroSizeVTArrayDoesNotCauseError_zsvtAry, zsvtAryOffset0, ZeroSizeValueType.class));
	}

	@NullRestricted
	static ZeroSizeValueTypeWrapper zsvtw;

	@Test
	static public void testGetValueOfZeroSizeVTObjectDoesNotCauseError() throws Throwable {
		zsvtw = new ZeroSizeValueTypeWrapper();
		long zsvtwOffset0 = myUnsafe.objectFieldOffset(ZeroSizeValueTypeWrapper.class.getDeclaredField("z"));
		assertNotNull(myUnsafe.getValue(zsvtw, zsvtwOffset0, ZeroSizeValueType.class));
	}

	@NullRestricted
	static ValueTypeInt testGetValuesOfObject_x;
	@NullRestricted
	static ValueTypeInt testGetValuesOfObject_y;

	@Test
	static public void testGetValuesOfObject() throws Throwable {
		testGetValuesOfObject_x = myUnsafe.getValue(vtPoint, vtPointOffsetX, ValueTypeInt.class);
		testGetValuesOfObject_y = myUnsafe.getValue(vtPoint, vtPointOffsetY, ValueTypeInt.class);
		if (isFlatteningEnabled) {
			assertEquals(testGetValuesOfObject_x.i, vtPoint.x.i);
			assertEquals(testGetValuesOfObject_y.i, vtPoint.y.i);
		}
	}

	@NullRestricted
	static ValueTypeLongPoint2D testGetValueOnVTWithLongFields_vtLongPoint;
	@NullRestricted
	static ValueTypeLong testGetValueOnVTWithLongFields_x;
	@NullRestricted
	static ValueTypeLong testGetValueOnVTWithLongFields_y;


	@Test
	static public void testGetValueOnVTWithLongFields() throws Throwable {
		testGetValueOnVTWithLongFields_vtLongPoint = new ValueTypeLongPoint2D(123, 456);
		testGetValueOnVTWithLongFields_x = myUnsafe.getValue(testGetValueOnVTWithLongFields_vtLongPoint, vtLongPointOffsetX, ValueTypeLong.class);
		testGetValueOnVTWithLongFields_y = myUnsafe.getValue(testGetValueOnVTWithLongFields_vtLongPoint, vtLongPointOffsetY, ValueTypeLong.class);

		if (isFlatteningEnabled) {
			assertEquals(testGetValueOnVTWithLongFields_x.l, testGetValueOnVTWithLongFields_vtLongPoint.x.l);
			assertEquals(testGetValueOnVTWithLongFields_y.l, testGetValueOnVTWithLongFields_vtLongPoint.y.l);
		}

		assertEquals(testGetValueOnVTWithLongFields_vtLongPoint.x.l, 123);
		assertEquals(testGetValueOnVTWithLongFields_vtLongPoint.y.l, 456);
	}

	@NullRestricted
	static ValueTypeInt vti;
	
	@Test
	static public void testGetValueOnNonVTObj() throws Throwable {
		if (isFlatteningEnabled) {
			IntWrapper iw = new IntWrapper(7);
			vti = myUnsafe.getValue(iw, intWrapperOffsetVti, ValueTypeInt.class);
			assertEquals(vti.i, iw.vti.i);
			assertEquals(iw.vti.i, 7);
		}
	}

	@Test
	static public void testNullObjPutValue() throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			myUnsafe.putValue(null, vtPointOffsetX, ValueTypeInt.class, new ValueTypeInt(1));
		});
	}

	@Test
	static public void testNullClzPutValue() throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			myUnsafe.putValue(vtPoint, vtPointOffsetX, null, new ValueTypeInt(1));
		});
	}

	@Test
	static public void testNullValuePutValue() throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			myUnsafe.putValue(vtPoint, vtPointOffsetX, ValueTypeInt.class, null);
		});
	}

	@Test
	static public void testPutValueWithNonVTclzAndValue() throws Throwable {
		int xBeforeTest = vtPoint.x.i;
		assertNotEquals(xBeforeTest, 10000);
		myUnsafe.putValue(vtPoint, vtPointOffsetX, IntWrapper.class, new IntWrapper(10000));
		assertEquals(vtPoint.x.i, xBeforeTest);
	}

	@Test
	static public void testPutValueWithNonVTClzButVTValue() throws Throwable {
		int xBeforeTest = vtPoint.x.i;
		assertNotEquals(xBeforeTest, 10000);
		myUnsafe.putValue(vtPoint, vtPointOffsetX, IntWrapper.class, new ValueTypeInt(10000));
		assertEquals(vtPoint.x.i, xBeforeTest);
	}

	@NullRestricted
	static ValueTypeInt newVal;

	@Test
	static public void testPutValueWithNonVTObj() throws Throwable {
		if (isFlatteningEnabled) {
			IntWrapper iw = new IntWrapper(7);
			newVal = new ValueTypeInt(5892);
			myUnsafe.putValue(iw, intWrapperOffsetVti, ValueTypeInt.class, newVal);
			assertEquals(iw.vti.i, newVal.i);
			assertEquals(newVal.i, 5892);
		}
	}

	@NullRestricted
	static ValueTypePoint2D testPutValuesOfArray_p;

	@Test
	static public void testPutValuesOfArray() throws Throwable {
		if (isFlatteningEnabled) {
			testPutValuesOfArray_p = new ValueTypePoint2D(new ValueTypeInt(34857), new ValueTypeInt(784382));
			myUnsafe.putValue(vtPointAry, vtPointAryOffset0, ValueTypePoint2D.class, testPutValuesOfArray_p);
			assertEquals(vtPointAry[0].x.i, testPutValuesOfArray_p.x.i);
			assertEquals(vtPointAry[0].y.i, testPutValuesOfArray_p.y.i);
			myUnsafe.putValue(vtPointAry, vtPointAryOffset1, ValueTypePoint2D.class, testPutValuesOfArray_p);
			assertEquals(vtPointAry[1].x.i, testPutValuesOfArray_p.x.i);
			assertEquals(vtPointAry[1].y.i, testPutValuesOfArray_p.y.i);
			assertEquals(testPutValuesOfArray_p.x.i, 34857);
			assertEquals(testPutValuesOfArray_p.y.i, 784382);
		}
	}

	@NullRestricted
	static ZeroSizeValueType[] testPutValueWithZeroSizeVTArrayDoesNotCauseError_zsvtAry;

	@Test
	static public void testPutValueWithZeroSizeVTArrayDoesNotCauseError() throws Throwable {
		testPutValueWithZeroSizeVTArrayDoesNotCauseError_zsvtAry = new ZeroSizeValueType[] {
			new ZeroSizeValueType(),
			new ZeroSizeValueType()
		};
		long zsvtAryOffset0 = myUnsafe.arrayBaseOffset(testPutValueWithZeroSizeVTArrayDoesNotCauseError_zsvtAry.getClass());
		myUnsafe.putValue(testPutValueWithZeroSizeVTArrayDoesNotCauseError_zsvtAry, 
			zsvtAryOffset0, ZeroSizeValueType.class, new ZeroSizeValueType());
	}

	@NullRestricted
	static ZeroSizeValueTypeWrapper testPutValueOfZeroSizeVTObjectDoesNotCauseError_zsvtw;

	@Test
	static public void testPutValueOfZeroSizeVTObjectDoesNotCauseError() throws Throwable {
		testPutValueOfZeroSizeVTObjectDoesNotCauseError_zsvtw = new ZeroSizeValueTypeWrapper();
		long zsvtwOffset0 = myUnsafe.objectFieldOffset(ZeroSizeValueTypeWrapper.class.getDeclaredField("z"));
		myUnsafe.putValue(testPutValueOfZeroSizeVTObjectDoesNotCauseError_zsvtw, zsvtwOffset0, ZeroSizeValueType.class, new ZeroSizeValueType());
	}

	@NullRestricted
	static ValueTypeInt testPutValuesOfObject_newI;

	@Test
	static public void testPutValuesOfObject() throws Throwable {
		testPutValuesOfObject_newI = new ValueTypeInt(47538);
		myUnsafe.putValue(vtPoint, vtPointOffsetX, ValueTypeInt.class, testPutValuesOfObject_newI);
		myUnsafe.putValue(vtPoint, vtPointOffsetY, ValueTypeInt.class, testPutValuesOfObject_newI);
		if (isFlatteningEnabled) {
			assertEquals(vtPoint.x.i, testPutValuesOfObject_newI.i);
			assertEquals(vtPoint.y.i, testPutValuesOfObject_newI.i);
		}
		assertEquals(testPutValuesOfObject_newI.i, 47538);
	}

	@NullRestricted
	static ValueTypeLongPoint2D testPutValueOnVTWithLongFields_vtLongPoint;
	@NullRestricted
	static ValueTypeLong testPutValueOnVTWithLongFields_newVal;

	@Test
	static public void testPutValueOnVTWithLongFields() throws Throwable {
		testPutValueOnVTWithLongFields_vtLongPoint = new ValueTypeLongPoint2D(123, 456);
		testPutValueOnVTWithLongFields_newVal = new ValueTypeLong(23427);
		myUnsafe.putValue(testPutValueOnVTWithLongFields_vtLongPoint, vtLongPointOffsetX, ValueTypeLong.class, newVal);
		myUnsafe.putValue(testPutValueOnVTWithLongFields_vtLongPoint, vtLongPointOffsetY, ValueTypeLong.class, newVal);
		if (isFlatteningEnabled) {
			assertEquals(testPutValueOnVTWithLongFields_vtLongPoint.y.l, testPutValueOnVTWithLongFields_newVal.l);
			assertEquals(testPutValueOnVTWithLongFields_vtLongPoint.x.l, testPutValueOnVTWithLongFields_newVal.l);
		}
		assertEquals(testPutValueOnVTWithLongFields_newVal.l, 23427);
	}

	@Test
	static public void testGetSizeOfVT() {
		long size = myUnsafe.getObjectSize(vtPoint);
		if (isCompressedRefsEnabled) {
			assertEquals(size, 12);
		} else {
			assertEquals(size, 24);
		}
	}

	@Test
	static public void testGetSizeOfNonVT() {
		long size = myUnsafe.getObjectSize(new IntWrapper(5));
		if (isCompressedRefsEnabled) {
			assertEquals(size, 16);
		} else {
			assertEquals(size, 24);
		}
	}

	@Test
	static public void testGetSizeOfVTWithLongFields() {
		long size = myUnsafe.getObjectSize(new ValueTypeLongPoint2D(123, 456));
		if (isCompressedRefsEnabled && !isFlatteningEnabled) {
			assertEquals(size, 12);
		} else {
			assertEquals(size, 24);
		}
	}

	@Test
	static public void testGetSizeOfArray() {
		long size = myUnsafe.getObjectSize(vtIntAry);

		int headerSizeAdjustment = (!isDualHeaderShapeDisabled && isGcPolicyGencon) ? 8 : 0;
		if (isCompressedRefsEnabled) {
			assertEquals(size, 24 - headerSizeAdjustment);
		} else {
			assertEquals(size, 40 - headerSizeAdjustment);
		}
	}

	@Test
	static public void testGetSizeOfZeroSizeVT() {
		long size = myUnsafe.getObjectSize(new ZeroSizeValueType());
		if (isCompressedRefsEnabled) {
			assertEquals(size, 4);
		} else {
			assertEquals(size, 8);
		}
	}

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetNullObj(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			compareAndSwapValue.execute(null, vtPointOffsetX, ValueTypeInt.class, vtPoint.x, new ValueTypeInt(1));
		});
	}

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetNullClz(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		if (isFlatteningEnabled) {
			assertThrows(NullPointerException.class, () -> {
				compareAndSwapValue.execute(vtPoint, vtPointOffsetX, null, vtPoint.x, new ValueTypeInt(1));
			});
		} else {
			boolean result = compareAndSwapValue.execute(vtPoint, vtPointOffsetX, null, vtPoint.x, new ValueTypeInt(1));
			assertTrue(result);
			assertEquals(vtPoint.x.i, 1);
		}
	}

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetNullV1(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtPoint.x.i;
		boolean success = compareAndSwapValue.execute(vtPoint, vtPointOffsetX, ValueTypeInt.class, null, new ValueTypeInt(1));
		assertFalse(success);
		assertEquals(vtPoint.x.i, original);
	}

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetNullV2(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtPoint.x.i;
		boolean success = compareAndSwapValue.execute(vtPoint, vtPointOffsetX, ValueTypeInt.class, new ValueTypeInt(original + 1), null);
		assertFalse(success);
		assertEquals(vtPoint.x.i, original);

		if (isFlatteningEnabled) {
			assertThrows(NullPointerException.class, () -> {
				compareAndSwapValue.execute(vtPoint, vtPointOffsetX, ValueTypeInt.class, vtPoint.x, null);
			});
		} else {
			success = compareAndSwapValue.execute(vtPoint, vtPointOffsetX, ValueTypeInt.class, vtPoint.x, null);
			assertTrue(success);
			assertEquals(vtPoint.x, null);
		}
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetPointXSuccess_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetPointXSuccess(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtPoint.x.i;
		testCompareAndSetPointXSuccess_newVti = new ValueTypeInt(328);
		boolean success = compareAndSwapValue.execute(vtPoint, vtPointOffsetX, ValueTypeInt.class, vtPoint.x, testCompareAndSetPointXSuccess_newVti);
		assertEquals(testCompareAndSetPointXSuccess_newVti.i, 328);
		assertTrue(success);
		assertEquals(vtPoint.x.i, testCompareAndSetPointXSuccess_newVti.i);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetPointYSuccess_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetPointYSuccess(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtPoint.y.i;
		testCompareAndSetPointYSuccess_newVti = new ValueTypeInt(328);
		boolean success = compareAndSwapValue.execute(vtPoint, vtPointOffsetY, ValueTypeInt.class, vtPoint.y, testCompareAndSetPointYSuccess_newVti);
		assertEquals(testCompareAndSetPointYSuccess_newVti.i, 328);
		assertTrue(success);
		assertEquals(vtPoint.y.i, testCompareAndSetPointYSuccess_newVti.i);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetPointXFailure_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetPointXFailure(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtPoint.x.i;
		testCompareAndSetPointXFailure_newVti = new ValueTypeInt(328);
		boolean success = compareAndSwapValue.execute(vtPoint, vtPointOffsetX, ValueTypeInt.class, new ValueTypeInt(original + 1), testCompareAndSetPointXFailure_newVti);
		assertEquals(testCompareAndSetPointXFailure_newVti.i, 328);
		assertFalse(success);
		assertEquals(vtPoint.x.i, original);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetPointYFailure_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetPointYFailure(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtPoint.y.i;
		testCompareAndSetPointYFailure_newVti = new ValueTypeInt(328);
		boolean success = compareAndSwapValue.execute(vtPoint, vtPointOffsetY, ValueTypeInt.class, new ValueTypeInt(original + 1), testCompareAndSetPointYFailure_newVti);
		assertEquals(testCompareAndSetPointYFailure_newVti.i, 328);
		assertFalse(success);
		assertEquals(vtPoint.y.i, original);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetArray0Success_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetArray0Success(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtIntAry[0].i;
		testCompareAndSetArray0Success_newVti = new ValueTypeInt(456);
		boolean success = compareAndSwapValue.execute(vtIntAry, vtIntAryOffset0, ValueTypeInt.class, vtIntAry[0], testCompareAndSetArray0Success_newVti);
		assertEquals(testCompareAndSetArray0Success_newVti.i, 456);
		assertTrue(success);
		assertEquals(vtIntAry[0].i, testCompareAndSetArray0Success_newVti.i);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetArray1Success_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetArray1Success(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtIntAry[1].i;
		testCompareAndSetArray1Success_newVti = new ValueTypeInt(456);
		boolean success = compareAndSwapValue.execute(vtIntAry, vtIntAryOffset1, ValueTypeInt.class, vtIntAry[1], testCompareAndSetArray1Success_newVti);
		assertEquals(testCompareAndSetArray1Success_newVti.i, 456);
		assertTrue(success);
		assertEquals(vtIntAry[1].i, testCompareAndSetArray1Success_newVti.i);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetArray0Failure_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetArray0Failure(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtIntAry[0].i;
		testCompareAndSetArray0Failure_newVti = new ValueTypeInt(328);
		boolean success = compareAndSwapValue.execute(vtIntAry, vtIntAryOffset0, ValueTypeInt.class, new ValueTypeInt(original + 1), testCompareAndSetArray0Failure_newVti);
		assertEquals(testCompareAndSetArray0Failure_newVti.i, 328);
		assertFalse(success);
		assertEquals(vtIntAry[0].i, original);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetArray1Failure_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetArray1Failure(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtIntAry[1].i;
		testCompareAndSetArray1Failure_newVti = new ValueTypeInt(328);
		boolean success = compareAndSwapValue.execute(vtIntAry, vtIntAryOffset1, ValueTypeInt.class, new ValueTypeInt(original + 1), testCompareAndSetArray1Failure_newVti);
		assertEquals(testCompareAndSetArray1Failure_newVti.i, 328);
		assertFalse(success);
		assertEquals(vtIntAry[1].i, original);
	}

	@NullRestricted
	static ValueTypeLongPoint2D testCompareAndSetOnVTWithLongFields_vtLongPoint;
	@NullRestricted
	static ValueTypeLong testCompareAndSetOnVTWithLongFields_newVtl;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetOnVTWithLongFields(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		testCompareAndSetOnVTWithLongFields_vtLongPoint = new ValueTypeLongPoint2D(123, 456);
		assertEquals(testCompareAndSetOnVTWithLongFields_vtLongPoint.x.l, 123);
		assertEquals(testCompareAndSetOnVTWithLongFields_vtLongPoint.y.l, 456);

		long original = testCompareAndSetOnVTWithLongFields_vtLongPoint.x.l;
		testCompareAndSetOnVTWithLongFields_newVtl = new ValueTypeLong(372);
		boolean success = compareAndSwapValue.execute(testCompareAndSetOnVTWithLongFields_vtLongPoint, 
			vtLongPointOffsetX, ValueTypeLong.class, 
			testCompareAndSetOnVTWithLongFields_vtLongPoint.x, 
			testCompareAndSetOnVTWithLongFields_newVtl);
		assertEquals(testCompareAndSetOnVTWithLongFields_newVtl.l, 372);
		assertTrue(success);
		assertEquals(testCompareAndSetOnVTWithLongFields_vtLongPoint.x.l, testCompareAndSetOnVTWithLongFields_newVtl.l);
	}

	@NullRestricted
	static ValueTypeInt testCompareAndSetWrongClz_newVti;

	@Test(dataProvider = "compareAndDoSomethingFuncs")
	static public void testCompareAndSetWrongClz(CompareAndDoSomethingFunction compareAndSwapValue) throws Throwable {
		int original = vtPoint.x.i;
		testCompareAndSetWrongClz_newVti = new ValueTypeInt(328);
		boolean success = compareAndSwapValue.execute(vtPoint, vtPointOffsetX, ValueTypeInt2.class, vtPoint.x, testCompareAndSetWrongClz_newVti);
		assertEquals(testCompareAndSetWrongClz_newVti.i, 328);
		if (isFlatteningEnabled) {
			assertFalse(success);
			assertEquals(vtPoint.x.i, original);
		} else {
			assertTrue(success);
			assertEquals(vtPoint.x.i, testCompareAndSetWrongClz_newVti.i);
		}
	}

	@Test
	static public void testGetAndSetNullObj() throws Throwable {
		assertThrows(NullPointerException.class, () -> {
			myUnsafe.getAndSetValue(null, vtPointOffsetX, ValueTypeInt.class, new ValueTypeInt(1));
		});
	}

	@NullRestricted
	static ValueTypeInt testGetAndSetNullClz_val;

	@Test
	static public void testGetAndSetNullClz() throws Throwable {
		if (isFlatteningEnabled) {
			testGetAndSetNullClz_val = new ValueTypeInt(1);
			assertThrows(NullPointerException.class, () -> {
				myUnsafe.getAndSetValue(vtPoint, vtPointOffsetX, null, testGetAndSetNullClz_val);
			});
		} else {
			int original = vtPoint.x.i;
			Object result = myUnsafe.getAndSetValue(vtPoint, vtPointOffsetX, null, new ValueTypeInt(1));
			assertEquals(((ValueTypeInt)result).i, original);
			assertEquals(vtPoint.x.i, 1);
		}
	}

	@Test
	static public void testGetAndSetNullV() throws Throwable {
		int original = vtPoint.x.i;

		if (isFlatteningEnabled) {
			assertThrows(NullPointerException.class, () -> {
				myUnsafe.getAndSetValue(vtPoint, vtPointOffsetX, ValueTypeInt.class, null);
			});
		} else {
			Object result = myUnsafe.getAndSetValue(vtPoint, vtPointOffsetX, ValueTypeInt.class, null);
			assertEquals(((ValueTypeInt)result).i, original);
			assertEquals(vtPoint.x, null);
		}
	}

	@Test
	static public void testGetAndSetPointX() throws Throwable {
		int original = vtPoint.x.i;
		ValueTypeInt newVti = new ValueTypeInt(328);
		Object result = myUnsafe.getAndSetValue(vtPoint, vtPointOffsetX, ValueTypeInt.class, newVti);
		assertEquals(newVti.i, 328);
		assertEquals(((ValueTypeInt)result).i, original);
		assertEquals(vtPoint.x.i, newVti.i);
	}

	@NullRestricted
	static ValueTypeInt testGetAndSetPointY_newVti;

	@Test
	static public void testGetAndSetPointY() throws Throwable {
		int original = vtPoint.y.i;
		testGetAndSetPointY_newVti = new ValueTypeInt(328);
		Object result = myUnsafe.getAndSetValue(vtPoint, vtPointOffsetY, ValueTypeInt.class, testGetAndSetPointY_newVti);
		assertEquals(testGetAndSetPointY_newVti.i, 328);
		assertEquals(((ValueTypeInt)result).i, original);
		assertEquals(vtPoint.y.i, testGetAndSetPointY_newVti.i);
	}

	@NullRestricted
	static ValueTypeInt testGetAndSetArray0_newVti;

	@Test
	static public void testGetAndSetArray0() throws Throwable {
		int original = vtIntAry[0].i;
		testGetAndSetArray0_newVti = new ValueTypeInt(456);
		Object result = myUnsafe.getAndSetValue(vtIntAry, vtIntAryOffset0, ValueTypeInt.class, testGetAndSetArray0_newVti);
		assertEquals(testGetAndSetArray0_newVti.i, 456);
		assertEquals(((ValueTypeInt)result).i, original);
		assertEquals(vtIntAry[0].i, testGetAndSetArray0_newVti.i);
	}

	@NullRestricted
	static ValueTypeInt testGetAndSetArray1_newVti;

	@Test
	static public void testGetAndSetArray1() throws Throwable {
		int original = vtIntAry[1].i;
		testGetAndSetArray1_newVti = new ValueTypeInt(456);
		Object result = myUnsafe.getAndSetValue(vtIntAry, vtIntAryOffset1, ValueTypeInt.class, testGetAndSetArray1_newVti);
		assertEquals(testGetAndSetArray1_newVti.i, 456);
		assertEquals(((ValueTypeInt)result).i, original);
		assertEquals(vtIntAry[1].i, testGetAndSetArray1_newVti.i);
	}

	@NullRestricted
	static ValueTypeLongPoint2D testGetAndSetOnVTWithLongFields_vtLongPoint;
	@NullRestricted
	static ValueTypeLong testGetAndSetOnVTWithLongFields_newVtl;

	@Test
	static public void testGetAndSetOnVTWithLongFields() throws Throwable {
		testGetAndSetOnVTWithLongFields_vtLongPoint = new ValueTypeLongPoint2D(123, 456);
		assertEquals(testGetAndSetOnVTWithLongFields_vtLongPoint.x.l, 123);
		assertEquals(testGetAndSetOnVTWithLongFields_vtLongPoint.y.l, 456);

		long original = testGetAndSetOnVTWithLongFields_vtLongPoint.x.l;
		testGetAndSetOnVTWithLongFields_newVtl = new ValueTypeLong(372);
		Object result = myUnsafe.getAndSetValue(testGetAndSetOnVTWithLongFields_vtLongPoint, vtLongPointOffsetX, ValueTypeLong.class, testGetAndSetOnVTWithLongFields_newVtl);
		assertEquals(testGetAndSetOnVTWithLongFields_newVtl.l, 372);
		assertEquals(((ValueTypeLong)result).l, original);
		assertEquals(testGetAndSetOnVTWithLongFields_vtLongPoint.x.l, testGetAndSetOnVTWithLongFields_newVtl.l);
	}
}
