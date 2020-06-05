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

import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import play.api.mvc.Request
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import util.UserRequestFixture.buildUserRequest

class AddressControllerSpec extends AddressBaseSpec {

  object SUT
      extends AddressController(
        injected[AuthJourney],
        withActiveTabAction,
        cc,
        displayAddressInterstitialView
      )

  "addressJourneyEnforcer" should {

    "complete given block" when {

      "a nino and person details are present in the request" in {

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

        val expectedContent = "Success"

        val result = SUT.addressJourneyEnforcer { _ => _ =>
          Ok(expectedContent)
        }(userRequest)

        status(result) shouldBe OK
        contentAsString(result) shouldBe expectedContent
      }
    }

    "show the address interstitial view page" when {

      "a nino cannot be found in the request" in {

        def userRequest[A]: UserRequest[A] =
          buildUserRequest(nino = None, request = FakeRequest().asInstanceOf[Request[A]])

        val result = SUT.addressJourneyEnforcer { _ => _ =>
          Ok("Success")
        }(userRequest)

        status(result) shouldBe OK
        contentAsString(result) should include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))
      }

      "person details cannot be found in the request" in {
        implicit def userRequest[A]: UserRequest[A] =
          buildUserRequest(personDetails = None, request = FakeRequest().asInstanceOf[Request[A]])

        val result = SUT.addressJourneyEnforcer { _ => _ =>
          Ok("Success")
        }

        status(result) shouldBe OK
        contentAsString(result) should include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))

      }
    }
  }

  "internalServerError" should {

    "return 500 and render the correct page" in {
      def userRequest[A]: UserRequest[A] =
        buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

      val result = SUT.internalServerError(userRequest)

      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentAsString(result) should include(messages("global.error.InternalServerError500.title"))
    }
  }
}
