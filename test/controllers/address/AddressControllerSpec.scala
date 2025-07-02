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
import error.ErrorRenderer
import models.PersonDetails
import models.admin.AddressChangeAllowedToggle
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import play.api.i18n.{Lang, Messages, MessagesImpl}
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.CitizenDetailsService
import testUtils.BaseSpec
import testUtils.Fixtures.buildPersonDetails
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.InternalServerErrorView

import scala.concurrent.Future

class AddressControllerSpec extends BaseSpec {
  val mockErrorRenderer: ErrorRenderer                 = mock[ErrorRenderer]
  val mockCitizenDetailsService: CitizenDetailsService = mock[CitizenDetailsService]
  val mockAppConfigDecorator: ConfigDecorator          = mock[ConfigDecorator]
  val internalServerErrorView: InternalServerErrorView = app.injector.instanceOf[InternalServerErrorView]
  implicit lazy val messages: Messages                 = MessagesImpl(Lang("en"), messagesApi)

  private object Controller
      extends AddressController(
        app.injector.instanceOf[AuthJourney],
        mcc,
        mockFeatureFlagService,
        mockErrorRenderer,
        mockCitizenDetailsService,
        internalServerErrorView
      )(mockAppConfigDecorator, implicitly)

  private lazy val controller: AddressController = Controller

  override def beforeEach(): Unit = {

    super.beforeEach()
    reset(mockCitizenDetailsService)
    reset(mockErrorRenderer)

    when(mockErrorRenderer.error(any())(any(), any())).thenReturn(InternalServerError("error"))

    when(mockCitizenDetailsService.personDetails(any())(any(), any(), any())).thenReturn(
      EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](
        Future.successful(Right(Some(buildPersonDetails)))
      )
    )
  }

  "addressJourneyEnforcer" must {

    "complete given block" when {

      "a nino and person details are present in the request" in {

        when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

        val expectedContent = "Success"

        val result = controller.addressJourneyEnforcer { _ => _ =>
          Future(Ok(expectedContent))
        }(userRequest)

        status(result) mustBe OK
        contentAsString(result) mustBe expectedContent
      }
    }

    "show the InternalServerErrorView" when {

      "the AddressChangeAllowedToggle is set to false" in {
        when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = false)))

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

        val expectedContent = "Success"

        val result = controller.addressJourneyEnforcer { _ => _ =>
          Future(Ok(expectedContent))
        }(userRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe internalServerErrorView
          .apply()(userRequest, mockAppConfigDecorator, messages)
          .body
      }
    }
  }

  "show the InternalServerErrorView" when {

    "the citizenDetailsService returns Right(None)" in {
      when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
        .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any()))
        .thenReturn(EitherT[Future, UpstreamErrorResponse, Option[PersonDetails]](Future.successful(Right(None))))

      def userRequest[A]: UserRequest[A] =
        buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

      val result = controller.addressJourneyEnforcer { _ => _ =>
        Future(Ok("Success"))
      }(userRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "the citizenDetailsService returns Left" in {
      when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
        .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

      when(mockCitizenDetailsService.personDetails(any())(any(), any(), any()))
        .thenReturn(EitherT.leftT[Future, Option[PersonDetails]](UpstreamErrorResponse("error", 500)))

      def userRequest[A]: UserRequest[A] =
        buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

      val result = controller.addressJourneyEnforcer { _ => _ =>
        Future(Ok("Success"))
      }(userRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }
}
