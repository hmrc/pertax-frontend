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
import controllers.bindable.SoleAddrType
import models.dto.AddressPageVisitedDto
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

class InternationalAddressChoiceControllerSpec extends BaseSpec with MockitoSugar with GuiceOneAppPerSuite {

  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAuthJourney: AuthJourney = mock[AuthJourney]

  override def afterEach: Unit =
    reset(mockLocalSessionCache, mockAuthJourney)

  trait LocalSetup {

    val requestWithForm: Request[_] = FakeRequest()

    lazy val fakeConfigDecorator: ConfigDecorator = injected[ConfigDecorator]

    val sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = requestWithForm.asInstanceOf[Request[A]])
        )
    }

    def controller =
      new InternationalAddressChoiceController(
        mockLocalSessionCache,
        mockAuthJourney,
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents]
      )(injected[LocalPartialRetriever], fakeConfigDecorator, injected[TemplateRenderer], injected[ExecutionContext]) {

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

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in new LocalSetup {
      override val sessionCacheResponse: Option[CacheMap] = None

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }
  }

  "onSubmit" should {

    "redirect to postcode lookup page when supplied with value = Yes (true)" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("internationalAddressChoice" -> "true")

      val result =
        controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/find-address")
    }

    "redirect to 'cannot use this service' page when value = No (false) and feature toggle is off" in new LocalSetup {
      val mockConfigDecorator: ConfigDecorator = mock[ConfigDecorator]
      override lazy val fakeConfigDecorator: ConfigDecorator = mockConfigDecorator

      when(mockConfigDecorator.updateInternationalAddressInPta).thenReturn(false)

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("internationalAddressChoice" -> "false")

      val result =
        controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/cannot-use-the-service")
    }

    "return a bad request when supplied no value" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")

      val result = controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
