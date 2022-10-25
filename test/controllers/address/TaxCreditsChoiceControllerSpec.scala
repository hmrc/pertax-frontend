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

import controllers.controllershelpers.AddressJourneyCachingHelper
import models.admin.{AddressTaxCreditsBrokerCallToggle, FeatureFlag, TaxcalcToggle}
import models.dto.{AddressPageVisitedDto, TaxCreditsChoiceDto}
import models.{CacheIdentifier, NonFilerSelfAssessmentUser, PersonDetails, SelfAssessmentUserType}
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, spy, times, verify, when}
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.libs.json.Json
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.admin.FeatureFlagService
import services.{LocalSessionCache, TaxCreditsService}
import testUtils.BaseSpec
import testUtils.Fixtures.buildPersonDetailsCorrespondenceAddress
import uk.gov.hmrc.http.{HeaderNames, HttpResponse}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.Future

class TaxCreditsChoiceControllerSpec extends BaseSpec {

  val mockTaxCreditsService: TaxCreditsService   = mock[TaxCreditsService]
  val mockLocalSessionCache: LocalSessionCache   = mock[LocalSessionCache]
  val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]
  val mockAddressJourneyCachingHelper            = mock[AddressJourneyCachingHelper]

  override implicit lazy val app: Application = localGuiceApplicationBuilder(saUserType, personDetailsForRequest)
    .overrides(
      bind[LocalSessionCache].toInstance(mockLocalSessionCache),
      bind[TaxCreditsService].toInstance(mockTaxCreditsService),
      bind[FeatureFlagService].toInstance(mockFeatureFlagService),
      bind[AddressJourneyCachingHelper].toInstance(mockAddressJourneyCachingHelper)
    )
    .build()

  override def beforeEach(): Unit = {
    reset(mockLocalSessionCache, mockTaxCreditsService, mockFeatureFlagService)
    super.beforeEach()
  }

  def currentRequest[A]: Request[A] = FakeRequest().asInstanceOf[Request[A]]

  def personDetailsForRequest: Option[PersonDetails] = Some(buildPersonDetailsCorrespondenceAddress)

  def saUserType: SelfAssessmentUserType = NonFilerSelfAssessmentUser

  val sessionCacheResponse: Option[CacheMap] =
    Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

  when(mockLocalSessionCache.cache(any(), any())(any(), any(), any())) thenReturn {
    Future.successful(CacheMap("id", Map.empty))
  }
  when(mockLocalSessionCache.remove()(any(), any())) thenReturn {
    Future.successful(mock[HttpResponse])
  }

  val controller = injected[TaxCreditsChoiceController]

  "onPageLoad" when {
    "Tax-credit-broker call is used" must {
      "return OK if the user does not receive tax credits" in {
        val arg = ArgumentCaptor.forClass(classOf[Result])

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))
        when(mockTaxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(Some(false)))
        when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
          .thenReturn(Future.successful(Ok("Page")))

        controller.onPageLoad(currentRequest)

        verify(mockAddressJourneyCachingHelper, times(1)).enforceDisplayAddressPageVisited(arg.capture)(any())
        verify(mockTaxCreditsService, times(1)).checkForTaxCredits(any())(any())
        val argCaptorValue: Result = arg.getValue
        argCaptorValue.header.status mustBe OK
      }

      "return SEE_OTHER and the correct redirect if the user hasn't got tax credits" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))
        when(mockTaxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(Some(false)))
        when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
          .thenReturn(Future.successful(Ok("Page")))

        val controller = app.injector.instanceOf[TaxCreditsChoiceController]

        val result = controller.onPageLoad(currentRequest)

        contentAsString(result) mustBe ""
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/your-address/residential/do-you-live-in-the-uk")

        verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
        verify(mockTaxCreditsService, times(1)).checkForTaxCredits(any())(any())
      }

      "return INTERNAL_SERVER_ERROR and no redirect URL if the service returns None" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))
        when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
          .thenReturn(Future.successful(Ok("Page")))

        val controller = app.injector.instanceOf[TaxCreditsChoiceController]

        when(mockTaxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(None))
        when(mockLocalSessionCache.fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())) thenReturn {
          Future.successful(Some(AddressPageVisitedDto(true)))
        }

        val result = controller.onPageLoad(currentRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        redirectLocation(result) mustBe None

        verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
      }

      "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in {
        when(mockLocalSessionCache.fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())) thenReturn {
          Future.successful(None)
        }
        when(mockAddressJourneyCachingHelper.addToCache(any(), any())(any(), any())) thenReturn {
          Future.successful(CacheMap("id", Map.empty))
        }
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, true)))
        when(mockTaxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(Some(true)))
        when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
          .thenReturn(Future.successful(Ok("Page")))

        val result = controller.onPageLoad(currentRequest)

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
        verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
      }
    }

    "Tax-credit-broker call is not used and the question ask to the user" in {
      "return SEE_OTHER and the correct redirect if the user has tax credits" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, false)))
        when(mockTaxCreditsService.checkForTaxCredits(any())(any())).thenReturn(Future.successful(Some(true)))
        when(mockLocalSessionCache.fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())) thenReturn {
          Future.successful(Some(AddressPageVisitedDto(true)))
        }
        when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
          .thenReturn(Future.successful(Ok("Page")))

        val controller = app.injector.instanceOf[TaxCreditsChoiceController]

        val result = controller.onPageLoad(currentRequest)

        contentAsString(result) mustBe ""
        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("http://localhost:9362/tax-credits-service/personal/change-address")

        verify(mockLocalSessionCache, times(1)).fetchAndGetEntry[AddressPageVisitedDto](any())(any(), any(), any())
      }

    }
  }

  "onSubmit" must {
    "redirect to expected tax credits page when supplied with value = Yes (true)" in {

      val controller = app.injector.instanceOf[TaxCreditsChoiceController]

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, false)))
      when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
        Future.successful(sessionCacheResponse)
      }
      when(mockAddressJourneyCachingHelper.addToCache(any(), any())(any(), any())) thenReturn {
        Future.successful(CacheMap("id", Map.empty))
      }
      when(mockEditAddressLockRepository.insert(any(), any())) thenReturn {
        Future.successful(true)
      }
      when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
        .thenReturn(Future.successful(Ok("Page")))

      val result =
        controller.onSubmit(
          FakeRequest("POST", "")
            .withFormUrlEncodedBody("taxCreditsChoice" -> "true")
        )

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("http://localhost:9362/tax-credits-service/personal/change-address")
    }

    "redirect to InternationalAddressChoice page when supplied with value = No (false)" in {
      val controller = app.injector.instanceOf[TaxCreditsChoiceController]

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, false)))
      when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
        Future.successful(sessionCacheResponse)
      }
      when(
        mockAddressJourneyCachingHelper.addToCache(any(), any())(any(), any())
      ) thenReturn {
        Future.successful(CacheMap("id", Map.empty))
      }
      when(mockEditAddressLockRepository.insert(any(), any())) thenReturn {
        Future.successful(true)
      }

      val result = controller.onSubmit(
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("taxCreditsChoice" -> "false")
      )

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/do-you-live-in-the-uk")
    }

    "return a bad request when supplied no value" in {
      when(mockLocalSessionCache.fetch()(any(), any())) thenReturn {
        Future.successful(sessionCacheResponse)
      }
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, false)))

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }
}
