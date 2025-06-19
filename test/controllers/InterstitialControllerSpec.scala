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
import config.NewsAndTilesConfig
import connectors.TaiConnector
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models._
import models.admin.{BreathingSpaceIndicatorToggle, ShowPlannedOutageBannerToggle}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.mockito.stubbing.OngoingStubbing
import play.api.Application
import play.api.inject.{Binding, bind}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.twirl.api.Html
import services.CitizenDetailsService
import services.partials.{FormPartialService, SaPartialService}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.play.partials.HtmlPartial
import util.AlertBannerHelper

import java.time.LocalDate
import scala.concurrent.Future

class InterstitialControllerSpec extends BaseSpec {
  private lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
  private val mockFormPartialService                                = mock[FormPartialService]
  private val mockSaPartialService                                  = mock[SaPartialService]
  private val mockNewsAndTilesConfig                                = mock[NewsAndTilesConfig]
  val mockCitizenDetailsService: CitizenDetailsService              = mock[CitizenDetailsService]
  val mockAlertBannerHelper: AlertBannerHelper                      = mock[util.AlertBannerHelper]
  private val mockTaiConnector: TaiConnector                        = mock[TaiConnector]

  private def setupAuth(
    saUserType: Option[SelfAssessmentUserType] = None,
    enrolments: Set[Enrolment] = Set.empty,
    trustedHelper: Option[TrustedHelper] = None
  ): OngoingStubbing[ActionBuilder[UserRequest, AnyContent]] = {
    val actionBuilderFixture: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        saUserType match {
          case Some(sut) =>
            block(
              buildUserRequest(
                saUser = sut,
                request = request,
                enrolments = enrolments,
                trustedHelper = trustedHelper
              )
            )
          case None      =>
            block(
              buildUserRequest(
                request = request,
                enrolments = enrolments,
                trustedHelper = trustedHelper
              )
            )
        }

    }
    when(mockAuthJourney.authWithPersonalDetails).thenReturn(actionBuilderFixture)
  }

  setupAuth(Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))))

  private def appn(bindings: Seq[Binding[_]] = Nil, extraConfigValues: Map[String, Any] = Map.empty): Application = {
    val fullBindings = Seq(
      bind[FormPartialService].toInstance(mockFormPartialService),
      bind[SaPartialService].toInstance(mockSaPartialService),
      bind[AuthJourney].toInstance(mockAuthJourney),
      bind[NewsAndTilesConfig].toInstance(mockNewsAndTilesConfig),
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
      bind[util.AlertBannerHelper].toInstance(mockAlertBannerHelper),
      bind[TaiConnector].toInstance(mockTaiConnector)
    ) ++ bindings

    localGuiceApplicationBuilder(extraConfigValues)
      .overrides(fullBindings)
      .build()
  }

  override implicit lazy val app: Application = appn()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSaPartialService)
    reset(mockFormPartialService)
    reset(mockNewsAndTilesConfig)
    reset(mockCitizenDetailsService)
    reset(mockAlertBannerHelper)
    reset(mockTaiConnector)
  }

  private def taxComponentsHICBCSuccessResponse(
    value: Boolean
  ): EitherT[Future, UpstreamErrorResponse, Option[Boolean]] =
    EitherT.right[UpstreamErrorResponse](Future.successful(Some(value)))

  "displayChildBenefits" must {
    "redirect to /your-national-insurance-state-pension when when call displayNationalInsurance" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth(Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))))
      val result                                  = controller.displayChildBenefits()(fakeRequest)

      status(result) mustBe MOVED_PERMANENTLY
      redirectLocation(
        result
      ) mustBe Some(controllers.routes.InterstitialController.displayChildBenefitsSingleAccountView.url)
    }
  }

  "Calling displayHICBCChargeInPAYEView" must {
    "return OK & correct view" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth(Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))))
      val result                                  = controller.displayHICBCChargeInPAYEView()(fakeRequest)
      status(result) mustBe OK

      val contentString = contentAsString(result)
      contentString must include("View the High Income Child Benefit charge in your tax-free amount")
    }
  }

  "Calling displayChildBenefitsSingleAccountView" must {
    "return OK & correct view for new Child Benefits where no HICBC components returned from API" in {
      when(mockTaiConnector.taxComponents[Boolean](any(), any())(any())(any(), any()))
        .thenReturn(taxComponentsHICBCSuccessResponse(false))
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth(Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))))
      val result                                  = controller.displayChildBenefitsSingleAccountView()(fakeRequest)
      status(result) mustBe OK

      val contentString = contentAsString(result)
      contentString must include("Visit High Income Child Benefit Charge")
      contentString must include("find out if you need to pay the charge")
    }
    "return OK & correct view for new Child Benefits where HICBC components returned from API" in {
      when(mockTaiConnector.taxComponents[Boolean](any(), any())(any())(any(), any()))
        .thenReturn(taxComponentsHICBCSuccessResponse(true))
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth(Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))))
      val result                                  = controller.displayChildBenefitsSingleAccountView()(fakeRequest)
      status(result) mustBe OK

      val contentString = contentAsString(result)
      contentString mustNot include("Visit High Income Child Benefit Charge")
      contentString mustNot include("find out if you need to pay the charge")
      contentString must include("View the High Income Child Benefit charge in your tax-free amount")
    }
  }

  "Calling viewSelfAssessmentSummary" must {
    "call FormPartialService.getSelfAssessmentPartial and return 200 when called by a high GG user" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth()

      val formPartialServiceResponse = Future.successful {
        HtmlPartial.Success(Some("Success"), Html("any"))
      }

      when(mockFormPartialService.getSelfAssessmentPartial(any())) thenReturn formPartialServiceResponse
      when(mockFormPartialService.getNationalInsurancePartial(any())) thenReturn formPartialServiceResponse

      when(mockSaPartialService.getSaAccountSummary(any())) thenReturn
        Future.successful {
          HtmlPartial.Success(Some("Success"), Html("any"))
        }

      val result = controller.displaySelfAssessment()(fakeRequest)

      status(result) mustBe OK
      verify(mockFormPartialService, times(1)).getSelfAssessmentPartial(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return return 401 for a high GG user not enrolled in SA" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth(Some(NonFilerSelfAssessmentUser))
      val result                                  = controller.displaySelfAssessment()(fakeRequest)
      status(result) mustBe UNAUTHORIZED
    }
  }

  "Calling getSa302" must {
    "return OK response when accessing with an SA user with a valid tax year" in {
      val saUtr: SaUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(Some(ActivatedOnlineFilerSelfAssessmentUser(saUtr)))

      val result = controller.displaySa302Interrupt(2018)(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include(saUtr.utr)
    }

    "return UNAUTHORIZED response when accessing with a non SA user with a valid tax year" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(Some(NonFilerSelfAssessmentUser))

      val result = controller.displaySa302Interrupt(2018)(fakeRequest)

      status(result) mustBe UNAUTHORIZED
    }
  }

  "Calling displayNewsAndUpdates" must {
    "call displayNewsAndUpdates and return 200 when called by authorised user using GG" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth()
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any())).thenReturn(
        List[NewsAndContentModel](
          NewsAndContentModel(
            "nicSection",
            "1.25 percentage points uplift in National Insurance contributions (base64 encoded)",
            "<p id=\"paragraph\">base64 encoded content with html</p>",
            isDynamic = false,
            LocalDate.now
          )
        )
      )
      val result = controller.displayNewsAndUpdates("nicSection")(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include("News and Updates")
    }

    "redirect to home when toggled on but no news items" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth()
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any())).thenReturn(List[NewsAndContentModel]())
      val result = controller.displayNewsAndUpdates("nicSection")(fakeRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(controllers.routes.HomeController.index.url)
    }

    "return UNAUTHORIZED when toggled off" in {
      val app                                     = appn(extraConfigValues = Map("feature.news.enabled" -> false))
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth()
      when(mockNewsAndTilesConfig.getNewsAndContentModelList()(any())).thenReturn(List[NewsAndContentModel]())
      val result                                  = controller.displayNewsAndUpdates("nicSection")(fakeRequest)
      status(result) mustBe UNAUTHORIZED
    }
  }

  "Calling displayBreathingSpaceDetails" must {
    "call displayBreathingSpaceDetails and return 200 when called by authorised user using GG" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(Some(NonFilerSelfAssessmentUser))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
        .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))
      val result = controller.displayBreathingSpaceDetails()(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include("You are in Breathing Space")
    }

    "return UNAUTHORIZED when toggled off" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(Some(NonFilerSelfAssessmentUser))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
        .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = false)))
      val result = controller.displayBreathingSpaceDetails()(fakeRequest)

      status(result) mustBe UNAUTHORIZED
    }
  }

  "Calling displayItsa" must {
    "return OK" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))),
        enrolments = Set(Enrolment("HMRC-MTD-IT", List(EnrolmentIdentifier("MTDITID", "XAIT00000888888")), "Activated"))
      )

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
        .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))

      val result = controller.displayItsaMergePage()(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include("Your Self Assessment")
    }
  }

  "Calling displaySaRegistrationPage" must {
    "return UNAUTHORIZED when trustedHelper is defined" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr))),
        trustedHelper =
          Some(TrustedHelper("principalName", "attorneyName", "returnLinkUrl", Some(generatedTrustedHelperNino.nino)))
      )

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
        .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))

      val result = controller.displaySaRegistrationPage()(fakeRequest)

      status(result) mustBe UNAUTHORIZED
    }

    "return UNAUTHORIZED when the user is an SA user" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)))
      )

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
        .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))

      val result = controller.displaySaRegistrationPage()(fakeRequest)

      status(result) mustBe UNAUTHORIZED
    }

    "return OK with selfAssessmentRegistrationPageView when no trustedHelper, no ITSA enrolment, and not an SA user and pegaEnabled is true" in {
      val dummyUrl                                = "/dummy"
      val app                                     = appn(extraConfigValues =
        Map(
          "feature.pegaSaRegistration.enabled"  -> true,
          "external-url.pegaSaRegistration.url" -> dummyUrl
        )
      )
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(NonFilerSelfAssessmentUser)
      )

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
        .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))

      val result = controller.displaySaRegistrationPage()(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include("Self Assessment: who needs to register")
      contentAsString(result) must include(dummyUrl)
    }

    "return UNAUTHORIZED when pegaEnabled is false" in {
      val app                                     = appn(extraConfigValues = Map("feature.pegaSaRegistration.enabled" -> false))
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(NonFilerSelfAssessmentUser)
      )

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
        .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))

      val result = controller.displaySaRegistrationPage()(fakeRequest)

      status(result) mustBe UNAUTHORIZED
    }
  }

  "Calling displayNpsShutteringPage" must {
    "return OK when NpsShutteringToggle is true" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)))
      )

      when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = true)))

      val result = controller.displayShutteringPage()(fakeRequest)
      status(result) mustBe OK
      contentAsString(result) must include(
        "The following services will be unavailable from 10pm on Friday 12 July to 7am on Monday 15 July."
      )
    }

    "return redirect back to the home page when NpsShutteringToggle is false" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)))
      )

      when(mockFeatureFlagService.get(ShowPlannedOutageBannerToggle))
        .thenReturn(Future.successful(FeatureFlag(ShowPlannedOutageBannerToggle, isEnabled = false)))

      val result = controller.displayShutteringPage()(fakeRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(routes.HomeController.index.url)
    }
  }

  "Calling displayTaxCreditsTransitionInformationInterstitialView" must {

    "return REDIRECT to decommissioned page" in {

      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]

      setupAuth(
        saUserType = Some(ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)))
      )

      val result: Future[Result] = controller.displayTaxCreditsTransitionInformationInterstitialView(fakeRequest)
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        controllers.routes.InterstitialController.displayTaxCreditsEndedInformationInterstitialView.url
      )
    }

  }

  "Calling displayNISP" must {
    "return OK and include banner content when voluntary contributions alert toggle is on" in {
      lazy val controller: InterstitialController = app.injector.instanceOf[InterstitialController]
      setupAuth()

      when(mockFormPartialService.getNISPPartial(any()))
        .thenReturn(Future.successful(HtmlPartial.Success(Some("title"), Html("nisp partial"))))

      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any()))
        .thenReturn(EitherT.rightT(Fixtures.buildPersonDetails))

      val bannerHtml = Html("<div class='voluntary-banner'>Banner Content</div>")
      when(mockAlertBannerHelper.getVoluntaryContributionsAlertBannerContent(any(), any()))
        .thenReturn(Future.successful(Some(bannerHtml)))

      val result = controller.displayNISP()(fakeRequest)

      status(result) mustBe OK
      val content = contentAsString(result)
      content must include("nisp partial")
      content must include("voluntary-banner")
    }
  }
}
