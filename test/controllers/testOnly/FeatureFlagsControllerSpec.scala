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

package controllers.testOnly

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.mvc.MessagesControllerComponents
import play.api.test.FakeRequest
import play.api.http.Status.{INTERNAL_SERVER_ERROR, OK}
import play.api.test.Helpers.{contentAsString, defaultAwaitTimeout, status}
import services.admin.FeatureFlagService
import scala.concurrent.Future
import testUtils.BaseSpec

class FeatureFlagsControllerSpec extends BaseSpec {

  implicit val mcc                = inject[MessagesControllerComponents]
  lazy val mockFeatureFlagService = mock[FeatureFlagService]

  val controller = new FeatureFlagsController(mcc, mockFeatureFlagService)

  "PUT /setDefaults" must {
    "return a OK response" when {
      "default values are successfully set" in {
        when(mockFeatureFlagService.setAll(any())).thenReturn(Future.successful())

        val result = controller.setDefaults()(
          FakeRequest().withHeaders("Authorization" -> "Token some-token")
        )

        status(result) mustBe OK
        contentAsString(result) mustBe "Default flags set"
      }
    }

    "recover with an error response" when {
      "there is error while setting default flag " in {

        when(mockFeatureFlagService.setAll(any()))
          .thenReturn(Future.failed(new IllegalArgumentException("some failure text")))

        val result = controller.setDefaults()(
          FakeRequest().withHeaders("Authorization" -> "Token some-token")
        )

        whenReady(result.failed) { e =>
          e mustBe an[IllegalArgumentException]

          e.getMessage must include("some failure text")
        }
      }
    }
  }
}
