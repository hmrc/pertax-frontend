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

import config.ConfigDecorator
import models.PertaxContext
import org.jsoup.Jsoup
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import util.{BaseSpec, Fixtures}

class viewNationalInsuranceInterstitialHomeSpec extends BaseSpec {

  val messages = Messages.Implicits.applicationMessages

  "Rendering viewNationalInsuranceInterstitialHome.scala.html" should {

    "show NINO section when user is High GG and with Paye" in {
      val pertaxUser = Fixtures.buildFakePertaxUser(withPaye = true, isGovernmentGateway = true, isHighGG = true)
      val document = Jsoup.parse(
        views.html.interstitial
          .viewNationalInsuranceInterstitialHome(Html(""), "asfa")(
            PertaxContext(
              FakeRequest("GET", "/test"),
              mockLocalPartialRetreiver,
              injected[ConfigDecorator],
              Some(pertaxUser)),
            messages)
          .toString)
      Option(document.select(".nino").first).isDefined shouldBe true
    }

    "show NINO section when user is Verify (not GG) and not SA" in {
      val pertaxUser = Fixtures.buildFakePertaxUser(withSa = false, isGovernmentGateway = false)
      val document = Jsoup.parse(
        views.html.interstitial
          .viewNationalInsuranceInterstitialHome(Html(""), "aas")(
            PertaxContext(
              FakeRequest("GET", "/test"),
              mockLocalPartialRetreiver,
              injected[ConfigDecorator],
              Some(pertaxUser)),
            messages)
          .toString)
      Option(document.select(".nino").first).isDefined shouldBe true
    }

  }
}
