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
import controllers.controllershelpers.{AddressJourneyCachingHelper, PersonalDetailsCardGenerator}
import models.AddressJourneyTTLModel
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.{times, verify, when}
import org.mockito.Matchers.{eq => meq, _}
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import repositories.EditAddressLockRepository
import services.{LocalSessionCache, NinoDisplayService}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView
import views.html.personaldetails.{AddressAlreadyUpdatedView, CannotUseServiceView, PersonalDetailsView}

import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsControllerSpec extends BaseSpec with MockitoSugar {

  val mockAuditConnector = mock[AuditConnector]
  val mockAuthJourney = mock[AuthJourney]
  val mockLocalSessionCache = mock[LocalSessionCache]
  val mockEditAddressLockRepository = mock[EditAddressLockRepository]
  val mockPersonalDetailsCardGenerator: PersonalDetailsCardGenerator = mock[PersonalDetailsCardGenerator]
  val ninoDisplayService = mock[NinoDisplayService]

  lazy val displayAddressInterstitial = injected[DisplayAddressInterstitialView]
  lazy val personalDetails = injected[PersonalDetailsView]

  implicit lazy val ec: ExecutionContext = injected[ExecutionContext]

  trait LocalSetup {

    def sessionCacheResponse: Option[CacheMap]

    def getEditedAddressIndicators: List[AddressJourneyTTLModel] = List.empty

    when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn {
      Future.successful(CacheMap("id", Map.empty))
    }
    when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
      Future.successful(sessionCacheResponse)
    }
    when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
      Future.successful(AuditResult.Success)
    }
    when(ninoDisplayService.getNino(any(), any())).thenReturn {
      Future.successful(Some(Fixtures.fakeNino))
    }
    when(mockEditAddressLockRepository.get(any())) thenReturn {
      Future.successful(getEditedAddressIndicators)
    }

    when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request).asInstanceOf[UserRequest[A]]
        )
    })

    def controller =
      new PersonalDetailsController(
        mockPersonalDetailsCardGenerator,
        mockEditAddressLockRepository,
        ninoDisplayService,
        mockAuthJourney,
        new AddressJourneyCachingHelper(mockLocalSessionCache),
        injected[WithActiveTabAction],
        mockAuditConnector,
        injected[MessagesControllerComponents],
        displayAddressInterstitial,
        personalDetails
      )(mock[LocalPartialRetriever], injected[ConfigDecorator], injected[TemplateRenderer], ec) {}

    "Calling AddressController.onPageLoad" should {

      "call citizenDetailsService.fakePersonDetails and return 200" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] =
          Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

        when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(request = request)
            )
        })

        val result = controller.onPageLoad()(FakeRequest())

        status(result) shouldBe OK
        verify(mockLocalSessionCache, times(1))
          .cache(meq("addressPageVisitedDto"), meq(AddressPageVisitedDto(true)))(any(), any(), any())
        verify(mockEditAddressLockRepository, times(1)).get(any())
      }

      "send an audit event when user arrives on personal details page" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] =
          Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

        val result = controller.onPageLoad()(FakeRequest())
        val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])

        status(result) shouldBe OK
        verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
      }
    }
  }
}
