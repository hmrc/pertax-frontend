package address

import com.github.tomakehurst.wiremock.client.WireMock._
import models.AgentClientStatus
import models.admin.{AgentClientAuthorisationToggle, AppleSaveAndViewNIToggle, NpsOutageToggle, RlsInterruptToggle, SingleAccountCheckToggle}
import org.mockito.{ArgumentMatchers, MockitoSugar}
import play.api.Application
import play.api.http.Status.OK
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => getStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import java.util.UUID
import scala.concurrent.Future
import play.api.inject.bind
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

class PersonalDetailsControllerSpec extends IntegrationSpec with MockitoSugar {
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

  lazy val mockFeatureFlagService = mock[FeatureFlagService]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[FeatureFlagService].toInstance(mockFeatureFlagService)
    )
    .configure(
      "feature.agent-client-authorisation.maxTps"       -> 100,
      "feature.agent-client-authorisation.cache"        -> true,
      "feature.agent-client-authorisation.timeoutInSec" -> 1
    )
    .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
      .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
      .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, false)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AppleSaveAndViewNIToggle)))
      .thenReturn(Future.successful(FeatureFlag(AppleSaveAndViewNIToggle, false)))
  }

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  val url       = s"/personal-account/profile-and-settings"
  val agentLink = "/manage-your-tax-agents"

  "your-profile" must {
    "show manage your agent link successfully" in {

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(designatoryDetails))
      )
      server.stubFor(
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )
      server.stubFor(
        get(urlEqualTo(s"/agent-client-authorisation/status"))
          .willReturn(ok(Json.toJson(AgentClientStatus(true, true, true)).toString))
      )

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientAuthorisationToggle)))
        .thenReturn(Future.successful(FeatureFlag(AgentClientAuthorisationToggle, true)))

      val result: Future[Result] = route(app, request).get
      contentAsString(result).contains(agentLink)
      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-authorisation/status")))
    }

    "show manage your agent link in 2 request but only one request to backend due to cache" in {

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientAuthorisationToggle)))
        .thenReturn(Future.successful(FeatureFlag(AgentClientAuthorisationToggle, true)))

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(designatoryDetails))
      )
      server.stubFor(
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )
      server.stubFor(
        get(urlEqualTo(s"/agent-client-authorisation/status"))
          .willReturn(ok(Json.toJson(AgentClientStatus(true, true, true)).toString))
      )

      val repeatRequest = request

      val result = (for {
        req1 <- route(app, repeatRequest).get
        req2 <- route(app, repeatRequest).get
      } yield (req1, req2)).futureValue

      contentAsString(Future.successful(result._1)).contains(agentLink)
      contentAsString(Future.successful(result._2)).contains(agentLink)

      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-authorisation/status")))
    }

    "loads between 1sec and 3sec due to early timeout on agent link" in {

      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientAuthorisationToggle)))
        .thenReturn(Future.successful(FeatureFlag(AgentClientAuthorisationToggle, true)))

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(designatoryDetails))
      )
      server.stubFor(
        put(urlMatching(s"/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )
      server.stubFor(
        get(urlEqualTo(s"/agent-client-authorisation/status"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(Json.toJson(AgentClientStatus(true, true, true)).toString)
              .withFixedDelay(5000)
          )
      )

      val startTime                      = System.nanoTime
      val result: Future[(Result, Long)] = route(app, request).get.map { result =>
        (result, (System.nanoTime - startTime) / 1000000.toLong)
      }

      val duration = result.map(_._2).futureValue

      duration mustBe <=[Long](3000)
      duration mustBe >=[Long](1000)
      getStatus(result.map(_._1)) mustBe OK
      server.verify(1, getRequestedFor(urlEqualTo("/agent-client-authorisation/status")))
    }
  }

}
