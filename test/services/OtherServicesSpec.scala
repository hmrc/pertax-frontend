/*
 * Copyright 2025 HM Revenue & Customs
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

package services

import cats.data.EitherT
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, OtherService, UserAnswers, WrongCredentialsSelfAssessmentUser}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class OtherServicesSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]
  private val mockFandFService: FandFService       = mock[FandFService]
  private val mockTaiService: TaiService           = mock[TaiService]

  private lazy val service: OtherServices = new OtherServices(mockConfigDecorator, mockFandFService, mockTaiService)
  implicit lazy val messages: Messages    = MessagesImpl(Lang("en"), messagesApi)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
  }

  "getSelfAssessment" must {
    val statuses = Map(
      ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11"))       -> None,
      NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")) -> None,
      WrongCredentialsSelfAssessmentUser(SaUtr("11"))           -> None,
      NotEnrolledSelfAssessmentUser(SaUtr("11"))                -> Some("/personal-account/self-assessment/request-access"),
      NonFilerSelfAssessmentUser                                -> None
    )

    statuses.foreach { case (saStatus, expected) =>
      s"return an item or None when trusted helper is disabled for $saStatus" in {
        val result = service.getSelfAssessment(saStatus, false).futureValue
        result.map(_.link) mustBe expected
      }
    }

    statuses.foreach { case (saStatus, _) =>
      s"return None when trusted helper is enabled for $saStatus" in {
        val result = service.getSelfAssessment(saStatus, true).futureValue
        result.map(_.link) mustBe None
      }
    }
  }

  "getMyServices" must {
    "return a list of items" in {
      when(mockConfigDecorator.annualTaxSaSummariesTileLinkShow).thenReturn("ats/")
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("trustedHelper/")
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(
        Future.successful(List.empty)
      )
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](false)
      )

      val request = UserRequest(
        generatedNino,
        ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
        Credentials("test", "test"),
        ConfidenceLevel.L200,
        None,
        Set.empty,
        None,
        None,
        FakeRequest(),
        UserAnswers("id")
      )
      val result  = service.getOtherServices(request).futureValue

      result mustBe Seq(
        OtherService(
          "Child Benefit",
          "/personal-account/child-benefit/home",
          Map(),
          Some("Benefits"),
          Some("Child Benefit")
        ),
        OtherService(
          "Marriage Allowance",
          "/marriage-allowance-application/history",
          Map(),
          Some("Benefits"),
          Some("Marriage Allowance")
        ),
        OtherService("Annual Tax Summary", "ats/", Map(), Some("Tax Summaries"), Some("Annual Tax Summary")),
        OtherService("Trusted helpers", "trustedHelper/", Map(), Some("Account"), Some("Trusted helpers"))
      )
    }
  }
}
