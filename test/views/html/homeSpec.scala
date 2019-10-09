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

package views.html

import controllers.auth.requests.UserRequest
import models._
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures}

import scala.collection.JavaConversions._

class homeSpec extends BaseSpec {

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  implicit val messages = Messages.Implicits.applicationMessages
  implicit val templateRenderer = app.injector.instanceOf[TemplateRenderer]

  val messageInboxPartial = Html("")

  "Rendering home.scala.html" should {

    "show the users name and not 'Your account' when the user has details and is not a GG user" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        Some(Fixtures.buildPersonDetails),
        None,
        None,
        None,
        FakeRequest()
      )

      lazy val document: Document = Jsoup.parse(
        views.html
          .home(Nil, Nil, Nil, true)
          .toString)

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show the users name and not 'Your account' when the user has no details but is a GG user" in {

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      lazy val document: Document = Jsoup.parse(
        views.html
          .home(Nil, Nil, Nil, true)
          .toString)

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show 'Your account' and not the users name when the user has no details and is not a GG user" in {
      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        None,
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      lazy val document: Document = Jsoup.parse(
        views.html
          .home(Nil, Nil, Nil, true)
          .toString)

      document.select("h1").exists(e => e.text == "Your account") shouldBe true
    }

  }

}
