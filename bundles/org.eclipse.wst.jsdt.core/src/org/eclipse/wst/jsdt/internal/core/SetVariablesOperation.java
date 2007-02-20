/*******************************************************************************
 * Copyright (c) 2000, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.wst.jsdt.internal.core;

import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.wst.jsdt.core.IClasspathEntry;
import org.eclipse.wst.jsdt.core.IJavaElement;
import org.eclipse.wst.jsdt.core.IJavaModel;
import org.eclipse.wst.jsdt.core.IJavaProject;
import org.eclipse.wst.jsdt.core.JavaModelException;
import org.eclipse.wst.jsdt.internal.core.util.Util;

public class SetVariablesOperation extends ChangeClasspathOperation {
	
	String[] variableNames;
	IPath[] variablePaths;
	boolean updatePreferences;
		
	/*
	 * Creates a new SetVariablesOperation for the given variable values (null path meaning removal), allowing to change multiple variable values at once.
	 */
	public SetVariablesOperation(String[] variableNames, IPath[] variablePaths, boolean updatePreferences) {
		super(new IJavaElement[] {JavaModelManager.getJavaModelManager().getJavaModel()}, !ResourcesPlugin.getWorkspace().isTreeLocked());
		this.variableNames = variableNames;
		this.variablePaths = variablePaths;
		this.updatePreferences = updatePreferences;
	}

	protected void executeOperation() throws JavaModelException {
		if (isCanceled()) 
			return;
		try {
			if (JavaModelManager.CP_RESOLVE_VERBOSE){
				Util.verbose(
					"CPVariable SET  - setting variables\n" + //$NON-NLS-1$
					"	variables: " + org.eclipse.wst.jsdt.internal.compiler.util.Util.toString(this.variableNames) + '\n' +//$NON-NLS-1$
					"	values: " + org.eclipse.wst.jsdt.internal.compiler.util.Util.toString(this.variablePaths)); //$NON-NLS-1$
			}
			
			JavaModelManager manager = JavaModelManager.getJavaModelManager();
			if (variablePutIfInitializingWithSameValue(manager))
				return;
	
			int varLength = this.variableNames.length;
			
			// gather classpath information for updating
			final HashMap affectedProjectClasspaths = new HashMap(5);
			IJavaModel model = getJavaModel();
		
			// filter out unmodified variables
			int discardCount = 0;
			for (int i = 0; i < varLength; i++){
				String variableName = this.variableNames[i];
				IPath oldPath = manager.variableGet(variableName); // if reentering will provide previous session value 
				if (oldPath == JavaModelManager.VARIABLE_INITIALIZATION_IN_PROGRESS) {
					oldPath = null;  //33695 - cannot filter out restored variable, must update affected project to reset cached CP
				}
				if (oldPath != null && oldPath.equals(this.variablePaths[i])){
					this.variableNames[i] = null;
					discardCount++;
				}
			}
			if (discardCount > 0){
				if (discardCount == varLength) return;
				int changedLength = varLength - discardCount;
				String[] changedVariableNames = new String[changedLength];
				IPath[] changedVariablePaths = new IPath[changedLength];
				for (int i = 0, index = 0; i < varLength; i++){
					if (this.variableNames[i] != null){
						changedVariableNames[index] = this.variableNames[i];
						changedVariablePaths[index] = this.variablePaths[i];
						index++;
					}
				}
				this.variableNames = changedVariableNames;
				this.variablePaths = changedVariablePaths;
				varLength = changedLength;
			}
			
			if (isCanceled()) 
				return;
	
			IJavaProject[] projects = model.getJavaProjects();
			nextProject : for (int i = 0, projectLength = projects.length; i < projectLength; i++){
				JavaProject project = (JavaProject) projects[i];
						
				// check to see if any of the modified variables is present on the classpath
				IClasspathEntry[] classpath = project.getRawClasspath();
				for (int j = 0, cpLength = classpath.length; j < cpLength; j++){
					
					IClasspathEntry entry = classpath[j];
					for (int k = 0; k < varLength; k++){
	
						String variableName = this.variableNames[k];						
						if (entry.getEntryKind() ==  IClasspathEntry.CPE_VARIABLE){
	
							if (variableName.equals(entry.getPath().segment(0))){
								affectedProjectClasspaths.put(project, project.getResolvedClasspath());
								continue nextProject;
							}
							IPath sourcePath, sourceRootPath;
							if (((sourcePath = entry.getSourceAttachmentPath()) != null	&& variableName.equals(sourcePath.segment(0)))
								|| ((sourceRootPath = entry.getSourceAttachmentRootPath()) != null	&& variableName.equals(sourceRootPath.segment(0)))) {
	
								affectedProjectClasspaths.put(project, project.getResolvedClasspath());
								continue nextProject;
							}
						}												
					}
				}
			}

			// update variables
			for (int i = 0; i < varLength; i++){
				manager.variablePut(this.variableNames[i], this.variablePaths[i]);
				if (this.updatePreferences)
					manager.variablePreferencesPut(this.variableNames[i], this.variablePaths[i]);
			}
					
			// update affected project classpaths
			if (!affectedProjectClasspaths.isEmpty()) {
				String[] dbgVariableNames = this.variableNames;
				try {
					// propagate classpath change
					Iterator projectsToUpdate = affectedProjectClasspaths.keySet().iterator();
					while (projectsToUpdate.hasNext()) {
	
						if (this.progressMonitor != null && this.progressMonitor.isCanceled()) return;
	
						JavaProject affectedProject = (JavaProject) projectsToUpdate.next();
	
						if (JavaModelManager.CP_RESOLVE_VERBOSE){
							Util.verbose(
								"CPVariable SET  - updating affected project due to setting variables\n" + //$NON-NLS-1$
								"	project: " + affectedProject.getElementName() + '\n' + //$NON-NLS-1$
								"	variables: " + org.eclipse.wst.jsdt.internal.compiler.util.Util.toString(dbgVariableNames)); //$NON-NLS-1$
						}
						// force resolved classpath to be recomputed
						affectedProject.getPerProjectInfo().resetResolvedClasspath();
						
						// if needed, generate delta, update project ref, create markers, ...
						classpathChanged(affectedProject);

						if (this.canChangeResources) {
							// touch project to force a build if needed
							affectedProject.getProject().touch(this.progressMonitor);
						}
					}
				} catch (CoreException e) {
					if (JavaModelManager.CP_RESOLVE_VERBOSE){
						Util.verbose(
							"CPVariable SET  - FAILED DUE TO EXCEPTION\n" + //$NON-NLS-1$
							"	variables: " + org.eclipse.wst.jsdt.internal.compiler.util.Util.toString(dbgVariableNames), //$NON-NLS-1$
							System.err); 
						e.printStackTrace();
					}
					if (e instanceof JavaModelException) {
						throw (JavaModelException)e;
					} else {
						throw new JavaModelException(e);
					}
				}
			}
		} finally {		
			done();
		}
	}

	/*
	 * Optimize startup case where 1 variable is initialized at a time with the same value as on shutdown.
	 */
	private boolean variablePutIfInitializingWithSameValue(JavaModelManager manager) {
		if (this.variableNames.length != 1)
			return false;
		String variableName = this.variableNames[0];
		IPath oldPath = manager.getPreviousSessionVariable(variableName);
		if (oldPath == null)
			return false;
		IPath newPath = this.variablePaths[0];
		if (!oldPath.equals(newPath))
			return false;
		manager.variablePut(variableName, newPath);
		return true;
	}

}
