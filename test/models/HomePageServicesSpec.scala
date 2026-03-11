/*
 * Copyright 2026 HM Revenue & Customs
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

package models

import testUtils.BaseSpec

class HomePageServicesSpec extends BaseSpec {

  "HomePageServices.myServices" must {

    "return only MyService entries" in {
      val myService1 = MyService(
        title = "Service 1",
        link = "/service-1",
        hintText = "Hint 1"
      )

      val myService2 = MyService(
        title = "Service 2",
        link = "/service-2",
        hintText = "Hint 2"
      )

      val otherService = OtherService(
        title = "Other service 1",
        link = "/other-service-1"
      )

      val result = HomePageServices(
        Seq(myService1, otherService, myService2)
      )

      result.myServices mustBe Seq(myService1, myService2)
    }

    "return an empty sequence when there are no MyService entries" in {
      val otherService1 = OtherService(
        title = "Other service 1",
        link = "/other-service-1"
      )

      val otherService2 = OtherService(
        title = "Other service 2",
        link = "/other-service-2"
      )

      val result = HomePageServices(Seq(otherService1, otherService2))

      result.myServices mustBe Seq.empty
    }

    "return an empty sequence when services is empty" in {
      val result = HomePageServices(Seq.empty)

      result.myServices mustBe Seq.empty
    }
  }

  "HomePageServices.otherServices" must {

    "return only OtherService entries" in {
      val myService = MyService(
        title = "Service 1",
        link = "/service-1",
        hintText = "Hint 1"
      )

      val otherService1 = OtherService(
        title = "Other service 1",
        link = "/other-service-1"
      )

      val otherService2 = OtherService(
        title = "Other service 2",
        link = "/other-service-2"
      )

      val result = HomePageServices(
        Seq(myService, otherService1, otherService2)
      )

      result.otherServices mustBe Seq(otherService1, otherService2)
    }

    "return an empty sequence when there are no OtherService entries" in {
      val myService1 = MyService(
        title = "Service 1",
        link = "/service-1",
        hintText = "Hint 1"
      )

      val myService2 = MyService(
        title = "Service 2",
        link = "/service-2",
        hintText = "Hint 2"
      )

      val result = HomePageServices(Seq(myService1, myService2))

      result.otherServices mustBe Seq.empty
    }

    "return an empty sequence when services is empty" in {
      val result = HomePageServices(Seq.empty)

      result.otherServices mustBe Seq.empty
    }
  }
}
