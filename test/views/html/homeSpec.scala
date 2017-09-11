/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package views.html

import config.ConfigDecorator
import models._
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import services._
import uk.gov.hmrc.domain. SaUtr
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import util.{BaseSpec, DateTimeTools, Fixtures}

import scala.collection.JavaConversions._

class homeSpec extends BaseSpec {

  val messages: Messages = Messages.Implicits.applicationMessages

  trait SpecSetup {

    def withPaye: Boolean
    def withActivePaye: Boolean
    def withSa: Boolean
    def isGovernmentGateway: Boolean
    def isHighGG: Boolean
    def userHasPersonDetails: Boolean
    def principalName: Option[String]

    val messageInboxPartial = Html("")
    val configDecorator: ConfigDecorator = injected[ConfigDecorator]

    lazy val testPertaxUser = PertaxUser(
      authContext = AuthContext(
        Authority(
          uri = "/auth/oid/flastname",
          accounts = Accounts(
            paye = if (withPaye) Some(PayeAccount("/paye/" + Fixtures.fakeNino.nino, Fixtures.fakeNino)) else None,
            sa = if (withSa) Some(SaAccount("/sa/1111111111", SaUtr("1111111111"))) else None
          ),
          loggedInAt = None,
          previouslyLoggedInAt = Some(DateTimeTools.asDateFromUnixDateTime("1982-04-30T00:00:00")),
          credentialStrength = CredentialStrength.Strong,
          confidenceLevel = ConfidenceLevel.L0,
          userDetailsLink = Some("/userDetailsLink"),
          enrolments = Some("/userEnrolmentsLink"),
          ids = None,
          legacyOid = ""
        ),
      nameFromSession = principalName),
      if (isGovernmentGateway) UserDetails(UserDetails.GovernmentGatewayAuthProvider) else UserDetails(UserDetails.VerifyAuthProvider),
      personDetails = if (userHasPersonDetails) Some(Fixtures.buildPersonDetails) else None,
      isHighGG)

    lazy val document: Document = Jsoup.parse(views.html.home(
      Html(""),
      userResearchLinkUrl = None, Nil, Nil, Nil)
    (PertaxContext(FakeRequest("GET", "/test"),
      mockLocalPartialRetreiver,
      injected[ConfigDecorator],
      Some(testPertaxUser)),
      messages).toString)
  }

  trait OldStyleSpecSetup {}

  "Rendering home.scala.html" should {

    "show the users name and not 'Your account' when the user has details and is not a GG user" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show the users name and not 'Your account' when the user has no details but is a GG user" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = Some("Firstname Lastname")
      override val userHasPersonDetails: Boolean = false

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show 'Your account' and not the users name when the user has no details and is not a GG user" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      document.select("h1").exists(e => e.text == "Your account") shouldBe true
    }







    "show 'Update your address' for the Personal details section when the user has details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      document.select("a").exists(e => e.text == "Update your address") shouldBe true
    }

    "show 'You can't view or update your address right now' for the Personal details section when the user no has details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      document.select("p").exists(e => e.text == "You can't view or update your address right now") shouldBe true
    }



    "show the personal details block when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true
      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }






    "show manage prefs block if user is High GG and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Low GG and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Low GG and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is High GG and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Verify and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Verify and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "not show NINO section when user is low GG and with Paye " in new SpecSetup {
      override val withPaye: Boolean = true
      override val withActivePaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      Option(document.select(".nino").first).isDefined shouldBe false
    }



  }

}
