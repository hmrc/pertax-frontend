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
import models.PersonDetails
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import services.LocalSessionCache
import util.Fixtures
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.Future

class AddressBaseControllerSpec extends AddressSpecHelper {

  object controllerUnderTest
      extends AddressBaseController(
        injected[LocalSessionCache],
        injected[AuthJourney],
        withActiveTabAction,
        mcc
      )

  val fakePersonDetails: PersonDetails = Fixtures.buildPersonDetails

  "addressJourneyEnforcer" should {

    "render the address interstitial page" when {

      "a nino is not present in the request" in {

        implicit val userRequest = buildUserRequest(
          request = FakeRequest(),
          nino = None,
          personDetails = Some(fakePersonDetails)
        )

        val result = controllerUnderTest.addressJourneyEnforcer(_ => _ => Future.successful(Ok("Block completed")))

        status(result) shouldBe OK
        contentAsString(result) should include(
          "You can see this part of your account if you complete some additional security steps.")
      }

      "person details are not present in the request" in {

        implicit val userRequest = buildUserRequest(
          request = FakeRequest(),
          personDetails = None
        )

        val result = controllerUnderTest.addressJourneyEnforcer(_ => _ => Future.successful(Ok("Block completed")))

        contentAsString(result) should include(
          "You can see this part of your account if you complete some additional security steps.")
      }
    }

    "complete the given block" when {

      "a nino and personal details are present in the request" in {

        implicit val userRequest = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(fakePersonDetails)
        )

        val content = "Block completed"

        val result = controllerUnderTest.addressJourneyEnforcer(_ => _ => Future.successful(Ok(content)))

        status(result) shouldBe OK
        contentAsString(result) should include(content)
      }
    }
  }
}
