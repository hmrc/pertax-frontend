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

  private class Harness extends PertaxBaseController(cc) {
    def executePersonalDetailsNameOrTrustedHelperName(
      optPersonDetails: Option[PersonDetails]
    )(implicit request: UserRequest[AnyContent]): Option[String] =
      this.personalDetailsNameOrTrustedHelperName(optPersonDetails)(request)

    def executePersonalDetailsNameOrDefault(
      optPersonDetails: Option[PersonDetails]
    )(implicit request: UserRequest[AnyContent]): String =
      this.personalDetailsNameOrDefault(optPersonDetails)(request)
  }

  private val sut = new Harness()

  private val th = TrustedHelper("principalName", "attorneyName", "returnUrl", Some(generatedTrustedHelperNino.nino))

  "personalDetailsNameOrDefault" must {
    "return correct default content when no personal details passed in" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executePersonalDetailsNameOrDefault(None)(userRequest) mustBe Messages("label.your_account")
    }
    "return principal (helpee) name when trusted helper retrieval on request" in {
      val th                                                        = TrustedHelper("principalName", "attorneyName", "returnUrl", Some(generatedTrustedHelperNino.nino))
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        request = FakeRequest(),
        trustedHelper = Some(th)
      )
      sut.executePersonalDetailsNameOrDefault(None)(userRequest) mustBe th.principalName
    }
    "return correct name when personal details passed in with values" in {
      val person                                                    = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails                                             = PersonDetails(person, None, None)
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executePersonalDetailsNameOrDefault(Some(personDetails))(userRequest) mustBe personDetails.person.shortName
        .getOrElse("")
    }
    "return correct default content when empty personal details passed in" in {
      val person                                                    = Person(None, None, None, None, None, None, None, None, None)
      val personDetails                                             = PersonDetails(person, None, None)
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executePersonalDetailsNameOrDefault(Some(personDetails))(userRequest) mustBe Messages("label.your_account")
    }
  }

  "personalDetailsNameOrTrustedHelperName" must {
    "return None when no personal details or trusted helper passed in" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executePersonalDetailsNameOrTrustedHelperName(None)(userRequest) mustBe None
    }
    "return principal (helpee) name when no personal details and trusted helper retrieval on request" in {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(
        request = FakeRequest(),
        trustedHelper = Some(th)
      )
      sut.executePersonalDetailsNameOrTrustedHelperName(None)(userRequest) mustBe Some(th.principalName)
    }
    "return correct name when personal details passed in with values and no trusted helper on request" in {
      val person                                                    = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails                                             = PersonDetails(person, None, None)
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executePersonalDetailsNameOrTrustedHelperName(Some(personDetails))(
        userRequest
      ) mustBe personDetails.person.shortName
    }
    "return correct name when personal details passed in with values and trusted helper on request" in {
      val person                                                    = Fixtures.buildPersonDetailsCorrespondenceAddress.person
      val personDetails                                             = PersonDetails(person, None, None)
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(request = FakeRequest(), trustedHelper = Some(th))
      sut.executePersonalDetailsNameOrTrustedHelperName(Some(personDetails))(
        userRequest
      ) mustBe personDetails.person.shortName
    }
    "return empty name when empty personal details passed in" in {
      val person                                                    = Person(None, None, None, None, None, None, None, None, None)
      val personDetails                                             = PersonDetails(person, None, None)
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())
      sut.executePersonalDetailsNameOrTrustedHelperName(Some(personDetails))(userRequest) mustBe None
    }
  }

}
