/*
 * Copyright 2023 HM Revenue & Customs
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
import controllers.auth.requests.UserRequest
import models.dto.InternationalAddressChoiceDto
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.ViewSpec
import views.html.personaldetails.PostalInternationalAddressChoiceView

class PostalInternationalAddressChoiceViewSpec extends ViewSpec {

  lazy val view: PostalInternationalAddressChoiceView = inject[PostalInternationalAddressChoiceView]

  implicit val configDecorator: ConfigDecorator                 = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  "rendering PostalInternationalAddressChoiceView" must {
    "must render the correct h1 appropriate to postal address when form has the default error message" in {
      val result = asDocument(view(InternationalAddressChoiceDto.form()).toString)
      assertContainsText(result, messages("label.is_your_postal_address_in_the_uk"))
    }
  }
}
