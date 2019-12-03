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
import models.{ActivatedOnlineFilerSelfAssessmentUser, NotEnrolledSelfAssessmentUser}
import org.scalatest.{FreeSpec, MustMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContent
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.{AuthConnector, ConfidenceLevel}
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.SaUtr

class WithGovernmentGatewayRouteActionSpec
    extends FreeSpec with MustMatchers with MockitoSugar with OneAppPerSuite with ScalaFutures {

  val expectedRedirect = "?redirect_uri=http%3A%2F%2F%2Fpersonal-account"

  override implicit lazy val app: Application = GuiceApplicationBuilder()
    .configure(Map("metrics.enabled" -> false))
    .build()

  def testRequest(url: Option[String]): UserRequest[AnyContent] =
    UserRequest(
      None,
      None,
      None,
      ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111")),
      Credentials("abc", "def"),
      ConfidenceLevel.L200,
      None,
      None,
      url,
      None,
      None,
      None,
      FakeRequest("GET", "/personal-account")
    )

  "A user without a profile url must have no profile url afterwards" in {
    val request = testRequest(None)

    val result = new WithGovernmentGatewayRouteAction().transform(request)

    whenReady(result) {
      _.profile mustBe None
    }
  }

  "A user with a profile url must also have a redirect" in {
    val request = testRequest(Some("abc"))

    val result = new WithGovernmentGatewayRouteAction().transform(request)

    whenReady(result) {
      _.profile mustBe Some("abc" + expectedRedirect)
    }
  }

  "A user with a redirect must only have one redirect no matter how many times we land on the page" in {
    val request = testRequest(Some("abc"))

    val result1 = new WithGovernmentGatewayRouteAction().transform(request)

    whenReady(result1) { x =>
      {
        val y = new WithGovernmentGatewayRouteAction().transform(x)
        whenReady(y) {
          _.profile mustBe Some("abc" + expectedRedirect)
        }
      }
    }
  }
}
