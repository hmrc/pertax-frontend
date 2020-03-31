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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import models.dto.TaxCreditsChoiceDto
import org.mockito.Matchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{redirectLocation, _}
import services.LocalSessionCache
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, LocalPartialRetriever}

import scala.concurrent.{ExecutionContext, Future}

class ResidencyChoiceControllerSpec extends BaseSpec with MockitoSugar with GuiceOneAppPerSuite {

  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAuthJourney: AuthJourney = mock[AuthJourney]

  override def afterEach: Unit =
    reset(mockLocalSessionCache, mockAuthJourney)

  trait LocalSetup {

    val sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(false)))))

    val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request)
        )
    }

    def controller =
      new ResidencyChoiceController(
        mockLocalSessionCache,
        mockAuthJourney,
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents]
      )(
        injected[LocalPartialRetriever],
        injected[ConfigDecorator],
        injected[TemplateRenderer],
        injected[ExecutionContext]) {

        when(mockAuthJourney.authWithPersonalDetails) thenReturn
          authActionResult

        when(mockLocalSessionCache.fetch()(any(), any())) thenReturn
          sessionCacheResponse

        when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn
          Future.successful(CacheMap("", Map.empty))

      }
  }

  "onPageLoad" should {

    "return OK when the user has indicated that they do not receive tax credits on the previous page" in new LocalSetup {

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "return to the beginning of journey when the user has indicated that they receive tax credits on the previous page" in new LocalSetup {
      override val sessionCacheResponse: Option[CacheMap] =
        Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(true)))))

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "return to the beginning of journey when the user has not selected any tax credits choice on the previous page" in new LocalSetup {
      override val sessionCacheResponse = None

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }
  }

  "onSubmit" should {

    "redirect to find address page with primary type when primary is submitted" in new LocalSetup {

      val requestWithform = FakeRequest("POST", "")
        .withFormUrlEncodedBody("residencyChoice" -> "primary")

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
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/do-you-live-in-the-uk")
    }

    "redirect to find address page with sole type when sole is submitted" in new LocalSetup {

      val requestWithform = FakeRequest("POST", "")
        .withFormUrlEncodedBody("residencyChoice" -> "sole")

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
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/do-you-live-in-the-uk")
    }

    "return a bad request when supplied a bad value" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("residencyChoice" -> "bad")

      override val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = requestWithForm.asInstanceOf[Request[A]]
            )
          )
      }

      val result = controller.onSubmit(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when supplied no value" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "")

      override val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = requestWithForm.asInstanceOf[Request[A]]
            )
          )
      }

      val result = controller.onSubmit(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
