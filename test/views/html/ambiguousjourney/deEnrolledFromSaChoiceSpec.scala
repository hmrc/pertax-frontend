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

import controllers.auth.requests.UserRequest
import models.dto.AmbiguousUserFlowDto
import models.{NonFilerSelfAssessmentUser, UserName}
import org.joda.time.DateTime
import org.jsoup.Jsoup
import play.api.i18n.Messages
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.renderer.TemplateRenderer
import util.{BaseSpec, Fixtures}

class deEnrolledFromSaChoiceSpec extends BaseSpec {

  override implicit lazy val app = localGuiceApplicationBuilder().build()

  implicit val messages = Messages.Implicits.applicationMessages
  implicit val templateRenderer = app.injector.instanceOf[TemplateRenderer]

  implicit val userRequest = UserRequest(
    Some(Fixtures.fakeNino),
    Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
    Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
    NonFilerSelfAssessmentUser,
    "GovernmentGateway",
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    FakeRequest()
  )

  "deEnrolledFromSaChoice view" should {
    "check page contents" in {
      val formWithErrors =
        AmbiguousUserFlowDto.form.withError("ambiguousUserFormChoice", Messages("error.enrolled.to.send.tax.required"))
      val document = Jsoup.parse(
        views.html.ambiguousjourney
          .deEnrolledFromSaChoice(formWithErrors)
          .toString)
      document.getElementsByTag("h1").text shouldBe Messages("label.did_you_deregister_from_sa_online")
      document.getElementsByClass("error-summary-list").text shouldBe Messages(
        "head.error.enrolled.to.send.tax.required")
    }
  }

}
