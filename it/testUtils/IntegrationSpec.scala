package testUtils

import akka.Done
import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api
import play.api.cache.AsyncCacheApi
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.Generator

import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

trait IntegrationSpec extends AnyWordSpec with GuiceOneAppPerSuite with WireMockHelper with ScalaFutures with Matchers {

  val mockCacheApi: AsyncCacheApi = new AsyncCacheApi {
    override def set(key: String, value: Any, expiration: Duration): Future[Done] = Future.successful(Done)

    override def remove(key: String): Future[Done] = Future.successful(Done)

    override def getOrElseUpdate[A](key: String, expiration: Duration)(orElse: => Future[A])(implicit
      evidence$1: ClassTag[A]
    ): Future[A] = orElse

    override def get[T](key: String)(implicit evidence$2: ClassTag[T]): Future[Option[T]] = Future.successful(None)

    override def removeAll(): Future[Done] = Future.successful(Done)
  }

  implicit override val patienceConfig = PatienceConfig(scaled(Span(15, Seconds)), scaled(Span(100, Millis)))

  lazy val messagesApi                 = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  val generatedNino = new Generator().nextNino
  val generatedUtr  = new Generator().nextAtedUtr.utr

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

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        api.inject.bind[AsyncCacheApi].toInstance(mockCacheApi)
      )
      .configure(
        "microservice.services.citizen-details.port"            -> server.port(),
        "microservice.services.auth.port"                       -> server.port(),
        "microservice.services.message-frontend.port"           -> server.port(),
        "microservice.services.agent-client-authorisation.port" -> server.port(),
        "microservice.services.cachable.session-cache.port"     -> server.port(),
        "microservice.services.breathing-space-if-proxy.port"   -> server.port()
      )

  override def beforeEach() = {
    super.beforeEach()
    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
  }
}
