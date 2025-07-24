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

class ViewMTDIDViewSpec extends ViewSpec {

  lazy val viewMTDITView: ViewMTDITView = inject[ViewMTDITView]

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

  "Rendering ViewMTDITView.scala.html" must {

    "show content" in {
      val doc =
        asDocument(
          viewMTDITView(
          ).toString
        )

      doc.text() must include(Messages("label.mtdit.heading"))
      doc.text() must include(Messages("label.mtdit.page.p1"))
      doc.text() must include(Messages("label.mtdit.page.p2"))

      hasLink(
        doc,
        Messages("label.mtdit.page.find_out_more")
      )
    }

  }
}
