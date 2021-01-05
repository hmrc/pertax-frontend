/*
 * Copyright 2021 HM Revenue & Customs
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
import controllers.bindable.SoleAddrType
import models.{Country}
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures}
import util.UserRequestFixture.buildUserRequest
import views.html.ViewSpec
import views.html.cards.personaldetails.MainAddressView

class MainAddressViewSpec extends ViewSpec with MockitoSugar {

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  lazy val view = injected[MainAddressView]

  implicit val templateRenderer = injected[TemplateRenderer]
  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())
  val result = asDocument(view(Fixtures.buildFakePersonDetails, true, false, false, List[Country]()).toString)

  "when on main address change PostalAddress points to InternationalPostalAddressChoiceController" in {

    assertContainsLink(
      result,
      messages("label.change_where_we_send_your_letters"),
      routes.PostalInternationalAddressChoiceController.onPageLoad().url)
  }

}
