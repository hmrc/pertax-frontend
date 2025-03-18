/*
 * Copyright 2025 HM Revenue & Customs
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

package controllers

import controllers.auth.requests.UserRequest
import models.{Person, PersonDetails}
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{AnyContent, AnyContentAsEmpty, MessagesControllerComponents}
import play.api.test.FakeRequest
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{BaseSpec, Fixtures}
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper

class PertaxBaseControllerSpec extends BaseSpec with I18nSupport {

  private val cc = app.injector.instanceOf[MessagesControllerComponents]

  private class Sut extends PertaxBaseController(cc) {
    def executeDisplayName(
      optPersonDetails: Option[PersonDetails]
    )(implicit request: UserRequest[AnyContent]): String =
      this.displayName(optPersonDetails)(request)
  }

  private val sut = new Sut()

  "displayName" must {
    "display correct default content when no personal details passed in" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executeDisplayName(None)(userRequest) mustBe Messages("label.your_personal_tax_account")
    }
    "display principal (helpee) name when trusted helper retrieval on request" in {
      val th                                                        = TrustedHelper("principalName", "attorneyName", "returnUrl", Some(generatedTrustedHelperNino.nino))
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        request = FakeRequest(),
        trustedHelper = Some(th)
      )
      sut.executeDisplayName(None)(userRequest) mustBe th.principalName
    }
    "display correct name when personal details passed in with values" in {
      val person                                                    = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails                                             = PersonDetails(person, None, None)
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executeDisplayName(Some(personDetails))(userRequest) mustBe personDetails.person.shortName.getOrElse("")
    }
    "display correct default content when empty personal details passed in" in {
      val person                                                    = Person(None, None, None, None, None, None, None, None, None)
      val personDetails                                             = PersonDetails(person, None, None)
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executeDisplayName(Some(personDetails))(userRequest) mustBe Messages("label.your_personal_tax_account")
    }
  }

}
