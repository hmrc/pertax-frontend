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

package controllers.helpers

import config.ConfigDecorator
import models._
import org.joda.time.LocalDate
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.inject.bind
import play.api.test.FakeRequest
import util.{BaseSpec, Fixtures}
import views.html.tags.formattedNino


class PersonalDetailsCardGeneratorSpec extends BaseSpec {

  override implicit lazy val app: Application = localGuiceApplicationBuilder
    .overrides(bind[ConfigDecorator].toInstance(MockitoSugar.mock[ConfigDecorator]))
    .build()

  trait SpecSetup extends I18nSupport {
    override def messagesApi: MessagesApi = injected[MessagesApi]

    implicit lazy val pertaxContext = PertaxContext(FakeRequest(), mockLocalPartialRetreiver, injected[ConfigDecorator], pertaxUser)

    lazy val controller = injected[PersonalDetailsCardGenerator]
    lazy val pertaxUser = Some(PertaxUser(Fixtures.buildFakeAuthContext(), UserDetails(UserDetails.GovernmentGatewayAuthProvider), None, true))
  }

  trait MainAddressSetup extends SpecSetup {

    def taxCreditsEnabled: Boolean

    def userHasPersonDetails: Boolean
    def userHasCorrespondenceAddress: Boolean

    def buildPersonDetails = PersonDetails("115", Person(
      Some("Firstname"), Some("Middlename"), Some("Lastname"), Some("FML"),
      Some("Dr"), Some("Phd."), Some("M"), Some(LocalDate.parse("1945-03-18")), Some(Fixtures.fakeNino)
    ), Some(Fixtures.buildFakeAddress), if (userHasCorrespondenceAddress) Some(Fixtures.buildFakeAddress) else None)

    override lazy val pertaxUser = Some(PertaxUser(
      Fixtures.buildFakeAuthContext(),
      UserDetails(UserDetails.VerifyAuthProvider),
      personDetails = if (userHasPersonDetails) Some(buildPersonDetails) else None,
      true)
    )

    override lazy val controller = {
      val c = injected[PersonalDetailsCardGenerator]
      when(c.configDecorator.taxCreditsEnabled) thenReturn taxCreditsEnabled
      c
    }

    lazy val cardBody = controller.getMainAddressCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
  }

  "Calling getMainAddressCard" should {

    "return nothing when there are no person details" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = false
      override lazy val userHasCorrespondenceAddress = false

      cardBody shouldBe None
    }

    "return the correct markup when there are person details and the user has a correspondence address" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true

      cardBody shouldBe
        Some(
          """<div class="card column-third">
            |  <a class="card-link ga-track-anchor-click" href="/personal-account/your-address/tax-credits-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Main Address">
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Main address</h3>
            |      <p><strong>
            |            1 Fake Street<br>
            |            Fake Town<br>
            |            Fake City<br>
            |            Fake Region<br>
            |          AA1 1AA
            |      </strong></p>
            |        <p>This has been your main home since 15 March 2015.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <ul>
            |      <li>
            |        <a class="ga-track-anchor-click" href="/personal-account/your-address/tax-credits-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Change your main address">Change your main address</a>
            |      </li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return the correct markup when there are person details and the user does not have a correspondence address" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = false

      cardBody shouldBe
        Some(
          """<div class="card column-third">
            |  <a class="card-link ga-track-anchor-click" href="/personal-account/your-address/tax-credits-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Main Address">
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Main address</h3>
            |      <p><strong>
            |            1 Fake Street<br>
            |            Fake Town<br>
            |            Fake City<br>
            |            Fake Region<br>
            |          AA1 1AA
            |      </strong></p>
            |        <p>This has been your main home since 15 March 2015.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <ul>
            |      <li>
            |        <a class="ga-track-anchor-click" href="/personal-account/your-address/tax-credits-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Change your main address">Change your main address</a>
            |      </li>
            |        <li>
            |          <a class="ga-track-anchor-click" href="/personal-account/your-address/postal/find-address" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Change where we send your letters">Change where we send your letters</a>
            |        </li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return the correct markup when tax credits is enabled" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true

      cardBody shouldBe
        Some(
          """<div class="card column-third">
            |  <a class="card-link ga-track-anchor-click" href="/personal-account/your-address/tax-credits-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Main Address">
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Main address</h3>
            |      <p><strong>
            |            1 Fake Street<br>
            |            Fake Town<br>
            |            Fake City<br>
            |            Fake Region<br>
            |          AA1 1AA
            |      </strong></p>
            |        <p>This has been your main home since 15 March 2015.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <ul>
            |      <li>
            |        <a class="ga-track-anchor-click" href="/personal-account/your-address/tax-credits-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Change your main address">Change your main address</a>
            |      </li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return the correct markup when tax credits is disabled" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = false
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true

      cardBody shouldBe
        Some(
          """<div class="card column-third">
            |  <a class="card-link ga-track-anchor-click" href="/personal-account/your-address/residency-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Main Address">
            |    <div class="card-content" role="link">
            |      <h3 class="heading-small no-margin-top">Main address</h3>
            |      <p><strong>
            |            1 Fake Street<br>
            |            Fake Town<br>
            |            Fake City<br>
            |            Fake Region<br>
            |          AA1 1AA
            |      </strong></p>
            |        <p>This has been your main home since 15 March 2015.</p>
            |    </div>
            |  </a>
            |  <div class="card-actions">
            |    <ul>
            |      <li>
            |        <a class="ga-track-anchor-click" href="/personal-account/your-address/residency-choice" data-ga-event-category="link - click" data-ga-event-action="Main address" data-ga-event-label="Change your main address">Change your main address</a>
            |      </li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }
  }


  trait PostalAddressSetup extends SpecSetup {

    def canUpdatePostalAddress: Boolean
    def userHasPersonDetails: Boolean
    def userHasCorrespondenceAddress: Boolean

    def buildPersonDetails = PersonDetails("115", Person(
      Some("Firstname"), Some("Middlename"), Some("Lastname"), Some("FML"),
      Some("Dr"), Some("Phd."), Some("M"), Some(LocalDate.parse("1945-03-18")), Some(Fixtures.fakeNino)
    ), Some(buildFakeAddress), if (userHasCorrespondenceAddress) Some(buildFakeAddress) else None)

    def buildFakeAddress = Address(
      Some("1 Fake Street"),
      Some("Fake Town"),
      Some("Fake City"),
      Some("Fake Region"),
      None,
      Some("AA1 1AA"),
      if (canUpdatePostalAddress) Some(LocalDate.now().minusDays(1)) else Some(LocalDate.now()),
      Some("Residential")
    )

    override lazy val pertaxUser = Some(PertaxUser(
      Fixtures.buildFakeAuthContext(),
      UserDetails(UserDetails.VerifyAuthProvider),
      personDetails = if (userHasPersonDetails) {
        Some(buildPersonDetails)
      } else None,
      true)
    )

    lazy val cardBody = controller.getPostalAddressCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
  }

  "Calling getPostalAddressCard" should {

    "return nothing when there are no person details" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = false
      override lazy val canUpdatePostalAddress = false
      override lazy val userHasCorrespondenceAddress = false

      cardBody shouldBe None
    }

    "return nothing when there are person details but no correspondence address" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val canUpdatePostalAddress = false
      override lazy val userHasCorrespondenceAddress = false

      cardBody shouldBe None
    }

    "return the correct markup when there is a correspondence address and the postal address can be updated" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val canUpdatePostalAddress = true

      cardBody shouldBe
        Some(
          """<div class="card column-third">
            |    <a class="card-link ga-track-anchor-click" href="/personal-account/your-address/postal/find-address" data-ga-event-category="link - click" data-ga-event-action="Postal address" data-ga-event-label="Postal address">
            |  <div class="card-content" role="link">
            |    <h3 class="heading-small no-margin-top">Postal address</h3>
            |    <p><strong>
            |          1 Fake Street<br>
            |          Fake Town<br>
            |          Fake City<br>
            |          Fake Region<br>
            |        AA1 1AA
            |    </strong></p>
            |    <p>All your letters will be sent to this address.</p>
            |  </div>
            |  </a>
            |  <div class="card-actions">
            |    <ul>
            |      <li>
            |          <a class="ga-track-anchor-click" href="/personal-account/your-address/postal/find-address" data-ga-event-category="link - click" data-ga-event-action="Postal address" data-ga-event-label="Change your postal address">Change your postal address</a>
            |      </li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }

    "return the correct markup when there is a correspondence address and the postal address cannot be updated" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val canUpdatePostalAddress = false

      cardBody shouldBe
        Some(
          """<div class="card column-third">
            |  <div class="card-content" role="link">
            |    <h3 class="heading-small no-margin-top">Postal address</h3>
            |    <p><strong>
            |          1 Fake Street<br>
            |          Fake Town<br>
            |          Fake City<br>
            |          Fake Region<br>
            |        AA1 1AA
            |    </strong></p>
            |    <p>All your letters will be sent to this address.</p>
            |  </div>
            |  <div class="card-actions">
            |    <ul>
            |      <li>
            |          <p>You can only change this address once a day. Please try again tomorrow.</p>
            |      </li>
            |    </ul>
            |  </div>
            |</div>""".stripMargin)
    }
  }


  "Calling getNationalInsuranceCard" should {

    trait LocalSetup extends SpecSetup {
      lazy val cardBody = controller.getNationalInsuranceCard().map(_.body.split("\n").filter(!_.trim.isEmpty).mkString("\n")) //remove empty lines
    }

    "always return the same markup" in new LocalSetup {

      cardBody shouldBe
        Some(
          s"""<div class="card column-third">
             |  <a class="card-link ga-track-anchor-click" href="/personal-account/national-insurance-summary" data-ga-event-category="link - click" data-ga-event-action="National Insurance" data-ga-event-label="National Insurance">
             |    <div class="card-content" role="link">
             |      <h3 class="heading-small no-margin-top">National Insurance</h3>
             |      <p><strong>${formattedNino(pertaxUser.get.nino.get)}</strong></p>
             |      <p>Your National Insurance number is your unique identifier.</p>
             |    </div>
             |  </a>
             |  <div class="card-actions">
             |    <ul>
             |      <li><a class="ga-track-anchor-click" href="/personal-account/national-insurance-summary/print-letter" data-ga-event-category="link - click" data-ga-event-action="National Insurance" data-ga-event-label="Print your National Insurance letter">Print your National Insurance letter</a></li>
             |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/nirecord/pta" data-ga-event-category="link - click" data-ga-event-action="National Insurance" data-ga-event-label="View gaps in your record">View gaps in your record</a></li>
             |      <li><a class="ga-track-anchor-click" href="/check-your-state-pension/account/pta" data-ga-event-category="link - click" data-ga-event-action="National Insurance" data-ga-event-label="Check your State Pension">Check your State Pension</a></li>
             |    </ul>
             |  </div>
             |</div>""".stripMargin)
    }
  }
}

