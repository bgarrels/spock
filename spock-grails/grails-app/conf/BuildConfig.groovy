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

grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"

grails.project.dependency.resolution = {
  inherits "global" // inherit Grails' default dependencies
  log "warn" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'

  repositories {
    grailsHome()
    grailsCentral()

    mavenLocal() // prefer local, so we pick up spock snapshot as part of whole build
    mavenRepo "http://m2repo.spockframework.org/snapshots"
    mavenRepo "http://repository.jboss.org/maven2" // for javax.security:jaas and javax.security:jacc (required by Hibernate plugin)
    mavenCentral()
  }
  
  dependencies {
    build 'org.spockframework:spock-core:0.5-groovy-1.7-SNAPSHOT'
    build 'junit:junit:4.8.1'
  }
}
