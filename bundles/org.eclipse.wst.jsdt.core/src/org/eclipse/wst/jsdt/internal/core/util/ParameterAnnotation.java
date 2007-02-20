/*******************************************************************************
 * Copyright (c) 2004, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.jsdt.internal.core.util;

import org.eclipse.wst.jsdt.core.util.ClassFormatException;
import org.eclipse.wst.jsdt.core.util.IAnnotation;
import org.eclipse.wst.jsdt.core.util.IConstantPool;
import org.eclipse.wst.jsdt.core.util.IParameterAnnotation;

/**
 * Default implementation of IParameterAnnotation
 */
public class ParameterAnnotation extends ClassFileStruct implements IParameterAnnotation {

	private static final IAnnotation[] NO_ENTRIES = new IAnnotation[0];
	
	private int annotationsNumber;
	private IAnnotation[] annotations;
	private int readOffset;
	
	/**
	 * Constructor for Annotation.
	 * 
	 * @param classFileBytes
	 * @param constantPool
	 * @param offset
	 * @throws ClassFormatException
	 */
	public ParameterAnnotation(
			byte[] classFileBytes,
			IConstantPool constantPool,
			int offset) throws ClassFormatException {
		
		final int length = u2At(classFileBytes, 0, offset);
		this.readOffset = 2;
		this.annotationsNumber = length;
		if (length != 0) {
			this.annotations = new IAnnotation[length];
			for (int i = 0; i < length; i++) {
				Annotation annotation = new Annotation(classFileBytes, constantPool, offset + readOffset);
				this.annotations[i] = annotation;
				this.readOffset += annotation.sizeInBytes();
			}
		} else {
			this.annotations = NO_ENTRIES;
		}
	}
	
	int sizeInBytes() {
		return this.readOffset;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.wst.jsdt.core.util.IParameterAnnotation#getAnnotations()
	 */
	public IAnnotation[] getAnnotations() {
		return this.annotations;
	}
	/* (non-Javadoc)
	 * @see org.eclipse.wst.jsdt.core.util.IParameterAnnotation#getAnnotationsNumber()
	 */
	public int getAnnotationsNumber() {
		return this.annotationsNumber;
	}
}
