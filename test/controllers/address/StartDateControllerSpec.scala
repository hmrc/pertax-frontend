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
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.auth.requests.UserRequest
import controllers.bindable.{PostalAddrType, PrimaryAddrType, SoleAddrType}
import models.PersonDetails
import models.dto.{AddressPageVisitedDto, DateDto}
import org.joda.time.LocalDate
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.test.Helpers._
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.redirectLocation
import services.{LocalSessionCache, PersonDetailsSuccessResponse}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}
import util.Fixtures.{asAddressDto, fakeStreetTupleListAddressForUnmodified}
import util.UserRequestFixture.buildUserRequest
import util.fixtures.PersonFixture.emptyPerson
import util.fixtures.AddressFixture.{address => addressLine}

import scala.concurrent.{ExecutionContext, Future}

class StartDateControllerSpec extends BaseSpec with MockitoSugar with GuiceOneAppPerSuite {

  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAuthJourney: AuthJourney = mock[AuthJourney]

  val thisYearStr: String = "2015"
  val fakePersonDetails: PersonDetails = Fixtures.buildPersonDetails

  override def afterEach: Unit =
    reset(mockLocalSessionCache, mockAuthJourney)

  trait LocalSetup {

    val sessionCacheResponse: Option[CacheMap] = Some(
      CacheMap(
        "id",
        Map("primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))))

    val requestWithForm: Request[_] = FakeRequest()

    val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = requestWithForm.asInstanceOf[Request[A]])
        )
    }

    def controller =
      new StartDateController(
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

    "return 200 when passed PrimaryAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      val result = controller.onPageLoad(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "return 200 when passed SoleAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      override val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map("soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to 'edit address' when passed PostalAddrType as this step is not valid for postal" in new LocalSetup {
      override val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/postal/edit-address")
      verify(controller.sessionCache, times(0)).fetch()(any(), any())
    }

    "redirect back to start of journey if submittedAddressDto is missing from keystore" in new LocalSetup {
      override val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.onPageLoad(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(controller.sessionCache, times(1)).fetch()(any(), any())
    }
  }

  "Calling AddressController.processEnterStartDate" should {

    "return 303 when passed PrimaryAddrType and a valid form with low numbers" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016")

      val result = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
      verify(controller.sessionCache, times(1))
        .cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(1, 1, 2016)))(any(), any(), any())
    }

    "return 303 when passed PrimaryAddrType and date is today" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "2", "startDate.month" -> "2", "startDate.year" -> "2016")

      val result = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
      verify(controller.sessionCache, times(1))
        .cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(2, 2, 2016)))(any(), any(), any())
    }

    "redirect to the changes to sole address page when passed PrimaryAddrType and a valid form with high numbers" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "12", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/changes")
      verify(controller.sessionCache, times(1))
        .cache(meq("soleSubmittedStartDateDto"), meq(DateDto.build(31, 12, 2015)))(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and missing date fields" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "").withFormUrlEncodedBody()

      val result =
        controller.onSubmit(PrimaryAddrType)(requestWithForm)
      status(result) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and day out of range - too early" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "0", "startDate.month" -> "1", "startDate.year" -> thisYearStr)

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(requestWithForm)
      status(result) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and day out of range - too late" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "32", "startDate.month" -> "1", "startDate.year" -> thisYearStr)

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and month out of range at lower bound" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "0", "startDate.year" -> thisYearStr)

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(requestWithForm)
      status(result) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and month out of range at upper bound" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "13", "startDate.year" -> thisYearStr)

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(requestWithForm)
      status(result) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and the updated start date is not after the start date on record" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "3", "startDate.month" -> "2", "startDate.year" -> "2016")

      override val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = requestWithForm,
              personDetails = Some(
                PersonDetails("", emptyPerson, Some(addressLine(startDate = Some(new LocalDate(2016, 11, 22)))), None)))
              .asInstanceOf[UserRequest[A]]
          )
      }

      val result: Future[Result] = controller.onSubmit(PrimaryAddrType)(requestWithForm)
      status(result) shouldBe BAD_REQUEST
      verify(controller.sessionCache, times(1)).cache(any(), any())(any(), any(), any())
    }

    "return a 400 when startDate is earlier than recorded with sole address type" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")

      val result = controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with sole address type" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")

      override val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = requestWithForm,
              personDetails = Some(
                PersonDetails("", emptyPerson, Some(addressLine(startDate = Some(new LocalDate(2015, 3, 15)))), None)))
              .asInstanceOf[UserRequest[A]]
          )
      }

      val result = controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is earlier than recorded with primary address type" in new LocalSetup {

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")

      val result = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with primary address type" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")

      val result = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to correct successful url when supplied with startDate after recorded with sole address type" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "16", "startDate.month" -> "03", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(SoleAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/changes")
    }

    "redirect to correct successful url when supplied with startDate after startDate on record with primary address" in new LocalSetup {
      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no startDate is on record" in new LocalSetup {
      val personDetailsNoStartDate =
        fakePersonDetails.copy(address = fakePersonDetails.address.map(_.copy(startDate = None)))
      val personDetailsResponse = PersonDetailsSuccessResponse(personDetailsNoStartDate)

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no address is on record" in new LocalSetup {
      val personDetailsNoAddress = fakePersonDetails.copy(address = None)
      val personDetailsResponse = PersonDetailsSuccessResponse(personDetailsNoAddress)

      override val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      val result = controller.onSubmit(PrimaryAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
    }
  }
}
