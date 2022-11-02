/*
 * Copyright 2022 HM Revenue & Customs
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

package services

import connectors.{AddressLookupConnector, AddressLookupErrorResponse, AddressLookupSuccessResponse, AddressLookupUnexpectedResponse}
import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import models.{AnyOtherMove, MovedFromScotland, MovedToScotland}
import org.mockito.Mockito.{mock, when}
import org.scalatest.concurrent.ScalaFutures
import play.api.http.Status._
import testUtils.BaseSpec
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.{ExecutionContext, Future}

class AddressMovedServiceSpec extends BaseSpec {

  implicit val executionContext = injected[ExecutionContext]
  val addressLookupService      = mock[AddressLookupConnector]
  val fromPostcode              = "AA1 1AA"
  val toPostcode                = "AA1 2AA"

  val englandRecordSet  = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(List.empty, None, None, fromPostcode, Some(Country("GB-ENG", "England")), Country("eng", "England")),
        "en"
      )
    )
  )
  val scotlandRecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(List.empty, None, None, fromPostcode, Some(Country("GB-SCT", "Scotland")), Country("blah", "blah")),
        "en"
      )
    )
  )

  val service = new AddressMovedService(addressLookupService)

  "moved" must {
    "be AnyOtherMove" when {
      "the AddressLookUpService gives an AddressLookupUnexpectedResponse" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupUnexpectedResponse(HttpResponse(BAD_REQUEST))))
        service.moved(fromPostcode, fromPostcode).futureValue mustBe AnyOtherMove
      }

      "the AddressLookUpService gives an AddressLookupErrorResponse" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupErrorResponse(new RuntimeException(":("))))
        service.moved(fromPostcode, fromPostcode).futureValue mustBe AnyOtherMove
      }

      "the post code is the same" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))
        service.moved(fromPostcode, fromPostcode).futureValue mustBe AnyOtherMove
      }

      "there are no addresses returned for the previous address" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(RecordSet(Seq.empty))))
        service.moved(fromPostcode, fromPostcode).futureValue mustBe AnyOtherMove
      }

      "there are no addresses returned for the new address" in {

        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))
        when(addressLookupService.lookup(toPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(RecordSet(Seq.empty))))

        service.moved(fromPostcode, toPostcode).futureValue mustBe AnyOtherMove
      }

      "there is no postcode for the moving to address" in {
        service.moved(fromPostcode, "").futureValue mustBe AnyOtherMove
      }

      "there is no postcode for the moving from address" in {
        service.moved("", toPostcode).futureValue mustBe AnyOtherMove
      }

      "there is no postcode for both the moving to and moving from address" in {
        service.moved("", "").futureValue mustBe AnyOtherMove
      }
    }

    "be MovedToScotland when they have moved to Scotland" in {
      when(addressLookupService.lookup(fromPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))
      when(addressLookupService.lookup(toPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(scotlandRecordSet)))

      service.moved(fromPostcode, toPostcode).futureValue mustBe MovedToScotland
    }

    "be MovedFromScotland when they have moved from Scotland" in {
      when(addressLookupService.lookup(fromPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(scotlandRecordSet)))
      when(addressLookupService.lookup(toPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))

      service.moved(fromPostcode, toPostcode).futureValue mustBe MovedFromScotland
    }
  }
}
