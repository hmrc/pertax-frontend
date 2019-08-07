/*
 * Copyright 2019 HM Revenue & Customs
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

package filters

import javax.inject.{Inject, Provider, Singleton}

import org.joda.time.Duration
import play.api.mvc.Request
import play.api.{Application, Configuration}
import play.twirl.api.Html
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.frontend.bootstrap.DefaultFrontendGlobal
import uk.gov.hmrc.play.frontend.filters.{FrontendAuditFilter, FrontendLoggingFilter, SessionTimeoutFilter}
@Singleton
class SessionTimeoutFilterProvider @Inject()(configuration: Configuration) extends Provider[SessionTimeoutFilter] {

  override def get(): SessionTimeoutFilter = {

    //TODO Hopefully the SessionTimeoutFilter will be refactored out of DefaultFrontendGlobal, but for now we have to do this
    val global = new DefaultFrontendGlobal {
      override def loggingFilter: FrontendLoggingFilter = ???
      override def frontendAuditFilter: FrontendAuditFilter = ???
      override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = ???
      override def standardErrorTemplate(pageTitle: String, heading: String, message: String)(
        implicit request: Request[_]): Html = ???
      override def auditConnector: AuditConnector = ???
    }
    global.sessionTimeoutFilter
  }
}
