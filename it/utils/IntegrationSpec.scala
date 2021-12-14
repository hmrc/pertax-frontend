package utils

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, urlEqualTo}
import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models.{ActivatedOnlineFilerSelfAssessmentUser, Address, Person, PersonDetails, SelfAssessmentUserType, UserDetails, UserName}
import org.joda.time.LocalDate
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.i18n.{Lang, Messages, MessagesApi, MessagesImpl}
import play.api.mvc.{AnyContentAsEmpty, Request}
import play.api.test.{FakeRequest, Injecting}
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name}
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.renderer.TemplateRenderer

class IntegrationSpec extends AnyWordSpec with GuiceOneAppPerSuite with Matchers with WireMockHelper with ScalaFutures with IntegrationPatience with Injecting with BeforeAndAfterEach {

  val generatedNino = new Generator().nextNino

  val generatedSaUtr = new Generator().nextAtedUtr

  lazy val messages = inject[Messages]

  override def beforeEach() = {

    super.beforeEach()

    val authResponse =
      s"""
         |{
         |    "confidenceLevel": 200,
         |    "credentialStrength": "strong",
         |    "nino": "$generatedNino",
         |    "saUtr": "$generatedSaUtr",
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
         |    "allEnrolments": []
         |}
         |""".stripMargin

    server.stubFor(post(urlEqualTo("/auth/authorise"))
      .willReturn(aResponse().withBody(authResponse)))
  }

  val fakePersonDetails: PersonDetails = PersonDetails(
    Person(
      Some("John"),
      None,
      Some("Doe"),
      Some("JD"),
      Some("Mr"),
      None,
      Some("M"),
      Some(LocalDate.parse("1975-12-03")),
      Some(generatedNino)),
    Some(Address(
      Some("1 Fake Street"),
      Some("Fake Town"),
      Some("Fake City"),
      Some("Fake Region"),
      None,
      Some("AA1 1AA"),
      None,
      Some(new LocalDate(2015, 3, 15)),
      None,
      Some("Residential")
    )),
    None
  )

    def buildUserRequest[A](
                             nino: Option[Nino] = Some(generatedNino),
                             userName: Option[UserName] = Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
                             saUser: SelfAssessmentUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
                             credentials: Credentials = Credentials("", UserDetails.GovernmentGatewayAuthProvider),
                             confidenceLevel: ConfidenceLevel = ConfidenceLevel.L200,
                             personDetails: Option[PersonDetails] = Some(fakePersonDetails),
                             trustedHelper: Option[TrustedHelper] = None,
                             profile: Option[String] = None,
                             messageCount: Option[Int] = None,
                             request: Request[A] = FakeRequest().asInstanceOf[Request[A]]
                           ): UserRequest[A] =
      UserRequest(
        nino,
        userName,
        saUser,
        credentials,
        confidenceLevel,
        personDetails,
        trustedHelper,
        profile,
        messageCount,
        None,
        None,
        request
      )

    implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest()

}
