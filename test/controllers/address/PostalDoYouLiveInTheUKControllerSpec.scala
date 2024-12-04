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

import models.UserAnswers
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentMatchers.any
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import routePages.HasAddressAlreadyVisitedPage
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class PostalDoYouLiveInTheUKControllerSpec extends AddressBaseSpec {
  private lazy val controller: PostalDoYouLiveInTheUKController =
    app.injector.instanceOf[PostalDoYouLiveInTheUKController]

  "onPageLoad" must {
    "return OK if there is an entry in the cache to say the user previously visited the 'personal details' page" in {
      def userAnswersToReturn: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswersToReturn))
      val result: Future[Result]           = controller.onPageLoad(currentRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in {
      val result: Future[Result] = controller.onPageLoad(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }
  }

  "onSubmit" must {

    "redirect to postcode lookup page when supplied with value = Yes (true)" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "true")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/find-address")
    }

    "redirect to enter international address page when supplied with value = No (false)" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "false")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/enter-international-address")
    }

    "redirect to 'cannot use this service' when service configured to prevent updating International Addresses" in {
      lazy val controller: PostalDoYouLiveInTheUKController = appn(extraConfigValues =
        Map("feature.update-international-address-form.enabled" -> false)
      ).injector.instanceOf[PostalDoYouLiveInTheUKController]

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "false")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/cannot-use-the-service")
    }

    "return a bad request when supplied no value" in {

      def currentRequest[A]: Request[A] = FakeRequest("POST", "").asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe BAD_REQUEST
    }
  }
}
