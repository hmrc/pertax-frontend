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
import connectors.{PreferencesFrontendConnector, TaiConnector}
import controllers.auth.{AuthJourney, AuthRetrievals}
import controllers.auth.requests.UserRequest
import controllers.bindable.Origin
import controllers.controllershelpers.{HomeCardGenerator, HomePageCachingHelper, PaperlessInterruptHelper, RlsInterruptHelper}
import models.BreathingSpaceIndicatorResponse.WithinPeriod
import models._
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Results._
import play.api.mvc.{Result, _}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services._
import services.partials.MessageFrontendService
import testUtils.Fixtures._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec, Fixtures, WireMockHelper}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.{Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.time.CurrentTaxYear
import util.AlertBannerHelper
import views.html.HomeView

import java.time.LocalDate
import scala.concurrent.Future

class HomeControllerSpec extends BaseSpec with CurrentTaxYear with WireMockHelper {

  override def fakeApplication(): Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port"   -> server.port(),
      "microservice.services.pertax.port" -> server.port()
    )
    .overrides(
      bind[AuthRetrievals].toInstance(mockAuthAction),
      bind[LocalSessionCache].toInstance(mockLocalSessionCache),
      bind[TaiConnector].toInstance(mockTaiService),
      bind[HomeCardGenerator].toInstance(mockHomeCardGenerator),
      bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper)
    )
    .build()

  val mockAuthAction: AuthRetrievals                                               = mock[AuthRetrievals]
  val mockConfigDecorator: ConfigDecorator                                         = mock[ConfigDecorator]
  val mockTaiService: TaiConnector                                                 = mock[TaiConnector]
  val mockSeissService: SeissService                                               = mock[SeissService]
  val mockMessageFrontendService: MessageFrontendService                           = mock[MessageFrontendService]
  val mockPreferencesFrontendConnector: PreferencesFrontendConnector               = mock[PreferencesFrontendConnector]
  val mockIdentityVerificationFrontendService: IdentityVerificationFrontendService =
    mock[IdentityVerificationFrontendService]
  val mockLocalSessionCache: LocalSessionCache                                     = mock[LocalSessionCache]
  val mockHomePageCachingHelper: HomePageCachingHelper                             = mock[HomePageCachingHelper]
  val mockBreathingSpaceService: BreathingSpaceService                             = mock[BreathingSpaceService]
  val mockHomeCardGenerator: HomeCardGenerator                                     = mock[HomeCardGenerator]
  val mockPaperlessInterruptHelper: PaperlessInterruptHelper                       = mock[PaperlessInterruptHelper]
  val mockTaiConnector: TaiConnector                                               = mock[TaiConnector]
  val mockRlsInterruptHelper: RlsInterruptHelper                                   = mock[RlsInterruptHelper]
  val mockAlertBannerHelper: AlertBannerHelper                                     = mock[AlertBannerHelper]

  lazy val authProviderType: String             = UserDetails.GovernmentGatewayAuthProvider
  lazy val nino: Nino                           = Fixtures.fakeNino
  lazy val personDetailsResponse: PersonDetails = Fixtures.buildPersonDetails
  lazy val confidenceLevel: ConfidenceLevel     = ConfidenceLevel.L200
  lazy val withPaye: Boolean                    = true
  lazy val year                                 = 2017
  lazy val trustedHelper: Option[TrustedHelper] = None

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockConfigDecorator,
      mockTaiService,
      mockMessageFrontendService,
      mockHomePageCachingHelper,
      mockHomeCardGenerator,
      mockPreferencesFrontendConnector,
      mockAuthAction,
      mockAuthJourney
    )
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
      FeatureFlag(TaxcalcToggle, isEnabled = true)
    )
  }

  override def now: () => LocalDate = () => LocalDate.now()

  private def personDetailsForRequest: Option[PersonDetails] = Some(buildPersonDetailsCorrespondenceAddress)

  trait LocalSetup {

    val mockAuthJourney: AuthJourney = mock[AuthJourney]

    def currentRequest[A]: Request[A] =
      FakeRequest()
        .withSession("sessionId" -> "FAKE_SESSION_ID")
        .asInstanceOf[Request[A]]

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request)
        )
    })

    lazy val getPaperlessPreferenceResponse: EitherT[Future, UpstreamErrorResponse, HttpResponse]             =
      EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(OK, ""))))
    lazy val getIVJourneyStatusResponse: EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse] =
      EitherT[Future, UpstreamErrorResponse, IdentityVerificationResponse](Future.successful(Right(Success)))
    lazy val getCitizenDetailsResponse                                                                        = true
    lazy val selfAssessmentUserType: SelfAssessmentUserType                                                   = ActivatedOnlineFilerSelfAssessmentUser(
      SaUtr(new SaUtrGenerator().nextSaUtr.utr)
    )
    lazy val getLtaServiceResponse: Future[Boolean]                                                           = Future.successful(true)

    lazy val allowLowConfidenceSA = false
    lazy val dummyHtml: Html      = Html("""<p>income</p>""")

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

    when(mockPreferencesFrontendConnector.getPaperlessPreference()(any())) thenReturn {
      getPaperlessPreferenceResponse
    }
    when(mockIdentityVerificationFrontendService.getIVJourneyStatus(any())(any(), any())) thenReturn {
      getIVJourneyStatusResponse
    }

    when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(false))

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
    when(mockBreathingSpaceService.getBreathingSpaceIndicator(any())(any(), any())) thenReturn Future.successful(
      WithinPeriod
    )

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle))) thenReturn Future.successful(
      FeatureFlag(TaxComponentsToggle, isEnabled = true)
    )
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle))) thenReturn Future.successful(
      FeatureFlag(RlsInterruptToggle, isEnabled = true)
    )
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PaperlessInterruptToggle))) thenReturn Future.successful(
      FeatureFlag(PaperlessInterruptToggle, isEnabled = true)
    )
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle))) thenReturn Future.successful(
      FeatureFlag(TaxSummariesTileToggle, isEnabled = false)
    )
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(ShowOutageBannerToggle))) thenReturn Future.successful(
      FeatureFlag(ShowOutageBannerToggle, isEnabled = true)
    )
    when(mockHomeCardGenerator.getIncomeCards(any())(any(), any()))
      .thenReturn(Future.successful(Seq(dummyHtml)))
    when(mockHomeCardGenerator.getPensionCards()(any())).thenReturn(Future.successful(List(dummyHtml)))
    when(mockHomeCardGenerator.getBenefitCards(any(), any())(any())).thenReturn(List(dummyHtml))

    val okBlock: Result = Ok("Block")

    when(mockRlsInterruptHelper.enforceByRlsStatus(any())(any(), any()))
      .thenReturn(Future(okBlock))

  }

  "Calling HomeController.index" must {

    "return a 200 status when accessing index page with good nino and sa User" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator),
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper)
        )
        .build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)

      status(result) mustBe OK

      verify(mockTaiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any(), any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder(NonFilerSelfAssessmentUser)
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService)
        )
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)
      status(result) mustBe OK

      verify(mockTaiService, times(1)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any(), any())
    }

    "return a 200 status when accessing index page with good nino and a non sa User and tai/taxcalc are disabled" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = false)
      )

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = false)
      )

      lazy val app: Application = localGuiceApplicationBuilder(NonFilerSelfAssessmentUser)
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)
      status(result) mustBe OK

      verify(mockTaiService, times(0)).taxComponents(meq(Fixtures.fakeNino), meq(current.currentYear))(any(), any())
    }

    "return 200 when Preferences Frontend returns ActivatePaperlessNotAllowedResponse" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      override lazy val getPaperlessPreferenceResponse: EitherT[Future, UpstreamErrorResponse, HttpResponse] =
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
        )

      val result: Future[Result] = controller.index()(currentRequest)
      status(result) mustBe OK

    }

    "redirect when Preferences Frontend returns ActivatePaperlessRequiresUserActionResponse" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector)
        )
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      override lazy val getPaperlessPreferenceResponse: EitherT[Future, UpstreamErrorResponse, HttpResponse] =
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(
            Right(HttpResponse(PRECONDITION_FAILED, """{"redirectUserTo": "http://www.example.com"}""".stripMargin))
          )
        )

      val result: Future[Result] = controller.index()(currentRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://www.example.com")
    }

    "return 200 when TaxCalculationService returns TaxCalculationNotFoundResponse" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[HomePageCachingHelper].toInstance(mockHomePageCachingHelper),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)
      status(result) mustBe OK

    }

    "return a 200 status when accessing index page with a nino that does not map to any personal details in citizen-details" in new LocalSetup {

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      def saUserType: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(Fixtures.saUtr)

      when(mockAuthJourney.authWithPersonalDetails)
        .thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                request = currentRequest[A],
                personDetails = personDetailsForRequest,
                saUser = saUserType
              )
            )
        })

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)
      //status(result) mustBe OK
      redirectLocation(result) mustBe Some("/personal-account/your-address/change-address-tax-credits")

    }

    "return a 303 status when both the user's residential and postal addresses status are rls" in new LocalSetup {

      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)

      status(result) mustBe SEE_OTHER
    }

    "return a 200 status when both the user's residential and postal addresses status are rls but both addresses have been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = true, postal = true)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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
        bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
      ).build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)

      status(result) mustBe OK
    }

    "return a 303 status when the user's residential address status is rls" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)

      status(result) mustBe SEE_OTHER
    }

    "return a 200 status when the user's residential address status is rls but address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = true, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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
        bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
      ).build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)

      status(result) mustBe OK
    }

    "return a 303 status when the user's postal address status is rls" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(FakeRequest())

      status(result) mustBe SEE_OTHER
    }

    "return a 200 status when the user's postal address status is rls but address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = true)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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
        bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
      ).build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(currentRequest)

      status(result) mustBe OK
    }

    "return a 303 status when the user's residential and postal address status is rls but residential address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = true, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(FakeRequest())

      status(result) mustBe SEE_OTHER
    }

    "return a 303 status when the user's residential and postal address status is rls but postal address has been updated" in new LocalSetup {
      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = true)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: Future[Result] = controller.index()(FakeRequest())

      status(result) mustBe SEE_OTHER
    }

  }

  "banner is present" when {
    "it is enabled and user has not closed it" in new LocalSetup {
      when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(false))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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
        bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
      ).configure(
        "feature.banner.home.enabled" -> true
      ).build()

      val result: Future[Result] = app.injector
        .instanceOf[HomeController]
        .index()(currentRequest)

      status(result) mustBe OK
      contentAsString(result) must include(config.bannerHomePageLinkUrl.replaceAll("&", "&amp;"))
      contentAsString(result) must include(config.bannerHomePageHeadingEn)
      contentAsString(result) must include(config.bannerHomePageLinkTextEn)
    }
  }

  "banner is not present" when {
    "it is not enabled" in new LocalSetup {
      when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(false))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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
        bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
      ).configure(
        "feature.banner.home.enabled" -> false
      ).build()

      val result: Future[Result] =
        app.injector
          .instanceOf[HomeController]
          .index()(currentRequest)

      status(result) mustBe OK

      contentAsString(result) mustNot include(config.bannerHomePageLinkUrl)
      contentAsString(result) mustNot include(config.bannerHomePageHeadingEn)
      contentAsString(result) mustNot include(config.bannerHomePageLinkTextEn)
    }

    "it is enabled and user has closed it" in new LocalSetup {
      when(mockHomePageCachingHelper.hasUserDismissedBanner(any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
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
        bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
      ).configure(
        "feature.banner.home.enabled" -> true
      ).build()

      val result: Future[Result] =
        app.injector
          .instanceOf[HomeController]
          .index()(
            FakeRequest()
              .withSession("sessionId" -> "FAKE_SESSION_ID")
              .withHeaders(HeaderNames.authorisation -> "Bearer 1")
          )

      status(result) mustBe OK
      contentAsString(result) mustNot include(config.bannerHomePageLinkUrl)
      contentAsString(result) mustNot include(config.bannerHomePageHeadingEn)
      contentAsString(result) mustNot include(config.bannerHomePageLinkTextEn)
    }

    "return a 200 status and one call to PreferencesFrontendConnector if AlertFlagToggle is enabled" in new LocalSetup {

      val homeController =
        new HomeController(
          mockPaperlessInterruptHelper,
          mockTaiConnector,
          mockBreathingSpaceService,
          mockFeatureFlagService,
          mockHomeCardGenerator,
          mockHomePageCachingHelper,
          mockAuthJourney,
          mcc,
          inject[HomeView],
          mockSeissService,
          mockRlsInterruptHelper,
          mockAlertBannerHelper
        )(config, ec)

      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerPaperlessStatusToggle))) thenReturn Future
        .successful(
          FeatureFlag(AlertBannerPaperlessStatusToggle, isEnabled = true)
        )
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, PaperlessMessagesStatus](
            Future.successful(Right(PaperlessStatusBounced()))
          )
        )

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
              request = request
            )
          )
      })

      val result: Future[Result] = homeController.index()(currentRequest)

      status(result) mustBe OK
      verify(mockPreferencesFrontendConnector, times(1)).getPaperlessStatus(any(), any())(any())
    }

    "return a 200 status and no calls to PreferencesFrontendConnector if AlertFlagToggle is disabled" in new LocalSetup {
      val homeController =
        new HomeController(
          mockPaperlessInterruptHelper,
          mockTaiConnector,
          mockBreathingSpaceService,
          mockFeatureFlagService,
          mockHomeCardGenerator,
          mockHomePageCachingHelper,
          mockAuthJourney,
          mcc,
          inject[HomeView],
          mockSeissService,
          mockRlsInterruptHelper,
          mockAlertBannerHelper
        )(config, ec)

      when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
        .thenReturn(Future.successful(AddressesLock(main = false, postal = false)))
      when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(Future.successful(true))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AlertBannerPaperlessStatusToggle))) thenReturn Future
        .successful(
          FeatureFlag(AlertBannerPaperlessStatusToggle, isEnabled = false)
        )
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, PaperlessMessagesStatus](
            Future.successful(Right(PaperlessStatusBounced()))
          )
        )

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
              request = request
            )
          )
      })

      val result: Future[Result] = homeController.index()(currentRequest)
      status(result) mustBe OK
      verify(mockPreferencesFrontendConnector, never).getPaperlessStatus(any(), any())(any())
    }
  }

  "Calling retrieveTaxComponentsState" must {

    val userNino = fakeNino

    "return TaxComponentsDisabled where taxComponents is not enabled" in new LocalSetup {
      when(mockTaiService.taxComponents(any(), any())(any(), any())).thenReturn(null)
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = false)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      val controller: HomeController = app.injector.instanceOf[HomeController]

      val result: TaxComponentsState = await(controller.retrieveTaxComponentsState(Some(userNino), year))

      result mustBe TaxComponentsDisabledState
      verify(mockTaiService, times(0)).taxComponents(any(), any())(any(), any())
    }

    "return TaxCalculationAvailable status when there are tax components" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      private val controller: HomeController = app.injector.instanceOf[HomeController]

      private val result = await(controller.retrieveTaxComponentsState(Some(userNino), year))
      result mustBe TaxComponentsAvailableState(
        TaxComponents(List("EmployerProvidedServices", "PersonalPensionPayments"))
      )
      verify(mockTaiService, times(1)).taxComponents(any(), any())(any(), any())
    }

    "return TaxComponentsNotAvailableState status when TaxComponentsUnavailableResponse from TaxComponents" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      private val controller: HomeController = app.injector.instanceOf[HomeController]

      when(mockTaiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier], any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", BAD_REQUEST)))
        )
      }

      private val result = await(controller.retrieveTaxComponentsState(Some(userNino), year))

      result mustBe TaxComponentsNotAvailableState
      verify(mockTaiService, times(1)).taxComponents(any(), any())(any(), any())
    }

    "return TaxComponentsUnreachableState status when there are TaxComponents returns an unexpected response" in new LocalSetup {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle))) thenReturn Future.successful(
        FeatureFlag(NationalInsuranceTileToggle, isEnabled = true)
      )
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle))) thenReturn Future.successful(
        FeatureFlag(TaxcalcToggle, isEnabled = true)
      )

      lazy val app: Application = localGuiceApplicationBuilder()
        .overrides(
          bind[TaiConnector].toInstance(mockTaiService),
          bind[HomeCardGenerator].toInstance(mockHomeCardGenerator)
        )
        .build()

      private val controller: HomeController = app.injector.instanceOf[HomeController]

      when(mockTaiService.taxComponents(any[Nino], any[Int])(any[HeaderCarrier], any())) thenReturn {
        EitherT[Future, UpstreamErrorResponse, HttpResponse](
          Future.successful(Left(UpstreamErrorResponse("", INTERNAL_SERVER_ERROR)))
        )
      }

      private val result = await(controller.retrieveTaxComponentsState(Some(userNino), year))

      result mustBe TaxComponentsUnreachableState
    }
  }
}
