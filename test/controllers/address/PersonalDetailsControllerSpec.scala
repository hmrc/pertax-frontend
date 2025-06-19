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
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.admin.RlsInterruptToggle
import models.{AddressesLock, NonFilerSelfAssessmentUser, PersonDetails}
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.Application
import play.api.http.Status._
import play.api.inject.bind
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, redirectLocation, status}
import services.CitizenDetailsService
import testUtils.BaseSpec
import testUtils.Fixtures.buildPersonDetailsWithPersonalAndCorrespondenceAddress
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.{HeaderNames, UpstreamErrorResponse}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import scala.concurrent.{ExecutionContext, Future}

class PersonalDetailsControllerSpec extends BaseSpec {
  private lazy val controller: PersonalDetailsController = app.injector.instanceOf[PersonalDetailsController]

  val personDetails: PersonDetails                     = buildPersonDetailsWithPersonalAndCorrespondenceAddress
  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]

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
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockCitizenDetailsService)

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, isEnabled = true)))
  }

  "Calling redirectToYourProfile" must {
    "redirect to the profile-and-settings page" in {

      val result: Future[Result] = controller.redirectToYourProfile()(FakeRequest())

      status(result) mustBe MOVED_PERMANENTLY
      redirectLocation(result) mustBe Some("/personal-account/profile-and-settings")
    }
  }

  "Calling onPageLoad" must {
    "redirect to the rls interrupt page" when {
      "main address has an rls status with true" in {
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, PersonDetails](
            Future.successful(Right(personDetails.copy(address = personDetails.address.map(_.copy(isRls = true)))))
          )
        )
        when(mockEditAddressLockRepository.getAddressesLock(any())(any())).thenReturn(
          Future.successful(AddressesLock(main = false, postal = false))
        )

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }

      "postal address has an rls status with true" in {
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, PersonDetails](
            Future.successful(
              Right(personDetails.copy(address = personDetails.address.map(_.copy(isRls = true))))
            )
          )
        )
        when(mockEditAddressLockRepository.getAddressesLock(any())(any())).thenReturn(
          Future.successful(AddressesLock(main = false, postal = false))
        )

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }

      "main and postal address has an rls status with true" in {
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, PersonDetails](
            Future.successful(
              Right(personDetails.copy(address = personDetails.address.map(_.copy(isRls = true))))
            )
          )
        )
        when(mockEditAddressLockRepository.getAddressesLock(any())(any())).thenReturn(
          Future.successful(AddressesLock(main = false, postal = false))
        )

        val result: Future[Result] = controller.onPageLoad(FakeRequest())

        status(result) mustBe SEE_OTHER
        redirectLocation(result) mustBe Some("/personal-account/update-your-address")
      }
    }

    "show the your profile page" when {
      "no address has an rls status with true" in {
        when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
          EitherT[Future, UpstreamErrorResponse, PersonDetails](
            Future.successful(
              Right(personDetails)
            )
          )
        )
        when(mockEditAddressLockRepository.getAddressesLock(any())(any())).thenReturn(
          Future.successful(AddressesLock(main = false, postal = false))
        )
        when(mockEditAddressLockRepository.get(any())).thenReturn(
          Future.successful(List.empty)
        )

        val result: Future[Result] = controller.onPageLoad(
          FakeRequest().withHeaders(
            (HeaderNames.xSessionId, "test-session-id")
          )
        )

        status(result) mustBe OK
      }
    }
  }

}
