/*
 * Copyright 2021 HM Revenue & Customs
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

import controllers.bindable.{PostalAddrType, SoleAddrType}
import models.dto._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.cache.client.CacheMap
import views.html.personaldetails._

class AddressErrorControllerSpec extends AddressBaseSpec {

  trait LocalSetup extends AddressControllerSetup {

    def sessionCacheResponse: Option[CacheMap] =
      Some(CacheMap("id", Map("addressPageVisitedDto" -> Json.toJson(AddressPageVisitedDto(true)))))

    def currentRequest[A]: Request[A] = FakeRequest("POST", "/test").asInstanceOf[Request[A]]

    def controller: AddressErrorController =
      new AddressErrorController(
        mockAuthJourney,
        addressJourneyCachingHelper,
        withActiveTabAction,
        cc,
        displayAddressInterstitialView,
        injected[CannotUseServiceView],
        injected[AddressAlreadyUpdatedView]
      )
  }

  "cannotUseThisService" should {

    "display the cannot use this service page" in new LocalSetup {
      val result = controller.cannotUseThisService(SoleAddrType)(currentRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("You cannot use this service to update your address")
    }
  }

  "showAddressAlreadyUpdated" should {

    "display the showAddressAlreadyUpdated page" in new LocalSetup {

      val result = controller.showAddressAlreadyUpdated(PostalAddrType)(currentRequest)

      status(result) shouldBe OK
      contentAsString(result) should include("Your address has already been updated")
    }
  }
}
