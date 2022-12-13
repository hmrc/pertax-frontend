package address

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, urlEqualTo, urlMatching, status => _}
import play.api.Application
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status => getStatus, _}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.{HeaderNames, SessionKeys}

class RLSInterruptPageSpec extends IntegrationSpec {

  val url = s"/personal-account"

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  "personal-account" must {
    "show rls interrupt" when {
      "RLS indicator is set for main address for non tax credit user" in {
        val designatoryDetails =
          """|
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
             |    "nino" : "TW189213B",
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
             |    "status": 1
             |  }
             |}
             |""".stripMargin

        server.stubFor(
          get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
            .willReturn(ok(designatoryDetails))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")

        val result = route(app, request)

        result.map(getStatus) mustBe Some(SEE_OTHER)
        result.map(redirectLocation) mustBe Some(Some("/personal-account/update-your-address"))
      }

      "RLS indicator is set for correspondence address for non tax credit user" in {
        val designatoryDetails =
          """|
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
             |    "nino" : "TW189213B",
             |    "deceased" : false
             |  },
             |  "correspondenceAddress" : {
             |    "line1" : "26 FARADAY DRIVE",
             |    "line2" : "PO BOX 45",
             |    "line3" : "LONDON",
             |    "postcode" : "CT1 1RQ",
             |    "startDate": "2009-08-29",
             |    "country" : "GREAT BRITAIN",
             |    "type" : "Correspondence",
             |    "status": 1
             |  }
             |}
             |""".stripMargin

        server.stubFor(
          get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
            .willReturn(ok(designatoryDetails))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")

        val result = route(app, request)

        result.map(getStatus) mustBe Some(SEE_OTHER)
        result.map(redirectLocation) mustBe Some(Some("/personal-account/update-your-address"))
      }

      "RLS indicator is set for both main address and correspondence address for non tax credit user" in {
        val designatoryDetails =
          """|
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
             |    "nino" : "TW189213B",
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
             |    "status": 1
             |  },
             |  "correspondenceAddress" : {
             |    "line1" : "26 FARADAY DRIVE",
             |    "line2" : "PO BOX 45",
             |    "line3" : "LONDON",
             |    "postcode" : "CT1 1RQ",
             |    "startDate": "2009-08-29",
             |    "country" : "GREAT BRITAIN",
             |    "type" : "Correspondence",
             |    "status": 1
             |  }
             |}
             |""".stripMargin

        server.stubFor(
          get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
            .willReturn(ok(designatoryDetails))
        )

        val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")

        val result = route(app, request)

        result.map(getStatus) mustBe Some(SEE_OTHER)
        result.map(redirectLocation) mustBe Some(Some("/personal-account/update-your-address"))
      }

    }
  }

  "not show rls interrupt" when {
    "no rls indicator for non tax credit user" in {
      val designatoryDetails =
        """|
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
           |    "nino" : "TW189213B",
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
           |  },
           |  "correspondenceAddress" : {
           |     "line1" : "26 FARADAY DRIVE",
           |     "line2" : "PO BOX 45",
           |     "line3" : "LONDON",
           |     "postcode" : "CT1 1RQ",
           |     "startDate": "2009-08-29",
           |     "country" : "GREAT BRITAIN",
           |     "type" : "Correspondence",
           |     "status": 0
           |  }
           |}
           |""".stripMargin

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(designatoryDetails))
      )

      val request = FakeRequest(GET, url).withSession(SessionKeys.authToken -> "Bearer 1")

      val result = route(app, request)

      result.map(getStatus) mustBe Some(OK)
    }

  }
}
