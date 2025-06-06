/*******************************************************************************
 * Copyright (c) 2000, 2024 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Genady Beryozkin <eclipse@genady.org> - [hovering] tooltip for constant string does not show constant value - https://bugs.eclipse.org/bugs/show_bug.cgi?id=85382
 *     Stephan Herrmann - Contribution for Bug 403917 - [1.8] Render TYPE_USE annotations in Javadoc hover/view
 *     Jozef Tomek - add styling enhancements (issue 1073)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.text.java.hover;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;

import org.osgi.framework.Bundle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Drawable;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;

import org.eclipse.core.resources.IFile;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.internal.text.html.BrowserInformationControl;
import org.eclipse.jface.internal.text.html.BrowserInformationControlInput;
import org.eclipse.jface.internal.text.html.BrowserInput;
import org.eclipse.jface.internal.text.html.HTMLPrinter;
import org.eclipse.jface.internal.text.html.HTMLTextPresenter;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.jface.text.AbstractReusableInformationControlCreator;
import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.IInformationControlExtension4;
import org.eclipse.jface.text.IInputChangedListener;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.TextPresentation;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPreferenceConstants;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.PartInitException;

import org.eclipse.ui.editors.text.EditorsUI;

import org.eclipse.jdt.core.IAnnotatable;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.manipulation.SharedASTProviderCore;

import org.eclipse.jdt.internal.core.manipulation.util.BasicElementLabels;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.javadoc.JavaDocLocations;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.actions.OpenAttachedJavadocAction;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.OpenBrowserUtil;
import org.eclipse.jdt.internal.ui.actions.SimpleSelectionProvider;
import org.eclipse.jdt.internal.ui.infoviews.JavadocView;
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.internal.ui.text.javadoc.JavadocContentAccess2;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabelComposer;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLinks;
import org.eclipse.jdt.internal.ui.viewsupport.javadoc.SignatureStylingMenuToolbarAction;


/**
 * Provides Javadoc as hover info for Java elements.
 *
 * @since 2.1
 */
public class JavadocHover extends AbstractJavaEditorTextHover {

	public static final String CONSTANT_VALUE_SEPARATOR= " : "; //$NON-NLS-1$

	public static class FallbackInformationPresenter extends HTMLTextPresenter {
		public FallbackInformationPresenter() {
			super(false);
		}

		@Override
		public String updatePresentation(Drawable drawable, String hoverInfo, TextPresentation presentation, int maxWidth, int maxHeight) {
			String warningInfo= JavaHoverMessages.JavadocHover_fallback_warning;
			String warning= super.updatePresentation(drawable, warningInfo, presentation, maxWidth, maxHeight);
			presentation.clear();

			String content= super.updatePresentation(drawable, hoverInfo, presentation, maxWidth, maxHeight);
			return content + System.lineSeparator()+System.lineSeparator() + warning;
		}
	}
	/**
	 * Action to go back to the previous input in the hover control.
	 *
	 * @since 3.4
	 */
	private static final class BackAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public BackAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_back);
			ISharedImages images= ISharedImages.get();
			setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_BACK));
			setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_BACK_DISABLED));

			update();
		}

		@Override
		public void run() {
			BrowserInformationControlInput previous= (BrowserInformationControlInput) fInfoControl.getInput().getPrevious();
			if (previous != null) {
				fInfoControl.setInput(previous);
			}
		}

		public void update() {
			BrowserInformationControlInput current= fInfoControl.getInput();

			if (current != null && current.getPrevious() != null) {
				BrowserInput previous= current.getPrevious();
				setToolTipText(Messages.format(JavaHoverMessages.JavadocHover_back_toElement_toolTip, BasicElementLabels.getJavaElementName(previous.getInputName())));
				setEnabled(true);
			} else {
				setToolTipText(JavaHoverMessages.JavadocHover_back);
				setEnabled(false);
			}
		}
	}

	/**
	 * Action to go forward to the next input in the hover control.
	 *
	 * @since 3.4
	 */
	private static final class ForwardAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public ForwardAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_forward);
			ISharedImages images= ISharedImages.get();
			setImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD));
			setDisabledImageDescriptor(images.getImageDescriptor(ISharedImages.IMG_TOOL_FORWARD_DISABLED));

			update();
		}

		@Override
		public void run() {
			BrowserInformationControlInput next= (BrowserInformationControlInput) fInfoControl.getInput().getNext();
			if (next != null) {
				fInfoControl.setInput(next);
			}
		}

		public void update() {
			BrowserInformationControlInput current= fInfoControl.getInput();

			if (current != null && current.getNext() != null) {
				setToolTipText(Messages.format(JavaHoverMessages.JavadocHover_forward_toElement_toolTip, BasicElementLabels.getJavaElementName(current.getNext().getInputName())));
				setEnabled(true);
			} else {
				setToolTipText(JavaHoverMessages.JavadocHover_forward_toolTip);
				setEnabled(false);
			}
		}
	}

	/**
	 * Action that shows the current hover contents in the Javadoc view.
	 *
	 * @since 3.4
	 */
	private static final class ShowInJavadocViewAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public ShowInJavadocViewAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_showInJavadoc);
			setImageDescriptor(JavaPluginImages.DESC_OBJS_JAVADOCTAG); //TODO: better image
		}

		/*
		 * @see org.eclipse.jface.action.Action#run()
		 */
		@Override
		public void run() {
			JavadocBrowserInformationControlInput infoInput= (JavadocBrowserInformationControlInput) fInfoControl.getInput(); //TODO: check cast
			fInfoControl.notifyDelayedInputChange(null);
			fInfoControl.dispose(); //FIXME: should have protocol to hide, rather than dispose
			try {
				JavadocView view= (JavadocView) JavaPlugin.getActivePage().showView(JavaUI.ID_JAVADOC_VIEW);
				view.setInput(infoInput);
			} catch (PartInitException e) {
				JavaPlugin.log(e);
			}
		}
	}

	/**
	 * Action that opens the current hover input element.
	 *
	 * @since 3.4
	 */
	private static final class OpenDeclarationAction extends Action {
		private final BrowserInformationControl fInfoControl;

		public OpenDeclarationAction(BrowserInformationControl infoControl) {
			fInfoControl= infoControl;
			setText(JavaHoverMessages.JavadocHover_openDeclaration);
			JavaPluginImages.setLocalImageDescriptors(this, "goto_input.svg"); //$NON-NLS-1$ //TODO: better images
		}

		/*
		 * @see org.eclipse.jface.action.Action#run()
		 */
		@Override
		public void run() {
			JavadocBrowserInformationControlInput infoInput= (JavadocBrowserInformationControlInput) fInfoControl.getInput(); //TODO: check cast
			fInfoControl.notifyDelayedInputChange(null);
			fInfoControl.dispose(); //FIXME: should have protocol to hide, rather than dispose

			try {
				//FIXME: add hover location to editor navigation history?
				openDeclaration(infoInput.getElement());
			} catch (PartInitException | JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
	}

	/**
	 * @return timeout that we want to have on javadoc hover to avoid two browser instances hanging
	 *         around for every opened java editor
	 */
	private static int getDisposeHoverTimeout() {
		String key = IWorkbenchPreferenceConstants.DISPOSE_CLOSED_BROWSER_HOVER_TIMEOUT;
		int timeout = Platform.getPreferencesService().getInt("org.eclipse.ui", key, -1, null);  //$NON-NLS-1$
		return timeout;
	}

	/**
	 * Presenter control creator.
	 *
	 * @since 3.3
	 */
	public static final class PresenterControlCreator extends AbstractReusableInformationControlCreator {

		private final IWorkbenchSite fSite;

		/**
		 * Creates a new PresenterControlCreator.
		 *
		 * @param site the site or <code>null</code> if none
		 * @since 3.6
		 */
		public PresenterControlCreator(IWorkbenchSite site) {
			fSite= site;
		}

		@Override
		public IInformationControl doCreateInformationControl(Shell parent) {
			if (BrowserInformationControl.isAvailable(parent)) {
				ToolBarManager tbm= new ToolBarManager(SWT.FLAT);
				String font= PreferenceConstants.APPEARANCE_JAVADOC_FONT;
				BrowserInformationControl iControl= new BrowserInformationControl(parent, font, tbm);
				iControl.setDisposeTimeout(getDisposeHoverTimeout());
				final BackAction backAction= new BackAction(iControl);
				backAction.setEnabled(false);
				tbm.add(backAction);
				final ForwardAction forwardAction= new ForwardAction(iControl);
				tbm.add(forwardAction);
				forwardAction.setEnabled(false);

				final ShowInJavadocViewAction showInJavadocViewAction= new ShowInJavadocViewAction(iControl);
				tbm.add(showInJavadocViewAction);
				final OpenDeclarationAction openDeclarationAction= new OpenDeclarationAction(iControl);
				tbm.add(openDeclarationAction);

				final SimpleSelectionProvider selectionProvider= new SimpleSelectionProvider();
				if (fSite != null) {
					OpenAttachedJavadocAction openAttachedJavadocAction= new OpenAttachedJavadocAction(fSite);
					openAttachedJavadocAction.setSpecialSelectionProvider(selectionProvider);
					openAttachedJavadocAction.setImageDescriptor(JavaPluginImages.DESC_ELCL_OPEN_BROWSER);
					selectionProvider.addSelectionChangedListener(openAttachedJavadocAction);
					selectionProvider.setSelection(new StructuredSelection());
					tbm.add(openAttachedJavadocAction);
				}

				var toolbarComposite= tbm.getControl().getParent();
				GridLayout layout= new GridLayout(4, false);
				layout.marginHeight= 0;
				layout.marginWidth= 0;
				layout.horizontalSpacing= 0;
				layout.verticalSpacing= 0;
				toolbarComposite.setLayout(layout);

				Runnable viewRefreshTask= () -> {
					if (iControl.getInput() instanceof JavadocHoverInformationControlInput input) {
						iControl.setInput(input.recreateInput());
					} else {
						iControl.setVisible(false);
					}
				};
				ToolBarManager tbmSecondary= new ToolBarManager(SWT.FLAT);
				tbmSecondary.createControl(toolbarComposite).setLayoutData(new GridData(SWT.END, SWT.BEGINNING, false, false));
				var stylingMenuAction= new SignatureStylingMenuToolbarAction(iControl.getShell(),
						iControl::addInputChangeListener, viewRefreshTask);
				tbmSecondary.add(stylingMenuAction);
				tbmSecondary.update(true);
				stylingMenuAction.setup(tbmSecondary.getControl());
				tbmSecondary.getControl().moveAbove(toolbarComposite.getChildren()[2]); // move to be before resizeCanvas

				IInputChangedListener inputChangeListener= newInput -> {
					backAction.update();
					forwardAction.update();
					if (newInput == null) {
						selectionProvider.setSelection(new StructuredSelection());
					} else if (newInput instanceof BrowserInformationControlInput) {
						BrowserInformationControlInput input= (BrowserInformationControlInput) newInput;
						Object inputElement= input.getInputElement();
						selectionProvider.setSelection(new StructuredSelection(inputElement));
						boolean isJavaElementInput= inputElement instanceof IJavaElement;
						showInJavadocViewAction.setEnabled(isJavaElementInput);
						openDeclarationAction.setEnabled(isJavaElementInput);
					}
				};
				iControl.addInputChangeListener(inputChangeListener);

				tbm.update(true);

				addLinkListener(iControl);
				return iControl;

			} else {
				return new DefaultInformationControl(parent, true);
			}
		}
	}


	/**
	 * Hover control creator.
	 *
	 * @since 3.3
	 */
	public static final class HoverControlCreator extends AbstractReusableInformationControlCreator implements IPropertyChangeListener {
		/**
		 * The information presenter control creator.
		 * @since 3.4
		 */
		private final IInformationControlCreator fInformationPresenterControlCreator;
		/**
		 * <code>true</code> to use the additional info affordance, <code>false</code> to use the hover affordance.
		 */
		private final boolean fAdditionalInfoAffordance;

		/**
		 * @param informationPresenterControlCreator control creator for enriched hover
		 * @since 3.4
		 */
		public HoverControlCreator(IInformationControlCreator informationPresenterControlCreator) {
			this(informationPresenterControlCreator, false);
		}

		/**
		 * @param informationPresenterControlCreator control creator for enriched hover
		 * @param additionalInfoAffordance <code>true</code> to use the additional info affordance, <code>false</code> to use the hover affordance
		 * @since 3.4
		 */
		public HoverControlCreator(IInformationControlCreator informationPresenterControlCreator, boolean additionalInfoAffordance) {
			fInformationPresenterControlCreator= informationPresenterControlCreator;
			fAdditionalInfoAffordance= additionalInfoAffordance;
		}

		private BrowserInformationControl iControl;
		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.hover.AbstractReusableInformationControlCreator#doCreateInformationControl(org.eclipse.swt.widgets.Shell)
		 */
		@Override
		public IInformationControl doCreateInformationControl(Shell parent) {
			String tooltipAffordanceString= fAdditionalInfoAffordance ? JavaPlugin.getAdditionalInfoAffordanceString() : EditorsUI.getTooltipAffordanceString();
			if (BrowserInformationControl.isAvailable(parent)) {
				String font= PreferenceConstants.APPEARANCE_JAVADOC_FONT;
				iControl= new BrowserInformationControl(parent, font, tooltipAffordanceString) {

					@Override
					public IInformationControlCreator getInformationPresenterControlCreator() {
						return fInformationPresenterControlCreator;
					}
				};
				iControl.setDisposeTimeout(getDisposeHoverTimeout());
				JFaceResources.getColorRegistry().addListener(this); // So propertyChange() method is triggered in context of IPropertyChangeListener
				setHoverColors();

				addLinkListener(iControl);
				return iControl;
			} else {
				return new DefaultInformationControl(parent, tooltipAffordanceString) {
					@Override
					public IInformationControlCreator getInformationPresenterControlCreator() {
						return parentShell -> new DefaultInformationControl(parentShell, (ToolBarManager) null, new FallbackInformationPresenter());
					}
				};
			}
		}

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			String property= event.getProperty();
			if (iControl != null &&
					("org.eclipse.jdt.ui.Javadoc.foregroundColor".equals(property) //$NON-NLS-1$
							|| "org.eclipse.jdt.ui.Javadoc.backgroundColor".equals(property))) { //$NON-NLS-1$
				setHoverColors();
			}
		}

		private void setHoverColors() {
			ColorRegistry registry = JFaceResources.getColorRegistry();
			Color fgRGB = registry.get("org.eclipse.jdt.ui.Javadoc.foregroundColor"); //$NON-NLS-1$
			Color bgRGB = registry.get("org.eclipse.jdt.ui.Javadoc.backgroundColor"); //$NON-NLS-1$
			iControl.setForegroundColor(fgRGB);
			iControl.setBackgroundColor(bgRGB);
		}

		@Override
		public void widgetDisposed(DisposeEvent e) {
			super.widgetDisposed(e);
			//Called when active editor is closed.
			JFaceResources.getColorRegistry().removeListener(this);
		}

		/*
		 * @see org.eclipse.jdt.internal.ui.text.java.hover.AbstractReusableInformationControlCreator#canReuse(org.eclipse.jface.text.IInformationControl)
		 */
		@Override
		public boolean canReuse(IInformationControl control) {
			if (!super.canReuse(control))
				return false;

			if (control instanceof IInformationControlExtension4) {
				String tooltipAffordanceString= fAdditionalInfoAffordance ? JavaPlugin.getAdditionalInfoAffordanceString() : EditorsUI.getTooltipAffordanceString();
				((IInformationControlExtension4)control).setStatusText(tooltipAffordanceString);
			}

			return true;
		}
	}

	private static class JavadocHoverInformationControlInput extends JavadocBrowserInformationControlInput {
		private final ITypeRoot fEditorInputElement;
		private final IRegion fHoverRegion;

		public JavadocHoverInformationControlInput(JavadocBrowserInformationControlInput previous, IJavaElement element, String html, int leadingImageWidth, ITypeRoot editorInputElement, IRegion hoverRegion) {
			super(previous, element, html, leadingImageWidth);
			fEditorInputElement= editorInputElement;
			fHoverRegion= hoverRegion;
		}

		public JavadocBrowserInformationControlInput recreateInput() {
			return JavadocHover.getHoverInfo(new IJavaElement[] { getElement() }, fEditorInputElement, fHoverRegion, (JavadocBrowserInformationControlInput) getPrevious());
		}
	}

	private static final long LABEL_FLAGS=  JavaElementLabels.ALL_FULLY_QUALIFIED
		| JavaElementLabels.M_PRE_RETURNTYPE | JavaElementLabels.M_PARAMETER_ANNOTATIONS | JavaElementLabels.M_PARAMETER_TYPES | JavaElementLabels.M_PARAMETER_NAMES | JavaElementLabels.M_EXCEPTIONS
		| JavaElementLabels.F_PRE_TYPE_SIGNATURE | JavaElementLabels.M_PRE_TYPE_PARAMETERS | JavaElementLabels.T_TYPE_PARAMETERS
		| JavaElementLabels.USE_RESOLVED;
	private static final long LOCAL_VARIABLE_FLAGS= LABEL_FLAGS & ~JavaElementLabels.F_FULLY_QUALIFIED | JavaElementLabels.F_POST_QUALIFIED;
	private static final long TYPE_PARAMETER_FLAGS= LABEL_FLAGS | JavaElementLabels.TP_POST_QUALIFIED;
	private static final long PACKAGE_FLAGS= LABEL_FLAGS & ~JavaElementLabels.ALL_FULLY_QUALIFIED;

	/**
	 * The style sheet (css).
	 * @since 3.4
	 */
	private static String fgStyleSheet;

	/**
	 * The hover control creator.
	 *
	 * @since 3.2
	 */
	private IInformationControlCreator fHoverControlCreator;
	/**
	 * The presentation control creator.
	 *
	 * @since 3.2
	 */
	private IInformationControlCreator fPresenterControlCreator;

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.hover.AbstractJavaEditorTextHover#getInformationPresenterControlCreator()
	 * @since 3.1
	 */
	@Override
	public IInformationControlCreator getInformationPresenterControlCreator() {
		if (fPresenterControlCreator == null)
			fPresenterControlCreator= new PresenterControlCreator(getSite());
		return fPresenterControlCreator;
	}

	private IWorkbenchSite getSite() {
		IEditorPart editor= getEditor();
		if (editor == null) {
			IWorkbenchPage page= JavaPlugin.getActivePage();
			if (page != null)
				editor= page.getActiveEditor();
		}
		if (editor != null)
			return editor.getSite();

		return null;
	}

	/*
	 * @see ITextHoverExtension#getHoverControlCreator()
	 * @since 3.2
	 */
	@Override
	public IInformationControlCreator getHoverControlCreator() {
		if (fHoverControlCreator == null)
			fHoverControlCreator= new HoverControlCreator(getInformationPresenterControlCreator());
		return fHoverControlCreator;
	}


	public static IEditorPart openDeclaration(IJavaElement element) throws PartInitException, JavaModelException {
		if (!(element instanceof IPackageFragment)) {
			return JavaUI.openInEditor(element);
		}

		IPackageFragment packageFragment= (IPackageFragment) element;
		ITypeRoot typeRoot;
		IPackageFragmentRoot root= (IPackageFragmentRoot) packageFragment.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
		if (root.getKind() == IPackageFragmentRoot.K_BINARY) {
			typeRoot= packageFragment.getClassFile(JavaModelUtil.PACKAGE_INFO_CLASS);
		} else {
			typeRoot= packageFragment.getCompilationUnit(JavaModelUtil.PACKAGE_INFO_JAVA);
		}

		// open the package-info file in editor if one exists
		if (typeRoot.exists())
			return JavaUI.openInEditor(typeRoot);

		// open the package.html file in editor if one exists
		Object[] nonJavaResources= packageFragment.getNonJavaResources();
		for (Object nonJavaResource : nonJavaResources) {
			if (nonJavaResource instanceof IFile) {
				IFile file= (IFile) nonJavaResource;
				if (file.exists() && JavaModelUtil.PACKAGE_HTML.equals(file.getName())) {
					return EditorUtility.openInEditor(file, true);
				}
			}
		}

		// select the package in the Package Explorer if there is no associated package Javadoc file
		PackageExplorerPart view= (PackageExplorerPart) JavaPlugin.getActivePage().showView(JavaUI.ID_PACKAGES);
		view.tryToReveal(packageFragment);
		return null;
	}

	private static void addLinkListener(final BrowserInformationControl control) {
		control.addLocationListener(JavaElementLinks.createLocationListener(new JavaElementLinks.ILinkHandler() {
			@Override
			public void handleJavadocViewLink(IJavaElement linkTarget) {
				control.notifyDelayedInputChange(null);
				control.setVisible(false);
				control.dispose(); //FIXME: should have protocol to hide, rather than dispose
				try {
					JavadocView view= (JavadocView) JavaPlugin.getActivePage().showView(JavaUI.ID_JAVADOC_VIEW);
					view.setInput(linkTarget);
				} catch (PartInitException e) {
					JavaPlugin.log(e);
				}
			}

			@Override
			public void handleInlineJavadocLink(IJavaElement linkTarget) {
				JavadocBrowserInformationControlInput hoverInfo= getHoverInfo(new IJavaElement[] { linkTarget }, null, null, (JavadocBrowserInformationControlInput) control.getInput());
				if (control.hasDelayedInputChangeListener())
					control.notifyDelayedInputChange(hoverInfo);
				else
					control.setInput(hoverInfo);
			}

			@Override
			public void handleDeclarationLink(IJavaElement linkTarget) {
				control.notifyDelayedInputChange(null);
				control.dispose(); //FIXME: should have protocol to hide, rather than dispose
				try {
					//FIXME: add hover location to editor navigation history?
					openDeclaration(linkTarget);
				} catch (PartInitException | JavaModelException e) {
					JavaPlugin.log(e);
				}
			}

			@Override
			public boolean handleExternalLink(URL url, Display display) {
				control.notifyDelayedInputChange(null);
				control.dispose(); //FIXME: should have protocol to hide, rather than dispose

				// Open attached Javadoc links
				OpenBrowserUtil.open(url, display);

				return true;
			}

			@Override
			public void handleTextSet() {
			}
		}));
	}

	/**
	 * @deprecated see {@link org.eclipse.jface.text.ITextHover#getHoverInfo(ITextViewer, IRegion)}
	 */
	@Override
	@Deprecated
	public String getHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		JavadocBrowserInformationControlInput info= (JavadocBrowserInformationControlInput) getHoverInfo2(textViewer, hoverRegion);
		return info != null ? info.getHtml() : null;
	}

	/*
	 * @see org.eclipse.jface.text.ITextHoverExtension2#getHoverInfo2(org.eclipse.jface.text.ITextViewer, org.eclipse.jface.text.IRegion)
	 */
	@Override
	public Object getHoverInfo2(ITextViewer textViewer, IRegion hoverRegion) {
		return internalGetHoverInfo(textViewer, hoverRegion);
	}

	private JavadocBrowserInformationControlInput internalGetHoverInfo(ITextViewer textViewer, IRegion hoverRegion) {
		IJavaElement[] elements= JavaCore.callReadOnly(() -> getJavaElementsAt(textViewer, hoverRegion));
		if (elements == null || elements.length == 0)
			return null;

		return getHoverInfo(elements, getEditorInputJavaElement(), hoverRegion, null);
	}

	/**
	 * Returns the first package with a valid Javadoc when there are multiple packages with the same
	 * name in the project. If no package could be found with a valid Javadoc then returns the first
	 * package in the array. If the array does not contain package, then return the array unaltered.
	 *
	 * @param elements array from which to filter duplicate packages
	 * @return the first package with a valid Javadoc. If no package is found with a valid Javadoc
	 *         then return the first element in the array if the element is of type
	 *         IPackageFragment, else return the elements array unaltered
	 * @since 3.9
	 */
	private static IJavaElement[] filterDuplicatePackages(IJavaElement[] elements) {
		if (elements.length <= 1 || !(elements[0] instanceof IPackageFragment)) {
			return elements;
		}

		for (IJavaElement element : elements) {
			try {
				if (element instanceof IPackageFragment) {
					IPackageFragment packageFragment= (IPackageFragment) element;
					if (JavadocContentAccess2.getHTMLContent(packageFragment) != null)
						return new IJavaElement[] { packageFragment };
				}
			} catch (CoreException e) {
				//ignore the exception and consider the next element to process
			}
		}

		return new IJavaElement[] { elements[0] };
	}

	/**
	 * Computes the hover info.
	 *
	 * @param elements the resolved elements
	 * @param editorInputElement the editor input, or <code>null</code>
	 * @param hoverRegion the text range of the hovered word, or <code>null</code>
	 * @param previousInput the previous input, or <code>null</code>
	 * @return the HTML hover info for the given element(s) or <code>null</code> if no information is available
	 * @since 3.4
	 */
	public static JavadocBrowserInformationControlInput getHoverInfo(IJavaElement[] elements, ITypeRoot editorInputElement, IRegion hoverRegion, JavadocBrowserInformationControlInput previousInput) {
		StringBuilder buffer= new StringBuilder();
		boolean hasContents= false;
		String base= null;
		IJavaElement element= null;
		int leadingImageWidth= 0;

		elements= filterDuplicatePackages(elements);

		if (elements.length > 1) {
			for (IJavaElement el : elements) {
				HTMLPrinter.startBulletList(buffer);
				IJavaElement curr= el;
				if (curr instanceof IMember || curr.getElementType() == IJavaElement.LOCAL_VARIABLE) {
					String label= JavaElementLabels.getElementLabel(curr, getHeaderFlags(curr));
					String link;
					try {
						String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, curr);
						link= JavaElementLinks.createLink(uri, label);
					} catch (URISyntaxException e) {
						JavaPlugin.log(e);
						link= label;
					}
					HTMLPrinter.addBullet(buffer, link);
					hasContents= true;
				}
				HTMLPrinter.endBulletList(buffer);
			}
		} else {
			element= elements[0];

			if (element instanceof IPackageFragment || element instanceof IMember
					|| element instanceof ILocalVariable || element instanceof ITypeParameter) {
				HTMLPrinter.addSmallHeader(buffer, getInfoText(element, editorInputElement, hoverRegion, true));
				buffer.append("<br>"); //$NON-NLS-1$
				addAnnotations(buffer, element, editorInputElement, hoverRegion);
				Reader reader= null;
				try {
					String content= JavadocContentAccess2.getHTMLContent(element, true);
					IPackageFragmentRoot root= (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
					boolean isBinary= root.exists() && root.getKind() == IPackageFragmentRoot.K_BINARY;
					if (content != null) {
						base= JavadocContentAccess2.extractBaseURL(content);
						if (base == null) {
							base= JavaDocLocations.getBaseURL(element, isBinary);
						}
						reader= new StringReader(content);
					} else {
						String explanationForMissingJavadoc= JavaDocLocations.getExplanationForMissingJavadoc(element, root);
						if (explanationForMissingJavadoc != null)
							reader= new StringReader(explanationForMissingJavadoc);
					}
				} catch (CoreException ex) {
					reader= new StringReader(JavaDocLocations.handleFailedJavadocFetch(ex));
				}

				if (reader != null) {
					HTMLPrinter.addParagraph(buffer, reader);
				}
				hasContents= true;
			}
			leadingImageWidth= 20;
		}

		if (!hasContents)
			return null;

		if (buffer.length() > 0) {

			ColorRegistry registry = JFaceResources.getColorRegistry();
			RGB fgRGB = registry.getRGB("org.eclipse.jdt.ui.Javadoc.foregroundColor"); //$NON-NLS-1$
			RGB bgRGB= registry.getRGB("org.eclipse.jdt.ui.Javadoc.backgroundColor"); //$NON-NLS-1$

			HTMLPrinter.insertPageProlog(buffer, 0, fgRGB, bgRGB, JavadocHover.getStyleSheet(buffer));
			if (base != null) {
				int endHeadIdx= buffer.indexOf("</head>"); //$NON-NLS-1$
				buffer.insert(endHeadIdx, "\n<base href='" + base + "'>\n"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			HTMLPrinter.addPageEpilog(buffer);
			return new JavadocHoverInformationControlInput(previousInput, element, buffer.toString(), leadingImageWidth, editorInputElement, hoverRegion);
		}

		return null;
	}

	private static String getInfoText(IJavaElement element, ITypeRoot editorInputElement, IRegion hoverRegion, boolean allowImage) {
		long flags= getHeaderFlags(element);

		boolean haveSource= editorInputElement instanceof ICompilationUnit;
		ASTNode node= haveSource ? getHoveredASTNode(editorInputElement, hoverRegion) : null;
		IBinding binding= getHoverBinding(element, node);

		StringBuilder label;
		if (binding != null) {
			label= new StringBuilder(JavaElementLinks.getBindingLabel(binding, element, flags, haveSource, true));
		} else {
			label= new StringBuilder(JavaElementLinks.getElementLabel(element, flags, false, true));
		}

		if (element.getElementType() == IJavaElement.FIELD) {
			String constantValue= getConstantValue((IField) element, editorInputElement, hoverRegion);
			if (constantValue != null) {
				constantValue= HTMLPrinter.convertToHTMLContentWithWhitespace(constantValue);
				label.append(CONSTANT_VALUE_SEPARATOR);
				label.append(constantValue);
			}
		}

		if (element.getElementType() == IJavaElement.METHOD) {
			String defaultValue;
			try {
				defaultValue= getAnnotationMemberDefaultValue((IMethod) element, editorInputElement, hoverRegion);
			} catch (JavaModelException e) {
				defaultValue= null;
			}
			if (defaultValue != null) {
				defaultValue= HTMLPrinter.convertToHTMLContentWithWhitespace(defaultValue);
				label.append(CONSTANT_VALUE_SEPARATOR);
				label.append(defaultValue);
			}
		}

		return getImageAndLabel(element, allowImage, label.toString());
	}

	/**
	 * Returns the default value of the given annotation type method.
	 *
	 * @param method the method
	 * @param editorInputElement the editor input element
	 * @param hoverRegion the hover region in the editor
	 * @return the default value of the given annotation type method or <code>null</code> if none
	 * @throws JavaModelException if an exception occurs while accessing its default value
	 */
	public static String getAnnotationMemberDefaultValue(IMethod method, ITypeRoot editorInputElement, IRegion hoverRegion) throws JavaModelException {
		IMemberValuePair memberValuePair= method.getDefaultValue();
		if (memberValuePair == null) {
			return null;
		}

		Object defaultValue= memberValuePair.getValue();
		boolean isEmptyArray= defaultValue instanceof Object[] && ((Object[]) defaultValue).length == 0;
		int valueKind= memberValuePair.getValueKind();

		if (valueKind == IMemberValuePair.K_UNKNOWN && !isEmptyArray) {
			IBinding binding= getHoveredNodeBinding(method, editorInputElement, hoverRegion);
			if (binding instanceof IMethodBinding) {
				Object value= ((IMethodBinding) binding).getDefaultValue();
				StringBuffer buf= new StringBuffer();
				try {
					addValue(buf, value, false);
				} catch (URISyntaxException e) {
					// should not happen as links are not added
				}
				return buf.toString();
			}

		} else if (defaultValue != null) {
			IAnnotation parentAnnotation= (IAnnotation) method.getAncestor(IJavaElement.ANNOTATION);
			StringBuffer buf= new StringBuffer();
			new JavaElementLabelComposer(buf).appendAnnotationValue(parentAnnotation, defaultValue, valueKind, LABEL_FLAGS);
			return buf.toString();
		}

		return null;
	}

	private static IBinding getHoveredNodeBinding(IJavaElement element, ITypeRoot editorInputElement, IRegion hoverRegion) {
		if (editorInputElement == null || hoverRegion == null) {
			return null;
		}
		IBinding binding;
		ASTNode node= getHoveredASTNode(editorInputElement, hoverRegion);
		if (node == null) {
			ASTParser p= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
			p.setProject(element.getJavaProject());
			p.setBindingsRecovery(true);
			try {
				binding= p.createBindings(new IJavaElement[] { element }, null)[0];
			} catch (OperationCanceledException e) {
				return null;
			}
		} else {
			binding= resolveBinding(node);
		}
		return binding;
	}

	/**
	 * Try to acquire a binding corresponding to the given element
	 * for more precise information about (type) annotations.
	 *
	 * Currently this lookup is only enabled when null-annotations are enabled for the project.
	 *
	 * @param element the element being rendered
	 * @param node the AST node corresponding to the given element, or null, if no AST node is available.
	 * @return either a binding corresponding to the given element or null.
	 */
	public static IBinding getHoverBinding(IJavaElement element, ASTNode node) {

		if (JavaCore.ENABLED.equals(element.getJavaProject().getOption(JavaCore.COMPILER_ANNOTATION_NULL_ANALYSIS, true))) {
			if (node == null) {
				if (element instanceof ISourceReference) {
					ASTParser p= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
					p.setProject(element.getJavaProject());
					p.setBindingsRecovery(true);
					try {
						return p.createBindings(new IJavaElement[] { element }, null)[0];
					} catch (OperationCanceledException e) {
						return null;
					}
				}
			} else {
				return resolveBinding(node);
			}
		}
		return null;
	}

	private static String getImageURL(IJavaElement element) {
		String imageName= null;
		URL imageUrl= JavaPlugin.getDefault().getImagesOnFSRegistry().getImageURL(element, 200);
		if (imageUrl != null) {
			imageName= imageUrl.toExternalForm();
		}

		return imageName;
	}

	public static long getHeaderFlags(IJavaElement element) {
		switch (element.getElementType()) {
			case IJavaElement.LOCAL_VARIABLE:
				return LOCAL_VARIABLE_FLAGS;
			case IJavaElement.TYPE_PARAMETER:
				return TYPE_PARAMETER_FLAGS;
			case IJavaElement.PACKAGE_FRAGMENT:
			case IJavaElement.PACKAGE_DECLARATION:
				return PACKAGE_FLAGS;
			default:
				return LABEL_FLAGS;
		}
	}

	/**
	 * Tells whether the given field is static final.
	 *
	 * @param field the member to test
	 * @return <code>true</code> if static final
	 * @since 3.4
	 */
	public static boolean isStaticFinal(IField field) {
		try {
			return JdtFlags.isFinal(field) && JdtFlags.isStatic(field);
		} catch (JavaModelException e) {
			JavaPlugin.log(e);
			return false;
		}
	}

	/**
	 * Returns the constant value for the given field.
	 *
	 * @param field the field
	 * @param editorInputElement the editor input element
	 * @param hoverRegion the hover region in the editor
	 * @return the constant value for the given field or <code>null</code> if none
	 * @since 3.4
	 */
	public static String getConstantValue(IField field, ITypeRoot editorInputElement, IRegion hoverRegion) {
		if (!isStaticFinal(field))
			return null;

		Object constantValue;
		ASTNode node= getHoveredASTNode(editorInputElement, hoverRegion);
		if (node != null) {
			constantValue= getVariableBindingConstValue(node, field);
		} else {
			constantValue= JavadocView.computeFieldConstantFromTypeAST(field, null);
		}
		if (constantValue == null)
			return null;

		if (constantValue instanceof String) {
			return ASTNodes.getEscapedStringLiteral((String) constantValue);
		} else {
			return getHexConstantValue(constantValue);
		}
	}

	private static ASTNode getHoveredASTNode(ITypeRoot editorInputElement, IRegion hoverRegion) {
		if (editorInputElement == null || hoverRegion == null)
			return null;

		CompilationUnit unit= SharedASTProviderCore.getAST(editorInputElement, SharedASTProviderCore.WAIT_ACTIVE_ONLY, null);
		if (unit == null)
			return null;

		return NodeFinder.perform(unit, hoverRegion.getOffset(),	hoverRegion.getLength());
	}

	/**
	 * Creates and returns a formatted message for the given
	 * constant with its hex value.
	 *
	 * @param constantValue the constant value
	 * @param hexValue the hex value
	 * @return a formatted string with constant and hex values
	 * @since 3.4
	 */
	private static String formatWithHexValue(Object constantValue, String hexValue) {
		return Messages.format(JavaHoverMessages.JavadocHover_constantValue_hexValue, new String[] { constantValue.toString(), hexValue });
	}

	/**
	 * Returns the Javadoc hover style sheet with the current Javadoc font from the preferences.
	 *
	 * @param content html content which will use the style sheet
	 * @return the updated style sheet
	 * @since 3.4
	 */
	private static String getStyleSheet(StringBuilder content) {
		if (fgStyleSheet == null) {
			fgStyleSheet= loadStyleSheet("/JavadocHoverStyleSheet.css"); //$NON-NLS-1$
		}
		String css= fgStyleSheet;
		if (css != null) {
			FontData fontData= JFaceResources.getFontRegistry().getFontData(PreferenceConstants.APPEARANCE_JAVADOC_FONT)[0];
			css= HTMLPrinter.convertTopLevelFont(css, fontData);
		}
		css= JavaElementLinks.modifyCssStyleSheet(css, content);

		return css;
	}

	/**
	 * Loads and returns the style sheet associated with either Javadoc hover or the view.
	 *
	 * @param styleSheetName the style sheet name of either the Javadoc hover or the view
	 * @return the style sheet, or <code>null</code> if unable to load
	 * @since 3.4
	 */
	public static String loadStyleSheet(String styleSheetName) {
		Bundle bundle= Platform.getBundle(JavaPlugin.getPluginId());
		URL styleSheetURL= bundle.getEntry(styleSheetName);
		if (styleSheetURL == null)
			return null;

		BufferedReader reader= null;
		try {
			reader= new BufferedReader(new InputStreamReader(styleSheetURL.openStream()));
			StringBuilder buffer= new StringBuilder(1500);
			String line= reader.readLine();
			while (line != null) {
				buffer.append(line);
				buffer.append('\n');
				line= reader.readLine();
			}

			FontData fontData= JFaceResources.getFontRegistry().getFontData(PreferenceConstants.APPEARANCE_JAVADOC_FONT)[0];
			return HTMLPrinter.convertTopLevelFont(buffer.toString(), fontData);
		} catch (IOException ex) {
			JavaPlugin.log(ex);
			return ""; //$NON-NLS-1$
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (IOException e) {
				//ignore
			}
		}
	}

	public static String getImageAndLabel(IJavaElement element, boolean allowImage, String label) {
		StringBuilder buf= new StringBuilder();
		int imageWidth= 16;
		int imageHeight= 16;
		int labelLeft= 20;
		int labelTop= 2;

		buf.append("<div style='word-wrap: break-word; position: relative; "); //$NON-NLS-1$

		String imageSrcPath= allowImage ? getImageURL(element) : null;
		if (imageSrcPath != null) {
			buf.append("margin-left: ").append(labelLeft).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
			buf.append("padding-top: ").append(labelTop).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
		}

		buf.append("'>"); //$NON-NLS-1$
		if (imageSrcPath != null) {
			if (element != null) {
				try {
					String uri= JavaElementLinks.createURI(JavaElementLinks.OPEN_LINK_SCHEME, element);
					buf.append("<a href='").append(uri).append("'>");  //$NON-NLS-1$//$NON-NLS-2$
				} catch (URISyntaxException e) {
					element= null; // no link
				}
			}
			StringBuilder imageStyle= new StringBuilder("border:none; position: absolute; "); //$NON-NLS-1$
			imageStyle.append("width: ").append(imageWidth).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
			imageStyle.append("height: ").append(imageHeight).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$
			imageStyle.append("left: ").append(- labelLeft - 1).append("px; "); //$NON-NLS-1$ //$NON-NLS-2$

			// hack for broken transparent PNG support in IE 6, see https://bugs.eclipse.org/bugs/show_bug.cgi?id=223900 :
			buf.append("<!--[if lte IE 6]><![if gte IE 5.5]>\n"); //$NON-NLS-1$
			String tooltip= element == null ? "" : "alt='" + JavaHoverMessages.JavadocHover_openDeclaration + "' "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			buf.append("<span ").append(tooltip).append("style=\"").append(imageStyle). //$NON-NLS-1$ //$NON-NLS-2$
					append("filter:progid:DXImageTransform.Microsoft.AlphaImageLoader(src='").append(imageSrcPath).append("')\"></span>\n"); //$NON-NLS-1$ //$NON-NLS-2$
			buf.append("<![endif]><![endif]-->\n"); //$NON-NLS-1$

			buf.append("<!--[if !IE]>-->\n"); //$NON-NLS-1$
			buf.append("<img ").append(tooltip).append("style='").append(imageStyle).append("' src='").append(imageSrcPath).append("'/>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			buf.append("<!--<![endif]-->\n"); //$NON-NLS-1$
			buf.append("<!--[if gte IE 7]>\n"); //$NON-NLS-1$
			buf.append("<img ").append(tooltip).append("style='").append(imageStyle).append("' src='").append(imageSrcPath).append("'/>\n"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			buf.append("<![endif]-->\n"); //$NON-NLS-1$
			if (element != null) {
				buf.append("</a>"); //$NON-NLS-1$
			}
		}

		buf.append(label);

		buf.append("</div>"); //$NON-NLS-1$
		return buf.toString();
	}

	public static void addAnnotations(StringBuilder buf, IJavaElement element, ITypeRoot editorInputElement, IRegion hoverRegion) {
		try {
			if (element instanceof IAnnotatable) {
				String annotationString= getAnnotations(element, editorInputElement, hoverRegion);
				if (annotationString != null) {
					buf.append("<div style='margin-bottom: 5px;'>"); //$NON-NLS-1$
					buf.append(annotationString);
					buf.append("</div>"); //$NON-NLS-1$
				}
			} else if (element instanceof IPackageFragment) {
				IPackageFragment pack= (IPackageFragment) element;
				ICompilationUnit cu= pack.getCompilationUnit(JavaModelUtil.PACKAGE_INFO_JAVA);
				if (cu.exists()) {
					IPackageDeclaration[] packDecls= cu.getPackageDeclarations();
					if (packDecls.length > 0) {
						addAnnotations(buf, packDecls[0], null, null);
					}
				} else {
					IOrdinaryClassFile classFile= pack.getOrdinaryClassFile(JavaModelUtil.PACKAGE_INFO_CLASS);
					if (classFile.exists()) {
						addAnnotations(buf, classFile.getType(), null, null);
					}
				}
			}
		} catch (JavaModelException | URISyntaxException e) {
			// no annotations this time...
			buf.append("<br>"); //$NON-NLS-1$
		}
	}

	private static String getAnnotations(IJavaElement element, ITypeRoot editorInputElement, IRegion hoverRegion) throws URISyntaxException, JavaModelException {
		if (!(element instanceof IPackageFragment)) {
			if (!(element instanceof IAnnotatable))
				return null;

			if (((IAnnotatable)element).getAnnotations().length == 0)
				return null;
		}

		IBinding binding= getHoveredNodeBinding(element, editorInputElement, hoverRegion);
		if (binding == null)
			return null;

		IAnnotationBinding[] annotations= binding.getAnnotations();
		if (annotations.length == 0)
			return null;

		StringBuffer buf= new StringBuffer();
		for (IAnnotationBinding annotation : annotations) {
			//TODO: skip annotations that don't have an @Documented annotation?
			addAnnotation(buf, annotation, true);
			buf.append("<br>"); //$NON-NLS-1$
		}

		return buf.toString();
	}

	private static IBinding resolveBinding(ASTNode node) {
		if (node instanceof SimpleName) {
			SimpleName simpleName= (SimpleName) node;
			// workaround for https://bugs.eclipse.org/62605 (constructor name resolves to type, not method)
			ASTNode normalized= ASTNodes.getNormalizedNode(simpleName);
			if (normalized.getLocationInParent() == ClassInstanceCreation.TYPE_PROPERTY) {
				ClassInstanceCreation cic= (ClassInstanceCreation) normalized.getParent();
				IMethodBinding constructorBinding= cic.resolveConstructorBinding();
				if (constructorBinding == null)
					return null;
				ITypeBinding declaringClass= constructorBinding.getDeclaringClass();
				if (!declaringClass.isAnonymous())
					return constructorBinding;
				ITypeBinding superTypeDeclaration= declaringClass.getSuperclass().getTypeDeclaration();
				return resolveSuperclassConstructor(superTypeDeclaration, constructorBinding);
			}
			return simpleName.resolveBinding();

		} else if (node instanceof SuperConstructorInvocation) {
			return ((SuperConstructorInvocation) node).resolveConstructorBinding();
		} else if (node instanceof ConstructorInvocation) {
			return ((ConstructorInvocation) node).resolveConstructorBinding();
		} else if (node instanceof LambdaExpression) {
			return ((LambdaExpression) node).resolveMethodBinding();
		} else {
			return null;
		}
	}

	private static IBinding resolveSuperclassConstructor(ITypeBinding superClassDeclaration, IMethodBinding constructor) {
		for (IMethodBinding method : superClassDeclaration.getDeclaredMethods()) {
			if (method.isConstructor() && constructor.isSubsignature(method))
				return method;
		}
		return null;
	}

	private static void addAnnotation(StringBuffer buf, IAnnotationBinding annotation, boolean addLinks) throws URISyntaxException {
		IJavaElement javaElement= annotation.getAnnotationType().getJavaElement();
		buf.append('@');
		if (javaElement == null || !addLinks) {
			buf.append(annotation.getName());
		} else {
			String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, javaElement);
			addLink(buf, uri, annotation.getName());
		}

		IMemberValuePairBinding[] mvPairs= annotation.getDeclaredMemberValuePairs();
		if (mvPairs.length > 0) {
			buf.append('(');
			for (int j= 0; j < mvPairs.length; j++) {
				if (j > 0) {
					buf.append(JavaElementLabels.COMMA_STRING);
				}
				IMemberValuePairBinding mvPair= mvPairs[j];
				if (addLinks) {
					String memberURI= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, mvPair.getMethodBinding().getJavaElement());
					addLink(buf, memberURI, mvPair.getName());
				} else {
					buf.append(mvPair.getName());
				}
				buf.append('=');
				addValue(buf, mvPair.getValue(), addLinks);
			}
			buf.append(')');
		}
	}

	private static void addValue(StringBuffer buf, Object value, boolean addLinks) throws URISyntaxException {
		// Note: To be bug-compatible with Javadoc from Java 5/6/7, we currently don't escape HTML tags in String-valued annotations.
		if (value instanceof ITypeBinding) {
			ITypeBinding typeBinding= (ITypeBinding)value;
			IJavaElement type= typeBinding.getJavaElement();
			if (type == null || !addLinks) {
				buf.append(typeBinding.getName());
			} else {
				String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, type);
				String name= type.getElementName();
				addLink(buf, uri, name);
			}
			buf.append(".class"); //$NON-NLS-1$

		} else if (value instanceof IVariableBinding) { // only enum constants
			IVariableBinding variableBinding= (IVariableBinding)value;
			IJavaElement variable= variableBinding.getJavaElement();
			if (variable == null || !addLinks) {
				buf.append(variableBinding.getName());
			} else {
				String uri= JavaElementLinks.createURI(JavaElementLinks.JAVADOC_SCHEME, variable);
				String name= variable.getElementName();
				addLink(buf, uri, name);
			}

		} else if (value instanceof IAnnotationBinding) {
			IAnnotationBinding annotationBinding= (IAnnotationBinding)value;
			addAnnotation(buf, annotationBinding, addLinks);

		} else if (value instanceof String) {
			buf.append(ASTNodes.getEscapedStringLiteral((String)value));

		} else if (value instanceof Character) {
			buf.append(ASTNodes.getEscapedCharacterLiteral(((Character)value)));

		} else if (value instanceof Object[]) {
			Object[] values= (Object[])value;
			buf.append('{');
			for (int i= 0; i < values.length; i++) {
				if (i > 0) {
					buf.append(JavaElementLabels.COMMA_STRING);
				}
				addValue(buf, values[i], addLinks);
			}
			buf.append('}');

		} else { // primitive types (except char) or null
			buf.append(String.valueOf(value));
		}
	}

	private static StringBuffer addLink(StringBuffer buf, String uri, String label) {
		return buf.append(JavaElementLinks.createLink(uri, label));
	}

	public static String getHexConstantValue(Object constantValue) {
		if (constantValue instanceof Character) {
			String constantResult= '\'' + constantValue.toString() + '\'';

			char charValue= ((Character) constantValue);
			String hexString= Integer.toHexString(charValue);
			StringBuilder hexResult= new StringBuilder("\\u"); //$NON-NLS-1$
			for (int i= hexString.length(); i < 4; i++) {
				hexResult.append('0');
			}
			hexResult.append(hexString);
			return formatWithHexValue(constantResult, hexResult.toString());

		} else if (constantValue instanceof Byte) {
			int byteValue= ((Byte) constantValue).intValue() & 0xFF;
			return formatWithHexValue(constantValue, "0x" + Integer.toHexString(byteValue)); //$NON-NLS-1$

		} else if (constantValue instanceof Short) {
			int shortValue= ((Short) constantValue).shortValue() & 0xFFFF;
			return formatWithHexValue(constantValue, "0x" + Integer.toHexString(shortValue)); //$NON-NLS-1$

		} else if (constantValue instanceof Integer) {
			int intValue= ((Integer) constantValue);
			return formatWithHexValue(constantValue, "0x" + Integer.toHexString(intValue)); //$NON-NLS-1$

		} else if (constantValue instanceof Long) {
			long longValue= ((Long) constantValue);
			return formatWithHexValue(constantValue, "0x" + Long.toHexString(longValue)); //$NON-NLS-1$

		} else {
			return constantValue.toString();
		}
	}

	public static Object getVariableBindingConstValue(ASTNode node, IField field) {
		if (node != null && node.getNodeType() == ASTNode.SIMPLE_NAME) {
			IBinding binding= ((SimpleName) node).resolveBinding();
			if (binding != null && binding.getKind() == IBinding.VARIABLE) {
				IVariableBinding variableBinding= (IVariableBinding) binding;
				if (field.equals(variableBinding.getJavaElement())) {
					return variableBinding.getConstantValue();
				}
			}
		}
		return null;
	}


}
