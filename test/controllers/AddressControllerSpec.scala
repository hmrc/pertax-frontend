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
import controllers.controllershelpers.{AddressJourneyCachingHelper, PersonalDetailsCardGenerator}
import models._
import models.dto._
import org.joda.time.LocalDate
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
            "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
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
            "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
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
            "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodifiedLowerCase)),
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
            "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)),
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
            "soleSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)),
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
            "soleSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
            "soleSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
          )
        ))

      val result = controller.reviewChanges(SoleAddrType)(FakeRequest())

      contentAsString(result) should include(Messages("label.your_address"))
      contentAsString(result) shouldNot include(Messages("label.when_you_started_living_here"))
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
          "nino"              -> Some(Fixtures.fakeNino.nino),
          "etag"              -> Some("115"),
          "submittedLine1"    -> submittedLine1,
          "submittedLine2"    -> Some("Fake Town"),
          "submittedLine3"    -> Some("Fake City"),
          "submittedLine4"    -> Some("Fake Region"),
          "submittedPostcode" -> Some("AA1 1AA"),
          "submittedCountry"  -> None,
          "addressType"       -> addressType,
          "submittedUPRN"     -> uprn,
          "originalLine1"     -> Some("1 Fake Street").filter(x => includeOriginals),
          "originalLine2"     -> Some("Fake Town").filter(x => includeOriginals),
          "originalLine3"     -> Some("Fake City").filter(x => includeOriginals),
          "originalLine4"     -> Some("Fake Region").filter(x => includeOriginals),
          "originalPostcode"  -> Some("AA1 1AA").filter(x => includeOriginals),
          "originalCountry"   -> Some("Country(UK,United Kingdom)").filter(x => includeOriginals),
          "originalUPRN"      -> uprn.filter(x => includeOriginals)
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

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            ).asInstanceOf[UserRequest[A]]
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
            ).asInstanceOf[UserRequest[A]]
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
            ).asInstanceOf[UserRequest[A]]
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
            "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
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
            ).asInstanceOf[UserRequest[A]]
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
            "postalSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
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
            "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForManualyEntered)),
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
            ).asInstanceOf[UserRequest[A]]
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
            "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModified)),
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
            ).asInstanceOf[UserRequest[A]]
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
            ).asInstanceOf[UserRequest[A]]
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

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = FakeRequest("POST", "/test")
                .asInstanceOf[Request[A]]
            ).asInstanceOf[UserRequest[A]]
          )
      })

      val result = controller.showAddressAlreadyUpdated(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
    }
  }
}
