/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import config.ConfigDecorator
import connectors.PreferencesFrontendConnector
import controllers.auth.requests.UserRequest
import controllers.controllershelpers.CountryHelper
import models._
import models.admin.AddressChangeAllowedToggle
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import play.api.Application
import play.api.inject.bind
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.twirl.api.HtmlFormat
import services.CitizenDetailsService
import testUtils.Fixtures
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Credentials
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import views.html.ViewSpec
import views.html.personaldetails.partials.{AddressUnavailableView, AddressView, CorrespondenceAddressView}
import views.html.tags.formattedNino

import java.time.{Instant, LocalDate}
import scala.concurrent.Future
import scala.util.Random
import org.mockito.Mockito.{reset, when}

class PersonalDetailsViewModelSpec extends ViewSpec {

  private val generator = new Generator(new Random())

  private val testNino: Nino                                      = generator.nextNino
  lazy val configDecorator: ConfigDecorator                       = inject[ConfigDecorator]
  lazy val addressView: AddressView                               = inject[AddressView]
  lazy val correspondenceAddressView: CorrespondenceAddressView   = inject[CorrespondenceAddressView]
  lazy val countryHelper: CountryHelper                           = inject[CountryHelper]
  lazy val mockPreferencesConnector: PreferencesFrontendConnector = mock[PreferencesFrontendConnector]
  lazy val personalDetailsViewModel: PersonalDetailsViewModel     = inject[PersonalDetailsViewModel]
  lazy val addressUnavailableView: AddressUnavailableView         = inject[AddressUnavailableView]
  lazy val mockCitizenDetailsService: CitizenDetailsService       = mock[CitizenDetailsService]

  override implicit lazy val app: Application = localGuiceApplicationBuilder()
    .overrides(
      bind[PreferencesFrontendConnector].toInstance(mockPreferencesConnector),
      bind[CitizenDetailsService].toInstance(mockCitizenDetailsService)
    )
    .build()

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("", "")
  val userRequest: UserRequest[AnyContentAsEmpty.type] = UserRequest(
    testNino,
    ActivatedOnlineFilerSelfAssessmentUser(SaUtr(new SaUtrGenerator().nextSaUtr.utr)),
    Credentials("", "GovernmentGateway"),
    ConfidenceLevel.L200,
    None,
    Set(),
    None,
    None,
    fakeRequest,
    UserAnswers.empty
  )

  val examplePerson: Person = Person(
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

  val exampleDetails: PersonDetails = PersonDetails(
    examplePerson,
    None,
    None
  )

  val testAddress: Address = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(LocalDate.of(2015, 3, 15)),
    None,
    Some("Residential"),
    isRls = false
  )

  def editedAddress(): EditResidentialAddress = EditResidentialAddress(Instant.now())

  def editedOtherAddress(): EditCorrespondenceAddress = EditCorrespondenceAddress(Instant.now())

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockFeatureFlagService)
    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
      .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))
  }

  "getSignInDetailsRow" must {
    "return None" when {
      "user is GG and profile URL is not defined" in {
        val actual = personalDetailsViewModel.getSignInDetailsRow(userRequest, messages)
        actual mustBe None
      }
    }

    "return PersonalDetailsTableRowModel" when {
      "user is GG and profile url is defined" in {
        val profileUrl = "example.com"
        val expected   = PersonalDetailsTableRowModel(
          "sign_in_details",
          "label.sign_in_details",
          HtmlFormat.raw(messages("label.sign_in_details_content")),
          "label.change",
          "label.your_gg_details",
          Some(profileUrl)
        )
        val request    = userRequest.copy(profile = Some(profileUrl))
        val actual     = personalDetailsViewModel.getSignInDetailsRow(request, messages)
        actual mustBe Some(expected)

      }
    }

  }

  "getPaperlessSettingsRow" must {
    "return PersonalDetailsTableRowModel" in {
      val expected = Some(
        PersonalDetailsTableRowModel(
          "paperless",
          messages("label.paperless_settings"),
          HtmlFormat.raw(messages("label.paperless_opt_in_response")),
          messages("label.paperless_opt_in_link"),
          messages("label.paperless_opt_in_hidden"),
          Some("link")
        )
      )

      when(mockPreferencesConnector.getPaperlessStatus(any(), any())(any())).thenReturn(
        EitherT.rightT[Future, UpstreamErrorResponse](PaperlessStatusOptIn("link"): PaperlessMessagesStatus)
      )

      val actual = personalDetailsViewModel.getPaperlessSettingsRow(userRequest, messages, ec)
      actual.futureValue mustBe expected
    }
  }

  "getTrustedHelpersRow" must {
    "return None" when {
      "user is not verify" in {
        val actual   = personalDetailsViewModel.getTrustedHelpersRow(messages)
        val expected = Some(
          PersonalDetailsTableRowModel(
            "trusted_helpers",
            "label.trusted_helpers",
            HtmlFormat.raw(messages("label.manage_trusted_helpers")),
            "label.manage",
            "label.your_trusted_helpers",
            Some(configDecorator.manageTrustedHelpersUrl)
          )
        )
        actual mustBe expected
      }
    }

    "return PersonalDetailsTableRowModel" when {
      "user is verify" in {
        val actual   = personalDetailsViewModel.getTrustedHelpersRow(messages)
        val expected = Some(
          PersonalDetailsTableRowModel(
            "trusted_helpers",
            "label.trusted_helpers",
            HtmlFormat.raw(messages("label.manage_trusted_helpers")),
            "label.manage",
            "label.your_trusted_helpers",
            Some(configDecorator.manageTrustedHelpersUrl)
          )
        )
        actual mustBe expected
      }
    }
  }

  "getPersonDetailsTable" must {
    "contain the ninoSaveUrl" when {
      "nino is defined in model" in {
        val actual   = personalDetailsViewModel.getPersonDetailsTable(Some(testNino), None)(userRequest)
        val expected = PersonalDetailsTableRowModel(
          "national_insurance",
          "label.national_insurance",
          formattedNino(testNino),
          "label.view_national_insurance_letter",
          "",
          Some(configDecorator.ptaNinoSaveUrl)
        )

        actual.futureValue mustBe List(expected)
      }
    }

    "contain nino row" when {
      "nino is defined" in {
        val actual   = personalDetailsViewModel.getPersonDetailsTable(Some(testNino), None)(userRequest)
        val expected = PersonalDetailsTableRowModel(
          "national_insurance",
          "label.national_insurance",
          formattedNino(testNino),
          "label.view_national_insurance_letter",
          "",
          Some(configDecorator.ptaNinoSaveUrl)
        )
        actual.futureValue mustBe List(expected)
      }
    }

    "not contain nino row" when {
      "nino is not defined" in {
        val request = userRequest
        val actual  = personalDetailsViewModel.getPersonDetailsTable(None, None)(request)
        actual.futureValue.isEmpty mustBe true
      }
    }
  }

  "getAddressRow" must {

    "display address unavailable content" when {
      "AddressChangeAllowedToggle toggle switched off" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = false)))

        val actual   = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)
        val expected =
          PersonalDetailsTableRowModel(
            id = "main_address",
            titleMessage = "label.main_address",
            content = addressUnavailableView(displayAllLettersLine = false),
            linkTextMessage = "",
            visuallyhiddenText = "label.your_main_home",
            linkUrl = None
          )

        actual.futureValue.mainAddress mustBe Some(expected)
      }
    }

    "contain main address row" when {
      "main address is defined and it hasn't been changed" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        val actual   = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)
        val expected = PersonalDetailsTableRowModel(
          "main_address",
          "label.main_address",
          addressView(testAddress, countryHelper.excludedCountries),
          "label.change",
          "label.your_main_home",
          Some(controllers.address.routes.DoYouLiveInTheUKController.onPageLoad.url)
        )

        actual.futureValue.mainAddress mustBe Some(expected)
      }

      "main address is defined and it has been changed" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        val actual   = personalDetailsViewModel.getAddressRow(
          Some(personDetails),
          List(AddressJourneyTTLModel(testNino.nino, editedAddress()))
        )(messages)
        val expected = PersonalDetailsTableRowModel(
          "main_address",
          "label.main_address",
          addressView(testAddress, countryHelper.excludedCountries),
          "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
          "label.your_main_home",
          None
        )

        actual.futureValue.mainAddress mustBe Some(expected)
      }
    }

    "not contain main address row" when {
      "person details is not defined" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, None)

        val actual = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)
        actual.futureValue.mainAddress.isEmpty mustBe true
      }

      "address is not defined" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, None)

        val actual = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)
        actual.futureValue.mainAddress.isEmpty mustBe true
      }
    }

    "display address unavailable content for postal address row" when {
      "AddressChangeAllowedToggle toggle switched off" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, address)

        when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
          .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = false)))

        val actual   = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)
        val expected =
          PersonalDetailsTableRowModel(
            id = "postal_address",
            titleMessage = "label.postal_address",
            content = addressUnavailableView(displayAllLettersLine = true),
            linkTextMessage = "",
            visuallyhiddenText = "label.your.postal_address",
            linkUrl = None
          )
        actual.futureValue.postalAddress mustBe Some(expected)

      }
    }

    "contain postal address row" when {
      "postal address is defined and it hasn't been changed" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, address)

        val actual   = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)
        val expected = PersonalDetailsTableRowModel(
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(Some(testAddress), countryHelper.excludedCountries),
          "label.change",
          "label.your.postal_address",
          Some(controllers.address.routes.PostalDoYouLiveInTheUKController.onPageLoad.url)
          // TODO: If start change of address page experiment is successful replace above line with below
          // Some(controllers.address.routes.StartChangeOfAddressController.onPageLoad(PostalAddrType).url)
        )

        actual.futureValue.postalAddress mustBe Some(expected)
      }

      "postal address is defined and it has been changed" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, address)

        val actual   = personalDetailsViewModel.getAddressRow(
          Some(personDetails),
          List(AddressJourneyTTLModel(testNino.nino, editedOtherAddress()))
        )(messages)
        val expected = PersonalDetailsTableRowModel(
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(Some(testAddress), countryHelper.excludedCountries),
          "label.you_can_only_change_this_address_once_a_day_please_try_again_tomorrow",
          "label.your.postal_address",
          None
        )

        actual.futureValue.postalAddress mustBe Some(expected)
      }

      "postal address is not defined and main address is defined" in {
        val address       = Fixtures.buildPersonDetailsCorrespondenceAddress.address.map(_.copy(isRls = true))
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, address, None)

        val actual                = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)
        val expectedPostalAddress = PersonalDetailsTableRowModel(
          "postal_address",
          "label.postal_address",
          correspondenceAddressView(Some(testAddress), countryHelper.excludedCountries),
          "label.change",
          "label.your.postal_address",
          Some(controllers.address.routes.PostalDoYouLiveInTheUKController.onPageLoad.url),
          // TODO: If start change of address page experiment is successful replace above line with below
          // Some(controllers.address.routes.StartChangeOfAddressController.onPageLoad(PostalAddrType).url),
          isPostalAddressSame = true
        )

        actual.futureValue.postalAddress mustBe Some(expectedPostalAddress)
      }
    }

    "not contain postal address" when {
      "personal details are not defined" in {
        val person        = Fixtures.buildPersonDetailsCorrespondenceAddress.person
        val personDetails = PersonDetails(person, None, None)

        val actual = personalDetailsViewModel.getAddressRow(Some(personDetails), List.empty)(messages)

        actual.futureValue.postalAddress.isEmpty mustBe true
      }
    }
  }

  "getManageTaxAgentsRow" must {
    "render the correct manageTaxAgentsUrl" in {
      val result = personalDetailsViewModel.getManageTaxAgentsRow(messages)

      result.get.linkUrl mustBe Some(s"http://localhost:9435/agent-client-relationships/manage-your-tax-agents")
    }
  }
}
