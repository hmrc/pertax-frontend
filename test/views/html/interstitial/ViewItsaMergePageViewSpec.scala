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
import models._
import org.mockito.Mockito.reset
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, AnyContentAsEmpty}
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.domain.{SaUtr, SaUtrGenerator}
import util.DateTimeTools.current
import views.html.ViewSpec

class ViewItsaMergePageViewSpec extends ViewSpec {

  lazy val viewItsaMergePageView: ViewItsaMergePageView = inject[ViewItsaMergePageView]

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

  "Rendering ViewItsaMergePageView.scala.html" must {

    "show content for Itsa" in {
      val doc =
        asDocument(
          viewItsaMergePageView(
            isSa = false,
            isSeiss = false,
            userRequest.saUserType
          ).toString
        )

      doc.text() must include(Messages("label.it_header"))
      doc.text() must include(Messages("label.mtd_for_it"))
      doc.text() must include(Messages("label.send_updates_sole_traders"))

      hasLink(
        doc,
        Messages("label.view_manage_your_mtd_for_it")
      )
    }

    "show content for Itsa , SA , Seiss when all the conditions are true with SA Enrolled" in {
      val doc =
        asDocument(
          viewItsaMergePageView(
            isSa = true,
            isSeiss = true,
            ActivatedOnlineFilerSelfAssessmentUser(saUtr)
          ).toString
        )

      doc.text() must include(Messages("label.it_header"))
      doc.text() must include(Messages("label.mtd_for_it"))
      doc.text() must include(Messages("label.send_updates_sole_traders"))
      doc.text() must include(Messages("label.self_assessment_tax_returns"))
      doc.text() must not include (Messages("label.old_way_sa_returns"))
      doc.text() must include(Messages("title.seiss"))
      hasLink(
        doc,
        Messages("label.view_manage_your_mtd_for_it")
      )

      hasLink(
        doc,
        Messages("label.access_your_sa_returns")
      )

      hasLink(
        doc,
        Messages("body.seiss")
      )
    }

    "show content for Itsa , SA , Seiss when all the conditions are true with SA not Enrolled" in {
      val doc =
        asDocument(
          viewItsaMergePageView(
            isSa = true,
            isSeiss = true,
            NotEnrolledSelfAssessmentUser(saUtr)
          ).toString
        )

      doc.text() must include(Messages("label.it_header"))
      doc.text() must include(Messages("label.mtd_for_it"))
      doc.text() must include(Messages("label.send_updates_sole_traders"))
      doc.text() must include(Messages("label.self_assessment_tax_returns"))
      doc.text() must not include (Messages("label.old_way_sa_returns"))
      doc.text() must include(Messages("title.seiss"))
      doc.text() must include(Messages("label.making_tax_digital"))

      hasLink(
        doc,
        Messages("label.view_manage_your_mtd_for_it")
      )

      hasLink(
        doc,
        messages("label.not_enrolled.link.text")
      )

      hasLink(
        doc,
        Messages("body.seiss")
      )
    }
  }
}
