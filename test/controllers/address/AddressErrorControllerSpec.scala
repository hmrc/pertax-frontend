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

package controllers.address

import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import controllers.bindable.ResidentialAddrType
import play.api.Application
import play.api.mvc.{ActionBuilder, AnyContent, BodyParser, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import testUtils.BaseSpec
import testUtils.UserRequestFixture.buildUserRequest
import play.api.inject.bind

import scala.concurrent.{ExecutionContext, Future}

class AddressErrorControllerSpec extends BaseSpec {

  class FakeAuthAction extends AuthJourney {
    override def authWithPersonalDetails: ActionBuilder[UserRequest, AnyContent] =
      new ActionBuilder[UserRequest, AnyContent] {
        override def parser: BodyParser[AnyContent] = play.api.test.Helpers.stubBodyParser()

        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
          block(buildUserRequest(request = request))

        override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
      }
  }

  override implicit lazy val app: Application         = localGuiceApplicationBuilder()
    .overrides(
      bind[AuthJourney].toInstance(new FakeAuthAction)
    )
    .build()
  private lazy val controller: AddressErrorController = app.injector.instanceOf[AddressErrorController]

  "cannotUseThisService" must {

    "display the cannot use this service page" in {
      def userRequest[A]: UserRequest[A] =
        buildUserRequest(request = FakeRequest().asInstanceOf[Request[A]])

      val result: Future[Result] = controller.cannotUseThisService(ResidentialAddrType)(userRequest)

      redirectLocation(result) mustBe None
      status(result) mustBe INTERNAL_SERVER_ERROR
      contentAsString(result) must include("You cannot use this service to update your address")
    }
  }
}
