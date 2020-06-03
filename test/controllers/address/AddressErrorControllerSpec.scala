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
import controllers.bindable.{PostalAddrType, SoleAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto._
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services._
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails._

import scala.concurrent.{ExecutionContext, Future}

class AddressErrorControllerSpec extends BaseSpec with MockitoSugar {

  val mockAuthJourney = mock[AuthJourney]
  val mockLocalSessionCache = mock[LocalSessionCache]

  lazy val displayAddressInterstitial = injected[DisplayAddressInterstitialView]
  lazy val cannotUseService = injected[CannotUseServiceView]
  lazy val addressAlreadyUpdated = injected[AddressAlreadyUpdatedView]

  implicit lazy val ec: ExecutionContext = injected[ExecutionContext]

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  trait LocalSetup {

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

    def controller =
      new AddressErrorController(
        mockAuthJourney,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents],
        displayAddressInterstitial,
        cannotUseService,
        addressAlreadyUpdated
      )(mock[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], ec) {

        when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn {
          Future.successful(CacheMap("id", Map.empty))
        }
        when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
          Future.successful(sessionCacheResponse)
        }
        when(mockLocalSessionCache.remove()(any(), any())) thenReturn {
          Future.successful(mock[HttpResponse])
        }
      }

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = currentRequest[A]).asInstanceOf[UserRequest[A]]
        )
    })
  }

  "cannotUseThisService" should {

    "display the cannot use this service page" in new LocalSetup {
      val result = controller.cannotUseThisService(SoleAddrType)(currentRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("You cannot use this service to update your address")
    }
  }

  "showAddressAlreadyUpdated" should {

    "display the showAddressAlreadyUpdated page" in new LocalSetup {

      val result = controller.showAddressAlreadyUpdated(PostalAddrType)(currentRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Your address has already been updated")
    }
  }
}
