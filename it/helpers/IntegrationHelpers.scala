package helpers

import java.util.UUID

import config.ConfigDecorator
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import services.http.SimpleHttp
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.play.test.UnitSpec

import scala.util.Random

trait IntegrationHelpers extends UnitSpec with GuiceOneAppPerSuite {

  lazy val config = app.injector.instanceOf[ConfigDecorator]
  lazy val http = app.injector.instanceOf[SimpleHttp]

  implicit val hc = HeaderCarrier(sessionId = Some(SessionId(s"session-${UUID.randomUUID()}")))

  def credId = Random.alphanumeric.take(8).mkString

  def jsonPayload(nino: String) = Json.parse(s"""{
                                                |  "credId": "$credId",
                                                |  "affinityGroup": "Individual",
                                                |  "confidenceLevel": 200,
                                                |  "credentialStrength": "strong",
                                                |  "enrolments": [
                                                |  {
                                                |      "key": "IR-SA",
                                                |      "identifiers": [
                                                |        {
                                                |          "key": "UTR",
                                                |          "value": "********"
                                                |        }
                                                |      ],
                                                |      "state": "Activated"
                                                |    }
                                                |  ],
                                                |  "gatewayToken": "SomeToken",
                                                |  "groupIdentifier": "SomeGroupIdentifier",
                                                |  "nino": "$nino"
                                                |}""".stripMargin)

  def getBearerToken(nino: String): String =
    await(
      http.post(s"${config.authLoginApiService}/government-gateway/session/login", jsonPayload(nino))(
        response => response.header("AUTHORIZATION").getOrElse(""),
        e => fail(e.getMessage)
      )
    )

  def hcWithBearer(nino: String): HeaderCarrier = hc.withExtraHeaders(("AUTHORIZATION", getBearerToken(nino)))
}
