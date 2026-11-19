/*
 * Copyright 2026 HM Revenue & Customs
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

import cats.data.EitherT
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import models.*
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, verify, when}
import play.api.i18n.Messages
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.{BaseSpec, UserRequestFixture}
import uk.gov.hmrc.http.UpstreamErrorResponse
import viewmodels.PtapContactPreferencesExperiment

import scala.concurrent.Future

class ContactPreferencesExperimentServiceSpec extends BaseSpec {

  private val mockPreferencesFrontendConnector                      = mock[PreferencesFrontendConnector]
  private implicit val messages: Messages                           = mock[Messages]
  private implicit val request: UserRequest[AnyContentAsEmpty.type] =
    UserRequestFixture.buildUserRequest(
      request = FakeRequest("GET", "/personal-account/your-tasks?ptap=true")
        .withSession("sessionId" -> "FAKE_SESSION_ID")
    )

  private val paperlessUrl =
    "https://www.qa.tax.service.gov.uk/paperless/check-settings" +
      "?returnUrl=encrypted-return-url&returnLinkText=encrypted-link-text"

  private val service = new ContactPreferencesExperimentService(mockPreferencesFrontendConnector)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockPreferencesFrontendConnector, messages)
    when(messages("ptap.contactPreferences.returnLinkText")).thenReturn("Back to your tasks")
  }

  "ContactPreferencesExperimentService.get" must {
    "return experiment data when the Paperless status is PAPER" in {
      stubStatus(PaperlessStatusOptOut(paperlessUrl))

      service.get.futureValue mustBe Some(PtapContactPreferencesExperiment(Some(paperlessUrl)))
      verify(mockPreferencesFrontendConnector)
        .getPaperlessStatus("/personal-account/your-tasks?ptap=true", "Back to your tasks")
    }

    List(
      PaperlessStatusNewCustomer(paperlessUrl),
      PaperlessStatusOptIn(paperlessUrl),
      PaperlessStatusNoEmail(paperlessUrl),
      PaperlessStatusUnverified(paperlessUrl),
      PaperlessStatusBounced(paperlessUrl),
      PaperlessStatusReopt(paperlessUrl),
      PaperlessStatusReoptModified(paperlessUrl)
    ).foreach { status =>
      s"return no experiment data when the Paperless status is ${status.responseCode}" in {
        stubStatus(status)

        service.get.futureValue mustBe None
      }
    }

    "return no experiment data when preferences-frontend returns an error" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.leftT[Future, PaperlessMessagesStatus](
          UpstreamErrorResponse("Unable to retrieve paperless status", 500)
        )
      )

      service.get.futureValue mustBe None
    }

    "return no experiment data when status retrieval fails unexpectedly" in {
      when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT[Future, UpstreamErrorResponse, PaperlessMessagesStatus](
          Future.failed(new RuntimeException("Invalid paperless response"))
        )
      )

      service.get.futureValue mustBe None
    }
  }

  private def stubStatus(status: PaperlessMessagesStatus): Unit =
    when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
      EitherT.rightT[Future, UpstreamErrorResponse](status)
    )
}
