/*
 * Copyright 2019 HM Revenue & Customs
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

package controllers.auth

import controllers.auth.requests.UserRequest
import models.{AmbiguousFilerSelfAssessmentUser, NonFilerSelfAssessmentUser}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.domain.SaUtr

import scala.concurrent.Future

class EnforceAmbiguousUserActionSpec extends FreeSpec with MustMatchers with GuiceOneAppPerSuite {

  def harness[A]()(implicit request: UserRequest[A]): Future[Result] = {

    lazy val actionProvider = app.injector.instanceOf[EnforceAmbiguousUserAction]

    actionProvider.invokeBlock(
      request, { _: UserRequest[_] =>
        Future.successful(
          Ok("")
        )
      }
    )
  }

  "EnforceAmbiguousUserAction must" - {
    "when a user is ambiguous" - {

      "return the request it was passed" in {
        val refinedRequest =
          UserRequest(
            None,
            None,
            None,
            AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111")),
            "",
            ConfidenceLevel.L50,
            None,
            None,
            None,
            None,
            FakeRequest())
        val result = harness()(refinedRequest)
        status(result) mustBe OK
      }

      "when a user is not ambiguous" - {

        "redirect to the landing page" in {
          val refinedRequest =
            UserRequest(
              None,
              None,
              None,
              NonFilerSelfAssessmentUser,
              "",
              ConfidenceLevel.L50,
              None,
              None,
              None,
              None,
              FakeRequest())
          val result = harness()(refinedRequest)
          status(result) mustBe SEE_OTHER
          redirectLocation(result).get must endWith("/personal-account")
        }
      }
    }
  }
}
