/*
 * Copyright 2019 HM Revenue & Customs
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
import org.joda.time.{DateTime, LocalDate}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.inject.bind
import play.api.test.FakeRequest
import play.twirl.api.Html
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.frontend.auth.AuthContext
import uk.gov.hmrc.play.frontend.auth.connectors.domain._
import util.{BaseSpec, Fixtures}
import views.html.cards.personaldetails._


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
    def mainHomeStartDate: Option[String]
    def show2016Message: Boolean

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

    lazy val cardBody = controller.getMainAddressCard()
  }

  "Calling getMainAddressCard" should {

    "return nothing when there are no person details" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = false
      override lazy val userHasCorrespondenceAddress = false
      override lazy val mainHomeStartDate = None
      override lazy val show2016Message = false

      cardBody shouldBe None

    }

    "return the correct markup when there are person details and the user has a correspondence address" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val show2016Message = false

      cardBody shouldBe Some(mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress))

    }

    "return the correct markup when there are person details and the user does not have a correspondence address" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = false
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val show2016Message = false

      cardBody shouldBe Some(mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress))

    }

      "return the correct markup when tax credits is enabled" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val show2016Message = false

      cardBody shouldBe Some(mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress))

    }

    "return the correct markup when tax credits is disabled" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = false
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val show2016Message = false

      cardBody shouldBe Some(mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress))

    }
  }


  trait PostalAddressSetup extends SpecSetup {

    def canUpdatePostalAddress: Boolean
    def userHasPersonDetails: Boolean
    def userHasCorrespondenceAddress: Boolean
    def userHasWelshLanguageUnitAddress: Boolean

    def buildPersonDetails = PersonDetails("115", Person(
      Some("Firstname"), Some("Middlename"), Some("Lastname"), Some("FML"),
      Some("Dr"), Some("Phd."), Some("M"), Some(LocalDate.parse("1945-03-18")), Some(Fixtures.fakeNino)
    ), Some(buildFakeAddress), if (userHasCorrespondenceAddress && userHasWelshLanguageUnitAddress) Some(buildFakeWLUAddress) else if(userHasCorrespondenceAddress) Some(buildFakeAddress) else None)

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

    def buildFakeWLUAddress = Address(
      Some("1 Fake Street"),
      Some("Fake Town"),
      Some("Fake City"),
      Some("Fake Region"),
      None,
      Some("CF145SH"),
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

    lazy val cardBody = controller.getPostalAddressCard()
  }

  "Calling getPostalAddressCard" should {

    "return nothing when there are no person details" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = false
      override lazy val canUpdatePostalAddress = false
      override lazy val userHasCorrespondenceAddress = false
      override lazy val userHasWelshLanguageUnitAddress = false

      cardBody shouldBe None
    }

    "return nothing when there are person details but no correspondence address" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val canUpdatePostalAddress = false
      override lazy val userHasCorrespondenceAddress = false
      override lazy val userHasWelshLanguageUnitAddress = false

      cardBody shouldBe None
    }

    "return the correct markup when there is a correspondence address and the postal address can be updated" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val canUpdatePostalAddress = true
      override lazy val userHasWelshLanguageUnitAddress = false

      cardBody shouldBe Some(postalAddress(buildPersonDetails, canUpdatePostalAddress))

    }

    "return the correct markup when there is a correspondence address and the postal address cannot be updated" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val canUpdatePostalAddress = false
      override lazy val userHasWelshLanguageUnitAddress = false

      cardBody shouldBe Some(postalAddress(buildPersonDetails, canUpdatePostalAddress))

    }

    "return nothing when the correspondence address matches with a Welsh Language Unit" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val canUpdatePostalAddress = false
      override val userHasWelshLanguageUnitAddress = true

      cardBody shouldBe None
    }
  }

  "Calling getNationalInsuranceCard" should {

    trait LocalSetup extends SpecSetup {
      lazy val cardBody = controller.getNationalInsuranceCard()
      def nino: Nino
    }

    "always return the same markup" in new LocalSetup {
      override lazy val nino = pertaxUser.get.nino.get

      cardBody shouldBe Some(nationalInsurance(nino))
    }
  }

  "Calling getChangeNameCard" should {

    trait LocalSetup extends SpecSetup {
      lazy val cardBody = controller.getChangeNameCard()

      def userHasPersonDetails: Boolean

      def buildPersonDetails = PersonDetails("115", Person(
        Some("Firstname"), Some("Middlename"), Some("Lastname"), Some("FML"),
        Some("Dr"), Some("Phd."), Some("M"), Some(LocalDate.parse("1945-03-18")), Some(Fixtures.fakeNino)
      ), Some(Fixtures.buildFakeAddress), None)

      override lazy val pertaxUser = Some(PertaxUser(
        Fixtures.buildFakeAuthContext(),
        UserDetails(UserDetails.VerifyAuthProvider),
        personDetails = if (userHasPersonDetails) Some(buildPersonDetails) else None,
        true)
      )
    }

    "always return the correct markup when user has a name" in new LocalSetup {
      override def userHasPersonDetails = true

      cardBody shouldBe Some(changeName())
    }

    "always return None when user does not have a name available" in new LocalSetup {
      override def userHasPersonDetails = false

      cardBody shouldBe None
    }
  }
}
