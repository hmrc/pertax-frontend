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
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.TaxCreditsChoiceDto
import org.mockito.Matchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.LocalSessionCache
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.ResidencyChoiceView

import scala.concurrent.{ExecutionContext, Future}

class ResidencyChoiceControllerSpec extends BaseSpec with MockitoSugar {

  trait LocalSetup {

    lazy val mockAuthJourney: AuthJourney = mock[AuthJourney]
    lazy val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]

    implicit lazy val ec = injected[ExecutionContext]

    def controller: ResidencyChoiceController =
      new ResidencyChoiceController(
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        mockAuthJourney,
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents],
        injected[ResidencyChoiceView],
        injected[DisplayAddressInterstitialView]
      )(injected[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], ec)

    def sessionCacheResponse: Future[Option[CacheMap]] =
      Future.successful(Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(false))))))

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    when(mockLocalSessionCache.cache(any(), any())(any(), any(), any()))
      .thenReturn(Future.successful(CacheMap("id", Map.empty)))

    when(mockLocalSessionCache.fetch()(any(), any())).thenReturn(Future.successful(sessionCacheResponse))

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = currentRequest[A]).asInstanceOf[UserRequest[A]]
        )
    })
  }

  "onPageLoad" should {

//    trait LocalSetup extends WithAddressControllerSpecSetup {
//      override lazy val fakeAddress = buildFakeAddress
//      override lazy val nino = Fixtures.fakeNino
//      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
//      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
//      override lazy val thisYearStr = "2015"
//
//    }

    "return OK when the user has indicated that they do not receive tax credits on the previous page" in new LocalSetup {

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return to the beginning of journey when the user has indicated that they receive tax credits on the previous page" in new LocalSetup {
      override def sessionCacheResponse: Future[Some[CacheMap]] =
        Future.successful(Some(CacheMap("id", Map("taxCreditsChoiceDto" -> Json.toJson(TaxCreditsChoiceDto(true))))))

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return to the beginning of journey when the user has not selected any tax credits choice on the previous page" in new LocalSetup {
      override def sessionCacheResponse: Future[Option[CacheMap]] = Future.successful(None)

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }

  "onSubmit" should {

//    trait LocalSetup extends WithAddressControllerSpecSetup {
//      override lazy val fakeAddress = buildFakeAddress
//      override lazy val nino = Fixtures.fakeNino
//      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
//      override lazy val sessionCacheResponse =
//        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
//      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
//      override lazy val thisYearStr = "2015"
//    }

    "redirect to find address page with primary type when supplied value=primary" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("residencyChoice" -> "primary")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/do-you-live-in-the-uk")
    }

    "redirect to find address page with sole type when supplied value=sole" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("residencyChoice" -> "sole")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/do-you-live-in-the-uk")
    }

    "return a bad request when supplied value=bad" in new LocalSetup {

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("residencyChoice" -> "bad")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }

    "return a bad request when supplied no value" in new LocalSetup {

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "").asInstanceOf[Request[A]]

      val result = controller.onSubmit(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }
  }
}
