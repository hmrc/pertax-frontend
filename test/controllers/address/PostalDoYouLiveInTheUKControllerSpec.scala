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
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.Origin
import models.admin.AddressChangeAllowedToggle
import models.{NonFilerSelfAssessmentUser, PersonDetails, UserAnswers}
import models.dto.AddressPageVisitedDto
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import repositories.JourneyCacheRepository
import routePages.HasAddressAlreadyVisitedPage
import services.CitizenDetailsService
import testUtils.BaseSpec
import testUtils.Fixtures.buildPersonDetailsWithPersonalAndCorrespondenceAddress
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.{ExecutionContext, Future}

class PostalDoYouLiveInTheUKControllerSpec extends BaseSpec {

  val personDetails: PersonDetails                       = buildPersonDetailsWithPersonalAndCorrespondenceAddress
  val mockCitizenDetailsService: CitizenDetailsService   = mock[CitizenDetailsService]
  val mockJourneyCacheRepository: JourneyCacheRepository = mock[JourneyCacheRepository]
  val mockConfigDecorator: ConfigDecorator               = mock[ConfigDecorator]

  class FakeAuthAction extends AuthJourney {
    override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
      new ActionBuilder[UserRequest, AnyContent] {
        override def parser: BodyParser[AnyContent] = play.api.test.Helpers.stubBodyParser()

        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(saUser = NonFilerSelfAssessmentUser, request = request))

        override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      }
  }

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(new FakeAuthAction),
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService),
      bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository),
      bind[ConfigDecorator].toInstance(mockConfigDecorator)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsService)
    reset(mockJourneyCacheRepository)
    reset(mockConfigDecorator)

    when(mockConfigDecorator.personalAccount).thenReturn("/")
    when(mockConfigDecorator.defaultOrigin).thenReturn(Origin("PERTAX"))
    when(mockConfigDecorator.getFeedbackSurveyUrl(any())).thenReturn("/test")

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
      .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))
  }

  private lazy val controller: PostalDoYouLiveInTheUKController =
    app.injector.instanceOf[PostalDoYouLiveInTheUKController]

  "onPageLoad" must {
    "return OK if there is an entry in the cache to say the user previously visited the 'personal details' page" in {
      def currentRequest[A]: Request[A] =
        FakeRequest("GET", "/test").asInstanceOf[Request[A]]

      def userAnswersToReturn: UserAnswers = UserAnswers
        .empty("id")
        .setOrException(HasAddressAlreadyVisitedPage, AddressPageVisitedDto(true))

      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(userAnswersToReturn))
      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, PersonDetails](
          Future.successful(Right(personDetails))
        )
      )

      val result: Future[Result] = controller.onPageLoad(currentRequest)

      status(result) mustBe OK
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }

    "redirect back to the start of the journey if there is no entry in the cache to say the user previously visited the 'personal details' page" in {
      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, PersonDetails](
          Future.successful(Right(personDetails))
        )
      )
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(UserAnswers.empty))

      val result: Future[Result] = controller.onPageLoad(FakeRequest())

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
      verify(mockJourneyCacheRepository, times(1)).get(any())
    }
  }

  "onSubmit" must {

    "redirect to postcode lookup page when supplied with value of a UK country" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "england")
          .asInstanceOf[Request[A]]

      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(UserAnswers.empty))
      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, PersonDetails](
          Future.successful(Right(personDetails))
        )
      )

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/find-address")
    }

    "redirect to enter international address page when supplied with value outsideUK" in {

      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "outsideUK")
          .asInstanceOf[Request[A]]

      when(mockConfigDecorator.updateInternationalAddressInPta).thenReturn(true)
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(UserAnswers.empty))
      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, PersonDetails](
          Future.successful(Right(personDetails))
        )
      )

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/enter-international-address")
    }

    "redirect to 'cannot use this service' when service configured to prevent updating International Addresses" in {
      def currentRequest[A]: Request[A] =
        FakeRequest("POST", "")
          .withFormUrlEncodedBody("internationalAddressChoice" -> "outsideUK")
          .asInstanceOf[Request[A]]

      when(mockConfigDecorator.updateInternationalAddressInPta).thenReturn(false)
      when(mockJourneyCacheRepository.set(any[UserAnswers])).thenReturn(Future.successful((): Unit))
      when(mockJourneyCacheRepository.get(any[HeaderCarrier])).thenReturn(Future.successful(UserAnswers.empty))
      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, PersonDetails](
          Future.successful(Right(personDetails))
        )
      )

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("/personal-account/your-address/postal/cannot-use-the-service")
    }

    "return a bad request when supplied no value" in {

      def currentRequest[A]: Request[A] = FakeRequest("POST", "").asInstanceOf[Request[A]]

      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, PersonDetails](
          Future.successful(Right(personDetails))
        )
      )

      val result: Future[Result] = controller.onSubmit(currentRequest)

      status(result) mustBe BAD_REQUEST
    }
  }
}
