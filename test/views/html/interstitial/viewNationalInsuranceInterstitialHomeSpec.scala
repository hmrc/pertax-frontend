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

package views.html.interstitial

import controllers.auth.requests.UserRequest
import models.NonFilerSelfAssessmentUser
import org.jsoup.Jsoup
import play.api.Play
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures}

class viewNationalInsuranceInterstitialHomeSpec extends BaseSpec {

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  implicit val messages = Messages.Implicits.applicationMessages
  implicit val templateRenderer = app.injector.instanceOf[TemplateRenderer]

  "Rendering viewNationalInsuranceInterstitialHome.scala.html" should {

    "show NINO section when user is High GG and with Paye" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        NonFilerSelfAssessmentUser,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest())
      val document = Jsoup.parse(
        views.html.interstitial
          .viewNationalInsuranceInterstitialHome(Html(""), "asfa")
          .toString)
      Option(document.select(".nino").first).isDefined shouldBe true
    }

    "show NINO section when user is Verify (not GG) and not SA" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        None,
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest())
      val document = Jsoup.parse(
        views.html.interstitial
          .viewNationalInsuranceInterstitialHome(Html(""), "aas")
          .toString)
      Option(document.select(".nino").first).isDefined shouldBe true
    }

  }
}
