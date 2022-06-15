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

package controllers

import connectors.PdfGeneratorConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithActiveTabAction, WithBreadcrumbAction}
import error.ErrorRenderer
import org.jsoup.Jsoup
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import util.UserRequestFixture.buildUserRequest
import util.{ActionBuilderFixture, BaseSpec, CitizenDetailsFixtures}
import views.html.print._

import scala.concurrent.Future

class NiLetterControllerSpec extends BaseSpec with MockitoSugar with CitizenDetailsFixtures {

  val mockPdfGeneratorConnector = mock[PdfGeneratorConnector]
  val mockAuthJourney = mock[AuthJourney]
  val mockInterstitialController = mock[InterstitialController]
  val mockHomeController = mock[HomeController]
  val mockRlsConfirmAddressController = mock[RlsController]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[PdfGeneratorConnector].toInstance(mockPdfGeneratorConnector),
      bind[HomeController].toInstance(mockHomeController),
      bind[RlsController].toInstance(mockRlsConfirmAddressController)
    )
    .configure(configValues)
    .build()

  def controller: NiLetterController =
    new NiLetterController(
      mockPdfGeneratorConnector,
      mockAuthJourney,
      injected[WithBreadcrumbAction],
      injected[MessagesControllerComponents],
      injected[ErrorRenderer],
      injected[PrintNationalInsuranceNumberView],
      injected[NiLetterPDfWrapperView],
      injected[NiLetterView],
      injected[WithActiveTabAction]
    )(
      config,
      templateRenderer,
      ec
    )

  "Calling NiLetterController.printNationalInsuranceNumber" must {

    "call printNationalInsuranceNumber should return OK when called by a high GG user" in {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      lazy val r = controller.printNationalInsuranceNumber()(FakeRequest())

      status(r) mustBe OK
    }

    "call printNationalInsuranceNumber should return OK when called by a verify user" in {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            )
          )
      })

      lazy val r = controller.printNationalInsuranceNumber()(FakeRequest())

      status(r) mustBe OK
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementById("page-title").text() mustBe "Your National Insurance letter"
      doc
        .getElementById("keep-ni-number-safe")
        .text() mustBe "Keep this number in a safe place. Do not destroy this letter."
      doc.getElementById("available-information-text-relay").text() must include(
        "Information is available in large print, audio tape and Braille formats."
      )
      doc.getElementById("available-information-text-relay").text() must include(
        "Text Relay service prefix number - 18001"
      )
      doc
        .getElementById("your-ni-number-unique")
        .text() mustBe "Your National Insurance number is unique to you and will never change. To prevent identity fraud, do not share it with anyone who does not need it."
    }
  }
}
