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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, OtherService, UserAnswers, WrongCredentialsSelfAssessmentUser}
import org.mockito.Mockito.{reset, when}
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.SaUtr

class OtherServicesSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

  private lazy val service: OtherServices = new OtherServices(mockConfigDecorator)

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
      NonFilerSelfAssessmentUser                                -> Some("https://www.gov.uk/self-assessment-tax-returns")
    )

    statuses.foreach { case (saStatus, expected) =>
      s"return an item or None for $saStatus" in {
        val result = service.getSelfAssessment(saStatus).futureValue
        result.map(_.link) mustBe expected
      }
    }
  }

  "getMyServices" must {
    "return a list of items" in {
      when(mockConfigDecorator.annualTaxSaSummariesTileLinkShow).thenReturn("ats/")
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("trustedHelper/")

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
        OtherService("label.child_benefit", "/personal-account/child-benefit/home"),
        OtherService("title.marriage_allowance", "/marriage-allowance-application/history"),
        OtherService("card.ats.heading", "ats/"),
        OtherService("label.trusted_helpers_heading", "trustedHelper/")
      )
    }
  }
}
