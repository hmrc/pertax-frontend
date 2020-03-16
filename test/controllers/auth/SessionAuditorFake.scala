/*
 * Copyright 2020 HM Revenue & Customs
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

package controllers.auth

import com.google.inject.Inject
import controllers.auth.SessionAuditor.sessionKey
import controllers.auth.requests.AuthenticatedRequest
import play.api.mvc.Result
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

class SessionAuditorFake @Inject()(auditConnector: AuditConnector)(implicit ec: ExecutionContext)
    extends SessionAuditor(auditConnector) {
  override def auditOnce[A](request: AuthenticatedRequest[A], result: Result)(
    implicit hc: HeaderCarrier): Future[Result] =
    Future.successful(result.addingToSession(sessionKey -> "true")(request))
}
