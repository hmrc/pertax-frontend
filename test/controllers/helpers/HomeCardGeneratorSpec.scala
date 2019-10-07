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
import controllers.auth.requests.UserRequest
import models._
import org.joda.time.DateTime
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.test.FakeRequest
import uk.gov.hmrc.auth.core.ConfidenceLevel
import uk.gov.hmrc.auth.core.retrieve.Name
import uk.gov.hmrc.domain.SaUtr
import util.{BaseSpec, Fixtures}
import views.html.cards.home._

class HomeCardGeneratorSpec extends BaseSpec with I18nSupport {

  override def messagesApi: MessagesApi = injected[MessagesApi]

  "Calling getPayAsYouEarnCard" should {
    "return nothing when called with no Pertax user" in {

      implicit val userRequest = UserRequest(
        None,
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "GovernmentGateway",
        ConfidenceLevel.L50,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getPayAsYouEarnCard(TaxComponentsUnreachableState)

      cardBody shouldBe None
    }

    //TODO: How can you be a pertax user without PAYE?
    "return nothing when called with a Pertax user that is not PAYE" in {

      implicit val userRequest = UserRequest(
        None,
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getPayAsYouEarnCard(TaxComponentsUnreachableState)

      cardBody shouldBe None
    }

    "return no content when called with with a Pertax user that is PAYE but has no tax summary" in {

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getPayAsYouEarnCard(TaxComponentsNotAvailableState)

      cardBody shouldBe None
    }

    //TODO: what will the request look like when the endpoint is returning an error??
    "return the static version of the markup (no card actions) when called with with a user that is PAYE but there was an error calling the endpoint" in {

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getPayAsYouEarnCard(TaxComponentsUnreachableState)

      cardBody shouldBe Some(payAsYouEarn())
    }

    "return the static version of the markup (no card actions) when called with with a Pertax user that is PAYE but the tax summary call is disabled" in {

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getPayAsYouEarnCard(TaxComponentsDisabledState)

      cardBody shouldBe Some(payAsYouEarn())
    }

    "return correct markup when called with with a Pertax user that is PAYE" in {

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        NonFilerSelfAssessmentUser,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getPayAsYouEarnCard(TaxComponentsAvailableState(Fixtures.buildTaxComponents))

      cardBody shouldBe Some(payAsYouEarn())
    }
  }

  "Calling getSelfAssessmentCard" should {
    val taxYear = "1819"
    val nextDeadlineTaxYear = 2019

    "return correct markup when called with ActivatedOnlineFilerSelfAssessmentUser" in {
      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        saUserType,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getSelfAssessmentCard(saUserType, 2019)

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return correct markup when called with NotYetActivatedOnlineFilerSelfAssessmentUser" in {
      val saUserType = NotYetActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        saUserType,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getSelfAssessmentCard(saUserType, 2019)

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return correct markup when called with AmbiguousFilerSelfAssessmentUser" in {
      val saUserType = AmbiguousFilerSelfAssessmentUser(SaUtr("1111111111"))

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        saUserType,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getSelfAssessmentCard(saUserType, 2019)

      cardBody shouldBe Some(selfAssessment(saUserType, taxYear, nextDeadlineTaxYear.toString))
    }

    "return nothing when called with NonFilerSelfAssessmentUser" in {
      val saUserType = NonFilerSelfAssessmentUser

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        saUserType,
        "GovernmentGateway",
        ConfidenceLevel.L200,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getSelfAssessmentCard(saUserType, 2019)

      cardBody shouldBe None
    }

    "return nothing for a verify user" in {
      val saUserType = ActivatedOnlineFilerSelfAssessmentUser(SaUtr("1111111111"))

      implicit val userRequest = UserRequest(
        Some(Fixtures.fakeNino),
        Some(UserName(Name(Some("Firstname"), Some("Lastname")))),
        Some(DateTime.parse("1982-04-30T00:00:00.000+01:00")),
        saUserType,
        "Verify",
        ConfidenceLevel.L500,
        None,
        None,
        None,
        None,
        FakeRequest()
      )

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getSelfAssessmentCard(saUserType, 2019)

      cardBody shouldBe None
    }
  }

  "Calling getNationalInsuranceCard" should {
    "always return the same markup" in {

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getNationalInsuranceCard()

      cardBody shouldBe Some(nationalInsurance())
    }
  }

  "Calling getTaxCreditsCard" should {
    "always return the same markup when taxCreditsPaymentLinkEnabled is enabled" in {
      lazy val showTaxCreditsPaymentLink = true

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getTaxCreditsCard(showTaxCreditsPaymentLink)

      cardBody shouldBe Some(taxCredits(showTaxCreditsPaymentLink))
    }

    "always return the same markup when taxCreditsPaymentLinkEnabled is disabled" in {
      lazy val showTaxCreditsPaymentLink = false

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getTaxCreditsCard(showTaxCreditsPaymentLink)

      cardBody shouldBe Some(taxCredits(showTaxCreditsPaymentLink))
    }
  }

  "Calling getChildBenefitCard" should {
    "always return the same markup" in {
      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getChildBenefitCard()

      cardBody shouldBe Some(childBenefit())
    }
  }

  "Calling getMarriageAllowanceCard" should {
    "return correct markup when called with a user who has tax summary and receives Marriage Allowance" in {
      val hasTaxComponents: Boolean = true
      val taxComponents = Seq("MarriageAllowanceReceived")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getMarriageAllowanceCard(tc)

      cardBody shouldBe Some(marriageAllowance(tc))
    }

    "return nothing when called with a user who has tax summary and transfers Marriage Allowance" in {
      val hasTaxComponents: Boolean = true
      val taxComponents = Seq("MarriageAllowanceTransferred")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getMarriageAllowanceCard(tc)

      cardBody shouldBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has no tax summary" in {
      val hasTaxComponents = false
      val taxComponents = Seq()

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getMarriageAllowanceCard(tc)

      cardBody shouldBe Some(marriageAllowance(tc))
    }

    "return correct markup when called with a user who has tax summary but no marriage allowance" in {
      val hasTaxComponents = true
      val taxComponents = Seq("MedicalInsurance")

      lazy val tc =
        if (hasTaxComponents) Some(Fixtures.buildTaxComponents.copy(taxComponents = taxComponents)) else None

      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getMarriageAllowanceCard(tc)

      cardBody shouldBe Some(marriageAllowance(tc))
    }
  }

  "Calling getStatePensionCard" should {
    "always return the same markup" in {
      val serviceUnderTest = new HomeCardGenerator()(configDecorator = injected[ConfigDecorator])
      lazy val cardBody = serviceUnderTest.getStatePensionCard()

      cardBody shouldBe Some(statePension())
    }
  }
}
