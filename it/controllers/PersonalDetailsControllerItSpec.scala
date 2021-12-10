package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import config.LocalTemplateRenderer
import org.jsoup.nodes.Document
import play.api.Application
import play.api.http.Status.{BAD_REQUEST, IM_A_TEAPOT, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, SERVICE_UNAVAILABLE}
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import uk.gov.hmrc.renderer.TemplateRenderer
import utils.{FileHelper, IntegrationSpec}
import org.jsoup.Jsoup
import org.scalatest.Assertion

class PersonalDetailsControllerItSpec extends IntegrationSpec {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "microservice.services.auth.port" -> server.port(),
      "microservice.services.citizen-details.port" -> server.port(),
      "microservice.services.tcs-broker.port" -> 7901,
      "microservice.services.tcs-broker.host" -> "127.0.0.1",
    )
    .build()

  def assertContainsLink(doc: Document, text: String, href: String): Assertion =
    assert(
      doc.getElementsContainingText(text).attr("href").contains(href),
      s"\n\nLink $href was not rendered on the page\n")

  "/personal-account/your-profile" must {
    val url = "/personal-account/your-profile"

    val tcsBrokerUrl = s"/tcs/AA055075C/dashboard-data"

    val citizenDetailsUrl = s" /citizen-details/nino/AA055075C"

    val personDetailsUrl = s" /citizen-details/AA055075C/designatory-details"

    "return an OK response and contain a link to TCS address change if the user has tax credits" in {

      server.stubFor(
        get(urlEqualTo(tcsBrokerUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/dashboard-data.json")))
      )

      server.stubFor(
        get(urlEqualTo(citizenDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/citizen-details.json")))
      )

      server.stubFor(
        get(urlEqualTo(personDetailsUrl))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )

      val request = FakeRequest(GET, url)

      val result = route(app, request)

      val content = result.map(contentAsString) match {
        case Some(value) => Jsoup.parse(value)
        case _ => new Document("")
      }

      result.get.futureValue.header.status mustBe OK

      assertContainsLink(content, "Your main address", "/personal-account/your-address/tax-credits-choice")
    }

//    "return an OK response and contain a link to Pertax address change if the user does not has tax credits" in {
//
//      server.stubFor(
//        get(urlEqualTo(tcsBrokerUrl))
//          .willReturn(aResponse().withStatus(NOT_FOUND))
//      )
//
//      server.stubFor(
//        get(urlEqualTo(citizenDetailsUrl))
//          .willReturn(aResponse().withStatus(OK))
//      )
//
//      server.stubFor(
//        get(urlEqualTo(designatoryDetailsUrl))
//          .willReturn(aResponse().withStatus(OK))
//      )
//
//      val request = FakeRequest(GET, url)
//
//      val result = route(app, request)
//
//      val content = result.map(contentAsString) match {
//        case Some(value) => Jsoup.parse(value)
//        case _ => new Document("")
//      }
//
//      result.get.futureValue.header.status mustBe OK
//
//      assertContainsLink(content, "Your main address", "/personal-account/your-address/residential/do-you-live-in-the-uk")
//    }
//
//    List(
//      BAD_REQUEST,
//      IM_A_TEAPOT,
//      INTERNAL_SERVER_ERROR,
//      SERVICE_UNAVAILABLE
//    ).foreach { response =>
//      s"return an OK response and contain a link to Pertax address change if the call to Tax Credits fails with a $response" in {
//
//        server.stubFor(
//          get(urlEqualTo(tcsBrokerUrl))
//            .willReturn(aResponse().withStatus(response))
//        )
//
//        server.stubFor(
//          get(urlEqualTo(citizenDetailsUrl))
//            .willReturn(aResponse().withStatus(OK))
//        )
//
//        server.stubFor(
//          get(urlEqualTo(designatoryDetailsUrl))
//            .willReturn(aResponse().withStatus(OK))
//        )
//
//        val request = FakeRequest(GET, url)
//
//        val result = route(app, request)
//
//        val content = result.map(contentAsString) match {
//          case Some(value) => Jsoup.parse(value)
//          case _ => new Document("")
//        }
//
//        result.get.futureValue.header.status mustBe OK
//
//        assertContainsLink(content, "Your main address", "/personal-account/your-address/residential/do-you-live-in-the-uk")
//      }
//    }
  }
}
