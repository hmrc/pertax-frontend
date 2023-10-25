package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{SingleAccountCheckToggle, TaxComponentsToggle}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.http.Status._
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{AnyContentAsEmpty, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty, status => httpStatus}
import testUtils.IntegrationSpec
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.Future

class HomeControllerMarriageAllowanceISpec extends IntegrationSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "feature.breathing-space-indicator.enabled"      -> true,
      "feature.breathing-space-indicator.timeoutInSec" -> 4,
      "microservice.services.taxcalc.port"             -> server.port(),
      "microservice.services.tai.port"                 -> server.port()
    )
    .build()

  val url = s"/personal-account"

  def request: FakeRequest[AnyContentAsEmpty.type] = {
    val uuid = UUID.randomUUID().toString
    FakeRequest(GET, url).withSession(SessionKeys.sessionId -> uuid, SessionKeys.authToken -> "1")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    beforeEachHomeController()

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
      .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, isEnabled = true)))
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(TaxComponentsToggle)))
      .thenReturn(Future.successful(FeatureFlag(TaxComponentsToggle, isEnabled = true)))
  }

  "personal-account" must {
    "show correct message on the Marriage Allowance tile when transferring Personal Allowance to partner" in {

      val taxComponentsJson = Json
        .parse("""{
            |   "data" : [ {
            |      "componentType" : "MarriageAllowanceTransferred",
            |      "employmentId" : 12,
            |      "amount" : 12321,
            |      "inputAmount" : 12321,
            |      "description" : "Personal Allowance transferred to partner",
            |      "iabdCategory" : "Deduction"
            |   } ],
            |   "links" : [ ]
            |}""".stripMargin)
        .toString

      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )
      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )

      contentAsString(result).contains(
        Messages("label.you_currently_transfer_part_of_your_personal_allowance_to_your_partner")
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )
    }

    "show correct message on the Marriage Allowance tile when receiving Personal Allowance from partner" in {

      val taxComponentsJson = Json
        .parse("""{
            |   "data" : [ {
            |      "componentType" : "MarriageAllowanceReceived",
            |      "employmentId" : 12,
            |      "amount" : 12321,
            |      "inputAmount" : 12321,
            |      "description" : "Personal Allowance transferred to partner",
            |      "iabdCategory" : "Deduction"
            |   } ],
            |   "links" : [ ]
            |}""".stripMargin)
        .toString

      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )
      server.stubFor(
        put(urlMatching("/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      contentAsString(result).contains(
        Messages("label.your_partner_currently_transfers_part_of_their_personal_allowance_to_you")
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )
    }

    "show correct message on the Marriage Allowance tile when not transferring or receiving Personal Allowance from partner" in {

      val taxComponentsJson = Json
        .parse("""{
            |   "data" : [ {
            |      "componentType" : "OtherAllowance",
            |      "employmentId" : 12,
            |      "amount" : 12321,
            |      "inputAmount" : 12321,
            |      "description" : "Personal Allowance transferred to partner",
            |      "iabdCategory" : "Deduction"
            |   } ],
            |   "links" : [ ]
            |}""".stripMargin)
        .toString

      server.stubFor(
        get(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
          .willReturn(ok(taxComponentsJson))
      )
      server.stubFor(
        put(urlMatching("/keystore/pertax-frontend/.*"))
          .willReturn(ok(Json.toJson(CacheMap("id", Map.empty)).toString))
      )

      val result: Future[Result] = route(app, request).get
      httpStatus(result) mustBe OK

      contentAsString(result).contains(
        Messages("label.transfer_part_of_your_personal_allowance_to_your_partner_")
      ) mustBe true
      server.verify(
        1,
        getRequestedFor(urlEqualTo(s"/tai/$generatedNino/tax-account/${LocalDateTime.now().getYear}/tax-components"))
      )
    }
  }
}
