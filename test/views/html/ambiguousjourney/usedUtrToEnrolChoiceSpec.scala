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

package views.html.ambiguousjourney

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.dto.AmbiguousUserFlowDto
import models.{NonFilerSelfAssessmentUser, UserName}
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.Messages
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures}

class usedUtrToEnrolChoiceSpec extends BaseSpec with MockitoSugar {

  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  implicit val messages = Messages.Implicits.applicationMessages
  implicit val templateRenderer = app.injector.instanceOf[TemplateRenderer]

  implicit val userRequest = UserRequest(
    Some(Fixtures.fakeNino),
    Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
    Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
    NonFilerSelfAssessmentUser,
    Credentials("", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    None,
    FakeRequest()
  )

  "usedUtrToEnrolChoice view" should {
    "check page contents" in {
      val form = AmbiguousUserFlowDto.form
      val document = Jsoup.parse(
        views.html.ambiguousjourney
          .usedUtrToEnrolChoice(form, "")
          .toString)
      document.getElementsByTag("h1").text shouldBe Messages("label.have_you_used_your_utr_to_enrol")

      val expectedMessage = Jsoup.parse(Messages("label.your_utr_is_a_ten_digit_number_we_sent_by_post")).text()
      document.getElementsContainingText(expectedMessage).hasText shouldBe true
    }
  }

}
