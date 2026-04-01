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
import models.admin.ShowTaxCalcTileToggle
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import testUtils.BaseSpec
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.sca.models.TrustedHelper
import uk.gov.hmrc.time.TaxYear

import scala.concurrent.Future

class HomePageServicesProviderSpec extends BaseSpec {

  private val mockConfigDecorator: ConfigDecorator       = mock[ConfigDecorator]
  private val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  private val mockFandFService: FandFService             = mock[FandFService]
  private val mockTaiService: TaiService                 = mock[TaiService]

  private lazy val service =
    new HomePageServicesProvider(
      mockConfigDecorator,
      mockFeatureFlagService,
      mockFandFService,
      mockTaiService
    )

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  private def buildRequest(
    saUserType: SelfAssessmentUserType = NonFilerSelfAssessmentUser,
    trustedHelper: Option[uk.gov.hmrc.sca.models.TrustedHelper] = None
  ): UserRequest[AnyContent] =
    UserRequest(
      authNino = generatedNino,
      saUserType = saUserType,
      credentials = Credentials("credId", "GovernmentGateway"),
      confidenceLevel = ConfidenceLevel.L200,
      trustedHelper = trustedHelper,
      enrolments = Set.empty,
      profile = None,
      breadcrumb = None,
      request = FakeRequest(),
      userAnswers = UserAnswers.empty
    )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockConfigDecorator)
    reset(mockFeatureFlagService)
    reset(mockFandFService)
    reset(mockTaiService)

    when(mockFeatureFlagService.get(eqTo(ShowTaxCalcTileToggle)))
      .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))

    when(mockTaiService.getTaxComponentsList(any(), any())(any(), any()))
      .thenReturn(Future.successful(List.empty))

    when(mockFandFService.isAnyFandFRelationships(any())(any()))
      .thenReturn(Future.successful(false))

    when(mockConfigDecorator.taxCalcHomePageUrl).thenReturn("taxcalc/")
    when(mockConfigDecorator.taxCalcYearsToShow).thenReturn(4)
    when(mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl).thenReturn("activate-sa-url")
    when(mockConfigDecorator.annualTaxSaSummariesTileLinkShow).thenReturn("ats/")
    when(mockConfigDecorator.manageTrustedHelpersUrl).thenReturn("trusted-helpers-url")
  }

  "getHomePageServices" must {

    "return full homepage services for an activated SA user when not a trusted helper" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")))

      when(mockFeatureFlagService.get(eqTo(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))

      val result = service.getHomePageServices.futureValue

      result.myServices mustBe Seq(
        MyService(
          "Pay As You Earn (PAYE)",
          controllers.routes.RedirectToPayeController.redirectToPaye.url,
          "",
          Map(),
          Some("Income"),
          Some("Pay As You Earn (PAYE)")
        ),
        MyService(
          s"Your tax calculation — PAYE ${TaxYear.current.back(4).startYear} to ${TaxYear.current.startYear}",
          "taxcalc/",
          "",
          Map(),
          Some("Income"),
          Some("Tax Calculation")
        ),
        MyService(
          "Self Assessment",
          controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url,
          "",
          Map(),
          Some("Income"),
          Some("Self Assessment")
        ),
        MyService(
          "National Insurance and State Pension",
          controllers.interstitials.routes.InterstitialController.displayNISP.url,
          "",
          Map(),
          Some("Income"),
          Some("National Insurance and State Pension"),
          None
        )
      )

      result.otherServices mustBe Seq(
        OtherService(
          "Making Tax Digital for Income Tax",
          controllers.interstitials.routes.MtdAdvertInterstitialController.displayMTDITPage.url,
          Map(),
          Some("MTDIT"),
          Some("Making Tax Digital for Income Tax")
        ),
        OtherService(
          "Child Benefit",
          controllers.interstitials.routes.InterstitialController.displayChildBenefitsSingleAccountView.url,
          Map(),
          Some("Benefits"),
          Some("Child Benefit")
        ),
        OtherService(
          "Annual Tax Summary",
          "ats/",
          Map(),
          Some("Tax Summaries"),
          Some("Annual Tax Summary")
        ),
        OtherService(
          "Marriage Allowance",
          "/marriage-allowance-application/history",
          Map(),
          Some("Benefits"),
          Some("Marriage Allowance"),
          None
        ),
        OtherService(
          "Trusted helpers",
          "trusted-helpers-url",
          Map(),
          Some("Account"),
          Some("Trusted helpers"),
          None
        )
      )
    }

    "return self assessment in myServices for activated online filer" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")))

      val result = service.getHomePageServices.futureValue

      result.myServices.map(_.link)    must contain(
        controllers.interstitials.routes.InterstitialController.displaySelfAssessment.url
      )
      result.otherServices.map(_.link) must not contain
        controllers.routes.SelfAssessmentController.requestAccess.url
    }

    "return self assessment in myServices for wrong credentials user" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(WrongCredentialsSelfAssessmentUser(SaUtr("11")))

      val result = service.getHomePageServices.futureValue

      result.myServices must contain(
        MyService(
          "Self Assessment",
          controllers.routes.SaWrongCredentialsController.landingPage().url,
          messages("title.signed_in_wrong_account.h1"),
          Map(),
          Some("Income"),
          Some("Self Assessment")
        )
      )
    }

    "return self assessment in otherServices for not yet activated SA user" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")))

      val result = service.getHomePageServices.futureValue

      result.otherServices must contain(
        OtherService(
          "Self Assessment",
          "activate-sa-url",
          Map(),
          Some("Income"),
          Some("Self Assessment"),
          None
        )
      )

      result.myServices.map(_.title) must not contain "Self Assessment"
    }

    "return self assessment in otherServices for not enrolled SA user" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(NotEnrolledSelfAssessmentUser(SaUtr("11")))

      val result = service.getHomePageServices.futureValue

      result.otherServices           must contain(
        OtherService(
          "Self Assessment",
          controllers.routes.SelfAssessmentController.requestAccess.url,
          Map(),
          Some("Income"),
          Some("Self Assessment"),
          None
        )
      )
      result.myServices.map(_.title) must not contain "Self Assessment"
    }

    "not return any self assessment service for non filer user" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(NonFilerSelfAssessmentUser)

      val result = service.getHomePageServices.futureValue

      result.myServices.map(_.title)   must not contain "Self Assessment"
      result.otherServices.map(_.link) must not contain
        controllers.routes.SelfAssessmentController.requestAccess.url
    }

    "return no tax calculation service when trusted helper is active" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(
          ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
          trustedHelper =
            Some(TrustedHelper("principal", "attorney", "return-url", Some(generatedTrustedHelperNino.nino)))
        )

      val result = service.getHomePageServices.futureValue

      result.myServices.map(_.gaLabel) must not contain Some("Tax Calculation")
      verify(mockFeatureFlagService, times(0)).get(eqTo(ShowTaxCalcTileToggle))
    }

    "return no self assessment, child benefit, annual tax summary, marriage allowance or trusted helpers when trusted helper is active" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest(
          ActivatedOnlineFilerSelfAssessmentUser(SaUtr("11")),
          trustedHelper =
            Some(TrustedHelper("principal", "attorney", "return-url", Some(generatedTrustedHelperNino.nino)))
        )

      val result = service.getHomePageServices.futureValue

      result.myServices mustBe Seq(
        MyService(
          "Pay As You Earn (PAYE)",
          controllers.routes.RedirectToPayeController.redirectToPaye.url,
          "",
          Map(),
          Some("Income"),
          Some("Pay As You Earn (PAYE)")
        ),
        MyService(
          "National Insurance and State Pension",
          controllers.interstitials.routes.InterstitialController.displayNISP.url,
          "",
          Map(),
          Some("Income"),
          Some("National Insurance and State Pension"),
          None
        )
      )

      result.otherServices mustBe Seq.empty

      verify(mockTaiService, times(0)).getTaxComponentsList(any(), any())(any(), any())
      verify(mockFandFService, times(0)).isAnyFandFRelationships(any())(any())
    }

    "return no tax calculation service when feature flag is disabled" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest()

      when(mockFeatureFlagService.get(eqTo(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = false)))

      val result = service.getHomePageServices.futureValue

      result.myServices.map(_.gaLabel) must not contain Some("Tax Calculation")
    }

    "return tax calculation service when feature flag is enabled" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest()

      when(mockFeatureFlagService.get(eqTo(ShowTaxCalcTileToggle)))
        .thenReturn(Future.successful(FeatureFlag(ShowTaxCalcTileToggle, isEnabled = true)))

      val result = service.getHomePageServices.futureValue

      result.myServices must contain(
        MyService(
          s"Your tax calculation — PAYE ${TaxYear.current.back(4).startYear} to ${TaxYear.current.startYear}",
          "taxcalc/",
          "",
          Map(),
          Some("Income"),
          Some("Tax Calculation")
        )
      )
    }

    "return marriage allowance in myServices when MarriageAllowanceTransferred tax component exists" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest()

      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any()))
        .thenReturn(Future.successful(List("MarriageAllowanceTransferred")))

      val result = service.getHomePageServices.futureValue

      result.myServices must contain(
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

      result.otherServices.map(_.title) must not contain "Marriage Allowance"
    }

    "return marriage allowance in myServices when MarriageAllowanceReceived tax component exists" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest()

      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any()))
        .thenReturn(Future.successful(List("MarriageAllowanceReceived")))

      val result = service.getHomePageServices.futureValue

      result.myServices must contain(
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

      result.otherServices.map(_.title) must not contain "Marriage Allowance"
    }

    "return marriage allowance in otherServices when there are no marriage allowance tax components" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest()

      when(mockTaiService.getTaxComponentsList(any(), any())(any(), any()))
        .thenReturn(Future.successful(List.empty))

      val result = service.getHomePageServices.futureValue

      result.otherServices must contain(
        OtherService(
          "Marriage Allowance",
          "/marriage-allowance-application/history",
          Map(),
          Some("Benefits"),
          Some("Marriage Allowance"),
          None
        )
      )
    }

    "return trusted helpers in myServices when fandf relationships exist" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest()

      when(mockFandFService.isAnyFandFRelationships(any())(any()))
        .thenReturn(Future.successful(true))

      val result = service.getHomePageServices.futureValue

      result.myServices must contain(
        MyService(
          "Trusted helpers",
          "trusted-helpers-url",
          "",
          Map(),
          Some("Account"),
          Some("Trusted helpers"),
          None
        )
      )

      result.otherServices.map(_.title) must not contain "Trusted helpers"
    }

    "return trusted helpers in otherServices when fandf relationships do not exist" in {
      implicit val request: UserRequest[AnyContent] =
        buildRequest()

      when(mockFandFService.isAnyFandFRelationships(any())(any()))
        .thenReturn(Future.successful(false))

      val result = service.getHomePageServices.futureValue

      result.otherServices must contain(
        OtherService(
          "Trusted helpers",
          "trusted-helpers-url",
          Map(),
          Some("Account"),
          Some("Trusted helpers"),
          None
        )
      )

      result.myServices.map(_.title) must not contain "Trusted helpers"
    }
  }
}
