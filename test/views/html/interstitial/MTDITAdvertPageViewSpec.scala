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

package views.html.interstitial

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.*
import org.mockito.Mockito.reset
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, AnyContentAsEmpty}
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.DateTimeTools.current
import views.html.ViewSpec

class MTDITAdvertPageViewSpec extends ViewSpec {

  lazy val mtditAdvertPageView: MTDITAdvertPageView = inject[MTDITAdvertPageView]

  lazy implicit val configDecorator: ConfigDecorator            = mock[ConfigDecorator] // inject[ConfigDecorator]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
    buildUserRequest(request = FakeRequest())

  val nextDeadlineTaxYear: String = (current.currentYear + 1).toString
  val saUtr: SaUtr                = SaUtr(new SaUtrGenerator().nextSaUtr.utr)

  trait SelfAssessmentLocalSetup {

    val user: SelfAssessmentUser

    implicit val request: UserRequest[AnyContent] = buildUserRequest(
      saUser = user,
      request = FakeRequest()
    )

  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(configDecorator)
  }

  "Rendering MTDITAdvertPageView.scala.html" must {

    "show english content" in {
      val doc =
        asDocument(
          mtditAdvertPageView(
          ).toString
        )

      doc.text() must include("Making Tax Digital for Income Tax")
      doc.text() must include(
        "Making Tax Digital for Income Tax is a new way for sole traders and landlords to report income and expenses to HMRC."
      )
      doc.text() must include(
        "Some sole traders and landlords must start using it from 6 April 2027, based on their total annual income from self-employment and property."
      )

      hasLink(
        doc,
        "Find out more about Making Tax Digital for Income Tax and see if you can sign up early"
      )
    }
    "show welsh content" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest())
      val doc                                                       =
        asDocument(
          mtditAdvertPageView(
          )(userRequest, welshMessages).toString
        )

      doc.text() must include("Troi Treth yn Ddigidol ar gyfer Treth Incwm")
      doc.text() must include(
        "Mae’r cynllun Troi Treth yn Ddigidol ar gyfer Treth Incwm yn ffordd newydd i unig fasnachwyr a landlordiaid roi gwybod i CThEF am incwm a threuliau."
      )
      doc.text() must include(
        "O 6 Ebrill 2027 ymlaen, bydd yn rhaid i rai unig fasnachwyr a landlordiaid ddechrau ei ddefnyddio, a hynny’n seiliedig ar gyfanswm eu hincwm blynyddol o hunangyflogaeth ac eiddo."
      )

      hasLink(
        doc,
        "Dysgwch ragor ynghylch Troi Treth yn Ddigidol ar gyfer Treth Incwm a gweld a allwch gofrestru’n gynnar"
      )
    }

  }

}
