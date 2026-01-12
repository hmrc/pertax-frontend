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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.inject.bind
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import repositories.JourneyCacheRepository
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys

import java.util.UUID
import scala.concurrent.Future

class SessionManagementControllerISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "external-url.bas-gateway-frontend.host"     -> "http://localhost:9553",
      "external-url.feedback-survey-frontend.host" -> "http://localhost:9514"
    )
    .overrides(
      bind[JourneyCacheRepository].toInstance(mockJourneyCacheRepository)
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(auth = false, memorandum = false)
  }

  "/personal-account/timeout" must {
    "redirect to BAS Gateway sign-out" in {

      when(mockJourneyCacheRepository.clear(any())).thenReturn(Future.successful((): Unit))

      val request =
        FakeRequest(GET, "/personal-account/timeout")
          .withSession(
            SessionKeys.sessionId -> UUID.randomUUID().toString,
            SessionKeys.authToken -> "1"
          )

      val result = route(app, request).get

      status(result) mustBe SEE_OTHER
      redirectLocation(result) mustBe Some(
        "http://localhost:9553/bas-gateway/sign-out-without-state?continue=http://localhost:9514/feedback/PERTAX"
      )
    }
  }
}
