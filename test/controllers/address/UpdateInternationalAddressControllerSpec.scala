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
import controllers.controllershelpers.CountryHelper
import models.addresslookup.{AddressLookupResponse, AddressLookupSuccessResponse}
import models.dto.{AddressDto, AddressPageVisitedDto, DateDto, ResidencyChoiceDto}
import org.joda.time.LocalDate
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.{AddressLookupService, LocalSessionCache, PersonDetailsSuccessResponse, UpdateAddressResponse, UpdateAddressSuccessResponse}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures.{asInternationalAddressDto, buildFakeAddress, fakeStreetPafAddressRecord, fakeStreetTupleListAddressForUnmodified, fakeStreetTupleListInternationalAddress, oneAndTwoOtherPlacePafRecordSet}
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.{ExecutionContext, Future}

class UpdateInternationalAddressControllerSpec extends BaseSpec with MockitoSugar with GuiceOneAppPerSuite {

  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAuthJourney: AuthJourney = mock[AuthJourney]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  override def afterEach: Unit =
    reset(mockLocalSessionCache, mockAuthJourney, mockAuditConnector)

  trait LocalSetup {

    val requestWithForm: Request[_] = FakeRequest()

    val sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

    val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = requestWithForm.asInstanceOf[Request[A]])
        )
    }

    def controller =
      new UpdateInternationalAddressController(
        injected[CountryHelper],
        mockAuditConnector,
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

    "find only the selected address from the session cache and no residency choice and return 303" in new LocalSetup {

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "fetch the selected address and a sole residencyChoice has been selected from the session cache and return 200" in new LocalSetup {

      override val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "selectedAddressRecord"  -> Json.toJson(fakeStreetPafAddressRecord),
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(PostalAddrType)))))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address with sole address type but residencyChoice in the session cache and still return 200" in new LocalSetup {

      override val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "find no residency choice in the session cache and redirect to the beginning of the journey" in new LocalSetup {

      override val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "redirect user to beginning of journey and return 303 for postal addressType and no pagevisitedDto in cache" in new LocalSetup {

      override val sessionCacheResponse = None

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and addressRecord in cache" in new LocalSetup {

      override val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)),
            "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and no addressRecord in cache" in new LocalSetup {

      override val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "find no addresses in the session cache and return 303" in new LocalSetup {

      override val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "find sole selected and submitted addresses in the session cache and return 200" in new LocalSetup {

      override val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "soleSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleResidencyChoiceDto"    -> Json.toJson(ResidencyChoiceDto(SoleAddrType))
          )
        ))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address but a submitted address in the session cache and return 200" in new LocalSetup {

      override val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleResidencyChoiceDto"  -> Json.toJson(ResidencyChoiceDto(SoleAddrType))
          )
        ))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "show 'Enter the address' when user amends correspondence address manually and address has not been selected" in new LocalSetup {

      override val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Your postal address") shouldBe true
    }

    "show 'Enter your address' when user amends residential address manually and address has not been selected" in new LocalSetup {

      override val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Your address") shouldBe true
    }

    "verify an audit event has been sent when user chooses to add/amend main address" in new LocalSetup {

      override val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }

    "verify an audit event has been sent when user chooses to add postal address" in new LocalSetup {

      override val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }
  }

  "onSubmit" should {

    "return 400 when supplied invalid form input" in new LocalSetup {
      override val sessionCacheResponse = Some(CacheMap("id", Map("selectedAddressRecord" -> Json.toJson(""))))

      val result = controller.onSubmit(PostalAddrType)(FakeRequest("POST", ""))

      status(result) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to review changes page when supplied valid form input on a postal journey and input default startDate into cache" in new LocalSetup {
      override val requestWithForm =
        FakeRequest("POST", "").withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)

      val result = controller.onSubmit(PostalAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/postal/changes")
      verify(controller.sessionCache, times(1)).cache(
        meq("postalSubmittedAddressDto"),
        meq(asInternationalAddressDto(fakeStreetTupleListInternationalAddress)))(any(), any(), any())
      verify(controller.sessionCache, times(1))
        .cache(meq("postalSubmittedStartDateDto"), meq(DateDto(LocalDate.now())))(any(), any(), any())
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a non postal journey" in new LocalSetup {
      override val requestWithForm =
        FakeRequest("POST", "").withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)

      val result = controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/enter-start-date")
      verify(controller.sessionCache, times(1)).cache(
        meq("soleSubmittedAddressDto"),
        meq(asInternationalAddressDto(fakeStreetTupleListInternationalAddress)))(any(), any(), any())
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }
  }

  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

}
