/*
 * Copyright 2024 HM Revenue & Customs
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
import controllers.InterstitialController
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.dto.AddressDto
import models.{Address, AddressChanged, AddressJourneyTTLModel, AddressesLock, ETag, MovedToScotland, NonFilerSelfAssessmentUser, PersonDetails, SelfAssessmentUserType, UserAnswers}
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.http.Status.NO_CONTENT
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.inject.bind
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import repositories.JourneyCacheRepository
import services.{AddressMovedService, AgentClientAuthorisationService, CitizenDetailsService}
import testUtils.Fixtures._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import views.html.InternalServerErrorView
import views.html.interstitial.DisplayAddressInterstitialView

import scala.concurrent.Future

trait NewAddressBaseSpec extends BaseSpec {
  protected def fakePOSTRequest[A]: Request[A]                                       = FakeRequest("POST", "/test").asInstanceOf[Request[A]]
  protected val mockJourneyCacheRepository: JourneyCacheRepository                   = mock[JourneyCacheRepository]
  protected val mockAddressLookupConnector: AddressLookupConnector                   = mock[AddressLookupConnector]
  protected val mockCitizenDetailsService: CitizenDetailsService                     = mock[CitizenDetailsService]
  protected val mockAddressMovedService: AddressMovedService                         = mock[AddressMovedService]
  protected val mockAuditConnector: AuditConnector                                   = mock[AuditConnector]
  protected val mockInterstitialController: InterstitialController                   = mock[InterstitialController]
  protected val mockAgentClientAuthorisationService: AgentClientAuthorisationService =
    mock[AgentClientAuthorisationService]

  protected implicit lazy val messages: Messages                               = MessagesImpl(Lang("en"), messagesApi)
  protected def cc: MessagesControllerComponents                               = app.injector.instanceOf[MessagesControllerComponents]
  protected def displayAddressInterstitialView: DisplayAddressInterstitialView =
    app.injector.instanceOf[DisplayAddressInterstitialView]
  protected def internalServerErrorView: InternalServerErrorView               = app.injector.instanceOf[InternalServerErrorView]

  implicit def configDecorator: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  protected def setupAuth(): Unit =
    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(
            request = request,
            personDetails = personDetailsForRequest,
            saUser = saUserType
          )
        )
    })

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
    setupAuth()
    when(mockAgentClientAuthorisationService.getAgentClientStatus(any(), any(), any()))
      .thenReturn(Future.successful(true))
    when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(UserAnswers.empty))
    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
    when(mockJourneyCacheRepository.clear(any[HeaderCarrier])).thenReturn(Future.successful((): Unit))
    when(mockAuditConnector.sendEvent(any())(any(), any())).thenReturn(Future.successful(AuditResult.Success))
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
    when(mockCitizenDetailsService.updateAddress(any(), any(), any())(any(), any())).thenReturn(updateAddressResponse())
    when(mockEditAddressLockRepository.insert(any(), any()))
      .thenReturn(Future.successful(isInsertCorrespondenceAddressLockSuccessful))
    when(mockEditAddressLockRepository.get(any())).thenReturn(Future.successful(getEditedAddressIndicators))
    when(mockEditAddressLockRepository.getAddressesLock(any())(any()))
      .thenReturn(Future.successful(getAddressesLockResponse))
    when(mockAddressMovedService.moved(any[String](), any[String]())(any(), any()))
      .thenReturn(Future.successful(MovedToScotland))
    when(mockAddressMovedService.toMessageKey(any[AddressChanged]())).thenReturn(None)
  }

  protected def pruneDataEvent(dataEvent: DataEvent): DataEvent =
    dataEvent
      .copy(tags = dataEvent.tags - "X-Request-Chain" - "X-Session-ID" - "token", detail = dataEvent.detail - "credId")

  protected def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  protected def fakeAddressJourneyCachingHelper: AddressJourneyCachingHelper = new AddressJourneyCachingHelper(
    mockJourneyCacheRepository
  )(ec)

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[AuthJourney].toInstance(mockAuthJourney),
      bind[AddressJourneyCachingHelper].toInstance(fakeAddressJourneyCachingHelper),
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
      bind[AddressMovedService].toInstance(mockAddressMovedService),
      bind[AuditConnector].toInstance(mockAuditConnector)
    )
    .build()

  protected lazy val nino: Nino                       = fakeNino
  protected lazy val fakePersonDetails: PersonDetails = buildPersonDetails
  protected lazy val fakeAddress: Address             = buildFakeAddress

  protected def saUserType: SelfAssessmentUserType                                            = NonFilerSelfAssessmentUser
  protected def personDetailsForRequest: Option[PersonDetails]                                = Some(buildPersonDetailsCorrespondenceAddress)
  protected def personDetailsResponse: PersonDetails                                          = fakePersonDetails
  protected def eTagResponse: Option[ETag]                                                    = Some(ETag("115"))
  protected def updateAddressResponse(): EitherT[Future, UpstreamErrorResponse, HttpResponse] =
    EitherT[Future, UpstreamErrorResponse, HttpResponse](Future.successful(Right(HttpResponse(NO_CONTENT, ""))))
  protected def getAddressesLockResponse: AddressesLock                                       = AddressesLock(main = false, postal = false)
  protected def isInsertCorrespondenceAddressLockSuccessful: Boolean                          = true
  protected def getEditedAddressIndicators: List[AddressJourneyTTLModel]                      = List.empty

}
