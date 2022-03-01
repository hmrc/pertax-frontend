package utils

import com.github.tomakehurst.wiremock.client.WireMock.{ok, post, get, urlEqualTo}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.Generator

trait IntegrationSpec extends AnyWordSpec with GuiceOneAppPerSuite with WireMockHelper with ScalaFutures with Matchers {

  implicit override val patienceConfig = PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(100, Millis)))

  val configTaxYear = 2021
  val testTaxYear = configTaxYear - 1
  val generatedNino = new Generator().nextNino

  val authResponse =
    s"""
       |{
       |    "confidenceLevel": 200,
       |    "nino": "$generatedNino",
       |    "name": {
       |        "name": "John",
       |        "lastName": "Smith"
       |    },
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
       |    "allEnrolments": [],
       |    "affinityGroup": "Individual",
       |    "credentialStrength": "strong"
       |}
       |""".stripMargin

  val citizenResponse =
    s"""|
       |{
       |  "name": {
       |    "current": {
       |      "firstName": "John",
       |      "lastName": "Smith"
       |    },
       |    "previous": []
       |  },
       |  "ids": {
       |    "nino": "$generatedNino"
       |  },
       |  "dateOfBirth": "11121971"
       |}
       |""".stripMargin

  override def fakeApplication = GuiceApplicationBuilder()
    .configure(
      "microservice.services.citizen-details.port" -> server.port(),
      "microservice.services.auth.port" -> server.port()
    )
    .build()

  override def beforeEach() = {
    super.beforeEach()
    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))

    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
  }
}