/*
 * Copyright 2023 HM Revenue & Customs
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

import controllers.auth.UserSessionAuditEvent.writes
import controllers.auth.requests.AuthenticatedRequest
import models.UserAnswers
import org.hamcrest.CustomMatcher
import org.mockito.ArgumentMatchers.any
import org.mockito.hamcrest.MockitoHamcrest.argThat
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Failure, Success}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import util.{AuditServiceTools, EnrolmentsHelper}

import scala.concurrent.Future

class SessionAuditorSpec extends BaseSpec with AuditTags {

  val auditConnector: AuditConnector                   = mock[AuditConnector]
  val enrolmentsHelper: EnrolmentsHelper               = inject[EnrolmentsHelper]
  val sessionAuditor                                   = new SessionAuditor(auditConnector, enrolmentsHelper)
  val testRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(auditConnector)
  }

  def originalResult[A]: Result = Ok

  def mockSendExtendedEvent(result: Future[AuditResult]): Unit =
    when(auditConnector.sendExtendedEvent(any())(any(), any())).thenReturn(result)

  def authenticatedRequest[A](request: Request[A]): AuthenticatedRequest[A] = AuthenticatedRequest[A](
    Fixtures.fakeNino,
    Some(Fixtures.fakeNino),
    Credentials("foo", "bar"),
    ConfidenceLevel.L200,
    None,
    None,
    None,
    Set.empty,
    request,
    None,
    UserAnswers.empty
  )

  def eqExtendedDataEvent[A](authenticatedRequest: AuthenticatedRequest[A]): ExtendedDataEvent = {
    val detailsJson = Json.toJson(writes(sessionAuditor.userSessionAuditEventFromRequest(authenticatedRequest)))
    val tags        = buildTags(authenticatedRequest)
    argThat[ExtendedDataEvent](new CustomMatcher[ExtendedDataEvent]("eq expected ExtendedDataEvent") {
      override def matches(o: Any): Boolean = o match {
        case ExtendedDataEvent(
              AuditServiceTools.auditSource,
              sessionAuditor.auditType,
              _,
              `tags`,
              `detailsJson`,
              _,
              _,
              _
            ) =>
          true
        case _ => false
      }
    })
  }

  "auditOnce" when {
    "the audit is successful" must {
      "call audit and update the session" in {
        mockSendExtendedEvent(Future.successful(Success))
        val result = sessionAuditor.auditOnce(authenticatedRequest(testRequest), originalResult)

        result.futureValue mustBe Ok.addingToSession(sessionAuditor.sessionKey -> "true")(testRequest)
        verify(auditConnector, times(1))
          .sendExtendedEvent(eqExtendedDataEvent(authenticatedRequest(testRequest)))(any(), any())
      }
    }

    "should not update the session" when {
      "the audit fails" in {
        mockSendExtendedEvent(Future.successful(Failure("")))
        val result = sessionAuditor.auditOnce(authenticatedRequest(testRequest), originalResult)

        result.futureValue mustBe Ok

        verify(auditConnector, times(1))
          .sendExtendedEvent(eqExtendedDataEvent(authenticatedRequest(testRequest)))(any(), any())
      }

      "the audit throws" in {
        mockSendExtendedEvent(Future.failed(new RuntimeException("throws")))

        val result = sessionAuditor.auditOnce(authenticatedRequest(testRequest), originalResult)

        result.futureValue mustBe Ok

        verify(auditConnector, times(1))
          .sendExtendedEvent(eqExtendedDataEvent(authenticatedRequest(testRequest)))(any(), any())
      }

      "the sessionKey has been set" in {
        val testRequest = FakeRequest().withSession(sessionAuditor.sessionKey -> "true")

        val result = sessionAuditor.auditOnce(authenticatedRequest(testRequest), originalResult)

        result.futureValue mustBe Ok

        verify(auditConnector, times(0))
          .sendExtendedEvent(any())(any(), any())
      }
    }
  }
}
