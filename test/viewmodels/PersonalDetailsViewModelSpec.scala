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

package viewmodels

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.CountryHelper
import models._
import org.joda.time.LocalDate
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import reactivemongo.bson.BSONDateTime
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import views.html.ViewSpec
import views.html.personaldetails.partials.{AddressView, CorrespondenceAddressView}
import views.html.tags.formattedNino

import java.time.OffsetDateTime
import scala.util.Random

class PersonalDetailsViewModelSpec extends ViewSpec {

  private val generator = new Generator(new Random())

  private val testNino: Nino = generator.nextNino
  lazy val configDecorator = injected[ConfigDecorator]
  lazy val personalDetailsViewModel = injected[PersonalDetailsViewModel]
  lazy val addressView = injected[AddressView]
  lazy val correspondenceAddressView = injected[CorrespondenceAddressView]
  lazy val countryHelper = injected[CountryHelper]

  val fakeRequest = FakeRequest("", "")
  val userRequest = UserRequest(
    None,
    None,
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
    Credentials("", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    None,
    None,
    None,
    None,
    None,
    fakeRequest
  )

  val examplePerson = Person(
    Some("Example"),
    None,
    Some("User"),
    None,
    None,
    None,
    None,
    None,
    None
  )

  val exampleDetails = PersonDetails(
    examplePerson,
    None,
    None
  )

  val testAddress = Address(
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
  )

  def editedAddress(dateTime: OffsetDateTime) = EditResidentialAddress(BSONDateTime(dateTime.toInstant.toEpochMilli))
  def editedOtherAddress(dateTime: OffsetDateTime) = EditCorrespondenceAddress(
    BSONDateTime(dateTime.toInstant.toEpochMilli)
  )

  "getSignInDetailsRow" must {
    "return None" when {
      "user is not GG and profile URL is defined" in {
        val request = userRequest.copy(credentials = Credentials("", "Verify"), profile = Some("example.com"))
        val actual = personalDetailsViewModel.getSignInDetailsRow(request, messages)
        actual mustBe None
      }

      "user is GG and profile URL is not defined" in {
        val actual = personalDetailsViewModel.getSignInDetailsRow(userRequest, messages)
        actual mustBe None
      }

      "user is not GG and profile URL is not defined" in {
        val request = userRequest.copy(credentials = Credentials("", "Verify"))
        val actual = personalDetailsViewModel.getSignInDetailsRow(request, messages)
        actual mustBe None
      }

    }

    "return PersonalDetailsTableRowModel" when {
      "user is GG and profile url is defined" in {
        val profileUrl = "example.com"
        val expected = PersonalDetailsTableRowModel(
          "sign_in_details",
          "label.sign_in_details",
          HtmlFormat.raw(messages("label.sign_in_details_content")),
          "label.change",
          "label.your_gg_details",
          Some(profileUrl)
        )
        val request = userRequest.copy(profile = Some(profileUrl))
        val actual = personalDetailsViewModel.getSignInDetailsRow(request, messages)
        actual mustBe Some(expected)

      }
    }

  }

  "getPaperlessSettingsRow" must {
    "return None" when {
      "user is not gg" in {
        val request = userRequest.copy(credentials = Credentials("", "Verify"))
        val actual = personalDetailsViewModel.getPaperlessSettingsRow(request, messages)
        actual mustBe None
      }
    }

    "return PersonalDetailsTableRowModel" when {
      "user is gg" in {
        val expected = Some(
          PersonalDetailsTableRowModel(
            "paperless",
            "label.paperless_settings",
            HtmlFormat.raw(""),
            "label.change",
            "label.your_paperless_settings",
            Some(controllers.routes.PaperlessPreferencesController.managePreferences.url)
          )
        )

        val actual = personalDetailsViewModel.getPaperlessSettingsRow(userRequest, messages)
        actual mustBe expected
      }
    }
  }

  "getTrustedHelpersRow" must {
    "return None" when {
      "user is not verify" in {
        val actual = personalDetailsViewModel.getTrustedHelpersRow(userRequest, messages)
        actual mustBe None
      }
    }

    "return PersonalDetailsTableRowModel" when {
      "user is verify" in {
        val request = userRequest.copy(credentials = Credentials("", "Verify"))
        val actual = personalDetailsViewModel.getTrustedHelpersRow(request, messages)
        val expected = Some(
          PersonalDetailsTableRowModel(
            "trusted_helpers",
            "label.trusted_helpers",
            HtmlFormat.raw(messages("label.manage_trusted_helpers")),
            "label.change",
            "label.your_trusted_helpers",
            Some(configDecorator.manageTrustedHelpersUrl)
          )
        )
        actual mustBe expected
      }
    }
  }

  "getPersonDetailsTable" must {
    "contain name row" when {
      "name is defined in userRequest" in {
        val request = userRequest.copy(personDetails = Some(exampleDetails))
        val expected =
          PersonalDetailsTableRowModel(
            "name",
            "label.name",
            HtmlFormat.raw("Example User"),
            "label.change",
            "label.your_name",
            Some(configDecorator.changeNameLinkUrl)
          )
        val actual = personalDetailsViewModel.getPersonDetailsTable(None)(request)

        actual mustBe List(expected)
      }
    }

    "not contain name row" when {
      "personal details is not defined" in {
        val request = userRequest.copy(personDetails = None)
        val actual = personalDetailsViewModel.getPersonDetailsTable(None)(request)
        actual.isEmpty mustBe true
      }

      "name is empty" in {
        val request = userRequest.copy(personDetails =
          Some(exampleDetails.copy(person = examplePerson.copy(firstName = None, lastName = None)))
        )
        val actual = personalDetailsViewModel.getPersonDetailsTable(None)(request)
        actual.isEmpty mustBe true
      }
    }

    "contain nino row" when {
      "nino is defined" in {
        val actual = personalDetailsViewModel.getPersonDetailsTable(Some(testNino))(userRequest)
        val expected = PersonalDetailsTableRowModel(
          "national_insurance",
          "label.national_insurance",
          formattedNino(testNino),
          "label.view_national_insurance_letter",
          "",
          Some(controllers.routes.NiLetterController.printNationalInsuranceNumber.url)
        )
        actual mustBe List(expected)
      }
    }

    "not contain nino row" when {
      "nino is not defined" in {
        val request = userRequest.copy(personDetails = None)
        val actual = personalDetailsViewModel.getPersonDetailsTable(None)(request)
        actual.isEmpty mustBe true
      }
    }
  }

  "getAddressRow" must {
    "contain main address row" when {
      "main address is defined and it hasn't been changed" in {
        val details = exampleDetails.copy(address = Some(testAddress))
        val request = userRequest.copy(personDetails = Some(details))

        val actual = personalDetailsViewModel.getAddressRow(List.empty)(request, messages)
        val expected = PersonalDetailsTableRowModel(
          "main_address",
          "label.main_address",
          addressView(testAddress, countryHelper.excludedCountries),
          "label.change",
          "label.your_main_home",
          Some(controllers.address.routes.TaxCreditsChoiceController.onPageLoad.url)
        )

        actual.mainAddress mustBe Some(expected)
      }

      "main address is defined and it has been changed" in {
        val details = exampleDetails.copy(address = Some(testAddress))
        val request = userRequest.copy(personDetails = Some(details))

        val actual = personalDetailsViewModel.getAddressRow(
          List(AddressJourneyTTLModel(testNino.nino, editedAddress(OffsetDateTime.now())))
        )(request, messages)
        val expected = PersonalDetailsTableRowModel(
          "main_address",
          "label.main_address",
          addressView(testAddress, countryHelper.excludedCountries),
          "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
          "label.your_main_home",
          None
        )

        actual.mainAddress mustBe Some(expected)
      }
    }

    "not contain main address row" when {
      "person details is not defined" in {
        val request = userRequest.copy(personDetails = None)
        val actual = personalDetailsViewModel.getAddressRow(List.empty)(request, messages)
        actual.mainAddress.isEmpty mustBe true
      }

      "address is not defined" in {
        val details =
          exampleDetails.copy(address = None, person = examplePerson.copy(firstName = None, lastName = None))
        val request = userRequest.copy(personDetails = Some(details))
        val actual = personalDetailsViewModel.getAddressRow(List.empty)(request, messages)
        actual.mainAddress.isEmpty mustBe true
      }
    }

    "contain postal address row" when {
      "postal address is defined and it hasn't been changed" in {
        val details = exampleDetails.copy(correspondenceAddress = Some(testAddress))
        val request = userRequest.copy(personDetails = Some(details))

        val actual = personalDetailsViewModel.getAddressRow(List.empty)(request, messages)
        val expected = PersonalDetailsTableRowModel(
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(Some(testAddress), countryHelper.excludedCountries),
          "label.change",
          "label.your.postal_address",
          Some(controllers.address.routes.PostalDoYouLiveInTheUKController.onPageLoad.url)
        )

        actual.postalAddress mustBe Some(expected)
      }

      "postal address is defined and it has been changed" in {
        val details = exampleDetails.copy(correspondenceAddress = Some(testAddress))
        val request = userRequest.copy(personDetails = Some(details))

        val actual = personalDetailsViewModel.getAddressRow(
          List(AddressJourneyTTLModel(testNino.nino, editedOtherAddress(OffsetDateTime.now())))
        )(request, messages)
        val expected = PersonalDetailsTableRowModel(
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(Some(testAddress), countryHelper.excludedCountries),
          "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
          "label.your.postal_address",
          None
        )

        actual.postalAddress mustBe Some(expected)
      }

      "postal address is not defined and main address is defined" in {
        val details = exampleDetails.copy(correspondenceAddress = None, address = Some(testAddress))
        val request = userRequest.copy(personDetails = Some(details))

        val actual = personalDetailsViewModel.getAddressRow(List.empty)(request, messages)
        val expectedPostalAddress = PersonalDetailsTableRowModel(
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(None, countryHelper.excludedCountries),
          "label.change",
          "label.your.postal_address",
          Some(controllers.address.routes.PostalDoYouLiveInTheUKController.onPageLoad.url)
        )

        actual.postalAddress mustBe Some(expectedPostalAddress)
      }
    }

    "not contain postal address" when {
      "personal details are not defined" in {
        val request = userRequest.copy(personDetails = None)

        val actual = personalDetailsViewModel.getAddressRow(List.empty)(request, messages)

        actual.postalAddress.isEmpty mustBe true
      }
    }
  }
}
