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

import config.ConfigDecorator
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.admin.AddressChangeAllowedToggle
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.InternalServerErrorView

import scala.concurrent.Future

class AddressControllerSpec extends AddressBaseSpec {
  private object Controller
      extends AddressController(
        app.injector.instanceOf[AuthJourney],
        cc,
        displayAddressInterstitialView,
        mockFeatureFlagService,
        internalServerErrorView
      )

  private lazy val controller: AddressController = Controller

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

    "show the address interstitial view page" when {

      "a nino cannot be found in the request" in {

        when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(nino = None, request = FakeRequest().asInstanceOf[Request[A]])

        val result = controller.addressJourneyEnforcer { _ => _ =>
          Future(Ok("Success"))
        }(userRequest)

        status(result) mustBe OK
        contentAsString(result) must include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))
      }

      "person details cannot be found in the request" in {

        when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

        implicit def userRequest[A]: UserRequest[A] =
          buildUserRequest(personDetails = None, request = FakeRequest().asInstanceOf[Request[A]])

        val result = controller.addressJourneyEnforcer { _ => _ =>
          Future(Ok("Success"))
        }

        status(result) mustBe OK
        contentAsString(result) must include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))

      }
    }

    "show the InternalServerErrorView" when {

      "the AddressChangeAllowedToggle is set to false" in {
        val internalServerErrorView: InternalServerErrorView = app.injector.instanceOf[InternalServerErrorView]
        val configDecorator: ConfigDecorator                 = app.injector.instanceOf[ConfigDecorator]
        when(mockFeatureFlagService.get(AddressChangeAllowedToggle))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = false)))

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

        val expectedContent = "Success"

        val result = controller.addressJourneyEnforcer { _ => _ =>
          Future(Ok(expectedContent))
        }(userRequest)

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsString(result) mustBe internalServerErrorView.apply()(userRequest, configDecorator, messages).body
      }
    }
  }
}
