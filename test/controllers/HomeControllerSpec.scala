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

package controllers

import cats.data.EitherT
import config.ConfigDecorator
import connectors.{PreferencesFrontendConnector, TaiConnector, TaxCalculationConnector}
import controllers.auth.AuthJourney
import controllers.controllershelpers.HomePageCachingHelper
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models._
import models.admin.{FeatureFlag, NationalInsuranceTileToggle, TaxcalcToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.Application
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import services.admin.FeatureFlagService
import services.partials.MessageFrontendService
import testUtils.Fixtures._
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}
import controllers.bindable.Origin
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate
import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with CurrentTaxYear {

  val mockConfigDecorator                     = mock[ConfigDecorator]
  val mockTaxCalculationService               = mock[TaxCalculationConnector]
  val mockTaiService                          = mock[TaiConnector]
  val mockSeissService                        = mock[SeissService]
  val mockMessageFrontendService              = mock[MessageFrontendService]
  val mockPreferencesFrontendConnector        = mock[PreferencesFrontendConnector]
  val mockIdentityVerificationFrontendService = mock[IdentityVerificationFrontendService]
  val mockLocalSessionCache                   = mock[LocalSessionCache]
  val mockAuthJourney                         = mock[AuthJourney]
  val mockHomePageCachingHelper               = mock[HomePageCachingHelper]
  val mockBreathingSpaceService               = mock[BreathingSpaceService]
  val mockFeatureFlagService                  = mock[FeatureFlagService]

  override def beforeEach: Unit =
    reset(
      mockConfigDecorator,
      mockTaxCalculationService,
      mockTaiService,
      mockMessageFrontendService,
      mockHomePageCachingHelper,
      mockFeatureFlagService
    )

  override def now: () => LocalDate = LocalDate.now

  trait LocalSetup {

    lazy val authProviderType: String             = UserDetails.GovernmentGatewayAuthProvider
    lazy val nino: Nino                           = Fixtures.fakeNino
    lazy val personDetailsResponse: PersonDetails = Fixtures.buildPersonDetails
    lazy val confidenceLevel: ConfidenceLevel     = ConfidenceLevel.L200
    lazy val withPaye: Boolean                    = true
    lazy val year                                 = 2017

    lazy val getPaperlessPreferenceResponse: EitherT[Future, UpstreamErrorResponse, HttpResponse]             =
      EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(OK, ""))))
    lazy val getIVJourneyStatusResponse: EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
      EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(Success)))
    lazy val getCitizenDetailsResponse                                                                        = true
    lazy val selfAssessmentUserType: SelfAssessmentUserType                                                   = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr(new SaUtrGenerator().nextSaUtr.utr)
    )
    lazy val getLtaServiceResponse                                                                            = Future.successful(true)

    lazy val allowLowConfidenceSA = false

    val taxComponentsJson: String =
      """{
        |   "data" : [ {
        |      "componentType" : "EmployerProvidedServices",
        |      "employmentId" : 12,
        |      "amount" : 12321,
        |      "description" : "Some Description",
        |      "iabdCategory" : "Benefit"
        |   }, {
        |      "componentType" : "PersonalPensionPayments",
        |      "employmentId" : 31,
        |      "amount" : 12345,
        |      "description" : "Some Description Some",
        |      "iabdCategory" : "Allowance"
        |   } ],
        |   "links" : [ ]
        |}""".stripMargin

    when(mockTaiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier], any())) thenReturn {
      EitherT[Future, UpstreamErrorResponse, HttpResponse](
        Future.successful(Right(HttpResponse(OK, taxComponentsJson)))
      )
    }
    when(mockSeissService.hasClaims(ActivatedOnlineFilerSelfAssessmentUser(any()))(any())) thenReturn Future.successful(
      true
    )
    when(mockSeissService.hasClaims(NotYetActivatedOnlineFilerSelfAssessmentUser(any()))(any())) thenReturn Future
      .successful(true)
    when(mockSeissService.hasClaims(WrongCredentialsSelfAssessmentUser(any()))(any())) thenReturn Future.successful(
      true
    )
    when(mockSeissService.hasClaims(NotEnrolledSelfAssessmentUser(any()))(any())) thenReturn Future.successful(true)
    when(mockSeissService.hasClaims(NonFilerSelfAssessmentUser)) thenReturn Future.successful(false)

    when(mockTaxCalculationService.getTaxYearReconciliations(any[Nino])(any[HeaderCarrier])).thenReturn(
      EitherT[Future, UpstreamErrorResponse, List[TaxYearReconciliation]](
        Future.successful(Right(buildTaxYearReconciliations))
      )
    )

    when(mockPreferencesFrontendConnector.getPaperlessPreference()(any())) thenReturn {
      getPaperlessPreferenceResponse
    }
    when(mockIdentityVerificationFrontendService.getIVJourneyStatus(any())(any(), any())) thenReturn {
      getIVJourneyStatusResponse
    }

    when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(false))

    when(mockMessageFrontendService.getUnreadMessageCount(any())) thenReturn {
      Future.successful(None)
    }

    when(mockConfigDecorator.enforcePaperlessPreferenceEnabled) thenReturn true
    when(mockConfigDecorator.taxComponentsEnabled) thenReturn true
    when(mockConfigDecorator.ltaEnabled) thenReturn true
    when(mockConfigDecorator.allowSaPreview) thenReturn true
    when(mockConfigDecorator.allowLowConfidenceSAEnabled) thenReturn allowLowConfidenceSA
    when(mockConfigDecorator.identityVerificationUpliftUrl) thenReturn "/mdtp/uplift"
    when(mockConfigDecorator.pertaxFrontendHost) thenReturn ""
    when(
      mockConfigDecorator.getBasGatewayFrontendSignOutUrl("/personal-account")
    ) thenReturn "/bas-gateway/sign-out-without-state?continue=/personal-account"
    when(
      mockConfigDecorator.getBasGatewayFrontendSignOutUrl("/feedback/PERTAX")
    ) thenReturn "/bas-gateway/sign-out-without-state?continue=/feedback/PERTAX"
    when(mockConfigDecorator.defaultOrigin) thenReturn Origin("PERTAX")
    when(mockConfigDecorator.getFeedbackSurveyUrl(Origin("PERTAX"))) thenReturn "/feedback/PERTAX"
    when(
      mockConfigDecorator.ssoToActivateSaEnrolmentPinUrl
    ) thenReturn "/bas-gateway/ssoout/non-digital?continue=%2Fservice%2Fself-assessment%3Faction=activate&step=enteractivationpin"
    when(mockConfigDecorator.ssoUrl) thenReturn Some("ssoUrl")
    when(mockConfigDecorator.bannerHomePageIsEnabled) thenReturn false
    when(mockConfigDecorator.rlsInterruptToggle) thenReturn true
    when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any())) thenReturn Future.successful(
      WithinPeriod
    )

  }

  "Calling HomeController.index" must {

    "return a 200 status when accessing index page with good nino and sa User" in new LocalSetup {

      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(false, false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> true,
          "feature.taxcalc.enabled"        -> true
        )
        .overrides(bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper))
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))
      status(r) mustBe OK

      verify(mockTaiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any(), any())
      verify(mockTaxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(NonFilerSelfAssessmentUser)
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService)
        )
        .configure(
          "feature.tax-components.enabled" -> true,
          "feature.taxcalc.enabled"        -> true
        )
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))
      status(r) mustBe OK

      verify(mockTaiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any(), any())
      verify(mockTaxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User and tai/taxcalc are disabled" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, false)
      )

      lazy val app: Application = localGuiceApplicationBuilder(NonFilerSelfAssessmentUser)
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> false
        )
        .overrides(bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper))
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))
      status(r) mustBe OK

      verify(mockTaiService, times(0)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any(), any())
      verify(mockTaxCalculationService, times(0)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return 200 when Preferences Frontend returns ActivatePaperlessNotAllowedResponse" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      override lazy val getPaperlessPreferenceResponse =
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
        )

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))
      status(r) mustBe OK

    }

    "redirect when Preferences Frontend returns ActivatePaperlessRequiresUserActionResponse" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector)
        )
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      override lazy val getPaperlessPreferenceResponse =
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(
            Right(HttpResponse(PRECONDITION_FAILED, """{"redirectUserTo": "http://www.example.com"}""".stripMargin))
          )
        )

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))
      status(r) mustBe SEE_OTHER
      redirectLocation(r) mustBe Some("http://www.example.com")
    }

    "return 200 when TaxCalculationService returns TaxCalculationNotFoundResponse" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService)
        )
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))
      status(r) mustBe OK

      verify(mockTaxCalculationService, times(1)).getTaxYearReconciliations(meq(Fixtures.fakeNino))(any())
    }

    "return a 200 status when accessing index page with a nino that does not map to any personal details in citizen-details" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))
      status(r) mustBe OK
    }

    "return a 303 status when both the user's residential and postal addresses status are rls" in new LocalSetup {

      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(false, false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = true)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)),
            person = buildFakePerson
          )
        )
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

      status(r) mustBe SEE_OTHER
    }

    "return a 200 status when both the user's residential and postal addresses status are rls but both addresses have been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(true, true)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = true)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)),
            person = buildFakePerson
          )
        )
      ).overrides(
        bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

      status(r) mustBe OK
    }

    "return a 303 status when the user's residential address status is rls" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(false, false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = true)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = false)),
            person = buildFakePerson
          )
        )
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

      status(r) mustBe SEE_OTHER
    }

    "return a 200 status when the user's residential address status is rls but address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(true, false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = true)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = false)),
            person = buildFakePerson
          )
        )
      ).overrides(
        bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

      status(r) mustBe OK
    }

    "return a 303 status when the user's postal address status is rls" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(false, false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = false)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)),
            person = buildFakePerson
          )
        )
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest())

      status(r) mustBe SEE_OTHER
    }

    "return a 200 status when the user's postal address status is rls but address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(false, true)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = false)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)),
            person = buildFakePerson
          )
        )
      ).overrides(
        bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

      status(r) mustBe OK
    }

    "return a 303 status when the user's residential and postal address status is rls but residential address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(true, false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = true)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)),
            person = buildFakePerson
          )
        )
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest())

      status(r) mustBe SEE_OTHER
    }

    "return a 303 status when the user's residential and postal address status is rls but postal address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(false, true)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        personDetails = Some(
          PersonDetails(
            address = Some(buildFakeAddress.copy(isRls = true)),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress.copy(isRls = true)),
            person = buildFakePerson
          )
        )
      ).build()

      val controller = app.injector.instanceOf[HomeController]

      val r: Future[Result] = controller.index()(FakeRequest())

      status(r) mustBe SEE_OTHER
    }

  }

  "banner is present" when {
    "it is enabled and user has not closed it" in new LocalSetup {
      when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(false))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        NonFilerSelfAssessmentUser,
        Some(
          PersonDetails(
            address = Some(buildFakeAddress),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress),
            person = buildFakePerson
          )
        )
      ).overrides(
        bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      ).configure(
        "feature.banner.home.enabled" -> true
      ).build()

      val configDecorator = injected[ConfigDecorator]

      val r: Future[Result] =
        app.injector
          .instanceOf[HomeController]
          .index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

      status(r) mustBe OK
      contentAsString(r) must include(configDecorator.bannerHomePageLinkUrl.replaceAll("&", "&amp;"))
      contentAsString(r) must include(configDecorator.bannerHomePageHeadingEn)
      contentAsString(r) must include(configDecorator.bannerHomePageLinkTextEn)
    }
  }

  "banner is not present" when {
    "it is not enabled" in new LocalSetup {
      when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(false))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        NonFilerSelfAssessmentUser,
        Some(
          PersonDetails(
            address = Some(buildFakeAddress),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress),
            person = buildFakePerson
          )
        )
      ).overrides(
        bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      ).configure(
        "feature.banner.home.enabled" -> false
      ).build()

      val configDecorator = injected[ConfigDecorator]

      val r: Future[Result] =
        app.injector
          .instanceOf[HomeController]
          .index()(FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

      status(r) mustBe OK

      contentAsString(r) mustNot include(configDecorator.bannerHomePageLinkUrl)
      contentAsString(r) mustNot include(configDecorator.bannerHomePageHeadingEn)
      contentAsString(r) mustNot include(configDecorator.bannerHomePageLinkTextEn)
    }
    "it is enabled and user has closed it" in new LocalSetup {
      when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(
        NonFilerSelfAssessmentUser,
        Some(
          PersonDetails(
            address = Some(buildFakeAddress),
            correspondenceAddress = Some(buildFakeCorrespondenceAddress),
            person = buildFakePerson
          )
        )
      ).overrides(
        bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      ).configure(
        "feature.banner.home.enabled" -> true
      ).build()

      val configDecorator = injected[ConfigDecorator]

      val r: Future[Result] =
        app.injector
          .instanceOf[HomeController]
          .index()(
            FakeRequest()
              .withSession("sessionId" -> "FAKE_SESSION_ID")
              .withHeaders(HeaderNames.authorisation -> "Bearer 1")
          )

      status(r) mustBe OK
      contentAsString(r) mustNot include(configDecorator.bannerHomePageLinkUrl)
      contentAsString(r) mustNot include(configDecorator.bannerHomePageHeadingEn)
      contentAsString(r) mustNot include(configDecorator.bannerHomePageLinkTextEn)
    }
  }

  "Calling serviceCallResponses" must {

    val userNino = Some(fakeNino)

    "return TaxComponentsDisabled where taxComponents is not enabled" in new LocalSetup {
      when(mockTaiService.taxComponents(any(), any())(any(), any())).thenReturn(null)
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[TaiConnector].toInstance(mockTaiService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> false,
          "feature.taxcalc.enabled"        -> true
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result mustBe TaxComponentsDisabledState
      verify(mockTaiService, times(0)).taxComponents(any(), any())(any(), any())
    }

    "return TaxCalculationAvailable status when data returned from TaxCalculation" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[TaiConnector].toInstance(mockTaiService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> true,
          "feature.taxcalc.enabled"        -> true
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))
      result mustBe TaxComponentsAvailableState(
        TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments"))
      )
      verify(mockTaiService, times(1)).taxComponents(any(), any())(any(), any())

    }

    "return TaxComponentsNotAvailableState status when TaxComponentsUnavailableResponse from TaxComponents" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[TaiConnector].toInstance(mockTaiService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> true,
          "feature.taxcalc.enabled"        -> true
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      when(mockTaiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier], any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", BAD_REQUEST)))
        )
      }

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result mustBe TaxComponentsNotAvailableState
      verify(mockTaiService, times(1)).taxComponents(any(), any())(any(), any())
      verify(mockTaxCalculationService, times(1)).getTaxYearReconciliations(any())(any())
    }

    "return TaxComponentsUnreachableState status when there is TaxComponents returns an unexpected response" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[TaiConnector].toInstance(mockTaiService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> true,
          "feature.taxcalc.enabled"        -> true
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      when(mockTaiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier], any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
        )
      }

      val (result, _, _) = await(controller.serviceCallResponses(userNino, year))

      result mustBe TaxComponentsUnreachableState
    }

    "return None where TaxCalculation service is not enabled" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, false)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[TaiConnector].toInstance(mockTaiService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> true
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val (_, resultCYm1, resultCYm2) = await(controller.serviceCallResponses(userNino, year))

      resultCYm1 mustBe None
      resultCYm2 mustBe None
    }

    "return only  CY-1 None and CY-2 None when get TaxYearReconcillation returns Nil" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[TaiConnector].toInstance(mockTaiService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> true,
          "feature.taxcalc.enabled"        -> true
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      when(mockTaxCalculationService.getTaxYearReconciliations(any[Nino])(any[HeaderCarrier])).thenReturn(
        EitherT[Future, UpstreamErrorResponse, List[TaxYearReconciliation]](
          Future.successful(Left(UpstreamErrorResponse("", NOT_FOUND)))
        )
      )

      val (_, resultCYM1, resultCYM2) = await(controller.serviceCallResponses(userNino, year))

      resultCYM1 mustBe None
      resultCYM2 mustBe None
    }

    "return taxCalculation for CY1 and CY2 status from list returned from TaxCalculation Service." in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaxCalculationConnector].toInstance(mockTaxCalculationService),
          bind[TaiConnector].toInstance(mockTaiService),
          bind[FeatureFlagService].toInstance(mockFeatureFlagService)
        )
        .configure(
          "feature.tax-components.enabled" -> true,
          "feature.taxcalc.enabled"        -> true
        )
        .build()

      val controller = app.injector.instanceOf[HomeController]

      val (_, resultCYM1, resultCYM2) = await(controller.serviceCallResponses(userNino, year))

      resultCYM1 mustBe Some(TaxYearReconciliation(2016, Balanced))
      resultCYM2 mustBe Some(TaxYearReconciliation(2015, Balanced))
    }
  }
}
