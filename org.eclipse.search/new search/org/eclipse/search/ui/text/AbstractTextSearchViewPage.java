package org.eclipse.search.ui.text;

import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.ISafeRunnable;
import org.eclipse.core.runtime.Platform;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IContributionManager;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;

import org.eclipse.jface.text.Position;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;

import org.eclipse.search.ui.IContextMenuConstants;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.ISearchResultListener;
import org.eclipse.search.ui.ISearchResultPage;
import org.eclipse.search.ui.ISearchResultViewPart;
import org.eclipse.search.ui.SearchResultEvent;

import org.eclipse.search.internal.ui.CopyToClipboardAction;
import org.eclipse.search.internal.ui.SearchPlugin;
import org.eclipse.search.internal.ui.SearchPluginImages;

import org.eclipse.search2.internal.ui.InternalSearchUI;
import org.eclipse.search2.internal.ui.SearchMessages;
import org.eclipse.search2.internal.ui.basic.views.INavigate;
import org.eclipse.search2.internal.ui.basic.views.RemoveAllResultsAction;
import org.eclipse.search2.internal.ui.basic.views.RemoveMatchAction;
import org.eclipse.search2.internal.ui.basic.views.SearchResultsTableViewer;
import org.eclipse.search2.internal.ui.basic.views.SearchResultsTreeViewer;
import org.eclipse.search2.internal.ui.basic.views.SetLayoutAction;
import org.eclipse.search2.internal.ui.basic.views.ShowNextResultAction;
import org.eclipse.search2.internal.ui.basic.views.ShowPreviousResultAction;
import org.eclipse.search2.internal.ui.text.AnnotationManager;

public abstract class AbstractTextSearchViewPage extends Page implements ISearchResultPage {
	private static final String KEY_LAYOUT= "org.eclipse.search.resultpage.layout"; //$NON-NLS-1$
	
	private StructuredViewer fViewer;
	private Composite fViewerContainer;
	private ISearchResultViewPart fViewPart;
	private Set fBatchedUpdates;
	private ISearchResultListener fListener;
	
	private MenuManager fMenu;
	
	// Actions
	private Action fCopyToClipboardAction;
	private Action fRemoveResultsAction;
	private Action fRemoveAllResultsAction;
	private Action fShowNextAction;
	private Action fShowPreviousAction;
	private Action fFlatAction;
	private Action fHierarchicalAction;
	
	private boolean fIsInitiallyFlat;

	private int fCurrentMatchIndex= 0;
	private String fId;

	protected AbstractTextSearchViewPage() {
		fRemoveAllResultsAction= new RemoveAllResultsAction(this);
		fRemoveResultsAction= new RemoveMatchAction(this);
		fShowNextAction= new ShowNextResultAction(this);
		fShowPreviousAction= new ShowPreviousResultAction(this);
		

		fFlatAction= new SetLayoutAction(this, SearchMessages.getString("AbstractTextSearchViewPage.flat_layout.label"), SearchMessages.getString("AbstractTextSearchViewPage.flat_layout.tooltip"), true); //$NON-NLS-1$ //$NON-NLS-2$
		fHierarchicalAction= new SetLayoutAction(this, SearchMessages.getString("AbstractTextSearchViewPage.hierarchical_layout.label"), SearchMessages.getString("AbstractTextSearchViewPage.hierarchical_layout.tooltip"), false); //$NON-NLS-1$ //$NON-NLS-2$
		
		SearchPluginImages.setImageDescriptors(fFlatAction, SearchPluginImages.T_LCL, SearchPluginImages.IMG_LCL_SEARCH_FLAT_LAYOUT);
		SearchPluginImages.setImageDescriptors(fHierarchicalAction, SearchPluginImages.T_LCL, SearchPluginImages.IMG_LCL_SEARCH_HIERARCHICAL_LAYOUT);
		
		fBatchedUpdates= new HashSet();
		fListener= new ISearchResultListener() {
			public void searchResultChanged(SearchResultEvent e) {
				handleSearchResultsChanged(e);
			}
		};
	}
	
	/**
	 * Gets a dialog settings object for this search result page. There will be one dialog settings object
	 * per search result page id.
	 * @see #getID()
	 * @return The dialog settings for this search result page.
	 */
	protected IDialogSettings getSettings() {
		IDialogSettings parent= SearchPlugin.getDefault().getDialogSettings();
		IDialogSettings settings= parent.getSection(getID());
		if (settings == null)
			settings= parent.addNewSection(getID());
		return settings;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setID(String id) {
		fId= id;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public String getID() {
		return fId;
	}
	/**
	 * Opens an editor on the given element and selects the given range
	 * of text.
	 * The location of matches are automatically updated when a file is editor through the file buffer
	 * infrastructure (@see org.eclipse.core.filebuffers.ITextFileBufferManager). When a file buffer is
	 * saved, the current positions are written back to the match.
	 * @param match The match to show
	 * @param currentOffset The current start offset of the match
	 * @param currentLength The current length of the selection
	 * @throws PartInitException If an editor can't be opened.
	 */
	protected abstract void showMatch(Match match, int currentOffset, int currentLength) throws PartInitException;
	/**
	 * This method is called whenever the set of matches for the given elements 
	 * changes. This method is guaranteed to be called in the UI thread.
	 * Note that this notification is asynchronous. i.e. further changes may
	 * have occured by the time this method is called. They will be described in a 
	 * future call.
	 * @param objects Array of objects that has to be refreshed. 
	 */
	protected abstract void elementsChanged(Object[] objects);
	/**
	 * This method is called whenever all elements have been removed
	 * from the shown <code>AbstractSearchResult</code>. 
	 * This method is guaranteed to be called in the UI thread.
	 * Note that this notification is asynchronous. i.e. further changes may
	 * have occured by the time this method is called. They will be described in a 
	 * future call.
	 */
	protected abstract void clear();
	
	/**
	 * Configures the given viewer. Implementers have to set at least
	 * a content provider and a label provider.
	 * @param viewer The viewer to be configured
	 */
	protected abstract void configureTreeViewer(AbstractTreeViewer viewer);
	/**
	 * Configures the given viewer. Implementers have to set at least
	 * a content provider and a label provider.
	 * @param viewer The viewer to be configured
	 */
	protected abstract void configureTableViewer(TableViewer viewer);

	private static void createStandardGroups(IContributionManager menu) {
		menu.add(new Separator(IContextMenuConstants.GROUP_NEW));
		menu.add(new GroupMarker(IContextMenuConstants.GROUP_GOTO));
		menu.add(new GroupMarker(IContextMenuConstants.GROUP_OPEN));
		menu.add(new Separator(IContextMenuConstants.GROUP_SHOW));
		menu.add(new Separator(IContextMenuConstants.GROUP_BUILD));
		menu.add(new Separator(IContextMenuConstants.GROUP_REORGANIZE));
		menu.add(new Separator(IContextMenuConstants.GROUP_REMOVE_MATCHES));
		menu.add(new GroupMarker(IContextMenuConstants.GROUP_GENERATE));
		menu.add(new Separator(IContextMenuConstants.GROUP_SEARCH));
		menu.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		menu.add(new Separator(IContextMenuConstants.GROUP_VIEWER_SETUP));
		menu.add(new Separator(IContextMenuConstants.GROUP_PROPERTIES));
	}
	

	/**
	 * Fills the context menu for this page.
	 * Subclasses may override this method.
	 * @param tbm
	 */
	protected void fillContextMenu(IMenuManager mgr) {
		mgr.appendToGroup(IContextMenuConstants.GROUP_ADDITIONS, fCopyToClipboardAction);
		mgr.appendToGroup(IContextMenuConstants.GROUP_SHOW, fShowNextAction);
		mgr.appendToGroup(IContextMenuConstants.GROUP_SHOW, fShowPreviousAction);
		if (getCurrentMatch() != null)
			mgr.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, fRemoveResultsAction);
		mgr.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, fRemoveAllResultsAction);
	}
	
	/** 
	 * {@inheritDoc}
	 */
	public void createControl(Composite parent) {
		fMenu= new MenuManager("#PopUp"); //$NON-NLS-1$

		fMenu.setRemoveAllWhenShown(true);
		fMenu.setParent(getSite().getActionBars().getMenuManager());
		fMenu.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager mgr) {
				createStandardGroups(mgr);
				fillContextMenu(mgr);
				fViewPart.fillContextMenu(mgr);
			}

		});
			
		fViewerContainer= new Composite(parent, SWT.NULL);
		fViewerContainer.setSize(100, 100);
		fViewerContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
		fViewerContainer.setLayout(new FillLayout());
		createViewer(fViewerContainer, fIsInitiallyFlat);
	}
	
	/**
	 * Toggles the page between tree mode and flat (table) layout.
	 */
	public void setFlatLayout(boolean on) {
		if (on == isFlatLayout())
			return;
		ISelection selection= fViewer.getSelection();
		ISearchResult result= disconnectViewer();
		fViewer.getControl().dispose();
		fViewer= null;
		createViewer(fViewerContainer, on);
		fViewerContainer.layout(true);
		connectViewer(result);
		fViewer.setSelection(selection, true);
		getSettings().put(KEY_LAYOUT, on);
	}

	private void updateLayoutActions() {
		fFlatAction.setChecked(isFlatLayout());
		fHierarchicalAction.setChecked(!isFlatLayout());
	}

	/**
	 * Tells whether the page shows it's result as a tree or as a 
	 * table.
	 * @see AbstractTextSearchViewPage#configureTreeViewer(AbstractTreeViewer);
	 * @see AbstractTextSearchViewPage#configureTableViewer(TableViewer);
	 * @return Whether the page shows a tree or a table.
	 */
	public boolean isFlatLayout() {
		return fViewer instanceof TableViewer;
	}


	private void createViewer(Composite parent, boolean flatMode) {
		if (flatMode) {
			TableViewer viewer= new SearchResultsTableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
			fViewer= viewer;
			configureTableViewer(viewer);
		} else { 
			TreeViewer viewer= new SearchResultsTreeViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL);
			fViewer= viewer;
			configureTreeViewer(viewer);
		}

		IToolBarManager tbm= getSite().getActionBars().getToolBarManager();
		tbm.removeAll();
		createStandardGroups(tbm);
		fillToolbar(tbm);
		tbm.update(false);

		fViewer.addOpenListener(new IOpenListener() {
			public void open(OpenEvent event) {
				boolean hasCurrentMatch= showCurrentMatch();
				if (event.getViewer() instanceof TreeViewer && event.getSelection() instanceof IStructuredSelection) {
					TreeViewer tv= (TreeViewer) event.getViewer();
					Object element= ((IStructuredSelection)event.getSelection()).getFirstElement();
					tv.setExpandedState(element, !tv.getExpandedState(element));
					return;
				} else if (!hasCurrentMatch){
					gotoNextMatch();
				}
			}
		});
		
		fViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				fCurrentMatchIndex= 0;
			}
		});
		
		Menu menu= fMenu.createContextMenu(fViewer.getControl());
		fViewer.getControl().setMenu(menu);

		getSite().setSelectionProvider(fViewer);
		// Register menu
		getSite().registerContextMenu(fViewPart.getViewSite().getId(), fMenu, fViewer);
		updateLayoutActions();
	}

	/** 
	 * {@inheritDoc}
	 */
	public void setFocus() {
		Control control= fViewer.getControl();
		if (control != null && !control.isDisposed())
			control.setFocus();
	}

	/** 
	 * {@inheritDoc}
	 */
	public Control getControl() {
		return fViewerContainer;
	}

	/** 
	 * {@inheritDoc}
	 */
	public void setInput(ISearchResult search, Object viewState) {
		ISearchResult oldSearch= disconnectViewer();
		if (oldSearch != null)
			oldSearch.removeListener(fListener);
		AnnotationManager.searchResultActivated(getSite().getWorkbenchWindow(), (AbstractTextSearchResult) search);
		if (search != null) {
			search.addListener(fListener);
			connectViewer(search);
			if (viewState instanceof ISelection)
				fViewer.setSelection((ISelection) viewState, true);
			else
				gotoNextMatch();		
		}
	}
	
	/** 
	 * {@inheritDoc}
	 */
	public Object getUIState() {
		return fViewer.getSelection();
	}

	private void connectViewer(ISearchResult search) {
		fCopyToClipboardAction= new CopyToClipboardAction(fViewer);
		fViewer.setInput(search);
	}


	private ISearchResult disconnectViewer() {
		ISearchResult result= (ISearchResult) fViewer.getInput();
		fViewer.setInput(null);
		return result;
	}
	
	/**
	 * Returns the viewer currently used in this page. 
	 * @return The currently used viewer or <code>null</code> if none has been created yet.
	 */
	protected StructuredViewer getViewer() {
		return fViewer;
	}
	
	private void showMatch(final Match match) {
		ISafeRunnable runnable= new ISafeRunnable() {
			public void handleException(Throwable exception) {
				if (exception instanceof PartInitException) {
					PartInitException pie= (PartInitException) exception;
					ErrorDialog.openError(getSite().getShell(), SearchMessages.getString("DefaultSearchViewPage.show_match"), SearchMessages.getString("DefaultSearchViewPage.error.no_editor"), pie.getStatus());  //$NON-NLS-1$ //$NON-NLS-2$
				}
			}

			public void run() throws Exception {
				Position currentPosition= InternalSearchUI.getInstance().getPositionTracker().getCurrentPosition(match);
				if (currentPosition != null) {
					showMatch(match, currentPosition.getOffset(), currentPosition.getLength());
				} else {
					showMatch(match, match.getOffset(), match.getLength());
				}
			}

		};
		Platform.run(runnable);
	}

	/**
	 * Returns the currently shown result.
	 * @see AbstractTextSearchViewPage#setInput(ISearchResult, Object)
	 * @return The previously set result or <code>null</code>
	 */
	public AbstractTextSearchResult getInput() {
		if (fViewer != null)
			return (AbstractTextSearchResult) fViewer.getInput();
		return null;
	}

	/**
	 * Selects the element corresponding to the next match and
	 * shows the match in an editor. Note that this will cycle back to 
	 * the first match after the last match.
	 */
	public void gotoNextMatch() {
		fCurrentMatchIndex++;
		Match nextMatch= getCurrentMatch();
		if (nextMatch == null) {
			navigateNext(true);
			fCurrentMatchIndex= 0;
		}
		showCurrentMatch();
	}

	/**
	 * Selects the element corresponding to the previous match and
	 * shows the match in an editor. Note that this will cycle back to 
	 * the last match after the first match.
	 */
	public void gotoPreviousMatch() {
		fCurrentMatchIndex--;
		Match nextMatch= getCurrentMatch();
		if (nextMatch == null) {
			navigateNext(false);
			fCurrentMatchIndex= getInput().getMatchCount(getFirstSelectedElement())-1;
		}
		showCurrentMatch();
	}
	
	private void navigateNext(boolean forward) {
		if (fViewer instanceof INavigate) {
			((INavigate)fViewer).navigateNext(forward);
		}
	}
	
	private boolean showCurrentMatch() {
		Match currentMatch= getCurrentMatch();
		if (currentMatch != null) {
			showMatch(currentMatch);
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * Returns the currently selected match.
	 * @return The selected match or null if none are selected.
	 */
	public Match getCurrentMatch() {
		Object element= getFirstSelectedElement();
		if (element != null) {
			Match[] matches= getInput().getMatches(element);
			if (fCurrentMatchIndex >= 0 && fCurrentMatchIndex < matches.length)
				return matches[fCurrentMatchIndex];
		}
		return null;
	}
	
	private Object getFirstSelectedElement() {
		IStructuredSelection selection= (IStructuredSelection) fViewer.getSelection();
		if (selection.size() > 0)
			return selection.getFirstElement();
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void dispose() {
		//disconnectViewer();
		super.dispose();
	}

	/**
	 * {@inheritDoc}
	 */
	public void init(IPageSite pageSite) {
		super.init(pageSite);
		addLayoutMenu(pageSite.getActionBars().getMenuManager());
	}

	/**
	 * Fills the toolbar contribution for this page.
	 * Subclasses may override this method.
	 * @param tbm
	 */
	protected void fillToolbar(IToolBarManager tbm) {
		tbm.appendToGroup(IContextMenuConstants.GROUP_SHOW, fShowNextAction); //$NON-NLS-1$
		tbm.appendToGroup(IContextMenuConstants.GROUP_SHOW, fShowPreviousAction); //$NON-NLS-1$
		tbm.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, fRemoveResultsAction); //$NON-NLS-1$
		tbm.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, fRemoveAllResultsAction); //$NON-NLS-1$
		IActionBars actionBars= getSite().getActionBars();
		if (actionBars != null) {
			actionBars.setGlobalActionHandler(ActionFactory.NEXT.getId(), fShowNextAction);
			actionBars.setGlobalActionHandler(ActionFactory.PREVIOUS.getId(), fShowPreviousAction);
		}
	}

	private void addLayoutMenu(IMenuManager menuManager) {
		MenuManager subMenu= new MenuManager(SearchMessages.getString("AbstractTextSearchViewPage.layout.label")); //$NON-NLS-1$
		subMenu.add(fFlatAction);
		subMenu.add(fHierarchicalAction);
		menuManager.add(subMenu);
	}

	/**
	 * { @inheritDoc }
	 */
	public void setViewPart(ISearchResultViewPart part) {
		fViewPart= part;
	}
	
	// multithreaded update handling.
	
	private synchronized void handleSearchResultsChanged(final SearchResultEvent e) {
		if (e instanceof MatchEvent) {
			MatchEvent me= (MatchEvent) e;
			Object element= me.getMatch().getElement();
			postUpdate(element);
			AbstractTextSearchResult result= (AbstractTextSearchResult) me.getSearchResult();
			if (result.getMatchCount() == 1)
				asyncExec(new Runnable() {
					public void run() {
						navigateNext(true);
					}
				});
		} else if (e instanceof RemoveAllEvent) {
			postClear();
		}
	}
	
	private synchronized void postUpdate(final Object element) {
		fBatchedUpdates.add(element);
		if (fBatchedUpdates.size() == 1) {
			asyncExec(new Runnable() {
				public void run() {
					runBatchedUpdates();
				}
			});
		}
	}
	
	private void runBatchedUpdates() {
		synchronized(this) {
			elementsChanged(fBatchedUpdates.toArray());
			fBatchedUpdates.clear();
		}
	}

	private void postClear() {
		asyncExec(new Runnable() {
			public void run() {
				runClear();
			}
		});
	}
	
	private void runClear() {
		synchronized(this) {
			fBatchedUpdates.clear();
		}
		clear();
	}

	private void asyncExec(Runnable runnable) {
		Control control= getControl();
		if (control != null && !control.isDisposed()) {
			control.getDisplay().asyncExec(runnable);
		}
	}

	/**
	 * Subclasses may override.
	 * { @inheritDoc }
	 */
	public void restoreState(IMemento memento) {
		fIsInitiallyFlat= getSettings().getBoolean(KEY_LAYOUT);
		if (memento != null) {
			Integer isFlat= memento.getInteger(KEY_LAYOUT);
			if (isFlat != null) {
				fIsInitiallyFlat= isFlat.intValue() == 1;
			} 
		}
	}
	
	/**
	 * Subclasses my override.
	 * { @inheritDoc }
	 */
	public void saveState(IMemento memento) {
		if (isFlatLayout()) 
			memento.putInteger(KEY_LAYOUT, 1);
		else
			memento.putInteger(KEY_LAYOUT, 0);
	}
}