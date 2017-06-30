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
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import services._
import uk.gov.hmrc.domain.{Nino, SaUtr}
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import util.{BaseSpec, DateTimeTools, Fixtures}

import scala.collection.JavaConversions._

class homeSpec extends BaseSpec {

  val messages = Messages.Implicits.applicationMessages

  trait SpecSetup {

    def withPaye: Boolean
    def withSa: Boolean
    def isGovernmentGateway: Boolean
    def isHighGG: Boolean
    def userHasPersonDetails: Boolean
    def principalName: Option[String]

    val messageInboxPartial = Html("")
    val configDecorator = injected[ConfigDecorator]

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
  }

  trait OldStyleSpecSetup {}

  "Rendering home.scala.html" should {

    "show the users name and not 'Your account' when the user has details and is not a GG user" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show the users name and not 'Your account' when the user has no details but is a GG user" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = Some("Firstname Lastname")
      override val userHasPersonDetails: Boolean = false

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show 'Your account' and not the users name when the user has no details and is not a GG user" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h1").exists(e => e.text == "Your account") shouldBe true
    }

    "show 'Check your tax codes and an estimate of the Income Tax you'll pay.' for the PAYE section when the user has details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h3 a").exists(e => e.text == "Pay As You Earn (PAYE)") shouldBe true
      document.select("p").exists(e => e.text == "Check your tax codes and an estimate of the Income Tax you'll pay.") shouldBe true
    }

    "show 'The tax codes and Income Tax service is currently unavailable' for the PAYE section when the user has no details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h3").exists(e => e.text == "Pay As You Earn (PAYE)") shouldBe true
      document.select("p").exists(e => e.text == "The tax codes and Income Tax service is currently unavailable") shouldBe true
    }

    "show 'See how company car and medical benefit could affect your taxable income.' for the Company benefits section when the user has details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h3 a").exists(e => e.text == "Company benefits") shouldBe true
      document.select("p").exists(e => e.text == "See how company car and medical benefit could affect your taxable income.") shouldBe true
    }

    "show 'The company benefits service is currently unavailable' for the Company benefits section when the user has no details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h3").exists(e => e.text == "Company benefits") shouldBe true
      document.select("p").exists(e => e.text == "The company benefits service is currently unavailable") shouldBe true
    }

    "show 'Update your address' for the Personal details section when the user has details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("a").exists(e => e.text == "Update your address") shouldBe true
    }

    "show 'You can't view or update your address right now' for the Personal details section when the user no has details" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "You can't view or update your address right now") shouldBe true
    }

    "show an SA Messages link when a SA, GG user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html("""<div class="pertax-messages">Simulated Messages Partial</div>"""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.getElementsByClass("pertax-messages")).isDefined shouldBe true
    }

    "not show an SA Messages link when a GG and non-SA user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".pertax-messages").first).isDefined shouldBe false
    }

    "not show an SA Messages link when a Verify and SA user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".pertax-messages").first).isDefined shouldBe false
    }

    "not show an SA Messages link when a Verify and non-SA user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".pertax-messages").first).isDefined shouldBe false
    }

    "show an SA link when a GG and SA user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, FileReturnSelfAssessmentActionNeeded(SaUtr("1111111111")), false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "Complete your tax return or make a payment.") shouldBe true
    }

    "not show an SA link when a GG and non-SA user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "Complete your tax return or make a payment.") shouldBe false
    }

    "not show an SA link when a non-GG and SA user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "Complete your tax return or make a payment.") shouldBe false
    }

    "not show an SA link when a non-GG and non-SA user is logged in" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "Complete your tax return or make a payment.") shouldBe false
    }

    "show the Tax Estimate block when a user is logged in via Verify and enrolled for PAYE" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.tax-estimate").first).isDefined shouldBe true
    }

    "show the Tax Estimate block when a user is High GG and enrolled for PAYE" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.tax-estimate").first).isDefined shouldBe true
    }

    "not show the Tax Estimate link when a user is logged in via Verify and not enrolled for PAYE" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.tax-estimate").first).isDefined shouldBe false
    }

    "not show the Tax Estimate block when a user is high GG and not enrolled for PAYE" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.tax-estimate").first).isDefined shouldBe false
    }

    "show the Tax Estimate block when a user is low GG and is enrolled for PAYE" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.tax-estimate").first).isDefined shouldBe true
    }

    "not show the Tax Estimate block when a user is low GG and not enrolled for PAYE" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.tax-estimate").first).isDefined shouldBe false
    }

    "show the personal details block when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the personal details block when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.personal-details").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true
      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the track your forms block when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.track-forms").first).isDefined shouldBe true
    }

    "show the trusted-helpers block when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.trusted-helpers").first).isDefined shouldBe true
    }

    "show the trusted-helpers block when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.trusted-helpers").first).isDefined shouldBe true
    }

    "not show the trusted-helpers block when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.trusted-helpers").first).isDefined shouldBe false
    }

    "not show the trusted-helpers block when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true
      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.trusted-helpers").first).isDefined shouldBe false
    }

    "not show the trusted-helpers block when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.trusted-helpers").first).isDefined shouldBe false
    }

    "not show the trusted-helpers block when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.trusted-helpers").first).isDefined shouldBe false
    }

    "show the National Insurance link when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.national-insurance").first).isDefined shouldBe true
    }

    "show the National Insurance link when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.national-insurance").first).isDefined shouldBe true
    }

    "show the National Insurance link when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.national-insurance").first).isDefined shouldBe true
    }

    "show the National Insurance link when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.national-insurance").first).isDefined shouldBe true
    }

    "show the National Insurance link when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.national-insurance").first).isDefined shouldBe true
    }

    "show the National Insurance link when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.national-insurance").first).isDefined shouldBe true
    }

    "show the National Insurance text when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "View your National Insurance record.") shouldBe true
    }

    "show the National Insurance text when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "View your National Insurance record.") shouldBe true
    }

    "show the National Insurance text when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "View your National Insurance record.") shouldBe true
    }

    "show the National Insurance text when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "View your National Insurance record.") shouldBe true
    }

    "show the National Insurance text when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "View your National Insurance record.") shouldBe true
    }

    "show the National Insurance text when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("p").exists(e => e.text == "View your National Insurance record.") shouldBe true
    }

    "show the Pensions link when a user is Verify, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.pensions").first).isDefined shouldBe injected[ConfigDecorator].nispEnabled
    }

    "show the Pensions link when a user is Verify, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.pensions").first).isDefined shouldBe injected[ConfigDecorator].nispEnabled
    }

    "show the Pensions link when a user is High GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.pensions").first).isDefined shouldBe injected[ConfigDecorator].nispEnabled
    }

    "show the Pensions link when a user is High GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.pensions").first).isDefined shouldBe injected[ConfigDecorator].nispEnabled
    }

    "show the Pensions link when a user is low GG, and has PAYE enrolment and SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.pensions").first).isDefined shouldBe injected[ConfigDecorator].nispEnabled
    }

    "show the Pensions link when a user is low GG, has PAYE enrolment and no SA enrolment" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.pensions").first).isDefined shouldBe injected[ConfigDecorator].nispEnabled
    }

    "show the Marriage Allowance block under services you might need regardless of user" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.tamc").first).isDefined
    }

    "show the Marriage Allowance link when user is Verify and showMarriageAllowanceSection is true" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), showMarriageAllowanceSection = true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".tamc-link").first).isDefined shouldBe true
    }

    "show the Marriage Allowance link when user is GG and uplifted and showMarriageAllowanceSection is true" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), showMarriageAllowanceSection = true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".tamc-link").first).isDefined shouldBe true
    }

    "not show the Marriage Allowance link when user is low GG but not uplifted and showMarriageAllowanceSection is true" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), showMarriageAllowanceSection = true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".tamc-link").first).isDefined shouldBe false
    }

    "not show the Marriage Allowance link when user is low GG but not uplifted and showMarriageAllowanceSection is false" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), showMarriageAllowanceSection = false, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".tamc-link").first).isDefined shouldBe false
    }

    "show the Child Benefits link under services you might need regardless of user" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), false, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".child-ben-service-link").first).isDefined shouldBe true
    }

    "show no feedback link when a user is logged into the service" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.getElementById("feedback-link")).isDefined shouldBe false
    }

    "show no Annual Taxable Income section when HMRC hold no information about the user" in new SpecSetup {
      override val withPaye: Boolean = false
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".annual-taxable-income").first()).isDefined shouldBe false
    }

    "not show sa important deadlines block when user is High GG and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.sa-deadlines-block").first).isDefined shouldBe false
    }

    "not show sa important deadlines block when user is Low GG and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.sa-deadlines-block").first).isDefined shouldBe false
    }

    "not show sa important deadlines block when user is Verify and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.sa-deadlines-block").first).isDefined shouldBe false
    }

    "not show sa important deadlines block when user is Verify and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.sa-deadlines-block").first).isDefined shouldBe false
    }

    "show manage prefs block if user is High GG and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Low GG and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Low GG and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is High GG and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = true
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Verify and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show manage prefs block if user is Verify and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select("div.manage-paperless").first).isDefined shouldBe true
    }

    "show services for businesses section when user is GG and SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.getElementById("business-tax-account-link")).isDefined shouldBe true
    }

    "not show NINO section when user is low GG and with Paye " in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.select(".nino").first).isDefined shouldBe false
    }


    "not show services for businesses section when user is GG and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.getElementById("business-tax-account-link")).isDefined shouldBe false
    }

    "not show services for businesses section when user is Verify (not GG) and is SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.getElementById("business-tax-account-link")).isDefined shouldBe false
    }

    "not show services for businesses section when user is Verify (not GG) and not SA" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      Option(document.getElementById("business-tax-account-link")).isDefined shouldBe false
    }

    "see the Services you might need header when showMarriageAllowanceSection is true" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = false
      override val isGovernmentGateway: Boolean = false
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), showMarriageAllowanceSection = true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h2").exists(e => e.text == "Services you might need") shouldBe true
    }

    "see the Services you might need header when showMarriageAllowanceSection is false" in new SpecSetup {
      override val withPaye: Boolean = true
      override val withSa: Boolean = true
      override val isGovernmentGateway: Boolean = true
      override val isHighGG: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      val document = Jsoup.parse(views.html.home(Html(""), showMarriageAllowanceSection = false, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(testPertaxUser)), messages).toString)
      document.select("h2").exists(e => e.text == "Services you might need") shouldBe true
    }

    abstract class WithIncomeTaxBlocksSetup {
      def isActivePaye: Boolean
      def isSa: Boolean
      def saAction: SelfAssessmentActionNeeded
      def isGovernmentGateway: Boolean

      lazy val pertaxUser = Fixtures.buildFakePertaxUser(withPaye = true, withSa = isSa, isGovernmentGateway = isGovernmentGateway, isHighGG = false)
      lazy val document = Jsoup.parse(views.html.home(
        inboxLinkPartial = Html(""),
        showMarriageAllowanceSection = true,
        isActivePaye = isActivePaye,
        showCompanyBenefitSection = true,
        taxCalculationState = TaxCalculationUnkownState,
        saAction,
        false,
        None
      )(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)

      def runCheck(saBlockExpected: Boolean, saMessage: String, payeBlockExpected: Boolean, incomeTaxHeaderExpected: Boolean) = {
        document.select("p").exists(e => e.text == saMessage) shouldBe saBlockExpected
        Option(document.select("div.tax-estimate").first).isDefined shouldBe payeBlockExpected
        document.select("h2").exists(e => e.text == "Tax and National Insurance") shouldBe incomeTaxHeaderExpected
      }
    }

    "see the income tax header, PAYE block and SA block when user is GovernmentGateway and PAYE and SA" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val saAction = FileReturnSelfAssessmentActionNeeded(SaUtr("1111111111"))

      runCheck(saBlockExpected = true, saMessage = "Complete your tax return or make a payment.", payeBlockExpected = true, incomeTaxHeaderExpected = true)
    }

    "see the income tax header, SA block but not PAYE block when user is GovernmentGateway, and SA but not PAYE" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val saAction = FileReturnSelfAssessmentActionNeeded(SaUtr("1111111111"))

      runCheck(saBlockExpected = true, saMessage = "Complete your tax return or make a payment.", payeBlockExpected = false, incomeTaxHeaderExpected = true)
    }

    "see the income tax header, SA Activation block and PAYE block when user is GovernmentGateway and PAYE and SA enrollment = 'NotYetActivated'" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val saAction = ActivateSelfAssessmentActionNeeded(SaUtr("1111111111"))

      runCheck(saBlockExpected = true, saMessage = "Activate your Self Assessment using the 12-digit activation code you received in the post.", payeBlockExpected = true, incomeTaxHeaderExpected = true)
    }

    "see the income tax header, SA Activation block but not PAYE block when user is GovernmentGateway, SA enrollment = 'NotYetActivated', but not PAYE" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val saAction = ActivateSelfAssessmentActionNeeded(SaUtr("1111111111"))

      runCheck(saBlockExpected = true, saMessage = "Activate your Self Assessment using the 12-digit activation code you received in the post.", payeBlockExpected = false, incomeTaxHeaderExpected = true)
    }

    "see the income tax header, SA Wrong Account block and PAYE block when user is GovernmentGateway and has a SaUtr matching record and is PAYE" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val saAction = NoEnrolmentFoundSelfAssessmentActionNeeded(SaUtr("1111111111"))

      runCheck(saBlockExpected = true, saMessage = "Check your Self Assessment details.", payeBlockExpected = true, incomeTaxHeaderExpected = true)
    }

    "see the income tax header, SA Wrong Account block but no PAYE block when user is GovernmentGateway and has a SaUtr matching record and is not PAYE" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val saAction = NoEnrolmentFoundSelfAssessmentActionNeeded(SaUtr("1111111111"))

      runCheck(saBlockExpected = true, saMessage = "Check your Self Assessment details.", payeBlockExpected = false, incomeTaxHeaderExpected = true)
    }

    "see the income tax header and PAYE block but no SA block when user is GovernmentGateway and is PAYE but is not Sa" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val saAction = NoSelfAssessmentActionNeeded

      runCheck(saBlockExpected = false, saMessage = "Check your Self Assessment details.", payeBlockExpected = true, incomeTaxHeaderExpected = true)
    }

    "see the income tax header but not the PAYE block or SA block when user is GovernmentGateway is not PAYE and is not Sa" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val saAction = NoSelfAssessmentActionNeeded

      runCheck(saBlockExpected = false, saMessage = "Check your Self Assessment details.", payeBlockExpected = false, incomeTaxHeaderExpected = true)
    }

    "see the income tax header and PAYE block but not SA block when user is PAYE and not GovernmentGateway and not Sa" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = false
      lazy val isGovernmentGateway = false
      lazy val saAction = NoSelfAssessmentActionNeeded

      runCheck(saBlockExpected = false, saMessage = "Check your Self Assessment details.", payeBlockExpected = true, incomeTaxHeaderExpected = true)
    }

    "see the income tax header but not the PAYE block or SA block when user is not PAYE and not GovernmentGateway and not Sa" in new WithIncomeTaxBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = false
      lazy val saAction = NoSelfAssessmentActionNeeded

      runCheck(saBlockExpected = false, saMessage = "Check your Self Assessment details.", payeBlockExpected = false, incomeTaxHeaderExpected = true)
    }

    abstract class WithNationalInsuranceBlocksSetup {
      def isActivePaye: Boolean
      def isSa: Boolean
      def isGovernmentGateway: Boolean
      def isHighGG: Boolean

      lazy val pertaxUser = Fixtures.buildFakePertaxUser(withPaye = isActivePaye, withSa = isSa, isGovernmentGateway = isGovernmentGateway, isHighGG = isHighGG)
      lazy val document = Jsoup.parse(views.html.home(Html(""), true, isActivePaye, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator],  Some(pertaxUser)), messages).toString)

      def runCheck(niBlockExpected: Boolean, companyBenefitsBlockExpected: Boolean) = {
        document.select("h3").exists(e => e.text == "National Insurance") shouldBe niBlockExpected
        document.select("h3").exists(e => e.text == "Company benefits") shouldBe companyBenefitsBlockExpected
      }
    }

    "see the national insurance block but not the company benefits block when user is not GovernmentGateway and not active PAYE, SA or High GG" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = false
      lazy val isHighGG = false

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = false)
    }

    "see the national insurance block but not the company benefits block when user is Government Gateway but not active PAYE, SA or High GG" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val isHighGG = false

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = false)
    }

    "see the national insurance block but not company benefits block when user is Government Gateway and active PAYE, but not SA or High GG" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val isHighGG = false

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = false)
    }

    "see the national insurance block but not company benefits block when user is Government Gateway and active PAYE and SA, but not High GG" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val isHighGG = false

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = false)
    }

    "see the national insurance block but not the company benefits block when user is not GovernmentGateway and not PAYE, SA but is High GG" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = false
      lazy val isHighGG = true

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = false)
    }

    "see the national insurance block but not the company benefits block when user is Government Gateway and High GG but not PAYE and SA" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val isHighGG = true

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = false)
    }

    "see the national insurance block and company benefits block when user is Government Gateway, High GG and active PAYE, but not SA" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val isHighGG = true

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = true)
    }

    "see the national insurance block and company benefits block when user is Government Gateway, High GG, active PAYE and SA" in new WithNationalInsuranceBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val isHighGG = true

      runCheck(niBlockExpected = true, companyBenefitsBlockExpected = true)
    }

    abstract class WithTaxAndChildBenefitsBlocksSetup {
      def isActivePaye: Boolean
      def isSa: Boolean
      def isGovernmentGateway: Boolean

      lazy val pertaxUser = Fixtures.buildFakePertaxUser(withPaye = isActivePaye, withSa = isSa, isGovernmentGateway = isGovernmentGateway, isHighGG = false)
      lazy val document = Jsoup.parse(views.html.home(Html(""), true, isActivePaye, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)

      def runCheck(taxCreditsBlockExpected: Boolean, childBenefitsBlockExpected: Boolean) = {
        document.select("h3").exists(e => e.text == "Tax credits") shouldBe taxCreditsBlockExpected
        document.select("h3").exists(e => e.text == "Child Benefit") shouldBe childBenefitsBlockExpected
      }
    }

    "see the tax credits block and child benefits block when user is not GovernmentGateway and not PAYE or SA" in new WithTaxAndChildBenefitsBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = false
      lazy val niBlockExpected = false
      lazy val companyBenefitsBlockExpected = false

      runCheck(taxCreditsBlockExpected = true, childBenefitsBlockExpected = true)
    }

    "see the tax credits block and child benefits block when user is GovernmentGateway and not PAYE or SA" in new WithTaxAndChildBenefitsBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val niBlockExpected = false
      lazy val companyBenefitsBlockExpected = false

      runCheck(taxCreditsBlockExpected = true, childBenefitsBlockExpected = true)
    }

    "see the tax credits block and child benefits block when user is GovernmentGateway and PAYE but not SA" in new WithTaxAndChildBenefitsBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = false
      lazy val isGovernmentGateway = true
      lazy val niBlockExpected = true
      lazy val companyBenefitsBlockExpected = false

      runCheck(taxCreditsBlockExpected = true, childBenefitsBlockExpected = true)
    }

    "see the tax credits block and child benefits block when user is GovernmentGateway, PAYE and SA" in new WithTaxAndChildBenefitsBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = true
      lazy val isGovernmentGateway = true
      lazy val niBlockExpected = true
      lazy val companyBenefitsBlockExpected = false

      runCheck(taxCreditsBlockExpected = true, childBenefitsBlockExpected = true)
    }

    "see the tax credits block and child benefits block when user is not GovernmentGateway but is PAYE and SA" in new WithTaxAndChildBenefitsBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = true
      lazy val isGovernmentGateway = false

      runCheck(taxCreditsBlockExpected = true, childBenefitsBlockExpected = true)
    }

    "see the tax credits block and child benefits block when user is not GovernmentGateway or PAYE but is SA" in new WithTaxAndChildBenefitsBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = true
      lazy val isGovernmentGateway = false

      runCheck(taxCreditsBlockExpected = true, childBenefitsBlockExpected = true)
    }

    "see the tax credits block and child benefits block when user is not GovernmentGateway or SA but is PAYE" in new WithTaxAndChildBenefitsBlocksSetup {
      lazy val isActivePaye = true
      lazy val isSa = false
      lazy val isGovernmentGateway = false

      runCheck(taxCreditsBlockExpected = true, childBenefitsBlockExpected = true)
    }

    abstract class WithLtaBlocksSetup {
      def isActivePaye: Boolean
      def isSa: Boolean
      def isGovernmentGateway: Boolean
      def isLta: Boolean

      lazy val pertaxUser = Fixtures.buildFakePertaxUser(withPaye = isActivePaye, withSa = isSa, isGovernmentGateway = isGovernmentGateway)
      lazy val document = Jsoup.parse(views.html.home(Html(""), true, isActivePaye, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, isLta, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)

      def runCheck(lifetimeAllowanceProtectionBlockExpected: Boolean) = {
        document.select("h3").exists(e => e.text == "Lifetime allowance protection") shouldBe lifetimeAllowanceProtectionBlockExpected
      }
    }

    "not see lifetime allowance protection block when user is not LTA" in new WithLtaBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = false
      lazy val isLta = false

      runCheck(lifetimeAllowanceProtectionBlockExpected = false)
    }

    "see the lifetime allowance protection block when user is LTA" in new WithLtaBlocksSetup {
      lazy val isActivePaye = false
      lazy val isSa = false
      lazy val isGovernmentGateway = false
      lazy val isLta = true

      runCheck(lifetimeAllowanceProtectionBlockExpected = true)
    }
  }

  "Header text should be 'Personal tax account'" in new OldStyleSpecSetup {
    val pertaxUser = Fixtures.buildFakePertaxUser(withSa = true, isGovernmentGateway = false)
    val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator],Some(pertaxUser)), messages).toString)

    document.select(".header__menu__proposition-name").exists(e => e.text == "Personal tax account") shouldBe true
  }

  abstract class TaxCalculationBannerSetup {
    def taxCalculationState : TaxCalculationState
    lazy val pertaxUser = Fixtures.buildFakePertaxUser(withPaye = true)
    lazy val document = Jsoup.parse(views.html.home(Html(""), true, true, true, taxCalculationState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator],Some(pertaxUser)), messages).toString)
  }

  "Should see 'You have paid too much tax' (Owed) banner when user has paid too much tax and has not requested a refund" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(1000.0)
    val startOfTaxYear = 2015
    val endOfTaxYear = 2016
    lazy val taxCalculationState = TaxCalculationRefundState(amount = amount, startOfTaxYear = startOfTaxYear, endOfTaxYear = endOfTaxYear)
    document.select("p").exists(e => e.text == "HM Revenue and Customs owe you a £1,000.00 refund for the 2015 to 2016 tax year.") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should see 'You paid too much tax' (Payment Processing) banner when user has paid too much tax and is currently being processed" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(1000.0)
    lazy val taxCalculationState = TaxCalculationPaymentProcessingState(amount = amount)
    document.select("p").exists(e => e.text == "HM Revenue and Customs are processing your £1,000.00 refund.") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should see 'You paid too much tax' (Bacs Paid) banner when user has paid too much tax a refund has been made" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(1000.0)
    lazy val taxCalculationState = TaxCalculationPaymentPaidState(amount = amount, datePaid = "19 May 2016")
    document.select("p").exists(e => e.text == "HM Revenue and Customs paid you a refund of £1,000.00 on 19 May 2016.") shouldBe true
    Option(document.getElementsByClass("alert--success")).isDefined shouldBe true
  }

  "Should see 'You paid too much tax' (Cheque Paid) banner when user has paid too much tax a refund has been made" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(1000.0)
    lazy val taxCalculationState = TaxCalculationPaymentChequeSentState(amount = amount, datePaid = "19 May 2016")
    document.select("p").exists(e => e.text == "HM Revenue and Customs sent you a cheque for £1,000.00 on 19 May 2016.") shouldBe true
    Option(document.getElementsByClass("alert--success")).isDefined shouldBe true
  }

  "Should not see any banner when user has not got an overpayment" in new TaxCalculationBannerSetup {
    lazy val taxCalculationState = TaxCalculationUnkownState
    document.select("h3").exists(e => e.text == "You have paid too much tax") shouldBe false
    document.select("h3").exists(e => e.text == "You paid too much tax") shouldBe false
  }

  "Should see 'You have paid too little tax' (Underpayment) banner when user has paid too little tax" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(1000.0)
    val startOfTaxYear = 2015
    val endOfTaxYear = 2016
    lazy val taxCalculationState = TaxCalculationPaymentDueState(amount = amount, startOfTaxYear = startOfTaxYear, endOfTaxYear = endOfTaxYear)
    document.select("p").exists(e => e.text == "You owe HM Revenue and Customs £1,000.00 for the 2015 to 2016 tax year.") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should see 'You have paid too little tax' (Underpayment) banner when user has part paid underpayment" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(1000.0)
    val startOfTaxYear = 2015
    val endOfTaxYear = 2016
    lazy val taxCalculationState = TaxCalculationPartPaidState(amount = amount, startOfTaxYear = startOfTaxYear, endOfTaxYear = endOfTaxYear)
    document.select("p").exists(e => e.text == "You owe HM Revenue and Customs £1,000.00 for the 2015 to 2016 tax year.") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should see 'You have paid too little tax' (Underpayment) banner when user has paid underpayment" in new TaxCalculationBannerSetup {
    lazy val taxCalculationState = TaxCalculationPaidAllState
    document.select("p").exists(e => e.text == "You have no payments to make to HMRC for the tax year 2016 to 2017") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should see 'You have paid too little tax' (Underpayment) banner when user has underpayment and payments is down" in new TaxCalculationBannerSetup {
    val startOfTaxYear = 2015
    val endOfTaxYear = 2016
    lazy val taxCalculationState = TaxCalculationPaymentsDownState(startOfTaxYear = startOfTaxYear, endOfTaxYear = endOfTaxYear)
    document.select("p").exists(e => e.text == "You owe HMRC for the 2015 to 2016 tax year.") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should see correct number formatting for amount displayed in tax calculation banner" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(123456789.78)
    val startOfTaxYear = 2015
    val endOfTaxYear = 2016
    lazy val taxCalculationState = TaxCalculationPaymentDueState(amount = amount, startOfTaxYear = startOfTaxYear, endOfTaxYear = endOfTaxYear)
    document.select("p").exists(e => e.text == "You owe HM Revenue and Customs £123,456,789.78 for the 2015 to 2016 tax year.") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should round to the nearest pence when there are three decimal places in the tax calculation banner" in new TaxCalculationBannerSetup {
    val amount = BigDecimal(1000.005)
    val startOfTaxYear = 2015
    val endOfTaxYear = 2016
    lazy val taxCalculationState = TaxCalculationPaymentDueState(amount = amount, startOfTaxYear = startOfTaxYear, endOfTaxYear = endOfTaxYear)
    document.select("p").exists(e => e.text == "You owe HM Revenue and Customs £1,000.01 for the 2015 to 2016 tax year.") shouldBe true
    Option(document.getElementsByClass("panel-indent--info")).isDefined shouldBe true
  }

  "Should see 'self assessment bucket' on the homepage with activation code instructions as a GG User who has received their 12 digit activation code" in new OldStyleSpecSetup {
    val pertaxUser = Fixtures.buildFakePertaxUser(withSa = true, isGovernmentGateway = true)
    val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, ActivateSelfAssessmentActionNeeded(SaUtr("1111111111")), false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)
    document.select("p").exists(e => e.text == "Activate your Self Assessment using the 12-digit activation code you received in the post.") shouldBe true
  }

  "Should not see 'self assessment bucket' on the homepage as a GG User who activated their 12 digit activation code" in new OldStyleSpecSetup {
    val pertaxUser = Fixtures.buildFakePertaxUser(withSa = true, isGovernmentGateway = true)
    val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)
    document.select("p").exists(e => e.text == "Activate your Self Assessment using the 12-digit activation code you received in the post.") shouldBe false
  }

  "Should see 'self assessment bucket' on the homepage linking to 'sa-not-shown' as GG User who already has an SAUTR that exists in citizen details" in new OldStyleSpecSetup {
    val pertaxUser = Fixtures.buildFakePertaxUser(withSa = true, isGovernmentGateway = true)
    val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoEnrolmentFoundSelfAssessmentActionNeeded(SaUtr("1111111111")), false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)
    document.select("p").exists(e => e.text == "Check your Self Assessment details.") shouldBe true
  }

  "Should not see 'self assessment bucket' on the homepage linking to 'sa-not-shown' as GG User whose SAUTR does not exist in ciziten details" in new OldStyleSpecSetup {
    val pertaxUser = Fixtures.buildFakePertaxUser(withSa = true, isGovernmentGateway = true)
    val document = Jsoup.parse(views.html.home(Html(""), true, true, true, TaxCalculationUnkownState, NoSelfAssessmentActionNeeded, false, None)(PertaxContext(FakeRequest("GET", "/"), mockLocalPartialRetreiver, injected[ConfigDecorator], Some(pertaxUser)), messages).toString)
    document.select("p").exists(e => e.text == "Check your Self Assessment details.") shouldBe false
  }
}
