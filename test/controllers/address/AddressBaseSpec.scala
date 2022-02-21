/*
 * Copyright 2022 HM Revenue & Customs
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
import error.ErrorRenderer
import models._
import models.dto.AddressDto
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import repositories.EditAddressLockRepository
import services._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import util.Fixtures._
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.UpdateAddressConfirmationView

import scala.concurrent.Future

trait AddressBaseSpec extends BaseSpec {

  val mockAuthJourney: AuthJourney = mock[AuthJourney]
  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAddressLookupService: AddressLookupService = mock[AddressLookupService]
  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val mockAddressMovedService: AddressMovedService = mock[AddressMovedService]
  val mockEditAddressLockRepository: EditAddressLockRepository = mock[EditAddressLockRepository]
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  lazy val addressJourneyCachingHelper = new AddressJourneyCachingHelper(mockLocalSessionCache)

  lazy val messagesApi: MessagesApi = injected[MessagesApi]

  lazy val withActiveTabAction: WithActiveTabAction = injected[WithActiveTabAction]
  lazy val cc: MessagesControllerComponents = injected[MessagesControllerComponents]
  lazy val errorRenderer: ErrorRenderer = injected[ErrorRenderer]
  lazy val displayAddressInterstitialView: DisplayAddressInterstitialView = injected[DisplayAddressInterstitialView]
  lazy val updateAddressConfirmationView: UpdateAddressConfirmationView = injected[UpdateAddressConfirmationView]

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  implicit lazy val configDecorator: ConfigDecorator = injected[ConfigDecorator]

  override def beforeEach: Unit =
    reset(
      mockAuthJourney,
      mockLocalSessionCache,
      mockAddressLookupService,
      mockCitizenDetailsService,
      mockAddressMovedService,
      mockEditAddressLockRepository,
      mockAuditConnector
    )

  val thisYearStr: String = "2019"

  def pruneDataEvent(dataEvent: DataEvent): DataEvent =
    dataEvent
      .copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token", detail = dataEvent.detail - "credId")

  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  trait AddressControllerSetup {

    lazy val nino: Nino = fakeNino

    lazy val fakePersonDetails: PersonDetails = buildPersonDetails

    lazy val fakeAddress: Address = buildFakeAddress

    def controller: AddressController

    def sessionCacheResponse: Option[CacheMap]

    def currentRequest[A]: Request[A]

    def saUserType: SelfAssessmentUserType = NonFilerSelfAssessmentUser

    def personDetailsForRequest: Option[PersonDetails] = Some(buildPersonDetailsCorrespondenceAddress)

    def personDetailsResponse: PersonDetailsResponse = PersonDetailsSuccessResponse(fakePersonDetails)

    def eTagResponse: Option[ETag] = Some(ETag("115"))

    def updateAddressResponse: UpdateAddressResponse = UpdateAddressSuccessResponse

    def addressLookupResponse: AddressLookupResponse = AddressLookupSuccessResponse(oneAndTwoOtherPlacePafRecordSet)

    def isInsertCorrespondenceAddressLockSuccessful: Boolean = true

    def getEditedAddressIndicators: List[AddressJourneyTTLModel] = List.empty

    when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn {
      Future.successful(CacheMap("id", Map.empty))
    }
    when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
      Future.successful(sessionCacheResponse)
    }
    when(mockLocalSessionCache.remove()(any(), any())) thenReturn {
      Future.successful(mock[HttpResponse])
    }
    when(mockAddressLookupService.lookup(any(), any())(any())) thenReturn {
      Future.successful(addressLookupResponse)
    }
    when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
      Future.successful(AuditResult.Success)
    }
    when(mockCitizenDetailsService.personDetails(any())(any())) thenReturn {
      Future.successful(personDetailsResponse)
    }
    when(mockCitizenDetailsService.getEtag(any())(any())) thenReturn {
      Future.successful(eTagResponse)
    }
    when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any())) thenReturn {
      Future.successful(updateAddressResponse)
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
            request = currentRequest[A],
            personDetails = personDetailsForRequest,
            saUser = saUserType
          ).asInstanceOf[UserRequest[A]]
        )
    })
  }
}
