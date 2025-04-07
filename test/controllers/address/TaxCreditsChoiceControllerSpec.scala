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
import controllers.InterstitialController
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.AddressJourneyCachingHelper
import error.ErrorRenderer
import models.admin.{AddressChangeAllowedToggle, AddressTaxCreditsBrokerCallToggle}
import models.dto.AddressPageVisitedDto
import models.{ActivatedOnlineFilerSelfAssessmentUser, SelfAssessmentUserType, UserAnswers}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.JourneyCacheRepository
import routePages.HasAddressAlreadyVisitedPage
import services.{CitizenDetailsService, TaxCreditsService}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, Fixtures, WireMockHelper}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class TaxCreditsChoiceControllerSpec extends AddressBaseSpec with WireMockHelper {
  override implicit lazy val app: Application =
    localGuiceApplicationBuilder()
      .overrides(
        bind[AuthJourney].toInstance(mockAuthJourney),
        bind[AddressJourneyCachingHelper].toInstance(mockAddressJourneyCachingHelper),
        bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository),
        bind[TaxCreditsService].toInstance(mockTaxCreditsService),
        bind[InterstitialController].toInstance(mockInterstitialController),
        bind[CitizenDetailsService].toInstance(mockCitizenDetailsService)
      )
      .build()

  val mockErrorRenderer: ErrorRenderer                      = mock[ErrorRenderer]
  private lazy val controller: TaxCreditsChoiceController   = app.injector.instanceOf[TaxCreditsChoiceController]
  private lazy val mockTaxCreditsService: TaxCreditsService = mock[TaxCreditsService]
  private lazy val mockAddressJourneyCachingHelper          = mock[AddressJourneyCachingHelper]

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(mockAuthJourney, mockJourneyCacheRepository, mockTaxCreditsService, mockAddressJourneyCachingHelper)

    when(mockAuthJourney.authWithPersonalDetails)
      .thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              request = request,
              saUser = saUserType
            )
          )
      })

    when(mockErrorRenderer.futureError(any())(any(), any())).thenReturn(Future.successful(Results.InternalServerError))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
      .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false)))
    when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
      .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))
    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
    when(mockJourneyCacheRepository.clear(any())).thenReturn(Future.successful((): Unit))
  }

  def userAnswers: UserAnswers =
    UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

  "onPageLoad" when {}

  "onSubmit" must {
    "redirect to expected tax credits page when supplied with value = Yes (true)" in {
      def saUserType: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(Fixtures.saUtr)

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false)))
      when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
        .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockAddressJourneyCachingHelper.addToCache(any(), any())(any(), any())) thenReturn {
        Future.successful(UserAnswers.empty("id"))
      }
      when(mockEditAddressLockRepository.insert(any(), any())) thenReturn {
        Future.successful(true)
      }
      when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
        .thenReturn(Future.successful(Ok("Page")))

      when(mockAuthJourney.authWithPersonalDetails)
        .thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                request = currentRequest[A],
                saUser = saUserType
              )
            )
        })

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("taxCreditsChoice" -> "true")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/change-address-tax-credits")
    }

    "redirect to InternationalAddressChoice page when supplied with value = No (false)" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false)))
      when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
        .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockAddressJourneyCachingHelper.addToCache(any(), any())(any(), any())) thenReturn {
        Future.successful(UserAnswers.empty("id"))
      }
      when(mockEditAddressLockRepository.insert(any(), any())) thenReturn {
        Future.successful(true)
      }

      when(mockAuthJourney.authWithPersonalDetails)
        .thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                request = currentRequest[A],
                saUser = saUserType
              )
            )
        })

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("taxCreditsChoice" -> "false")
          .asInstanceOf[Request[A]]

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/residential/where-is-your-new-address")
      // TODO: If start change of address page experiment is successful replace above line with below
//      redirectLocation(result) mustBe Some("/personal-account/your-address/change-main-address")
    }

    "return a bad request when supplied no value" in {

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswers))
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false)))
      when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
        .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))
      when(mockAuthJourney.authWithPersonalDetails)
        .thenReturn(new ActionBuilderFixture {
          override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
            block(
              buildUserRequest(
                request = currentRequest[A],
                saUser = saUserType
              )
            )
        })

      val result = controller.onSubmit(FakeRequest())

      status(result) mustBe BAD_REQUEST
    }
  }
}
