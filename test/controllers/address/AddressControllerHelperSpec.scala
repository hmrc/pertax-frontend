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

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction}
import org.mockito.Mockito.when
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.mvc.Results._
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures.buildFakeAddress
import util.UserRequestFixture.buildUserRequest
import util.fixtures.AddressFixture
import util.{ActionBuilderFixture, BaseSpec, LocalPartialRetriever}
import views.html.interstitial.DisplayAddressInterstitialView

import scala.concurrent.{ExecutionContext, Future}

class AddressControllerHelperSpec extends BaseSpec {

  lazy val messagesApi: MessagesApi = injected[MessagesApi]

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  object SUT
      extends AddressController(
        injected[AuthJourney],
        injected[WithActiveTabAction],
        injected[MessagesControllerComponents],
        injected[DisplayAddressInterstitialView]
      )(
        injected[LocalPartialRetriever],
        injected[ConfigDecorator],
        injected[TemplateRenderer],
        injected[ExecutionContext]
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

  "getAddress" should {

    "return Address when the option contains address" in {
      SUT.getAddress(Some(buildFakeAddress)) shouldBe buildFakeAddress
    }

    "throw an Exception when address is None" in {

      the[Exception] thrownBy {
        SUT.getAddress(None)
      } should have message "Address does not exist in the current context"
    }
  }
}
