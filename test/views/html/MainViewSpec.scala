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
import controllers.auth.requests.UserRequest
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest

class MainViewSpec extends ViewSpec {

  def main = injected[MainView]

  def mainV2: Html =
    main(
      "Main Test",
      Some("Page name"),
      showUserResearchBanner = true,
      Some(Html("SidebarLinks")),
      Some("sidebar-class"),
      supportLinkEnabled = true,
      Some(Html("script")),
      Some(Html("ScriptElement")),
      Some("article-class"),
      includeGridWrapper = true,
      Some("/test"),
      Some(Html("AdditionalGaCalls")),
      printableDocument = true
    )(Html("Content"))

  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val templateRenderer: TemplateRenderer = injected[TemplateRenderer]

  "Main" should {

    "show the number of unread messages in the messages link" in {
      val msgCount = 21
      implicit val userRequest = buildUserRequest(request = FakeRequest(), messageCount = Some(msgCount))
      val doc = asDocument(mainV2.toString)

      println(mainV2)

      doc.text() should include(msgCount.toString)
      //        .getElementsByAttributeValueMatching("href", "/personal-account/messages").text() should include(
      //        msgCount.toString)
    }
  }
}
