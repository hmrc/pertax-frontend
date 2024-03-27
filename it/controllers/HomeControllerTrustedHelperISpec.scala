package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.PertaxBackendToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.Helpers.{GET, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import play.api.test.FakeRequest
import testUtils.IntegrationSpec
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.annotation.tailrec
import scala.concurrent.Future

class HomeControllerTrustedHelperISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"   -> false,
      "microservice.services.taxcalc-frontend.port" -> server.port(),
      "microservice.services.tai.port"              -> server.port(),
      "microservice.services.pertax.port"           -> server.port()
    )
    .build()

  val url          = s"/personal-account"
  val uuid: String = UUID.randomUUID().toString
  val pertaxUrl    = s"/pertax/$generatedNino/authorise"
  val authUrl      = s"/auth/authorise"

  @tailrec
  private def generateHelperNino: Nino = {
    val nino = new Generator().nextNino
    if (nino == generatedNino)
      generateHelperNino
    else {
      nino
    }
  }

  val generatedHelperNino: Nino = generateHelperNino

  val authTrustedHelperResponse: String =
    s"""
       |{
       |    "confidenceLevel": 200,
       |    "nino": "$generatedNino",
       |    "name": {
       |        "name": "John",
       |        "lastName": "Smith"
       |    },
       |    "trustedHelper": {
       |        "principalName": "principal",
       |        "attorneyName": "attorney",
       |        "returnLinkUrl": "returnLink",
       |        "principalNino": "$generatedHelperNino"
       |     },
       |    "loginTimes": {
       |        "currentLogin": "2021-06-07T10:52:02.594Z",
       |        "previousLogin": null
       |    },
       |    "optionalCredentials": {
       |        "providerId": "4911434741952698",
       |        "providerType": "GovernmentGateway"
       |    },
       |    "authProviderId": {
       |        "ggCredId": "xyz"
       |    },
       |    "externalId": "testExternalId",
       |    "allEnrolments": [
       |       {
       |          "key":"HMRC-PT",
       |          "identifiers": [
       |             {
       |                "key":"NINO",
       |                "value": "$generatedNino"
       |             }
       |          ]
       |       }
       |    ],
       |    "affinityGroup": "Individual",
       |    "credentialStrength": "strong"
       |}
       |""".stripMargin

  def request: FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController(memorandum = false)

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
      .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, isEnabled = true)))

    server.stubFor(
      get(urlPathEqualTo(pertaxUrl))
        .willReturn(ok("""{"code":"ACCESS_GRANTED","message":"Access granted"}"""))
    )

    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authTrustedHelperResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedHelperNino")).willReturn(ok(citizenResponse)))
  }

  "personal-account" must {

    "show the home page when helping someone" in {

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK
      server.verify(1, getRequestedFor(urlEqualTo(pertaxUrl)))
      server.verify(1, getRequestedFor(urlEqualTo(s"/citizen-details/nino/$generatedHelperNino")))

    }
  }
}
