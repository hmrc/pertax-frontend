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

package error

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import views.html.{InternalServerErrorView, UnauthenticatedErrorView, ViewSpec}

class LocalErrorHandlerSpec extends ViewSpec {

  lazy val internalServerError: InternalServerErrorView = inject[InternalServerErrorView]
  lazy val standardError: UnauthenticatedErrorView      = inject[UnauthenticatedErrorView]

  implicit val configDecorator: ConfigDecorator                 = inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  "standardErrorTemplate" in {
    val doc =
      asDocument(
        standardError(
          "Service unavailable",
          "Service unavailable",
          "Sorry, we are currently experiencing technical issues."
        ).toString()
      )

    doc.getElementsByTag("h1").toString must include(messages("label.service_unavailable"))
    doc.getElementsByTag("p").toString  must include(
      messages("label.sorry_we_are_currently_experiencing_technical_issues")
    )

  }

  "internalServerErrorTemplate" in {
    val doc = asDocument(internalServerError().toString())
    doc.getElementsByTag("h1").toString must include(messages("global.error.InternalServerError500.pta.title"))
    doc.getElementsByTag("p").toString  must include(
      messages(
        "global.error.InternalServerError500.pta.message.you.can"
      ) + " <a href=\"https://www.gov.uk/contact-hmrc\" class=\"govuk-link\">" + messages(
        "global.error.InternalServerError500.pta.message.contact.hmrc"
      ) + "</a> " + messages("global.error.InternalServerError500.pta.message.by.phone.post")
    )

  }

}
