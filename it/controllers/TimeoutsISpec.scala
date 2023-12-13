package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import controllers.auth.AuthJourney
import controllers.auth.requests.UserRequest
import models.admin._
import models.{Person, PersonDetails}
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import org.mockito.MockitoSugar.mock
import play.api.Application
import play.api.http.Status.OK
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.UserRequestFixture.buildUserRequest
import testUtils.{ActionBuilderFixture, FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

class TimeoutsISpec extends IntegrationSpec {
  private val timeoutThresholdInMilliseconds  = 50
  private val delayInMilliseconds             = 200
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"                              -> true,
      "microservice.services.breathing-space-if-proxy.port"                    -> server.port(),
      "microservice.services.breathing-space-if-proxy.timeoutInMilliseconds"   -> timeoutThresholdInMilliseconds,
      "microservice.services.tai.port"                                         -> server.port(),
      "microservice.services.tai.timeoutInMilliseconds"                        -> timeoutThresholdInMilliseconds,
      "microservice.services.taxcalc.port"                                     -> server.port(),
      "microservice.services.taxcalc.timeoutInMilliseconds"                    -> timeoutThresholdInMilliseconds,
      "microservice.services.citizen-details.port"                             -> server.port(),
      "microservice.services.citizen-details.timeoutInMilliseconds"            -> timeoutThresholdInMilliseconds,
      "microservice.services.tcs-broker.port"                                  -> server.port(),
      "microservice.services.tcs-broker.timeoutInMilliseconds"                 -> timeoutThresholdInMilliseconds,
      "microservice.services.dfs-digital-forms-frontend.port"                  -> server.port(),
      "microservice.services.dfs-digital-forms-frontend.timeoutInMilliseconds" -> timeoutThresholdInMilliseconds
    )
    .build()

  private val dummyContent      = "my body content"
  private val breathingSpaceUrl = s"/$generatedNino/memorandum"
  private val taxComponentsUrl  = s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"
  private val taxCalcUrl        = s"/taxcalc/$generatedNino/reconciliations"
  private val citizenDetailsUrl = s"/citizen-details/$generatedNino/designatory-details"
  private val dfsPartialNinoUrl = "/digital-forms/forms/personal-tax/national-insurance/catalogue"
  private val dfsPartialSAUrl   = "/digital-forms/forms/personal-tax/self-assessment/catalogue"

  private val personDetails: PersonDetails =
    PersonDetails(
      Person(
        Some("Firstname"),
        Some("Middlename"),
        Some("Lastname"),
        None,
        None,
        None,
        None,
        None,
        None
      ),
      None,
      None
    )

  private def homePageGET: Future[Result] = route(
    app,
    FakeRequest(GET, "/personal-account")
      .withSession(SessionKeys.sessionId -> UUID.randomUUID().toString, SessionKeys.authToken -> "1")
  ).get

  private def getHomePageWithAllTimeouts: Future[Result] = {
    server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))
    server.stubFor(get(urlMatching("/messages/count.*")).willReturn(ok("{}")))
    server.stubFor(get(urlPathEqualTo(breathingSpaceUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
    server.stubFor(get(urlEqualTo(taxComponentsUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
    server.stubFor(get(urlPathEqualTo(taxCalcUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
    server.stubFor(get(urlPathEqualTo(citizenDetailsUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
    homePageGET
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(BreathingSpaceIndicatorToggle)))
      .thenReturn(Future.successful(FeatureFlag(BreathingSpaceIndicatorToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxcalcToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxcalcToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, isEnabled = true)))
  }

  "/personal-account" must {
    "hide breathing space related components when breathing space connector times out" in {
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe
        Messages("label.this.section.is") + " " + Messages("label.account_home")

      contentAsString(result).contains(Messages("label.breathing_space")) mustBe false
      contentAsString(result).contains(
        controllers.routes.InterstitialController.displayBreathingSpaceDetails.url
      ) mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(breathingSpaceUrl)))
    }

    "show generic marriage allowance content when tax components connector times out" in {
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe
        Messages("label.this.section.is") + " " + Messages("label.account_home")

      contentAsString(result).contains(
        Messages("label.transfer_part_of_your_personal_allowance_to_your_partner_")
      ) mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(taxComponentsUrl)))
    }

    "hide tax calc elements on page when tax calc connector times out" in {
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe
        Messages("label.this.section.is") + " " + Messages("label.account_home")

      contentAsString(result).contains(Messages("label.you_paid_the_right_amount_of_tax")) mustBe false
      contentAsString(result).contains(Messages("label.your_tax_has_not_been_calculated")) mustBe false
      contentAsString(result).contains(Messages("label.find_out_why_you_paid_too_much")) mustBe false
      contentAsString(result).contains(Messages("label.make_a_payment")) mustBe false
      server.verify(1, getRequestedFor(urlEqualTo(taxCalcUrl)))
    }

    "show no person name for citizen details when citizen details connector times out" in {
      val result            = getHomePageWithAllTimeouts
      val content: Document = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("hmrc-caption govuk-caption-xl").get(0).text() mustBe
        Messages("label.this.section.is") + " " + Messages("label.account_home")

      content.getElementsByClass("govuk-heading-xl").get(0).text().isEmpty mustBe true
      server.verify(1, getRequestedFor(urlEqualTo(citizenDetailsUrl)))
    }

    "show correct person name when citizen details connector does NOT time out" in {
      beforeEachHomeController(memorandum = false, matchingDetails = false)
      server.stubFor(get(urlPathEqualTo(breathingSpaceUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
      server.stubFor(get(urlEqualTo(taxComponentsUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
      server.stubFor(get(urlPathEqualTo(taxCalcUrl)).willReturn(aResponse.withFixedDelay(delayInMilliseconds)))
      server.stubFor(get(urlPathEqualTo(citizenDetailsUrl)).willReturn(ok(Json.toJson(personDetails).toString())))

      val result: Future[Result] = homePageGET
      val content                = Jsoup.parse(contentAsString(result))
      content.getElementsByClass("govuk-heading-xl").get(0).text() mustBe "Firstname Lastname"
      server.verify(1, getRequestedFor(urlEqualTo(citizenDetailsUrl)))
    }
  }

  "/personal-account/your-address/tax-credits-choice" must {
    "render the do you get tax credits page when tax credits broker connector times out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressTaxCreditsBrokerCallToggle)))
        .thenReturn(Future.successful(FeatureFlag(AddressTaxCreditsBrokerCallToggle, isEnabled = true)))
      server.stubFor(
        get(urlPathMatching("/keystore/pertax-frontend/.*"))
          .willReturn(
            aResponse()
              .withStatus(OK)
              .withBody(
                """{"id": "session-id","data": {"addressPageVisitedDto": {"hasVisitedPage": true}}}""".stripMargin
              )
          )
      )
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )
      server.stubFor(
        get(urlPathEqualTo(s"/tcs/$generatedNino/exclusion"))
          .willReturn(aResponse.withFixedDelay(delayInMilliseconds))
      )

      val request = FakeRequest(GET, "/personal-account/your-address/tax-credits-choice")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result  = route(app, request)

      result.get.futureValue.header.status mustBe OK
      contentAsString(result.get).contains("Do you get tax credits?") mustBe true
    }
  }

  "/personal-account/national-insurance-summary" must {
    "display no NI content when partial connector times out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )
      server.stubFor(
        get(urlEqualTo(dfsPartialNinoUrl))
          .willReturn(aResponse.withFixedDelay(delayInMilliseconds))
      )

      val request   = FakeRequest(GET, "/personal-account/national-insurance-summary")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("national_insurance").text()
      niContent.isEmpty mustBe true
    }

    "display NI content when partial does not time out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))
      server.stubFor(
        get(urlEqualTo(s"/citizen-details/$generatedNino/designatory-details"))
          .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
      )
      server.stubFor(
        get(urlEqualTo(dfsPartialNinoUrl))
          .willReturn(ok(dummyContent))
      )

      val request   = FakeRequest(GET, "/personal-account/national-insurance-summary")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("national_insurance").text()
      niContent mustBe dummyContent
    }
  }

  // TODO: 8107: The below more complex because checks are done on auth related stuff in controller
  "/personal-account/self-assessment-summary" must {
    "display no SA content when partial times out" in {
      server.stubFor(post(urlEqualTo("/auth/authorise")).willReturn(ok(authResponse)))


//      val mockAuthJourney: AuthJourney                          = mock[AuthJourney]
//      when(mockAuthJourney.authWithPersonalDetails).thenReturn(new ActionBuilderFixture {
//        override def invokeBlock[A](request: Request[A], block: UserRequest[A] => Future[Result]): Future[Result] =
//          block(
//            buildUserRequest(request = request)
//          )
//      })
      
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(dfsPartialSAUrl))
          .willReturn(aResponse.withFixedDelay(100))
      )

      val request   = FakeRequest(GET, "/personal-account/self-assessment-summary")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("self-assessment-forms").text()
      niContent.isEmpty mustBe true
    }

    "display SA content when partial does not time out" in {
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
        .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

      server.stubFor(
        get(urlEqualTo(dfsPartialSAUrl))
          .willReturn(ok(dummyContent))
      )

      val request   = FakeRequest(GET, "/personal-account/self-assessment-summary")
        .withSession(SessionKeys.sessionId -> "1", SessionKeys.authToken -> "1")
      val result    = route(app, request).get
      val content   = Jsoup.parse(contentAsString(result))
      val niContent = content.getElementById("self-assessment-forms").text()
      niContent mustBe dummyContent
    }
  }
}
