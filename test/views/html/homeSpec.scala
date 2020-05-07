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

package views.html

import config.ConfigDecorator
import models._
import org.jsoup.nodes.Document
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures
import util.UserRequestFixture.buildUserRequest
import viewmodels.HomeViewModel

import scala.collection.JavaConversions._

class homeSpec extends ViewSpec with MockitoSugar {

  lazy val home = injected[home]

  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val templateRenderer = injected[TemplateRenderer]

  val homeViewModel = HomeViewModel(Nil, Nil, Nil, true, None)

  "Rendering HomeView.scala.html" should {

    "show the users name and not 'Your account' when the user has details and is not a GG user" in {
      implicit val userRequest =
        buildUserRequest(personDetails = Some(Fixtures.buildPersonDetails), userName = None, request = FakeRequest())

      lazy val document: Document = asDocument(home(homeViewModel).toString)

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show the users name and not 'Your account' when the user has no details but is a GG user" in {
      implicit val userRequest = buildUserRequest(
        personDetails = None,
        userName = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        request = FakeRequest()
      )

      lazy val document: Document = asDocument(home(homeViewModel).toString)

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show 'Your account' and not the users name when the user has no details and is not a GG user" in {
      implicit val userRequest = buildUserRequest(personDetails = None, userName = None, request = FakeRequest())

      lazy val document: Document = asDocument(home(homeViewModel).toString)

      document.select("h1").exists(e => e.text == "Your account") shouldBe true
    }

    "should not show the UTR if the user is not a self assessment user" in {
      implicit val userRequest = buildUserRequest(request = FakeRequest())

      val view = home(homeViewModel).toString

      view should not contain messages("label.home_page.utr")
    }

    "should show the UTR if the user is a self assessment user" in {
      implicit val userRequest = buildUserRequest(request = FakeRequest())
      val utr = new SaUtrGenerator().nextSaUtr.utr
      val view = home(homeViewModel.copy(saUtr = Some(utr))).toString

      view should include(messages("label.home_page.utr"))
      view should include(utr)
    }
  }
}
