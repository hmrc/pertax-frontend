/*
 * Copyright 2020 HM Revenue & Customs
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

import controllers.auth.requests.UserRequest
import models.dto.AddressPageVisitedDto
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import uk.gov.hmrc.http.cache.client.CacheMap
import util.ActionBuilderFixture
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.Future

class TaxCreditsChoiceControllerSpec extends AddressSpecHelper {

  trait LocalSetup {

    val sessionCacheResponse: Option[CacheMap] = Some(
      CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    val requestWithForm: Request[_] = FakeRequest()

    val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = requestWithForm.asInstanceOf[Request[A]])
        )
    }

    def controller =
      new TaxCreditsChoiceController(
        mockLocalSessionCache,
        mockAuthJourney,
        withActiveTabAction,
        mcc
      ) {

        when(mockAuthJourney.authWithPersonalDetails) thenReturn
          authActionResult

        when(mockLocalSessionCache.fetch()(any(), any())) thenReturn
          sessionCacheResponse

        when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn
          Future.successful(CacheMap("", Map.empty))

      }
  }

  "onPageLoad" should {

    "return OK if there is an entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {
      override val sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }
  }

  "onSubmit" should {

    "redirect to expected tax credits page when supplied with value = Yes (true)" in new LocalSetup {

      val requestWithform = FakeRequest("POST", "")
        .withFormUrlEncodedBody("taxCreditsChoice" -> "true")

      override val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = requestWithform.asInstanceOf[Request[A]]
            )
          )
      }

      val result =
        controller.onSubmit()(requestWithform)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/tax-credits-service/personal/change-address")
    }

    "redirect to ResidencyChoice page when supplied with value = No (false)" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("taxCreditsChoice" -> "false")

      val result = controller.onSubmit(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/residency-choice")
    }

    "return a bad request when supplied no value" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")

      val result = controller.onSubmit(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
