/*******************************************************************************
 * Copyright (c) 2008 Ovidio Mallo and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Ovidio Mallo - initial API and implementation (bug 235195)
 *     Ovidio Mallo - bug 237856
 ******************************************************************************/

package org.eclipse.jface.tests.databinding.wizard;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.ValidationStatusProvider;
import org.eclipse.core.databinding.observable.Diffs;
import org.eclipse.core.databinding.observable.ObservableTracker;
import org.eclipse.core.databinding.observable.Observables;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.core.databinding.observable.value.AbstractObservableValue;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.core.databinding.observable.value.WritableValue;
import org.eclipse.core.databinding.validation.ValidationStatus;
import org.eclipse.core.internal.commands.util.Util;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.databinding.wizard.WizardPageSupport;
import org.eclipse.jface.tests.databinding.AbstractSWTTestCase;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.widgets.Composite;

/**
 * @since 1.2
 */
public class WizardPageSupportTest extends AbstractSWTTestCase {

	/**
	 * Bug 235195.
	 */
	public void testPageComplete() {
		IWizardPage page = new WizardPage("Page") {
			public void createControl(Composite parent) {
				setControl(parent);

				IObservableValue validation = new WritableValue(
						ValidationStatus.ok(), IStatus.class);

				DataBindingContext dbc = new DataBindingContext();
				ValidationProvider validationProvider = new ValidationProvider(
						validation);
				dbc.addValidationStatusProvider(validationProvider);

				WizardPageSupport.create(this, dbc);

				assertTrue(isPageComplete());

				validation.setValue(ValidationStatus.info("INFO"));
				assertTrue(isPageComplete());

				validation.setValue(ValidationStatus.warning("WARNING"));
				assertTrue(isPageComplete());

				validation.setValue(ValidationStatus.error("ERROR"));
				assertFalse(isPageComplete());

				validation.setValue(ValidationStatus.cancel("CANCEL"));
				assertFalse(isPageComplete());
			}
		};

		loadWizardPage(page);
	}

	public void testPageCompleteOnValidationStaleness() {
		IWizardPage page = new WizardPage("Page") {
			public void createControl(Composite parent) {
				setControl(parent);

				ValidationObservable validation = new ValidationObservable();

				DataBindingContext dbc = new DataBindingContext();
				dbc.addValidationStatusProvider(new ValidationProvider(
						validation));

				WizardPageSupport.create(this, dbc);

				assertTrue(isPageComplete());

				validation.setStale(true);
				assertFalse(isPageComplete());

				validation.setStale(false);
				assertTrue(isPageComplete());
			}
		};

		loadWizardPage(page);
	}

	private void loadWizardPage(IWizardPage page) {
		Wizard wizard = new Wizard() {
			public boolean performFinish() {
				return true;
			}
		};
		wizard.addPage(page);

		WizardDialog dialog = new WizardDialog(getShell(), wizard);
		dialog.create();
	}

	private static class ValidationObservable extends AbstractObservableValue {

		private Object value = ValidationStatus.ok();

		private boolean stale = false;

		public ValidationObservable() {
			super(Realm.getDefault());
		}

		protected Object doGetValue() {
			return value;
		}

		protected void doSetValue(Object value) {
			Object oldValue = this.value;
			this.value = value;
			if (!Util.equals(oldValue, value)) {
				fireValueChange(Diffs.createValueDiff(oldValue, value));
			}
		}

		public boolean isStale() {
			ObservableTracker.getterCalled(this);
			return stale;
		}

		public void setStale(boolean stale) {
			if (this.stale != stale) {
				this.stale = stale;
				if (stale) {
					fireStale();
				} else {
					fireValueChange(Diffs.createValueDiff(value, value));
				}
			}
		}

		public Object getValueType() {
			return IStatus.class;
		}
	}

	private static class ValidationProvider extends ValidationStatusProvider {

		private final IObservableValue validation;

		public ValidationProvider(IObservableValue validation) {
			this.validation = validation;
		}

		public IObservableValue getValidationStatus() {
			return validation;
		}

		public IObservableList getTargets() {
			return Observables.emptyObservableList();
		}

		public IObservableList getModels() {
			return Observables.emptyObservableList();
		}
	}
}
