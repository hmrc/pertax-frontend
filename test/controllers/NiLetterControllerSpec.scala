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

package controllers

import config.ConfigDecorator
import connectors.PdfGeneratorConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import org.jsoup.Jsoup
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.MessagesApi
import play.api.inject._
import play.api.mvc.{ActionBuilder, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{ActionBuilderFixture, BaseSpec, CitizenDetailsFixtures}
import util.UserRequestFixture.buildUserRequest

import scala.concurrent.Future

class NiLetterControllerSpec extends BaseSpec with MockitoSugar with CitizenDetailsFixtures {

  val mockPdfGeneratorConnector = mock[PdfGeneratorConnector]
  val mockAuthJourney = mock[AuthJourney]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PdfGeneratorConnector].toInstance(mockPdfGeneratorConnector),
      bind[AuthJourney].toInstance(mockAuthJourney)
    )
    .configure(configValues)
    .build()

  def controller: NiLetterController =
    new NiLetterController(
      injected[MessagesApi],
      mockPdfGeneratorConnector,
      mockAuthJourney,
      injected[WithBreadcrumbAction])(
      mockLocalPartialRetriever,
      injected[ConfigDecorator],
      injected[TemplateRenderer]
    )

  "Calling NiLetterController.printNationalInsuranceNumber" should {

    "call printNationalInsuranceNumber should return OK when called by a high GG user" in {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      lazy val r = controller.printNationalInsuranceNumber()(FakeRequest())

      status(r) shouldBe OK
    }

    "call printNationalInsuranceNumber should return OK when called by a verify user" in {
      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "Verify"),
              confidenceLevel = ConfidenceLevel.L500,
              request = request
            ))
      })

      lazy val r = controller.printNationalInsuranceNumber()(FakeRequest())

      status(r) shouldBe OK
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementById("page-title").text() shouldBe "Your National Insurance letter"
      doc
        .getElementById("keep-ni-number-safe")
        .text() shouldBe "Keep this number in a safe place. Do not destroy this letter."
      doc.getElementById("available-information-text-relay").text() should include(
        "Information is available in large print, audio tape and Braille formats.")
      doc.getElementById("available-information-text-relay").text() should include(
        "Text Relay service prefix number - 18001")
      doc
        .getElementById("your-ni-number-unique")
        .text() shouldBe "Your National Insurance number is unique to you and will never change. To prevent identity fraud, do not share it with anyone who does not need it."
    }
  }
}
