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

package org.eclipse.ui.internal.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.commands.IAction;
import org.eclipse.ui.commands.ICategory;
import org.eclipse.ui.commands.ICommand;
import org.eclipse.ui.commands.ICommandManager;
import org.eclipse.ui.commands.ICommandManagerEvent;
import org.eclipse.ui.commands.ICommandManagerListener;
import org.eclipse.ui.commands.IContextBinding;
import org.eclipse.ui.commands.IKeyConfiguration;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.eclipse.ui.internal.commands.registry.CategoryDefinition;
import org.eclipse.ui.internal.commands.registry.CommandDefinition;
import org.eclipse.ui.internal.commands.registry.IActiveKeyConfigurationDefinition;
import org.eclipse.ui.internal.commands.registry.ICategoryDefinition;
import org.eclipse.ui.internal.commands.registry.ICommandDefinition;
import org.eclipse.ui.internal.commands.registry.ICommandRegistry;
import org.eclipse.ui.internal.commands.registry.ICommandRegistryEvent;
import org.eclipse.ui.internal.commands.registry.ICommandRegistryListener;
import org.eclipse.ui.internal.commands.registry.IKeyConfigurationDefinition;
import org.eclipse.ui.internal.commands.registry.KeyConfigurationDefinition;
import org.eclipse.ui.internal.commands.registry.PluginCommandRegistry;
import org.eclipse.ui.internal.commands.registry.PreferenceCommandRegistry;
import org.eclipse.ui.internal.util.Util;

public final class CommandManager implements ICommandManager {

	private static CommandManager instance;

	public static CommandManager getInstance() {
		if (instance == null)
			instance = new CommandManager();
			
		return instance;
	}

	private SortedMap actionsById = new TreeMap();	
	private List activeContextIds = new ArrayList();
	private SortedSet activeContextIdsAsSet = new TreeSet();
	private String activeKeyConfigurationId = Util.ZERO_LENGTH_STRING;
	private String activeLocale = Util.ZERO_LENGTH_STRING;
	private String activePlatform = Util.ZERO_LENGTH_STRING;	
	private SortedMap categoriesById = new TreeMap();	
	private SortedMap categoryDefinitionsById = new TreeMap();
	private SortedMap commandDefinitionsById = new TreeMap();
	private ICommandManagerEvent commandManagerEvent;
	private List commandManagerListeners;
	private SortedMap commandsById = new TreeMap();	
	private SortedMap contextBindingsByCommandId = new TreeMap();	
	private SortedSet definedCategoryIds = new TreeSet();
	private SortedSet definedCommandIds = new TreeSet();
	private SortedSet definedKeyConfigurationIds = new TreeSet();	
	private SortedMap imageBindingsByCommandId = new TreeMap();
	private SortedMap keyBindingsByCommandId = new TreeMap();
	private SortedMap keyConfigurationDefinitionsById = new TreeMap();
	private SortedMap keyConfigurationsById = new TreeMap();	
	private PluginCommandRegistry pluginCommandRegistry;
	private PreferenceCommandRegistry preferenceCommandRegistry;

	private CommandManager() {
		if (pluginCommandRegistry == null)
			pluginCommandRegistry = new PluginCommandRegistry(Platform.getPluginRegistry());
			
		loadPluginCommandRegistry();		

		pluginCommandRegistry.addCommandRegistryListener(new ICommandRegistryListener() {
			public void commandRegistryChanged(ICommandRegistryEvent commandRegistryEvent) {
				readRegistry();
			}
		});

		if (preferenceCommandRegistry == null)
			preferenceCommandRegistry = new PreferenceCommandRegistry(WorkbenchPlugin.getDefault().getPreferenceStore());	

		loadPreferenceCommandRegistry();

		preferenceCommandRegistry.addCommandRegistryListener(new ICommandRegistryListener() {
			public void commandRegistryChanged(ICommandRegistryEvent commandRegistryEvent) {
				readRegistry();
			}
		});
		
		readRegistry();
	}

	public void addCommandManagerListener(ICommandManagerListener commandManagerListener) {
		if (commandManagerListener == null)
			throw new NullPointerException();
			
		if (commandManagerListeners == null)
			commandManagerListeners = new ArrayList();
		
		if (!commandManagerListeners.contains(commandManagerListener))
			commandManagerListeners.add(commandManagerListener);
	}

	public SortedMap getActionsById() {
		return Collections.unmodifiableSortedMap(actionsById);
	}

	public List getActiveContextIds() {
		return Collections.unmodifiableList(activeContextIds);
	}

	public String getActiveKeyConfigurationId() {		
		return activeKeyConfigurationId;
	}
	
	public String getActiveLocale() {
		return activeLocale;
	}
	
	public String getActivePlatform() {
		return activePlatform;
	}

	public ICategory getCategory(String categoryId) {
		if (categoryId == null)
			throw new NullPointerException();
			
		Category category = (Category) categoriesById.get(categoryId);
		
		if (category == null) {
			category = new Category(categoryId);
			updateCategory(category);
			categoriesById.put(categoryId, category);
		}
		
		return category;
	}

	public ICommand getCommand(String commandId) {
		if (commandId == null)
			throw new NullPointerException();
			
		Command command = (Command) commandsById.get(commandId);
		
		if (command == null) {
			command = new Command(commandId);
			updateCommand(command);
			commandsById.put(commandId, command);
		}
		
		return command;
	}

	public SortedSet getDefinedCategoryIds() {
		return Collections.unmodifiableSortedSet(definedCategoryIds);
	}
	
	public SortedSet getDefinedCommandIds() {
		return Collections.unmodifiableSortedSet(definedCommandIds);
	}
	
	public SortedSet getDefinedKeyConfigurationIds() {
		return Collections.unmodifiableSortedSet(definedKeyConfigurationIds);
	}

	public IKeyConfiguration getKeyConfiguration(String keyConfigurationId) {
		if (keyConfigurationId == null)
			throw new NullPointerException();
			
		KeyConfiguration keyConfiguration = (KeyConfiguration) keyConfigurationsById.get(keyConfigurationId);
		
		if (keyConfiguration == null) {
			keyConfiguration = new KeyConfiguration(keyConfigurationId);
			updateKeyConfiguration(keyConfiguration);
			keyConfigurationsById.put(keyConfigurationId, keyConfiguration);
		}
		
		return keyConfiguration;
	}
	
	public void removeCommandManagerListener(ICommandManagerListener commandManagerListener) {
		if (commandManagerListener == null)
			throw new NullPointerException();
			
		if (commandManagerListeners != null) {
			commandManagerListeners.remove(commandManagerListener);
			
			if (commandManagerListeners.isEmpty())
				commandManagerListeners = null;
		}
	}

	public void setActionsById(SortedMap actionsById)
		throws IllegalArgumentException {
		actionsById = Util.safeCopy(actionsById, String.class, IAction.class);	
	
		if (!Util.equals(actionsById, this.actionsById)) {	
			this.actionsById = actionsById;	
			fireCommandManagerChanged();
		}
	}

	public void setActiveContextIds(List activeContextIds) {
		activeContextIds = Util.safeCopy(activeContextIds, String.class);
		boolean commandManagerChanged = false;
		SortedSet updatedCommandIds = null;

		if (!this.activeContextIds.equals(activeContextIds)) {
			this.activeContextIds = activeContextIds;
			activeContextIdsAsSet = new TreeSet(this.activeContextIds);			
			commandManagerChanged = true;			
			// TODO this could change key bindings			
			updatedCommandIds = updateCommands(this.definedCommandIds);	
		}
		
		if (commandManagerChanged)
			fireCommandManagerChanged();

		if (updatedCommandIds != null)
			notifyCommands(updatedCommandIds);
	}

	public void setActiveLocale(String locale) {		
		// TODO this could change image bindings or key bindings		
	}
	
	public void setActivePlatform(String platform) {
		// TODO this could change image bindings or key bindings
	}

	private void fireCommandManagerChanged() {
		if (commandManagerListeners != null) {
			for (int i = 0; i < commandManagerListeners.size(); i++) {
				if (commandManagerEvent == null)
					commandManagerEvent = new CommandManagerEvent(this);
							
				((ICommandManagerListener) commandManagerListeners.get(i)).commandManagerChanged(commandManagerEvent);
			}				
		}		
	}

	private ICommandRegistry getPluginCommandRegistry() {
		return pluginCommandRegistry;
	}

	private ICommandRegistry getPreferenceCommandRegistry() {
		return preferenceCommandRegistry;
	}

	private boolean inContext(List contextBindings) {
		Iterator iterator = contextBindings.iterator();
		
		while (iterator.hasNext()) {
			IContextBinding contextBinding = (IContextBinding) iterator.next();
			
			if (activeContextIds.contains(contextBinding.getContextId()))
				return true;			
		}
		
		return false;
	}

	private void loadPluginCommandRegistry() {
		try {
			pluginCommandRegistry.load();
		} catch (IOException eIO) {
			eIO.printStackTrace();
		}
	}
	
	private void loadPreferenceCommandRegistry() {
		try {
			preferenceCommandRegistry.load();
		} catch (IOException eIO) {
			eIO.printStackTrace();
		}		
	}

	private void notifyCategories(SortedSet categoryIds) {	
		Iterator iterator = categoryIds.iterator();
		
		while (iterator.hasNext()) {
			String categoryId = (String) iterator.next();					
			Category category = (Category) categoriesById.get(categoryId);
			
			if (category != null)
				category.fireCategoryChanged();
		}
	}

	private void notifyCommands(SortedSet commandIds) {	
		Iterator iterator = commandIds.iterator();
		
		while (iterator.hasNext()) {
			String commandId = (String) iterator.next();					
			Command command = (Command) commandsById.get(commandId);
			
			if (command != null)
				command.fireCommandChanged();
		}
	}

	private void notifyKeyConfigurations(SortedSet keyConfigurationIds) {	
		Iterator iterator = keyConfigurationIds.iterator();
		
		while (iterator.hasNext()) {
			String keyConfigurationId = (String) iterator.next();					
			KeyConfiguration keyConfiguration = (KeyConfiguration) keyConfigurationsById.get(keyConfigurationId);
			
			if (keyConfiguration != null)
				keyConfiguration.fireKeyConfigurationChanged();
		}
	}

	private void readRegistry() {			
		List activeKeyConfigurationDefinitions = new ArrayList();
		activeKeyConfigurationDefinitions.addAll(pluginCommandRegistry.getActiveKeyConfigurationDefinitions());
		activeKeyConfigurationDefinitions.addAll(preferenceCommandRegistry.getActiveKeyConfigurationDefinitions());
		String activeKeyConfigurationId = Util.ZERO_LENGTH_STRING;
		
		if (activeKeyConfigurationDefinitions.size() >= 1) {
			IActiveKeyConfigurationDefinition activeKeyConfigurationDefinition = (IActiveKeyConfigurationDefinition) activeKeyConfigurationDefinitions.get(activeKeyConfigurationDefinitions.size() - 1);
			activeKeyConfigurationId = activeKeyConfigurationDefinition.getKeyConfigurationId();
		}
		
		List categoryDefinitions = new ArrayList();
		categoryDefinitions.addAll(pluginCommandRegistry.getCategoryDefinitions());
		categoryDefinitions.addAll(preferenceCommandRegistry.getCategoryDefinitions());		
		SortedMap categoryDefinitionsById = CategoryDefinition.sortedMapById(categoryDefinitions);
		SortedSet definedCategoryIds = new TreeSet(categoryDefinitionsById.keySet());	
		List commandDefinitions = new ArrayList();
		commandDefinitions.addAll(pluginCommandRegistry.getCommandDefinitions());
		commandDefinitions.addAll(preferenceCommandRegistry.getCommandDefinitions());
		SortedMap commandDefinitionsById = CommandDefinition.sortedMapById(commandDefinitions);
		SortedSet definedCommandIds = new TreeSet(commandDefinitionsById.keySet());		
		List keyConfigurationDefinitions = new ArrayList();
		keyConfigurationDefinitions.addAll(pluginCommandRegistry.getKeyConfigurationDefinitions());
		keyConfigurationDefinitions.addAll(preferenceCommandRegistry.getKeyConfigurationDefinitions());
		SortedMap keyConfigurationDefinitionsById = KeyConfigurationDefinition.sortedMapById(keyConfigurationDefinitions);
		SortedSet definedKeyConfigurationIds = new TreeSet(keyConfigurationDefinitionsById.keySet());	

		List pluginContextBindingDefinitions = pluginCommandRegistry.getContextBindingDefinitions();
		List preferenceContextBindingDefinitions = preferenceCommandRegistry.getContextBindingDefinitions();
		List pluginImageBindingDefinitions = pluginCommandRegistry.getImageBindingDefinitions();
		List preferenceImageBindingDefinitions = preferenceCommandRegistry.getImageBindingDefinitions();
		List pluginKeyBindingDefinitions = pluginCommandRegistry.getKeyBindingDefinitions();
		List preferenceKeyBindingDefinitions = preferenceCommandRegistry.getKeyBindingDefinitions();
		
		boolean commandManagerChanged = false;
		SortedSet updatedCategoryIds = null;
		SortedSet updatedCommandIds = null;
		SortedSet updatedKeyConfigurationIds = null;

		if (!this.activeKeyConfigurationId.equals(activeKeyConfigurationId)) {
			this.activeKeyConfigurationId = activeKeyConfigurationId;
			commandManagerChanged = true;
		}
		
		if (!this.definedCategoryIds.equals(definedCategoryIds)) {
			this.definedCategoryIds = definedCategoryIds;
			commandManagerChanged = true;	
		}

		if (!this.categoryDefinitionsById.equals(categoryDefinitionsById)) {
			this.categoryDefinitionsById = categoryDefinitionsById;	
			updatedCategoryIds = updateCategories(this.definedCategoryIds);	
		}

		if (!this.definedCommandIds.equals(definedCommandIds)) {
			this.definedCommandIds = definedCommandIds;
			commandManagerChanged = true;	
		}

		if (!this.commandDefinitionsById.equals(commandDefinitionsById)) {
			this.commandDefinitionsById = commandDefinitionsById;	
			updatedCommandIds = updateCommands(this.definedCommandIds);	
		}

		if (!this.definedKeyConfigurationIds.equals(definedKeyConfigurationIds)) {
			this.definedKeyConfigurationIds = definedKeyConfigurationIds;
			commandManagerChanged = true;	
		}

		if (!this.keyConfigurationDefinitionsById.equals(keyConfigurationDefinitionsById)) {
			this.keyConfigurationDefinitionsById = keyConfigurationDefinitionsById;	
			updatedKeyConfigurationIds = updateKeyConfigurations(this.definedKeyConfigurationIds);	
		}

		if (commandManagerChanged)
			fireCommandManagerChanged();

		if (updatedCategoryIds != null)
			notifyCategories(updatedCategoryIds);
		
		if (updatedCommandIds != null)
			notifyCommands(updatedCommandIds);

		if (updatedKeyConfigurationIds != null)
			notifyKeyConfigurations(updatedKeyConfigurationIds);
	}

	private SortedSet updateCategories(SortedSet categoryIds) {
		SortedSet updatedIds = new TreeSet();
		Iterator iterator = categoryIds.iterator();
		
		while (iterator.hasNext()) {
			String categoryId = (String) iterator.next();					
			Category category = (Category) categoriesById.get(categoryId);
			
			if (category != null && updateCategory(category))
				updatedIds.add(categoryId);			
		}
		
		return updatedIds;
	}

	private boolean updateCategory(Category category) {
		boolean updated = false;
		ICategoryDefinition categoryDefinition = (ICategoryDefinition) categoryDefinitionsById.get(category.getId());
		updated |= category.setDefined(categoryDefinition != null);
		updated |= category.setDescription(categoryDefinition != null ? categoryDefinition.getDescription() : null);
		updated |= category.setName(categoryDefinition != null ? categoryDefinition.getName() : Util.ZERO_LENGTH_STRING);
		return updated;		
	}

	private boolean updateCommand(Command command) {
		boolean updated = false;
		List contextBindings = (List) contextBindingsByCommandId.get(command.getId());
		updated |= command.setActive(inContext(contextBindings));
		ICommandDefinition commandDefinition = (ICommandDefinition) commandDefinitionsById.get(command.getId());
		updated |= command.setCategoryId(commandDefinition != null ? commandDefinition.getCategoryId() : null);
		updated |= command.setContextBindings(contextBindings != null ? contextBindings : Collections.EMPTY_LIST);
		updated |= command.setDefined(commandDefinition != null);
		updated |= command.setDescription(commandDefinition != null ? commandDefinition.getDescription() : null);
		updated |= command.setHelpId(commandDefinition != null ? commandDefinition.getHelpId() : null);
		List imageBindings = (List) imageBindingsByCommandId.get(command.getId());
		updated |= command.setImageBindings(imageBindings != null ? imageBindings : Collections.EMPTY_LIST);
		List keyBindings = (List) keyBindingsByCommandId.get(command.getId());
		updated |= command.setKeyBindings(keyBindings != null ? keyBindings : Collections.EMPTY_LIST);
		updated |= command.setName(commandDefinition != null ? commandDefinition.getName() : Util.ZERO_LENGTH_STRING);
		return updated;
	}

	private SortedSet updateCommands(SortedSet commandIds) {
		SortedSet updatedIds = new TreeSet();
		Iterator iterator = commandIds.iterator();
		
		while (iterator.hasNext()) {
			String commandId = (String) iterator.next();					
			Command command = (Command) commandsById.get(commandId);
			
			if (command != null && updateCommand(command))
				updatedIds.add(commandId);			
		}
		
		return updatedIds;
	}
	
	private boolean updateKeyConfiguration(KeyConfiguration keyConfiguration) {
		boolean updated = false;
		updated |= keyConfiguration.setActive(keyConfiguration.getId().equals(activeKeyConfigurationId));
		IKeyConfigurationDefinition keyConfigurationDefinition = (IKeyConfigurationDefinition) keyConfigurationDefinitionsById.get(keyConfiguration.getId());
		updated |= keyConfiguration.setDefined(keyConfigurationDefinition != null);
		updated |= keyConfiguration.setDescription(keyConfigurationDefinition != null ? keyConfigurationDefinition.getDescription() : null);
		updated |= keyConfiguration.setName(keyConfigurationDefinition != null ? keyConfigurationDefinition.getName() : Util.ZERO_LENGTH_STRING);
		updated |= keyConfiguration.setParentId(keyConfigurationDefinition != null ? keyConfigurationDefinition.getParentId() : null);
		return updated;
	}

	private SortedSet updateKeyConfigurations(SortedSet keyConfigurationIds) {
		SortedSet updatedIds = new TreeSet();
		Iterator iterator = keyConfigurationIds.iterator();
		
		while (iterator.hasNext()) {
			String keyConfigurationId = (String) iterator.next();					
			KeyConfiguration keyConfiguration = (KeyConfiguration) keyConfigurationsById.get(keyConfigurationId);
			
			if (keyConfiguration != null && updateKeyConfiguration(keyConfiguration))	
				updatedIds.add(keyConfigurationId);				
		}	
		
		return updatedIds;		
	}		
}
