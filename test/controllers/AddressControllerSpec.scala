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
  val mockEditAddressLockRepository = mock[EditAddressLockRepository]
  val mockPersonalDetailsCardGenerator: PersonalDetailsCardGenerator = mock[PersonalDetailsCardGenerator]
  val ninoDisplayService = mock[NinoDisplayService]

  lazy val messagesApi = injected[MessagesApi]

  lazy val displayAddressInterstitial = injected[DisplayAddressInterstitialView]
  lazy val personalDetails = injected[PersonalDetailsView]
  lazy val cannotUseService = injected[CannotUseServiceView]
  lazy val addressAlreadyUpdated = injected[AddressAlreadyUpdatedView]

  implicit val lang: Lang = Lang("en-gb")
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)
  implicit lazy val ec: ExecutionContext = injected[ExecutionContext]

  override def beforeEach: Unit =
    reset(
      mockLocalSessionCache,
      mockAuditConnector,
      mockAuthJourney
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
        addressAlreadyUpdated
      )(mock[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], ec) {

        when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
          Future.successful(AuditResult.Success)
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
