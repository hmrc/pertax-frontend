package controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import models.admin.{AgentClientAuthorisationToggle, AppleSaveAndViewNIToggle, PertaxBackendToggle, RlsInterruptToggle, SingleAccountCheckToggle}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.when
import play.api.Application
import play.api.i18n._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, route, writeableOf_AnyContentAsEmpty}
import testUtils.{FileHelper, IntegrationSpec}
import uk.gov.hmrc.http.SessionKeys
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag

import java.util.UUID
import scala.concurrent.Future

class PersonalDetailsControllerItSpec extends IntegrationSpec {
  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .configure(
      "microservice.services.preferences-frontend.port" -> server.port()
    )
    .build()

  val uuid: String                                    = UUID.randomUUID().toString
  val cacheMap: String                                = s"/keystore/pertax-frontend/session-$uuid/data/"
  val agentClientAuthorisationUrl: String             = "/agent-client-authorisation/status"
  val personDetailsUrl: String                        = s"/citizen-details/$generatedNino/designatory-details"
  implicit lazy val messageProvider: MessagesProvider = app.injector.instanceOf[MessagesProvider]
  val paperlessStatusTargetUrl: String                =
    "http://localhost/paperless/check-settings?returnUrl=encrypted1&returnLinkText=encrypted2"

  def paperlessStatusMessage(responseCode: String): String = s"""{
                                                       |  "status": {
                                                       |    "name": "${responseCode.toUpperCase()}",
                                                       |    "category": "INFO",
                                                       |    "text": "You chose to get your Self Assessment tax letters online"
                                                       |  },
                                                       |  "url": {
                                                       |    "link": "$paperlessStatusTargetUrl",
                                                       |    "text": "Check your settings"
                                                       |  }
                                                       |}""".stripMargin

  override def beforeEach(): Unit = {
    super.beforeEach()
    server.stubFor(
      get(urlEqualTo(personDetailsUrl))
        .willReturn(ok(FileHelper.loadFile("./it/resources/person-details.json")))
    )
    server.stubFor(
      put(urlEqualTo(cacheMap + "addressPageVisitedDto"))
        .willReturn(ok(s"""
                          |{
                          |	"id": "session-$uuid",
                          |	"data": {
                          |   "addressPageVisitedDto": {
                          |     "hasVisitedPage": true
                          |   }
                          |	},
                          |	"modifiedDetails": {
                          |		"createdAt": {
                          |			"$$date": 1400258561678
                          |		},
                          |		"lastUpdated": {
                          |			"$$date": 1400258561675
                          |		}
                          |	}
                          |}
                          |""".stripMargin))
    )
    server.stubFor(
      get(urlEqualTo(agentClientAuthorisationUrl))
        .willReturn(ok("""{
      |  "hasPendingInvitations": true,
      |  "hasInvitationsHistory": true,
      |  "hasExistingRelationships": true
      |  }""".stripMargin))
    )
  }

  "/personal-account/profile-and-settings" must {
    val url = "/personal-account/profile-and-settings"

    Map(
      "NEW_CUSTOMER"       -> List(
        Some("label.paperless_new_response"),
        Some("label.paperless_new_link"),
        Some("label.paperless_new_hidden")
      ),
      "BOUNCED_EMAIL"      -> List(
        Some("label.paperless_bounced_response"),
        Some("label.paperless_bounced_link"),
        Some("label.paperless_bounced_hidden")
      ),
      "EMAIL_NOT_VERIFIED" -> List(
        Some("label.paperless_unverified_response"),
        Some("label.paperless_unverified_link"),
        Some("label.paperless_unverified_hidden")
      ),
      "RE_OPT_IN"          -> List(Some("label.paperless_reopt_response"), Some("label.paperless_reopt_link"), None),
      "RE_OPT_IN_MODIFIED" -> List(
        Some("label.paperless_reopt_modified_response"),
        Some("label.paperless_reopt_modified_link"),
        None
      ),
      "PAPER"              -> List(
        Some("label.paperless_opt_out_response"),
        Some("label.paperless_opt_out_link"),
        Some("label.paperless_opt_out_hidden")
      ),
      "ALRIGHT"            -> List(
        Some("label.paperless_opt_in_response"),
        Some("label.paperless_opt_in_link"),
        Some("label.paperless_opt_in_hidden")
      ),
      "NO_EMAIL"           -> List(
        Some("label.paperless_no_email_response"),
        Some("label.paperless_no_email_link"),
        Some("label.paperless_no_email_hidden")
      )
    ).foreach { case (key, texts) =>
      s"show $key status for Contact preferences" in {
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(SingleAccountCheckToggle)))
          .thenReturn(Future.successful(FeatureFlag(SingleAccountCheckToggle, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(PertaxBackendToggle)))
          .thenReturn(Future.successful(FeatureFlag(PertaxBackendToggle, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(RlsInterruptToggle)))
          .thenReturn(Future.successful(FeatureFlag(RlsInterruptToggle, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AgentClientAuthorisationToggle)))
          .thenReturn(Future.successful(FeatureFlag(AgentClientAuthorisationToggle, false)))
        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AppleSaveAndViewNIToggle)))
          .thenReturn(Future.successful(FeatureFlag(AppleSaveAndViewNIToggle, false)))

        server.stubFor(
          get(urlMatching("/paperless/status.*"))
            .willReturn(ok(paperlessStatusMessage(key)))
        )

        val request         = FakeRequest(GET, url)
          .withSession(SessionKeys.authToken -> "Bearer 1", SessionKeys.sessionId -> s"session-$uuid")
        val result          = route(app, request)
        val includeExpected = texts.foldLeft(include(paperlessStatusTargetUrl)) { (accumulator, currentValue) =>
          currentValue match {
            case None        => accumulator
            case Some(label) => accumulator and include(messages(label))
          }
        }

        contentAsString(result.get) must includeExpected
      }
    }
  }

}
