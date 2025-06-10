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

package controllers

import cats.data.EitherT
import connectors._
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.PayApiModels
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{AnyContentAsEmpty, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.time.CurrentTaxYear

import java.time.LocalDate
import scala.concurrent.Future

class PaymentsControllerSpec extends BaseSpec with CurrentTaxYear {

  override def now: () => LocalDate = () => LocalDate.now()

  lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")

  val mockPayConnector: PayApiConnector = mock[PayApiConnector]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PayApiConnector].toInstance(mockPayConnector),
      bind[AuthJourney].toInstance(mockAuthJourney)
    )
    .build()

  private lazy val controller: PaymentsController = app.injector.instanceOf[PaymentsController]

  when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
    override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
      block(
        buildUserRequest(
          request = request
        )
      )
  })

  "makePayment" must {
    "redirect to the response's nextUrl" in {
      val expectedNextUrl       = "someNextUrl"
      val createPaymentResponse = PayApiModels("someJourneyId", expectedNextUrl)

      when(mockPayConnector.createPayment(any())(any(), any()))
        .thenReturn(
          EitherT[Future, UpstreamErrorResponse, Option[PayApiModels]](
            Future.successful(Right(Some(createPaymentResponse)))
          )
        )

      val result = controller.makePayment()(FakeRequest())
      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some("someNextUrl")
    }

    List(
      BAD_REQUEST,
      NOT_FOUND,
      REQUEST_TIMEOUT,
      UNPROCESSABLE_ENTITY,
      INTERNAL_SERVER_ERROR,
      BAD_GATEWAY,
      SERVICE_UNAVAILABLE
    ).foreach { error =>
      s"redirect to a BAD_REQUEST page if createPayment fails with an $error status" in {

        when(mockPayConnector.createPayment(any())(any(), any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, Option[PayApiModels]](
              Future.successful(Left(UpstreamErrorResponse("", error)))
            )
          )

        val result = controller.makePayment()(FakeRequest())
        status(result) mustBe BAD_REQUEST
      }
    }
  }
}
