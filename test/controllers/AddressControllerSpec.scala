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

package controllers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import controllers.bindable.{PostalAddrType, PrimaryAddrType, SoleAddrType}
import controllers.controllershelpers.{AddressJourneyCachingHelper, CountryHelper, PersonalDetailsCardGenerator}
import models._
import models.dto._
import org.joda.time.LocalDate
import org.jsoup.Jsoup
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{eq => meq, _}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.EditAddressLockRepository
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest
import util.fixtures.AddressFixture.{address => addressFixture}
import util.fixtures.PersonFixture._
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails._

import scala.concurrent.{ExecutionContext, Future}

class AddressControllerSpec extends BaseSpec with MockitoSugar {

  val mockAuditConnector = mock[AuditConnector]
  val mockAuthJourney = mock[AuthJourney]
  val mockLocalSessionCache = mock[LocalSessionCache]
  val mockCitizenDetailsService = mock[CitizenDetailsService]
  val mockEditAddressLockRepository = mock[EditAddressLockRepository]
  val mockPersonalDetailsCardGenerator: PersonalDetailsCardGenerator = mock[PersonalDetailsCardGenerator]
  val mockAddressLookupService: AddressLookupService = mock[AddressLookupService]
  val mockAddressMovedService: AddressMovedService = mock[AddressMovedService]
  val ninoDisplayService = mock[NinoDisplayService]

  lazy val messagesApi = injected[MessagesApi]

  lazy val displayAddressInterstitial = injected[DisplayAddressInterstitialView]
  lazy val personalDetails = injected[PersonalDetailsView]
  lazy val cannotUseService = injected[CannotUseServiceView]
  lazy val updateAddress = injected[UpdateAddressView]
  lazy val updateInternationalAddress = injected[UpdateInternationalAddressView]
  lazy val enterStartDate = injected[EnterStartDateView]
  lazy val cannotUpdateAddress = injected[CannotUpdateAddressView]
  lazy val closeCorrespondenceAdressChoice = injected[CloseCorrespondenceAddressChoiceView]
  lazy val confirmCloseCorrespondenceAddress = injected[ConfirmCloseCorrespondenceAddressView]
  lazy val updateAddressConfirmation = injected[UpdateAddressConfirmationView]
  lazy val reviewChanges = injected[ReviewChangesView]
  lazy val addressAlreadyUpdated = injected[AddressAlreadyUpdatedView]

  implicit val lang: Lang = Lang("en-gb")
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)
  implicit lazy val ec: ExecutionContext = injected[ExecutionContext]

  override def beforeEach: Unit =
    reset(
      mockLocalSessionCache,
      mockAuditConnector,
      mockEditAddressLockRepository,
      mockAuthJourney,
      mockCitizenDetailsService,
      mockPersonalDetailsCardGenerator,
      mockAddressLookupService,
      mockAddressMovedService
    )

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  trait WithAddressControllerSpecSetup {

    def fakeAddress: Address

    def nino: Nino

    def personDetailsResponse: PersonDetailsResponse

    def sessionCacheResponse: Option[CacheMap]

    def thisYearStr: String

    def eTagResponse: Option[ETag] = Some(ETag("115"))

    def updateAddressResponse: UpdateAddressResponse

    lazy val fakePersonDetails = Fixtures.buildPersonDetails

    def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

    def pruneDataEvent(dataEvent: DataEvent): DataEvent =
      dataEvent.copy(
        tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token",
        detail = dataEvent.detail - "credId")

    def isInsertCorrespondenceAddressLockSuccessful: Boolean = true

    def getEditedAddressIndicators: List[AddressJourneyTTLModel] = List.empty

    def controller =
      new AddressController(
        mockCitizenDetailsService,
        mockAddressLookupService,
        mockAddressMovedService,
        mockPersonalDetailsCardGenerator,
        injected[CountryHelper],
        mockEditAddressLockRepository,
        ninoDisplayService,
        mockAuthJourney,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        injected[WithActiveTabAction],
        mockAuditConnector,
        injected[MessagesControllerComponents],
        displayAddressInterstitial,
        personalDetails,
        cannotUseService,
        updateAddress,
        updateInternationalAddress,
        enterStartDate,
        cannotUpdateAddress,
        closeCorrespondenceAdressChoice,
        confirmCloseCorrespondenceAddress,
        updateAddressConfirmation,
        reviewChanges,
        addressAlreadyUpdated
      )(mock[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], ec) {

        when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
          Future.successful(AuditResult.Success)
        }
        when(mockCitizenDetailsService.personDetails(meq(nino))(any())) thenReturn {
          Future.successful(personDetailsResponse)
        }
        when(mockCitizenDetailsService.getEtag(any())(any())) thenReturn {
          Future.successful(eTagResponse)
        }
        when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any())) thenReturn {
          Future.successful(updateAddressResponse)
        }
        when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn {
          Future.successful(CacheMap("id", Map.empty))
        }
        when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
          Future.successful(sessionCacheResponse)
        }
        when(mockLocalSessionCache.remove()(any(), any())) thenReturn {
          Future.successful(mock[HttpResponse])
        }
        when(mockPersonalDetailsCardGenerator.getPersonalDetailsCards(any(), any())(any(), any(), any())) thenReturn {
          Seq.empty
        }
        when(mockEditAddressLockRepository.insert(any(), any())) thenReturn {
          Future.successful(isInsertCorrespondenceAddressLockSuccessful)
        }
        when(mockEditAddressLockRepository.get(any())) thenReturn {
          Future.successful(getEditedAddressIndicators)
        }
        when(mockAddressMovedService.moved(any[String](), any[String]())(any(), any())) thenReturn {
          Future.successful(MovedToScotland)
        }
        when(mockAddressMovedService.toMessageKey(any[AddressChanged]())) thenReturn {
          None
        }
        when(ninoDisplayService.getNino(any(), any())).thenReturn(Future.successful(Some(Fixtures.fakeNino)))
      }

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request).asInstanceOf[UserRequest[A]]
        )
    })
  }

  "Calling AddressController.personalDetails" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "call citizenDetailsService.fakePersonDetails and return 200" in new LocalSetup {
      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      val result = controller.personalDetails()(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1))
        .cache(meq("addressPageVisitedDto"), meq(AddressPageVisitedDto(true)))(any(), any(), any())
      verify(mockEditAddressLockRepository, times(1)).get(any())
    }

    "send an audit event when user arrives on personal details page" in new LocalSetup {
      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.personalDetails()(FakeRequest())
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])

      status(result) shouldBe OK
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }
  }

  "Calling AddressController.getAddress" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return Address when option contains address" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map()))

      val result = controller.getAddress(Some(buildFakeAddress))

      result shouldBe buildFakeAddress
    }

    "return error when address is None" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map()))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = None).asInstanceOf[UserRequest[A]]
          )
      })

      an[Exception] shouldBe thrownBy {
        controller.getAddress(None)
      }
    }
  }

  "Calling AddressController.showUpdateAddressForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "find only the selected address from the session cache and no residency choice and return 303" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "fetch the selected address and a sole residencyChoice has been selected from the session cache and return 200" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(PostalAddrType)))))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address with sole address type but residencyChoice in the session cache and still return 200" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no residency choice in the session cache and redirect to the beginning of the journey" in new LocalSetup {

      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "redirect user to beginning of journey and return 303 for postal addressType and no pagevisitedDto in cache" in new LocalSetup {

      lazy val sessionCacheResponse = None

      val result = controller.showUpdateAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and addressRecord in cache" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)),
            "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

      val result = controller.showUpdateAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and no addressRecord in cache" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.showUpdateAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no addresses in the session cache and return 303" in new LocalSetup {

      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.showUpdateAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find sole selected and submitted addresses in the session cache and return 200" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType))
          )
        ))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address but a submitted address in the session cache and return 200" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType))
          )
        ))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "show 'Enter the address' when user amends correspondence address manually and address has not been selected" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.showUpdateAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Your postal address") shouldBe true
    }

    "show 'Enter your address' when user amends residential address manually and address has not been selected" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Your address") shouldBe true
    }

    "show 'Edit the address (optional)' when user amends correspondence address manually and address has been selected" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)),
            "postalSelectedAddressRecord" -> Json.toJson(Fixtures.fakeStreetPafAddressRecord)
          )
        ))

      val result = controller.showUpdateAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Edit the address (optional)") shouldBe true
    }

    "show 'Edit your address (optional)' when user amends residential address manually and address has been selected" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)),
            "soleSelectedAddressRecord" -> Json.toJson(Fixtures.fakeStreetPafAddressRecord)
          )
        ))

      val result = controller.showUpdateAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Edit your address (optional)") shouldBe true
    }
  }

  "Calling AddressController.processUpdateAddressForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 400 when supplied invalid form input" in new LocalSetup {

      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("selectedAddressRecord" -> Json.toJson(""))))

      val result = controller.processUpdateAddressForm(PostalAddrType)(FakeRequest("POST", ""))

      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a postal journey" in new LocalSetup {

      val form = FakeRequest("POST", "").withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = form
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processUpdateAddressForm(PostalAddrType)(form)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/postal/changes")
      verify(mockLocalSessionCache, times(1)).cache(
        meq("postalSubmittedAddressDto"),
        meq(asAddressDto(fakeStreetTupleListAddressForUnmodified)))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to review changes page when supplied valid form input on a non postal journey and input default startDate into cache" in new LocalSetup {

      val form = FakeRequest("POST", "").withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = form
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processUpdateAddressForm(SoleAddrType)(form)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/changes")
      verify(mockLocalSessionCache, times(1)).cache(
        meq("soleSubmittedAddressDto"),
        meq(asAddressDto(fakeStreetTupleListAddressForUnmodified)))(any(), any(), any())
      verify(mockLocalSessionCache, times(1))
        .cache(meq("soleSubmittedStartDateDto"), meq(DateDto(LocalDate.now())))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }

  "Calling AddressController.showUpdateInternationalAddressForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

    }

    "find only the selected address from the session cache and no residency choice and return 303" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "fetch the selected address and a sole residencyChoice has been selected from the session cache and return 200" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(PostalAddrType)))))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address with sole address type but residencyChoice in the session cache and still return 200" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no residency choice in the session cache and redirect to the beginning of the journey" in new LocalSetup {

      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "redirect user to beginning of journey and return 303 for postal addressType and no pagevisitedDto in cache" in new LocalSetup {

      lazy val sessionCacheResponse = None

      val result = controller.showUpdateInternationalAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and addressRecord in cache" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)),
            "selectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord))))

      val result = controller.showUpdateInternationalAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "display edit address page and return 200 for postal addressType with pagevisitedDto and no addressRecord in cache" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.showUpdateInternationalAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no addresses in the session cache and return 303" in new LocalSetup {

      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.showUpdateInternationalAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find sole selected and submitted addresses in the session cache and return 200" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType))
          )
        ))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "find no selected address but a submitted address in the session cache and return 200" in new LocalSetup {

      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType))
          )
        ))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "show 'Enter the address' when user amends correspondence address manually and address has not been selected" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.showUpdateInternationalAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Your postal address") shouldBe true
    }

    "show 'Enter your address' when user amends residential address manually and address has not been selected" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      val doc = Jsoup.parse(contentAsString(result))
      doc.getElementsByClass("heading-xlarge").toString().contains("Your address") shouldBe true
    }

    "verify an audit event has been sent when user chooses to add/amend main address" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("soleResidencyChoiceDto" -> Json.toJson(ResidencyChoiceDto(SoleAddrType)))))

      val result = controller.showUpdateInternationalAddressForm(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }

    "verify an audit event has been sent when user chooses to add postal address" in new LocalSetup {

      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.showUpdateInternationalAddressForm(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())

      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }
  }

  "Calling AddressController.closePostalAddressChoice" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

    }

    "display the closeCorrespondenceAddressChoice form that contains the main address" in new LocalSetup {
      val result = controller.closePostalAddressChoice(FakeRequest())

      contentAsString(result) should include(fakeAddress.line1.getOrElse("line6"))

      status(result) shouldBe OK
    }
  }

  "Calling AddressController.processClosePostalAddressChoice" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 400 when supplied invalid form input" in new LocalSetup {

      val result = controller.processClosePostalAddressChoice(FakeRequest("POST", ""))

      status(result) shouldBe BAD_REQUEST
    }

    "return 303, caching closePostalAddressChoiceDto and redirecting to review changes page when supplied valid form input" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "").withFormUrlEncodedBody("closePostalAddressChoice" -> "true")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm.asInstanceOf[Request[A]])
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processClosePostalAddressChoice()(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/close-correspondence-address-confirm")
    }

    "return 303, caching closePostalAddressChoiceDto and redirecting to personal details page" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "").withFormUrlEncodedBody("closePostalAddressChoice" -> "false")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm.asInstanceOf[Request[A]])
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processClosePostalAddressChoice(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }
  }

  "Calling AddressController.confirmClosePostalAddress" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

    }

    "render the appropriate content that includes the address" in new LocalSetup {
      val result = controller.confirmClosePostalAddress(FakeRequest())

      contentAsString(result) should include(fakeAddress.line1.getOrElse("line6"))
    }
  }

  "Calling AddressController.processUpdateInternationalAddressForm" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressLookupServiceDown" -> Json.toJson(Some(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 400 when supplied invalid form input" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map("selectedAddressRecord" -> Json.toJson(""))))

      val result = controller.processUpdateInternationalAddressForm(PostalAddrType)(FakeRequest("POST", ""))

      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to review changes page when supplied valid form input on a postal journey and input default startDate into cache" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "").withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processUpdateInternationalAddressForm(PostalAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/postal/changes")
      verify(mockLocalSessionCache, times(1)).cache(
        meq("postalSubmittedAddressDto"),
        meq(asInternationalAddressDto(fakeStreetTupleListInternationalAddress)))(any(), any(), any())
      verify(mockLocalSessionCache, times(1))
        .cache(meq("postalSubmittedStartDateDto"), meq(DateDto(LocalDate.now())))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 303, caching addressDto and redirecting to enter start date page when supplied valid form input on a non postal journey" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "").withFormUrlEncodedBody(fakeStreetTupleListInternationalAddress: _*)
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processUpdateInternationalAddressForm(SoleAddrType)(requestWithForm)

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/enter-start-date")
      verify(mockLocalSessionCache, times(1)).cache(
        meq("soleSubmittedAddressDto"),
        meq(asInternationalAddressDto(fakeStreetTupleListInternationalAddress)))(any(), any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }

  "Calling AddressController.enterStartDate" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 200 when passed PrimaryAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map("primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))))

      val result = controller.enterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 200 when passed SoleAddrType and submittedAddressDto is in keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map("soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)))))

      val result = controller.enterStartDate(SoleAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect to 'edit address' when passed PostalAddrType as this step is not valid for postal" in new LocalSetup {
      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.enterStartDate(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/postal/edit-address")
      verify(mockLocalSessionCache, times(0)).fetch()(any(), any())
    }

    "redirect back to start of journey if submittedAddressDto is missing from keystore" in new LocalSetup {
      lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      val result = controller.enterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }
  }

  "Calling AddressController.processEnterStartDate" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 303 when passed PrimaryAddrType and a valid form with low numbers" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "1", "startDate.year" -> "2016")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(1, 1, 2016)))(any(), any(), any())
    }

    "return 303 when passed PrimaryAddrType and date is in the today" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "2", "startDate.month" -> "2", "startDate.year" -> "2016")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("primarySubmittedStartDateDto"), meq(DateDto.build(2, 2, 2016)))(any(), any(), any())
    }

    "redirect to the changes to sole address page when passed PrimaryAddrType and a valid form with high numbers" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "12", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })
      val result = controller.processEnterStartDate(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/changes")
      verify(mockLocalSessionCache, times(1))
        .cache(meq("soleSubmittedStartDateDto"), meq(DateDto.build(31, 12, 2015)))(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and missing date fields" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "").withFormUrlEncodedBody()

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result =
        controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())
      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and day out of range - too early" in new LocalSetup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "0", "startDate.month" -> "1", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())
      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and day out of range - too late" in new LocalSetup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "32", "startDate.month" -> "1", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and month out of range at lower bound" in new LocalSetup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "1", "startDate.month" -> "0", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())
      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and month out of range at upper bound" in new LocalSetup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "31", "startDate.month" -> "13", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())
      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(0)).cache(any(), any())(any(), any(), any())
    }

    "return 400 when passed PrimaryAddrType and the updated start date is not after the start date on record" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "3", "startDate.month" -> "2", "startDate.year" -> "2016")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm,
              personDetails = Some(PersonDetails(emptyPerson, Some(addressFixture(startDate = Some(new LocalDate(2016, 11, 22)))), None)))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result: Future[Result] = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())
      status(result) shouldBe BAD_REQUEST
      verify(mockLocalSessionCache, times(1)).cache(any(), any())(any(), any(), any())
    }

    "return a 400 when startDate is earlier than recorded with sole address type" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(SoleAddrType)(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with sole address type" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(SoleAddrType)(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is earlier than recorded with primary address type" in new LocalSetup {

      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "14", "startDate.month" -> "03", "startDate.year" -> "2015")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }

    "return a 400 when startDate is the same as recorded with primary address type" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "15", "startDate.month" -> "03", "startDate.year" -> "2015")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }

    "redirect to correct successful url when supplied with startDate after recorded with sole address type" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "16", "startDate.month" -> "03", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/sole/changes")
    }

    "redirect to correct successful url when supplied with startDate after startDate on record with primary address" in new LocalSetup {
      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no startDate is on record" in new LocalSetup {
      lazy val personDetailsNoStartDate =
        fakePersonDetails.copy(address = fakePersonDetails.address.map(_.copy(startDate = None)))
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetailsNoStartDate)

      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
    }

    "redirect to success page when no address is on record" in new LocalSetup {
      lazy val personDetailsNoAddress = fakePersonDetails.copy(address = None)
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(personDetailsNoAddress)

      val requestWithForm = FakeRequest("POST", "")
        .withFormUrlEncodedBody("startDate.day" -> "20", "startDate.month" -> "06", "startDate.year" -> thisYearStr)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = requestWithForm)
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processEnterStartDate(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/primary/changes")
    }
  }

  "Calling AddressController.reviewChanges" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "return 200 if both SubmittedAddressDto and SubmittedStartDateDto are present in keystore for non-postal" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 200 if only SubmittedAddressDto is present in keystore for postal" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
          )))

      val result = controller.reviewChanges(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for non-postal" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
          )))

      val result = controller.reviewChanges(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for postal" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map()
        )
      )

      val result = controller.reviewChanges(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "display no message relating to the date the address started when the primary address has not changed" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(PrimaryAddrType)(FakeRequest())

      contentAsString(result) shouldNot include(controller.messagesApi("label.when_this_became_your_main_home"))
    }

    "display no message relating to the date the address started when the primary address has not changed when the postcode is in lower case" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodifiedLowerCase)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(PrimaryAddrType)(FakeRequest())

      contentAsString(result) shouldNot include(Messages("label.when_this_became_your_main_home"))
    }

    "display no message relating to the date the address started when the primary address has not changed when the postcode entered has no space" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedAddressDto" -> Json.toJson(
              asAddressDto(fakeStreetTupleListAddressForUnmodifiedNoSpaceInPostcode)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(PrimaryAddrType)(FakeRequest())

      contentAsString(result) shouldNot include(Messages("label.when_this_became_your_main_home"))
    }

    "display a message relating to the date the address started when the primary address has changed" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(PrimaryAddrType)(FakeRequest())

      contentAsString(result) should include(Messages("label.when_this_became_your_main_home"))
    }

    "display the appropriate label for address when the sole address has changed" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)),
            "soleSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(SoleAddrType)(FakeRequest())

      contentAsString(result) should include(Messages("label.your_new_address"))
      contentAsString(result) should include(Messages("label.when_you_started_living_here"))
    }

    "display the appropriate label for address when the sole address has not changed" in new LocalSetup {
      lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(SoleAddrType)(FakeRequest())

      contentAsString(result) should include(Messages("label.your_address"))
      contentAsString(result) shouldNot include(Messages("label.when_you_started_living_here"))
    }
  }

  "Calling AddressController.closePostalAddressChoice" should {
    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
      override lazy val sessionCacheResponse = None

    }

    "return OK when closePostalAddressChoice is called" in new LocalSetup {

      val result = controller.closePostalAddressChoice(FakeRequest())

      status(result) shouldBe OK
    }
  }

  "Calling AddressController.processClosePostalAddressChoice" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "redirect to expected confirm close correspondence confirmation page when supplied with value = Yes (true)" in new LocalSetup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("closePostalAddressChoice" -> "true")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = requestWithForm
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processClosePostalAddressChoice(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/your-address/close-correspondence-address-confirm")
    }

    "redirect to personal details page when supplied with value = No (false)" in new LocalSetup {
      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("closePostalAddressChoice" -> "false")

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = requestWithForm
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.processClosePostalAddressChoice(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
    }

    "return a bad request when supplied no value" in new LocalSetup {
      val result = controller.processClosePostalAddressChoice(FakeRequest())

      status(result) shouldBe BAD_REQUEST
    }
  }

  "Calling AddressController.confirmClosePostalAddress" should {
    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
      override lazy val sessionCacheResponse = None

    }

    "return OK when confirmClosePostalAddress is called" in new LocalSetup {
      val result = controller.confirmClosePostalAddress(FakeRequest())

      status(result) shouldBe OK
    }
  }

  "Calling AddressController.submitConfirmClosePostalAddress" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddressWithEndDate
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(buildPersonDetailsCorrespondenceAddress)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
      override lazy val sessionCacheResponse = Some(CacheMap("id", Map.empty))

      def comparatorDataEvent(dataEvent: DataEvent, auditType: String, uprn: Option[String]) = DataEvent(
        "pertax-frontend",
        auditType,
        dataEvent.eventId,
        Map("path" -> "/test", "transactionName" -> "closure_of_correspondence"),
        Map(
          "nino" -> Some(Fixtures.fakeNino.nino),
          "etag" -> Some("115"),
          "submittedLine1" -> Some("1 Fake Street"),
          "submittedLine2" -> Some("Fake Town"),
          "submittedLine3" -> Some("Fake City"),
          "submittedLine4" -> Some("Fake Region"),
          "submittedPostcode" -> Some("AA1 1AA"),
          "submittedCountry" -> None,
          "addressType" -> Some("correspondence")
        ).collect { case (k, Some(v)) => k -> v },
        dataEvent.generatedAt
      )
    }

    "render the thank you page upon successful submission of closing the correspondence address" in new LocalSetup {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              personDetails = Some(buildPersonDetailsCorrespondenceAddress),
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]])
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitConfirmClosePostalAddress(FakeRequest())

      status(result) shouldBe OK

      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "closedAddressSubmitted", Some("GB101"))
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "redirect to personal details if there is a lock on the correspondence address for the user" in new LocalSetup {
      override def getEditedAddressIndicators: List[AddressJourneyTTLModel] =
        List(mock[AddressJourneyTTLModel])

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitConfirmClosePostalAddress(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some(routes.AddressController.personalDetails().url)

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockCitizenDetailsService, times(0)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 400 if UpdateAddressBadRequestResponse is received from citizen-details" in new LocalSetup {
      override lazy val updateAddressResponse = UpdateAddressBadRequestResponse

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitConfirmClosePostalAddress()(FakeRequest())

      status(result) shouldBe BAD_REQUEST
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an UpdateAddressUnexpectedResponse is received from citizen-details" in new LocalSetup {
      override lazy val updateAddressResponse = UpdateAddressUnexpectedResponse(HttpResponse(SEE_OTHER))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitConfirmClosePostalAddress()(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1))
        .updateAddress(meq(Fixtures.fakeNino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if an UpdateAddressErrorResponse is received from citizen-details" in new LocalSetup {
      override lazy val updateAddressResponse = UpdateAddressErrorResponse(new RuntimeException("Any exception"))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request, personDetails = Some(buildPersonDetailsCorrespondenceAddress))
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitConfirmClosePostalAddress()(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if insert address lock fails" in new LocalSetup {
      override def isInsertCorrespondenceAddressLockSuccessful: Boolean = false

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              personDetails = Some(buildPersonDetailsCorrespondenceAddress),
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]])
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitConfirmClosePostalAddress(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR

      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue

      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "closedAddressSubmitted", Some("GB101"))
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
      verify(controller.editAddressLockRepository, times(1)).insert(meq(nino.withoutSuffix), meq(PostalAddrType))
    }

    "return 500 if fetching etag from citizen details fails" in new LocalSetup {
      override def eTagResponse: Option[ETag] = None

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              personDetails = Some(buildPersonDetailsCorrespondenceAddress),
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]])
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitConfirmClosePostalAddress(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "Calling AddressController.submitChanges" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"

      def comparatorDataEvent(
                               dataEvent: DataEvent,
                               auditType: String,
                               uprn: Option[String],
                               includeOriginals: Boolean,
                               submittedLine1: Option[String] = Some("1 Fake Street"),
                               addressType: Option[String] = Some("Residential")) = DataEvent(
        "pertax-frontend",
        auditType,
        dataEvent.eventId,
        Map("path" -> "/test", "transactionName" -> "change_of_address"),
        Map(
          "nino" -> Some(Fixtures.fakeNino.nino),
          "etag" -> Some("115"),
          "submittedLine1" -> submittedLine1,
          "submittedLine2" -> Some("Fake Town"),
          "submittedLine3" -> Some("Fake City"),
          "submittedLine4" -> Some("Fake Region"),
          "submittedPostcode" -> Some("AA1 1AA"),
          "submittedCountry" -> None,
          "addressType" -> addressType,
          "submittedUPRN" -> uprn,
          "originalLine1" -> Some("1 Fake Street").filter(x => includeOriginals),
          "originalLine2" -> Some("Fake Town").filter(x => includeOriginals),
          "originalLine3" -> Some("Fake City").filter(x => includeOriginals),
          "originalLine4" -> Some("Fake Region").filter(x => includeOriginals),
          "originalPostcode" -> Some("AA1 1AA").filter(x => includeOriginals),
          "originalCountry" -> Some("Country(UK,United Kingdom)").filter(x => includeOriginals),
          "originalUPRN" -> uprn.filter(x => includeOriginals)
        ).map(t => t._2.map((t._1, _))).flatten.toMap,
        dataEvent.generatedAt
      )
    }

    "redirect to start of journey if primarySubmittedStartDateDto is missing from the cache, and the journey type is PrimaryAddrType" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
          )))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]])
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(SoleAddrType))

    }

    "redirect to start of journey if soleSubmittedStartDateDto is missing from the cache, and the journey type is SoleAddrType" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
          )))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "render the thank-you page if postalSubmittedStartDateDto is not in the cache, and the journey type is PostalAddrType" in new LocalSetup {
      override lazy val fakeAddress =
        buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
          )))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "redirect to start of journey if primarySubmittedAddressDto is missing from the cache" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "render the thank-you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressSubmitted",
        Some("GB101"),
        false)
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address, this time using postal type and having no postalSubmittedStartDateDto in the cache " in new LocalSetup {
      override lazy val fakeAddress =
        buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "postalSelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
          )
        ))

      val requestWithForm: FakeRequest[AnyContentAsFormUrlEncoded] =
        FakeRequest("POST", "/test")
          .withFormUrlEncodedBody(fakeStreetTupleListAddressForUnmodified: _*)

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = requestWithForm
                .asInstanceOf[Request[A]])
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressSubmitted",
        Some("GB101"),
        false,
        addressType = Some("Correspondence"))
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a manualAddressSubmitted audit event upon successful submission of a manually entered address" in new LocalSetup {
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForManualyEntered)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(dataEvent, "manualAddressSubmitted", None, false)
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "render the thank you page and log a postcodeAddressModifiedSubmitted audit event upon successful of a modified address" in new LocalSetup {
      override lazy val fakeAddress = buildFakeAddress.copy(line1 = Some("11 Fake Street"))
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "primarySelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
            "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModified)),
            "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              saUser = NonFilerSelfAssessmentUser,
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe OK
      val arg = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(mockAuditConnector, times(1)).sendEvent(arg.capture())(any(), any())
      val dataEvent = arg.getValue
      pruneDataEvent(dataEvent) shouldBe comparatorDataEvent(
        dataEvent,
        "postcodeAddressModifiedSubmitted",
        Some("GB101"),
        true,
        Some("11 Fake Street"))
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "return 500 when fetching etag from citizen details fails" in new LocalSetup {

      override def eTagResponse: Option[ETag] = None

      override lazy val fakeAddress =
        buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override lazy val sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map(
            "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
          )))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.submitChanges(PostalAddrType)(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "Calling AddressController.showAddressAlreadyUpdated" should {

    trait LocalSetup extends WithAddressControllerSpecSetup {
      override lazy val fakeAddress = buildFakeAddress
      override lazy val nino = Fixtures.fakeNino
      override lazy val personDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)
      override lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))
      override lazy val updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse
      override lazy val thisYearStr = "2015"
    }

    "display the showAddressAlreadyUpdated page" in new LocalSetup {

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture{
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            )
              .asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.showAddressAlreadyUpdated(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
    }
  }
}
