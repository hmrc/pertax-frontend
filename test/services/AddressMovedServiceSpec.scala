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

package services

import cats.data.EitherT
import connectors.AddressLookupConnector
import models.addresslookup.{Address, AddressRecord, Country, RecordSet}
import models.{AnyOtherMove, MovedFromScotland, MovedToScotland}
import org.mockito.Mockito.{reset, verifyNoInteractions, when}
import org.scalatest.BeforeAndAfterEach
import play.api.http.Status.*
import testUtils.BaseSpec
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.{ExecutionContext, Future}

class AddressMovedServiceSpec extends BaseSpec with BeforeAndAfterEach {

  implicit val executionContext: ExecutionContext = inject[ExecutionContext]

  val addressLookupService: AddressLookupConnector = mock[AddressLookupConnector]

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(addressLookupService)
  }

  val fromPostcode = "AA1 1AA"
  val toPostcode   = "AA1 2AA"

  val englandRecordSet: RecordSet = RecordSet(
    Seq(
      AddressRecord(
        "some id",
        Address(List.empty, None, None, fromPostcode, Some(Country("GB-ENG", "England")), Country("eng", "England")),
        "en"
      )
    )
  )

  val scotlandRecordSet: RecordSet = RecordSet(
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
      "the post code is the same" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(
            EitherT(Future.successful(Right(englandRecordSet)))
          )

        service.moved(fromPostcode, fromPostcode, false).futureValue mustBe AnyOtherMove
      }

      "there are no addresses returned for the previous address" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(
            EitherT(Future.successful(Right(RecordSet(Seq.empty[AddressRecord]))))
          )

        service.moved(fromPostcode, fromPostcode, false).futureValue mustBe AnyOtherMove
      }

      "there are no addresses returned for the new address" in {
        when(addressLookupService.lookup(fromPostcode))
          .thenReturn(
            EitherT(Future.successful(Right(englandRecordSet)))
          )
        when(addressLookupService.lookup(toPostcode))
          .thenReturn(
            EitherT(Future.successful(Right(RecordSet(Seq.empty[AddressRecord]))))
          )

        service.moved(fromPostcode, toPostcode, false).futureValue mustBe AnyOtherMove
      }

      "there is no postcode for the moving to address" in {
        service.moved(fromPostcode, "", false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      "there is no postcode for the moving from address" in {
        service.moved("", toPostcode, false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      "there is no postcode for both the moving to and moving from address" in {
        service.moved("", "", false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      "the moving to postcode is only whitespace" in {
        service.moved(fromPostcode, "   ", false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      "the moving from postcode is only whitespace" in {
        service.moved("   ", toPostcode, false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      "the moving from postcode is not a valid UK postcode" in {
        service.moved("NOT-A-POSTCODE", toPostcode, false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      "the moving to postcode is not a valid UK postcode" in {
        service.moved(fromPostcode, "NOT-A-POSTCODE", false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      "both postcodes are not valid UK postcodes" in {
        service.moved("INVALID1", "INVALID2", false).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }

      List(
        TOO_MANY_REQUESTS,
        INTERNAL_SERVER_ERROR,
        BAD_GATEWAY,
        SERVICE_UNAVAILABLE,
        IM_A_TEAPOT,
        NOT_FOUND,
        BAD_REQUEST,
        UNPROCESSABLE_ENTITY
      ).foreach { httpResponse =>
        s"the AddressLookUpService gives a Left containing $httpResponse" in {
          when(addressLookupService.lookup(fromPostcode))
            .thenReturn(
              EitherT(Future.successful(Left(UpstreamErrorResponse("", httpResponse))))
            )

          service.moved(fromPostcode, fromPostcode, false).futureValue mustBe AnyOtherMove
        }
      }

      "when p85enabled is true" in {
        service.moved(fromPostcode, toPostcode, true).futureValue mustBe AnyOtherMove
        verifyNoInteractions(addressLookupService)
      }
    }

    "be MovedToScotland when they have moved to Scotland" in {
      when(addressLookupService.lookup(fromPostcode))
        .thenReturn(
          EitherT(Future.successful(Right(englandRecordSet)))
        )
      when(addressLookupService.lookup(toPostcode))
        .thenReturn(
          EitherT(Future.successful(Right(scotlandRecordSet)))
        )

      service.moved(fromPostcode, toPostcode, false).futureValue mustBe MovedToScotland
    }

    "be MovedFromScotland when they have moved from Scotland" in {
      when(addressLookupService.lookup(fromPostcode))
        .thenReturn(
          EitherT(Future.successful(Right(scotlandRecordSet)))
        )
      when(addressLookupService.lookup(toPostcode))
        .thenReturn(
          EitherT(Future.successful(Right(englandRecordSet)))
        )

      service.moved(fromPostcode, toPostcode, false).futureValue mustBe MovedFromScotland
    }
  }
}
