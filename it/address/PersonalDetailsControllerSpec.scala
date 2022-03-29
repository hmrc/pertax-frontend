package address

import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, getRequestedFor, ok, put, urlEqualTo, urlMatching}
import models.AgentClientStatus
import play.api.Application
import play.api.http.Status.OK
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, redirectLocation, route, writeableOf_AnyContentAsEmpty, status => getStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.duration._
import scala.concurrent.Future

class PersonalDetailsControllerSpec extends IntegrationSpec {
  val designatoryDetails =
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



  val url = s"/personal-account/your-profile"
  val agentLink = "/manage-your-tax-agents"

  "your-profile" must {
    "show manage your agent link 5 out od 10 requests due to rate limit" in {
      implicit lazy val app: Application = localGuiceApplicationBuilder()
        .configure(
          "feature.agent-client-authorisation.maxTps" -> 1,
          "feature.agent-client-authorisation.cache" -> false,
          "feature.agent-client-authorisation.enabled" -> true
        )
        .build()

      server.stubFor(get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(ok(designatoryDetails)))
      server.stubFor(put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString)))
      server.stubFor(get(urlEqualTo(s"/agent-client-authorisation/status"))
        .willReturn(ok(Json.toJson(AgentClientStatus(true, true, true)).toString)))

      val request = FakeRequest(GET, url)

      val system: ActorSystem = ActorSystem()
      val result: Seq[Future[Result]] = (0 until 10).map { delay =>
        akka.pattern.after((delay * 500) millisecond, system.scheduler) {
            route(app, request).get
          }
      }

      val present = result.map { each =>
        contentAsString(each).contains(agentLink)
      }

      present.count(_ == true) mustBe 5
      server.verify(5, getRequestedFor(urlEqualTo("/agent-client-authorisation/status")))
    }

    "show manage your agent link in 10 request but only one request to backend due to cache" in {
      implicit lazy val app: Application = localGuiceApplicationBuilder()
        .configure(
          "feature.agent-client-authorisation.maxTps" -> 20,
          "feature.agent-client-authorisation.cache" -> true,
          "feature.agent-client-authorisation.enabled" -> true
        )
        .build()

      server.stubFor(get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(ok(designatoryDetails)))
      server.stubFor(put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString)))
      server.stubFor(get(urlEqualTo(s"/agent-client-authorisation/status"))
        .willReturn(ok(Json.toJson(AgentClientStatus(true, true, true)).toString)))

      val request = FakeRequest(GET, url)

      val system: ActorSystem = ActorSystem()
      val result: Seq[Future[Result]] = (0 until 10).map { delay =>
        akka.pattern.after((delay * 10) millisecond, system.scheduler) {
          route(app, request).get
        }
      }

      val present = result.map { each =>
        contentAsString(each).contains(agentLink)
      }

      present.count(_ == true) mustBe 10
      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-authorisation/status")))
    }

    "loads between 1sec and 3sec due to early timeout on agent link" in {
      implicit lazy val app: Application = localGuiceApplicationBuilder()
        .configure(
          "feature.agent-client-authorisation.maxTps" -> 20,
          "feature.agent-client-authorisation.cache" -> true,
          "feature.agent-client-authorisation.enabled" -> true,
          "feature.agent-client-authorisation.timeoutInSec" -> 1
        )
        .build()

      server.stubFor(get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(ok(designatoryDetails)))
      server.stubFor(put(urlMatching(s"/keystore/pertax-frontend/.*"))
        .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString)))
      server.stubFor(get(urlEqualTo(s"/agent-client-authorisation/status"))
        .willReturn(
          aResponse()
            .withStatus(OK)
            .withBody(Json.toJson(AgentClientStatus(true, true, true)).toString)
            .withFixedDelay(5000)
        ))

      val request = FakeRequest(GET, url)

      val startTime = System.nanoTime
      val result: Future[(Result, Long)] = route(app, request).get.map { result =>
        (result, (System.nanoTime - startTime)/1000000.toLong)
      }

      val duration = result.map(_._2).futureValue

      duration mustBe <=[Long](3000)
      duration mustBe >=[Long](1000)
      getStatus(result.map(_._1)) mustBe OK
      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-authorisation/status")))
    }
  }

}
