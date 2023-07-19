package testUtils

import akka.Done
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, ok, post, serverError, urlEqualTo, urlMatching}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import models.admin.NpsOutageToggle
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.{reset, when}
import org.mockito.MockitoSugar.mock
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api
import play.api.cache.AsyncCacheApi
import play.api.http.Status.NOT_FOUND
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService

import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import play.api.inject.bind
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

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

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(scaled(Span(15, Seconds)), scaled(Span(100, Millis)))

  lazy val messagesApi: MessagesApi              = app.injector.instanceOf[MessagesApi]
  implicit lazy val messages: Messages           = MessagesImpl(Lang("en"), messagesApi)
  implicit lazy val ec: ExecutionContext         = app.injector.instanceOf[ExecutionContext]
  val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  val generatedNino: Nino  = new Generator().nextNino
  val generatedUtr: String = new Generator().nextAtedUtr.utr

  val authResponse: String =
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

  val citizenResponse: String =
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
        api.inject.bind[AsyncCacheApi].toInstance(mockCacheApi),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      )
      .configure(
        "microservice.services.citizen-details.port"            -> server.port(),
        "microservice.services.auth.port"                       -> server.port(),
        "microservice.services.message-frontend.port"           -> server.port(),
        "microservice.services.agent-client-authorisation.port" -> server.port(),
        "microservice.services.cachable.session-cache.port"     -> server.port(),
        "microservice.services.breathing-space-if-proxy.port"   -> server.port()
      )

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(NpsOutageToggle)))
      .thenReturn(Future.successful(FeatureFlag(NpsOutageToggle, false)))
  }

  def beforeEachHomeController(
    auth: Boolean = true,
    memorandum: Boolean = true,
    matchingDetails: Boolean = true
  ): StubMapping = {
    if (auth) {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    }
    if (memorandum) {
      server.stubFor(get(urlMatching(s"/$generatedNino/memorandum")).willReturn(serverError()))
    }
    if (matchingDetails) {
      server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    }
    server.stubFor(
      get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )
    server.stubFor(
      get(urlMatching("/keystore/pertax-frontend/.*"))
        .willReturn(aResponse().withStatus(NOT_FOUND))
    )
    server.stubFor(
      get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
        .willReturn(serverError())
    )
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
  }
}
