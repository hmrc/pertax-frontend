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
import controllers.bindable.{PostalAddrType, PrimaryAddrType, SoleAddrType}
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.DateDto
import models.{AddressChanged, AddressJourneyTTLModel, ETag, MovedToScotland, NonFilerSelfAssessmentUser}
import org.joda.time.LocalDate
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{reset, times, verify, when}
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.{I18nSupport, Lang, Messages, MessagesApi, MessagesImpl}
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsFormUrlEncoded, MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.http.Status.{OK, SEE_OTHER}
import repositories.EditAddressLockRepository
import services.{AddressMovedService, CitizenDetailsService, LocalSessionCache, PersonDetailsResponse, PersonDetailsSuccessResponse, UpdateAddressResponse, UpdateAddressSuccessResponse}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{ReviewChangesView, UpdateAddressConfirmationView}

import scala.concurrent.{ExecutionContext, Future}

class AddressSubmissionControllerSpec extends BaseSpec with MockitoSugar {

  lazy val messagesApi: MessagesApi = injected[MessagesApi]
  implicit val lang: Lang = Lang("en-gb")
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  implicit lazy val ec: ExecutionContext = injected[ExecutionContext]

  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val mockAddressMovedService: AddressMovedService = mock[AddressMovedService]
  val mockEditAddressLockRepository: EditAddressLockRepository = mock[EditAddressLockRepository]
  val mockAuthJourney: AuthJourney = mock[AuthJourney]
  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  def pruneDataEvent(dataEvent: DataEvent): DataEvent =
    dataEvent
      .copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token", detail = dataEvent.detail - "credId")

  override def beforeEach: Unit =
    reset(
      mockLocalSessionCache,
      mockAuditConnector,
      mockEditAddressLockRepository,
      mockAuthJourney,
      mockCitizenDetailsService,
      mockAddressMovedService
    )

  trait LocalSetup {

    lazy val nino: Nino = fakeNino

    lazy val fakePersonDetails = buildPersonDetails

    lazy val fakeAddress = buildFakeAddress

    def personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)

    def sessionCacheResponse: Option[CacheMap]

    def thisYearStr: String = "2019"

    def eTagResponse: Option[ETag] = Some(ETag("115"))

    def updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse

    def isInsertCorrespondenceAddressLockSuccessful: Boolean = true

    def getEditedAddressIndicators: List[AddressJourneyTTLModel] = List.empty

    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

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

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(
            request = currentRequest,
            saUser = NonFilerSelfAssessmentUser
          ).asInstanceOf[UserRequest[A]]
        )
    })

    def controller =
      new AddressSubmissionController(
        mockCitizenDetailsService,
        mockAddressMovedService,
        mockEditAddressLockRepository,
        mockAuthJourney,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        injected[WithActiveTabAction],
        mockAuditConnector,
        injected[MessagesControllerComponents],
        injected[UpdateAddressConfirmationView],
        injected[ReviewChangesView],
        injected[DisplayAddressInterstitialView]
      )(injected[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], ec)
  }

  "Calling AddressController.onPageLoad" should {

    "return 200 if both SubmittedAddressDto and SubmittedStartDateDto are present in keystore for non-postal" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      val result = controller.onPageLoad(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "return 200 if only SubmittedAddressDto is present in keystore for postal" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )))

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for non-postal" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "redirect back to start of journey if SubmittedAddressDto is missing from keystore for postal" in new LocalSetup {
      override def sessionCacheResponse = Some(
        CacheMap(
          "id",
          Map()
        )
      )

      val result = controller.onPageLoad(PostalAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "display no message relating to the date the address started when the primary address has not changed" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      val result = controller.onPageLoad(PrimaryAddrType)(FakeRequest())

      contentAsString(result) shouldNot include(controller.messagesApi("label.when_this_became_your_main_home"))
    }

    "display no message relating to the date the address started when the primary address has not changed when the postcode is in lower case" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedAddressDto" -> Json.toJson(
                asAddressDto(fakeStreetTupleListAddressForUnmodifiedLowerCase)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      val result = controller.onPageLoad(PrimaryAddrType)(FakeRequest())

      contentAsString(result) shouldNot include(Messages("label.when_this_became_your_main_home"))
    }

    "display no message relating to the date the address started when the primary address has not changed when the postcode entered has no space" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedAddressDto" -> Json.toJson(
                asAddressDto(fakeStreetTupleListAddressForUnmodifiedNoSpaceInPostcode)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      val result = controller.onPageLoad(PrimaryAddrType)(FakeRequest())

      contentAsString(result) shouldNot include(Messages("label.when_this_became_your_main_home"))
    }

    "display a message relating to the date the address started when the primary address has changed" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      val result = controller.onPageLoad(PrimaryAddrType)(FakeRequest())

      contentAsString(result) should include(Messages("label.when_this_became_your_main_home"))
    }

    "display the appropriate label for address when the sole address has changed" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "soleSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModifiedPostcode)),
              "soleSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      contentAsString(result) should include(Messages("label.your_new_address"))
      contentAsString(result) should include(Messages("label.when_you_started_living_here"))
    }

    "display the appropriate label for address when the sole address has not changed" in new LocalSetup {
      override def sessionCacheResponse =
        Some(
          CacheMap(
            "id",
            Map(
              "soleSubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "soleSubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      val result = controller.onPageLoad(SoleAddrType)(FakeRequest())

      contentAsString(result) should include(Messages("label.your_address"))
      contentAsString(result) shouldNot include(Messages("label.when_you_started_living_here"))
    }
  }

  "Calling AddressController.onSubmit" should {

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

    "redirect to start of journey if primarySubmittedStartDateDto is missing from the cache, and the journey type is PrimaryAddrType" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )))

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(controller.editAddressLockRepository, times(0)).insert(meq(nino.withoutSuffix), meq(SoleAddrType))

    }

    "redirect to start of journey if soleSubmittedStartDateDto is missing from the cache, and the journey type is SoleAddrType" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "soleSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(SoleAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "render the thank-you page if postalSubmittedStartDateDto is not in the cache, and the journey type is PostalAddrType" in new LocalSetup {
      override lazy val fakeAddress =
        buildFakeAddress.copy(`type` = Some("Correspondence"), startDate = Some(LocalDate.now))
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
      verify(mockCitizenDetailsService, times(1)).updateAddress(meq(nino), meq("115"), meq(fakeAddress))(any())
    }

    "redirect to start of journey if primarySubmittedAddressDto is missing from the cache" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(FakeRequest())

      status(result) shouldBe SEE_OTHER
      redirectLocation(result) shouldBe Some("/personal-account/personal-details")

      verify(mockAuditConnector, times(0)).sendEvent(any())(any(), any())
      verify(mockLocalSessionCache, times(1)).fetch()(any(), any())
    }

    "render the thank-you page and log a postcodeAddressSubmitted audit event upon successful submission of an unmodified address" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
              "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      override def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(FakeRequest())

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
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
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

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

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
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForManualyEntered)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(FakeRequest())

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
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "primarySelectedAddressRecord" -> Json.toJson(fakeStreetPafAddressRecord),
              "primarySubmittedAddressDto"   -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForModified)),
              "primarySubmittedStartDateDto" -> Json.toJson(DateDto.build(15, 3, 2015))
            )
          ))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PrimaryAddrType)(FakeRequest())

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
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "postalSubmittedAddressDto" -> Json.toJson(asAddressDto(fakeStreetTupleListAddressForUnmodified))
            )))

      override def currentRequest[A]: Request[A] =
        FakeRequest("POST", "/test")
          .asInstanceOf[Request[A]]

      val result = controller.onSubmit(PostalAddrType)(FakeRequest())

      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
