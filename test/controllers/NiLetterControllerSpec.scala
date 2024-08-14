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

import controllers.auth.WithBreadcrumbAction
import controllers.auth.requests.UserRequest
import play.api.Application
import play.api.inject.bind
import play.api.mvc.{MessagesControllerComponents, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, BaseSpec, CitizenDetailsFixtures}

import scala.concurrent.Future

class NiLetterControllerSpec extends BaseSpec with CitizenDetailsFixtures {

  val mockInterstitialController: InterstitialController = mock[InterstitialController]
  val mockHomeController: HomeController                 = mock[HomeController]
  val mockRlsConfirmAddressController: RlsController     = mock[RlsController]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[InterstitialController].toInstance(mockInterstitialController),
      bind[HomeController].toInstance(mockHomeController),
      bind[RlsController].toInstance(mockRlsConfirmAddressController)
    )
    .configure(configValues)
    .build()

  def controller: NiLetterController =
    new NiLetterController(
      mockAuthJourney,
      inject[WithBreadcrumbAction],
      inject[MessagesControllerComponents]
    )(
      config,
      ec
    )

  "Calling NiLetterController.saveNationalInsuranceNumberAsPdf" must {
    "redirect to nino service" in {
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

    "redirect to nino service" in {

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

  }
}
