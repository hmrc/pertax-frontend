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

package services

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{Person, PersonDetails}
import org.mockito.ArgumentMatchers.{any, eq => meq}
import org.mockito.Mockito._
import play.api.http.Status.SEE_OTHER
import play.api.test.FakeRequest
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HttpResponse
import util.UserRequestFixture.buildUserRequest
import util.{BaseSpec, Fixtures}

import scala.concurrent.Future
import scala.util.Random

class NinoDisplayServiceSpec extends BaseSpec {

  val citizenDetailsService = mock[CitizenDetailsService]
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

  "getNino" must {
    "the feature toggle getNinoFromCID is false" in {
      val configDecorator = mock[ConfigDecorator]
      when(configDecorator.getNinoFromCID).thenReturn(false)

      "return the NINO from the request (auth)" in {
        val service = new NinoDisplayService(configDecorator, citizenDetailsService)

        implicit val request: UserRequest[_] = buildUserRequest(request = FakeRequest())

        val result = service.getNino
        val authNino = request.nino
        result.futureValue mustBe authNino
      }
    }

    "the feature toggle getNinoFromCID is true" in {
      val configDecorator = mock[ConfigDecorator]
      when(configDecorator.getNinoFromCID).thenReturn(true)

      "return the NINO from citizen details" in {
        when(citizenDetailsService.personDetails(meq(authNino))(any()))
          .thenReturn(Future.successful(PersonDetailsSuccessResponse(personDetails)))

        val service = new NinoDisplayService(configDecorator, citizenDetailsService)

        val result = service.getNino
        result.futureValue mustBe Some(aDifferentNinoToAuth)
      }

      "return NONE if there is no auth NINO" in {
        when(citizenDetailsService.personDetails(meq(authNino))(any()))
          .thenReturn(Future.successful(PersonDetailsSuccessResponse(personDetails)))

        implicit val request: UserRequest[_] = buildUserRequest(nino = None, request = FakeRequest())

        val service = new NinoDisplayService(configDecorator, citizenDetailsService)

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
          when(citizenDetailsService.personDetails(meq(authNino))(any()))
            .thenReturn(Future.successful(response))

          val service = new NinoDisplayService(configDecorator, citizenDetailsService)

          implicit val request: UserRequest[_] = buildUserRequest(request = FakeRequest())

          val result = service.getNino
          result.futureValue mustBe None
        }
      }
    }
  }
}
