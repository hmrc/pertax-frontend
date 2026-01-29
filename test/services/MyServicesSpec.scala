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
import models.admin.{PayeToPegaRedirectToggle, ShowTaxCalcTileToggle}
import models.{ActivatedOnlineFilerSelfAssessmentUser, NonFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, NotYetActivatedOnlineFilerSelfAssessmentUser, WrongCredentialsSelfAssessmentUser}
import org.mockito.ArgumentMatchers
import testUtils.BaseSpec
import org.mockito.Mockito.{reset, when}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import scala.concurrent.Future

class MyServicesSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator       = mock[ConfigDecorator]
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  private lazy val service: MyServices = new MyServices(mockConfigDecorator, mockFeatureFlagService)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    reset(mockFeatureFlagService)
  }

  "getSelAssessment" must {
    val statuses = Map(
      ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11"))       -> Some("/personal-account/self-assessment-summary"),
      NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")) -> Some("a/url"),
      WrongCredentialsSelfAssessmentUser(SaUtr("11"))           -> Some(
        "/personal-account/self-assessment/signed-in-wrong-account"
      ),
      NotEnrolledSelfAssessmentUser(SaUtr("11"))                -> Some("/personal-account/self-assessment/request-access"),
      NonFilerSelfAssessmentUser                                -> None
    )

    statuses.foreach { case (saStatus, expected) =>
      s"return an item for $saStatus" in {
        when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl).thenReturn("a/url")

        val result = service.getSelAssessment(saStatus).futureValue
        result.map(_.link) mustBe expected
      }
    }
  }

  "getPayAsYouEarn" must {
    "return an item with a link to pega" when {
      "Trusted helper is disabled, nino is in the list and toggle is on" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
          .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = true)))
        when(mockConfigDecorator.taiHost).thenReturn("tai/")
        when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(
          Seq(0)
        )
        when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("pega")

        val nino   = generatedNino.copy(nino = generatedNino.nino.updated(6, '0'))
        val result = service.getPayAsYouEarn(nino, false).futureValue

        result.map(_.link) mustBe Some("pega")
      }
    }

    "return an item with a link to tai" when {
      "Trusted helper is disabled, nino is not in the list and toggle is on" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
          .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = true)))
        when(mockConfigDecorator.taiHost).thenReturn("tai/")
        when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(
          Seq(1)
        )
        when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("pega")

        val nino   = generatedNino.copy(nino = generatedNino.nino.updated(6, '0'))
        val result = service.getPayAsYouEarn(nino, false).futureValue

        result.map(_.link) mustBe Some("tai//check-income-tax/what-do-you-want-to-do")
      }

      "Trusted helper is enabled, nino is in the list and toggle is on" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
          .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = true)))
        when(mockConfigDecorator.taiHost).thenReturn("tai/")
        when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(
          Seq(0)
        )
        when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("pega")

        val nino   = generatedNino.copy(nino = generatedNino.nino.updated(6, '0'))
        val result = service.getPayAsYouEarn(nino, true).futureValue

        result.map(_.link) mustBe Some("tai//check-income-tax/what-do-you-want-to-do")
      }

      "Trusted helper is disabled, nino is in the list and toggle is off" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
          .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = false)))
        when(mockConfigDecorator.taiHost).thenReturn("tai/")
        when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(
          Seq(0)
        )
        when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("pega")

        val nino   = generatedNino.copy(nino = generatedNino.nino.updated(6, '0'))
        val result = service.getPayAsYouEarn(nino, false).futureValue

        result.map(_.link) mustBe Some("tai//check-income-tax/what-do-you-want-to-do")
      }
    }
  }

  "getTaxcalc" must {
    "return an item" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))
      when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("taxcalc/")

      val result = service.getTaxcalc(false).futureValue

      result.map(_.link) mustBe Some("taxcalc/")
    }

    "return None" when {
      "trusted helper is defined" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))
        when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("taxcalc/")

        val result = service.getTaxcalc(true).futureValue

        result mustBe None
      }

      "feature flag is disabled" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))
        when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("taxcalc/")

        val result = service.getTaxcalc(false).futureValue

        result mustBe None
      }
    }
  }

  "getNationalInsuranceCard" must {
    "return an item" in {
      val result = service.getNationalInsuranceCard.futureValue
      result.map(_.link) mustBe Some("/personal-account/your-national-insurance-state-pension")
    }
  }
}
