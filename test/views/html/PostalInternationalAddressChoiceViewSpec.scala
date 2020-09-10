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
import models.dto.InternationalAddressChoiceDto
import org.jsoup.nodes.Document
import org.scalatestplus.mockito.MockitoSugar
import play.api.data.Form
import play.api.test.FakeRequest
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import views.html.personaldetails.PostalInternationalAddressChoiceView

class PostalInternationalAddressChoiceViewSpec extends ViewSpec with MockitoSugar {

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  lazy val view = injected[PostalInternationalAddressChoiceView]

  implicit val templateRenderer = app.injector.instanceOf[TemplateRenderer]
  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val userRequest = buildUserRequest(request = FakeRequest())

  "rendering PostalInternationalAddressChoiceViewSpec" should {
    "must render the correct h1 appropriate to postal address" in {
      val result = view(InternationalAddressChoiceDto.form).toString()
      result should include(messages("label.is_your_postal_address_in_the_uk"))
    }

    "must not render the h1 appropriate to residential addresses" in {
      val result = view(InternationalAddressChoiceDto.form).toString()
      result should not include (messages("label.do_you_live_in_the_uk"))
    }
  }
}
