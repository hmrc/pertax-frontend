/*
 * Copyright 2026 HM Revenue & Customs
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
import models.*
import models.admin.{PayeToPegaRedirectToggle, ShowTaxCalcTileToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.sca.models.TrustedHelper
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.Future

class MyServicesSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator       = mock[ConfigDecorator]
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  private val mockFandFService: FandFService             = mock[FandFService]
  private val mockTaiService: TaiService                 = mock[TaiService]

  private lazy val service: MyServices =
    new MyServices(mockConfigDecorator, mockFeatureFlagService, mockFandFService, mockTaiService)

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    reset(mockFeatureFlagService)
    reset(mockFandFService)
    reset(mockTaiService)
  }

  private def buildRequest(
                            saUserType: SelfAssessmentUserType,
                            enrolments: Set[Enrolment] = Set.empty,
                            trustedHelper: Option[TrustedHelper] = None
                          ): UserRequest[AnyContent] =
    UserRequest(
      authNino = generatedNino,
      saUserType = saUserType,
      credentials = Credentials("credId", "GovernmentGateway"),
      confidenceLevel = ConfidenceLevel.L200,
      trustedHelper = trustedHelper,
      enrolments = enrolments,
      profile = None,
      breadcrumb = None,
      request = FakeRequest(),
      userAnswers = UserAnswers.empty
    )

  private val mtdItsaEnrolment: Enrolment =
    Enrolment("HMRC-MTD-IT", Seq(EnrolmentIdentifier("MTDITID", "11111")), "Activated")

  "getMyServices" must {

    "include Self Assessment tile for ActivatedOnlineFilerSelfAssessmentUser when user has no MTD ITSA enrolment" in {
      val yearsToShow = 4

      when(mockConfigDecorator.taxCalcYearsToShow).thenReturn(yearsToShow)
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = true)))
      when(mockConfigDecorator.taiHost).thenReturn("tai/")
      when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq.empty)
      when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("pega")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))
      when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("taxcalc/")
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(Future.successful(List.empty))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(true))
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("/fandf")

      val req = buildRequest(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")))
      val res = service.getMyServices(req).futureValue

      res.map(_.link) must contain("/personal-account/self-assessment-summary")
      res.find(_.gaLabel.contains("Self Assessment")).map(_.link) mustBe Some(
        "/personal-account/self-assessment-summary"
      )
      res.find(_.gaLabel.contains("Self Assessment")).map(_.description) mustBe Some(
        "The deadline for online returns is 31 January 2027."
      )
    }

    "include combined tile (MTD IT & SA) for ActivatedOnlineFilerSelfAssessmentUser when user has MTD ITSA enrolment" in {
      val yearsToShow = 4

      when(mockConfigDecorator.taxCalcYearsToShow).thenReturn(yearsToShow)
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = false)))
      when(mockConfigDecorator.taiHost).thenReturn("tai/")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(Future.successful(List.empty))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(false))
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("/fandf")

      val req = buildRequest(
        ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
        enrolments = Set(mtdItsaEnrolment)
      )

      val res = service.getMyServices(req).futureValue

      res.exists(_.gaLabel.contains("MTD IT & SA")) mustBe true
    }

    "include combined tile (MTD IT & SA) linking to SA wrong-credentials journey for WrongCredentialsSelfAssessmentUser when user has MTD ITSA enrolment" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = false)))
      when(mockConfigDecorator.taiHost).thenReturn("tai/")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(Future.successful(List.empty))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(false))
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("/fandf")

      val req = buildRequest(
        WrongCredentialsSelfAssessmentUser(SaUtr("11")),
        enrolments = Set(mtdItsaEnrolment)
      )

      val res = service.getMyServices(req).futureValue

      res.find(_.gaLabel.contains("MTD IT & SA")).map(_.link) mustBe Some(
        "/personal-account/self-assessment/signed-in-wrong-account"
      )
    }

    "include Self Assessment activation tile for NotYetActivatedOnlineFilerSelfAssessmentUser when user has no MTD ITSA enrolment" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = false)))
      when(mockConfigDecorator.taiHost).thenReturn("tai/")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))
      when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl).thenReturn("a/url")
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(Future.successful(List.empty))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(false))

      val req = buildRequest(NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")))
      val res = service.getMyServices(req).futureValue

      res.find(_.gaLabel.contains("Self Assessment")).map(_.link) mustBe Some("a/url")
      res.find(_.gaLabel.contains("Self Assessment")).map(_.description) mustBe Some(
        "Activate your Self Assessment registration"
      )
    }

    "return None items when trusted helper is enabled" in {
      val req = buildRequest(
        ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
        trustedHelper = Some(TrustedHelper("Principal", "Trusted helper", "return-url", Some(generatedNino.nino)))
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = false)))
      when(mockConfigDecorator.taiHost).thenReturn("tai/")

      val res = service.getMyServices(req).futureValue

      verify(mockTaiService, times(0)).getTaxComponentsList(any(), any())(any(), any())
      res.exists(_.gaLabel.contains("Self Assessment")) mustBe false
    }

    "return a list of items" in {
      val yearsToShow = 4

      when(mockConfigDecorator.taxCalcYearsToShow).thenReturn(yearsToShow)
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(PayeToPegaRedirectToggle)))
        .thenReturn(Future.successful(FeatureFlag(PayeToPegaRedirectToggle, isEnabled = true)))
      when(mockConfigDecorator.taiHost).thenReturn("tai/")
      when(mockConfigDecorator.payeToPegaRedirectList).thenReturn(Seq.empty)
      when(mockConfigDecorator.payeToPegaRedirectUrl).thenReturn("pega")
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))
      when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("taxcalc/")
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(Future.successful(List.empty))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(true))
      when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("/fandf")

      val request = buildRequest(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")))
      val result  = service.getMyServices(request).futureValue

      result mustBe Seq(
        MyService(
          "Pay As You Earn (PAYE)",
          controllers.routes.RedirectToPayeController.redirectToPaye.url,
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
          "The deadline for online returns is 31 January 2027.",
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

  "getMarriageAllowanceTile" must {
    "return None" when {
      "trusted helper is active" in {
        val req = buildRequest(
          NonFilerSelfAssessmentUser,
          trustedHelper = Some(TrustedHelper("Principal", "Trusted helper", "return-url", Some(generatedNino.nino)))
        )

        val result = service.getMyServices(req).futureValue

        result.exists(_.gaLabel.contains("Marriage Allowance")) mustBe false
        verify(mockTaiService, times(0)).getTaxComponentsList(any(), any())(any(), any())
      }

      "trusted helper is not active and there is no tax component for marriage allowance" in {
        when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(
          Future.successful(List.empty)
        )
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
          .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))
        when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(false))

        val req    = buildRequest(NonFilerSelfAssessmentUser)
        val result = service.getMyServices(req).futureValue

        result.exists(_.gaLabel.contains("Marriage Allowance")) mustBe false
      }
    }

    "return an item with MarriageAllowanceTransferred" in {
      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any())).thenReturn(
        Future.successful(List("MarriageAllowanceTransferred"))
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(false))

      val req    = buildRequest(NonFilerSelfAssessmentUser)
      val result = service.getMyServices(req).futureValue

      result.find(_.gaLabel.contains("Marriage Allowance")) mustBe Some(
        MyService(
          "Marriage Allowance",
          "/marriage-allowance-application/history",
          "You currently transfer part of your Personal Allowance to your partner.",
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
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))
      when(mockFandFService.isAnyFandFRelationships(any())(any())).thenReturn(Future.successful(false))

      val req    = buildRequest(NonFilerSelfAssessmentUser)
      val result = service.getMyServices(req).futureValue

      result.find(_.gaLabel.contains("Marriage Allowance")) mustBe Some(
        MyService(
          "Marriage Allowance",
          "/marriage-allowance-application/history",
          "Your partner currently transfers part of their Personal Allowance to you.",
          Map(),
          Some("Benefits"),
          Some("Marriage Allowance"),
          None
        )
      )
    }
  }
}