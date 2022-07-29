/*
 * Copyright 2022 HM Revenue & Customs
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

import config.{ConfigDecorator, NewsAndTilesConfig}
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.ErrorRenderer
import models._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.i18n.{Langs, Messages}
import play.api.mvc.{AnyContentAsEmpty, MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import play.twirl.api.Html
import services._
import services.partials.{FormPartialService, SaPartialService}
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.auth.core.{ConfidenceLevel, Enrolment, EnrolmentIdentifier}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.partials.HtmlPartial
import testUtils.UserRequestFixture.buildUserRequest
import util._
import views.html.SelfAssessmentSummaryView
import views.html.interstitial.{ViewBreathingSpaceView, ViewChildBenefitsSummaryInterstitialView, ViewNationalInsuranceInterstitialHomeView, ViewNewsAndUpdatesView, ViewSaAndItsaMergePageView}
import views.html.selfassessment.Sa302InterruptView

import scala.concurrent.Future

class InterstitialControllerSpec extends BaseSpec {

  override lazy val app: Application = localGuiceApplicationBuilder().build()

  trait LocalSetup {

    lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
    val mockAuthJourney: AuthJourney = mock[AuthJourney]
    val mockNewsAndTileConfig: NewsAndTilesConfig = mock[NewsAndTilesConfig]
    val mockMessages = mock[Messages]

    def simulateFormPartialServiceFailure: Boolean

    def simulateSaPartialServiceFailure: Boolean

    def paperlessResponse: ActivatePaperlessResponse

    def controller: InterstitialController =
      new InterstitialController(
        mock[FormPartialService],
        mock[SaPartialService],
        mock[PreferencesFrontendService],
        mockAuthJourney,
        injected[WithBreadcrumbAction],
        injected[MessagesControllerComponents],
        injected[ErrorRenderer],
        injected[ViewNationalInsuranceInterstitialHomeView],
        injected[ViewChildBenefitsSummaryInterstitialView],
        injected[SelfAssessmentSummaryView],
        injected[Sa302InterruptView],
        injected[ViewNewsAndUpdatesView],
        injected[ViewSaAndItsaMergePageView],
        injected[ViewBreathingSpaceView],
        injected[EnrolmentsHelper],
        injected[SeissService],
        mockNewsAndTileConfig
      )(config, ec) {
        private def formPartialServiceResponse = Future.successful {
          if (simulateFormPartialServiceFailure) {
            HtmlPartial.Failure()
          } else {
            HtmlPartial.Success(Some("Success"), Html("any"))
          }
        }

        when(formPartialService.getSelfAssessmentPartial(any())) thenReturn formPartialServiceResponse
        when(formPartialService.getNationalInsurancePartial(any())) thenReturn formPartialServiceResponse

        when(saPartialService.getSaAccountSummary(any())) thenReturn {
          Future.successful {
            if (simulateSaPartialServiceFailure) {
              HtmlPartial.Failure()
            } else {
              HtmlPartial.Success(Some("Success"), Html("any"))
            }
          }
        }

        when(preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
          Future.successful(paperlessResponse)
        }
      }
  }

  "Calling displayNationalInsurance" must {

    "call FormPartialService.getNationalInsurancePartial and return 200 when called by authorised user " in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              request = request
            )
          )
      })

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController: InterstitialController = controller

      val result: Future[Result] = testController.displayNationalInsurance(fakeRequest)

      status(result) mustBe OK

      verify(testController.formPartialService, times(1)).getNationalInsurancePartial(any())
    }
  }

  "Calling displayChildBenefits" must {

    "call FormPartialService.getChildBenefitPartial and return 200 when called by authorised user" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

      val fakeRequestWithPath: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("GET", "/foo")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              request = request
            )
          )
      })

      val result: Future[Result] = controller.displayChildBenefits(fakeRequestWithPath)

      status(result) mustBe OK

    }
  }

  "Calling viewSelfAssessmentSummary" must {

    "call FormPartialService.getSelfAssessmentPartial and return 200 when called by a high GG user" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val testController: InterstitialController = controller
      val r: Future[Result] = testController.displaySelfAssessment(fakeRequest)

      status(r) mustBe OK

      verify(testController.formPartialService, times(1))
        .getSelfAssessmentPartial(any())
    }

    "call FormPartialService.getSelfAssessmentPartial and return return 401 for a high GG user not enrolled in SA" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val testController: InterstitialController = controller

      val r: Future[Result] = testController.displaySelfAssessment(fakeRequest)
      status(r) mustBe UNAUTHORIZED
    }

    "call FormPartialService.getSelfAssessmentPartial and return 401 for a user not logged in via GG" in new LocalSetup {

      lazy val simulateFormPartialServiceFailure = true
      lazy val simulateSaPartialServiceFailure = true
      lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      val testController: InterstitialController = controller

      val r: Future[Result] = testController.displaySelfAssessment(fakeRequest)
      status(r) mustBe UNAUTHORIZED
    }

    "Calling getSa302" must {

      "should return OK response when accessing with an SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

        val saUtr: SaUtr = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

        def userRequest[A](request: Request[A]): UserRequest[A] = buildUserRequest(
          saUser = ActivatedOnlineFilerSelfAssessmentUser(saUtr),
          request = request
        )

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              userRequest(request = request)
            )
        })

        val testController: InterstitialController = controller

        val r: Future[Result] = testController.displaySa302Interrupt(2018)(fakeRequest)

        status(r) mustBe OK
        contentAsString(r) must include(saUtr.utr)
      }

      "should return UNAUTHORIZED response when accessing with a non SA user with a valid tax year" in new LocalSetup {

        lazy val simulateFormPartialServiceFailure = false
        lazy val simulateSaPartialServiceFailure = false
        lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                saUser = NonFilerSelfAssessmentUser,
                request = request
              )
            )
        })

        val testController: InterstitialController = controller
        val r: Future[Result] = testController.displaySa302Interrupt(2018)(fakeRequest)

        status(r) mustBe UNAUTHORIZED
      }
    }
  }

  "Calling displayNewsAndUpdates" must {

    "call displayNewsAndUpdates and return 200 when called by authorised user using verify" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              request = request
            )
          )
      })

      when(mockNewsAndTileConfig.getNewsAndContentModelList()(any())).thenReturn(List[NewsAndContentModel]())

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController: InterstitialController = controller

      val result: Future[Result] = testController.displayNewsAndUpdates("nicSection")(fakeRequest)

      status(result) mustBe OK

      contentAsString(result) must include("News and Updates")
    }

    "call displayNewsAndUpdates and return 200 when called by authorised user using GG" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      when(mockNewsAndTileConfig.getNewsAndContentModelList()(any())).thenReturn(List[NewsAndContentModel]())

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse: ActivatePaperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController: InterstitialController = controller

      val result: Future[Result] = testController.displayNewsAndUpdates("nicSection")(fakeRequest)

      status(result) mustBe OK

      contentAsString(result) must include("News and Updates")
    }

    "return UNAUTHORIZED when toggled off" in {
      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val isNewsAndUpdatesTileEnabled: Boolean = false
      }

      lazy val fakeRequest = FakeRequest("", "")

      val mockAuthJourney = mock[AuthJourney]

      def controller: InterstitialController =
        new InterstitialController(
          mock[FormPartialService],
          mock[SaPartialService],
          mock[PreferencesFrontendService],
          mockAuthJourney,
          injected[WithBreadcrumbAction],
          injected[MessagesControllerComponents],
          injected[ErrorRenderer],
          injected[ViewNationalInsuranceInterstitialHomeView],
          injected[ViewChildBenefitsSummaryInterstitialView],
          injected[SelfAssessmentSummaryView],
          injected[Sa302InterruptView],
          injected[ViewNewsAndUpdatesView],
          injected[ViewSaAndItsaMergePageView],
          injected[ViewBreathingSpaceView],
          injected[EnrolmentsHelper],
          injected[SeissService],
          mock[NewsAndTilesConfig]
        )(stubConfigDecorator, ec) {
          private def formPartialServiceResponse = Future.successful {
            HtmlPartial.Success(Some("Success"), Html("any"))
          }

          when(formPartialService.getSelfAssessmentPartial(any())) thenReturn formPartialServiceResponse
          when(formPartialService.getNationalInsurancePartial(any())) thenReturn formPartialServiceResponse

          when(saPartialService.getSaAccountSummary(any())) thenReturn {
            Future.successful(HtmlPartial.Success(Some("Success"), Html("any")))
          }

          when(preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
            Future.successful(ActivatePaperlessNotAllowedResponse)
          }
        }

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val result = controller.displayNewsAndUpdates("nicSection")(fakeRequest)

      status(result) mustBe UNAUTHORIZED

    }
  }

  "Calling displayBreathingSpaceDetails" must {

    "call displayBreathingSpaceDetails and return 200 when called by authorised user using GG" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      lazy val simulateFormPartialServiceFailure = false
      lazy val simulateSaPartialServiceFailure = false
      lazy val paperlessResponse = ActivatePaperlessNotAllowedResponse

      val testController = controller

      val result = testController.displayBreathingSpaceDetails(fakeRequest)

      status(result) mustBe OK

      contentAsString(result) must include("You are in Breathing Space")
    }

    "return UNAUTHORIZED when toggled off" in {
      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val isBreathingSpaceIndicatorEnabled: Boolean = false
      }

      lazy val fakeRequest = FakeRequest("", "")

      val mockAuthJourney = mock[AuthJourney]
      val mockNewsAndTileConfig: NewsAndTilesConfig = mock[NewsAndTilesConfig]

      def controller: InterstitialController =
        new InterstitialController(
          mock[FormPartialService],
          mock[SaPartialService],
          mock[PreferencesFrontendService],
          mockAuthJourney,
          injected[WithBreadcrumbAction],
          injected[MessagesControllerComponents],
          injected[ErrorRenderer],
          injected[ViewNationalInsuranceInterstitialHomeView],
          injected[ViewChildBenefitsSummaryInterstitialView],
          injected[SelfAssessmentSummaryView],
          injected[Sa302InterruptView],
          injected[ViewNewsAndUpdatesView],
          injected[ViewSaAndItsaMergePageView],
          injected[ViewBreathingSpaceView],
          injected[EnrolmentsHelper],
          injected[SeissService],
          mockNewsAndTileConfig
        )(stubConfigDecorator, ec) {
          private def formPartialServiceResponse = Future.successful {
            HtmlPartial.Success(Some("Success"), Html("any"))
          }
          when(formPartialService.getSelfAssessmentPartial(any())) thenReturn formPartialServiceResponse
          when(formPartialService.getNationalInsurancePartial(any())) thenReturn formPartialServiceResponse

          when(saPartialService.getSaAccountSummary(any())) thenReturn {
            Future.successful(HtmlPartial.Success(Some("Success"), Html("any")))
          }

          when(preferencesFrontendService.getPaperlessPreference()(any())) thenReturn {
            Future.successful(ActivatePaperlessNotAllowedResponse)
          }
        }

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = request
            )
          )
      })

      val result = controller.displayBreathingSpaceDetails(fakeRequest)

      status(result) mustBe UNAUTHORIZED

    }
  }

  "Calling displayItsa" must {

    "saItsaTileEnabled is false return Unauthorized" in {
      lazy val fakeRequest = FakeRequest("", "")

      val mockAuthJourney = mock[AuthJourney]

      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val saItsaTileEnabled: Boolean = false
      }

      def controller: InterstitialController =
        new InterstitialController(
          mock[FormPartialService],
          mock[SaPartialService],
          mock[PreferencesFrontendService],
          mockAuthJourney,
          injected[WithBreadcrumbAction],
          injected[MessagesControllerComponents],
          injected[ErrorRenderer],
          injected[ViewNationalInsuranceInterstitialHomeView],
          injected[ViewChildBenefitsSummaryInterstitialView],
          injected[SelfAssessmentSummaryView],
          injected[Sa302InterruptView],
          injected[ViewNewsAndUpdatesView],
          injected[ViewSaAndItsaMergePageView],
          injected[ViewBreathingSpaceView],
          injected[EnrolmentsHelper],
          injected[SeissService],
          mock[NewsAndTilesConfig]
        )(stubConfigDecorator, ec)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              credentials = Credentials("", "Verify"),
              request = request
            )
          )
      })

      val result = controller.displaySaAndItsaMergePage()(fakeRequest)

      status(result) mustBe UNAUTHORIZED
    }

    "saItsaTileEnabled is true return OK" in {
      lazy val fakeRequest = FakeRequest("", "")

      val mockAuthJourney = mock[AuthJourney]

      val stubConfigDecorator = new ConfigDecorator(
        injected[Configuration],
        injected[Langs],
        injected[ServicesConfig]
      ) {
        override lazy val saItsaTileEnabled: Boolean = true
      }

      def controller: InterstitialController =
        new InterstitialController(
          mock[FormPartialService],
          mock[SaPartialService],
          mock[PreferencesFrontendService],
          mockAuthJourney,
          injected[WithBreadcrumbAction],
          injected[MessagesControllerComponents],
          injected[ErrorRenderer],
          injected[ViewNationalInsuranceInterstitialHomeView],
          injected[ViewChildBenefitsSummaryInterstitialView],
          injected[SelfAssessmentSummaryView],
          injected[Sa302InterruptView],
          injected[ViewNewsAndUpdatesView],
          injected[ViewSaAndItsaMergePageView],
          injected[ViewBreathingSpaceView],
          injected[EnrolmentsHelper],
          injected[SeissService],
          mock[NewsAndTilesConfig]
        )(stubConfigDecorator, ec)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
              request = request
            )
          )
      })

      val result = controller.displaySaAndItsaMergePage()(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) must include("Your Self Assessment")
    }
  }
}
