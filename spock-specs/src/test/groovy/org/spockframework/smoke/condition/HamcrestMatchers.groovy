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

package org.spockframework.smoke.condition

import spock.lang.*

import org.hamcrest.BaseMatcher
import org.hamcrest.Description
import org.spockframework.EmbeddedSpecification
import org.spockframework.runtime.InvalidSpecException
import org.spockframework.runtime.ConditionNotSatisfiedError

import static org.hamcrest.CoreMatchers.*
import static spock.util.matcher.MatcherSupport.that

class HamcrestMatchers extends EmbeddedSpecification {
  def "can be used in expect-blocks"() {
    def x = 42

    expect:
    x equalTo(42)
  }

  def "can be used in then-blocks"() {
    when:
    def x = 42

    then:
    x equalTo(42)
  }

  def "can be used with alternative 'that' syntax"() {
    def x = 42

    expect:
    that x, equalTo(42)
  }

  def "can be used in explicit conditions (but only with 'that' syntax)"() {
    setup:
    def x = 42
    assert that(x, equalTo(42))
  }

  @FailsWith(InvalidSpecException) // TODO
  def "can be used with custom message"() {
    def x = 42

    expect:
    assert that(x, equalTo(42)), "a custom message"
  }

  def "can have custom implementations"() {
    def x = "otto"
    
    expect:
    x palindrome()
  }

  @FailsWith(ConditionNotSatisfiedError)
  def "cause a failure when they don't match (1)"() {
    def x = 21

    expect:
    x equalTo(42)
  }

  @FailsWith(ConditionNotSatisfiedError)
  def "cause a failure when they don't match (2)"() {
    when:
    def x = 21

    then:
    x equalTo(42)
  }

  @FailsWith(ConditionNotSatisfiedError)
  def "cause a failure when they don't match (3)"() {
    def x = 21

    expect:
    that x, equalTo(42)
  }

  @FailsWith(ConditionNotSatisfiedError)
  def "cause a failure when they don't match (4)"() {
    setup:
    def x = 21
    assert that(x, equalTo(42))
  }

  @FailsWith(MissingMethodException)
  def "can only be used where a condition is expected (1)"() {
    setup:
    def x = 42
    x equalTo(42)
  }

  @FailsWith(InvalidSpecException)
  def "can only be used where a condition is expected (2)"() {
    setup:
    def x = 42
    that x, equalTo(42)
  }

  def "can be matched to numeric literals"() {
    def x = 42

    expect:
    42 equalTo(x)
  }

  @FailsWith(MissingMethodException)
  def "cannot be matched to string literals (because a string literal in this place is parsed as a method call)"() {
    def x = "fred"

    expect:
    "fred" equalTo(x)
  }

  def "can be matched to string literals with alternative 'that' syntax"() {
    def x = "fred"

    expect:
    that "fred", equalTo(x)    
  }

  def "can be used as argument contraints in interactions"() {
    def list = Mock(List)

    when:
    list.add(42)

    then:
    0 * list.add(equalTo(21))
    1 * list.add(equalTo(42))
  }

  private static palindrome() {
    new IsPalindrome()
  }
}

private class IsPalindrome extends BaseMatcher<String> {
  boolean matches(Object value) {
    value instanceof String && value.reverse() == value
  }

  void describeTo(Description description) {
    description.appendText("a palindrome")
  }
}
