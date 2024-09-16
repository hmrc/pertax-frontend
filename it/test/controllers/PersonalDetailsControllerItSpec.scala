/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status, writeableOf_AnyContentAsEmpty}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys

import java.util.UUID

class PersonalDetailsControllerItSpec extends IntegrationSpec {
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.preferences-frontend.port" -> server.port()
    )
    .build()

  val uuid: String                                    = UUID.randomUUID().toString
  val agentClientAuthorisationUrl: String             = "/agent-client-authorisation/status"
  val personDetailsUrl: String                        = s"/citizen-details/$generatedNino/designatory-details"
  implicit lazy val messageProvider: MessagesProvider = app.injector.instanceOf[MessagesProvider]
  val paperlessStatusTargetUrl: String                =
    "http://localhost/paperless/check-settings?returnUrl=encrypted1&returnLinkText=encrypted2"

  def paperlessStatusMessage(responseCode: String): String = s"""{
                                                       |  "status": {
                                                       |    "name": "${responseCode.toUpperCase()}",
                                                       |    "category": "INFO",
                                                       |    "text": "You chose to get your Self Assessment tax letters online"
                                                       |  },
                                                       |  "url": {
                                                       |    "link": "$paperlessStatusTargetUrl",
                                                       |    "text": "Check your settings"
                                                       |  }
                                                       |}""".stripMargin

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(
          ok(FileHelper.loadFileInterpolatingNino("./it/test/resources/person-details.json", generatedNino))
        )
    )
    server.stubFor(
      get(urlEqualTo(agentClientAuthorisationUrl))
        .willReturn(ok("""{
      |  "hasPendingInvitations": true,
      |  "hasInvitationsHistory": true,
      |  "hasExistingRelationships": true
      |  }""".stripMargin))
    )
  }

  "/personal-account/profile-and-settings" must {
    val url = "/personal-account/profile-and-settings"

    Map(
      "NEW_CUSTOMER"       -> List(
        Some("label.paperless_new_response"),
        Some("label.paperless_new_link"),
        Some("label.paperless_new_hidden")
      ),
      "BOUNCED_EMAIL"      -> List(
        Some("label.paperless_bounced_response"),
        Some("label.paperless_bounced_link"),
        Some("label.paperless_bounced_hidden")
      ),
      "EMAIL_NOT_VERIFIED" -> List(
        Some("label.paperless_unverified_response"),
        Some("label.paperless_unverified_link"),
        Some("label.paperless_unverified_hidden")
      ),
      "RE_OPT_IN"          -> List(Some("label.paperless_reopt_response"), Some("label.paperless_reopt_link"), None),
      "RE_OPT_IN_MODIFIED" -> List(
        Some("label.paperless_reopt_modified_response"),
        Some("label.paperless_reopt_modified_link"),
        None
      ),
      "PAPER"              -> List(
        Some("label.paperless_opt_out_response"),
        Some("label.paperless_opt_out_link"),
        Some("label.paperless_opt_out_hidden")
      ),
      "ALRIGHT"            -> List(
        Some("label.paperless_opt_in_response"),
        Some("label.paperless_opt_in_link"),
        Some("label.paperless_opt_in_hidden")
      ),
      "NO_EMAIL"           -> List(
        Some("label.paperless_no_email_response"),
        Some("label.paperless_no_email_link"),
        Some("label.paperless_no_email_hidden")
      )
    ).foreach { case (key, texts) =>
      s"show $key status for Contact preferences" in {
        server.stubFor(
          get(
            urlEqualTo(
              "/paperless/status?returnUrl=DO8MisXKpizAWqbqizwb/NmVXB7xoygTAvj2HM8Iu90TdGXVcrxI848U4tDa5gI%2BPdRU4lmXYlaEQe/%2Bt1KoUg%3D%3D&returnLinkText=HWSAralA6odRpvfKtB4jCw%3D%3D"
            )
          )
            .willReturn(ok(paperlessStatusMessage(key)))
        )

        val request         = FakeRequest(GET, url)
          .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
        val result          = route(app, request).get
        val includeExpected = texts.foldLeft(include(paperlessStatusTargetUrl)) { (accumulator, currentValue) =>
          currentValue match {
            case None        => accumulator
            case Some(label) => accumulator and include(messages(label))
          }
        }

        status(result) mustBe OK
        contentAsString(result) must includeExpected
        server.verify(
          getRequestedFor(
            urlEqualTo(
              "/paperless/status?returnUrl=DO8MisXKpizAWqbqizwb/NmVXB7xoygTAvj2HM8Iu90TdGXVcrxI848U4tDa5gI%2BPdRU4lmXYlaEQe/%2Bt1KoUg%3D%3D&returnLinkText=HWSAralA6odRpvfKtB4jCw%3D%3D"
            )
          )
        )
      }
    }

    s"not show Contact preferences" when {
      "There is a client error" in {
        server.stubFor(
          get(urlMatching("/paperless/status.*"))
            .willReturn(badRequest())
        )

        val request = FakeRequest(GET, url)
          .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
        val result  = route(app, request)

        contentAsString(result.get) must not include "Contact preferences"
      }

      "There is a server error" in {
        server.stubFor(
          get(urlMatching("/paperless/status.*"))
            .willReturn(serverError())
        )

        val request = FakeRequest(GET, url)
          .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
        val result  = route(app, request)

        contentAsString(result.get) must not include "Contact preferences"
      }
    }
  }

}
