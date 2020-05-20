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

package views.html.interstitial

import config.ConfigDecorator
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import views.html.ViewSpec

class ViewNationalInsuranceInterstitialHomeViewSpec extends ViewSpec with MockitoSugar {

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  lazy val view = injected[ViewNationalInsuranceInterstitialHomeView]

  implicit val templateRenderer = app.injector.instanceOf[TemplateRenderer]
  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())

  "Rendering ViewNationalInsuranceInterstitialHomeView.scala.html" should {

    "show NINO section when a nino is present" in {
      val document = asDocument(view(Html(""), "asfa", userRequest.nino).toString)
      Option(document.select(".nino").first).isDefined shouldBe true
    }

    "show incomplete when there is no NINO" in {
      val document = asDocument(view(Html(""), "http://google.com", None).toString)
      Option(document.select(".nino").first).isDefined shouldBe false
      document.body().toString should include(messages("label.you_can_see_this_part_of_your_account_if_you_complete"))
    }

  }
}
