package connectors

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.{HeaderNames, MimeTypes, Status}
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait ConnectorSpec extends AnyWordSpec with GuiceOneAppPerSuite with Status with HeaderNames with MimeTypes with Matchers with ScalaFutures {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  protected val server: WireMockServer

  implicit def app(confStrings: Map[String, String]): Application = new GuiceApplicationBuilder()
    .configure(confStrings)
    .build()

  def mockConnector[A <: Connector](connector : Class[A]): A = app.injector.instanceOf(connector)

  def stubGet(url: String, responseStatus: Int, responseBody: Option[String]): StubMapping = server.stubFor {
    val baseResponse = aResponse().withStatus(responseStatus).withHeader(CONTENT_TYPE, JSON)
    val response = responseBody.fold(baseResponse)(body => baseResponse.withBody(body))

    get(url).willReturn(response)
  }

  def stubPut(url: String, responseStatus: Int, requestBody: Option[String], responseBody: Option[String]): StubMapping = server.stubFor{
    val baseResponse = aResponse().withStatus(responseStatus).withHeader(CONTENT_TYPE, JSON)
    val response = responseBody.fold(baseResponse)(body => baseResponse.withBody(body))

    requestBody.fold(put(url).willReturn(response))(
      requestBody => put(url).withRequestBody(equalToJson(requestBody)).willReturn(response)
    )
  }

  def stubPost(url: String, responseStatus: Int, requestBody: Option[String], responseBody: Option[String]): StubMapping = server.stubFor {
    val baseResponse = aResponse().withStatus(responseStatus).withHeader(CONTENT_TYPE, JSON)
    val response = responseBody.fold(baseResponse)(body => baseResponse.withBody(body))

    requestBody.fold(put(url).willReturn(response))(
      requestBody => put(url).withRequestBody(equalToJson(requestBody)).willReturn(response)
    )
  }

  def stubDelete(url: String, responseStatus: Int, responseBody: Option[String]): StubMapping = server.stubFor {
    val baseResponse = aResponse().withStatus(responseStatus).withHeader(CONTENT_TYPE, JSON)
    val response = responseBody.fold(baseResponse)(body => baseResponse.withBody(body))

   delete(url).willReturn(response)
  }

  def verifyCorrelationIdHeader(requestPattern: RequestPatternBuilder): Unit =
    server.verify(
      requestPattern.withHeader(
        "Correlation-Id",
        matching("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}")
      )
    )

}
