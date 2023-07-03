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

import controllers.controllershelpers.RlsInterruptHelper
import models.PersonDetails
import models.admin.{FeatureFlag, RlsInterruptToggle}
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{any, eq => meq}
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, status}
import repositories.EditAddressLockRepository
import services.admin.FeatureFlagService
import uk.gov.hmrc.http.cache.client.CacheMap
import viewmodels.PersonalDetailsViewModel
import views.html.personaldetails.PersonalDetailsView

import scala.concurrent.Future

class PersonalDetailsControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {
    def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

    val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))

    def rlsInterruptHelper = new RlsInterruptHelper(cc, injected[EditAddressLockRepository], mockFeatureFlagService)

    def controller =
      new PersonalDetailsController(
        injected[PersonalDetailsViewModel],
        mockEditAddressLockRepository,
        mockAuthJourney,
        addressJourneyCachingHelper,
        mockAuditConnector,
        rlsInterruptHelper,
        mockAgentClientAuthorisationService,
        cc,
        displayAddressInterstitialView,
        injected[PersonalDetailsView],
        mockFeatureFlagService,
        internalServerErrorView
      )
  }

  "Calling AddressController.redirectToYourProfile" must {
    "redirect to the profile-and-settings page" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] = None

      val result: Future[Result] = controller.redirectToYourProfile()(FakeRequest())

      status(result) mustBe MOVED_PERMANENTLY
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }
  }

  "Calling AddressController.onPageLoad" must {
    "redirect to the rls interrupt page" when {
      "main address has an rls status with true" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] = None

        override def personDetailsResponse: PersonDetails = {
          val address = fakeAddress.copy(isRls = true)
          fakePersonDetails.copy(address = Some(address))
        }

        override def personDetailsForRequest: Option[PersonDetails] = {
          val address = fakeAddress.copy(isRls = true)
          Some(fakePersonDetails.copy(address = Some(address)))
        }

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }

      "postal address has an rls status with true" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] = None

        override def personDetailsResponse: PersonDetails = {
          val address = fakeAddress.copy(isRls = true)
          fakePersonDetails.copy(correspondenceAddress = Some(address))
        }

        override def personDetailsForRequest: Option[PersonDetails] = {
          val address = fakeAddress.copy(isRls = true)
          Some(fakePersonDetails.copy(address = Some(address)))
        }

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }

      "main and postal address has an rls status with true" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] = None

        override def personDetailsResponse: PersonDetails = {
          val address = fakeAddress.copy(isRls = true)
          fakePersonDetails.copy(address = Some(address), correspondenceAddress = Some(address))
        }

        override def personDetailsForRequest: Option[PersonDetails] = {
          val address = fakeAddress.copy(isRls = true)
          Some(fakePersonDetails.copy(address = Some(address)))
        }

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }
    }

    "show the your profile page" when {
      "no address has an rls status with true" in new LocalSetup {
        override def sessionCacheResponse: Option[CacheMap] = None

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe OK
      }
    }
  }

  "Calling AddressController.onPageLoad" must {
    "call citizenDetailsService.fakePersonDetails and return 200" in new LocalSetup {
      override def sessionCacheResponse: Option[CacheMap] =
        Some(
          CacheMap(
            "id",
            Map(
              "addressPageVisitedDto" -> Json
                .toJson(AddressPageVisitedDto(true))
            )
          )
        )

      val result: Future[Result] = controller.onPageLoad(FakeRequest())

      status(result) mustBe OK
      verify(mockLocalSessionCache, times(1))
        .cache(
          meq("addressPageVisitedDto"),
          meq(AddressPageVisitedDto(true))
        )(any(), any(), any())
      verify(mockEditAddressLockRepository, times(1)).get(any())
    }
  }
}
