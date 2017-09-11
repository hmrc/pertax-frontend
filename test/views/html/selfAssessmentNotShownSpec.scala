/*
 * Copyright 2017 HM Revenue & Customs
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

package views.html

import config.ConfigDecorator
import models._
import org.jsoup.Jsoup
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import util.{BaseSpec, Fixtures}

import scala.collection.JavaConversions._

class selfAssessmentNotShownSpec extends BaseSpec {

  abstract class selfAssessmentNotShownSetup {
    val messages = Messages.Implicits.applicationMessages

    val pertaxUser = Fixtures.buildFakePertaxUser(withSa = true, isGovernmentGateway = true)

    val document = Jsoup.parse(views.html.selfAssessmentNotShown(Html(""))(PertaxContext(FakeRequest("GET", "/test"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)
  }


  "Rendering selfAssessmentNotShown.scala.html" should {

    "show the correct h1 title of 'You can't access your Self Assessment information from this account' on selfAssessmentNotShown page" in new selfAssessmentNotShownSetup {
      document.select("h1").exists(e => e.text == "You can't access your Self Assessment information from this account") shouldBe true
    }

    "show the correct h2 title of 'If you send your tax return online' on selfAssessmentNotShown page" in new selfAssessmentNotShownSetup {
      document.select("h2").exists(e => e.text == "If you send your tax return online") shouldBe true
    }

    "show the correct h2 title of 'If you send your tax return by post' on selfAssessmentNotShown page" in new selfAssessmentNotShownSetup {
      document.select("h2").exists(e => e.text == "If you send your tax return by post") shouldBe true
    }

    "show the correct h2 title of 'If you have never sent your tax return online' on selfAssessmentNotShown page" in new selfAssessmentNotShownSetup {
      document.select("h2").exists(e => e.text == "If you have never sent your tax return online") shouldBe true
    }
  }
}
