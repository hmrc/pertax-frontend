/*
 * Copyright 2022 HM Revenue & Customs
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

package util

import config.ConfigDecorator
import controllers.auth.requests.UserRequest
import models._
import models.addresslookup.{AddressRecord, Country, RecordSet, Address => PafAddress}
import models.dto.AddressDto
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.{PatienceConfiguration, ScalaFutures}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc._
import play.api.test.{FakeRequest, Helpers}
import play.twirl.api.Html
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}
import uk.gov.hmrc.play.partials.FormPartialRetriever
import uk.gov.hmrc.renderer.TemplateRenderer
import uk.gov.hmrc.time.DateTimeUtils._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Random

trait PafFixtures {
  val exampleCountryUK = Country("UK", "United Kingdom")
  val subDivision = Some(Country("GB-ENG", "England"))

  val fakeStreetPafAddressRecord = AddressRecord(
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

  val oneOtherPlacePafAddress =
    PafAddress(
      List("2 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 1AA",
      subDivision,
      exampleCountryUK
    )
  val twoOtherPlacePafAddress =
    PafAddress(
      List("3 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 1AA",
      Some(Country("GB-SCT", "Scotland")),
      exampleCountryUK
    )
  val otherPlacePafDifferentPostcodeAddress =
    PafAddress(
      List("3 Other Place", "Some District"),
      Some("Anytown"),
      None,
      "AA1 2AA",
      subDivision,
      exampleCountryUK
    )

  val oneOtherPlacePafAddressRecord = AddressRecord("GB990091234514", oneOtherPlacePafAddress, "en")
  val twoOtherPlacePafAddressRecord = AddressRecord("GB990091234515", twoOtherPlacePafAddress, "en")
  val otherPlacePafDifferentPostcodeAddressRecord =
    AddressRecord("GB990091234516", otherPlacePafDifferentPostcodeAddress, "en")

  val oneAndTwoOtherPlacePafRecordSet = RecordSet(
    List(
      oneOtherPlacePafAddressRecord,
      twoOtherPlacePafAddressRecord
    )
  )

  val newPostcodePlacePafRecordSet = RecordSet(
    List(
      otherPlacePafDifferentPostcodeAddressRecord
    )
  )

  val twoOtherPlaceRecordSet = RecordSet(
    List(twoOtherPlacePafAddressRecord)
  )
}

trait TaiFixtures {

  def buildTaxComponents: TaxComponents = TaxComponents(Seq("EmployerProvidedServices", "PersonalPensionPayments"))
}

trait TaxCalculationFixtures {
  def buildTaxCalculation = TaxCalculation("Overpaid", BigDecimal(84.23), 2015, Some("REFUND"), None, None, None)

  def buildTaxYearReconciliations: List[TaxYearReconciliation] =
    List(TaxYearReconciliation(2015, Balanced), TaxYearReconciliation(2016, Balanced))
}

trait CitizenDetailsFixtures {
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
      None
    )

  def buildPersonDetailsCorrespondenceAddress =
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

  def buildFakeAddress = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(new LocalDate(2015, 3, 15)),
    None,
    Some("Residential"),
    false
  )

  def buildFakeCorrespondenceAddress = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(new LocalDate(2015, 3, 15)),
    None,
    Some("Correspondence"),
    false
  )

  def buildFakeAddressWithEndDate = Address(
    Some("1 Fake Street"),
    Some("Fake Town"),
    Some("Fake City"),
    Some("Fake Region"),
    None,
    Some("AA1 1AA"),
    None,
    Some(new LocalDate(now)),
    Some(new LocalDate(now)),
    Some("Correspondence"),
    false
  )

  def buildFakeJsonAddress = Json.toJson(buildFakeAddress)

  def asAddressDto(l: List[(String, String)]) = AddressDto.ukForm.bind(l.toMap).get

  def asInternationalAddressDto(l: List[(String, String)]) = AddressDto.internationalForm.bind(l.toMap).get

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

  def fakeStreetTupleListAddressForManualyEntered: List[(String, String)] = List(
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

  val fakeNino = Nino(new Generator(new Random()).nextNino.nino)

  def buildFakeRequestWithSessionId(method: String) =
    FakeRequest(method, "/personal-account").withSession("sessionId" -> "FAKE_SESSION_ID")

  def buildFakeRequestWithAuth(
    method: String,
    uri: String = "/personal-account"
  ): FakeRequest[AnyContentAsEmpty.type] = {
    val session = Map(
      SessionKeys.sessionId            -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> now.getMillis.toString
    )

    FakeRequest(method, uri).withSession(session.toList: _*)
  }

  def buildFakeRequestWithVerify(
    method: String,
    uri: String = "/personal-account"
  ): FakeRequest[AnyContentAsEmpty.type] = {
    val session = Map(
      SessionKeys.sessionId            -> s"session-${UUID.randomUUID()}",
      SessionKeys.lastRequestTimestamp -> now.getMillis.toString
    )

    FakeRequest(method, uri).withSession(session.toList: _*)
  }

  def buildUnusedAllowance = UnusedAllowance(BigDecimal(4000.00))

  def buildFakePersonDetails = PersonDetails(buildFakePerson, None, None)

  def buildFakePerson =
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
    extends AnyWordSpec with GuiceOneAppPerSuite with Matchers with PatienceConfiguration with BeforeAndAfterEach
    with MockitoSugar with ScalaFutures {
  this: Suite =>

  implicit val hc = HeaderCarrier()

  val mockPartialRetriever = mock[FormPartialRetriever]

  when(mockPartialRetriever.getPartialContent(any(), any(), any())(any(), any())) thenReturn Html("")

  val configValues =
    Map(
      "cookie.encryption.key"         -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "sso.encryption.key"            -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "queryParameter.encryption.key" -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "json.encryption.key"           -> "gvBoGdgzqG1AarzF1LY0zQ==",
      "metrics.enabled"               -> false,
      "auditing.enabled"              -> false
    )

  protected def localGuiceApplicationBuilder(): GuiceApplicationBuilder =
    GuiceApplicationBuilder()
      .overrides(
        bind[TemplateRenderer].toInstance(MockTemplateRenderer),
        bind[FormPartialRetriever].toInstance(mockPartialRetriever)
      )
      .configure(configValues)

  override implicit lazy val app: Application = localGuiceApplicationBuilder().build()

  implicit lazy val ec = app.injector.instanceOf[ExecutionContext]

  lazy val config = app.injector.instanceOf[ConfigDecorator]

  implicit lazy val templateRenderer = app.injector.instanceOf[TemplateRenderer]

  def injected[T](c: Class[T]): T = app.injector.instanceOf(c)

  def injected[T](implicit evidence: ClassTag[T]): T = app.injector.instanceOf[T]

}
trait ActionBuilderFixture extends ActionBuilder[UserRequest, AnyContent] {
  override def invokeBlock[A](a: Request[A], block: UserRequest[A] => Future[Result]): Future[Result]
  override def parser: BodyParser[AnyContent] = Helpers.stubBodyParser()
  override protected def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
}
