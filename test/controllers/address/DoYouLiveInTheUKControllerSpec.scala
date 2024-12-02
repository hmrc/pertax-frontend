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

package controllers.address

import config.ConfigDecorator
import controllers.InterstitialController
import controllers.auth.AuthJourney
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.UserAnswers
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentMatchers.any
import org.mockito.invocation.InvocationOnMock
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import routePages.HasAddressAlreadyVisitedPage
import views.html.personaldetails.InternationalAddressChoiceView

import scala.concurrent.Future

class DoYouLiveInTheUKControllerSpec extends AddressBaseSpec {
  val mockAddressJourneyCachingHelper: AddressJourneyCachingHelper = mock[AddressJourneyCachingHelper]
  val mockInterstitialController: InterstitialController           = mock[InterstitialController]
  override implicit lazy val app: Application                      = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[AuthJourney].toInstance(mockAuthJourney),
      bind[AddressJourneyCachingHelper].toInstance(mockAddressJourneyCachingHelper)
    )
    .build()
  trait LocalSetup extends AddressControllerSetup {
    def controller: DoYouLiveInTheUKController = app.injector.instanceOf[DoYouLiveInTheUKController]

    def userAnswersToReturn: UserAnswers = UserAnswers
      .empty("id")
      .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

    when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
      .thenAnswer((invocation: InvocationOnMock) => Future.successful(invocation.getArgument(0)))

    when(mockAddressJourneyCachingHelper.addToCache(any(), any())(any(), any()))
      .thenAnswer((_: InvocationOnMock) => Future.successful(userAnswersToReturn))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]
  }

  "onPageLoad" must {
    "return OK if there is an entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {

      val result: Future[Result] = controller.onPageLoad(currentRequest)

      status(result) mustBe OK
      verify(mockAddressJourneyCachingHelper, times(1)).enforceDisplayAddressPageVisited(any())(any())
    }
  }

  "onSubmit" must {

    "redirect to postcode lookup page when supplied with value = Yes (true)" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "true")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/find-address")
    }

    "redirect to 'cannot use this service' when service configured to prevent updating International Addresses" in new LocalSetup {

      lazy val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]

      when(mockConfigDecorator.updateInternationalAddressInPta).thenReturn(false)

      override def controller: DoYouLiveInTheUKController =
        new DoYouLiveInTheUKController(
          addressJourneyCachingHelper,
          mockAuthJourney,
          cc,
          inject[InternationalAddressChoiceView],
          displayAddressInterstitialView,
          mockFeatureFlagService,
          internalServerErrorView
        )(mockConfigDecorator, ec)

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "false")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/cannot-use-the-service")
    }

    "return a bad request when supplied no value" in new LocalSetup {

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "").asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe BAD_REQUEST
    }
  }
}
