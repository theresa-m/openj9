package org.openj9.test.java.lang;

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

    public static byte[] generateRecordAttributes(String className, String rcName, String rcType, 
        String rcSignature, String rcAnnotationDescriptor, String rcTypeAnnotationDescriptor)
    {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V14 | V_PREVIEW, ACC_FINAL | ACC_SUPER, className, null, "java/lang/Record", null);

        /* add record component */
        RecordComponentVisitor rcv = cw.visitRecordComponentExperimental(
                ACC_DEPRECATED,
                rcName,
                rcType,
                rcSignature
            );

        /* add annotation */
        if (null != rcAnnotationDescriptor) {
            rcv.visitAnnotationExperimental(rcAnnotationDescriptor, true);
        }

        /* add type annotation */
        if (null != rcTypeAnnotationDescriptor) {
            rcv.visitTypeAnnotationExperimental(
                TypeReference.CLASS_TYPE_PARAMETER,
                null,
                rcTypeAnnotationDescriptor,
                true);
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

		cw.visitEnd();
		return cw.toByteArray();
	}
 }