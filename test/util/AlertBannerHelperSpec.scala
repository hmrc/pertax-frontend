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

package util

import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import play.api.Application
import play.api.i18n.Messages
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.{BaseSpec, UserRequestFixture}

class AlertBannerHelperSpec extends BaseSpec {

  val helper: AlertBannerHelper                                           = inject[AlertBannerHelper]
  lazy val mockPreferencesFrontendConnector: PreferencesFrontendConnector = mock[PreferencesFrontendConnector]
  implicit val userRequest: UserRequest[AnyContentAsEmpty.type]           =
    UserRequestFixture.buildUserRequest(request = FakeRequest().withSession("sessionId" -> "FAKE_SESSION_ID"))

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPreferencesFrontendConnector, mockFeatureFlagService)
  }

  override lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector)
    )
    .build()
  implicit lazy val messages         = app.injector.instanceOf[Messages]

  "AlertBannerHelper" ignore {}
}
