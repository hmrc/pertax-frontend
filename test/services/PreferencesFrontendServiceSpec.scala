/*
 * Copyright 2022 HM Revenue & Customs
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
import models.{NonFilerSelfAssessmentUser, PaperlessMessages, PaperlessResponse, PaperlessStatus, PaperlessUrl}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.IM_A_TEAPOT
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import testUtils.BaseSpec
import testUtils.UserRequestFixture.buildUserRequest
import uk.gov.hmrc.http.UpstreamErrorResponse

import scala.concurrent.Future

class PreferencesFrontendServiceSpec extends BaseSpec {

  val mockPreferencesFrontendConnector = mock[PreferencesFrontendConnector]

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(bind[PreferencesFrontendConnector].toInstance(mockPreferencesFrontendConnector))
    .build()

  "PreferenceFrontendService" must {
    List(
      ("NEW_CUSTOMER", "new"),
      ("BOUNCED_EMAIL", "bounced"),
      ("EMAIL_NOT_VERIFIED", "unverified"),
      ("PAPER", "opt_out"),
      ("ALRIGHT", "opt_in"),
      ("NO_EMAIL", "no_email")
    ).foreach { key =>
      s"return PaperlessMessages with the ${key._1} message keys" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            request = FakeRequest()
          )
        when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PaperlessResponse](
              Future.successful(Right(PaperlessResponse(PaperlessStatus(key._1, ""), PaperlessUrl("", ""))))
            )
          )

        val service = app.injector.instanceOf[PreferencesFrontendService]
        service
          .getPaperlessPreference("url", "returnMessage")(userRequest)
          .value
          .futureValue
          .getOrElse(UpstreamErrorResponse("", IM_A_TEAPOT)) mustBe PaperlessMessages(
          s"label.paperless_${key._2}_response",
          s"label.paperless_${key._2}_link",
          Some(s"label.paperless_${key._2}_hidden")
        )
      }
    }

    List(
      ("RE_OPT_IN", "reopt"),
      ("RE_OPT_IN_MODIFIED", "reopt_modified")
    ).foreach { key =>
      s"return PaperlessMessages with the ${key._1} message keys" in {
        implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
          buildUserRequest(
            saUser = NonFilerSelfAssessmentUser,
            request = FakeRequest()
          )
        when(mockPreferencesFrontendConnector.getPaperlessStatus(any(), any())(any()))
          .thenReturn(
            EitherT[Future, UpstreamErrorResponse, PaperlessResponse](
              Future.successful(Right(PaperlessResponse(PaperlessStatus(key._1, ""), PaperlessUrl("", ""))))
            )
          )

        val service = app.injector.instanceOf[PreferencesFrontendService]
        service
          .getPaperlessPreference("url", "returnMessage")(userRequest)
          .value
          .futureValue
          .getOrElse(UpstreamErrorResponse("", IM_A_TEAPOT)) mustBe PaperlessMessages(
          s"label.paperless_${key._2}_response",
          s"label.paperless_${key._2}_link",
          None
        )
      }
    }
  }
}
