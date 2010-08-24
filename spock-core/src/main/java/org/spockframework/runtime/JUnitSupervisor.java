/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.spockframework.runtime;

import org.junit.ComparisonFailure;
import org.junit.internal.runners.model.MultipleFailureException;
import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;

import org.spockframework.runtime.condition.IObjectRenderer;
import org.spockframework.runtime.model.*;
import org.spockframework.util.InternalSpockError;
import org.spockframework.util.TextUtil;

import spock.lang.Unroll;

import static org.spockframework.runtime.RunStatus.*;

public class JUnitSupervisor implements IRunSupervisor {
  private final SpecInfo spec;
  private final RunNotifier notifier;
  private final IStackTraceFilter filter;
  private final IRunListener masterListener;
  private final IObjectRenderer<Object> diffedObjectRenderer;

  private FeatureInfo feature;
  private boolean unrollFeature;
  private UnrolledFeatureNameGenerator unrolledNameGenerator;

  private int iterationCount;
  private Description unrolledDescription;
  private boolean errorSinceLastReset;

  public JUnitSupervisor(SpecInfo spec, RunNotifier notifier, IStackTraceFilter filter,
      IObjectRenderer<Object> diffedObjectRenderer) {
    this.spec = spec;
    this.notifier = notifier;
    this.filter = filter;
    this.masterListener = new MasterRunListener(spec);
    this.diffedObjectRenderer = diffedObjectRenderer;
  }

  public void beforeSpec(SpecInfo spec) {
    masterListener.beforeSpec(spec);
  }

  public void beforeFeature(FeatureInfo feature) {
    masterListener.beforeFeature(feature);
    this.feature = feature;

    Unroll unroll = feature.getFeatureMethod().getReflection().getAnnotation(Unroll.class);
    unrollFeature = unroll != null;
    if (unrollFeature)
      unrolledNameGenerator = new UnrolledFeatureNameGenerator(feature, unroll.value());
    else
      notifier.fireTestStarted(getDescription(feature.getFeatureMethod()));

    if (feature.isParameterized()) {
      iterationCount = 0;
      errorSinceLastReset = false;
    }
  }

  public void beforeIteration(IterationInfo iteration) {
    masterListener.beforeIteration(iteration);
    iterationCount++;
    if (!unrollFeature) return;

    unrolledDescription = getUnrolledDescription(iteration.getDataValues());
    notifier.fireTestStarted(unrolledDescription);
  }

  public int error(ErrorInfo error) {
    Throwable exception = error.getException();

    if (exception instanceof MultipleFailureException)
      return handleMultipleFailures(error);

    if (isCausedByFailedEqualityComparison(exception))
      exception = convertToComparisonFailure(exception);

    filter.filter(exception);

    masterListener.error(error);
    notifier.fireTestFailure(new Failure(getCurrentDescription(), exception));

    errorSinceLastReset = true;
    return statusFor(error);
  }

  // for better JUnit compatibility, e.g when a @Rule is used
  private int handleMultipleFailures(ErrorInfo error) {
    MultipleFailureException multiFailure = (MultipleFailureException) error.getException();
    int runStatus = OK;
    for (Throwable failure : multiFailure.getFailures())
      runStatus = error(new ErrorInfo(error.getMethod(), failure));
    return runStatus;
  }

  private boolean isCausedByFailedEqualityComparison(Throwable exception) {
    if (!(exception instanceof ConditionNotSatisfiedError)) return false;

    Condition condition = ((ConditionNotSatisfiedError) exception).getCondition();
    ExpressionInfo expr = condition.getExpression();
    return expr != null && expr.isEqualityComparison();
  }

  // enables IDE support (diff dialog)
  private Throwable convertToComparisonFailure(Throwable exception) {
    assert isCausedByFailedEqualityComparison(exception);

    Condition condition = ((ConditionNotSatisfiedError) exception).getCondition();
    ExpressionInfo expr = condition.getExpression();

    String actual = renderValue(expr.getChildren().get(0).getValue());
    String expected = renderValue(expr.getChildren().get(1).getValue());
    ComparisonFailure failure = new SpockComparisonFailure(condition, expected, actual);
    failure.setStackTrace(exception.getStackTrace());

    return failure;
  }

  private String renderValue(Object value) {
    try {
      return diffedObjectRenderer.render(value);
    } catch (Throwable t) {
      return "Failed to render value due to:\n\n" + TextUtil.printStackTrace(t);
    }
  }

  private int statusFor(ErrorInfo error) {
    switch (error.getMethod().getKind()) {
      case DATA_PROCESSOR:
        return END_ITERATION;
      case SETUP:
      case CLEANUP:
      case FEATURE:
        return feature.isParameterized() ? END_ITERATION : END_FEATURE;
      case FEATURE_EXECUTION:
      case DATA_PROVIDER:
        return END_FEATURE;
      case SETUP_SPEC:
      case CLEANUP_SPEC:
      case SPEC_EXECUTION:
        return END_SPEC;
      default:
        throw new InternalSpockError("unknown method kind");
    }
  }

  public void afterIteration(IterationInfo iteration) {
    masterListener.afterIteration(iteration);
    if (!unrollFeature) return;

    notifier.fireTestFinished(unrolledDescription);
    unrolledDescription = null;
  }

  public void afterFeature(FeatureInfo feature) {
    if (feature.isParameterized()) {
      if (iterationCount == 0 && !errorSinceLastReset)
        notifier.fireTestFailure(new Failure(getDescription(feature.getFeatureMethod()),
            new SpockExecutionException("Data provider has no data")));
    }

    masterListener.afterFeature(feature);
    if (!unrollFeature)
      notifier.fireTestFinished(getDescription(feature.getFeatureMethod()));

    this.feature = null;
    unrollFeature = false;
    unrolledNameGenerator = null;
  }

  public void afterSpec(SpecInfo spec) {
    masterListener.afterSpec(spec);
  }

  public void specSkipped(SpecInfo spec) {
    masterListener.specSkipped(spec);
    notifier.fireTestIgnored(getDescription(spec));
  }

  public void featureSkipped(FeatureInfo feature) {
    masterListener.featureSkipped(feature);
    notifier.fireTestIgnored(getDescription(feature));
  }

  private Description getDescription(NodeInfo node) {
    return (Description) node.getMetadata();
  }

  private Description getCurrentDescription() {
    if (unrolledDescription != null) return unrolledDescription;
    if (feature != null) return getDescription(feature.getFeatureMethod());
    return getDescription(spec);
  }

  private Description getUnrolledDescription(Object[] args) {
    return Description.createTestDescription(spec.getReflection(), unrolledNameGenerator.nameFor(args));
  }
}
