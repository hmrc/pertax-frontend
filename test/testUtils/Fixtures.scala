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

package testUtils

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import controllers.auth.{AuthJourney, FakeAuthJourney}
import models._
import models.addresslookup.{AddressRecord, Country, RecordSet, Address => PafAddress}
import models.admin._
import models.dto.AddressDto
import org.mockito.ArgumentMatchers.any
import org.mockito.{ArgumentMatchers, MockitoSugar}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers, Injecting}
import play.twirl.api.Html
import repositories.EditAddressLockRepository
import uk.gov.hmrc.auth.core.retrieve.v2.TrustedHelper
import uk.gov.hmrc.domain.{Generator, Nino, SaUtr, SaUtrGenerator}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.mongoFeatureToggles.model.FeatureFlag
import uk.gov.hmrc.mongoFeatureToggles.services.FeatureFlagService
import uk.gov.hmrc.play.partials.FormPartialRetriever

import java.time.temporal.ChronoField
import java.time.{Instant, LocalDate}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

trait PafFixtures {

  val exampleCountryUK: Country    = Country("UK", "United Kingdom")
  val subDivision: Option[Country] = Some(Country("GB-ENG", "England"))

  val fakeStreetPafAddressRecord: AddressRecord = AddressRecord(
    "GB101",
    PafAddress(
      List("1 Fake Street", "Fake Town", "Fake City"),
      Some("Fake Region"),
      None,
      "AA1 1AA",
      subDivision,
      exampleCountryUK
    ),
    "en"
  )

  val oneOtherPlacePafAddress: PafAddress =
    PafAddress(
      List("2 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 1AA",
      subDivision,
      exampleCountryUK
    )

  val twoOtherPlacePafAddress: PafAddress =
    PafAddress(
      List("3 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 1AA",
      Some(Country("GB-SCT", "Scotland")),
      exampleCountryUK
    )

  val otherPlacePafDifferentPostcodeAddress: PafAddress =
    PafAddress(
      List("3 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 2AA",
      subDivision,
      exampleCountryUK
    )

  val oneOtherPlacePafAddressRecord: AddressRecord               = AddressRecord("GB990091234514", oneOtherPlacePafAddress, "en")
  val twoOtherPlacePafAddressRecord: AddressRecord               = AddressRecord("GB990091234515", twoOtherPlacePafAddress, "en")
  val otherPlacePafDifferentPostcodeAddressRecord: AddressRecord =
    AddressRecord("GB990091234516", otherPlacePafDifferentPostcodeAddress, "en")

  val oneAndTwoOtherPlacePafRecordSet: RecordSet = RecordSet(
    List(
      oneOtherPlacePafAddressRecord,
      twoOtherPlacePafAddressRecord
    )
  )

  val newPostcodePlacePafRecordSet: RecordSet = RecordSet(
    List(
      otherPlacePafDifferentPostcodeAddressRecord
    )
  )

  val twoOtherPlaceRecordSet: RecordSet = RecordSet(
    List(twoOtherPlacePafAddressRecord)
  )
}

trait TaiFixtures {

  def buildTaxComponents: TaxComponents = TaxComponents(List("EmployerProvidedServices", "PersonalPensionPayments"))
}

trait TaxCalculationFixtures {
  def buildTaxCalculation: TaxCalculation =
    TaxCalculation("Overpaid", BigDecimal(84.23), 2015, Some("REFUND"), None, None, None)

  def buildTaxYearReconciliations: List[TaxYearReconciliation] =
    List(TaxYearReconciliation(2015, Balanced), TaxYearReconciliation(2016, Balanced))
  def buildTrustedHelper: Option[TrustedHelper]                = Some(TrustedHelper("John", "Smith", "Some Url", "AH498813B"))
}

trait CitizenDetailsFixtures {
  def buildPersonDetails: PersonDetails =
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
      None
    )

  def buildPersonDetailsCorrespondenceAddress: PersonDetails =
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
      Some(buildFakeCorrespondenceAddress)
    )

  def buildFakeAddress: Address = Address(
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

  def buildFakeCorrespondenceAddress: Address = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(LocalDate.of(2015, 3, 15)),
    None,
    Some("Correspondence"),
    isRls = false
  )

  def buildFakeAddressWithEndDate: Address = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(LocalDate.now),
    Some(LocalDate.now),
    Some("Correspondence"),
    isRls = false
  )

  def buildFakeJsonAddress: JsValue = Json.toJson(buildFakeAddress)

  def asAddressDto(l: List[(String, String)]): AddressDto = AddressDto.ukForm.bind(l.toMap).get

  def asInternationalAddressDto(l: List[(String, String)]): AddressDto = AddressDto.internationalForm.bind(l.toMap).get

  def fakeStreetTupleListAddressForUnmodified: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListInternationalAddress: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("country", "South Georgia and South Sandwich Island")
  )

  def fakeStreetTupleListAddressForUnmodifiedLowerCase: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "aa1 1aa")
  )

  def fakeStreetTupleListAddressForUnmodifiedNoSpaceInPostcode: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA11AA")
  )

  def fakeStreetTupleListAddressForManuallyEntered: List[(String, String)] = List(
    ("line1", "1 Fake Street"),
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForModified: List[(String, String)] = List(
    ("line1", "11 Fake Street"), //Note 11 not 1
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 1AA")
  )

  def fakeStreetTupleListAddressForModifiedPostcode: List[(String, String)] = List(
    ("line1", "11 Fake Street"), //Note 11 not 1
    ("line2", "Fake Town"),
    ("line3", "Fake City"),
    ("line4", "Fake Region"),
    ("line5", ""),
    ("postcode", "AA1 2AA")
  )
}

object Fixtures extends PafFixtures with TaiFixtures with CitizenDetailsFixtures with TaxCalculationFixtures {

  val fakeNino: Nino = Nino(new Generator(new Random()).nextNino.nino)

  val saUtr: SaUtr = new SaUtrGenerator().nextSaUtr

  val etag = "1"

  def buildFakeRequestWithSessionId(method: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(method, "/personal-account").withSession("sessionId" -> "FAKE_SESSION_ID")

  def buildFakeRequestWithAuth(
    method: String,
    uri: String = "/personal-account"
  ): FakeRequest[AnyContentAsEmpty.type] = {
    val session = Map(
      SessionKeys.sessionId            -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> Instant.now().get(ChronoField.MILLI_OF_SECOND).toString
    )

    FakeRequest(method, uri).withSession(session.toList: _*)
  }

  def buildUnusedAllowance: UnusedAllowance = UnusedAllowance(BigDecimal(4000.00))

  def buildFakePersonDetails: PersonDetails = PersonDetails(buildFakePerson, None, None)

  def buildFakePerson: Person =
    Person(
      Some("Firstname"),
      Some("Middlename"),
      Some("Lastname"),
      Some("FML"),
      Some("Mr"),
      None,
      Some("M"),
      Some(LocalDate.parse("1931-01-17")),
      Some(Fixtures.fakeNino)
    )

}

trait BaseSpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with BeforeAndAfterEach
    with MockitoSugar
    with ScalaFutures
    with IntegrationPatience
    with Injecting {
  this: Suite =>

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockPartialRetriever: FormPartialRetriever = mock[FormPartialRetriever]
  when(mockPartialRetriever.getPartialContentAsync(any(), any(), any())(any(), any()))
    .thenReturn(Future.successful(Html("")))

  val mockEditAddressLockRepository: EditAddressLockRepository = mock[EditAddressLockRepository]

  val configValues: Map[String, Any] =
    Map(
      "cookie.encryption.key"         -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"            -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"           -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled"               -> false,
      "auditing.enabled"              -> false
    )

  val mockFeatureFlagService: FeatureFlagService = mock[FeatureFlagService]

  protected def localGuiceApplicationBuilder(
    saUser: SelfAssessmentUserType = NonFilerSelfAssessmentUser,
    personDetails: Option[PersonDetails] = None
  ): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[FormPartialRetriever].toInstance(mockPartialRetriever),
        bind[EditAddressLockRepository].toInstance(mockEditAddressLockRepository),
        bind[AuthJourney].toInstance(new FakeAuthJourney(saUser, personDetails)),
        bind[FeatureFlagService].toInstance(mockFeatureFlagService)
      )
      .configure(configValues)

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit lazy val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  lazy val config: ConfigDecorator = app.injector.instanceOf[ConfigDecorator]

  override def beforeEach(): Unit = {
    super.beforeEach()
    org.mockito.MockitoSugar.reset(mockFeatureFlagService)
    AllFeatureFlags.list.foreach { flag =>
      when(mockFeatureFlagService.get(ArgumentMatchers.eq(flag)))
        .thenReturn(Future.successful(FeatureFlag(flag, isEnabled = false)))
    }

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(GetPersonFromCitizenDetailsToggle)))
      .thenReturn(Future.successful(FeatureFlag(GetPersonFromCitizenDetailsToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(AddressChangeAllowedToggle)))
      .thenReturn(Future.successful(FeatureFlag(AddressChangeAllowedToggle, isEnabled = true)))

    when(mockFeatureFlagService.get(ArgumentMatchers.eq(DfsDigitalFormFrontendAvailableToggle)))
      .thenReturn(Future.successful(FeatureFlag(DfsDigitalFormFrontendAvailableToggle, isEnabled = true)))

  }
}

trait ActionBuilderFixture extends ActionBuilder[UserRequest, AnyContent] {
  override def invokeBlock[A](a: Request[A], block: UserRequest[A] => Future[Result]): Future[Result]
  override def parser: BodyParser[AnyContent]               = Helpers.stubBodyParser()
  override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
