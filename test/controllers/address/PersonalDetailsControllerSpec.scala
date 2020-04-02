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
import controllers.bindable.SoleAddrType
import controllers.controllershelpers.PersonalDetailsCardGenerator
import models.AddressJourneyTTLModel
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.{any, eq => meq}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.EditAddressLockRepository
import services.LocalSessionCache
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, Fixtures, LocalPartialRetriever}

import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsControllerSpec extends BaseSpec with MockitoSugar with GuiceOneAppPerSuite {

  val mockPersonalDetailsCardGenerator = mock[PersonalDetailsCardGenerator]
  val mockEditAddressLockRepository = mock[EditAddressLockRepository]
  val mockAuditConnector = mock[AuditConnector]
  val mockLocalSessionCache: LocalSessionCache = mock[LocalSessionCache]
  val mockAuthJourney: AuthJourney = mock[AuthJourney]

  override def afterEach: Unit =
    reset(mockLocalSessionCache, mockAuthJourney, mockAuditConnector)

  trait LocalSetup {

    val authActionResult: ActionBuilderFixture = new ActionBuilderFixture {
      override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
        block(
          buildUserRequest(request = request)
        )
    }

    val repoGetResult: List[AddressJourneyTTLModel] = List.empty

    implicit val userRequest = Fixtures
    implicit val configDecorator = injected[ConfigDecorator]

    val cardGeneratorResponse =
      Seq()

    def controller =
      new PersonalDetailsController(
        mockPersonalDetailsCardGenerator,
        mockEditAddressLockRepository,
        mockAuditConnector,
        mockLocalSessionCache,
        mockAuthJourney,
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents]
      )(injected[LocalPartialRetriever], configDecorator, injected[TemplateRenderer], injected[ExecutionContext]) {

        when(mockAuthJourney.authWithPersonalDetails) thenReturn
          authActionResult

        when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn
          Future.successful(CacheMap("", Map.empty))

        when(mockLocalSessionCache.fetch()(any(), any())) thenReturn
          Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

        when(mockEditAddressLockRepository.get(any())) thenReturn
          Future.successful(repoGetResult)

        when(mockAuditConnector.sendEvent(any())(any(), any())) thenReturn {
          Future.successful(AuditResult.Success)
        }

        when(mockPersonalDetailsCardGenerator.getPersonalDetailsCards(any())(any(), any(), any())) thenReturn
          cardGeneratorResponse
      }
  }

  "onPageLoad" should {

    "return OK and render the Personal Details page" in new LocalSetup {

      val result = controller.onPageLoad(FakeRequest())

      status(result) shouldBe OK
      verify(mockLocalSessionCache, times(1))
        .cache(meq("addressPageVisitedDto"), meq(AddressPageVisitedDto(true)))(any(), any(), any())
      verify(mockEditAddressLockRepository, times(1)).get(any())
    }

    "send an audit event when user arrives on personal details page" in new LocalSetup {
      lazy val sessionCacheResponse =
        Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

      val result = controller.onPageLoad()(FakeRequest())
      val eventCaptor = ArgumentCaptor.forClass(classOf[DataEvent])

      status(result) shouldBe OK
      verify(mockAuditConnector, times(1)).sendEvent(eventCaptor.capture())(any(), any())
    }
  }

  "cannotUseThisService" should {

    "return OK and render the cannot use this service page" in new LocalSetup {

      val result = controller.cannotUseThisService(SoleAddrType)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) should include("You cannot use this service to update your address")
    }
  }

  "showAddressAlreadyUpdated" should {

    "return OK and render the address already updated page" in new LocalSetup {

      val result = controller.showAddressAlreadyUpdated(SoleAddrType)(FakeRequest())
      status(result) shouldBe OK
      contentAsString(result) should include("Your address has already been updated")
    }
  }
}
