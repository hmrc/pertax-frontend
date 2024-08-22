/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import config.ConfigDecorator
import connectors.AddressLookupConnector
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models._
import models.addresslookup.RecordSet
import models.dto.AddressDto
import org.mockito.ArgumentMatchers.any
import play.api.http.Status.NO_CONTENT
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import repositories.JourneyCacheRepository
import services._
import testUtils.Fixtures._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.UpdateAddressConfirmationView

import scala.concurrent.Future

trait AddressBaseSpec extends BaseSpec {

  val mockJourneyCacheRepository: JourneyCacheRepository                   = mock[JourneyCacheRepository]
  val mockAddressLookupConnector: AddressLookupConnector                   = mock[AddressLookupConnector]
  val mockCitizenDetailsService: CitizenDetailsService                     = mock[CitizenDetailsService]
  val mockAddressMovedService: AddressMovedService                         = mock[AddressMovedService]
  val mockAuditConnector: AuditConnector                                   = mock[AuditConnector]
  val mockAgentClientAuthorisationService: AgentClientAuthorisationService = mock[AgentClientAuthorisationService]

  lazy val addressJourneyCachingHelper = new AddressJourneyCachingHelper(mockJourneyCacheRepository)

  lazy val cc: MessagesControllerComponents                               = inject[MessagesControllerComponents]
  lazy val errorRenderer: ErrorRenderer                                   = inject[ErrorRenderer]
  lazy val displayAddressInterstitialView: DisplayAddressInterstitialView = inject[DisplayAddressInterstitialView]
  lazy val updateAddressConfirmationView: UpdateAddressConfirmationView   = inject[UpdateAddressConfirmationView]
  lazy val internalServerErrorView: InternalServerErrorView               = inject[InternalServerErrorView]

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  implicit lazy val configDecorator: ConfigDecorator = inject[ConfigDecorator]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockAuthJourney,
      mockJourneyCacheRepository,
      mockAddressLookupConnector,
      mockCitizenDetailsService,
      mockAddressMovedService,
      mockEditAddressLockRepository,
      mockAuditConnector,
      mockAgentClientAuthorisationService
    )
  }

  val thisYearStr: String = "2019"

  def pruneDataEvent(dataEvent: DataEvent): DataEvent =
    dataEvent
      .copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token", detail = dataEvent.detail - "credId")

  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  trait AddressControllerSetup {

    lazy val nino: Nino                       = fakeNino
    lazy val fakePersonDetails: PersonDetails = buildPersonDetails
    lazy val fakeAddress: Address             = buildFakeAddress

    def controller: AddressController
    def currentRequest[A]: Request[A]
    def saUserType: SelfAssessmentUserType                                            = NonFilerSelfAssessmentUser
    def personDetailsForRequest: Option[PersonDetails]                                = Some(buildPersonDetailsCorrespondenceAddress)
    def personDetailsResponse: PersonDetails                                          = fakePersonDetails
    def eTagResponse: Option[ETag]                                                    = Some(ETag("115"))
    def updateAddressResponse(): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
      EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(NO_CONTENT, ""))))
    def getAddressesLockResponse: AddressesLock                                       = AddressesLock(main = false, postal = false)
    def addressLookupResponse: RecordSet                                              = oneAndTwoOtherPlacePafRecordSet
    def isInsertCorrespondenceAddressLockSuccessful: Boolean                          = true
    def getEditedAddressIndicators: List[AddressJourneyTTLModel]                      = List.empty

    when(mockAgentClientAuthorisationService.getAgentClientStatus(any(), any(), any())).thenReturn(
      Future.successful(true)
    )

    when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(
      Future.successful(AuditResult.Success)
    )
    when(mockCitizenDetailsService.personDetails(any())(any(), any())).thenReturn(
      EitherT[Future, UpstreamErrorResponse, PersonDetails](
        Future.successful(Right(personDetailsResponse))
      )
    )
    when(mockCitizenDetailsService.getEtag(any())(any(), any())).thenReturn(
      EitherT[Future, UpstreamErrorResponse, Option[ETag]](
        Future.successful(Right(eTagResponse))
      )
    )
    when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any(), any())).thenReturn(
      updateAddressResponse()
    )
    when(mockEditAddressLockRepository.insert(any(), any())).thenReturn(
      Future.successful(isInsertCorrespondenceAddressLockSuccessful)
    )
    when(mockEditAddressLockRepository.get(any())).thenReturn(
      Future.successful(getEditedAddressIndicators)
    )
    when(mockEditAddressLockRepository.getAddressesLock(any())(any())).thenReturn(
      Future.successful(getAddressesLockResponse)
    )
    when(mockAddressMovedService.moved(any[String](), any[String]())(any(), any())).thenReturn(
      Future.successful(MovedToScotland)
    )
    when(mockAddressMovedService.toMessageKey(any[AddressChanged]())).thenReturn(
      None
    )

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(
            request = currentRequest[A],
            personDetails = personDetailsForRequest,
            saUser = saUserType
          )
        )
    })

  }
}
