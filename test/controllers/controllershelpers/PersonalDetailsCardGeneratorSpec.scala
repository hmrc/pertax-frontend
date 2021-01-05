/*
 * Copyright 2021 HM Revenue & Customs
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

package controllers.controllershelpers

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models._
import org.joda.time.LocalDate
import org.mockito.Mockito._
import org.scalatestplus.mockito.MockitoSugar
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import util.UserRequestFixture.buildUserRequest
import util.{BaseSpec, Fixtures}
import views.html.cards.personaldetails._

class PersonalDetailsCardGeneratorSpec extends BaseSpec with MockitoSugar with I18nSupport {

  implicit val mockConfigDecorator = mock[ConfigDecorator]
  override def messagesApi: MessagesApi = injected[MessagesApi]

  val mainAddress = injected[MainAddressView]
  val postalAddress = injected[PostalAddressView]
  val nationalInsurance = injected[NationalInsuranceView]
  val changeName = injected[ChangeNameView]

  def controller = new PersonalDetailsCardGenerator(
    mockConfigDecorator,
    injected[CountryHelper],
    mainAddress,
    postalAddress,
    nationalInsurance,
    changeName
  )

  trait MainAddressSetup {

    def taxCreditsEnabled: Boolean

    def userHasPersonDetails: Boolean

    def userHasCorrespondenceAddress: Boolean

    def mainHomeStartDate: Option[String]

    def isLocked: Boolean

    implicit def userRequest: UserRequest[_]

    def buildPersonDetails =
      PersonDetails(
        Person(
          Some("Firstname"),
          Some("Middlename"),
          Some("Lastname"),
          Some("FML"),
          Some("Dr"),
          Some("Phd."),
          Some("M"),
          Some(LocalDate.parse("1945-03-18")),
          Some(Fixtures.fakeNino)
        ),
        Some(Fixtures.buildFakeAddress),
        if (userHasCorrespondenceAddress) Some(Fixtures.buildFakeAddress) else None
      )

    when(mockConfigDecorator.taxCreditsEnabled) thenReturn taxCreditsEnabled

    lazy val cardBody: Option[_root_.play.twirl.api.HtmlFormat.Appendable] =
      controller.getMainAddressCard(isLocked)

  }

  lazy val excludedCountries = List(
    Country("GREAT BRITAIN"),
    Country("SCOTLAND"),
    Country("ENGLAND"),
    Country("WALES"),
    Country("NORTHERN IRELAND")
  )

  "Calling getMainAddressCard" should {

    "return nothing when there are no person details" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = false
      override lazy val userHasCorrespondenceAddress = false
      override lazy val mainHomeStartDate = None
      override lazy val isLocked = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = None, request = FakeRequest())

      cardBody shouldBe None

    }

    "return the correct markup when there are person details and the user has a correspondence address" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val isLocked = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(
        mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress, isLocked, excludedCountries))

      cardBody.map(_.body).get should not include "Change where we send your letters"
    }

    "return the correct markup when there are person details and the user does not have a correspondence address" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = false
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val isLocked = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(
        mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress, isLocked, excludedCountries))

      cardBody.map(_.body).get should include("Change where we send your letters")
    }

    "return the correct markup when there are person details and the user does not have a correspondence address and there is a correspondence address lock" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = false
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val isLocked = true

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(
        mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress, isLocked, excludedCountries))

      cardBody.map(_.body).get should not include "Change where we send your letters"
    }

    "return the correct markup when there are person details and the user has edited their view address" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val isLocked = true

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(
        mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress, isLocked, excludedCountries))

      cardBody.map(_.body).get should include("You can only change this address once a day. Please try again tomorrow.")
    }

    "return the correct markup when tax credits is enabled" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = true
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val isLocked = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(
        mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress, isLocked, excludedCountries))

      cardBody.map(_.body).get should not include "Change where we send your letters"
    }

    "return the correct markup when tax credits is disabled" in new MainAddressSetup {
      override lazy val taxCreditsEnabled = false
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val mainHomeStartDate = Some("15 March 2015")
      override lazy val isLocked = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(
        mainAddress(buildPersonDetails, taxCreditsEnabled, userHasCorrespondenceAddress, isLocked, excludedCountries))

      cardBody.map(_.body).get should not include "Change where we send your letters"
    }
  }

  trait PostalAddressSetup {

    implicit def userRequest: UserRequest[_]

    def isLocked: Boolean

    def userHasPersonDetails: Boolean

    def userHasCorrespondenceAddress: Boolean

    def userHasWelshLanguageUnitAddress: Boolean

    def closePostalAddressEnabled: Boolean

    def buildPersonDetails =
      PersonDetails(
        Person(
          Some("Firstname"),
          Some("Middlename"),
          Some("Lastname"),
          Some("FML"),
          Some("Dr"),
          Some("Phd."),
          Some("M"),
          Some(LocalDate.parse("1945-03-18")),
          Some(Fixtures.fakeNino)
        ),
        Some(buildFakeAddress),
        if (userHasCorrespondenceAddress && userHasWelshLanguageUnitAddress) Some(buildFakeWLUAddress)
        else if (userHasCorrespondenceAddress) Some(buildFakeAddress)
        else None
      )

    def buildFakeAddress = Address(
      Some("1 Fake Street"),
      Some("Fake Town"),
      Some("Fake City"),
      Some("Fake Region"),
      None,
      Some("AA1 1AA"),
      None,
      if (isLocked) Some(LocalDate.now()) else Some(LocalDate.now().minusDays(1)),
      None,
      Some("Residential")
    )

    def buildFakeWLUAddress = Address(
      Some("1 Fake Street"),
      Some("Fake Town"),
      Some("Fake City"),
      Some("Fake Region"),
      None,
      Some("CF145SH"),
      None,
      if (isLocked) Some(LocalDate.now()) else Some(LocalDate.now().minusDays(1)),
      None,
      Some("Residential")
    )

    def cardBody = controller.getPostalAddressCard(isLocked)

    when(controller.configDecorator.closePostalAddressEnabled) thenReturn closePostalAddressEnabled

    lazy val excludedCountries = List(
      Country("GREAT BRITAIN"),
      Country("SCOTLAND"),
      Country("ENGLAND"),
      Country("WALES"),
      Country("NORTHERN IRELAND")
    )
  }

  "Calling getPostalAddressCard" should {

    "return nothing when there are no person details" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = false
      override lazy val isLocked = true
      override lazy val userHasCorrespondenceAddress = false
      override lazy val userHasWelshLanguageUnitAddress = false
      override lazy val closePostalAddressEnabled = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = None, request = FakeRequest())

      cardBody shouldBe None
    }

    "return nothing when there are person details but no correspondence address" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val isLocked = true
      override lazy val userHasCorrespondenceAddress = false
      override lazy val userHasWelshLanguageUnitAddress = false
      override lazy val closePostalAddressEnabled = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe None
    }

    "return the correct markup when there is a correspondence address and the postal address can be updated when closePostalAddressToggle is off" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val isLocked = false
      override lazy val userHasWelshLanguageUnitAddress = false
      override lazy val closePostalAddressEnabled = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(postalAddress(buildPersonDetails, isLocked, excludedCountries, closePostalAddressEnabled))

    }

    "return the correct markup when there is a correspondence address and the postal address can be updated when closePostalAddressToggle is on" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val isLocked = false
      override lazy val userHasWelshLanguageUnitAddress = false
      override lazy val closePostalAddressEnabled = true

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(postalAddress(buildPersonDetails, isLocked, excludedCountries, closePostalAddressEnabled))

    }

    "return the correct markup when there is a correspondence address and the postal address cannot be updated" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val isLocked = true
      override lazy val userHasWelshLanguageUnitAddress = false
      override lazy val closePostalAddressEnabled = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(postalAddress(buildPersonDetails, isLocked, excludedCountries, false))

    }

    "return nothing when the correspondence address matches with a Welsh Language Unit" in new PostalAddressSetup {
      override lazy val userHasPersonDetails = true
      override lazy val userHasCorrespondenceAddress = true
      override lazy val isLocked = false
      override val userHasWelshLanguageUnitAddress = true
      override lazy val closePostalAddressEnabled = false

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe None
    }
  }

  "Calling getNationalInsuranceCard" should {

    trait LocalSetup {
      implicit def userRequest: UserRequest[_]

      lazy val cardBody = controller.getNationalInsuranceCard(userRequest.nino)
    }

    "always return the same markup" in new LocalSetup {

      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] = buildUserRequest(request = FakeRequest())

      cardBody shouldBe Some(nationalInsurance(userRequest.nino.get))
    }
  }

  "Calling getChangeNameCard" should {

    trait LocalSetup {

      implicit def userRequest: UserRequest[_]

      lazy val cardBody = controller.getChangeNameCard()

      def buildPersonDetails =
        PersonDetails(
          Person(
            Some("Firstname"),
            Some("Middlename"),
            Some("Lastname"),
            Some("FML"),
            Some("Dr"),
            Some("Phd."),
            Some("M"),
            Some(LocalDate.parse("1945-03-18")),
            Some(Fixtures.fakeNino)
          ),
          Some(Fixtures.buildFakeAddress),
          None
        )

    }

    "always return the correct markup when user has a name" in new LocalSetup {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = Some(buildPersonDetails), request = FakeRequest())

      cardBody shouldBe Some(changeName())
    }

    "always return None when user does not have a name available" in new LocalSetup {
      implicit val userRequest: UserRequest[AnyContentAsEmpty.type] =
        buildUserRequest(personDetails = None, userName = None, request = FakeRequest())

      cardBody shouldBe None
    }
  }
}
