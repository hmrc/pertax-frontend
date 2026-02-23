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
import models.admin.{PayeToPegaRedirectToggle, ShowTaxCalcTileToggle}
import models.*
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import testUtils.BaseSpec
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.Future

class MyServicesSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator       = mock[ConfigDecorator]
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  private val mockFandFService: FandFService             = mock[FandFService]
  private val mockTaiService: TaiService                 = mock[TaiService]

  private lazy val service: MyServices          =
    new MyServices(mockConfigDecorator, mockFeatureFlagService, mockFandFService, mockTaiService)
  implicit lazy val messages: Messages          = MessagesImpl(Lang("en"), messagesApi)
  implicit val request: UserRequest[AnyContent] = UserRequest(
    generatedNino,
    NonFilerSelfAssessmentUser,
    Credentials("credId", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    Set.empty,
    None,
    None,
    FakeRequest(),
    UserAnswers.empty
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    reset(mockFeatureFlagService)
  }

  "getSelfAssessment" must {
    val statuses = Map(
      ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11"))       -> Some("/personal-account/self-assessment-summary"),
      NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")) -> Some("a/url"),
      WrongCredentialsSelfAssessmentUser(SaUtr("11"))           -> Some(
        "/personal-account/self-assessment/signed-in-wrong-account"
      ),
      NotEnrolledSelfAssessmentUser(SaUtr("11"))                -> None,
      NonFilerSelfAssessmentUser                                -> None
    )

    statuses.foreach { case (saStatus, expected) =>
      s"return an item or None for $saStatus" in {
        when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl).thenReturn("a/url")

        val result = service.getSelfAssessment(saStatus, false).futureValue
        result.map(_.link) mustBe expected
      }
    }

    statuses.foreach { case (saStatus, _) =>
      s"return None when trustHelper is enabled for $saStatus" in {
        when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl).thenReturn("a/url")

        val result = service.getSelfAssessment(saStatus, true).futureValue
        result.map(_.link) mustBe None
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

  "getMarriageAllowance" must {
    "return None" when {
      "trusted helper is active" in {
        val result = service.getMarriageAllowance(generatedNino, true).futureValue

        result mustBe None
        verify(mockTaiService, times(0)).getTaxComponentsList(any(), any())(any(), any())
      }

      "trusted helper is not active and there is no tax component for marriage allowance" in {
        when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(
          Future.successful(List.empty)
        )
        val result = service.getMarriageAllowance(generatedNino, false).futureValue

        result mustBe None
      }
    }

    "return an item with MarriageAllowanceTransferred" in {
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(
        Future.successful(List("MarriageAllowanceTransferred"))
      )

      val result = service.getMarriageAllowance(generatedNino, false).futureValue

      result mustBe Some(
        MyService(
          "Marriage Allowance",
          "You currently transfer part of your Personal Allowance to your partner.",
          "/marriage-allowance-application/history",
          Map(),
          Some("Benefits"),
          Some("Marriage Allowance"),
          None
        )
      )
    }

    "return an item with MarriageAllowanceReceived" in {
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(
        Future.successful(List("MarriageAllowanceReceived"))
      )

      val result = service.getMarriageAllowance(generatedNino, false).futureValue

      result mustBe Some(
        MyService(
          "Marriage Allowance",
          "Your partner currently transfers part of their Personal Allowance to you.",
          "/marriage-allowance-application/history",
          Map(),
          Some("Benefits"),
          Some("Marriage Allowance"),
          None
        )
      )
    }
  }

  "getMyServices" must {
    "return a list of items" in {
      val yearsToShow = 4

      when(mockConfigDecorator.taxCalcYearsToShow).thenReturn(yearsToShow)
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = true)))
      when(mockConfigDecorator.taiHost).thenReturn("tai/")
      when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(
        Seq.empty
      )
      when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("pega")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))
      when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("taxcalc/")
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(
        Future.successful(List.empty)
      )
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(
        Future.successful(true)
      )
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("/fandf")

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
      val result  = service.getMyServices(request).futureValue

      result mustBe Seq(
        MyService(
          "Pay As You Earn (PAYE)",
          "tai//check-income-tax/what-do-you-want-to-do",
          "",
          Map(),
          Some("Income"),
          Some("Pay As You Earn (PAYE)")
        ),
        MyService(
          s"Your tax calculation — PAYE ${TaxYear.current.back(yearsToShow).startYear} to ${TaxYear.current.startYear}",
          "taxcalc/",
          "",
          Map(),
          Some("Income"),
          Some("Tax Calculation")
        ),
        MyService(
          "Self Assessment",
          "/personal-account/self-assessment-summary",
          "The deadline for online returns is 31 January 2026.",
          Map(),
          Some("Income"),
          Some("Self Assessment")
        ),
        MyService(
          "Your National Insurance and State Pension",
          "/personal-account/your-national-insurance-state-pension",
          "",
          Map(),
          Some("Income"),
          Some("National Insurance and State Pension"),
          None
        ),
        MyService("Trusted helpers", "/fandf", "", Map(), Some("Account"), Some("Trusted helpers"), None)
      )
    }
  }
}
