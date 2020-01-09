/*
 * Copyright 2020 HM Revenue & Customs
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

package services

import config.ConfigDecorator
import models.NonFilerSelfAssessmentUser
import org.scalatest.mockito.MockitoSugar
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Result
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.http.HttpResponse
import util.UserRequestFixture.buildUserRequest
import util.BaseSpec

class UpdateAddressResponseSpec extends BaseSpec with I18nSupport with MockitoSugar {

  implicit val configDecorator: ConfigDecorator = injected[ConfigDecorator]

  override def messagesApi: MessagesApi = injected[MessagesApi]

  implicit val userRequest = buildUserRequest(
    saUser = NonFilerSelfAssessmentUser,
    credentials = Credentials("", "Verify"),
    confidenceLevel = ConfidenceLevel.L500,
    request = FakeRequest()
  )

  def genericFunc(): Result =
    Ok

  "UpdateAddressResponse.response" should {
    "return the block result for UpdateAddressSuccessResponse" in {
      val result = UpdateAddressSuccessResponse.response(genericFunc)
      status(result) shouldBe OK
    }

    "return BAD_REQUEST for UpdateAddressBadRequestResponse" in {
      val result = UpdateAddressBadRequestResponse.response(genericFunc)
      status(result) shouldBe BAD_REQUEST
    }

    "return INTERNAL_SERVER_ERROR for UpdateAddressUnexpectedResponse" in {
      val updateAddressResponse = UpdateAddressUnexpectedResponse(HttpResponse(123))
      val result = updateAddressResponse.response(genericFunc)
      status(result) shouldBe INTERNAL_SERVER_ERROR
    }

    "return INTERNAL_SERVER_ERROR for UpdateAddressErrorResponse" in {
      val updateAddressResponse = UpdateAddressErrorResponse(new RuntimeException("not used"))
      val result = updateAddressResponse.response(genericFunc)
      status(result) shouldBe INTERNAL_SERVER_ERROR
    }
  }
}
