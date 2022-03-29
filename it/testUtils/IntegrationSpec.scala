package testUtils

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, post, urlEqualTo, urlMatching}
import config.ConfigDecorator
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl, MessagesProvider}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import repositories.EditAddressLockRepository
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.play.partials.FormPartialRetriever
import uk.gov.hmrc.renderer.TemplateRenderer
import testUtils.MockTemplateRenderer

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

trait IntegrationSpec extends AnyWordSpec with GuiceOneAppPerSuite with WireMockHelper with ScalaFutures with Matchers {

  implicit override val patienceConfig = PatienceConfig(scaled(Span(15, Seconds)), scaled(Span(100, Millis)))

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

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.citizen-details.port" -> server.port(),
        "microservice.services.auth.port" -> server.port(),
        "microservice.services.message-frontend.port" -> server.port(),
        "microservice.services.agent-client-authorisation.port" -> server.port(),
        "microservice.services.cachable.session-cache.port" -> server.port()
      ).overrides(
      api.inject.bind[TemplateRenderer].to(testUtils.MockTemplateRenderer)
    )

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit lazy val ec = app.injector.instanceOf[ExecutionContext]

  lazy val config = app.injector.instanceOf[ConfigDecorator]

  implicit lazy val templateRenderer = app.injector.instanceOf[TemplateRenderer]

  def injected[T](c: Class[T]): T = app.injector.instanceOf(c)

  def injected[T](implicit evidence: ClassTag[T]): T = app.injector.instanceOf[T]

  implicit lazy val messageProvider = app.injector.instanceOf[MessagesProvider]
  lazy val messagesApi = app.injector.instanceOf[MessagesApi]

  implicit lazy val messages: Messages = MessagesImpl(Lang("en"), messagesApi)

  override def beforeEach() = {
    super.beforeEach()
    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlEqualTo(s"/citizen-details/nino/$generatedNino")).willReturn(ok(citizenResponse)))
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
  }
}
