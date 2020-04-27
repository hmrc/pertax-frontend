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

package services

import models.addresslookup.{Address, AddressLookupErrorResponse, AddressLookupSuccessResponse, AddressLookupUnexpectedResponse, AddressRecord, Country, RecordSet}
import models.{AnyOtherMove, MovedFromScotland, MovedToScotland}
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import uk.gov.hmrc.http.HttpResponse
import util.BaseSpec

import scala.concurrent.{ExecutionContext, Future}

class AddressMovedServiceSpec extends BaseSpec with MockitoSugar {

  implicit val executionContext = injected[ExecutionContext]
  val addressLookupService = mock[AddressLookupService]
  val fromPostcode = "AA1 1AA"
  val toPostcode = "AA1 2AA"

  val englandRecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(Seq.empty, None, None, fromPostcode, Country("eng", "England"), Some("GB-ENG")),
        "en")))
  val scotlandRecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(Seq.empty, None, None, fromPostcode, Country("blah", "blah"), Some("GB-SCT")),
        "en")))

  val service = new AddressMovedService(addressLookupService)

  "moved" should {
    "be AnyOtherMove" when {
      "the AddressLookUpService gives an AddressLookupUnexpectedResponse" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupUnexpectedResponse(HttpResponse(BAD_REQUEST))))
        await(service.moved(fromPostcode, fromPostcode)) shouldBe AnyOtherMove
      }

      "the AddressLookUpService gives an AddressLookupErrorResponse" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupErrorResponse(new RuntimeException(":("))))
        await(service.moved(fromPostcode, fromPostcode)) shouldBe AnyOtherMove
      }

      "the post code is the same" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))
        await(service.moved(fromPostcode, fromPostcode)) shouldBe AnyOtherMove
      }

      "there are no addresses returned for the previous address" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(RecordSet(Seq.empty))))
        await(service.moved(fromPostcode, fromPostcode)) shouldBe AnyOtherMove
      }

      "there are no addresses returned for the new address" in {

        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))
        when(addressLookupService.lookup(toPostcode))
          .thenReturn(Future.successful(AddressLookupSuccessResponse(RecordSet(Seq.empty))))

        await(service.moved(fromPostcode, toPostcode)) shouldBe AnyOtherMove
      }

      "there is no postcode for the moving to address" in {
        await(service.moved(fromPostcode, "")) shouldBe AnyOtherMove
      }

      "there is no postcode for the moving from address" in {
        await(service.moved("", toPostcode)) shouldBe AnyOtherMove
      }

      "there is no postcode for both the moving to and moving from address" in {
        await(service.moved("", "")) shouldBe AnyOtherMove
      }
    }

    "be MovedToScotland when they have moved to Scotland" in {
      when(addressLookupService.lookup(fromPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))
      when(addressLookupService.lookup(toPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(scotlandRecordSet)))

      await(service.moved(fromPostcode, toPostcode)) shouldBe MovedToScotland
    }

    "be MovedFromScotland when they have moved from Scotland" in {
      when(addressLookupService.lookup(fromPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(scotlandRecordSet)))
      when(addressLookupService.lookup(toPostcode))
        .thenReturn(Future.successful(AddressLookupSuccessResponse(englandRecordSet)))

      await(service.moved(fromPostcode, toPostcode)) shouldBe MovedFromScotland
    }
  }
}
