/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.auth.SessionAuditor.UserSessionAuditEvent
import controllers.auth.requests.AuthenticatedRequest
import org.hamcrest.CustomMatcher
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Failure, Success}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import util.{AuditServiceTools, Fixtures}

import scala.concurrent.{ExecutionContext, Future}

class SessionAuditorSpec
    extends PlaySpec with GuiceOneAppPerSuite with ScalaFutures with BeforeAndAfterEach with AuditTags {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(auditConnector)
  }

  implicit lazy val ec = app.injector.instanceOf[ExecutionContext]
  implicit val hc = HeaderCarrier()

  val auditConnector = mock[AuditConnector]
  val sessionAuditor = new SessionAuditor(auditConnector)

  def originalResult[A]: Result = Ok

  def mockSendExtendedEvent(result: Future[AuditResult]): Unit =
    when(auditConnector.sendExtendedEvent(any())(any(), any())).thenReturn(result)

  val testRequest = FakeRequest()

  def authenticatedRequest[A](request: Request[A]) = AuthenticatedRequest[A](
    Some(Fixtures.fakeNino),
    None,
    Credentials("foo", "bar"),
    ConfidenceLevel.L200,
    None,
    None,
    None,
    Set.empty,
    request
  )

  def eqExtendedDataEvent[A](authenticatedRequest: AuthenticatedRequest[A]): ExtendedDataEvent = {
    val detailsJson = Json.toJson(UserSessionAuditEvent(authenticatedRequest))
    val tags = buildTags(authenticatedRequest)
    argThat[ExtendedDataEvent](new CustomMatcher[ExtendedDataEvent]("eq expected ExtendedDataEvent") {
      override def matches(o: Any): Boolean = o match {
        case ExtendedDataEvent(AuditServiceTools.auditSource, SessionAuditor.auditType, _, `tags`, `detailsJson`, _) =>
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

        result.futureValue mustBe Ok.addingToSession(SessionAuditor.sessionKey -> "true")(testRequest)
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
        val testRequest = FakeRequest().withSession(SessionAuditor.sessionKey -> "true")

        val result = sessionAuditor.auditOnce(authenticatedRequest(testRequest), originalResult)

        result.futureValue mustBe Ok

        verify(auditConnector, times(0))
          .sendExtendedEvent(any())(any(), any())
      }
    }
  }
}
