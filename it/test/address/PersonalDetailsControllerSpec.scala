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

package address

import com.github.tomakehurst.wiremock.client.WireMock._
import models.AgentClientStatus
import models.admin.AgentClientRelationshipsToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, status => getStatus, writeableOf_AnyContentAsEmpty}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.concurrent.Future

class PersonalDetailsControllerSpec extends IntegrationSpec {
  val designatoryDetails: String =
    s"""|
       |{
        |  "etag" : "115",
        |  "person" : {
        |    "firstName" : "HIPPY",
        |    "middleName" : "T",
        |    "lastName" : "NEWYEAR",
        |    "title" : "Mr",
        |    "honours": "BSC",
        |    "sex" : "M",
        |    "dateOfBirth" : "1952-04-01",
        |    "nino" : "$generatedNino",
        |    "deceased" : false
        |  },
        |  "address" : {
        |    "line1" : "26 FARADAY DRIVE",
        |    "line2" : "PO BOX 45",
        |    "line3" : "LONDON",
        |    "postcode" : "CT1 1RQ",
        |    "startDate": "2009-08-29",
        |    "country" : "GREAT BRITAIN",
        |    "type" : "Residential",
        |    "status": 0
        |  }
        |}
        |""".stripMargin

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.agent-client-relationships.maxTps"       -> 100,
      "feature.agent-client-relationships.cache"        -> true,
      "feature.agent-client-relationships.timeoutInSec" -> 1
    )
    .build()

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientRelationshipsToggle)))
      .thenReturn(Future.successful(FeatureFlag(AgentClientRelationshipsToggle, isEnabled = true)))
  }

  val url       = s"/personal-account/profile-and-settings"
  val agentLink = "/manage-your-tax-agents"

  "your-profile" must {
    "show manage your agent link successfully" in {

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details?cached=true"))
          .willReturn(ok(designatoryDetails))
      )
      server.stubFor(
        get(urlEqualTo(s"/agent-client-relationships/customer-status"))
          .willReturn(
            ok(
              Json
                .toJson(
                  AgentClientStatus(
                    hasPendingInvitations = true,
                    hasInvitationsHistory = true,
                    hasExistingRelationships = true
                  )
                )
                .toString
            )
          )
      )

      val result: Future[Result] = route(app, request).get
      contentAsString(result).contains(agentLink)
      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-relationships/customer-status")))
    }

    "show manage your agent link in 2 request but only one request to backend due to cache" in {

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details?cached=true"))
          .willReturn(ok(designatoryDetails))
      )
      server.stubFor(
        get(urlEqualTo(s"/agent-client-relationships/customer-status"))
          .willReturn(
            ok(
              Json
                .toJson(
                  AgentClientStatus(
                    hasPendingInvitations = true,
                    hasInvitationsHistory = true,
                    hasExistingRelationships = true
                  )
                )
                .toString
            )
          )
      )

      val repeatRequest = request

      val result = (for {
        req1 <- route(app, repeatRequest).get
        req2 <- route(app, repeatRequest).get
      } yield (req1, req2)).futureValue

      contentAsString(Future.successful(result._1)).contains(agentLink)
      contentAsString(Future.successful(result._2)).contains(agentLink)

      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-relationships/customer-status")))
    }

    "loads between 1sec and 4sec due to early timeout on agent link" in {

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details?cached=true"))
          .willReturn(ok(designatoryDetails))
      )
      server.stubFor(
        get(urlEqualTo(s"/agent-client-relationships/customer-status"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                Json
                  .toJson(
                    AgentClientStatus(
                      hasPendingInvitations = true,
                      hasInvitationsHistory = true,
                      hasExistingRelationships = true
                    )
                  )
                  .toString
              )
              .withFixedDelay(5000)
          )
      )

      val startTime                      = System.nanoTime
      val result: Future[(Result, Long)] = route(app, request).get.map { result =>
        (result, (System.nanoTime - startTime) / 1000000.toLong)
      }

      val duration = result.map(_._2).futureValue

      duration mustBe <=[Long](4000)
      duration mustBe >=[Long](1000)
      getStatus(result.map(_._1)) mustBe OK
      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-relationships/customer-status")))
    }
  }

}
