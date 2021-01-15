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

package error
import config.ConfigDecorator
import org.jsoup.Jsoup
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import uk.gov.hmrc.renderer.TemplateRenderer
import util.UserRequestFixture.buildUserRequest
import views.html.{InternalServerErrorView, ViewSpec}

class LocalErrorHandlerSpec extends ViewSpec with MockitoSugar {

  lazy val internalServerError = injected[InternalServerErrorView]

  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]
  implicit val templateRenderer = injected[TemplateRenderer]
  implicit val userRequest = buildUserRequest(request = FakeRequest())

  "errorTemplate" in {
    val doc = asDocument(internalServerError().toString())
    doc.getElementsByTag("h1").toString shouldBe messages("global.error.StandardError.heading")
    doc.getElementsByTag("p").toString shouldBe messages("global.error.StandardError.message")
  }

}
