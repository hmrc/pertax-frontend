/*
 * Copyright 2022 HM Revenue & Customs
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

package views.html.address

import config.ConfigDecorator
import controllers.address.routes
import models.Country
import play.api.test.FakeRequest
import uk.gov.hmrc.renderer.TemplateRenderer
import util.Fixtures
import util.UserRequestFixture.buildUserRequest
import views.html.ViewSpec
import views.html.cards.personaldetails.PostalAddressView

class PostalAddressViewSpec extends ViewSpec {
  override implicit lazy val app = localGuiceApplicationBuilder().build()

  lazy val view = injected[PostalAddressView]

  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())
  val result = asDocument(view(Fixtures.buildFakePersonDetails, false, List[Country](), true).toString)

  "when on Postal address change PostalAddress points to InternationalPostalAddressChoiceController" in {

    assertContainsLink(
      result,
      messages("label.change_your_postal_address"),
      routes.PostalDoYouLiveInTheUKController.onPageLoad.url
    )
  }

}
