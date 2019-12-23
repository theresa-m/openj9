package org.openj9.test.records;

/*******************************************************************************
 * Copyright (c) 2019, 2019 IBM Corp. and others
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
 * [2] http://openjdk.java.net/legal/assembly-exception.html
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0 OR GPL-2.0 WITH Classpath-exception-2.0 OR LicenseRef-GPL-2.0 WITH Assembly-exception
 *******************************************************************************/

import org.testng.annotations.Test;

import org.openj9.test.utilities.RecordClassGenerator;
import org.openj9.test.utilities.CustomClassLoader;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 
 * Test class formatting for records classes introduced in JEP 359
 * TODO include test summary
 */
@Test(groups = { "level.sanity" })
public class RecordClassTests {
      /**
        TODO verify these with hotspot and then match it
        - no more than one record attribute per class
        - record classes must be final
        - record classes  cannot be abstract
        - There may be at most one of each of the 3 attribute types in a record_component_info structure
        - cannot declare any instance fields that are not components
       */

    String name = "TestBadRecords";

    @Retention(RetentionPolicy.RUNTIME)
    @Inherited()
    public @interface TestAnnotation {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE_USE)
    public @interface TestTypeAnnotation {}

    /* jvmspec 4.7.30: There may be at most one Record attribute in the attributes table of a ClassFile structure. */
    @Test(expectedExceptions = java.lang.ClassFormatError.class)
    public void test_oneRecordAttributePerClass() {
        CustomClassLoader classloader = new CustomClassLoader();
        byte[] bytes = RecordClassGenerator.generateTwoRecordAttributes(name);
        Class<?> clazz = classloader.getClass(name, bytes);
    }

    /* record classes are implicitely final */
    @Test(expectedExceptions = java.lang.ClassFormatError.class)
    public void test_recordMustBeFinal() {
        CustomClassLoader classloader = new CustomClassLoader();
        byte[] bytes = RecordClassGenerator.generateNonFinalRecord(name);
        Class<?> clazz = classloader.getClass(name, bytes);
    }

    /* record classes cannot be abstract */
    @Test(expectedExceptions = java.lang.ClassFormatError.class)
    public void test_recordCannotBeAbstract() {
        CustomClassLoader classloader = new CustomClassLoader();
        byte[] bytes = RecordClassGenerator.generateAbstractRecord(name);
        Class<?> clazz = classloader.getClass(name, bytes);
    }

    /* 4.7.9: There may be at most one Signature attribute in the attributes table of a ... record_component_info structure. */
    @Test(expectedExceptions = java.lang.ClassFormatError.class)
    public void test_atMostOneSignatureAttributeTypeInRecordComponent() {
        CustomClassLoader classloader = new CustomClassLoader();
        byte[] bytes = RecordClassGenerator.addTwoSignatureAttributesToRecordComponent(name);
        Class<?> clazz = classloader.getClass(name, bytes);
    }

    /* 4.7.16: There may be at most one RuntimeVisibleAnnotations attribute in the attributes table of a ...record_component_info structure. */
    @Test(expectedExceptions = java.lang.ClassFormatError.class)
    public void test_atMostOneRuntimeAnnotationsAttributeTypeInRecordComponent() {
        CustomClassLoader classloader = new CustomClassLoader();
        byte[] bytes = RecordClassGenerator.addTwoRuntimeAnnotationsAttributesToRecordComponent(name,
         TestAnnotation.class.descriptorString());
        Class<?> clazz = classloader.getClass(name, bytes);
    }

    /* 4.7.20: There may be at most one RuntimeVisibleTypeAnnotations attribute in the attributes table of a ... record_component_info structure */
    @Test(expectedExceptions = java.lang.ClassFormatError.class)
    public void test_atMostOneRuntimeTypeAnnotationsAttributeTypeInRecordComponent() {
        CustomClassLoader classloader = new CustomClassLoader();
        byte[] bytes = RecordClassGenerator.addTwoRuntimeTypeAnnotationsAttributesToRecordComponent(name,
         TestTypeAnnotation.class.descriptorString());
        Class<?> clazz = classloader.getClass(name, bytes);
    }

    /* records cannot declare instance fields that are not record components */
    @Test(expectedExceptions = java.lang.ClassFormatError.class)
    public void test_instanceFieldMustBeRecordComponent() {
        CustomClassLoader classloader = new CustomClassLoader();
        byte[] bytes = RecordClassGenerator.addInvalidInstanceFieldToRecord(name);
        Class<?> clazz = classloader.getClass(name, bytes);
    }
}