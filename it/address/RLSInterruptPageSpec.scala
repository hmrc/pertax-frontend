package address

import com.github.tomakehurst.wiremock.client.WireMock.{get, ok, urlEqualTo, status => _}
import models.admin._
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.{Application, inject}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, route, status => getStatus, _}
import services.admin.FeatureFlagService
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys

import scala.concurrent.Future

class RLSInterruptPageSpec extends IntegrationSpec {

  val url                    = "/personal-account"
  val mockFeatureFlagService = mock[FeatureFlagService]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      inject.bind[FeatureFlagService].toInstance(mockFeatureFlagService)
    )
    .build()

  when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
    .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, true)))
  when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
    .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, true)))
  when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
    .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, true)))
  when(mockFeatureFlagService.get(ArgumentMatchers.eq(PaperlessInterruptToggle)))
    .thenReturn(Future.successful(FeatureFlag(PaperlessInterruptToggle, true)))
  when(mockFeatureFlagService.get(ArgumentMatchers.eq(NationalInsuranceTileToggle)))
    .thenReturn(Future.successful(FeatureFlag(NationalInsuranceTileToggle, true)))
  when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxSummariesTileToggle)))
    .thenReturn(Future.successful(FeatureFlag(TaxSummariesTileToggle, true)))
  when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
    .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, true)))
  when(mockFeatureFlagService.get(ArgumentMatchers.eq(ChildBenefitSingleAccountToggle)))
    .thenReturn(Future.successful(FeatureFlag(ChildBenefitSingleAccountToggle, true)))

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

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")

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

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
        val result  = route(app, request)

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

        val request = FakeRequest(GET, url).withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")

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

      val request = FakeRequest(GET, url)
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1", SessionKeys.sessionId -> "2")
      val result  = route(app, request)

      result.map(getStatus) mustBe Some(OK)
    }

  }
}
