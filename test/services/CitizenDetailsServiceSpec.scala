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

import config.ConfigDecorator
import connectors.{CitizenDetailsConnector, PersonDetailsErrorResponse, PersonDetailsHiddenResponse, PersonDetailsNotFoundResponse, PersonDetailsSuccessResponse, PersonDetailsUnexpectedResponse}
import controllers.auth.requests.UserRequest
import controllers.bindable.{InvalidAddresses, ValidAddressesBothInterrupt, ValidAddressesCorrespondenceInterrupt, ValidAddressesNoInterrupt, ValidAddressesResidentialInterrupt}
import models.{Address, Person, PersonDetails}
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.SEE_OTHER
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import util.Fixtures
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class CitizenDetailsServiceSpec
    extends AnyFreeSpec with Matchers with MockitoSugar with ScalaFutures with GuiceOneAppPerSuite {

  val citizenDetailsConnector = mock[CitizenDetailsConnector]
  val aDifferentNinoToAuth = Nino(new Generator(new Random()).nextNino.nino)
  val authNino = Fixtures.fakeNino

  val personDetails = PersonDetails(
    Person(
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      None,
      Some(aDifferentNinoToAuth)
    ),
    None,
    None
  )

  implicit val request: UserRequest[_] = buildUserRequest(request = FakeRequest())
  implicit lazy val hc: HeaderCarrier = HeaderCarrier()
  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  "getNino" - {
    "the feature toggle getNinoFromCID is false" - {
      val configDecorator = mock[ConfigDecorator]
      when(configDecorator.getNinoFromCID).thenReturn(false)

      "return the NINO from the request (auth)" in {
        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(request = FakeRequest())

        val result = service.getNino
        val authNino = request.nino
        result.futureValue mustBe authNino
      }
    }

    "the feature toggle getNinoFromCID is true" - {
      val configDecorator = mock[ConfigDecorator]
      when(configDecorator.getNinoFromCID).thenReturn(true)

      "return the NINO from citizen details" in {
        when(citizenDetailsConnector.personDetails(meq(authNino))(any()))
          .thenReturn(Future.successful(PersonDetailsSuccessResponse(personDetails)))

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        val result = service.getNino
        result.futureValue mustBe Some(aDifferentNinoToAuth)
      }

      "return NONE if there is no auth NINO" in {
        when(citizenDetailsConnector.personDetails(meq(authNino))(any()))
          .thenReturn(Future.successful(PersonDetailsSuccessResponse(personDetails)))

        implicit val request: UserRequest[_] = buildUserRequest(nino = None, request = FakeRequest())

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        val result = service.getNino
        result.futureValue mustBe None
      }

      Seq(
        PersonDetailsNotFoundResponse,
        PersonDetailsHiddenResponse,
        PersonDetailsUnexpectedResponse(HttpResponse(SEE_OTHER)),
        PersonDetailsErrorResponse(new RuntimeException("Any"))
      ) map { response =>
        s"return NONE when citizen details responds with $response" in {
          when(citizenDetailsConnector.personDetails(meq(authNino))(any()))
            .thenReturn(Future.successful(response))

          val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

          implicit val request: UserRequest[_] = buildUserRequest(request = FakeRequest())

          val result = service.getNino
          result.futureValue mustBe None
        }
      }
    }
  }

  //  sealed trait AddressStatus
  //  case object ValidAddressesNoInterrupt extends AddressStatus
  //  case object ValidAddressesBothInterrupt extends AddressStatus
  //  case object ValidAddressesResidentialInterrupt extends AddressStatus
  //  case object ValidAddressesCorrespondanceInterrupt extends AddressStatus
  //  case object InvalidAddress extends AddressStatus

  "getAddressStatusFromPersonalDetails" - {
    "the feature toggle getAddressStatusFromCID is true" - {
      val configDecorator = mock[ConfigDecorator]
      when(configDecorator.getAddressStatusFromCID).thenReturn(true)

      def addressBuildFromTypeAndStatus(addrType: String, status: Int) = Address(
        Some("testLine1"),
        Some("testLine1"),
        None,
        None,
        None,
        Some("testPostcode"),
        Some("testCountry"),
        Some(new LocalDate(2015, 3, 15)),
        None,
        Some(addrType),
        Some(status)
      )

      "return ValidAddressesNoInterrupt when only residential status exists and it's a 0" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(personDetails.copy(address = Some(addressBuildFromTypeAndStatus("Residential", 0))))
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesNoInterrupt
      }
      "return ValidAddressesNoInterrupt when only correspondence status exists and it's a 0" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails =
            Some(personDetails.copy(correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 0))))
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesNoInterrupt
      }
      "return ValidAddressesNoInterrupt when both residential and correspondence address status exists and are both 0" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(
            personDetails.copy(
              address = Some(addressBuildFromTypeAndStatus("Residential", 0)),
              correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 0))
            )
          )
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesNoInterrupt
      }
      "return ValidAddressesResidentialInterrupt when residential address status is 1 and there's no correspondence address" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(personDetails.copy(address = Some(addressBuildFromTypeAndStatus("Residential", 1))))
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesResidentialInterrupt
      }
      "return ValidAddressesResidentialInterrupt when residential address status is 1 and correspondence address status is 0" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(
            personDetails.copy(
              address = Some(addressBuildFromTypeAndStatus("Residential", 1)),
              correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 0))
            )
          )
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesResidentialInterrupt
      }
      "return ValidAddressesCorrespondenceInterrupt when correspondence address status is 1 and there's no residential address" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails =
            Some(personDetails.copy(correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 1))))
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesCorrespondenceInterrupt
      }
      "return ValidAddressesCorrespondenceInterrupt when residential address status is 0 and correspondence address status is 1" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(
            personDetails.copy(
              address = Some(addressBuildFromTypeAndStatus("Residential", 0)),
              correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 1))
            )
          )
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesCorrespondenceInterrupt
      }

      "return ValidAddressesBothInterrupt if no addresses exists" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(request = FakeRequest())

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesBothInterrupt
      }

      "return ValidAddressesBothInterrupt when residential address status is 1 and correspondence address status is 1" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(
            personDetails.copy(
              address = Some(addressBuildFromTypeAndStatus("Residential", 1)),
              correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 1))
            )
          )
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe ValidAddressesBothInterrupt
      }
      "return InvalidAddresses when residential address status is 2 and there's no correspondence address" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(
            personDetails.copy(address = Some(addressBuildFromTypeAndStatus("Residential", 2)))
          )
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe InvalidAddresses
      }
      "return InvalidAddresses when correspondence address status is 2 and there's no residential address" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(
            personDetails
              .copy(address = None, correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 2)))
          )
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe InvalidAddresses
      }
      "return InvalidAddresses when residential address status is 2 and correspondence status is 2" in {

        val service = new CitizenDetailsService(configDecorator, citizenDetailsConnector)

        implicit val request: UserRequest[_] = buildUserRequest(
          request = FakeRequest(),
          personDetails = Some(
            personDetails.copy(
              address = Some(addressBuildFromTypeAndStatus("Residential", 2)),
              correspondenceAddress = Some(addressBuildFromTypeAndStatus("Correspondence", 2))
            )
          )
        )

        val result = service.getAddressStatusFromPersonalDetails
        result.futureValue mustBe InvalidAddresses
      }
    }
  }
}
