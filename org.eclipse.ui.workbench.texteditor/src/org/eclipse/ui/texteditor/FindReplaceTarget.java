/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.texteditor;

import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;

import org.eclipse.jface.text.IFindReplaceTarget;
import org.eclipse.jface.text.IFindReplaceTargetExtension;
import org.eclipse.jface.text.IRegion;

/**
 * Internal find/replace target wrapping the editor's source viewer.
 * @since 2.1
 */
class FindReplaceTarget implements IFindReplaceTarget, IFindReplaceTargetExtension, IFindReplaceTargetExtension2 {
	
	private AbstractTextEditor fEditor;
	private IFindReplaceTarget fTarget;

	public FindReplaceTarget(AbstractTextEditor editor, IFindReplaceTarget target) {
		fEditor= editor;
		fTarget= target;
	}
	
	private IFindReplaceTarget getTarget() {
		return fTarget;
	}
	
	private IFindReplaceTargetExtension getExtension() {
		if (fTarget instanceof IFindReplaceTargetExtension)
			return (IFindReplaceTargetExtension) fTarget;
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTarget#canPerformFind()
	 */
	public boolean canPerformFind() {
		if (getTarget() != null)
			return getTarget().canPerformFind();
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTarget#findAndSelect(int, java.lang.String, boolean, boolean, boolean)
	 */
	public int findAndSelect(int offset, String findString, boolean searchForward, boolean caseSensitive, boolean wholeWord) {
		if (getTarget() != null)
			return getTarget().findAndSelect(offset, findString, searchForward, caseSensitive, wholeWord);
		return -1;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTarget#getSelection()
	 */
	public Point getSelection() {
		if (getTarget() != null)
			return getTarget().getSelection();
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTarget#getSelectionText()
	 */
	public String getSelectionText() {
		if (getTarget() != null)
			return getTarget().getSelectionText();
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTarget#isEditable()
	 */
	public boolean isEditable() {
		if (getTarget() != null) {
			if (getTarget().isEditable())
				return true;
			return fEditor.isEditorInputModifiable();
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTarget#replaceSelection(java.lang.String)
	 */
	public void replaceSelection(String text) {
		if (getTarget() != null)
			getTarget().replaceSelection(text);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#beginSession()
	 */
	public void beginSession() {
		if (getExtension() != null)
			getExtension().beginSession();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#endSession()
	 */
	public void endSession() {
		if (getExtension() != null)
			getExtension().endSession();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#getScope()
	 */
	public IRegion getScope() {
		if (getExtension() != null)
			return getExtension().getScope();
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#setScope(org.eclipse.jface.text.IRegion)
	 */
	public void setScope(IRegion scope) {
		if (getExtension() != null)
			getExtension().setScope(scope);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#getLineSelection()
	 */
	public Point getLineSelection() {
		if (getExtension() != null)
			return getExtension().getLineSelection();
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#setSelection(int, int)
	 */
	public void setSelection(int offset, int length) {
		if (getExtension() != null)
			getExtension().setSelection(offset, length);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#setScopeHighlightColor(org.eclipse.swt.graphics.Color)
	 */
	public void setScopeHighlightColor(Color color) {
		if (getExtension() != null)
			getExtension().setScopeHighlightColor(color);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.text.IFindReplaceTargetExtension#setReplaceAllMode(boolean)
	 */
	public void setReplaceAllMode(boolean replaceAll) {
		if (getExtension() != null)
			getExtension().setReplaceAllMode(replaceAll);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.texteditor.IFindReplaceTargetExtension2#validateTargetState()
	 */
	public boolean validateTargetState() {
		return fEditor.validateEditorInputState();
	}
};
