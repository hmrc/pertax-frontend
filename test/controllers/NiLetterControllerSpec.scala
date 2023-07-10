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

import connectors.PdfGeneratorConnector
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, WithBreadcrumbAction}
import error.ErrorRenderer
import models.admin.AppleSaveAndViewNIToggle
import org.jsoup.Jsoup
import org.mockito.ArgumentMatchers
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec, CitizenDetailsFixtures}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.print._

import scala.concurrent.Future

class NiLetterControllerSpec extends BaseSpec with CitizenDetailsFixtures {

  val mockPdfGeneratorConnector: PdfGeneratorConnector   = mock[PdfGeneratorConnector]
  val mockAuthJourney: AuthJourney                       = mock[AuthJourney]
  val mockInterstitialController: InterstitialController = mock[InterstitialController]
  val mockHomeController: HomeController                 = mock[HomeController]
  val mockRlsConfirmAddressController: RlsController     = mock[RlsController]
  val mockFeatureFlagService: FeatureFlagService         = mock[FeatureFlagService]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[PdfGeneratorConnector].toInstance(mockPdfGeneratorConnector),
      bind[HomeController].toInstance(mockHomeController),
      bind[RlsController].toInstance(mockRlsConfirmAddressController),
      bind[FeatureFlagService].toInstance(mockFeatureFlagService)
    )
    .configure(configValues)
    .build()

  def controller: NiLetterController =
    new NiLetterController(
      mockPdfGeneratorConnector,
      mockAuthJourney,
      injected[WithBreadcrumbAction],
      mockFeatureFlagService,
      injected[MessagesControllerComponents],
      injected[ErrorRenderer],
      injected[PrintNationalInsuranceNumberView],
      injected[NiLetterPDfWrapperView],
      injected[NiLetterView]
    )(
      config,
      ec
    )

  "Calling NiLetterController.saveNationalInsuranceNumberAsPdf" must {

    "redirect to nino service when feature flag is true" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AppleSaveAndViewNIToggle)))
        .thenReturn(Future.successful(FeatureFlag(AppleSaveAndViewNIToggle, true)))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      lazy val r = controller.saveNationalInsuranceNumberAsPdf()(FakeRequest())

      status(r) mustBe MOVED_PERMANENTLY
      redirectLocation(r) mustBe Some("http://localhost:9019/save-your-national-insurance-number")
    }
  }

  "Calling NiLetterController.printNationalInsuranceNumber" must {

    "redirect to nino service when feature flag is true" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AppleSaveAndViewNIToggle)))
        .thenReturn(Future.successful(FeatureFlag(AppleSaveAndViewNIToggle, true)))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(request = request)
          )
      })

      lazy val r = controller.printNationalInsuranceNumber()(FakeRequest())

      status(r) mustBe MOVED_PERMANENTLY
      redirectLocation(r) mustBe Some("http://localhost:9019/save-your-national-insurance-number")
    }

    "call printNationalInsuranceNumber should return OK when called by a high GG user" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AppleSaveAndViewNIToggle)))
        .thenReturn(Future.successful(FeatureFlag(AppleSaveAndViewNIToggle, false)))

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
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AppleSaveAndViewNIToggle)))
        .thenReturn(Future.successful(FeatureFlag(AppleSaveAndViewNIToggle, false)))

      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(
            buildUserRequest(
              credentials = Credentials("", "GovernmentGateway"),
              confidenceLevel = ConfidenceLevel.L200,
              request = request
            )
          )
      })

      lazy val r = controller.printNationalInsuranceNumber()(FakeRequest())

      status(r) mustBe OK
      val doc = Jsoup.parse(contentAsString(r))
      doc.getElementsByClass("govuk-heading-xl").text() mustBe "Your National Insurance letter"
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
