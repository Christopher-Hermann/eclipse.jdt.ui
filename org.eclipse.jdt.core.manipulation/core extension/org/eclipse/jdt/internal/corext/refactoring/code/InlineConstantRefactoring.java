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
 *     Nikolay Metchev <nikolaymetchev@gmail.com> - [inline] problem with fields from generic types - https://bugs.eclipse.org/218431
 *     Microsoft Corporation - copied to jdt.core.manipulation
 *     Microsoft Corporation - read formatting options from the compilation unit
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.RangeMarker;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.TextUtilities;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringDescriptor;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.TypeLocation;
import org.eclipse.jdt.core.manipulation.ImportReferencesCollector;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.core.refactoring.IJavaRefactorings;
import org.eclipse.jdt.core.refactoring.descriptors.InlineConstantDescriptor;
import org.eclipse.jdt.core.refactoring.descriptors.JavaRefactoringDescriptor;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.core.manipulation.JavaElementLabelsCore;
import org.eclipse.jdt.internal.core.manipulation.JavaManipulationPlugin;
import org.eclipse.jdt.internal.core.manipulation.dom.NecessaryParenthesesChecker;
import org.eclipse.jdt.internal.core.manipulation.util.Strings;
import org.eclipse.jdt.internal.core.refactoring.descriptors.RefactoringSignatureDescriptorFactory;
import org.eclipse.jdt.internal.corext.CorextCore;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.HierarchicalASTVisitor;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.dom.fragments.ASTFragmentFactory;
import org.eclipse.jdt.internal.corext.dom.fragments.IExpressionFragment;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.JDTRefactoringDescriptorComment;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringArguments;
import org.eclipse.jdt.internal.corext.refactoring.JavaRefactoringDescriptorUtil;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine2;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusCodes;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationRefactoringChange;
import org.eclipse.jdt.internal.corext.refactoring.structure.ASTNodeSearchUtil;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TightSourceRangeComputer;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.util.Progress;

public class InlineConstantRefactoring extends Refactoring {

	private static final String ATTRIBUTE_REPLACE= "replace"; //$NON-NLS-1$
	private static final String ATTRIBUTE_REMOVE= "remove";	 //$NON-NLS-1$

	private static class InlineTargetCompilationUnit {

		private static class InitializerTraversal extends HierarchicalASTVisitor {

			private static boolean areInSameType(ASTNode one, ASTNode other) {
				ASTNode onesContainer= getContainingTypeDeclaration(one);
				ASTNode othersContainer= getContainingTypeDeclaration(other);

				if (onesContainer == null || othersContainer == null)
					return false;

				ITypeBinding onesContainerBinding= getTypeBindingForTypeDeclaration(onesContainer);
				if (one instanceof SimpleName) {
					IBinding oneTypeBinding= ((SimpleName)one).resolveBinding();
					if (oneTypeBinding instanceof IVariableBinding) {
						ITypeBinding onesDeclaringClassBinding= ((IVariableBinding)oneTypeBinding).getDeclaringClass();
						if (onesDeclaringClassBinding != null) {
							onesContainerBinding= onesDeclaringClassBinding;
						}
					}
				} else if (one instanceof MethodInvocation) {
					IMethodBinding oneMethodBinding= ((MethodInvocation)one).resolveMethodBinding();
					if (oneMethodBinding != null) {
						ITypeBinding onesDeclaringClassBinding= oneMethodBinding.getDeclaringClass();
						if (onesDeclaringClassBinding != null) {
							onesContainerBinding= onesDeclaringClassBinding;
						}
					}
				}
				ITypeBinding othersContainerBinding= getTypeBindingForTypeDeclaration(othersContainer);


				Assert.isNotNull(othersContainerBinding);

				String onesKey= onesContainerBinding.getKey();
				String othersKey= othersContainerBinding.getKey();

				if (onesKey == null || othersKey == null)
					return false;

				return onesKey.equals(othersKey);
			}

			private static boolean isStaticAccess(SimpleName memberName) {
				IBinding binding= memberName.resolveBinding();
				Assert.isTrue(binding instanceof IVariableBinding || binding instanceof IMethodBinding || binding instanceof ITypeBinding);

				if (binding instanceof ITypeBinding)
					return true;

				if (binding instanceof IVariableBinding)
					return ((IVariableBinding) binding).isField();

				int modifiers= binding.getModifiers();
				return Modifier.isStatic(modifiers);
			}

			private static ASTNode getContainingTypeDeclaration(ASTNode node) {
				while (node != null && !(node instanceof AbstractTypeDeclaration) && !(node instanceof AnonymousClassDeclaration)) {
					node= node.getParent();
				}
				return node;
			}

			private static ITypeBinding getTypeBindingForTypeDeclaration(ASTNode declaration) {
				if (declaration instanceof AnonymousClassDeclaration)
					return ((AnonymousClassDeclaration) declaration).resolveBinding();

				if (declaration instanceof AbstractTypeDeclaration)
					return ((AbstractTypeDeclaration) declaration).resolveBinding();

				Assert.isTrue(false);
				return null;
			}

			private final Expression fInitializer;
			private ASTRewrite fInitializerRewrite;
			private final HashSet<SimpleName> fStaticImportsInInitializer2;

			// cache:
			private Set<String> fNamesDeclaredLocallyAtNewLocation;

			private final Expression fNewLocation;
			private final HashSet<SimpleName> fStaticImportsInReference;
			private final CompilationUnitRewrite fNewLocationCuRewrite;
			private final ImportRewriteContext fNewLocationContext;

			public InitializerTraversal(Expression initializer, HashSet<SimpleName> staticImportsInInitializer, Expression newLocation, HashSet<SimpleName> staticImportsInReference, CompilationUnitRewrite newLocationCuRewrite) {
				fInitializer= initializer;
				fInitializerRewrite= ASTRewrite.create(initializer.getAST());
				fStaticImportsInInitializer2= staticImportsInInitializer;

				fNewLocation= newLocation;
				fStaticImportsInReference= staticImportsInReference;
				fNewLocationCuRewrite= newLocationCuRewrite;
				fNewLocationContext= new ContextSensitiveImportRewriteContext(fNewLocation, fNewLocationCuRewrite.getImportRewrite());

				perform(initializer);
			}

			/**
			 * @param scope not a TypeDeclaration
			 * @return Set containing Strings representing simple names
			 */
			private Set<String> getLocallyDeclaredNames(BodyDeclaration scope) {
				Assert.isTrue(!(scope instanceof AbstractTypeDeclaration));

				final Set<String> result= new HashSet<>();

				if (scope instanceof FieldDeclaration)
					return result;

				scope.accept(new HierarchicalASTVisitor() {

					@Override
					public boolean visit(AbstractTypeDeclaration node) {
						Assert.isTrue(node.getParent() instanceof TypeDeclarationStatement);

						result.add(node.getName().getIdentifier());
						return false;
					}

					@Override
					public boolean visit(AnonymousClassDeclaration anonDecl) {
						return false;
					}

					@Override
					public boolean visit(VariableDeclaration varDecl) {
						result.add(varDecl.getName().getIdentifier());
						return false;
					}
				});
				return result;
			}

			public ASTRewrite getInitializerRewrite() {
				return fInitializerRewrite;
			}

			private void perform(Expression initializer) {
				initializer.accept(this);
				addExplicitTypeArgumentsIfNecessary(initializer);
			}

			private void addExplicitTypeArgumentsIfNecessary(@SuppressWarnings("unused") Expression invocation) {
// TODO: this requires additional logic, for example, mark these locations and recompile new source
//				if (Invocations.isResolvedTypeInferredFromExpectedType(invocation)) {
//					ASTNode referenceContext= fNewLocation.getParent();
//					if (!(referenceContext instanceof VariableDeclarationFragment)
//							&& !(referenceContext instanceof SingleVariableDeclaration)
//							&& !(referenceContext instanceof Assignment)) {
//						ListRewrite typeArgsRewrite= Invocations.getInferredTypeArgumentsRewrite(fInitializerRewrite, invocation);
//						for (ITypeBinding typeArgument2 : Invocations.getInferredTypeArguments(invocation)) {
//							Type typeArgument= fNewLocationCuRewrite.getImportRewrite().addImport(typeArgument2, fNewLocationCuRewrite.getAST(), fNewLocationContext, TypeLocation.TYPE_ARGUMENT);
//							fNewLocationCuRewrite.getImportRemover().registerAddedImports(typeArgument);
//							typeArgsRewrite.insertLast(typeArgument, null);
//						}
//
//						if (invocation instanceof MethodInvocation) {
//							MethodInvocation methodInvocation= (MethodInvocation) invocation;
//							Expression expression= methodInvocation.getExpression();
//							if (expression == null) {
//								IMethodBinding methodBinding= methodInvocation.resolveMethodBinding();
//								if (methodBinding != null) {
//									expression= fNewLocationCuRewrite.getAST().newName(fNewLocationCuRewrite.getImportRewrite().addImport(methodBinding.getDeclaringClass().getTypeDeclaration(), fNewLocationContext));
//									fInitializerRewrite.set(invocation, MethodInvocation.EXPRESSION_PROPERTY, expression, null);
//								}
//							}
//						}
//					}
//				}
			}

			@Override
			public boolean visit(FieldAccess fieldAccess) {
				fieldAccess.getExpression().accept(this);
				return false;
			}

			@Override
			public boolean visit(MethodInvocation invocation) {
				if (invocation.getExpression() == null)
					qualifyUnqualifiedMemberNameIfNecessary(invocation.getName());
				else
					invocation.getExpression().accept(this);

				for (Iterator<Expression> it= invocation.arguments().iterator(); it.hasNext();)
					it.next().accept(this);

				return false;
			}

			@Override
			public boolean visit(Name name) {
				StructuralPropertyDescriptor locationInParent= name.getLocationInParent();
				if (locationInParent == ExpressionMethodReference.NAME_PROPERTY
						|| locationInParent == TypeMethodReference.NAME_PROPERTY
						|| locationInParent == SuperMethodReference.NAME_PROPERTY) {
					return false;
				}

				SimpleName leftmost= getLeftmost(name);

				IBinding leftmostBinding= leftmost.resolveBinding();
				if (leftmostBinding instanceof IVariableBinding || leftmostBinding instanceof IMethodBinding || leftmostBinding instanceof ITypeBinding) {
					if (shouldUnqualify(leftmost))
						unqualifyMemberName(leftmost);
					else
						qualifyUnqualifiedMemberNameIfNecessary(leftmost);
				}

				if (leftmostBinding instanceof ITypeBinding) {
					String addedImport= fNewLocationCuRewrite.getImportRewrite().addImport((ITypeBinding)leftmostBinding, fNewLocationContext);
					fNewLocationCuRewrite.getImportRemover().registerAddedImport(addedImport);
				}

				return false;
			}

			private void qualifyUnqualifiedMemberNameIfNecessary(SimpleName memberName) {
				if (shouldQualify(memberName))
					qualifyMemberName(memberName);
			}

			private boolean shouldUnqualify(SimpleName memberName) {
				if (areInSameType(memberName, fNewLocation))
					return ! mayBeShadowedByLocalDeclaration(memberName);

				return false;
			}

			private void unqualifyMemberName(SimpleName memberName) {
				if (doesParentQualify(memberName))
					fInitializerRewrite.replace(memberName.getParent(), memberName, null);
			}

			private boolean shouldQualify(SimpleName memberName) {
				if (! areInSameType(fInitializer, fNewLocation))
					return true;

				return mayBeShadowedByLocalDeclaration(memberName);
			}

			private boolean mayBeShadowedByLocalDeclaration(SimpleName memberName) {
				return getNamesDeclaredLocallyAtNewLocation().contains(memberName.getIdentifier());
			}

			private Set<String> getNamesDeclaredLocallyAtNewLocation() {
				if (fNamesDeclaredLocallyAtNewLocation != null)
					return fNamesDeclaredLocallyAtNewLocation;

				BodyDeclaration enclosingBodyDecl= ASTNodes.getParent(fNewLocation, BodyDeclaration.class);
				Assert.isTrue(!(enclosingBodyDecl instanceof AbstractTypeDeclaration));

				return fNamesDeclaredLocallyAtNewLocation= getLocallyDeclaredNames(enclosingBodyDecl);
			}

			private void qualifyMemberName(SimpleName memberName) {
				if (isStaticAccess(memberName)) {
					IBinding memberBinding= memberName.resolveBinding();

					if (memberBinding instanceof IVariableBinding || memberBinding instanceof IMethodBinding) {
						if (fStaticImportsInReference.contains(fNewLocation) // Use static import if reference location used static import
								|| fStaticImportsInInitializer2.contains(memberName)) { // Use static import if already imported statically in initializer
							importStatically(memberName, memberBinding);
							return;
						}
					}
					qualifyToTopLevelClass(memberName); //otherwise: qualify and import non-static
				}
			}

			private void importStatically(SimpleName toImport, IBinding binding) {
				String newName= fNewLocationCuRewrite.getImportRewrite().addStaticImport(binding);
				fNewLocationCuRewrite.getImportRemover().registerAddedStaticImport(binding);

				Name newReference= ASTNodeFactory.newName(fInitializerRewrite.getAST(), newName);
				fInitializerRewrite.replace(toImport, newReference, null);
			}

			private void qualifyToTopLevelClass(SimpleName toQualify) {
				ITypeBinding declaringClass= getDeclaringClassBinding(toQualify);
				if (declaringClass == null)
					return;

				Type newQualification= fNewLocationCuRewrite.getImportRewrite().addImport(declaringClass.getErasure(), fInitializerRewrite.getAST(), fNewLocationContext);
				fNewLocationCuRewrite.getImportRemover().registerAddedImports(newQualification);

				SimpleName newToQualify= (SimpleName) fInitializerRewrite.createMoveTarget(toQualify);
				Type newType= fInitializerRewrite.getAST().newQualifiedType(newQualification, newToQualify);
				fInitializerRewrite.replace(toQualify, newType, null);
			}

			private static ITypeBinding getDeclaringClassBinding(SimpleName memberName) {

				IBinding binding= memberName.resolveBinding();
				if (binding instanceof IMethodBinding)
					return ((IMethodBinding) binding).getDeclaringClass();

				if (binding instanceof IVariableBinding)
					return ((IVariableBinding) binding).getDeclaringClass();

				if (binding instanceof ITypeBinding)
					return ((ITypeBinding) binding).getDeclaringClass();

				Assert.isTrue(false);
				return null;

			}

		}

		private final Expression fInitializer;
		private final ICompilationUnit fInitializerUnit;
		private final VariableDeclarationFragment fOriginalDeclaration;

		/** The references in this compilation unit, represented as AST Nodes in the parsed representation of the compilation unit */
		private final Expression[] fReferences;
		private final VariableDeclarationFragment fDeclarationToRemove;
		private final CompilationUnitRewrite fCuRewrite;
		private final TightSourceRangeComputer fSourceRangeComputer;
		private final HashSet<SimpleName> fStaticImportsInInitializer;

		private InlineTargetCompilationUnit(CompilationUnitRewrite cuRewrite, Name[] references, InlineConstantRefactoring refactoring, HashSet<SimpleName> staticImportsInInitializer) {
			fInitializer= refactoring.getInitializer();
			fInitializerUnit= refactoring.getDeclaringCompilationUnit();

			fCuRewrite= cuRewrite;
			fSourceRangeComputer= new TightSourceRangeComputer();
			fCuRewrite.getASTRewrite().setTargetSourceRangeComputer(fSourceRangeComputer);
			if (refactoring.getRemoveDeclaration() && refactoring.getReplaceAllReferences() && cuRewrite.getCu().equals(fInitializerUnit))
				fDeclarationToRemove= refactoring.getDeclaration();
			else
				fDeclarationToRemove= null;

			fOriginalDeclaration= refactoring.getDeclaration();

			fReferences= new Expression[references.length];
			for (int i= 0; i < references.length; i++)
				fReferences[i]= getQualifiedReference(references[i]);

			fStaticImportsInInitializer= staticImportsInInitializer;
		}

		private static Expression getQualifiedReference(Name fieldName) {
			if (doesParentQualify(fieldName))
				return (Expression) fieldName.getParent();

			return fieldName;
		}

		private static boolean doesParentQualify(Name fieldName) {
			ASTNode parent= fieldName.getParent();
			Assert.isNotNull(parent);

			if (parent instanceof FieldAccess && ((FieldAccess) parent).getName() == fieldName)
				return true;

			if (parent instanceof QualifiedName && ((QualifiedName) parent).getName() == fieldName)
				return true;

			if (parent instanceof MethodInvocation && ((MethodInvocation) parent).getName() == fieldName)
				return true;

			return false;
		}

		public CompilationUnitChange getChange() throws CoreException {
			for (Expression fReference : fReferences)
				inlineReference(fReference);

			removeConstantDeclarationIfNecessary();

			return fCuRewrite.createChange(true);
		}

		private void inlineReference(Expression reference) throws CoreException {
			ImportDeclaration importDecl= ASTNodes.getParent(reference, ImportDeclaration.class);
			if (importDecl != null) {
				fCuRewrite.getImportRemover().registerInlinedStaticImport(importDecl);
				return;
			}

			String modifiedInitializer= prepareInitializerForLocation(reference);
			if (modifiedInitializer == null)
				return;

			TextEditGroup msg= fCuRewrite.createGroupDescription(RefactoringCoreMessages.InlineConstantRefactoring_Inline);

			Expression newReference;
			boolean isStringPlaceholder= false;

			AST ast= fCuRewrite.getAST();
			ITypeBinding explicitCast= ASTNodes.getExplicitCast(fInitializer, reference);
			if (explicitCast != null) {
				CastExpression cast= ast.newCastExpression();
				Expression modifiedInitializerExpr= (Expression) fCuRewrite.getASTRewrite().createStringPlaceholder(modifiedInitializer, reference.getNodeType());
				if (NecessaryParenthesesChecker.needsParentheses(fInitializer, cast, CastExpression.EXPRESSION_PROPERTY)) {
					ParenthesizedExpression parenthesized= ast.newParenthesizedExpression();
					parenthesized.setExpression(modifiedInitializerExpr);
					modifiedInitializerExpr= parenthesized;
				}
				cast.setExpression(modifiedInitializerExpr);
				ImportRewriteContext context= new ContextSensitiveImportRewriteContext(reference, fCuRewrite.getImportRewrite());
				cast.setType(fCuRewrite.getImportRewrite().addImport(explicitCast, ast, context, TypeLocation.CAST));
				newReference= cast;

			} else if (fInitializer instanceof ArrayInitializer) {
				ArrayCreation arrayCreation= ast.newArrayCreation();
				ArrayType arrayType= (ArrayType) ASTNodeFactory.newType(ast, fOriginalDeclaration);
				arrayCreation.setType(arrayType);

				ArrayInitializer newArrayInitializer= (ArrayInitializer) fCuRewrite.getASTRewrite().createStringPlaceholder(modifiedInitializer,
						ASTNode.ARRAY_INITIALIZER);
				arrayCreation.setInitializer(newArrayInitializer);
				newReference= arrayCreation;

				ITypeBinding typeToAddToImport= ASTNodes.getType(fOriginalDeclaration).resolveBinding();
				ImportRewriteContext context= new ContextSensitiveImportRewriteContext(reference, fCuRewrite.getImportRewrite());
				fCuRewrite.getImportRewrite().addImport(typeToAddToImport, context);
				fCuRewrite.getImportRemover().registerAddedImport(typeToAddToImport.getName());

			} else {
				newReference= (Expression) fCuRewrite.getASTRewrite().createStringPlaceholder(modifiedInitializer, reference.getNodeType());
				isStringPlaceholder= true;
			}

			if (NecessaryParenthesesChecker.needsParentheses((isStringPlaceholder ? fInitializer : newReference), reference.getParent(), reference.getLocationInParent())) {
				ParenthesizedExpression parenthesized= ast.newParenthesizedExpression();
				parenthesized.setExpression(newReference);
				newReference= parenthesized;
			}
			fCuRewrite.getASTRewrite().replace(reference, newReference, msg);
			fSourceRangeComputer.addTightSourceNode(reference);
			fCuRewrite.getImportRemover().registerRemovedNode(reference);
		}

		private String prepareInitializerForLocation(Expression location) throws CoreException {
			HashSet<SimpleName> staticImportsInReference= new HashSet<>();
			final IJavaProject project= fCuRewrite.getCu().getJavaProject();
			ImportReferencesCollector.collect(location, project, null, new ArrayList<>(), staticImportsInReference);

			InitializerTraversal traversal= new InitializerTraversal(fInitializer, fStaticImportsInInitializer, location, staticImportsInReference, fCuRewrite);
			ASTRewrite initializerRewrite= traversal.getInitializerRewrite();
			IDocument document= new Document(fInitializerUnit.getBuffer().getContents()); // could reuse document when generating and applying undo edits

			final RangeMarker marker= new RangeMarker(fInitializer.getStartPosition(), fInitializer.getLength());
			TextEdit[] rewriteEdits= initializerRewrite.rewriteAST(document, fInitializerUnit.getOptions(true)).removeChildren();
			marker.addChildren(rewriteEdits);
			try {
				marker.apply(document, TextEdit.UPDATE_REGIONS);
				String rewrittenInitializer= document.get(marker.getOffset(), marker.getLength());
				IRegion region= document.getLineInformation(document.getLineOfOffset(marker.getOffset()));
				int oldIndent= Strings.computeIndentUnits(document.get(region.getOffset(), region.getLength()), fInitializerUnit);
				return Strings.changeIndent(rewrittenInitializer, oldIndent, fInitializerUnit, "", TextUtilities.getDefaultLineDelimiter(document)); //$NON-NLS-1$
			} catch (MalformedTreeException | BadLocationException e) {
				JavaManipulationPlugin.log(e);
			}
			return fInitializerUnit.getBuffer().getText(fInitializer.getStartPosition(), fInitializer.getLength());
		}

		private void removeConstantDeclarationIfNecessary() {
			if (fDeclarationToRemove == null)
				return;

			FieldDeclaration parentDeclaration= (FieldDeclaration) fDeclarationToRemove.getParent();
			ASTNode toRemove;
			if (parentDeclaration.fragments().size() == 1)
				toRemove= parentDeclaration;
			else
				toRemove= fDeclarationToRemove;

			TextEditGroup msg= fCuRewrite.createGroupDescription(RefactoringCoreMessages.InlineConstantRefactoring_remove_declaration);
			fCuRewrite.getASTRewrite().remove(toRemove, msg);
			fCuRewrite.getImportRemover().registerRemovedNode(toRemove);
		}
	}

	// ---- End InlineTargetCompilationUnit ----------------------------------------------------------------------------------------------

	private static SimpleName getLeftmost(Name name) {
		if (name instanceof SimpleName)
			return (SimpleName) name;

		return getLeftmost(((QualifiedName) name).getQualifier());
	}

	private int fSelectionStart;
	private int fSelectionLength;

	private ICompilationUnit fSelectionCu;
	private CompilationUnitRewrite fSelectionCuRewrite;
	private Name fSelectedConstantName;

	private IField fField;
	private CompilationUnitRewrite fDeclarationCuRewrite;
	private VariableDeclarationFragment fDeclaration;
	private SearchResultGroup[] fReferences;
	private boolean fDeclarationSelected;
	private boolean fDeclarationSelectedChecked= false;
	private boolean fInitializerAllStaticFinal;
	private boolean fInitializerChecked= false;

	private boolean fRemoveDeclaration= false;
	private boolean fReplaceAllReferences= true;

	private CompilationUnitChange[] fChanges;

	/**
	 * Creates a new inline constant refactoring.
	 * <p>
	 * This constructor is only used by <code>DelegateCreator</code>.
	 * </p>
	 *
	 * @param field the field to inline
	 */
	public InlineConstantRefactoring(IField field) {
		Assert.isNotNull(field);
		Assert.isTrue(!field.isBinary());
		fField= field;
	}

	/**
	 * Creates a new inline constant refactoring.
	 *
	 * @param unit the compilation unit, or <code>null</code> if invoked by scripting
	 * @param node the compilation unit node, or <code>null</code> if invoked by scripting
	 * @param selectionStart the start of the selection
	 * @param selectionLength the length of the selection
	 */
	public InlineConstantRefactoring(ICompilationUnit unit, CompilationUnit node, int selectionStart, int selectionLength) {
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);
		fSelectionCu= unit;
		fSelectionStart= selectionStart;
		fSelectionLength= selectionLength;
		if (unit != null)
			initialize(unit, node);
	}

    public InlineConstantRefactoring(JavaRefactoringArguments arguments, RefactoringStatus status) {
   		this(null, null, 0, 0);
   		RefactoringStatus initializeStatus= initialize(arguments);
   		status.merge(initializeStatus);
    }

	private void initialize(ICompilationUnit cu, CompilationUnit node) {
		fSelectionCuRewrite= new CompilationUnitRewrite(cu, node);
		fSelectedConstantName= findConstantNameNode();
	}

	private Name findConstantNameNode() {
		ASTNode node= NodeFinder.perform(fSelectionCuRewrite.getRoot(), fSelectionStart, fSelectionLength);
		if (node == null)
			return null;
		if (node instanceof FieldAccess)
			node= ((FieldAccess) node).getName();
		if (!(node instanceof Name))
			return null;
		Name name= (Name) node;
		IBinding binding= name.resolveBinding();
		if (!(binding instanceof IVariableBinding))
			return null;
		IVariableBinding variableBinding= (IVariableBinding) binding;
		if (!variableBinding.isField() || variableBinding.isEnumConstant())
			return null;
		int modifiers= binding.getModifiers();
		if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers))
			return null;

		return name;
	}

	public RefactoringStatus checkStaticFinalConstantNameSelected() {
		if (fSelectedConstantName == null)
			return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.InlineConstantRefactoring_static_final_field, null, CorextCore.getPluginId(), RefactoringStatusCodes.NOT_STATIC_FINAL_SELECTED, null);

		return new RefactoringStatus();
	}

	@Override
	public String getName() {
		return RefactoringCoreMessages.InlineConstantRefactoring_name;
	}

	/**
	 * Returns the field to inline, or null if the field could not be found or
	 * {@link #checkInitialConditions(IProgressMonitor)} has not been called yet.
	 *
	 * @return the field, or <code>null</code>
	 */
	public IJavaElement getField() {
		return fField;
	}

	@Override
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 3); //$NON-NLS-1$

			if (!fSelectionCu.isStructureKnown())
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.InlineConstantRefactoring_syntax_errors, null, CorextCore.getPluginId(), RefactoringStatusCodes.SYNTAX_ERRORS, null);

			RefactoringStatus result= checkStaticFinalConstantNameSelected();
			if (result.hasFatalError())
				return result;

			result.merge(findField());
			if (result.hasFatalError())
				return result;
			pm.worked(1);

			result.merge(findDeclaration());
			if (result.hasFatalError())
				return result;
			pm.worked(1);

			result.merge(checkInitializer());
			if (result.hasFatalError())
				return result;
			pm.worked(1);

			return result;

		} finally {
			pm.done();
		}
	}

	private RefactoringStatus findField() {
		fField= (IField) fSelectedConstantName.resolveBinding().getJavaElement();
		if (fField != null && ! fField.exists())
			return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.InlineConstantRefactoring_local_anonymous_unsupported, null, CorextCore.getPluginId(), RefactoringStatusCodes.LOCAL_AND_ANONYMOUS_NOT_SUPPORTED, null);

		return null;
	}
	private RefactoringStatus findDeclaration() throws JavaModelException {
		fDeclarationSelectedChecked= true;
		fDeclarationSelected= false;
		ASTNode parent= fSelectedConstantName.getParent();
		if (parent instanceof VariableDeclarationFragment) {
			VariableDeclarationFragment parentDeclaration= (VariableDeclarationFragment) parent;
			if (parentDeclaration.getName() == fSelectedConstantName) {
				fDeclarationSelected= true;
				fDeclarationCuRewrite= fSelectionCuRewrite;
				fDeclaration= (VariableDeclarationFragment) fSelectedConstantName.getParent();
				return null;
			}
		}

		VariableDeclarationFragment declaration= (VariableDeclarationFragment) fSelectionCuRewrite.getRoot().findDeclaringNode(fSelectedConstantName.resolveBinding());
		if (declaration != null) {
			fDeclarationCuRewrite= fSelectionCuRewrite;
			fDeclaration= declaration;
			return null;
		}

		if (fField.getCompilationUnit() == null)
			return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.InlineConstantRefactoring_binary_file, null, CorextCore.getPluginId(), RefactoringStatusCodes.DECLARED_IN_CLASSFILE, null);

		fDeclarationCuRewrite= new CompilationUnitRewrite(fField.getCompilationUnit());
		fDeclaration= ASTNodeSearchUtil.getFieldDeclarationFragmentNode(fField, fDeclarationCuRewrite.getRoot());
		return null;
	}

	private RefactoringStatus checkInitializer() {
		Expression initializer= getInitializer();
		if (initializer == null)
			return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.InlineConstantRefactoring_blank_finals, null, CorextCore.getPluginId(), RefactoringStatusCodes.CANNOT_INLINE_BLANK_FINAL, null);

		fInitializerAllStaticFinal= ConstantChecks.isStaticFinalConstant((IExpressionFragment) ASTFragmentFactory.createFragmentForFullSubtree(initializer));
		fInitializerChecked= true;
		return new RefactoringStatus();
	}

	private VariableDeclarationFragment getDeclaration() {
		return fDeclaration;
	}

	private Expression getInitializer() {
		return fDeclaration.getInitializer();
	}

	private ICompilationUnit getDeclaringCompilationUnit() {
		return fField.getCompilationUnit();
	}

	@Override
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		pm.beginTask("", 3); //$NON-NLS-1$

		try {
			fSelectionCuRewrite.clearASTAndImportRewrites();
			fDeclarationCuRewrite.clearASTAndImportRewrites();
			List<CompilationUnitChange>changes= new ArrayList<>();
			HashSet<SimpleName> staticImportsInInitializer= new HashSet<>();
			ImportReferencesCollector.collect(getInitializer(), fField.getJavaProject(), null, new ArrayList<>(), staticImportsInInitializer);

			if (getReplaceAllReferences()) {
				for (SearchResultGroup group : getReferences(pm, result)) {
					if (pm.isCanceled())
						throw new OperationCanceledException();
					ICompilationUnit cu= group.getCompilationUnit();

					CompilationUnitRewrite cuRewrite= getCuRewrite(cu);
					Name[] references= extractReferenceNodes(group.getSearchResults(), cuRewrite.getRoot());
					InlineTargetCompilationUnit targetCompilationUnit= new InlineTargetCompilationUnit(
							cuRewrite, references, this, staticImportsInInitializer);
					CompilationUnitChange change= targetCompilationUnit.getChange();
					if (change != null)
						changes.add(change);
				}

			} else {
				Assert.isTrue(! isDeclarationSelected());
				InlineTargetCompilationUnit targetForOnlySelectedReference= new InlineTargetCompilationUnit(
						fSelectionCuRewrite, new Name[] { fSelectedConstantName }, this, staticImportsInInitializer);
				CompilationUnitChange change= targetForOnlySelectedReference.getChange();
				if (change != null)
					changes.add(change);
			}

			if (result.hasFatalError())
				return result;

			if (getRemoveDeclaration() && getReplaceAllReferences()) {
				boolean declarationRemoved= false;
				for (CompilationUnitChange change : changes) {
					if (change.getCompilationUnit().equals(fDeclarationCuRewrite.getCu())) {
						declarationRemoved= true;
						break;
					}
				}
				if (! declarationRemoved) {
					InlineTargetCompilationUnit targetForDeclaration= new InlineTargetCompilationUnit(fDeclarationCuRewrite, new Name[0], this, staticImportsInInitializer);
					CompilationUnitChange change= targetForDeclaration.getChange();
					if (change != null)
						changes.add(change);
				}
			}

			ICompilationUnit[] cus= new ICompilationUnit[changes.size()];
			for (int i= 0; i < changes.size(); i++) {
				CompilationUnitChange change= changes.get(i);
				cus[i]= change.getCompilationUnit();
			}
			result.merge(Checks.validateModifiesFiles(ResourceUtil.getFiles(cus), getValidationContext(), pm));

			pm.worked(1);

			fChanges= changes.toArray(new CompilationUnitChange[changes.size()]);

			return result;

		} finally {
			pm.done();
		}
	}

	private Name[] extractReferenceNodes(SearchMatch[] searchResults, CompilationUnit cuNode) {
		Name[] references= new Name[searchResults.length];
		for (int i= 0; i < searchResults.length; i++)
			references[i]= (Name) NodeFinder.perform(cuNode, searchResults[i].getOffset(), searchResults[i].getLength());
		return references;
	}

	private CompilationUnitRewrite getCuRewrite(ICompilationUnit cu) {
		CompilationUnitRewrite cuRewrite;
		if (cu.equals(fSelectionCu)) {
			cuRewrite= fSelectionCuRewrite;
		} else if (cu.equals(fField.getCompilationUnit())) {
			cuRewrite= fDeclarationCuRewrite;
		} else {
			cuRewrite= new CompilationUnitRewrite(cu);
		}
		return cuRewrite;
	}

	private SearchResultGroup[] findReferences(IProgressMonitor pm, RefactoringStatus status) throws JavaModelException {
		SearchPattern pattern= SearchPattern.createPattern(fField, IJavaSearchConstants.REFERENCES);
		if (pattern == null) {
			return new SearchResultGroup[0];
		}
		final RefactoringSearchEngine2 engine= new RefactoringSearchEngine2(pattern);
		engine.setFiltering(true, true);
		engine.setScope(RefactoringScopeFactory.create(fField));
		engine.setStatus(status);
		engine.setRequestor(match -> match.isInsideDocComment() ? null : match);
		engine.searchPattern(Progress.subMonitor(pm, 1));
		return (SearchResultGroup[]) engine.getResults();
	}

	@Override
	public Change createChange(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask(RefactoringCoreMessages.InlineConstantRefactoring_preview, 2);
			final Map<String, String> arguments= new HashMap<>();
			String project= null;
			IJavaProject javaProject= fSelectionCu.getJavaProject();
			if (javaProject != null)
				project= javaProject.getElementName();
			int flags= RefactoringDescriptor.STRUCTURAL_CHANGE | JavaRefactoringDescriptor.JAR_REFACTORING | JavaRefactoringDescriptor.JAR_SOURCE_ATTACHMENT;
			try {
				if (!Flags.isPrivate(fField.getFlags()))
					flags|= RefactoringDescriptor.MULTI_CHANGE;
			} catch (JavaModelException exception) {
				JavaManipulationPlugin.log(exception);
			}
			final String description= Messages.format(RefactoringCoreMessages.InlineConstantRefactoring_descriptor_description_short, JavaElementLabelsCore.getElementLabel(fField, JavaElementLabelsCore.ALL_DEFAULT));
			final String header= Messages.format(RefactoringCoreMessages.InlineConstantRefactoring_descriptor_description, new String[] { JavaElementLabelsCore.getElementLabel(fField, JavaElementLabelsCore.ALL_FULLY_QUALIFIED), JavaElementLabelsCore.getElementLabel(fField.getParent(), JavaElementLabelsCore.ALL_FULLY_QUALIFIED)});
			final JDTRefactoringDescriptorComment comment= new JDTRefactoringDescriptorComment(project, this, header);
			comment.addSetting(Messages.format(RefactoringCoreMessages.InlineConstantRefactoring_original_pattern, JavaElementLabelsCore.getElementLabel(fField, JavaElementLabelsCore.ALL_FULLY_QUALIFIED)));
			if (fRemoveDeclaration)
				comment.addSetting(RefactoringCoreMessages.InlineConstantRefactoring_remove_declaration);
			if (fReplaceAllReferences)
				comment.addSetting(RefactoringCoreMessages.InlineConstantRefactoring_replace_references);
			final InlineConstantDescriptor descriptor= RefactoringSignatureDescriptorFactory.createInlineConstantDescriptor(project, description, comment.asString(), arguments, flags);
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT, JavaRefactoringDescriptorUtil.elementToHandle(project, fSelectionCu));
			arguments.put(JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION, Integer.toString(fSelectionStart) + " " + Integer.toString(fSelectionLength)); //$NON-NLS-1$
			arguments.put(ATTRIBUTE_REMOVE, Boolean.toString(fRemoveDeclaration));
			arguments.put(ATTRIBUTE_REPLACE, Boolean.toString(fReplaceAllReferences));
			return new DynamicValidationRefactoringChange(descriptor, RefactoringCoreMessages.InlineConstantRefactoring_inline, fChanges);
		} finally {
			pm.done();
			fChanges= null;
		}
	}

	private void checkInvariant() {
		if (isDeclarationSelected())
			Assert.isTrue(fReplaceAllReferences);
	}

	public boolean getRemoveDeclaration() {
		return fRemoveDeclaration;
	}

	public boolean getReplaceAllReferences() {
		checkInvariant();
		return fReplaceAllReferences;
	}

	public boolean isDeclarationSelected() {
		Assert.isTrue(fDeclarationSelectedChecked);
		return fDeclarationSelected;
	}

	public boolean isInitializerAllStaticFinal() {
		Assert.isTrue(fInitializerChecked);
		return fInitializerAllStaticFinal;
	}

	public void setRemoveDeclaration(boolean removeDeclaration) {
		fRemoveDeclaration= removeDeclaration;
	}

	public void setReplaceAllReferences(boolean replaceAllReferences) {
		fReplaceAllReferences= replaceAllReferences;
		checkInvariant();
	}

	/**
	 * Returns the references, or empty list if the references could not be found.
	 * @param monitor progress monitor
	 * @param status the status to report errors
	 *
	 * @return the references
	 */
	public SearchResultGroup[] getReferences(IProgressMonitor monitor, RefactoringStatus status) {
		if (this.fReferences == null) {
			try {
				this.fReferences= findReferences(monitor, status);
			} catch (JavaModelException e) {
				this.fReferences= new SearchResultGroup[0];
			}
		}
		return this.fReferences;
	}

	private RefactoringStatus initialize(JavaRefactoringArguments arguments) {
		final String selection= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION);
		if (selection != null) {
			int offset= -1;
			int length= -1;
			final StringTokenizer tokenizer= new StringTokenizer(selection);
			if (tokenizer.hasMoreTokens())
				offset= Integer.parseInt(tokenizer.nextToken());
			if (tokenizer.hasMoreTokens())
				length= Integer.parseInt(tokenizer.nextToken());
			if (offset >= 0 && length >= 0) {
				fSelectionStart= offset;
				fSelectionLength= length;
			} else
				return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { selection, JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION}));
		}
		final String handle= arguments.getAttribute(JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT);
		if (handle != null) {
			final IJavaElement element= JavaRefactoringDescriptorUtil.handleToElement(arguments.getProject(), handle, false);
			if (element == null || !element.exists())
				return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getName(), IJavaRefactorings.INLINE_CONSTANT);
			else {
				if (element instanceof ICompilationUnit) {
					fSelectionCu= (ICompilationUnit) element;
					if (selection == null)
						return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_SELECTION));
				} else if (element instanceof IField) {
					final IField field= (IField) element;
					try {
						final ISourceRange range= field.getNameRange();
						if (range != null) {
							fSelectionStart= range.getOffset();
							fSelectionLength= range.getLength();
						} else
							return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, IJavaRefactorings.INLINE_CONSTANT));
					} catch (JavaModelException exception) {
						return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getName(), IJavaRefactorings.INLINE_CONSTANT);
					}
					fSelectionCu= field.getCompilationUnit();
				} else
					return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_illegal_argument, new Object[] { handle, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT}));
				final ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
				parser.setResolveBindings(true);
				parser.setSource(fSelectionCu);
				final CompilationUnit unit= (CompilationUnit) parser.createAST(null);
				initialize(fSelectionCu, unit);
				if (checkStaticFinalConstantNameSelected().hasFatalError())
					return JavaRefactoringDescriptorUtil.createInputFatalStatus(element, getName(), IJavaRefactorings.INLINE_CONSTANT);
			}
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, JavaRefactoringDescriptorUtil.ATTRIBUTE_INPUT));
		final String replace= arguments.getAttribute(ATTRIBUTE_REPLACE);
		if (replace != null) {
			fReplaceAllReferences= Boolean.parseBoolean(replace);
		} else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REPLACE));
		final String remove= arguments.getAttribute(ATTRIBUTE_REMOVE);
		if (remove != null)
			fRemoveDeclaration= Boolean.parseBoolean(remove);
		else
			return RefactoringStatus.createFatalErrorStatus(Messages.format(RefactoringCoreMessages.InitializableRefactoring_argument_not_exist, ATTRIBUTE_REMOVE));
		return new RefactoringStatus();
	}
}
