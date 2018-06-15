/*
 * Copyright 2018 HM Revenue & Customs
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
import org.joda.time.DateTime
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.scalatest.Assertion
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.twirl.api.Html
import services._
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import util.{BaseSpec, DateTimeTools, Fixtures}

import scala.collection.JavaConversions._

class homeSpec extends BaseSpec {

  val messages: Messages = Messages.Implicits.applicationMessages

  trait SpecSetup {

    def isGovernmentGateway: Boolean
    def userHasPersonDetails: Boolean
    def principalName: Option[String]

    val messageInboxPartial = Html("")
    val configDecorator: ConfigDecorator = injected[ConfigDecorator]

    lazy val testPertaxUser = PertaxUser(
      authContext = AuthContext(
        Authority(
          uri = "/auth/oid/flastname",
          accounts = Accounts(
            paye = Some(PayeAccount("/paye/" + Fixtures.fakeNino.nino, Fixtures.fakeNino)),
            sa = None
          ),
          loggedInAt = None,
          previouslyLoggedInAt = Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
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
      true)

    lazy val document: Document = Jsoup.parse(views.html.home(
      Nil, Nil, Nil, true)
    (PertaxContext(FakeRequest("GET", "/test"),
      mockLocalPartialRetreiver,
      injected[ConfigDecorator],
      Some(testPertaxUser)),
      messages).toString)
  }

  "Rendering home.scala.html" should {

    "show the users name and not 'Your account' when the user has details and is not a GG user" in new SpecSetup {
      override val isGovernmentGateway: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = true

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show the users name and not 'Your account' when the user has no details but is a GG user" in new SpecSetup {
      override val isGovernmentGateway: Boolean = true
      override val principalName: Option[String] = Some("Firstname Lastname")
      override val userHasPersonDetails: Boolean = false

      document.select("h1").exists(e => e.text == "Firstname Lastname") shouldBe true
      document.select("h1").exists(e => e.text == "Your account") shouldBe false
    }

    "show 'Your account' and not the users name when the user has no details and is not a GG user" in new SpecSetup {
      override val isGovernmentGateway: Boolean = false
      override val principalName: Option[String] = None
      override val userHasPersonDetails: Boolean = false

      document.select("h1").exists(e => e.text == "Your account") shouldBe true
    }

  }

}
