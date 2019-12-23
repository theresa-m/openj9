package org.openj9.test.utilities;

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

import org.objectweb.asm.*;

 public class RecordClassGenerator implements Opcodes {

    /* Generata a valid record with optional attributes */
    public static byte[] generateRecordAttributes(String className, String rcName, String rcType, 
        String rcSignature, String rcAnnotationDescriptor, String rcTypeAnnotationDescriptor)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Record", null);

        generateValidRecordComponentWithAccessor(cw, className, rcName, rcType, rcSignature, rcAnnotationDescriptor, rcTypeAnnotationDescriptor);
        
        cw.visitEnd();
        return cw.toByteArray();
	}

    public static byte[] generateTwoRecordAttributes(String className) {
        String rc1Name = "x";
        String rc2Name = "y";
        String rcType = "I";

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Record", null);

        generateValidRecordComponentWithAccessor(cw, className, rc1Name, rcType, null, null, null);
        generateValidRecordComponentWithAccessor(cw, className, rc2Name, rcType, null, null, null);

        cw.visitEnd();
        return cw.toByteArray();
    }

    public static byte[] generateNonFinalRecord(String className) {
        return generateRecordWithCustomOpcodes(className, ACC_SUPER);
    }

    public static byte[] generateAbstractRecord(String className) {
        return generateRecordWithCustomOpcodes(className, ACC_FINAL | ACC_SUPER | ACC_ABSTRACT);
    }

    private static byte[] generateRecordWithCustomOpcodes(String className, int access) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, access, className, null, "java/lang/Record", null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static byte[] addTwoSignatureAttributesToRecordComponent(String className) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Record", null);

        generateRecordComponentWithAccessor(cw, className, "x", "I", "I", 2, null, 0, null, 0);

		cw.visitEnd();
		return cw.toByteArray();
    }

    public static byte[] addTwoRuntimeAnnotationsAttributesToRecordComponent(String className, String rcAnnotationDescriptor) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Record", null);

        generateRecordComponentWithAccessor(cw, className, "x", "I", null, 0, rcAnnotationDescriptor, 2, null, 0);
        
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static byte[] addTwoRuntimeTypeAnnotationsAttributesToRecordComponent(String className, String rcTypeAnnotationDescriptor) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Record", null);

        generateRecordComponentWithAccessor(cw, className, "x", "I", null, 0, null, 0, rcTypeAnnotationDescriptor, 2);
        
        cw.visitEnd();
        return cw.toByteArray();
    }

    public static byte[] addInvalidInstanceFieldToRecord(String className) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Record", null);

        cw.visitField(ACC_PUBLIC, "badField", "I", null, null);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void generateValidRecordComponentWithAccessor(ClassWriter cw, String className, String rcName, String rcType, 
        String rcSignature, String rcAnnotationDescriptor, String rcTypeAnnotationDescriptor)
    {
        generateRecordComponentWithAccessor(cw, className, rcName, rcType, rcSignature, 1, rcAnnotationDescriptor, 1, rcTypeAnnotationDescriptor, 1);
    }

    private static void generateRecordComponentWithAccessor(ClassWriter cw, String className, String rcName, String rcType, 
        String rcSignature, int numSignatures, String rcAnnotationDescriptor, int numAnnotations, 
        String rcTypeAnnotationDescriptor, int numTypeAnnotations)
    {
        /* add record component */
        RecordComponentVisitor rcv = cw.visitRecordComponentExperimental(
                ACC_DEPRECATED,
                rcName,
                rcType,
                rcSignature
            );

        for (int i = 1; i < numSignatures; i++) {
            // TODO figure something out for this
            rcv.visitAttributeExperimental(null);
        }

        /* add annotation */
        if (null != rcAnnotationDescriptor) {
            for (int i = 0; i < numAnnotations; i++) {
                rcv.visitAnnotationExperimental(rcAnnotationDescriptor, true);
            }
        }

        /* add type annotation */
        if (null != rcTypeAnnotationDescriptor) {
            for (int i = 0; i < numTypeAnnotations; i++) {
                rcv.visitTypeAnnotationExperimental(
                    TypeReference.CLASS_TYPE_PARAMETER,
                    null,
                    rcTypeAnnotationDescriptor,
                    true);
            }
        }

        rcv.visitEndExperimental();

        /* add accessor method for record component */
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, rcName, "()" + rcType, null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, className, rcName, rcType);
        mv.visitInsn(IRETURN);
		mv.visitMaxs(1, 1);
		mv.visitEnd();
    }
 }