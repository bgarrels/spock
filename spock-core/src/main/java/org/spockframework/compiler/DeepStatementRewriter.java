/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.spockframework.compiler;

import java.util.List;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;

import org.spockframework.compiler.model.*;
import org.spockframework.util.Identifiers;

/**
 * Walks the statement and expression tree to:
 * - rewrite explicit conditions,
 * - rewrite interactions,
 * - rewrite core language primitives (members of class Specification)
 * - Forbid
 * 
 * Also records whether conditions and interactions were found.
 *
 * @author Peter Niederwieser
 */
public class DeepStatementRewriter extends StatementReplacingVisitorSupport {
  private final IRewriteResources resources;

  private boolean conditionFound = false;
  private boolean interactionFound = false;
  // scope for the current closure; null if not in a closure
  private VariableScope closureScope;

  public DeepStatementRewriter(IRewriteResources resources) {
    this.resources = resources;
  }
  
  public boolean isConditionFound() {
    return conditionFound;
  }

  public boolean isInteractionFound() {
    return interactionFound;
  }

  public void visitBlock(Block block) {
    replaceAll(block.getAst());
  }
  
  @Override
  public void visitAssertStatement(AssertStatement stat) {
    super.visitAssertStatement(stat);
    conditionFound = true;
    replaceVisitedStatementWith(
        ConditionRewriter.rewriteExplicitCondition(stat, resources));
  }

  @Override
  public void visitExpressionStatement(ExpressionStatement stat) {
    super.visitExpressionStatement(stat);

    Statement rewritten = new InteractionRewriter(resources).rewrite(stat);
    if (rewritten == null) return;
    
    interactionFound = true;
    replaceVisitedStatementWith(rewritten);
  }

  @Override
  public void visitClosureExpression(ClosureExpression expr) {
    // because a closure might be executed asynchronously, its conditions
    // and interactions are handled independently from the conditions
    // and interactions of the closure's context

    boolean oldConditionFound = conditionFound;
    boolean oldInteractionFound = interactionFound;
    VariableScope oldClosureScope = closureScope;
    conditionFound = false;
    interactionFound = false;
    closureScope = expr.getVariableScope();

    fixupParameters(closureScope, true);
    super.visitClosureExpression(expr);
    if (conditionFound) defineValueRecorder(expr);

    conditionFound = oldConditionFound;
    interactionFound = oldInteractionFound;
    closureScope = oldClosureScope;
  }

  private void defineValueRecorder(ClosureExpression expr) {
    resources.defineValueRecorder(AstUtil.getStatements(expr));
  }

  private void fixupParameters(VariableScope scope, boolean isClosureScope) {
    Method method = resources.getCurrentMethod();
    if (!(method instanceof FeatureMethod)) return;

    // if this is a parameterized feature method w/o explicit parameter list,
    // update any references to parameterization variables
    // (parameterization variables used to be free variables,
    // but have been changed to method parameters by WhereBlockRewriter)
    for (Parameter param : method.getAst().getParameters()) {
      Variable var = scope.getReferencedClassVariable(param.getName());
      if (var instanceof DynamicVariable) {
        scope.removeReferencedClassVariable(param.getName());
        scope.putReferencedLocalVariable(param);
        if (isClosureScope)
          param.setClosureSharedVariable(true);
      }
    }
  }

  @Override
  public void visitBlockStatement(BlockStatement stat) {
    super.visitBlockStatement(stat);
    fixupParameters(stat.getVariableScope(), false);
  }

  @Override
  public void visitDeclarationExpression(DeclarationExpression expr) {
    visitBinaryExpression(expr);
  }

  @Override
  public void visitBinaryExpression(BinaryExpression expr) {
    if (AstUtil.isBuiltinMemberAssignment(expr, Identifiers.MOCK, 0, 1))
      try {
        AstUtil.expandBuiltinMemberAssignment(expr, resources.getMockControllerRef());
      } catch (InvalidSpecCompileException e) {
        resources.getErrorReporter().error(e);
        return;
      }

    // only descend after we have expanded Specification.Mock so that it's not
    // expanded by visit(Static)MethodCallExpression instead
    super.visitBinaryExpression(expr);
  }

  @Override
  public void visitMethodCallExpression(MethodCallExpression expr) {
    super.visitMethodCallExpression(expr);
    forbidUseOfSuperInFixtureMethod(expr);
    handleMockAndOldCalls(expr);
  }

  // Forbid the use of super.foo() in fixture method foo,
  // because it is most likely a mistake (user thinks he is overriding
  // the base method and doesn't know that it will be run automatically)
  private void forbidUseOfSuperInFixtureMethod(MethodCallExpression expr) {
    Method currMethod = resources.getCurrentMethod();
    Expression target = expr.getObjectExpression();
    
    if (currMethod instanceof FixtureMethod
        && target instanceof VariableExpression
        && ((VariableExpression)target).isSuperExpression()
        && currMethod.getName().equals(expr.getMethodAsString())) {
      resources.getErrorReporter().error(expr,
        "A base class fixture method should not be called explicitely " +
        "because it is always run automatically by the framework");
    }
  }

  private void handleMockAndOldCalls(Expression expr) {
    if (AstUtil.isBuiltinMemberCall(expr, Identifiers.MOCK, 0, 1))
      handleMockCall(expr);
    else if (AstUtil.isBuiltinMemberCall(expr, Identifiers.OLD, 1, 1))
      handleOldCall(expr);
  }

  private void handleMockCall(Expression expr) {
    try {
      AstUtil.expandBuiltinMemberCall(expr, resources.getMockControllerRef());
    } catch (InvalidSpecCompileException e) {
      resources.getErrorReporter().error(e);
    }
  }

  private void handleOldCall(Expression expr) {
    if (!(resources.getCurrentBlock() instanceof ThenBlock)) {
      resources.getErrorReporter().error(expr, "old() may only be used in a 'then' block");
      return;
    }

    List<Expression> args = AstUtil.getArguments(expr);
    VariableExpression oldValue = resources.captureOldValue(args.get(0));
    args.set(0, oldValue);
    args.add(ConstantExpression.FALSE); // dummy arg

    if (closureScope != null) {
      oldValue.setClosureSharedVariable(true);
      closureScope.putReferencedLocalVariable(oldValue);
    }
  }
}
