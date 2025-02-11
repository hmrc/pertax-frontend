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
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.AddressJourneyCachingHelper
import models.admin.{AddressChangeAllowedToggle, AddressTaxCreditsBrokerCallToggle}
import models.dto.AddressPageVisitedDto
import models.{ActivatedOnlineFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser, SelfAssessmentUserType, UserAnswers}
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import play.api.Application
import play.api.http.Status.SEE_OTHER
import play.api.inject.bind
import play.api.mvc.Results.Ok
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.JourneyCacheRepository
import routePages.HasAddressAlreadyVisitedPage
import services.TaxCreditsService
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, Fixtures, WireMockHelper}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.Future

class TaxCreditsChoiceControllerSpec extends AddressBaseSpec with WireMockHelper {
  override implicit lazy val app: Application =
    localGuiceApplicationBuilder()
      .overrides(
        bind[AuthJourney].toInstance(mockAuthJourney),
        bind[AddressJourneyCachingHelper].toInstance(mockAddressJourneyCachingHelper),
        bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository),
        bind[TaxCreditsService].toInstance(mockTaxCreditsService)
      )
      .build()

  private lazy val controller: TaxCreditsChoiceController   = app.injector.instanceOf[TaxCreditsChoiceController]
  private lazy val mockTaxCreditsService: TaxCreditsService = mock[TaxCreditsService]
  private lazy val mockAddressJourneyCachingHelper          = mock[AddressJourneyCachingHelper]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuthJourney, mockJourneyCacheRepository, mockTaxCreditsService, mockAddressJourneyCachingHelper)

    when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
    when(mockJourneyCacheRepository.clear(any())).thenReturn(
      Future.successful((): Unit)
    )
  }

  def userAnswers: UserAnswers =
    UserAnswers.empty("id").setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

  "onPageLoad" when {
    "Tax-credit-broker call is used" must {
      "redirect to `do-you-live-in-the-uk` if the user does not receives tax credits" in {
        def saUserType: SelfAssessmentUserType = NotEnrolledSelfAssessmentUser(Fixtures.saUtr)

        val arg = ArgumentCaptor.forClass(classOf[Result])

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

        when(mockTaxCreditsService.isAddressChangeInPTA(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Option[Boolean]](Future.successful(Right(Some(true))))
          )
        when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
          .thenReturn(Future.successful(Ok("Fake Page")))

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

        val result = controller.onPageLoad(currentRequest)

        status(result) mustBe OK
        verify(mockFeatureFlagService, times(2)).get(any())
        verify(mockAddressJourneyCachingHelper, times(1)).enforceDisplayAddressPageVisited(arg.capture)(any())
        verify(mockTaxCreditsService, times(1)).isAddressChangeInPTA(any())(any())
        val argCaptorValue: Result = arg.getValue
        argCaptorValue.header.status mustBe SEE_OTHER
        redirectLocation(Future.successful(argCaptorValue)) mustBe Some(
          "/personal-account/your-address/residential/where-is-your-new-address"
        )
      }

      "display the 'do you get tax credits' page if the tax credits service returns None, " +
        "indicating that we don't know if the user needs to change its address on PTA or tax credits" in {
          val arg = ArgumentCaptor.forClass(classOf[Result])

          when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
            .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))
          when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
            .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))
          when(mockTaxCreditsService.isAddressChangeInPTA(any())(any()))
            .thenReturn(
              EitherT[Future, UpstreamErrorResponse, Option[Boolean]](Future.successful(Right(None)))
            )
          when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
            .thenReturn(Future.successful(Ok("Fake Page")))

          when(mockAuthJourney.authWithPersonalDetails)
            .thenReturn(new ActionBuilderFixture {
              override def invokeBlock[A](
                request: Request[A],
                block: UserRequest[A] => Future[Result]
              ): Future[Result] =
                block(
                  buildUserRequest(
                    request = currentRequest[A],
                    saUser = saUserType
                  )
                )
            })

          val result = controller.onPageLoad(currentRequest)

          status(result) mustBe OK
          verify(mockFeatureFlagService, times(2)).get(any())
          verify(mockAddressJourneyCachingHelper, times(1)).enforceDisplayAddressPageVisited(arg.capture)(any())
          verify(mockTaxCreditsService, times(1)).isAddressChangeInPTA(any())(any())
          val argCaptorValue: Result = arg.getValue
          argCaptorValue.header.status mustBe OK
          contentAsString(Future.successful(argCaptorValue)) must include("Do you get tax credits?")

        }
    }

    "Tax-credit-broker call is not used and the question ask to the user" must {
      "display do you get tax credits page if the user has tax credits" in {
        def saUserType: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(Fixtures.saUtr)
        val arg                                = ArgumentCaptor.forClass(classOf[Result])

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = false)))
        when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

        when(mockTaxCreditsService.isAddressChangeInPTA(any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Option[Boolean]](Future.successful(Right(Some(false))))
          )

        when(mockAddressJourneyCachingHelper.enforceDisplayAddressPageVisited(any())(any()))
          .thenReturn(Future.successful(Ok("Fake Page")))

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

        val result = controller.onPageLoad(currentRequest)

        status(result) mustBe OK
        verify(mockFeatureFlagService, times(2)).get(any())
        verify(mockAddressJourneyCachingHelper, times(1)).enforceDisplayAddressPageVisited(arg.capture)(any())
        verify(mockTaxCreditsService, times(0)).isAddressChangeInPTA(any())(any())
        val argCaptorValue: Result = arg.getValue
        argCaptorValue.header.status mustBe OK
        contentAsString(Future.successful(argCaptorValue)) must include("Do you get tax credits?")
      }
    }
  }

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
